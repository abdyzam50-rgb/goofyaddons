# Bazaar NPC access and account requirements — mod 1.3.55

Keep companion 1.3.53. Existing configs get the defaults below when loading;
no saved orders, inventory records or gameplay history need deleting.

Add or edit this top-level object in `config/goofyaddons.json`, then stop trading
and reload the config:

```json
"access": {
  "bazaarMode": "AUTO",
  "checkSkills": true,
  "navigationTimeoutSeconds": 90
}
```

## Access modes

- `AUTO` starts with the usual Bazaar commands. A server message rejecting a
  recent Bazaar command because of the Cookie Buff switches Bazaar access to the
  NPC for that server connection. Ordinary player chat cannot activate this switch.
- `COMMAND` keeps command access. Use it if you have a cookie and want that route.
- `NPC` always interacts with the physical Bazaar NPC, then uses its Search or
  Manage Orders controls. It does not send Bazaar-opening commands.

Without a pathfinder, **stand within three blocks of the visible Bazaar NPC in
Hub**, with a clear line of sight. If the NPC is out of reach, the trader pauses
with instructions instead of issuing movement guesses. It never buys a cookie.

NPC interaction is retried at most three times, two seconds apart. Search waits
for the sign and then a new Bazaar container; order navigation waits for the
orders container. Ignored Search/Manage Orders clicks can retry twice, only
while the same container still shows the exact reversible control. The whole request has a bounded timeout (15–180 seconds).
An occupied cursor or unrelated menu stops navigation. No engine gets to click
while NPC navigation owns the menu. Travel time remains part of the trade's
execution history, while menu watchdog clocks resume after navigation.
Stopping, pausing and safety checks cancel the pathfinder's movement.

**Scope:** this enables physical Bazaar access, not cookie-free access to every
book-flipping action. Books still use `/anvil` and configured storage commands.
Their physical alternatives need separate navigation and verification. Live
Hypixel NPC names and menu behavior still need a field test; automated tests
exercise the state machine with delayed server acknowledgements.

## Account checks

Each fresh start reads the Skills menu in one visit. Recognized numeric and
Roman levels are retained for that run. Missing or conflicting levels remain
unknown. The check retries at most three commands and ends after 25 seconds;
it closes only a Skills menu, leaving unrelated menus untouched.

Before opening a new buy order, both traders examine the verified product's buy
control for requirements. Anvil combination checks the Combine Items control
before submitting. Confirmed unmet skill requirements or explicit server unlock
restrictions block that action and report the reason. Buy restrictions skip the
unfunded route. A combine restriction retains the inputs and pauses for review,
since those books may already be physically inside the anvil; its output route
is excluded from future purchases.

Enchantment **application** requirements on an item's descriptive lore are not
assumed to prohibit buying or combining it. Market quotes cannot prove account
unlocks. There is no invented enchantment-level catalog: a requirement first
revealed by an anvil control can only be discovered after inputs are available.
A complete upfront book/craft/forge prerequisite catalog is still future work.

Learned restrictions persist in `config/goofyaddons-bazaar-access.json`. On the
next fresh start, a recorded numeric restriction clears if the observed account
level now meets it. An exclusion based on an unreadable skill gets another
chance next session. Explicit unlock exclusions and the existing Garden Mutation
category exclusions remain. Existing positions can still be sold/settled.
Setting `checkSkills` to false disables the startup visit, but keeps checks on
live action controls; unknown skill levels do not count as level zero.

## Custom pathfinder integration

The adapter is `HubPathfinder` in `features.access`. Register it on the Minecraft
client thread with `BazaarNpcAccess.installPathfinder(provider)`.

- `goTo("BAZAAR")` starts movement in the current Hub and returns whether it
  accepted the destination.
- `active()` stays true until arrival or failure.
- `stop()` immediately releases all movement inputs owned by this provider.

The provider must not click menus, issue trading commands or keep moving after
`stop()`. World changes already pause the trader. Its source and Minecraft
26.1.2 integration are still needed before automatic walking is available.

## Next execution stages

The custom pathfinder is also needed for wandering and physical anvil/storage
access. Wandering must yield to transactions and stop before any menu owner
acts. Background flipping alongside another macro needs that macro to cooperate
with the same menu and movement ownership rules.

Long-duration routes need explicit holding limits, slow fill expectations and
separate capital allocations, while preserving stale-order recovery. Crafting
needs verified recipes, unlocks, inventory reservations and output confirmation.
Forge jobs additionally need persisted timers, slot ownership, confirmed
submission and claim recovery across restarts. These modes are not executors in
this release; they must not be presented as executable calculator routes yet.

## Release validation

Mod artifact: `dist/goofyaddons-1.3.55-BETA.jar`

SHA-256: `918fe33c6d4213f1ae631d465733ac678c47f8b37b4516bf7baac3a0f126f5a1`

518 Java tests and one real Java-to-Node calculator integration test passed.
The release build passed; no live Hypixel session was available for validation.
