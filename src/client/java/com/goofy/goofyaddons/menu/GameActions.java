package com.goofy.goofyaddons.menu;

/**
 * Everything an engine does to the world, behind one interface.
 *
 * <p>The effects half of the observation seam. With this and {@link MenuSnapshot}, a
 * test can run an engine and assert exactly which clicks and commands it produced —
 * including, crucially, that it produced none. Most of the engines' safety rules are
 * of the form "do not click", and that is the assertion no current test can make.
 */
public interface GameActions {
    /** Click a menu slot. {@code shift} is the quick-move that transfers a stack. */
    void click(int slot, boolean shift);

    /** Close whatever menu is open. A no-op when nothing is open. */
    void closeMenu();

    /** Send a chat command, without the leading slash. */
    void command(String text);

    /** Show the player a client-side message. */
    void message(String text);

    /**
     * Write an amount onto an open quantity sign and dismiss it.
     *
     * @return false when there is no sign open or the amount could not be written, so a
     *         caller never assumes an amount reached the server.
     */
    boolean writeSign(String text);
}
