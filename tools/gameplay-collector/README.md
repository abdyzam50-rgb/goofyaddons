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

This section is for a **new deployment**. Existing owners should use
[Update an existing deployment](#update-an-existing-deployment), preserving the
Worker name, D1 binding/ID and secrets. End users installing the mod do not need
Cloudflare, a Hypixel key or a GitHub token.

From your clone's `tools/gameplay-collector` directory, use Node 22+ and Wrangler
4.147.0. The tracked `wrangler.jsonc` is a template with a placeholder D1 ID;
copy it to an ignored owner-local configuration, without overwriting an existing
working file:

```powershell
if (!(Test-Path ".\wrangler.local.jsonc")) {
    Copy-Item ".\wrangler.jsonc" ".\wrangler.local.jsonc"
}
npx wrangler@4.147.0 login
npx wrangler@4.147.0 d1 create goofy-gameplay
```

Edit `wrangler.local.jsonc`: set the returned database ID/name and your repository;
retain the `DB`, `ASSETS` and `PROFILE_RATE_LIMITER` bindings and the asset directory.
The database name below must match the database you created. Do not deploy
`REPLACE_WITH_CREATED_DATABASE_ID`. Initialize the remote schema:

```powershell
npx wrangler@4.147.0 d1 execute goofy-gameplay --remote --file schema.sql --config ".\wrangler.local.jsonc"
```

Create a GitHub fine-grained token for the target repository with Contents
read/write permission, then enter it at the secure Wrangler prompt:

```powershell
npx wrangler@4.147.0 secret put GITHUB_TOKEN --config ".\wrangler.local.jsonc"
```

For private profile lookup, put an authorized Hypixel key on the Worker too:

```powershell
npx wrangler@4.147.0 secret put HYPIXEL_API_KEY --config ".\wrangler.local.jsonc"
```

Keys go in these prompts, not JSON, Git, chat or command arguments. Contributor
enrollment uses a separate key. Create an enrollment:

```powershell
node enroll.mjs add tester-01
```

The helper prints paths, not key values. Its registry/key files are in the
persistent data directory outside the installation. Give the `.key` file privately
to its tester. Enter `contributor-hashes.json` contents at the following secret
prompt, then deploy with the same local config:

```powershell
npx wrangler@4.147.0 secret put CONTRIBUTOR_HASHES --config ".\wrangler.local.jsonc"
npx wrangler@4.147.0 deploy --config ".\wrangler.local.jsonc"
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

### Diagnose account profile access

`GET /v1/profiles?username=curedmc` returns a fixed `failureCode` when profile
lookup fails. `HYPIXEL_FORBIDDEN` means the upstream Hypixel request returned
403; `HYPIXEL_UNAUTHORIZED` means 401. Both require checking the Worker
`HYPIXEL_API_KEY` and its Hypixel application permissions. A Worker HTTP 502
does not by itself identify a network outage. The response and mod debug export
retain bounded upstream service/status facts without returning API keys or raw
upstream bodies. Failed lookups do not grant unverified account unlocks.

Use the update steps below to deploy the current profile lookup code. A successful
profile response uses `goofy-profile/1`; deployment success alone does not verify
Hypixel authorization. Invalid keys need replacement through Wrangler's private
`secret put HYPIXEL_API_KEY` prompt, using the same config file as deployment.

## Update an existing deployment

First enter the **cloned repository**, not your home directory. For the owner's
existing Windows clone:

```powershell
Set-Location "C:\Users\abdy2\goofyaddons" -ErrorAction Stop
$repoRemote = git remote get-url origin
if ($LASTEXITCODE -ne 0 -or $repoRemote -notmatch 'abdyzam50-rgb/goofyaddons(?:\.git)?$') {
    throw "Check the repository folder and remote before updating."
}
git fetch origin
if ($LASTEXITCODE -ne 0) { throw "Git fetch failed; stop here." }
git switch claude/project-thread-wmox9w
if ($LASTEXITCODE -ne 0) { throw "Branch switch failed; review local changes." }
git pull --ff-only origin claude/project-thread-wmox9w
if ($LASTEXITCODE -ne 0) { throw "Git update failed; do not deploy stale sources." }
Set-Location ".\tools\gameplay-collector" -ErrorAction Stop
Get-Location
```

Replace the initial path for another installation. The remote must be the
`abdyzam50-rgb/goofyaddons` repository; use your corresponding remote name if it
is not `origin`. This branch contains application changes; published datasets go
to the separate `gameplay-data` branch. If Git reports local changes, conflicts
or a different remote, stop and review them rather than resetting/discarding.
For a first clone, run this from the desired parent folder, then enter the clone:

```powershell
git clone --branch claude/project-thread-wmox9w https://github.com/abdyzam50-rgb/goofyaddons.git
```

Use the **existing working owner configuration**. If it is already
`wrangler.jsonc`, keep using that filename, or copy it to `wrangler.local.jsonc`
first. Do not overwrite a real database ID with the tracked placeholder template.
Owner-local `wrangler.local.jsonc` is ignored by Git.

```powershell
if (!(Test-Path ".\wrangler.local.jsonc")) { throw "Locate your existing Worker config first." }
if ((Get-Content ".\wrangler.local.jsonc" -Raw) -match 'REPLACE_WITH_CREATED_DATABASE_ID') {
    throw "Set the real D1 database ID before deploying."
}
npx wrangler@4.147.0 deploy --config ".\wrangler.local.jsonc"
```

Run the deployment only if the path exists and its Worker/D1 values are correct.
If your working file is named `wrangler.jsonc`, replace the config argument in
**every** command, including secret updates. A missing config (`ENOENT`) means
wrong path/filename. "Could not detect a directory containing static files" can
mean Wrangler ran from the wrong directory or without the intended config.
Do not create a new database or enroll existing contributors again just to update.

To replace an expired/rejected Hypixel key:

```powershell
npx wrangler@4.147.0 secret put HYPIXEL_API_KEY --config ".\wrangler.local.jsonc"
Invoke-RestMethod "https://goofy-gameplay-collector.abdyzam50.workers.dev/v1/profiles?username=curedmc"
```

Use your own Worker URL/username when different. Check `goofy-profile/1`, the
returned profile names, unknown fields and `stats.recipeUnlocks`. Missing/empty
recipe metadata does not verify an unlock; the mod conservatively excludes
recipes that need it. No player should enter the Worker's key into their mod.

A JAR update changes the client; this deployment changes the public Worker and
uploads its configured static assets. Git data publication does not rebuild the
website's bundled historical charts. See [website deployment](../../docs/PUBLIC-WEBSITE.md)
for that separate build/deployment workflow.

## Trigger publication without waiting for cron

The owner-only `POST /v1/admin/publish` publishes existing D1 evidence now; it
**does not** perform a fresh market collection, start player trading or rebuild
the website. It shares a lease with scheduled publication. Configure a separate
`ADMIN_TOKEN` Worker secret if not already configured (32–128 URL-safe letters,
digits, `_` or `-`); it is not the contributor key or GitHub token:

```powershell
npx wrangler@4.147.0 secret put ADMIN_TOKEN --config ".\wrangler.local.jsonc"
```

Test using that existing admin token without placing its value in command history:

```powershell
$adminSecret = Read-Host "Worker admin token" -AsSecureString
$adminPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminSecret)
try {
    $adminToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($adminPointer)
    Invoke-RestMethod -Method Post `
      -Uri "https://goofy-gameplay-collector.abdyzam50.workers.dev/v1/admin/publish" `
      -Headers @{Authorization="Bearer $adminToken"} | ConvertTo-Json -Depth 8
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($adminPointer)
    Remove-Variable adminToken, adminSecret, adminPointer -ErrorAction SilentlyContinue
}
```

`COMMITTED` means a dataset commit was written; `UNCHANGED` is a successful no-op
and can have a null commit SHA. `busy: true` / HTTP 409 means another publication
holds the lease. HTTP 401 here means the supplied **admin** credential was not
accepted. A publisher's GitHub HTTP 401 instead concerns the Worker's
`GITHUB_TOKEN`; check which operation failed. `/health` readiness checks bindings
and secret presence, not successful GitHub/Hypixel authorization.
