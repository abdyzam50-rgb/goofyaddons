# Book loop repair — 1.3.9-BETA

## Evidence from the uploaded diagnostics

Reviewed `diagnostics-1791026905808-da952d0d.zip`, focusing on session
`ec2a4ddd-55b5-44ee-9f87-b8a33da2c70c` on 2026-10-03 (1.3.8-BETA).
Other sessions and manual trading before the macro started are separate evidence.

At 11:22:31–11:22:35 UTC, events 679 and 681 shift two Overload I books into
anvil inputs. Events 684 and 687 click output slot 22. Event 689 shifts the third
Overload I into the anvil. Event 700 captures Overload II in inventory and the
remaining Overload I in slot 29, while the task still models three Overload I.
The merge happened, but bookkeeping missed it. Recovery then re-enters the
Bazaar order path, cancels the outstanding 13-input buy order and replaces it.
The later Duplex merges show the same stale input model. The run ultimately pauses.

The old combine counter was overwritten on each observation, including while
the cursor was busy. It tested a moving baseline instead of the inventory before
the operation, clicked output repeatedly, and could start another input before
acknowledging the previous merge. A regression reproduced the lost merge against
the old logic when output arrived before the cursor cleared. The bundle does not
capture every intermediate cursor/menu update, so that exact packet ordering is
a reproduction of the code defect, not an independently observed log fact.

## Changes

- `BookCombiner` owns one operation, its two model entries, container and fixed
  inventory baselines. Input clicks require acknowledgment; submission requires
  matching native input and output identities. Collection is issued once after
  input consumption. Two consumed inputs and one arrived output update the model
  before another merge can begin. Delayed packets cannot reset the baseline.
- Missing inputs pause with ownership retained. This path no longer cancels or
  replaces a buy order to repair a stale inventory model.
- `BookTransfer` updates location only after a matching source decrease and
  destination increase on the same storage page. Missing source items do not
  count as successful moves. It does not repeat a pending shift-click.
- Startup storage adoption and transfers verify `ec` / `ec N` against the actual
  `Ender Chest (N/total)` title. Backpack commands are currently unsupported and
  pause; they need captured page identity fixtures before enabling adoption.
- Buy claims enter the task model only after the exact inventory increase arrives.
  Pending claims and transfers prevent engine handoff. A still-visible sale order
  cannot cause repeated claim clicks or restart the receipt deadline.
- Completed combinations proceed to sale only with the expected final book.
  Partial batches wait for outstanding inputs or store their partial holdings.
  Unpaired stored inputs no longer cycle between retrieval and combining.
- Retrieval capacity counts stored books; insufficient capacity pauses rather
  than cycling through store/retrieve. Task creation honors `maxActiveBooks`.
- Diagnostics now include selected native IDs and enchantment levels for menu
  items, plus the pending combine phase and baselines.

## Validation and remaining work

New production-controller tests cover cursor delay, stale packets, context
replacement, lookalikes, timeouts, partial batches, source/destination arrival,
wrong storage pages and missing sources. A simulated anvil drives 16 level-I
inputs through all 15 merges to one level-V output and the sale state. This is
a combine sequence test, not a complete Bazaar engine transaction replay.

The full build and 254 tests pass. No live Minecraft session was run with this
patch. Native preview tags and the consumed-input/collect-output transition still
need verification on the server; unexpected formats retain ownership and pause.

The next substantial step remains migrating the rest of `BazaarFlipper` to the
menu/action/world seam and exercising buy → claim → store/retrieve → combine →
sell → claim → next route, including partial fills and restart recovery. The
current ownership journal is not a durable replay of every pending controller
operation. Instant routes remain blocked by the existing confirmation policy.
