# Public calculator — visitors install nothing

The calculator is a public website, served by the existing
`goofy-gameplay-collector` Cloudflare Worker. After the updated files are deployed,
the intended public address is:

**https://goofy-gameplay-collector.abdyzam50.workers.dev/**

The root opens `/calculator/`. Anyone can open that link from a phone or computer
without Minecraft, a mod, Node, an account, a contributor key or an API key.
Market calculations run in the browser against live public Hypixel Bazaar
prices. The full flip categories and planner are included. A username is optional:
visitors can import a profile's purse and published unlocks, or enter a budget.

The owner's private Hypixel key enables username lookup through the shared API.
It is not sent to visitors. Each visitor's calculator preferences stay in their
own browser. There is no public access to the mod's private inventory, controls
or trading dashboard. The local dashboard remains an optional companion for mod
users, separate from public access.

## Owner publishes once

The updated website is prepared in source and `dist/goofyaddons-public-website.zip`.
It is not published simply by building or downloading this ZIP. The existing
online Worker must be redeployed with these assets. Visitors never run the
commands below.

1. Update the collector source files and `tools/bazaar-calc/calculator/`. Keep the
   existing folder structure. The ZIP contains those files without credentials.
2. Preserve your existing D1 binding and database ID in `wrangler.jsonc`.
   The template's `REPLACE_WITH_CREATED_DATABASE_ID` must not replace your actual
   database ID. Keep both contributor secrets and the GitHub token.
3. Ensure the configuration includes these additional bindings:

```json
"assets": {
  "directory": "../bazaar-calc/calculator",
  "binding": "ASSETS",
  "run_worker_first": true
},
"ratelimits": [{
  "name": "PROFILE_RATE_LIMITER",
  "namespace_id": "1001",
  "simple": { "limit": 5, "period": 60 }
}]
```

4. From the updated `tools/gameplay-collector` directory, run:

```powershell
npx wrangler@4.147.0 secret put HYPIXEL_API_KEY
npx wrangler@4.147.0 deploy
```

Paste the owner's application API key at the secret prompt, never into source or
chat. Obtain the key at https://developer.hypixel.net/. If it was already stored,
only `deploy` is needed. Wrangler uploads the whole website along with the Worker.
Uploading only the browser-editor JavaScript does not upload the website assets.

5. Open the public address in a private/incognito browser window. The calculator
   should open without installing anything. Load a username to verify profile
   lookup, and open a category directly to verify page navigation.

Local verification covers asset routing, private-key handling, upload permissions,
browser calculations and purse budgets. Live Cloudflare publication requires
access to the owner's Cloudflare account, which is unavailable in the coding
environment; there is no claim that this prepared build is already online.

## Publishing status and live refresh

`/calculator/status` now polls `/v1/publishing-status` every 30 seconds. It shows
the last scheduled publisher attempt, failure or unchanged result, last successful
check, actual GitHub commit link, new uploads awaiting a check, sample counts and
the next 15-minute schedule boundary. The countdown is an estimate; it cannot
promise Cloudflare execution at that exact second. Commit details appear after
the updated publisher performs its first changed publication. A configured
GitHub token alone is never displayed as a successful commit.

The publisher stores this aggregate state in a `publisher_status` D1 table,
created automatically on first use. No manual schema import is required for this
update. The endpoint exposes no credentials, contributor hashes or account data.
The seven-day dataset and contributor/route publication caps remain in place.
Unchanged data skips the GitHub write. Failures preserve the last successful
commit and show a safe HTTP code or generic error; inspect Worker logs for details.

Visible pages start live market polling even if no calculator has been opened.
Quotes use the Worker's fixed `/v1/market` proxy, cached for 20 seconds;
`/v1/items` provides current item metadata. Quotes older than one minute are
rejected and the header shows UNAVAILABLE or STALE. Rankings, plans, Outlook,
item details and event queries invalidate when a new quote arrives. Returning
to a hidden tab resumes polling. Background tabs may pause unless an alert,
tracked order or paper trade needs them.

Historical charts still use the bundled `calculator/data` reference snapshot.
Uploading gameplay evidence to GitHub does not rebuild those historical market
files, and the Status page explicitly distinguishes these sources. Rebuilding
and deploying the website updates its history snapshot.

For an existing installation, deploy the complete updated Worker and calculator
assets together, preserving the D1 ID and Cloudflare secrets. Copying only the
standalone Worker cannot update the browser pages.

## Live craft plans (0.2.27 website update)

Open `/calculator/flips/craft`. A new production planner sits above the original
craft research table; the existing buy-order/sell-offer calculations remain.
Load a username or enter your budget and confirmed unlocks in Settings & unlocks.
The production planner prices whole batches, including base preparations such as
blaze rods → blaze powder, from the same verified recipe catalog as the mod.

Search, sale-market, minimum-profit, maximum-batch and feasible-only controls are
local browser preferences for this view. Rankings remain visible when a route is
blocked, showing the unmet requirement or budget/liquidity/price reason. They do
not depend on macro positions or slots. Without confirmed unlocks, required routes
remain marked for confirmation. The browser cannot observe inventory, positions,
compactor configuration or available space; those must be checked before trading.

Bazaar instant costs use full ask depth, the mod's 4% observed purchase surcharge
and 3% price-change allowance. Sales use full bid depth, a 3% movement allowance
and configured tax. Batches are limited to 5% of estimated daily volume (the smaller
weekly side divided by seven). AH outputs need Coflnet discovery plus a fresh,
item-specific BIN quote; listing fees and a conservative sale allowance are
included. Discovery prices alone cannot authorize a recommendation. AH provider
volume has an unspecified window and is never labeled daily volume.

`/v1/crafts/market` refreshes bounded quote rotations centrally and strips auction
IDs. Bazaar and AH requests refresh about every 20 seconds while the page is
visible. Expired Bazaar prices clear production recommendations; expired AH
quotes disable their routes. If AH fails, Bazaar plans continue. `COFLNET_TOKEN`
is an optional Worker **secret** if the deployed Coflnet service requires one;
no contributor, GitHub, Hypixel or Coflnet secret is sent to visitors.

Estimated net profit is per batch. The effort/liquidity ranking score is not a
coins/hour forecast, and each row evaluates your budget independently. AH results
are manual planning opportunities: the mod's automatic AH execution still needs
sale/expiry/claim reconciliation. This page does not start the macro.

For this update, download `dist/goofyaddons-public-website-craft-0.2.27.zip`, copy
its `tools` files into the existing website deployment folder. This update ships
`wrangler.example.jsonc` so extraction preserves your actual `wrangler.jsonc` D1
database binding. Keep that existing configuration, then run from
`tools/gameplay-collector`:

```powershell
npx wrangler@4.147.0 deploy
```

Existing Worker secrets stay on Cloudflare; they need not be entered again.
Verify `/calculator/flips/craft` and `/v1/crafts/market` after deploying. No schema
migration or new user installation is required. Building this update does not
publish it to your Cloudflare account.

## Current progression parser update (0.2.38)

Download `dist/goofyaddons-public-website-craft-0.2.38.zip` and extract it. Copy
its `tools` contents into your existing deployment's `tools` folder. Keep your
existing `tools/gameplay-collector/wrangler.jsonc`; the update ships
`wrangler.example.jsonc` and does not overwrite your database/configuration.
From that existing `tools/gameplay-collector` folder run:

```powershell
npx wrangler@4.147.0 deploy
```

Then click Load / refresh profile on the calculator. The profile JSON endpoint
`/v1/profiles?username=curedmc` should contain
`"parserVersion":"2026-10-10-skill-tree"`. This identifies the corrected HotM,
Quick Forge, Slayer and Garden parser. Existing Worker secrets remain in place.
Installing the mod alone does not update the hosted profile API.

Collector patch 0.2.39 completes published zero Slayer/Garden progression and
removes irrelevant vanilla XP warnings. Deploy its website ZIP as above while
keeping `wrangler.jsonc`. No mod reinstall is needed. The profile response now
reports `parserVersion: "2026-10-10-unlock-completeness"`.

## Complete craft calculations (0.2.40)

Use `dist/goofyaddons-public-website-craft-0.2.40.zip`. Copy its `tools` files into
an existing deployment while preserving your `tools/gameplay-collector/wrangler.jsonc`.
Then run `npx wrangler@4.147.0 deploy` from `tools/gameplay-collector`.
Cloudflare secrets stay on the Worker. For a new deployment, configure the included
`wrangler.example.jsonc` as described above.

Open `/calculator/flips/craft` after deployment. Every catalog craft output is
listed, with costs/profit wherever fresh input/output prices exist and a reason
otherwise. Search prioritizes AH quotes for the matching craft and its components.
Use "Show 100 more crafts" to browse beyond the first page. Unknown AH demand,
stale prices, missing unlocks and untradable components remain conservative blocks.
The bundled local calculator receives the same calculation changes in mod
`astar-client-0.2.40-BETA.jar`.

### Worker asset-path correction (0.2.41)

The 0.2.40 craft endpoint requested `/calculator/data/production-recipes.json`
from `env.ASSETS`, but that binding is rooted at the calculator directory and
requires `/data/production-recipes.json`. The external website URL keeps its
`/calculator/` prefix; only the internal asset request changes. This caused
`Craft catalog unavailable` before any Coflnet collection could run.

Existing 0.2.40 deployments only need the latest `public-crafts.mjs` copied into
`tools/gameplay-collector` followed by `npx wrangler@4.147.0 deploy`. No mod update,
new token, database migration or settings reset is needed. The corrected public
package is `dist/goofyaddons-public-website-craft-0.2.41.zip`.

### Cloudflare fetch receiver correction (0.2.42)

Live discovery reported `Illegal invocation: function called with incorrect this
reference`. CraftMarket now calls the injected fetcher through a wrapper, so
Cloudflare's global fetch is not invoked as a method of CraftMarket. Item BIN
requests used a different call path and could succeed despite failed discovery.
Individual failed quote requests now include their HTTP status in the craft
market response; the rebuilt planner displays that failure instead of only waiting.

For an existing 0.2.40 deployment, update both `tools/bazaar-calc/craft-market.mjs`
and `tools/gameplay-collector/public-crafts.mjs`, then deploy. The full public
package `dist/goofyaddons-public-website-craft-0.2.42.zip` also includes the updated
error display. Preserve your existing wrangler.jsonc when copying files.

### Craft discovery route and independent demand (0.2.43)

The live Worker returned `Coflnet craft discovery HTTP 404`. Coflnet SkyApi's
CraftingController routes profit at `/api/craft/profit` (singular); the internal
SkyCrafts service's controller name is not the public gateway route. Corrected
all local/Worker callers and endpoint contract fixtures.

Demand no longer depends solely on appearance in the provider's profitable list.
Bounded quote rotations also fetch `/api/item/price/{item}` sales summaries, cache
validated volume/median for five minutes, and keep BIN prices usable if the sales
request fails. Summary and BIN calls run concurrently. Zero reported volume is
preserved and never interpreted as positive demand. The page displays individual
summary HTTP errors instead of endlessly saying only that demand is unavailable.
Provider activity is not assumed to be units/day or promised sell-through timing.

Source contracts inspected: Coflnet/SkyApi revision
`aa4349e07d28b8389ff8f605cf4dd93fa2177edf`, Controllers/CraftingController.cs
and Controllers/PricesController.cs; Coflnet/SkyCrafts revision
`1d25352d31cfa94f04a797bd7c120e4d0bc3b565`, Models/ItemResult.cs.

Public deployment: copy the `tools` files from
`dist/goofyaddons-public-website-craft-0.2.43.zip`, preserving wrangler.jsonc,
then deploy from tools/gameplay-collector. For a minimal collector-only patch,
update public-crafts.mjs and ../bazaar-calc/craft-market.mjs. The full package
also includes detailed demand error messages. Local mod users can install
astar-client-0.2.43-BETA.jar for the same corrected routes and demand collection.
