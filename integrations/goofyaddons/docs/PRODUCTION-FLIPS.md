# Production flipping: first implementation stage

This stage adds **inventory-backed auto-crafting** and the transaction foundations
for Forge, Kat and BIN-only Auction House trading. Mod 1.3.57 also connects
opt-in listings of existing inventory to the live menu scheduler; see
[AUCTION-HOUSE.md](AUCTION-HOUSE.md). It does **not** yet enable an
automatic buy-components → process → sell loop for those routes. Existing book
and general trading modes keep their current selection behavior.

## Auto-crafting from existing inventory

Use mod 1.3.57 with companion 1.3.53. Keep your config and saved trading files.

1. Stop trading with K and resolve any retained order/config recovery errors.
2. Put the ingredients in ordinary inventory and keep at least one empty slot.
3. Run `.a* goofyaddon production recipes ENCHANTED_COAL_BLOCK` to inspect catalog recipes.
4. Run `.a* goofyaddon craft ENCHANTED_COAL_BLOCK` or append a batch count, such as
   `.a* goofyaddon craft ENCHANTED_COAL_BLOCK 2`. A batch is one recipe execution;
   its output count can exceed one. The limit is 16 batches.
5. Close unrelated menus and press J. The crafter opens SkyBlock's `/craft` menu,
   places exact ingredient quantities, then collects only the matching output.
6. Read `.a* goofyaddon production jobs` for persisted job state. Output remains in
   inventory; this stage does not automatically sell it.

Recipes are chosen from the catalog by output ID and available ingredients.
An alternate recipe is usable when all its ingredients are present. Ingredients
or outputs reserved by an existing trader position cannot be adopted for crafting.
J also starts the configured book/general mode as usual. The crafter takes menu
priority while its queued operation runs, then the ordinary traders continue.
The crafter uses the same menu scheduler as the traders, preventing concurrent
clicks. K also cancels a queued craft before J starts it.

A separate packet-fed inventory snapshot prevents Minecraft's local click
predictions from counting as successful server actions. Grid, cursor and inventory
must agree before another action. Half-stack splits reduce clicks when applicable.
An unchanged action can retry only against fresh, unchanged server observations;
missing acknowledgements stop the operation. A short packet mismatch can settle;
persistent mismatches stop for review. Unknown recipe variants are not guessed.

Verified completed batches are saved after each craft. A crash or interruption
while a cursor/grid operation is pending requires review instead of replaying it.
An output in inventory is not a completed sale or a profit receipt.

## Catalog and saved state

The bundled catalog contains 2,259 crafting recipes, 68 Forge recipes and 210 Kat
upgrades from a pinned MIT-licensed NEU item repository. Invalid/unsupported
variants, quantities and durations are rejected during extraction. Catalog facts
do not prove this account's collection, HotM or workstation unlocks.

Source:
https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/tree/02663f51abf7d93cbd109c203c3fe259c381c00d

Jobs are stored in the Minecraft instance's
`config/goofyaddons-production-jobs.json`, alongside the other persistent mod
records. Replacing the mod JAR does not replace this file. Keep it when updating.
Do not replace an entire Minecraft instance/config folder to update the mod.

The journal stores recipe, account, completed batches, workstation slot, observed
timers, cost basis when known, pet UUID and auction UUID when applicable. It saves
an intent before an irreversible action. Interrupted submissions, purchases,
claims and listings remain uncertain and require live reconciliation. A ready
clock is a prediction; a claim needs the actual completed GUI and inventory proof.

## Forge, Kat and Auction House groundwork

These components are implemented and tested through injected menu/server
observations, but **are not connected to automatic live route selection yet**:

- Forge navigation searches recognized categories/pages and stops at a matching
  confirmation. Input/output quantities must match the catalog.
- Forge/Kat transaction state machines persist submission/claim intent, verify
  consumed inputs and coin debits, track observed timers, and verify output arrival.
- Kat checks the exact pet UUID, type, rarity, XP, held item, skin and candy state.
  Actual quoted fees can be lower than the catalog's baseline cost.
- BIN purchase navigation targets an exact auction UUID. Ordinary bidding menus,
  stale quotes, changed identities/prices and budget overruns are rejected.
  Purchase proof needs the item in inventory and the matching purse debit.
- BIN listing navigation/publication is now connected for explicit existing-inventory
  jobs. It verifies a new seller listing and the quoted fee debit. Automatic route
  selection and completed-sale settlement remain pending.
- Production economics use real Bazaar depth for input purchases. Insufficient
  depth, insufficient inventory space, excluded products and stale markets are
  filtered. These proposals remain research-only, not executable recommendations.

GUI facts were cross-checked against public NEU client source at commit
`9b1fcfebc646e9fb69f99006327faa3e734e5f51`; this is not validation of current live
Hypixel menus. The requested wiki GUI references are currently blocked by the
cloud environment domain allowlist. A settings draft adds `wiki.hypixel.net` and
`hypixelskyblock.minecraft.wiki` so that verification can continue.

## Remaining work before full automatic production flips

- Verify current wiki GUI layouts and confirmation/receipt formats.
- Add automatic component acquisition and connect crafted output to verified
  Bazaar offers or BIN listings with complete fee/cost accounting.
- Add fresh AH listing data, pet-specific comparisons and route ranking.
- Read collection/HotM/profile unlocks before funding an unfamiliar recipe.
- Connect timed jobs to menu scheduling, occupied slot limits, capital/inventory
  ownership and safe recovery across sessions/profiles.
- Connect physical Forge/Kat/AH access to the forthcoming custom pathfinder.

The physical anvil/storage and wandering work from the previous stage remains
noted separately in [BAZAAR-ACCESS.md](BAZAAR-ACCESS.md).
