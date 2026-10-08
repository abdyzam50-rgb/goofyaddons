package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.features.generalflipper.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticSelectionTest {
    private static final long NOW=MarketAnalysisProtocolTest.NOW;
    private MarketAnalysisProtocol.Report report(String kind,String input,String output,int units,int batch) {
        return new MarketAnalysisProtocol.Report(NOW,NOW,NOW,true,"FRESH","a".repeat(40),1,Map.of(),List.of(
                new MarketAnalysisProtocol.Recommendation(kind,input,input,output,units,batch,100,1000,10,10000,3600,"ESTIMATED",true,"fills","current offer")));
    }
    @Test void catalogUsesActualGuiNamesAndOnlySupportedCombiningRoutes() {
        assertEquals("L.A.S.R.'s Eye",AutomaticSelection.generalItem("GIANT_FRAGMENT_LASER").name());
        assertNull(AutomaticSelection.generalItem("CHORUS_FRUIT"));assertNull(AutomaticSelection.generalItem("SYNTHETIC_UNKNOWN"));
        var b=AutomaticSelection.book("ENCHANTMENT_SMARTY_PANTS_1","ENCHANTMENT_SMARTY_PANTS_5");
        assertEquals("Smarty Pants",b.name());assertEquals(16,b.getQtyAmount(b.level()));
        assertNull(AutomaticSelection.book("ENCHANTMENT_COMPACT_1","ENCHANTMENT_COMPACT_2"));
        assertNull(AutomaticSelection.book("ENCHANTMENT_SMARTY_PANTS_1","ENCHANTMENT_SMARTY_PANTS_6"));
    }
    @Test void automaticGeneralCandidatesWorkWithoutListsAndRecheckLiveLimits() {
        var cfg=MarketAnalysisProtocolTest.config();cfg.general.items=List.of();
        var products=MarketAnalysisProtocolTest.market(NOW).getAsJsonObject("products");
        var r=report("GENERAL","ENCHANTED_COAL","ENCHANTED_COAL",4,4);
        var a=AutomaticSelection.general(r,NOW,products,cfg.general,1.25,10000,32,Set.of());
        assertEquals(1,a.size());assertEquals(4,a.getFirst().quantity());assertTrue(cfg.general.items.isEmpty());
        assertTrue(AutomaticSelection.general(r,NOW+61000,products,cfg.general,1.25,10000,32,Set.of()).isEmpty());
        assertTrue(AutomaticSelection.general(r,NOW,products,cfg.general,1.25,10000,32,Set.of("ENCHANTED_COAL")).isEmpty());
        cfg.general.minProfitPerBatch=1e6;
        assertTrue(AutomaticSelection.general(r,NOW,products,cfg.general,1.25,10000,32,Set.of()).isEmpty());
    }
    @Test void automaticBooksUseLiveProfitAndStayOrderOnly() {
        var products=MarketAnalysisProtocolTest.market(NOW).getAsJsonObject("products");
        var r=report("BOOK","ENCHANTMENT_OVERLOAD_4","ENCHANTMENT_OVERLOAD_5",2,1);
        products.getAsJsonObject("ENCHANTMENT_OVERLOAD_5").getAsJsonArray("buy_summary").get(0).getAsJsonObject().addProperty("pricePerUnit",400);
        var items=AutomaticSelection.books(r,NOW,products,1.25,0,Map.of("enchanting",33));
        assertTrue(AutomaticSelection.books(r,NOW,products,1.25,0,Map.of("enchanting",32)).isEmpty());
        assertTrue(AutomaticSelection.books(r,NOW,products,1.25,0).isEmpty());
        assertEquals(1,items.size());assertFalse(items.getFirst().instaBuy());assertFalse(items.getFirst().instaSell());
        assertTrue(AutomaticSelection.books(r,NOW,products,1.25,1e6,Map.of("enchanting",33)).isEmpty());
        assertTrue(AutomaticSelection.books(r,NOW+61000,products,1.25,0,Map.of("enchanting",33)).isEmpty());
    }
}
