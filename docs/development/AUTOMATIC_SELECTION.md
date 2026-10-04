# Automatic selection — 1.3.38

`marketAnalysis.automaticSelection` is an explicit opt-in and requires analysis
enabled. Old configs stay manual. The protocol's existing `configured` marker now
means execution eligible under the request's selection policy: manual-list
membership in manual mode, locally supported discovery in automatic mode. Java
independently recomputes that marker rather than trusting the companion's claim.

The shared static catalog contains native product display names from the cited NEU
revision and supported zero-XP book level pairs derived from the bundled engine.
Unknown names, mutation crops and unsupported combines cannot become automatic
trader inputs. The companion retains its current-market warning, entry, inventory,
budget and order-flow filters. Explicit unmet GUI requirements discovered by the
general trader persist through the existing instance-local exclusion registry.

Before new entry selection, the observer rebuilds PipelinePlanner from current
purse, inventory, reservations, exclusions and engine slots. Traders receive only
the first eligible proposal. Reserving the product removes it from the next plan.
General selection converts that proposal to a narrow copy of GeneralSettings,
using the proposed batch as an upper bound and rechecking live quote profitability,
volume, capital and inventory. Book selection independently runs FlipCalculator
on the native supported route with instant percentages zero. Both paths then use
the original order intent, price confirmation, receipt and journal verification.
Selection never rewrites config lists or clears ownership records.

Book FETCHING/needsMenu use automatic proposals instead of waiting for the manual
calculator's list, including when the manual lists are empty. Its recovery path
continues using saved journal route identities. Missing/stale calculator reports
block new automatic entries; retained positions still get normal maintenance.
This is a greedy next-entry pipeline with existing fixed position limits, not a
persistent multi-step execution queue or a globally optimal portfolio allocator.

Gameplay calibration uses at least ten eligible whole cycles for the exact route
and batch in 24 hours. Its p75 duration adjusts physical throughput both upward
and downward. The raw market-duration/observed-duration multiplier is bounded to
0.5–1.5, with weight min(1, samples/30). Current market spreads remain unchanged;
the adjusted rate is applied before ranking. Evidence survives the strict Java
bridge and the dashboard shows market-only, adjusted and observed net route rates.
Observed net rate is sum(profit)/sum(cycle duration), not portfolio wall-clock
earnings. Partial, paused, recovered or unknown-basis executions do not qualify.

Checks cover empty-list eligibility, native names and combine limits, independent
entry validation, current reservation and freshness invalidation, two complete
automatic general cycles, Java-to-Node compatibility, calibration directions and
weighting, and desktop/mobile automatic scope/evidence rendering. A real Minecraft
server session remains necessary to validate GUI layouts and realized throughput.
