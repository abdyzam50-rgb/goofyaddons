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
