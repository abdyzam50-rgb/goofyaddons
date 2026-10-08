# Automatic shared gameplay collector

The combined mod sends a trade-only local feed when contributor uploads are
enabled; the optional account dashboard is independent. Enrolled companions automatically
upload a strict public projection every five minutes. This Cloudflare Worker
validates and stores it in D1, then publishes a bounded seven-day dataset every
15 minutes to `gameplay-data:community-history.json` in
`abdyzam50-rgb/goofyaddons`. Companions download that dataset every 15 minutes.
No tester needs GitHub permissions or a repository token.

Uploads are opt-in. Downloads use the public repository. Existing local trading
and personal learning continue through collector outages. Failed uploads remain
local and retry; acknowledgements survive replacing the installation folder.

## Why Cloudflare

We also considered Supabase (a viable free alternative with scheduled Edge
Functions, a 500 MB database and 500,000 function calls/month), Render free web
services, and hosting on the owner's PC. Supabase free projects can pause;
Render needs durable external storage for this design; a PC needs to remain
online and expose a reachable endpoint. The owner selected Workers + D1.

Workers Free currently allows 100,000 requests/day and 10 ms CPU per invocation.
Free D1 allows 50 queries/invocation and a 500 MB database. Uploads are limited to
20 samples (40 queries), with at most 500 newly stored samples per contributor
per UTC day. Each submitted sample remains untrusted evidence. Enrollment,
strict schemas, deduplication and bounded statistical weighting constrain its
influence; they cannot prove a tester's reports are authentic.

Monitor CPU and D1 usage in Cloudflare before scaling the tester group. This is
a free-tier design, not a promise of unlimited free hosting or service uptime.
Verified sources:

- https://developers.cloudflare.com/workers/platform/pricing/
- https://developers.cloudflare.com/d1/platform/limits/
- https://supabase.com/docs/guides/platform/billing-on-supabase
- https://supabase.com/docs/guides/functions/schedule-functions

## Owner: deploy once

Run these from `tools/gameplay-collector` with Node 22+ and a free Cloudflare
account. Wrangler 4.147.0 is the validated tooling version. Credentials must be
entered into Wrangler's secret prompts, never committed to the repository.

```sh
npx wrangler@4.147.0 login
npx wrangler@4.147.0 d1 create goofy-gameplay
```

Copy the returned database ID into `wrangler.jsonc`'s `database_id` field. Keep
the `DB` binding. Initialize the remote schema:

```sh
npx wrangler@4.147.0 d1 execute goofy-gameplay --remote --file schema.sql
```

Create a GitHub fine-grained token limited to this repository with Contents
read/write permission. Store it **only on the Worker**:

```sh
npx wrangler@4.147.0 secret put GITHUB_TOKEN
```

Create the first enrollment:

```sh
node enroll.mjs add tester-01
```

The helper prints paths, never key values. Its registry and key files live in
GoofyAddons' persistent data directory, outside the installation. Give the
`.key` file privately to that tester. Set `CONTRIBUTOR_HASHES` by running the
following and pasting the contents of `contributor-hashes.json` at the prompt:

```sh
npx wrangler@4.147.0 secret put CONTRIBUTOR_HASHES
npx wrangler@4.147.0 deploy
```

The resulting `https://goofy-gameplay-collector.<account>.workers.dev` URL is the
collector address testers configure. Visit its `/health`: `ready` checks binding
and secret presence, not successful GitHub authorization. Confirm a real tester
upload and the next scheduled commit before distributing it widely. The cron
creates the `gameplay-data` branch when needed and writes only its dataset file.
It does not update master or refactor. Avoid edits to this file from other
writers; a concurrent commit failure retries on the next scheduled run.

For another tester, deploy the updated Worker and use a separate
`CONTRIBUTOR_HASHES_EXTRA` secret. Keep the existing `CONTRIBUTOR_HASHES` untouched.
Both lists authorize uploads independently. `/health` reports
`additionalContributorKeysSupported: true` after the new code is deployed.

Add/import testers using the helper, then set:

```sh
npx wrangler@4.147.0 secret put CONTRIBUTOR_HASHES_EXTRA
```

On a phone, add a new **Secret** with that name through the Worker settings and
paste the new hash array. Original owner approvals remain active. Invalid extra
lists do not disable valid original keys.

To revoke an extra-list tester:

```sh
node enroll.mjs revoke tester-01
npx wrangler@4.147.0 secret put CONTRIBUTOR_HASHES_EXTRA
```

Replace the extra list with the updated file. Keys in the original list remain
approved independently; removing a duplicate from only one list does not revoke it.
Revocation blocks future uploads; it
does not erase public Git history or already published samples. Never put a
shared repository token in the companion ZIP or mod.

## Tester: mod settings

Combined A* mod 0.2.5+ includes **G → Macros → Shared gameplay learning**. Paste the
private owner-approved contributor key, enable uploads and save. There is no terminal
setup for testers. See [the shared-learning guide](../../docs/SHARED-LEARNING.md).

Keys from external generators can be imported by the owner:

```powershell
node enroll.mjs import tester-01 "C:\private\tester-01.key"
```

Then update the updated Worker's `CONTRIBUTOR_HASHES_EXTRA` secret with the complete
generated tester list, preserving the original owner secret. The supported key format is 32–128 ASCII letters,
numbers, hyphens or underscores. Approval remains owner-only.

## Tester: legacy standalone companion


Keep mod 1.3.52 or newer and install companion 1.3.53 or newer. Stop the companion.
From the extracted `bazaar-calc` folder, PowerShell can read the enrollment key
without placing its literal value in shell history:

```powershell
$env:GOOFY_CONTRIBUTOR_TOKEN = (Get-Content -Raw 'C:\path\tester-01.key').Trim()
node .\community.mjs configure https://goofy-gameplay-collector.YOUR-ACCOUNT.workers.dev
Remove-Item Env:GOOFY_CONTRIBUTOR_TOKEN
node .\server.mjs
```

In a repository checkout, use `node tools/bazaar-calc/community.mjs configure
https://YOUR-COLLECTOR` and `node tools/bazaar-calc/server.mjs` instead.
Enable `marketAnalysis.dashboardEnabled` so gameplay outcomes reach the
companion. Uploads run without manual exports or commits. The local dashboard
shows imported samples, pending uploads and sync errors. To stop sharing, run
`node community.mjs disable` and restart. Start the server with `--no-community`
to disable both public downloads and uploads. Disabling sharing leaves personal
history intact. Do not distribute private settings, enrollment keys or caches.

## Public evidence and learning

Only complete, known-profit cycles with their original forecast are exported.
Public fields are product IDs, engine, quantities, observed duration rounded to
seconds, completion time rounded down to the hour, original cycle/volume
forecasts and a bounded realized/expected-profit ratio. IDs are salted hashes;
a contributor is an opaque key hash. These are pseudonymous records, not a
claim of anonymity: timing and unusual route choices may be recognizable.

Account names, UUIDs, chat, inventory, purse balances, raw receipt IDs and exact
profit/proceeds are excluded by a strict allowlist on both client and server.
The database keeps seven days; Git commits retain historical public versions.
Don't upload anything you wouldn't want retained publicly.

Shared timing/profit corrections transfer only between the same engine and
routes within 4× both input and output daily volumes. Weight decays with age.
Each contributor contributes at most three trades per route and ten total
trades to a route's prior. Personal outcomes refine it and fully replace the
prior at ten qualifying outcomes. A tester's own uploads are excluded from
its imported prior. Public evidence never enters personal profit accounting.

## Local verification

From the repository root:

```sh
node --test tools/bazaar-calc/*.test.mjs
cd tools/gameplay-collector
npx wrangler@4.147.0 deploy --dry-run --outdir /tmp/goofy-worker-build
```

Tests use real SQLite behind a D1-shaped adapter and a simulated GitHub API.
They validate authentication, strict schemas, free-tier batch sizes, daily
caps, duplicate receipts, retry/restart behavior, public-field privacy,
learning bounds, and data-branch-only publishing. A dry run verifies bundling;
it does not deploy or demonstrate production credentials/cron execution.
