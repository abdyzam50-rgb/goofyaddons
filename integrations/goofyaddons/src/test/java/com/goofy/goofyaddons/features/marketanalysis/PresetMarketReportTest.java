package com.goofy.goofyaddons.features.marketanalysis;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PresetMarketReportTest {
    private static final long NOW=MarketAnalysisProtocolTest.NOW;
    @Test void disabledCalculatorUsesOnlyConfiguredPresetsAndDoesNotChangeSettings() {
        var cfg=MarketAnalysisProtocolTest.config();cfg.marketAnalysis.enabled=false;cfg.marketAnalysis.automaticSelection=false;
        var market=MarketAnalysisProtocolTest.market(NOW);
        var report=PresetMarketReport.build(cfg,market,10000,2048,Map.of(),NOW);
        assertFalse(report.rows().isEmpty());
        assertTrue(report.rows().stream().allMatch(r->r.inputId().equals("ENCHANTED_COAL")));
        cfg.general.items=List.of();cfg.books=List.of();
        assertTrue(PresetMarketReport.build(cfg,market,10000,2048,Map.of(),NOW).rows().isEmpty());
        assertFalse(cfg.marketAnalysis.enabled);assertFalse(cfg.marketAnalysis.automaticSelection);
    }
    @Test void customConfiguredItemCanExecuteWithoutAutomaticCatalogMembership() {
        var cfg=MarketAnalysisProtocolTest.config();cfg.books=List.of();
        cfg.general.items=List.of(new com.goofy.goofyaddons.features.generalflipper.GeneralItem("CUSTOM_TEST_ITEM","Custom test item"));
        var market=MarketAnalysisProtocolTest.market(NOW);var products=market.getAsJsonObject("products");
        products.add("CUSTOM_TEST_ITEM",products.get("ENCHANTED_COAL").deepCopy());
        var report=PresetMarketReport.build(cfg,market,10000,2048,Map.of(),NOW);
        assertFalse(AutomaticSelection.general(report,NOW,products,cfg.general,cfg.bazaarTaxPercentage,10000,2048,Set.of()).isEmpty());
    }
    @Test void localSelectionHonorsFundsInventoryProfitVolumeAndFreshness() {
        var cfg=MarketAnalysisProtocolTest.config();var market=MarketAnalysisProtocolTest.market(NOW);cfg.books=List.of();
        assertTrue(PresetMarketReport.build(cfg,market,0,2048,Map.of(),NOW).rows().isEmpty());
        assertTrue(PresetMarketReport.build(cfg,market,10000,0,Map.of(),NOW).rows().isEmpty());
        cfg.general.minProfitPerBatch=1000000;
        assertTrue(PresetMarketReport.build(cfg,market,10000,2048,Map.of(),NOW).rows().isEmpty());
        cfg.general.minProfitPerBatch=0;cfg.general.minWeeklyVolume=1000000;
        assertTrue(PresetMarketReport.build(cfg,market,10000,2048,Map.of(),NOW).rows().isEmpty());
        assertNull(PresetMarketReport.build(cfg,market,10000,2048,Map.of(),NOW+61000));
    }
}
