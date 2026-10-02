package com.goofy.goofyaddons.features;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfirmationCheckTest {
    private String preview="Item: Enchanted Ink Sac\nAmount: 32x\nPrice per unit: 133,350.4 coins\nTotal cost: 4,267,212.8 coins";
    private boolean matches(String lore) {return ConfirmationCheck.matches("Confirm Buy Order",false,"Confirm",lore,"Enchanted Ink Sac",32,133350.4);}
    @Test void exactIdentityQuantityAndBothPricesAreRequired() {
        assertTrue(matches(preview));
        assertTrue(matches(preview.replace("Item:","§7Product:").replace("32x","§a32x")));
        assertFalse(matches(preview.replace("Ink Sac","Sugar")));
        assertFalse(matches(preview.replace("32x","31x")));
        assertFalse(matches(preview.replace("133,350.4","133,350.5")));
        assertFalse(matches(preview.replace("4,267,212.8","4,267,213.8")));
    }
    @Test void unknownOrConflictingEvidenceCannotBeConfirmed() {
        assertFalse(matches("Amount: 32x\nPrice per unit: 133,350.4 coins"));
        assertFalse(matches("Item: Enchanted Ink Sac\nPrice per unit: 133,350.4 coins"));
        assertFalse(matches("Item: Enchanted Ink Sac\nAmount: 32x"));
        assertFalse(matches(preview+"\nProduct: Sugar"));
        assertFalse(matches(preview+"\nQuantity: 64x"));
        assertFalse(matches(preview+"\nUnit price: 1 coins"));
        assertFalse(matches(preview+"\nUnit price: Loading..."));
        assertFalse(matches(preview+"\nBuying: Loading..."));
        assertFalse(matches(preview+"\nBuying: 3,2x Enchanted Ink Sac"));
        assertFalse(matches(preview.replace("32x","3,2x")));
        assertFalse(matches(preview.replace("32x","32x 999")));
        assertFalse(ConfirmationCheck.matches("Confirm Sell Offer",false,"Confirm",preview,"Enchanted Ink Sac",32,133350.4));
    }
    @Test void explicitActionIdentityCanSupplyTheQuantity() {
        assertTrue(ConfirmationCheck.matches("Confirm Sell Offer",true,"Confirm","Selling: 1x Ultimate Wise V\nTotal value: 1,000 coins","Ultimate Wise V",1,1000));
        assertFalse(ConfirmationCheck.matches("Confirm Sell Offer",true,"Confirm","Buying: 1x Ultimate Wise V\nTotal value: 1,000 coins","Ultimate Wise V",1,1000));
    }
    @Test void finalBuyCheckRejectsChangedProfitAndCapitalLimits() {
        assertTrue(ConfirmationCheck.buyAllowed(100,10,120,1.25,5,100,1000));
        assertFalse(ConfirmationCheck.buyAllowed(100,10,101,1.25,5,100,1000));
        assertFalse(ConfirmationCheck.buyAllowed(100,10,120,1.25,5,100,999));
        assertFalse(ConfirmationCheck.buyAllowed(100,10,Double.NaN,1.25,5,100,1000));
    }
}
