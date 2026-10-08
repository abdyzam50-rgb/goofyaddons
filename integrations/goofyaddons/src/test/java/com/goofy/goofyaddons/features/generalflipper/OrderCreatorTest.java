package com.goofy.goofyaddons.features.generalflipper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderCreatorTest {
    private static final String ME = "GoofyPlayer";

    @Test void ownOrdersAreRecognisedThroughRanksAndFormatting() {
        assertEquals(OrderLore.Creator.OWN, OrderLore.creator("By: GoofyPlayer", ME));
        assertEquals(OrderLore.Creator.OWN, OrderLore.creator("§7By: §b[MVP§c+§b] §bGoofyPlayer", ME));
        assertEquals(OrderLore.Creator.OWN, OrderLore.creator("Filled: 1/1\nBy: goofyplayer", ME));
    }

    @Test void anotherPlayersOrderIsNeverRetryable() {
        // A readable creator that is not us is a final answer, not a loading artefact.
        assertEquals(OrderLore.Creator.OTHER, OrderLore.creator("By: SomeoneElse", ME));
        assertEquals(OrderLore.Creator.OTHER, OrderLore.creator("§7By: §b[VIP] §bSomeoneElse", ME));
    }

    @Test void anAbsentCreatorFieldIsRetryableRatherThanAttributed() {
        // A menu that has not finished loading has no By: line at all.
        assertEquals(OrderLore.Creator.UNREADABLE, OrderLore.creator("", ME));
        assertEquals(OrderLore.Creator.UNREADABLE, OrderLore.creator(null, ME));
        assertEquals(OrderLore.Creator.UNREADABLE, OrderLore.creator("Filled: 0/16\nClick to view options!", ME));
    }

    @Test void aPresentButUnparsableCreatorIsNotTreatedAsOurs() {
        // The field exists, so it is not a loading artefact, but it does not name us.
        assertEquals(OrderLore.Creator.OTHER, OrderLore.creator("By:", ME));
        assertEquals(OrderLore.Creator.OTHER, OrderLore.creator("By: ThisNameIsFarTooLongToBeValid", ME));
    }

    @Test void anUnknownViewerCanNeverOwnAnOrder() {
        assertEquals(OrderLore.Creator.OTHER, OrderLore.creator("By: GoofyPlayer", null));
        assertEquals(OrderLore.Creator.OTHER, OrderLore.creator("By: GoofyPlayer", "  "));
    }

    @Test void theThreeWayDecisionAgreesWithTheOwnOrderPredicate() {
        for (String lore : new String[]{"By: GoofyPlayer", "By: Other", "By:", "", "Filled: 1/1", null}) {
            boolean own = OrderLore.ownOrder(lore, ME);
            assertEquals(own, OrderLore.creator(lore, ME) == OrderLore.Creator.OWN, () -> "lore: " + lore);
        }
    }
}
