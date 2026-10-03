package com.goofy.goofyaddons.menu;

/**
 * Everything an engine observes, behind one interface.
 *
 * <p>With this and {@link GameActions} an engine needs no reference to Minecraft at all,
 * so a test can describe a situation and assert what the engine does about it.
 */
public interface GameWorld {
    /** False when there is no player or no level, so engines stop rather than guess. */
    boolean inWorld();

    /** The player's name, or null when unknown. Needed to tell a co-op order apart. */
    String username();

    /**
     * The current time in milliseconds.
     *
     * <p>On the seam rather than read directly, so a test can advance time instead of
     * sleeping. The observation helpers already take a clock as an argument; this is what
     * lets an engine hand them a controlled one.
     */
    long now();

    /** Whether a quantity sign is the open screen. */
    boolean signEditorOpen();

    /** The open menu, or null when there is no player. */
    MenuSnapshot menu();

    /** Runs work on the client thread, for completions that arrive off it. */
    void onClientThread(Runnable work);
}
