package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.features.TradingMode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ForecastDecisionTest {
    private final long now = 1_000_000;
    private MarketAnalysisProtocol.Recommendation route(String kind, String input, double cost, double rank, boolean configured) {
        return new MarketAnalysisProtocol.Recommendation(kind, input, input, input, 1, 1, 100, cost, 1, rank, 3600, "ESTIMATED", configured, "capital", "current offer");
    }
    private MarketAnalysisProtocol.Report report(List<MarketAnalysisProtocol.Deferral> deferred, MarketAnalysisProtocol.Recommendation... rows) {
        return new MarketAnalysisProtocol.Report(now, now, now, true, "FRESH", "a".repeat(40), rows.length, Map.of(), List.of(rows),
                Map.of("minimum-margin", 1), deferred, new MarketAnalysisProtocol.Provenance(2, "coinsPerHour descending", "execution-history/1", 0, 0, 0));
    }

    @Test
    void explainsTheChosenRouteAndTheHigherRoutesItPassedOver() {
        var execution = report(List.of(new MarketAnalysisProtocol.Deferral("GENERAL", "SLIME", "minimum-margin")),
                route("GENERAL", "EXPENSIVE", 1000, 300, true), route("GENERAL", "RESEARCH", 10, 250, false), route("GENERAL", "COAL", 250, 100, true));
        var account = new PipelineAccount(now, TradingMode.BOTH, 700.0, 0, 0, 700, 30, 2, 2, Set.of(), true, null);
        var plan = PipelinePlanner.build(account, execution, now);
        var decision = ForecastDecision.explain(execution, execution, plan, now);
        assertEquals("COAL", decision.routeKey());
        assertEquals(3, decision.executionRank());
        assertEquals(List.of("EXPENSIVE", "RESEARCH"), decision.passedOver().stream().map(PipelinePlanner.Deferred::routeKey).toList());
        assertEquals("Insufficient spendable capital", decision.passedOver().getFirst().reason());
        assertEquals("SLIME", decision.notRanked().getFirst().routeKey());
        assertEquals("execution-history/1", decision.calibrationModel());
        assertTrue(decision.summary().contains("#3 of 3 executable"), decision.summary());
        assertTrue(decision.summary().contains("passed over 2 higher routes (Insufficient spendable capital)"), decision.summary());
        assertTrue(decision.summary().contains("uncalibrated estimate"), decision.summary());
    }

    @Test
    void noProposalMeansNoDecision() {
        var execution = report(List.of(), route("GENERAL", "COAL", 250, 100, false));
        var account = new PipelineAccount(now, TradingMode.BOTH, 700.0, 0, 0, 700, 30, 2, 2, Set.of(), true, null);
        assertNull(ForecastDecision.explain(execution, execution, PipelinePlanner.build(account, execution, now), now));
        assertNull(ForecastDecision.explain(null, null, null, now));
    }

    @Test
    void parsesFilterReasonsDeferralsAndProvenanceFromTheCalculator() {
        var c = MarketAnalysisProtocolTest.config();
        var request = MarketAnalysisProtocolTest.request(c, MarketAnalysisProtocolTest.NOW);
        var response = MarketAnalysisProtocolTest.response(MarketAnalysisProtocolTest.NOW);
        response.add("filterReasons", JsonParser.parseString("{\"minimum-margin\":2,\"enchanting-level\":1}"));
        response.add("deferred", JsonParser.parseString("[{\"kind\":\"BOOK\",\"routeKey\":\"ENCHANTMENT_OVERLOAD:4:5\",\"reason\":\"enchanting-level\"}]"));
        var forecast = new JsonObject();
        forecast.addProperty("contract", 2); forecast.addProperty("scoring", "coinsPerHour descending");
        forecast.addProperty("quoteAt", MarketAnalysisProtocolTest.NOW); forecast.addProperty("historyAt", MarketAnalysisProtocolTest.NOW);
        forecast.addProperty("historyStatus", "FRESH");
        forecast.add("calibration", JsonParser.parseString("{\"model\":\"execution-history/1\",\"calibratedRows\":0,\"personalSamples\":0,\"sharedSamples\":0}"));
        response.add("forecast", forecast);
        var parsed = MarketAnalysisProtocol.parse(response, request, MarketAnalysisProtocolTest.NOW);
        assertEquals(Map.of("minimum-margin", 2, "enchanting-level", 1), parsed.filterReasons());
        assertEquals("enchanting-level", parsed.deferred().getFirst().reason());
        assertEquals(2, parsed.provenance().contract());
        assertEquals("execution-history/1", parsed.provenance().calibrationModel());

        forecast.addProperty("quoteAt", MarketAnalysisProtocolTest.NOW - 1);
        assertThrows(IllegalArgumentException.class, () -> MarketAnalysisProtocol.parse(response, request, MarketAnalysisProtocolTest.NOW));
        response.remove("forecast");
        response.add("filterReasons", JsonParser.parseString("{\"Not A Reason\":1}"));
        assertThrows(IllegalArgumentException.class, () -> MarketAnalysisProtocol.parse(response, request, MarketAnalysisProtocolTest.NOW));
    }

    @Test
    void olderCalculatorsWithoutExplanationsStillParse() {
        var c = MarketAnalysisProtocolTest.config();
        var parsed = MarketAnalysisProtocol.parse(MarketAnalysisProtocolTest.response(MarketAnalysisProtocolTest.NOW),
                MarketAnalysisProtocolTest.request(c, MarketAnalysisProtocolTest.NOW), MarketAnalysisProtocolTest.NOW);
        assertTrue(parsed.filterReasons().isEmpty());
        assertEquals(MarketAnalysisProtocol.Provenance.LEGACY, parsed.provenance());
        assertEquals(1, parsed.withRows(List.of()).total());
    }
}
