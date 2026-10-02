package com.goofy.goofyaddons.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link GameActions} that records instead of acting, so a test can assert the exact
 * sequence an engine produced — or that it produced nothing at all.
 */
public final class RecordingActions implements GameActions {
    private final List<String> performed = new ArrayList<>();
    private boolean signPresent = true;

    /** Simulates there being no sign open, so writeSign reports failure. */
    public RecordingActions withoutSign() {
        signPresent = false;
        return this;
    }

    @Override public void click(int slot, boolean shift) {
        performed.add((shift ? "shiftclick:" : "click:") + slot);
    }

    @Override public void closeMenu() { performed.add("close"); }
    @Override public void command(String text) { performed.add("command:" + text); }
    @Override public void message(String text) { performed.add("message:" + text); }

    @Override public boolean writeSign(String text) {
        performed.add(signPresent ? "sign:" + text : "sign-failed:" + text);
        return signPresent;
    }

    /** Everything performed, in order. */
    public List<String> performed() { return List.copyOf(performed); }

    /** Only the actions that change server state; messages and closes are not those. */
    public List<String> serverEffects() {
        return performed.stream()
                .filter(action -> action.startsWith("click:") || action.startsWith("shiftclick:")
                        || action.startsWith("command:") || action.startsWith("sign:"))
                .toList();
    }

    public void clear() { performed.clear(); }
}
