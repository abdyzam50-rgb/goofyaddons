package com.goofy.goofyaddons.menu;

import org.junit.jupiter.api.Test;
import java.util.List;
import static com.goofy.goofyaddons.menu.BedrockMenuRecovery.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockMenuRecoveryTest {
    private MenuSnapshot menu(boolean cursorEmpty,boolean player,String material) {
        var slot=new SlotView(10,player,10,false,"Buy instantly","Buy instantly",List.of(),null,null,1,1,
            new ItemMetadata(null,null,null,null,null,null,null,material));
        return new MenuSnapshot(42,"Blue Jay Shard → Instant Buy",cursorEmpty,List.of(slot));
    }
    @Test void waitForPopulationThenReopenInsteadOfAcceptingTheNamedBedrockButton() {
        var recovery=new BedrockMenuRecovery();var broken=menu(true,false,"minecraft:bedrock");
        assertEquals(WAITING,recovery.observe("buy",broken,0));
        assertEquals(WAITING,recovery.observe("buy",broken,1499));
        assertEquals(REOPEN,recovery.observe("buy",broken,1500));
        assertEquals(READY,recovery.observe("buy",menu(true,false,"minecraft:gold_ingot"),3000));
    }
    @Test void retriesStayBoundedAcrossHealthyIntermediateMenusAndMenuReopens() {
        var recovery=new BedrockMenuRecovery();var broken=menu(true,false,"minecraft:bedrock");
        for(int i=0;i<3;i++) {
            long at=i*5000;assertEquals(WAITING,recovery.observe("buy",broken,at));
            assertEquals(REOPEN,recovery.observe("buy",broken,at+1500));
            assertEquals(READY,recovery.observe("buy",null,at+2000));
            assertEquals(READY,recovery.observe("buy",menu(true,false,"minecraft:stone"),at+2500));
        }
        assertEquals(WAITING,recovery.observe("buy",broken,15000));
        assertEquals(EXHAUSTED,recovery.observe("buy",broken,16500));
        assertEquals(WAITING,recovery.observe("next-trade",broken,20000));
        assertEquals(REOPEN,recovery.observe("next-trade",broken,21500));
    }
    @Test void playerInventoryBedrockAndCursorHeldItemsDoNotAuthorizeClosing() {
        assertFalse(BedrockMenuRecovery.hasPlaceholder(menu(true,true,"minecraft:bedrock")));
        var recovery=new BedrockMenuRecovery();
        assertEquals(WAITING,recovery.observe("buy",menu(false,false,"minecraft:bedrock"),0));
        assertEquals(WAITING,recovery.observe("buy",menu(false,false,"minecraft:bedrock"),5000));
    }
}
