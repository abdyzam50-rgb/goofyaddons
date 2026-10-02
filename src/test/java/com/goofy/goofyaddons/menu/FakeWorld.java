package com.goofy.goofyaddons.menu;

/**
 * A {@link GameWorld} a test sets up directly. Client-thread work runs inline, which is
 * what a single-threaded test wants.
 */
public final class FakeWorld implements GameWorld {
    private boolean inWorld = true;
    private String username = "GoofyPlayer";
    private boolean signOpen;
    private MenuSnapshot menu;

    public FakeWorld inWorld(boolean value) { this.inWorld = value; return this; }
    public FakeWorld username(String value) { this.username = value; return this; }
    public FakeWorld signOpen(boolean value) { this.signOpen = value; return this; }
    public FakeWorld showing(MenuSnapshot value) { this.menu = value; return this; }

    /** No menu open: the engine sees a player in the world with nothing on screen. */
    public FakeWorld showingNothing() { this.menu = new MenuSnapshot(0, null, true, java.util.List.of()); return this; }

    @Override public boolean inWorld() { return inWorld; }
    @Override public String username() { return username; }
    @Override public boolean signEditorOpen() { return signOpen; }
    @Override public MenuSnapshot menu() { return menu; }
    @Override public void onClientThread(Runnable work) { work.run(); }
}
