package com.goofy.goofyaddons.menu;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The fake's own contract. Engine tests will assert against serverEffects(), so the
 * line it draws between "changed the server" and "only told the player" matters.
 */
class RecordingActionsTest {
    @Test void everyActionIsRecordedInOrder() {
        RecordingActions actions = new RecordingActions();
        actions.command("bz Ultimate Wise");
        actions.click(15, false);
        actions.click(9, true);
        actions.writeSign("16");
        actions.closeMenu();
        actions.message("paused");
        assertEquals(List.of("command:bz Ultimate Wise", "click:15", "shiftclick:9", "sign:16", "close", "message:paused"),
                actions.performed());
    }

    @Test void onlyTheActionsThatReachTheServerCountAsEffects() {
        RecordingActions actions = new RecordingActions();
        actions.closeMenu();
        actions.message("Trading paused: something went wrong");
        assertEquals(List.of(), actions.serverEffects(),
                "closing a menu and warning the player change nothing a trade depends on");

        actions.click(13, false);
        assertEquals(List.of("click:13"), actions.serverEffects());
    }

    @Test void aSignThatIsNotOpenReportsFailureAndIsNotAnEffect() {
        RecordingActions actions = new RecordingActions().withoutSign();
        assertFalse(actions.writeSign("16"), "a caller must be able to tell the write failed");
        assertEquals(List.of("sign-failed:16"), actions.performed());
        assertEquals(List.of(), actions.serverEffects(), "a failed write never reached the server");
    }

    @Test void clearingLetsATestAssertPerPhase() {
        RecordingActions actions = new RecordingActions();
        actions.click(1, false);
        actions.clear();
        assertEquals(List.of(), actions.performed());
    }
}
