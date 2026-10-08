# Shared gameplay learning

GitHub stays public so everyone can download shared learning. Upload approval is
controlled privately by the collector owner. The mod does not generate or approve
contributor keys, and testers do not need GitHub access.

## Tester: configure in Minecraft

In **G → Macros → Shared gameplay learning**:

1. Check **Collector**: normally `https://goofy-gameplay-collector.abdyzam50.workers.dev`.
2. Check **Dataset repository**: normally `abdyzam50-rgb/goofyaddons`.
3. Paste the private contributor key supplied by the owner into **Contributor key**.
   Input is masked. Leave this field blank when saving later to retain a saved key.
4. Turn on **Upload gameplay** and click **Save sharing**. The bundled calculator
   restarts to apply the settings. If you run a separate calculator, restart it yourself.
5. **Saved key**, **Trade feed**, and **Sync status** show local configuration,
   connection to the calculator, acknowledged uploads and imported shared samples.

Saving a key does not itself approve it: the collector owner must register its hash.
Rejected keys leave pending samples local for retry. Valid-key syntax is 32–128 ASCII
letters, numbers, hyphens or underscores. Generated 256-bit base64url keys and 64-character
hex keys work. Do not use memorable passwords as keys.

Uploads stay off by default. Turn off **Upload gameplay** and save to pause contributions
while retaining your key. **Forget key** removes it locally and disables future uploads;
that does not revoke the key on the collector or erase previously published evidence.
Shared downloads remain enabled and need no contributor key.

Your key lives only in `community-settings.json` in the persistent companion data folder:
`%LOCALAPPDATA%\GoofyAddons\bazaar-calc` on Windows, the existing platform data folder on
macOS/Linux, or your `GOOFY_BAZAAR_DATA_DIR` override. It is kept outside the mod/config
JSON, diagnostic exports and update packages. Do not share that private file.

The mod sends trade results locally to the calculator independently of the optional
account dashboard. Public uploads contain product IDs, rounded timing, forecast
volumes and profit ratios. They exclude account names, exact balances/profit receipts,
inventory, chat, server addresses and credentials.

Uploads run every five minutes, the collector publishes every fifteen minutes, and
calculators download shared evidence every fifteen minutes. Sync is periodic rather
than instantaneous. Each user also retains personal evidence and live market collection;
rankings can differ with capital, requirements, mode and local outcomes.

## Owner: generate or import approved keys

Use the private enrollment helper in `tools/gameplay-collector` on your PC. Neither it
nor a public key-issuing endpoint is part of the user mod. Keep its registry and keys
private. Any generator producing suitably random keys in the supported format works.

Generate a new key with the existing helper:

```powershell
node .\enroll.mjs add tester-01
```

Or import a key from another generator. Put only that key in a private text file:

```powershell
node .\enroll.mjs import tester-01 "C:\private\tester-01.key"
```

Both commands print file locations, never the key. Give that tester their individual
`.key` file privately. Duplicate keys/labels and invalid formats are rejected; existing
records are preserved. Do not put the key in a shell argument, Git commit or public download.

## Owner: keep your original key and add a separate tester list

The updated collector accepts either of these secrets:

- **CONTRIBUTOR_HASHES**: your existing owner/approved-key list. Keep it unchanged.
- **CONTRIBUTOR_HASHES_EXTRA**: the additional tester-key list. Add this separately.

Deploy the updated collector code before relying on the extra secret. The mod needs
no update for this change. A collector whose `/health` returns
`additionalContributorKeysSupported: true` supports both lists.

After adding/importing tester keys, update only **CONTRIBUTOR_HASHES_EXTRA** with the
complete tester list from the enrollment helper's printed `contributor-hashes.json`:

```powershell
npx wrangler@4.147.0 secret put CONTRIBUTOR_HASHES_EXTRA
```

Existing helper registries may also include your owner hash; a duplicate in both
lists is harmless. Your original secret remains independently valid. The helper
cannot retrieve, edit or revoke entries in the original secret.

On a phone, open **Cloudflare Dashboard → Workers & Pages →
goofy-gameplay-collector → Settings → Variables and Secrets → Add**. Select
**Secret**, name it **CONTRIBUTOR_HASHES_EXTRA**, paste a JSON array of the new
keys' SHA-256 hashes, and deploy. Use **Edit/Rotate** on that extra secret for
later changes. Do not rotate the original secret when adding testers.

All values are hashes rather than raw keys or tester labels. An invalid extra
list is rejected independently and cannot disable valid owner approvals.
Keep the GitHub publishing token server-side.

To revoke a tester listed only in the extra secret:

```powershell
node .\enroll.mjs revoke tester-01
npx wrangler@4.147.0 secret put CONTRIBUTOR_HASHES_EXTRA
```

Paste the helper's updated full extra list. A key approved by both secrets remains
approved until removed from both; this protects your original owner key when
rotating tester approvals. Revocation blocks future uploads and does not remove
samples from Git history. One key per tester makes revocation independent.
Existing upload limits, validation, duplicate rejection and bounded prediction
influence apply. Approved users can fabricate plausible data, so shared evidence
is a limited prior rather than proof of a trade.

The optional read-only username lookup uses a separate private `HYPIXEL_API_KEY`
secret and `PROFILE_RATE_LIMITER` binding. It never writes player profiles to
the shared dataset. See [Account calculator](ACCOUNT-CALCULATOR.md).
