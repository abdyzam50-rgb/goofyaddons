# Profile identity, unlocks and Worker access

The combined mod uses the **observed SkyBlock profile**, not whichever profile is
selected on a third-party website. Settings and access restrictions are stored
beside its journals under `config/goofyaddons/accounts/<player>/<profile>/`.
Skills, API evidence and forecasts clear when the observed profile changes.
If journals are already pinned, restart Minecraft before trading on a different
profile. `.a* goofyaddon profiles` shows observed/pinned identity; its `adopt`
and `setaside` subcommands are only for legacy file migration.

No end user needs a personal Hypixel key. Profile requests go directly to the
public Worker; the local calculator is not proof that profile access works.
The Worker owner maintains `HYPIXEL_API_KEY` using Cloudflare's secret storage.
Contributor uploads and their enrollment keys are separate from profile lookup.

## Diagnose a blocked requirement

Use `.a* goofyaddon production requirements` and export diagnostics with F7 or
`.a* goofyaddon debug export`. The export identifies the current lookup profile,
failure category, freshness and counts; secret values and raw upstream bodies
are excluded. It can contain gameplay/profile context and should be treated as
a support bundle, not a public market dataset.

- HTTP 401/403: the owner must renew the Worker key/check SkyBlock permissions.
- Rate limit/connection failure: the mod retries with bounded backoff; it does
  not grant unlocks during the outage.
- Collection data unpublished: enable the appropriate SkyBlock API visibility;
  unknown tiers are not inferred from item prices or another profile.
- Profile/skill mismatch: confirm the actual in-game profile and allow a fresh
  observation. Do not copy another profile's files or disable ownership checks.
- Recipe locked/unverified: inspect the server recipe book. Enchanted Red Sand
  specifically requires verified current-profile recipe-unlock metadata before
  purchasing ingredients. Older Worker responses or missing metadata keep it
  excluded even if a different profile can craft it.

## Owner update

Use the [collector deployment guide](../tools/gameplay-collector/README.md#update-an-existing-deployment).
Run Wrangler from the cloned repository's `tools/gameplay-collector` directory,
with your existing Worker/D1 configuration. A relative `tools/` path from your
Windows home folder does not point into the repository.

To replace a rejected key, from that collector directory:

```powershell
npx wrangler@4.147.0 secret put HYPIXEL_API_KEY --config ".\wrangler.local.jsonc"
```

Use your actual config filename if different. Enter the key only at the private
prompt. Never put it in Git, chat, a mod config or a command-line argument.
Check a response after updating:

```powershell
Invoke-RestMethod "https://goofy-gameplay-collector.abdyzam50.workers.dev/v1/profiles?username=curedmc"
```

Replace the URL/username for your deployment. A successful response uses
`goofy-profile/1` and includes profile progression; published collection recipe
metadata appears in `profiles[].stats.recipeUnlocks`. An empty or missing map
is not permission to craft. HTTP 200/Worker deployment success alone does not
prove that every recipe/unlock was published or imported.

The old `goofyaddons-profile-service-0.2.19.zip` is a historical artifact; use
current branch sources for current behaviour. JAR updates do not deploy the
Worker. The existing owner database/secrets do not need recreating for an update.
