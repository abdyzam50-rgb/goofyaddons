package com.goofy.goofyaddons.features.marketanalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Why automatic selection handed a route to an engine.
 *
 * <p>The forecast, the allocation preview and the engine's own live checks are separate
 * steps. This record ties them together for one decision: the chosen route's score and
 * rank, the forecast provenance it came from, which higher-scored routes were passed over
 * and why, and which wanted routes the calculator did not rank at all. It explains a
 * decision; it does not authorize anything, and the engine still applies every check.
 */
public record ForecastDecision(long at, String kind, String routeKey, int rank, int executionRank, double coinsPerHour,
                               String confidence, String historyStatus, long quoteAt, String calibrationModel,
                               int calibrationSamples, List<PipelinePlanner.Deferred> passedOver,
                               List<MarketAnalysisProtocol.Deferral> notRanked, String summary) {
    private static final int SHOWN = 5;

    /** Explains the plan's first proposal, or returns null when the plan proposes nothing. */
    public static ForecastDecision explain(MarketAnalysisProtocol.Report execution, MarketAnalysisProtocol.Report ranking,
                                           PipelinePlanner.Plan plan, long now) {
        if (execution == null || plan == null || plan.next().isEmpty()) return null;
        var chosen = plan.next().getFirst().route();
        int executionRank = indexOf(execution, chosen) + 1;
        int rank = ranking == null ? 0 : indexOf(ranking, chosen) + 1;
        var above = new java.util.HashSet<String>();
        for (var row : execution.rows()) {
            if (row.kind().equals(chosen.kind()) && row.routeKey().equals(chosen.routeKey())) break;
            above.add(row.kind() + ":" + row.routeKey());
        }
        var passedOver = new ArrayList<PipelinePlanner.Deferred>();
        for (var deferred : plan.deferred()) {
            if (passedOver.size() >= SHOWN) break;
            if (above.contains("BOOK:" + deferred.routeKey()) || above.contains("GENERAL:" + deferred.routeKey())) passedOver.add(deferred);
        }
        var notRanked = execution.deferred().stream().limit(SHOWN).toList();
        var provenance = execution.provenance();
        var evidence = chosen.executionEvidence();
        int samples = evidence.get("samples") instanceof Number n ? n.intValue() : 0;
        int shared = evidence.get("sharedSamples") instanceof Number n ? n.intValue() : 0;
        String summary = String.format(java.util.Locale.ROOT, "%s %s: %,.0f coins/h, #%d of %d executable%s; %s history, %s",
                chosen.kind(), chosen.routeKey(), chosen.coinsPerHour(), executionRank, execution.rows().size(),
                rank > 0 ? ", #" + rank + " overall" : "", execution.historyStatus().toLowerCase(java.util.Locale.ROOT),
                samples + shared > 0 ? "calibrated by " + samples + " personal and " + shared + " shared trades" : "uncalibrated estimate")
                + (passedOver.isEmpty() ? "" : "; passed over " + passedOver.size() + " higher route" + (passedOver.size() == 1 ? "" : "s")
                + " (" + passedOver.getFirst().reason() + ")");
        return new ForecastDecision(now, chosen.kind(), chosen.routeKey(), rank, executionRank, chosen.coinsPerHour(),
                chosen.confidence(), execution.historyStatus(), execution.marketAt(), provenance.calibrationModel(), samples + shared,
                List.copyOf(passedOver), notRanked, summary);
    }

    private static int indexOf(MarketAnalysisProtocol.Report report, MarketAnalysisProtocol.Recommendation route) {
        for (int i = 0; i < report.rows().size(); i++) {
            var row = report.rows().get(i);
            if (row.kind().equals(route.kind()) && row.routeKey().equals(route.routeKey())) return i;
        }
        return -1;
    }
}
