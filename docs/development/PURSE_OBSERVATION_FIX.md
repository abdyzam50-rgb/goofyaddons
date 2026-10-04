# Purse observation repair — 1.3.10-BETA

Bundle `diagnostics-1791035595250-b61d224a.zip` extends session
`2262af27-94b2-45b9-8bae-bb4151232f8c` running 1.3.9-BETA.
At 13:50:57 UTC, event 1367 pauses with “Book purchase exceeds the available
capital/purse” in the buy-price menu. Its purse is -1, the unreadable sentinel.
Before entering menus it is 62,546,627; event 1369 reads 62,546,632 roughly
0.2 seconds after the pause closes the GUI. This is a failed observation,
not evidence that the account has insufficient coins. The bundle does not
show which scoreboard condition made that reading fail.

The price-selection check compared the order cost directly against -1, then
reported insufficient capital. It also independently re-read the purse for
the allocation check. Final confirmation had the same unknown-as-insufficient
classification through its `cost > purse` condition.

Both purchase boundaries now wait up to 10 seconds for a finite nonnegative
reading, without clicking purchase controls or using a previous balance.
Persistent failure pauses with an unreadable-purse explanation. A valid
reading still passes through the existing affordability, reserve, capital,
inventory and confirmation checks. Price selection uses one purse observation
for both cost and allocation checks.

Diagnostics distinguish absent player/world/sidebar, absent or unparseable
purse line, and multiple purse lines. `books.purse_observation_wait` records
the first failed observation in a wait episode, without exporting scoreboard
text or player names. These reasons will help determine whether a later failure
needs a scoreboard parsing or visibility repair.

Four tests cover the recorded known → unknown → known sequence, mixed invalid
readings with a fixed deadline, zero as a valid balance that still needs an
affordability check, and clearing the deadline between runs. Full build: 258
tests, zero failures. This patch has not been exercised in a live game.

The separate startup issue from the previous bundle remains open: missing-order
refreshes are per task and storage holdings trigger them. This purse fix does
not change startup order scanning.
