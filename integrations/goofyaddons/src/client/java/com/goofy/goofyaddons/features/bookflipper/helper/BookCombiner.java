package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.features.transaction.ActionRetry;
import com.goofy.goofyaddons.menu.GameActions;
import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.menu.SlotView;
import com.goofy.goofyaddons.utils.Chat;
import java.util.Comparator;
import java.util.List;

/** One acknowledged anvil operation at a time. Inventory baselines never advance while waiting. */
public final class BookCombiner {
    public enum Result { WAITING, MERGED, NO_PAIR, INPUTS_MISSING, BLOCKED }
    private enum Phase { FIRST_INPUT, SECOND_INPUT, SUBMITTED, COLLECTING }
    private Task owner;
    private BookList first, second;
    private Phase phase;
    private int container, beforeInput, beforeOutput;
    private long started;
    private String failure;
    private java.util.Map<String,Integer> skillLevels=java.util.Map.of();
    public void observedSkills(java.util.Map<String,Integer> levels){skillLevels=java.util.Map.copyOf(levels);}
    private final ActionRetry retry = new ActionRetry();

    public void slowdown(long now) { if (pending()) retry.slowdown(now); }

    public String failure() { return failure; }
    public String progress() { return phase == null ? "idle" : phase.name(); }
    public boolean pending() { return phase != null; }
    public int actionRetries() { return retry.retries(); }
    /** A timed-out inventory operation can return to physical reconciliation only with no stranded anvil/cursor item. */
    public boolean canReconcilePhysicalTimeout(MenuSnapshot menu) {
        return failure != null && failure.startsWith("Anvil operation timed out") && menu != null
                && "Anvil".equals(Chat.strip(menu.title())) && menu.cursorEmpty()
                && !occupied(menu.slot(29)) && !occupied(menu.slot(33))
                && !hasBook(menu.slot(13)) && !hasBook(menu.slot(22));
    }
    public java.util.Map<String,Object> diagnosticState() {
        return java.util.Map.of("phase",progress(),"container",container,"inputLevel",first==null?0:first.level,
                "beforeInputs",beforeInput,"beforeOutputs",beforeOutput,"failure",failure==null?"none":failure,
                "actionRetries",retry.retries());
    }
    public void reset() { owner=null;first=null;second=null;phase=null;failure=null;retry.reset(); }

    public Result tick(Task task, MenuSnapshot menu, GameActions actions, long now) {
        if (failure != null) return Result.BLOCKED;
        if (menu == null || !"Anvil".equals(Chat.strip(menu.title()))) return Result.WAITING;
        if (phase != null && (owner != task || container != menu.containerId()))
            return block("Anvil context changed with an unverified combine; ownership retained.");
        if (!menu.cursorEmpty()) {
            retry.clearObservation();
            if (phase != null && now - started >= 30_000)
                return block("Anvil operation timed out; inputs and ownership retained.");
            return Result.WAITING;
        }
        if (phase == null) {
            for (var a : task.bookList) {
                if (a.location != 0 || a.level >= task.getBook().sellLevel()) continue;
                var b = task.bookList.stream().filter(other -> other != a && other.location == 0 && other.level == a.level)
                        .findFirst().orElse(null);
                if (b != null) { first=a;second=b;break; }
            }
            if (first == null) return Result.NO_PAIR;
            if (occupied(menu.slot(29)) || occupied(menu.slot(33)))
                return block("Anvil already contains inputs without a matching operation; review required.");
            var inventory = inventory(menu,task.getBook(),first.level);
            if (inventory.size() < 2) { first=null;second=null;return Result.INPUTS_MISSING; }
            owner=task;container=menu.containerId();started=now;
            beforeInput=inventory.size();beforeOutput=inventory(menu,task.getBook(),first.level+1).size();
            phase=Phase.FIRST_INPUT;
            retry.sent(inventory.getFirst(),true,now);
            actions.click(inventory.getFirst(),true);
            return Result.WAITING;
        }
        Book book=task.getBook(); int level=first.level;
        int output=inventory(menu,book,level+1).size();
        int input=inventory(menu,book,level).size() + (matches(menu.slot(29),book,level)?1:0)
                + (matches(menu.slot(33),book,level)?1:0);
        // Observe completion before any other click. A cursor/packet delay cannot consume this baseline.
        if ((phase==Phase.SUBMITTED || phase==Phase.COLLECTING) && output==beforeOutput+1
                && input==beforeInput-2 && !occupied(menu.slot(29)) && !occupied(menu.slot(33))) {
            if (!task.bookList.contains(first) || !task.bookList.contains(second))
                return block("Book model changed during combination; ownership retained.");
            task.bookList.removeAll(List.of(first,second));
            task.bookList.add(new BookList(book,level+1,0));
            task.progress(now);
            task.bookList.sort(Comparator.comparingInt(b->b.level));
            reset();
            return Result.MERGED;
        }
        if (now - started >= 30_000)
            return block("Anvil operation timed out; inputs and ownership retained.");
        if (output>beforeOutput+1 || input>beforeInput)
            return block("Unexpected books arrived during combination; reconcile before continuing.");
        if (retry.coolingDown(now)) { retry.clearObservation();return Result.WAITING; }
        if (phase==Phase.FIRST_INPUT) {
            if (occupied(menu.slot(29)) && !matches(menu.slot(29),book,level)
                    || occupied(menu.slot(33))) return block("Unexpected first anvil input; no combine submitted.");
            var inventory=inventory(menu,book,level);
            if (!matches(menu.slot(29),book,level) || inventory.size()!=beforeInput-1) {
                retry.retry(menu,!occupied(menu.slot(29)) && !occupied(menu.slot(33))
                        && inventory.size()==beforeInput && output==beforeOutput
                        && inventory.contains(retry.slot()),actions,now);
                return Result.WAITING;
            }
            phase=Phase.SECOND_INPUT;
            retry.sent(inventory.getFirst(),true,now);
            actions.click(inventory.getFirst(),true);
            return Result.WAITING;
        }
        if (phase==Phase.SECOND_INPUT) {
            if (occupied(menu.slot(29)) && !matches(menu.slot(29),book,level)
                    || occupied(menu.slot(33)) && !matches(menu.slot(33),book,level))
                return block("Unexpected anvil input identity; no combine submitted.");
            if (!matches(menu.slot(29),book,level) || !matches(menu.slot(33),book,level)
                    || inventory(menu,book,level).size()!=beforeInput-2) {
                var inventory=inventory(menu,book,level);
                retry.retry(menu,matches(menu.slot(29),book,level) && !occupied(menu.slot(33))
                        && inventory.size()==beforeInput-1 && output==beforeOutput
                        && inventory.contains(retry.slot()),actions,now);
                return Result.WAITING;
            }
            // The result preview is above the action button, not the button itself.
            var button = menu.slot(22);
            if(button!=null && "Combine Items".equals(Chat.strip(button.customName()))) {
                String reason=com.goofy.goofyaddons.features.access.ActionRequirements.blocked(button.lore(),
                        skillLevels,com.goofy.goofyaddons.features.access.ActionRequirements.Action.COMBINE);
                if(reason!=null){failure="Cannot combine: "+reason+"; input books retained for review";return Result.BLOCKED;}
            }
            if (!matches(menu.slot(13),book,level+1) || button == null || button.empty()
                    || !"Combine Items".equals(Chat.strip(button.customName()))
                    || !button.hasLoreLine("Click to combine!")) return Result.WAITING;
            phase=Phase.SUBMITTED;
            retry.sent(22,false,now);
            actions.click(22,false);
            return Result.WAITING;
        }
        // The second click only collects a verified output after the inputs have been consumed.
        // A stale preview with both inputs still present never authorizes repeat submission.
        if (phase==Phase.SUBMITTED && !occupied(menu.slot(29)) && !occupied(menu.slot(33))
                && matches(menu.slot(13),book,level+1) && input==beforeInput-2 && output==beforeOutput
                && claimReady(menu.slot(22))) {
            phase=Phase.COLLECTING;
            retry.sent(22,false,now);
            // The book remains in display slot 13; the sign in slot 22 claims it.
            actions.click(22,false);
            return Result.WAITING;
        }
        var button=menu.slot(22);
        boolean ready=button!=null && !button.empty();
        boolean stillUncombined=phase==Phase.SUBMITTED && matches(menu.slot(29),book,level)
                && matches(menu.slot(33),book,level) && input==beforeInput && output==beforeOutput
                && matches(menu.slot(13),book,level+1) && ready
                && "Combine Items".equals(Chat.strip(button.customName())) && button.hasLoreLine("Click to combine!");
        boolean stillUncollected=phase==Phase.COLLECTING && !occupied(menu.slot(29)) && !occupied(menu.slot(33))
                && input==beforeInput-2 && output==beforeOutput && matches(menu.slot(13),book,level+1)
                && claimReady(button);
        retry.retry(menu,stillUncombined || stillUncollected,actions,now);
        return Result.WAITING;
    }
    private Result block(String message) { failure=message;return Result.BLOCKED; }
    private static boolean occupied(SlotView slot) { return slot != null && !slot.empty(); }
    private static boolean claimReady(SlotView slot) {
        return occupied(slot) && slot.hasLoreLine("Claim the result item above!");
    }
    private static boolean hasBook(SlotView slot) { return slot!=null && !slot.empty() && slot.enchantedBook(); }
    private static boolean matches(SlotView slot,Book book,int level) {
        return hasBook(slot) && slot.count()==1 && slot.enchantments()!=null && slot.enchantments().size()==1
                && Integer.valueOf(level).equals(slot.enchantments().get(MenuSnapshot.enchantmentKey(book.id())));
    }
    private static List<Integer> inventory(MenuSnapshot menu,Book book,int level) {
        return menu.slots().stream().filter(slot->slot.inPlayerInventory() && slot.containerSlot()>=0
                && slot.containerSlot()<36 && matches(slot,book,level)).map(SlotView::index).toList();
    }
}
