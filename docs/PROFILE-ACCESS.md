# Fixing profile prerequisite lookup

Hypixel HTTP 403 means the Worker could not access SkyBlock profiles with its
current API key. It is separate from Bazaar market collection: a ready local
calculator does not prove that profile access works.

In Cloudflare, open `goofy-gameplay-collector` → Settings → Variables and Secrets.
Edit the existing `HYPIXEL_API_KEY` secret, enter a current Hypixel key authorized
for SkyBlock profile access, and deploy the change. Keep the key private; never
put it in `wrangler.jsonc`, GitHub, a mod config, or a chat message. If a current
key still gets HTTP 403, check its application's Hypixel permissions.

From the existing `tools/gameplay-collector` folder, the equivalent command is:

```powershell
npx wrangler@4.147.0 secret put HYPIXEL_API_KEY
```

Enter the key at the private prompt. Test your username on the public calculator.
Once profile lookup succeeds, the mod retries automatically within a minute; the
queued production run can continue when the requirement is verified.

## Mod 0.2.19

The companion forwards fixed error categories instead of hiding them behind a
generic profile-unavailable message. The mod shows the cause in Account
prerequisites and blocked production messages. Diagnostic snapshots and state
change events include the safe failure category, freshness and observation
counts. They exclude usernames, profile names, keys and raw response bodies.
Missing and unpublished progression remains unknown; failed lookups never grant
Gold IV or any other unlock.

## Optional Worker collection update

Published collection tiers now also travel by their stable IDs (`GOLD_INGOT`),
independently of the separate collection-name resources request. If the name
service is unavailable, published tiers are still usable. The mod accepts both
the prior named format and this new ID format. This does not bypass API access
or invent tiers when collections are unpublished.

The small `goofyaddons-profile-service-0.2.19.zip` contains the updated
`tools/gameplay-collector/profile-lookup.mjs`. Extract it over your existing
repository folder, preserving your current Wrangler database configuration,
then run from the existing collector folder:

```powershell
npx wrangler@4.147.0 deploy
```

Validation: 977 Java tests and 102 Node tests passed, including HTTP 403
classification, private response redaction, retry after fixing access,
profile-switch cancellation and collection import without resource names.
Live Minecraft and your replacement Hypixel key still require verification.
