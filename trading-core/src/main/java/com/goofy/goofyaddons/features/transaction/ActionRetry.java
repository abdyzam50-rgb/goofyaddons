package com.goofy.goofyaddons.features.transaction;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.List;
import java.util.Locale;

/**
 * Retry only a still-unapplied action after a quiet, stable observation window.
 *
 * <p>Shared by the book and general engines so a click is repeated under one rule: the
 * caller proves nothing changed since it was sent, the menu stayed still, and the retry
 * budget is not spent. A placement or cancellation confirmation is never retried here.
 */
public final class ActionRetry {
    private int slot, retries;
    private boolean shift;
    private long sentAt, blockedUntil, stableSince;
    private List<SlotView> stableSlots;
    private static final long RETRY_MS = 3_000, STABLE_MS = 750;
    private static final int MAX_RETRIES = 3;

    public static boolean slowdownMessage(String message) {
        String text=Chat.strip(message).toLowerCase(Locale.ROOT);
        return text.startsWith("you are clicking too fast") || text.startsWith("you're clicking too fast")
                || text.startsWith("you are doing that too fast") || text.startsWith("you're doing that too fast")
                || text.startsWith("please slow down") || text.startsWith("slow down!");
    }
    public void reset() { retries=0; sentAt=0; blockedUntil=0; stableSlots=null; }
    public void slowdown(long now) { blockedUntil=Math.max(blockedUntil,now+1_500); stableSlots=null; }
    public boolean coolingDown(long now) { return now<blockedUntil; }
    public int retries() { return retries; }
    public void sent(int clickSlot,boolean quickMove,long now) {
        slot=clickSlot;shift=quickMove;sentAt=now;retries=0;stableSlots=null;
    }
    public void clearObservation() { stableSlots=null; }

    /** The caller proves original quantities, exact identities and the same menu context. */
    public boolean retry(MenuSnapshot menu,boolean unapplied,GameActions actions,long now) {
        if (!unapplied || !menu.cursorEmpty()) { clearObservation(); return false; }
        if (!menu.slots().equals(stableSlots)) { stableSlots=menu.slots();stableSince=now;return false; }
        if (now-sentAt<RETRY_MS || now-stableSince<STABLE_MS || coolingDown(now) || retries>=MAX_RETRIES)
            return false;
        sentAt=now;retries++;stableSlots=null;
        actions.click(slot,shift);
        return true;
    }
    public int slot() { return slot; }
}
