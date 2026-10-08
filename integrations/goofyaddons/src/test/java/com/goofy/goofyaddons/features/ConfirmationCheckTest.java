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
    private com.google.gson.JsonObject observedPreview() throws Exception {
        try(var input=java.util.Objects.requireNonNull(getClass().getResourceAsStream("/fixtures/overload-buy-confirmation.json"));
            var reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)) {
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private String observedLore(com.google.gson.JsonObject fixture) {
        return String.join("\n",fixture.getAsJsonArray("lore").asList().stream().map(com.google.gson.JsonElement::getAsString).toList());
    }
    @Test void observedOverloadOrderFieldIsAcceptedWithItsExactPrices() throws Exception {
        var f=observedPreview();String lore=observedLore(f);
        assertTrue(ConfirmationCheck.matches(f.get("title").getAsString(),false,f.get("buttonName").getAsString(),lore,
                f.get("expectedItem").getAsString(),f.get("expectedUnits").getAsInt(),f.get("expectedUnitPrice").getAsDouble()));
        assertTrue(ConfirmationCheck.matches("Confirm Buy Order",false,"Buy Order",lore.replace("Order: 16x Overload I","§7Order: §a16x Overload I"),"Overload I",16,685510));
    }
    @Test void observedOverloadOrderFieldCannotHideItemQuantityPriceOrDuplicateConflicts() throws Exception {
        String lore=observedLore(observedPreview());
        for(String invalid:new String[]{lore.replace("16x Overload I","15x Overload I"),
                lore.replace("Overload I","Overload II"),lore.replace("685,510.0","685,511.0"),
                lore.replace("10,968,160","10,968,161"),lore+"\nOrder: 32x Overload I",
                lore+"\nItem: Sugar",lore.replace("Order: 16x Overload I","Order: Loading..."),
                lore.replace("16x Overload I","1,6x Overload I")}) {
            assertFalse(ConfirmationCheck.matches("Confirm Buy Order",false,"Buy Order",invalid,"Overload I",16,685510),invalid);
        }
    }
    @Test void observedRoundedTotalsAreAcceptedForFractionalUnitPrices() {
        // Captured 2026-10-02: 16 x 749,014.1 = 11,984,225.6 and 16 x 782,277.9 = 12,516,446.4, totals rounded.
        String[][] observed={{"749,014.1","11,984,226"},{"782,277.9","12,516,446"}};
        double[] prices={749014.1,782277.9};
        for(int i=0;i<observed.length;i++) {
            String lore="Bazaar\n\nPrice per unit: "+observed[i][0]+" coins\n\nOrder: 16x Overload I\nTotal price: "+observed[i][1]
                    +" coins\n\nOrders are shared by co-op!\n\nClick to submit order!";
            assertTrue(ConfirmationCheck.matches("Confirm Buy Order",false,"Buy Order",lore,"Overload I",16,prices[i]),lore);
            String offByOne=lore.replace(observed[i][1]+" coins",observed[i][1].substring(0,observed[i][1].length()-1)
                    +(char)(observed[i][1].charAt(observed[i][1].length()-1)+1)+" coins");
            assertFalse(ConfirmationCheck.matches("Confirm Buy Order",false,"Buy Order",offByOne,"Overload I",16,prices[i]),offByOne);
            assertFalse(ConfirmationCheck.matches("Confirm Buy Order",false,"Buy Order",lore,"Overload I",16,prices[i]+0.1));
        }
        String gear="Bazaar\n\nPrice per unit: 403,562.6 coins\n\nOrder: 19x Precursor Gear\nTotal price: 7,667,689 coins\n\nClick to submit order!";
        assertTrue(ConfirmationCheck.matches("Confirm Buy Order",false,"Buy Order",gear,"Precursor Gear",19,403562.6));
    }
}
