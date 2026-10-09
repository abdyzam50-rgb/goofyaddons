# Craft prerequisite lookup and full-stack placement

Work is based on `claude/project-thread-2bdbsg` at `a423fa1`, including its crafting
menu settle delay and instant-sale navigation fixes.

## Profile lookup

The mod uses the website's existing profile service: bundled companion
`/v1/profiles` → Cloudflare Worker `/v1/profiles` → Hypixel profile API. It fetches
while the player is in-world, even before trading starts or Your Skills is opened.
A single fresh result supplies both skills and collections, slayers, faction
reputation and Heart of the Mountain. Evidence expires after five minutes and is
cleared on account/profile changes. The current server-observed profile name must
match exactly one API profile; a cached `selected` flag alone is insufficient.
Live skill observations override API estimates and conflicting published levels
prevent importing unlocks. Missing or unpublished progression remains unknown.

The website response now includes the nine standard XP-based skills used by
craft recipes. This Worker change must be deployed to expose those extra skills;
the mod also works with the previous response, retaining unknowns for omitted
skills. The Hypixel API key remains in the Worker secret.

A successful fresh Enchanting observation from that profile skips the startup
skills GUI. If the profile service fails, the existing bounded skills visit is
the fallback. Craft procurement checks requirements before buying components,
waits while lookup is in progress, and blocks unavailable requirements. Recipe
selection skips alternative recipes whose prerequisites are unverified.

## Crafting input

Inventory pickup uses a left click and prefers the largest available stack,
including a full 64 when available. For a 32-item cell, a carried 64 is placed in
that grid cell and right-clicked there to pick up half. The cell retains its
required 32; the other 32 goes to the next matching recipe cell or returns to the
original inventory slot. Partial stacks and other recipe counts retain exact
placement. Only the verified grid result in slot 23 is shift-clicked.

Each step retains the existing acknowledgement/settle delays, conservation check,
and bounded identical-state retries. An unexpected split, changed owner/container
or missing items blocks the operation with grid and cursor retained.

## Verification

Regression coverage checks profile identity without a skills-GUI visit, stale or
conflicting profile data, unpublished levels, buying only after prerequisites,
full-stack pickup/grid splitting, five 32-item cells, and an ignored split click.
Live Minecraft validation remains necessary for this branch's recipe menus.

Validation passed: 968 Java tests (110 foundation, 851 client, 7 calculator
integration) and 99 Node tests. Built client: `0.2.17-BETA`.

## Duplicate Bazaar result names (0.2.18)

Buy and sell search select the unique matching product ID before considering
display names. A readable conflicting ID rejects that result even if its name
matches. When a result lacks an ID, a unique exact name remains supported;
multiple unresolved matches do not trigger a guessed click. Product-page checks
retain that identity decision, including truncated titles. Regression scenarios
cover Gold Ingot versus Enchanted Gold Ingot in either result order, missing IDs,
ambiguous IDs, inventory exclusion, and a conflicting product page.

## Repeated grid splitting and fast pacing (0.2.20)

Owned grid stacks can be halved repeatedly, such as 64 → 32 → 16 → 8. Halves are
placed into another matching ingredient cell even when a different ingredient
appears earlier in the recipe. Oversized cells are accepted only when this
executor placed them for splitting and their count can be halved exactly to the
required count. Arbitrary quantities retain exact one-item placement where a
half-stack operation cannot fit. Unneeded halves return to the original inventory
slot; no ingredient is dropped.

Normal crafting uses a 100 ms interval from the previous click after observing
the owned cursor/grid/inventory change. Crafting no longer adds the randomized
trader delay, and observation latency counts toward that interval. Every tick
keeps conservation and ownership guards in place. Missing acknowledgements still
wait, with unchanged bounded retries. A server slowdown message pauses clicks
and retries for one second and raises subsequent intervals by 100 ms, up to
500 ms. The backed-off interval carries over into later batches.

The catalog Enchanted Eye of Ender regression fills the four 16-powder cells from
one 64 stack, places 16 enchanted pearls and shift-clicks the verified result in
11 clicks, approximately 1.1 seconds in a menu that acknowledges immediately.
This is simulation evidence, not a live latency guarantee. Regression tests also
cover returning 48 unused units from a single 16-unit cell, cooldown protection,
and refusing unowned oversized cells. The full build passed 981 Java tests.

## Normal Blaze Powder and transient profile connections (0.2.21)

The bundled `crafting:BLAZE_POWDER:0` recipe uses one normal `BLAZE_ROD`
and produces two `BLAZE_POWDER`, with no account prerequisite. It is separate
from enchanted Blaze Powder. The executor regression uses a full 64-rod stack,
returns the remaining 63 rods and shift-clicks the two verified powder into
inventory. This recipe was already present; no duplicate catalog entry is added.

To craft with existing rods: `.a* goofyaddon craft BLAZE_POWDER 16` produces
32 powder over 16 batches. Toggle trading on to run the queued craft. To buy the
missing rod, craft two powder and instant-sell them as a test, use
`.a* goofyaddon production test BLAZE_POWDER`.

The supplied October 9 diagnostics show a connection failure at 13:36:06 UTC,
a verified profile at 13:37:06 UTC, a completed Eye of Ender craft at 13:37:12
and a finished production run at 13:37:14. The calculator was ready at export.
This establishes automatic recovery, rather than another persistent API denial.
The original exception details were not recorded, so the exact cause of the
initial connection failure cannot be proved from that bundle.

Local connection failures and timeouts now retry after 5, 15, 30 and then 60
seconds, capped at 60. Successful responses reset the backoff. Known permission,
rate-limit and invalid-response failures keep the normal retry interval; no
unknown collection level is treated as an unlock. Messages distinguish a local
calculator connection failure, timeout and invalid response without displaying
exception bodies or credentials. Profile changes still cancel stale requests.

## Preparing basic ingredients before the final craft (0.2.22)

Production procurement expands three basic intermediates: `BLAZE_POWDER`,
`STICK`, and `WOOD` (oak planks). Missing powder is made from Blaze Rods;
missing sticks are made from planks, and missing planks are made from logs.
Other inputs retain their existing procurement behavior. This is a bounded
vanilla preparation system, not arbitrary recursive SkyBlock recipe execution.

The planner uses recipe yields and existing inventory, rounds batches upward,
shares preparation surplus and reserves ingredients already held for the final
recipe. With no powder, one Enchanted Eye of Ender requires buying 32 Blaze
Rods instead of 64 powder, plus any missing 16 Enchanted Ender Pearls. Five
missing sticks require one log, one plank batch and two stick batches (eight
sticks produced). Already-held intermediates and base inputs reduce purchases.

Variant cost selection uses expanded base purchase quotes. Products used by
trader positions are rejected, and the run locks the complete ingredient chain
against new trader selections. Missing base quotes or insufficient spendable
capital block purchases. With automatic buying disabled, held base inputs can
still be crafted; missing base inputs must be supplied by the player.

Once base inputs are acquired, preparation runs through the existing crafting
executor and production journal, at most 16 batches per child job. The parent
records preparation intent before queueing each craft. The run waits for that
child's verified completion and replans from actual inventory, rather than
assuming all projected output exists. Failed or uncertain preparation requires
review and cannot advance to the final craft or sale. Restart/stop follows the
existing production recovery rules; interrupted preparation is marked for review.

Regression coverage checks rods procurement without a powder quote, log/plank/
stick ordering and yield rounding, held input accounting, occupied base inputs,
32 powder batches across two child jobs and failure before the final craft.
Live Minecraft validation remains necessary.
