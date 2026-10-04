package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookCombinerTest {
    private final Book route = new Book("ENCHANTMENT_OVERLOAD", 1, 5, "Overload", 0, 0);
    private SlotView book(int slot, int level, boolean inventory) {
        return SlotView.enchantedBook(slot, inventory, inventory ? slot - 54 : slot,
                "overload", level, slot == 13 ? List.of(route.getRomanLevel(level), "", "This is the item you will get.", "Click the ANVIL BELOW to combine.") : List.of(route.getRomanLevel(level)), "Enchanted Book");
    }
    private MenuSnapshot menu(boolean cursorEmpty, SlotView... books) {
        var slots = new ArrayList<SlotView>();
        for (int i=0;i<90;i++) slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        slots.set(8,SlotView.named(8,"",List.of()));
        slots.set(22,SlotView.named(22,"Combine Items",List.of("Combine the items in the slots to the", "left and right below.", "", "Cost", "0 Exp Levels", "", "Click to combine!")));
        boolean result=Arrays.stream(books).anyMatch(b->b.index()==13);
        boolean inputs=Arrays.stream(books).anyMatch(b->b.index()==29 || b.index()==33);
        if(result && !inputs) slots.set(22,SlotView.named(22,"Anvil",List.of("Claim the result item above!")));
        for (var b:books) slots.set(b.index(),b);
        return new MenuSnapshot(77,"Anvil",cursorEmpty,slots);
    }
    private Task task(int units) {
        var task = new Task(route,false,false);
        task.assignBook(route,1,0,units);
        task.setBookState(Task.BookState.COMBINE);
        return task;
    }
    @Test void outputArrivalBeforeCursorClearsDoesNotLoseTheCompletedMerge() {
        var task=task(3);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(81,1,true),book(82,1,true),book(83,1,true)),actions,1000);
        combine.tick(task,menu(true,book(29,1,false),book(82,1,true),book(83,1,true)),actions,1500);
        combine.tick(task,menu(true,book(29,1,false),book(33,1,false),book(83,1,true),book(13,2,false)),actions,2000);
        combine.tick(task,menu(false,book(81,2,true),book(83,1,true)),actions,2500);
        combine.tick(task,menu(false,book(81,2,true),book(83,1,true)),actions,2750);
        actions.clear();
        assertEquals(BookCombiner.Result.MERGED,combine.tick(task,menu(true,book(81,2,true),book(83,1,true)),actions,3000));
        assertEquals(List.of(1,2),task.bookList.stream().map(b->b.level).sorted().toList());
        assertEquals(List.of(),actions.serverEffects(),"bookkeeping precedes any click on the leftover input");
    }

    @Test void shortStaleInputAndOutputPacketsNeverRepeatACombineClick() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        var initial=menu(true,book(81,1,true),book(82,1,true));
        combine.tick(task,initial,actions,1000);
        for(int i=0;i<5;i++) combine.tick(task,initial,actions,1100+i*100);
        assertEquals(List.of("shiftclick:81"),actions.serverEffects());
        combine.tick(task,menu(true,book(29,1,false),book(82,1,true)),actions,2000);
        var preview=menu(true,book(29,1,false),book(33,1,false),book(13,2,false));
        combine.tick(task,preview,actions,2500);
        actions.clear();
        for(int i=0;i<10;i++) combine.tick(task,preview,actions,2600+i*100);
        assertEquals(List.of(),actions.serverEffects(),"a stale preview cannot repeat submission");
        combine.tick(task,menu(true,book(13,2,false)),actions,4000);
        combine.tick(task,menu(true,book(13,2,false)),actions,4500);
        assertEquals(List.of("click:22"),actions.serverEffects(),"output collection is also issued once");
    }

    @Test void consumedInputsWithResultAboveSignAreCollectedOnceThenAcknowledged() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(54,1,true),book(55,1,true)),actions,1000);
        combine.tick(task,menu(true,book(29,1,false),book(55,1,true)),actions,1500);
        combine.tick(task,menu(true,book(29,1,false),book(33,1,false),book(13,2,false)),actions,2000);
        actions.clear();
        var resultAboveSign=menu(true,book(13,2,false),SlotView.named(22,"Anvil",List.of("Claim the result item above!")));
        combine.tick(task,resultAboveSign,actions,2500);
        assertEquals(List.of("click:22"),actions.serverEffects());
        assertEquals("COLLECTING",combine.progress());
        assertEquals(2,task.bookList.size(),"claim click alone does not acknowledge arrival");
        actions.clear();
        combine.tick(task,resultAboveSign,actions,3000);
        assertTrue(actions.serverEffects().isEmpty());
        assertEquals(BookCombiner.Result.MERGED,combine.tick(task,menu(true,book(54,2,true)),actions,3500));
        assertEquals(1,task.bookList.size());
        assertEquals(2,task.bookList.getFirst().level);
    }

    @Test void returnedInputsWithAStaleResultDisplayCannotAuthorizeCollection() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(54,1,true),book(55,1,true)),actions,1000);
        combine.tick(task,menu(true,book(29,1,false),book(55,1,true)),actions,1500);
        combine.tick(task,menu(true,book(29,1,false),book(33,1,false),book(13,2,false)),actions,2000);
        actions.clear();
        combine.tick(task,menu(true,book(13,2,false),book(54,1,true),book(55,1,true)),actions,2500);
        assertTrue(actions.serverEffects().isEmpty());
        assertEquals("SUBMITTED",combine.progress());
    }

    @Test void rejectedFirstAndSecondInputClicksRetryTheirExactSourceOnlyAfterSettling() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        var initial=menu(true,book(54,1,true),book(55,1,true));
        combine.tick(task,initial,actions,1000);actions.clear();
        combine.slowdown(1200);
        combine.tick(task,initial,actions,4000);
        assertTrue(actions.serverEffects().isEmpty());
        combine.tick(task,initial,actions,4800);
        assertEquals(List.of("shiftclick:54"),actions.serverEffects());
        var firstAccepted=menu(true,book(29,1,false),book(55,1,true));
        actions.clear();combine.tick(task,firstAccepted,actions,5000);
        assertEquals(List.of("shiftclick:55"),actions.serverEffects());
        actions.clear();combine.tick(task,firstAccepted,actions,8000);
        combine.tick(task,firstAccepted,actions,8800);
        assertEquals(List.of("shiftclick:55"),actions.serverEffects());
        assertEquals(2,task.bookList.size());
    }

    @Test void rejectedCombineIsRetriedButConsumedInputsSwitchToCollectionInstead() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(54,1,true),book(55,1,true)),actions,1000);
        combine.tick(task,menu(true,book(29,1,false),book(55,1,true)),actions,1500);
        var preview=menu(true,book(29,1,false),book(33,1,false),book(13,2,false));
        combine.tick(task,preview,actions,2000);actions.clear();
        combine.tick(task,preview,actions,5000);combine.tick(task,preview,actions,5800);
        assertEquals(List.of("click:22"),actions.serverEffects());
        assertEquals("SUBMITTED",combine.progress());
        actions.clear();combine.tick(task,menu(true,book(13,2,false)),actions,6000);
        assertEquals("COLLECTING",combine.progress());
        assertEquals(List.of("click:22"),actions.serverEffects());
    }

    @Test void capturedFailedClaimRetriesAndAcknowledgesLateArrivalExactlyOnce() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(54,1,true),book(55,1,true)),actions,1000);
        combine.tick(task,menu(true,book(29,1,false),book(55,1,true)),actions,1500);
        combine.tick(task,menu(true,book(29,1,false),book(33,1,false),book(13,2,false)),actions,2000);
        var unclaimed=menu(true,book(13,2,false));
        combine.tick(task,unclaimed,actions,2500);actions.clear();
        combine.slowdown(2600);
        combine.tick(task,unclaimed,actions,5500);combine.tick(task,unclaimed,actions,6300);
        assertEquals(List.of("click:22"),actions.serverEffects());
        assertEquals(2,task.bookList.size());actions.clear();
        assertEquals(BookCombiner.Result.MERGED,combine.tick(task,menu(true,book(54,2,true)),actions,6500));
        assertTrue(actions.serverEffects().isEmpty());assertEquals(1,task.bookList.size());
        assertEquals(2,task.bookList.getFirst().level);
    }

    @Test void successDuringSlowdownWaitAcknowledgesWithoutAnyRetry() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(54,1,true),book(55,1,true)),actions,1000);
        combine.tick(task,menu(true,book(29,1,false),book(55,1,true)),actions,1500);
        combine.tick(task,menu(true,book(29,1,false),book(33,1,false),book(13,2,false)),actions,2000);
        combine.slowdown(2100);actions.clear();
        assertEquals(BookCombiner.Result.MERGED,combine.tick(task,menu(true,book(54,2,true)),actions,2300));
        assertTrue(actions.serverEffects().isEmpty());
    }

    @Test void changedSourceSlotAndBusyCursorDoNotAuthorizeRetries() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(54,1,true),book(55,1,true)),actions,1000);actions.clear();
        var rearranged=menu(true,book(56,1,true),book(55,1,true));
        combine.tick(task,rearranged,actions,4000);combine.tick(task,rearranged,actions,5000);
        var busy=menu(false,book(54,1,true),book(55,1,true));
        combine.tick(task,busy,actions,6000);combine.tick(task,busy,actions,7000);
        assertTrue(actions.serverEffects().isEmpty());
    }

    @Test void staleCombineButtonAfterInputConsumptionWaitsForClaimLore() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(54,1,true),book(55,1,true)),actions,1000);
        combine.tick(task,menu(true,book(29,1,false),book(55,1,true)),actions,1500);
        combine.tick(task,menu(true,book(29,1,false),book(33,1,false),book(13,2,false)),actions,2000);
        actions.clear();
        combine.tick(task,menu(true,book(13,2,false),SlotView.named(22,"Combine Items",List.of("Click to combine!"))),actions,2500);
        assertTrue(actions.serverEffects().isEmpty());assertEquals("SUBMITTED",combine.progress());
        combine.tick(task,menu(true,book(13,2,false)),actions,3000);
        assertEquals(List.of("click:22"),actions.serverEffects());
    }

    @Test void capturedPreviewAboveTheButtonSubmitsOnceAndAcknowledgesDirectDelivery() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(54,1,true),book(61,1,true)),actions,1000);
        combine.tick(task,menu(true,book(29,1,false),book(61,1,true)),actions,1500);
        actions.clear();
        var captured=menu(true,book(29,1,false),book(33,1,false),book(13,2,false));
        combine.tick(task,captured,actions,2000);
        assertEquals(List.of("click:22"),actions.serverEffects());
        assertEquals("SUBMITTED",combine.progress());
        actions.clear();
        combine.tick(task,captured,actions,2500);
        assertTrue(actions.serverEffects().isEmpty());
        assertEquals(BookCombiner.Result.MERGED,combine.tick(task,menu(true,book(54,2,true)),actions,3000));
        assertEquals(2,task.bookList.getFirst().level);
    }

    @Test void previewAloneOrWrongPreviewNeverAuthorizesSubmission() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(54,1,true),book(61,1,true)),actions,1000);
        combine.tick(task,menu(true,book(29,1,false),book(61,1,true)),actions,1500);
        actions.clear();
        combine.tick(task,menu(true,book(29,1,false),book(33,1,false),book(13,3,false)),actions,2000);
        combine.tick(task,menu(true,book(29,1,false),book(33,1,false),book(13,2,false),
                SlotView.named(22,"Combine Items",List.of("Not enough experience!"))),actions,2500);
        combine.tick(task,menu(true,book(29,1,false),book(33,1,false),book(22,2,false)),actions,3000);
        assertTrue(actions.serverEffects().isEmpty());
        assertEquals("SECOND_INPUT",combine.progress());
        assertEquals(2,task.bookList.size());
    }

    @Test void changedContainerAndUnrelatedInventoryCannotAcknowledgeAnOperation() {
        var task=task(2);var combine=new BookCombiner();var actions=new RecordingActions();
        combine.tick(task,menu(true,book(81,1,true),book(82,1,true)),actions,1000);
        var changed=menu(true,book(81,2,true));
        actions.clear();
        assertEquals(BookCombiner.Result.BLOCKED,combine.tick(task,
                new MenuSnapshot(78,changed.title(),true,changed.slots()),actions,2000));
        assertEquals(2,task.bookList.size());assertTrue(actions.serverEffects().isEmpty());
    }

    @Test void multiEnchantmentLookalikesNeverEnterTheAnvil() {
        var task=task(2);var actions=new RecordingActions();
        var lookalike=new SlotView(81,true,27,false,"Enchanted Book","Enchanted Book",
                List.of("Overload I"),"ENCHANTED_BOOK",Map.of("overload",1,"power",5),1,1);
        assertEquals(BookCombiner.Result.INPUTS_MISSING,new BookCombiner().tick(task,
                menu(true,lookalike,book(82,1,true)),actions,1000));
        assertTrue(actions.serverEffects().isEmpty());
    }

    @Test void unrelatedItemAlreadyInTheAnvilBlocksInputClicks() {
        var actions=new RecordingActions();
        assertEquals(BookCombiner.Result.BLOCKED,new BookCombiner().tick(task(2),
                menu(true,SlotView.named(29,"Sword",List.of()),book(81,1,true),book(82,1,true)),actions,1000));
        assertTrue(actions.serverEffects().isEmpty());
    }

    @Test void timeoutKeepsOwnershipAndNeverResubmits() {
        var task=task(2);var actions=new RecordingActions();var combine=new BookCombiner();
        var initial=menu(true,book(81,1,true),book(82,1,true));
        combine.tick(task,initial,actions,1000);actions.clear();
        assertEquals(BookCombiner.Result.BLOCKED,combine.tick(task,initial,actions,31000));
        assertEquals(2,task.bookList.size());assertTrue(actions.serverEffects().isEmpty());
    }

    @Test void physicalTimeoutCanReleaseOnlyWhenNoBookIsStrandedInTheAnvilOrCursor() {
        var task=task(2);var actions=new RecordingActions();var combine=new BookCombiner();
        combine.tick(task,menu(true,book(81,1,true),book(82,1,true)),actions,1000);
        assertEquals(BookCombiner.Result.BLOCKED,combine.tick(task,menu(true),actions,31000));
        assertTrue(combine.canReconcilePhysicalTimeout(menu(true)));
        assertFalse(combine.canReconcilePhysicalTimeout(menu(true,book(29,1,false))));
        assertFalse(combine.canReconcilePhysicalTimeout(menu(true,book(22,2,false))));
        assertFalse(combine.canReconcilePhysicalTimeout(menu(true,book(13,2,false))));
        assertFalse(combine.canReconcilePhysicalTimeout(menu(false)));
        assertEquals(2,task.bookList.size(),"release does not itself declare loss or mutate ownership");
    }

    @Test void partialInputsAreCombinedThenWaitWithoutCancellingTheLiveBuyOrder() {
        var task=task(3);task.actionSchedule=Task.ActionSchedule.NONE;
        // Verified output replaces two inputs; one input remains and 13 are outstanding.
        task.bookList.remove(0);task.bookList.remove(0);task.bookList.add(new BookList(route,2,0));
        var actions=new RecordingActions();
        assertEquals(BookCombiner.Result.NO_PAIR,new BookCombiner().tick(task,
                menu(true,book(81,2,true),book(83,1,true)),actions,1000));
        task.finishCombining();
        assertEquals(Task.BookState.IN_BUY_ORDER,task.getBookState());
        assertEquals(13,task.getAmountToOrder());assertTrue(actions.serverEffects().isEmpty());
    }

    @Test void unpairedStoredInputsWaitInsteadOfCyclingBetweenAnvilAndCombine() {
        var task=task(3);
        task.bookList.clear();
        task.bookList.add(new BookList(route,1,1));
        task.bookList.add(new BookList(route,2,2));
        task.finishCombining();
        assertEquals(Task.BookState.IN_BUY_ORDER,task.getBookState());
        assertEquals(13,task.getAmountToOrder());
    }

    @Test void storedPairsAreRetrievedBeforeCombiningAndInvalidCompletedModelsAreRejected() {
        var partial=task(2);partial.bookList.getFirst().location=1;
        partial.finishCombining();
        assertEquals(Task.BookState.ANVIL,partial.getBookState());
        var full=task(16);full.bookList.clear();
        assertThrows(IllegalStateException.class,full::finishCombining);
    }

    @Test void sixteenInputsReachOneOutputAndTheSaleStageAcrossFifteenVerifiedMerges() {
        replaySixteenInputs(false);
    }

    @Test void sixteenInputsReachSaleEvenWhenEveryAnvilStepIsRejectedOnce() {
        replaySixteenInputs(true);
    }

    private void replaySixteenInputs(boolean rejectEachStep) {
        var task=task(16);var combine=new BookCombiner();var actions=new RecordingActions();
        var held=new ArrayList<Integer>(Collections.nCopies(16,1));
        long now=1000;int merges=0;Integer left=null,right=null,output=null;
        var rejected=new HashSet<String>();
        for(int tick=0;tick<1000 && merges<15;tick++,now+=500) {
            var placed=new ArrayList<SlotView>();
            for(int i=0;i<held.size();i++) placed.add(book(54+i,held.get(i),true));
            if(left!=null) placed.add(book(29,left,false));
            if(right!=null) placed.add(book(33,right,false));
            if(output!=null) placed.add(book(13,output,false));
            else if(left!=null && right!=null && left.equals(right)) placed.add(book(13,left+1,false));
            actions.clear();
            var result=combine.tick(task,menu(true,placed.toArray(SlotView[]::new)),actions,now);
            if(result==BookCombiner.Result.MERGED) merges++;
            assertNotEquals(BookCombiner.Result.BLOCKED,result,combine.failure());
            assertTrue(actions.serverEffects().size()<=1);
            for(String action:actions.serverEffects()) {
                if(rejectEachStep && rejected.add(merges+":"+combine.progress())) {
                    combine.slowdown(now+100);
                    continue;
                }
                if(action.startsWith("shiftclick:")) {
                    int level=held.remove(Integer.parseInt(action.substring(11))-54);
                    if(left==null) left=level;else right=level;
                } else if(output==null) { output=left+1;left=null;right=null; }
                else {held.add(output);output=null;}
            }
        }
        assertEquals(15,merges);assertEquals(List.of(5),held);
        if(rejectEachStep) assertEquals(60,rejected.size(),"both inputs, combine and claim each fail once for every merge");
        task.finishCombining();assertEquals(Task.BookState.SELL,task.getBookState());
        assertEquals(1,task.bookList.size());assertEquals(5,task.bookList.getFirst().level);
    }
}
