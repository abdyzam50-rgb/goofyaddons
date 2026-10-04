# Validated anvil retries — 1.3.16-BETA

The uploaded `diagnostics-1791041079844-4b18314a.zip` extends 1.3.14 session
`971997f3-1037-4c40-9930-6802aec8402d`. Event 1102 reports a timeout in
COLLECTING for two Duplex III inputs. Those inputs have been consumed; native
Duplex IV remains in display slot 13, and slot 22 is named “Anvil” with lore
“Claim the result item above!”. Inventory has not received the output.
The preceding merge was acknowledged at 1094, demonstrating that the existing
slot-22 claim action does work when accepted. The stalled operation submitted
at 1097, claimed at 1098, then waited until its timeout without another attempt.
This is consistent with the user's reported rejected/throttled claim. The old
logger does not record the slowdown notice itself, so that message's exact
wording cannot be established from this bundle.

## Behavior

All four anvil steps now check for success before deciding whether to retry.
Slowdown messages provide a cooldown signal, never a failure acknowledgement.
The fixed inventory baselines and original model entries survive every attempt.
The same owner and container, a clear cursor, and exact native single-enchantment
book identities are required throughout.

- First input: both input slots empty, original input/output inventory counts,
  and the originally clicked inventory slot still contains the expected book.
- Second input: the first book remains in slot 29, slot 33 is empty, inventory
  has exactly one fewer input, and the second click's original slot still holds
  its expected book.
- Combine: both expected inputs remain in 29/33, original total input count,
  no extra output in inventory, correct preview in 13, and ready Combine Items
  button in 22. If inputs were consumed, this path cannot resubmit combination.
- Claim: input slots empty, total input count exactly two below baseline,
  no inventory output arrival, expected output still in 13, and the captured
  “Claim the result item above!” lore in 22.

Retry requires the entire menu contents to remain unchanged for 750ms, and
at least three seconds since the previous attempt. Changed/partial observations
and busy cursors restart this stability window. Recognized server-style slowdown
notices postpone clicks for at least 1.5 seconds after the latest notice. A new
notice restarts observation settling. Completion can still be acknowledged while
cooling down, without any new click. The initial claim also checks the captured
claim lore, so a delayed Combine Items button does not get clicked as a claim.

There are at most three retries for each step, and the existing 30-second total
operation deadline remains. Failure retains ownership; it does not declare a
missing item or repeat indefinitely. Retry never changes task quantities; only
verified inventory output arrival, verified input consumption and a clear cursor
acknowledge the merge. `books.anvil_slowdown` records the phase without logging
unrelated chat; `books.anvil_action_retried` records phase, attempt and snapshot.

## Validation and limits

The Gradle build passes with 306 tests, zero failures, errors or skips.
Regressions cover rejected first/second input moves, rejected combination,
rejected claim followed by arrival, success during cooldown, moved source slots,
busy cursors, stale button lore, repeated slowdown cooldowns, partial packet
updates and retry exhaustion. A complete sixteen-input replay performs all
fifteen merges and reaches the sale state after every one of its sixty steps
is rejected once. The ordinary successful replay also passes.

Stable client observations cannot prove the absence of an arbitrarily delayed
server packet. The cooldowns, exact pre-action requirements, bounded retries and
success-first acknowledgement reduce that risk without promising impossible
server synchronization. A live run of this new build is still needed.
This policy covers anvil inputs, combine and result collection; storage transfer
and monetary order submission retries are unchanged.
