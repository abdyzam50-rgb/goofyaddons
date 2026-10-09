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
