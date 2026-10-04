# Adaptive gameplay rankings and recent volume (1.3.46)

The earlier calibration waited for ten completed cycles with exactly the same
batch size and never reduced throughput below half its market estimate. It
ignored active positions and deliberately retired routes. Consequently slow
routes could remain optimistic while waiting to collect enough successful data.

This update:

- Applies downside after three confirmed cycles, using normalized per-output
  duration for matching route IDs and recipe ratios. Ten slow cycles carry full
  weight. Upside still needs ten cycles, is weighted through thirty, and cannot
  exceed 1.5 or current market volume. Timing factors bottom out at 0.1.
- Sends session-only open execution observations through the existing opt-in local
  account feed. A route watched for at least three minutes and overdue against its
  forecast supplies a lower bound. Observations expire after fifteen seconds.
- Persists continuously watched book retirement as a distinct duration lower bound,
  excluding interrupted sessions. It never pretends cleanup completed a successful
  cycle, and cannot increase estimated throughput or confirmed profit.
- Captures expected net batch profit at verified purchase. Three confirmed known
  outcomes can reduce future estimates when realized profit repeatedly falls short
  of each original forecast; ten carry full weight. This factor never boosts
  current margins. Legacy samples lack expected profit and remain timing-only.
- Derives recent buy/sell trade rates from valid sampled intervals in the last day.
  The collector excludes gaps, rollover and cancellation-only book changes. After
  one hour, a two-hour weekly prior smooths each side before conservatively capping
  the engine's supply/demand forecast. Stale or insufficient coverage falls back.
- Ranks recommendations before report truncation using adjusted coins/hour, while
  preserving current quotes, raw market profit, tax and transaction checks. The
  dashboard exposes both raw and adjusted profit plus timing and volume evidence.

Daily-equivalent observed rates and weekly-average daily volume are explicitly
separate; sparse sampling cannot establish exact 24-hour volume. All observations
remain account-local or in the companion's external user-data directory. Completed
history remains readable across upgrades; open session timers are never resumed
through offline time. Older sample formats remain accepted.

Regression checks cover early downside, batch normalization, bounded upside,
active expiry, retirement persistence, pause exclusion, expected-versus-realized
profit, daily-volume reordering and stale fallbacks, protocol arithmetic/coverage,
HTTP telemetry, plus real desktop/mobile dashboard rendering. The build and
calculator integration check pass. Live gameplay validation is still needed.

Install both matching 1.3.46 artifacts and restart Minecraft and the companion.
Keep configs, order journals, execution history and the companion user-data folder.
Gameplay telemetry requires `marketAnalysis.dashboardEnabled`; no config reset is
needed. The revised ranking affects subsequent entries; retained positions still
finish or exit through the existing transaction and stale-book policies.
