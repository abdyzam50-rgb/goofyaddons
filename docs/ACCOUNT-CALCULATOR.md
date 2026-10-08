# Full calculator and account lookup

**Public access is the intended setup for visitors.** See
[Public website](PUBLIC-WEBSITE.md). The Cloudflare Worker serves the full website
at its public HTTPS address; visitors install nothing. The local address below
is an optional integration for people already running the mod.

Open the existing dashboard and select **Full calculator · no macro needed**,
or open **http://127.0.0.1:8789/calculator/**. The combined mod starts the
calculator automatically; you do not have to start trading. Users without the
mod can run the packaged companion with `node server.mjs` and open the same URL.

This is the complete upstream Bazaar Calc interface, including its route planner,
Bazaar, books, crafting, Forge, NPC, Kat and shard fusion tabs, market charts,
manual order tracking, paper trading, filters, settings and unlocks. A calculator
route is a recommendation for a person; it does not add support for executing
that route in the macro.

Enter a Minecraft username, click **Load / refresh profile**, and select a
SkyBlock profile. Its published purse sets the calculator budget. Enchanting,
skills, HotM, Quick Forge, collections, slayers and faction reputation are
imported when published. Missing fields are listed as unknown and their required
routes are conservatively excluded. Current vanilla XP levels are not reliably
published: enter those in **Settings & unlocks** for XP-dependent book routes.
Normal profiles can trade; Ironman, Stranded and Bingo profiles receive a zero
trading budget with an explanation.

The budget uses purse coins, excluding bank deposits and existing order funds.
It is an API snapshot; the shared lookup caches results for up to five minutes.
Refresh after spending or earning coins, checking the displayed fetch time. You can edit
the budget and requirements manually at any time, including when lookup is
unavailable. The planner fits its combined allocation within the selected
budget and available order/Forge slots; individual estimates are not guaranteed
earnings. The requirement filter can be turned off to inspect research routes.

## Enable username lookup on the shared Worker

The website is ready locally, but lookup needs this updated collector deployed
and a Hypixel API key provisioned privately. Existing contributor secrets and
the D1 database remain in place. There is no Hypixel key inside the mod, ZIP,
website or GitHub repository, and contributors do not need their own key.

1. Obtain a Hypixel API key for the application through https://developer.hypixel.net/.
2. In Cloudflare, open **Workers & Pages → goofy-gameplay-collector → Settings →
   Variables and Secrets**. Add a **Secret** named **HYPIXEL_API_KEY** containing
   that key. Keep both contributor-hash secrets and the GitHub token.
3. Deploy the updated collector from `tools/gameplay-collector`, preserving your
   existing database ID. Its Wrangler configuration adds **PROFILE_RATE_LIMITER**:
   five lookups per client address per minute, namespace ID `1001`. On the
   Cloudflare dashboard this is a Rate Limiting binding with the same name,
   limit and period. Lookup fails closed if either the secret or binding is absent.
4. Open the calculator and load your username. It calls the shared Worker’s
   `/v1/profiles?username=YOUR_NAME` endpoint. Lookup uses Mojang to resolve the
   username and Hypixel’s public API for the selected account’s SkyBlock profiles.

Using Wrangler from PowerShell in the collector directory:

```powershell
npx wrangler@4.147.0 secret put HYPIXEL_API_KEY
npx wrangler@4.147.0 deploy
```

Paste the API key only at Wrangler’s secret prompt or in Cloudflare’s Secret
field. The self-contained `dist/gameplay-collector-update.mjs` also includes
lookup for the browser editor; deploy it with the secret and rate-limit binding.

Profile responses contain only the fields used by the calculator. Inventory,
chat, bank data and authentication credentials are excluded. Profile lookup
does not write to D1, the gameplay-data branch, or the shared learning dataset.
The browser stores calculator budgets and unlock settings locally; it does not
store the API key. Upload permissions and contributor-key revocation remain
independent of this read-only service.

## Market data and public hosting

The local website uses the existing 20-second public Bazaar collector, even
when the macro is stopped. Prices older than 60 seconds cannot produce new
recommendations. NPC item metadata refreshes from Hypixel; published history
and recipe data are bundled with attribution. Auction prices older than two
hours are excluded, so Kat/AH routes can be unpriceable until reference auction
data is refreshed; they are listed with the reason. This change does not add a
continuous Auction House scanner.

The generated `tools/bazaar-calc/calculator/` directory is also a static website:
publish it at `/calculator/`, preserving the directory and SPA fallback for
item pages. Without the local companion it fetches live Bazaar prices directly
from Hypixel and uses the same shared profile lookup. Users can then calculate
without installing Minecraft, the mod or Node. The live trading dashboard is
local; public hosting does not expose anyone’s local inventory or macro controls.
Public hosting and Cloudflare deployment still require the owner to publish the
prepared files; local build completion does not update the existing online sites.

## Rebuild and attribution

Upstream website/shared engine: https://github.com/Goofythesecond/bazaar-calc,
revision `85cc23d621cd7194b163abf3d55ea76b682fcc2a` (MIT). Recipes and progression:
NotEnoughUpdates-REPO revision `777a3aae04ff462ea20ea9b346e9208d7ce9adc5`
(MIT). Enchant/wiki-derived facts retain the upstream license notices.

Install the upstream checkout’s pinned dependencies and build its shared package,
then run `node tools/bazaar-calc/build-website.mjs CHECKOUT SITE_DATA NEU_DIRECTORY`.
The checked-in overlay and build script reproduce the embedded website; the
trader’s existing restricted execution engine is separate. Refresh published
history/reference data when packaging a later release. Test with
`node --test tools/bazaar-calc/*.test.mjs tools/gameplay-collector/profile-lookup.test.mjs`
and `node tools/bazaar-calc/calculator-browser-check.mjs`.
