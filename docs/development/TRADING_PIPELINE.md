# Shared pipeline preview — stage 1, 1.3.31

The calculator ranked routes individually while each engine selected purchases
independently. PipelineAccount now provides one planning projection of observed
purse, committed and pending capital, spendable balance, conservative inventory
capacity, product exclusions and remaining book/general position slots. The
existing dashboard account payload includes observed inventory/storage and tracked
positions; uninspected storage and unrelated manual orders remain unknown.

PipelinePlanner ranks validated recommendations by expected coins/hour, then capital
and route key, and builds a deterministic greedy allocation across both engines.
Selected routes consume a single provisional budget, input capacity and their engine
slots. Unaffordable leaders cannot crowd out cheaper candidates. Product input,
output and enchantment family exclusions prevent overlapping proposals. Existing
stock/reservations, current mode, configured lists and active-position limits are
revalidated even while a calculator response is cached. Unconfigured discoveries
are deferred as research. This is a bounded preview of returned recommendations,
not a globally optimal market allocation. Rates are not added together.

Account projection only treats enchanted-book stacks as physical book exclusions;
armor enchantments no longer suppress otherwise eligible book recommendations.
The previous analysis request could exclude Wisdom solely because worn armor had
Ultimate Wisdom, despite no physical Wisdom books or tracked route being held.
Requests now use the same account projection as the planner.

Incomplete inventory, an occupied cursor, an unreadable purse, a settling purchase
or missing/expired forecasts produce no proposed queue. Four inventory slots are
reserved conservatively; each input is treated as potentially unstackable. This
preview does not forecast future frees of capital/inventory or assume storage is
available. Quotes expire after 60 seconds, independent of repeated account updates.
The dashboard suppresses plans on disconnect/stale account or missing forecast,
and clears them when its connection to the companion fails.

The Java planner has no GameActions or transport dependency and never calls capital
reservation methods or changes executable tasks/config. Reports include its plan
in diagnostics and opt-in local telemetry. The dashboard adds Planned next and
Deferred candidates, shows spendable/pending/preview/remaining capital and position/
inventory headroom, and labels estimates and advisory authority explicitly.

Run the updated 1.3.31 mod and companion with existing marketAnalysis.enabled and
dashboardEnabled true; start trading and open http://127.0.0.1:8789/. Keep the
companion data directory and all mod configuration/ownership/profit files.
No new execution setting is introduced. The actual traders still choose work.

Validation: 411 Java tests, 24 companion tests, separate Java-to-Node integration,
and Chromium checks at 1440px/390px with synthetic observations. Scenarios cover
aggregate budget/capacity, engine limits, affordable fallback, overlapping levels,
research candidates, stale forecasts, unknown inventory, armor versus books, and
executor reservation changes without another calculator request. Chromium checks
show the new queue/deferred reasons and remove it on stale account snapshots.
No live Minecraft trading or public-market access was validated in this environment.

Next stages: connect verified work states and persistent execution intents to this
shared planner; finish/collect existing work first; model capacity/capital release
without trusting predicted fills; then let the planner feed revalidated tasks to
the existing menu executors. Do not treat preview proposals as already reserved or
resume a saved preview blindly after restart.
