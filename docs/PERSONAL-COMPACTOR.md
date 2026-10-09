# Personal Compactor detection and bulk implementation boundary (0.2.25)

## Implemented

Detection uses exact item IDs for the 4000, 5000, 6000 and 7000 tiers; capacities
are 1, 3, 7 and 12 respectively. A 7000 does not need a configured tier setting.
The live adapter captures only the device's UUID, `personal_compact_0` through
its last slot, and `PERSONAL_DELETOR_ACTIVE` (the activation key used for both
Personal Compactors and Deletors). Deletors and Super Compactors are not accepted
as Personal Compactors. Missing/invalid activation remains unknown.

Source of the field/capacity facts: public SkyHanni revision
[e8cc2d1](https://github.com/hannibal002/SkyHanni/tree/e8cc2d141a3bdef2cbf8f2efb79f0f305bc8d513),
`PersonalCompactorOverlay.kt` and `SkyBlockItemModifierUtils.kt`. No reference
implementation is copied. The official wiki could not be reached from this
workspace; these source facts do not establish live GUI add/remove controls.

Ordinary inventory and the currently open Accessory Bag page are inspected.
Auction previews are not owned devices. Closed bag pages and ender storage are
not automatically searched. Detection checks all visible devices, including a
lower-tier active one when a higher-tier device is disabled.

Production stops before a purchase if a visible active compactor can transform
required inputs or the intended output. Manual grid crafting also checks its
inputs before clicking. Unknown activation/configuration or an unknown active
recipe cannot authorize raw-input procurement. This check is conservative and
does not assume the current inventory is below the compaction threshold.
It prevents treating server compaction as a missing-item/failed-purchase receipt.

`.a* goofyaddon production compactor` reports visible devices and activation.
Diagnostics include a count, highest observed tier/capacity and readiness status;
raw NBT, device UUIDs and configured recipe IDs are not added to shared telemetry.

Open the compactor menu and then run
`.a* goofyaddon production compactor inspect`. Its cached title, item/control
names, IDs and lore are saved to `config/goofyaddons-compactor-menu.json`; no raw
NBT, UUIDs or credentials are captured. The cache clears on leaving the world.

## Still pending; automatic bulk compaction is disabled

This release does not add/remove compactor recipes, toggle a compactor, or buy
multiple inventories of raw materials for compaction. `automaticBulkEnabled`
is explicitly false. Existing compactor settings are never changed by this code.
A live menu capture is needed to verify the controls before implementing that
adapter. Existing Bazaar purchase receipts also need to accept a verified
raw-input-to-output conversion, rather than demanding all purchased units remain
raw in inventory. Turning on an already configured compactor and buying as usual
would violate that transaction contract.

The bulk implementation must:

1. Identify the exact device UUID and snapshot its original recipes/activation.
2. Persist account/profile-scoped configuration intent before any GUI mutation.
3. Verify support, prerequisites and the configured output in live device data.
4. Buy only a batch that fits inventory capacity; reconcile inputs, output yield,
   leftovers and the purse debit before another batch. Never assume conversion.
5. Remove each recipe added by that job and verify removal **before the next
   ordinary crafting stage**, so its ingredients cannot compact unexpectedly.
6. Preserve unrelated user recipes. Temporary changes to existing settings need
   their original values restored at the end of the complete production run.
7. Treat cleanup as required on finish, cancellation and graceful shutdown.
   Disconnect/crash leaves a durable cleanup obligation; reconcile before a new
   job. If another actor changed a leased slot, stop rather than erase its entry.

The detection, ownership, unknown-state and pre-purchase conflict tests pass.
Cleanup and bulk GUI behavior are not implemented or live-tested yet.

### Capture without closing the menu (0.2.26+)

Press **Debug export** (default **F7**) with the compactor GUI open. Share the ZIP
from `logs/goofyaddons/bundles/`; `current-menu.json` contains its visible controls
and descriptions. The key can be reassigned in **G → Macros → Keybinds**.
The focused inspect command remains available.
