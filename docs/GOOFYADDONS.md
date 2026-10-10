# Trading inside A*

The combined **A* with GoofyAddons 0.2.27-BETA** targets Minecraft **26.3**,
Fabric Loader **0.19.5 or newer**, Fabric API **0.161.0+26.3**, and Java **25**.
It includes the trader from GoofyAddons 1.3.57 in one client JAR.

The 0.2.15 foundation refactor introduces a separate trading-core module and
injects the general trader's runtime services and order repository. Existing
order files retain their format and paths. See the [foundation notes](../trading-core/README.md).

The 0.2.16 book-engine refactor adds the same dependency separation, replaces raw
game-slot reads with captured menu observations, and retains the book journal's
existing record fields. See the [book refactor log](BOOK-ENGINE-REFACTOR.md).

The 0.2.17 craft update imports current-profile prerequisites through the website
service and picks up full ingredient stacks before splitting 32s inside the grid.
See [lookup, placement and verification notes](CRAFT-PREREQUISITES-AND-STACKS.md).

The 0.2.18 search fix selects Bazaar results by exact product ID, so duplicate
display names cannot confuse plain and enchanted ingredients. Buy and sell
navigation share this rule; conflicting product-page icons are rejected.

The 0.2.19 prerequisite update shows profile failures (including Hypixel HTTP 403)
in settings, production messages and diagnostics. See [fixing profile access](PROFILE-ACCESS.md).

The 0.2.20 craft update repeatedly halves owned grid stacks (including 64 → 32 → 16),
reuses halves across matching cells, and removes stacked crafting delays.
See [craft split and pacing details](CRAFT-PREREQUISITES-AND-STACKS.md).

The 0.2.21 update shortens retries for transient profile connection failures and
verifies the normal Blaze Rod → two Blaze Powder recipe. See the craft notes above.

The 0.2.22 update prepares missing Blaze Powder, sticks and oak planks from
base materials before the final craft; other ingredients retain direct procurement.
See the craft notes above for yield accounting and recovery rules.

The 0.2.23 update expands basic ingredient preparation to paper/books, wooden
components and tools, gold nuggets, redstone torches, Eyes of Ender and bottles.
See the [ingredient audit](BASIC-INGREDIENT-AUDIT.md) for supported recipes and limits.

The 0.2.24 update uses Auction House browsing and search for BIN discovery,
with Coflnet as a price validator. See [auction navigation and pricing](AUCTION-GUI-MARKET-CHECK.md)
for the supported flow and remaining AH procurement integration.

The 0.2.25 update detects Personal Compactor tiers and blocks conflicting active
recipes before production uses their inputs. Automatic bulk configuration and
cleanup remain pending; see [the implementation boundary](PERSONAL-COMPACTOR.md).

The 0.2.26 update replaces the mode-switch shortcut with **Debug export** (F7
by default). Change it in **G → Macros → Keybinds** or Minecraft Controls.
It also works with an inventory/container GUI open and captures the screen before
writing the bundle in the background. Choose trading mode in the settings GUI.
An existing `modeKey` setting migrates to `debugKey`; customized bindings are retained.

## Installation and saved data

Replace the standalone A* and GoofyAddons JARs with the combined JAR. Keep Fabric
API for Minecraft 26.3. Installing both the standalone and combined versions
would duplicate their mod IDs/classes and initialization.

Keep `config/goofyaddons.json` and every existing `config/goofyaddons-*.json`
record. Orders, inventory/storage observations, production jobs, profit receipts
and execution history retain their original paths and formats. Keep A*'s saved
maps under `astar/places` too.

If your launcher creates a separate Minecraft 26.3 instance, copy those saved
config records into the new instance's config directory. The mod reads the active
instance; it does not search other instances or guess which account files to adopt.
The calculator's existing external data directory and collector settings remain
compatible. There is no need to reenroll contributors or deploy Cloudflare again.

Old GLFW keyboard bindings are migrated to Minecraft 26.3's SDL scancodes in
memory. Old default J/K/M bindings become F6/F7/F8; customized start keys are retained and old mode keys become Debug export. The new `keyCodeSchema: 2` marker is written when
settings are saved. Unsupported legacy keys stop config loading instead of
silently changing bindings. The original file stays intact until an explicit save.

## Macros page

Press **G**, then **Macros**. Stop trading with **F6** before opening settings.

Version 0.2.9 includes A* upstream GUI fix `9042383`: focusing a text field starts
Minecraft 26.3 text input, and unfocusing or closing the window stops it. This
restores typed letters in search, map names, trader settings and contributor keys.

The native A* window now includes:

- Trader status, current task, observed purse and confirmed receipt profit.
- Start/stop and Books/General/Both mode.
- Capital limit, purse reserve, and active book/item slot limits.
- Calculator analysis, automatic route selection, and the account dashboard.
- AUTO/COMMAND/NPC Bazaar access and account skill checks.
- Regional daily rests, time zone, and the existing login/logout range fields.
- Explicit save/review and reload controls.

Settings edits validate before an atomic file replacement. A bad value or failed
write preserves the last working settings. Editing requires stopping trading.
Fresh installs still require reviewing the capital and reserve defaults; the
**Save reviewed** control acknowledges them. Start closes the window before
transaction menus open. F6 toggles trading on and off; F7 exports diagnostics and F8 reloads the config.

Advanced limits, recipe selection and production commands remain available in
the existing config and command system.

With scheduled sessions enabled, starting during an offline/rest window logs
out immediately before starting trading or saved-position recovery. The rest
screen waits until the next scheduled online window, then reconnects and resumes
normal recovery. A held cursor item, pending transaction or safety block prevents
that immediate logout. Starting inside an online window still starts trading.

## Bundled calculator: one mod install

### Account requirement gates

Book routes check the Enchanting minimum before selection, purchase navigation
and a new combine operation. The minimum is inclusive: Overload requires 33,
so 32 is skipped and 33 is accepted. A fresh start reads the Skills menu once;
opening it manually updates the observation too. This check is mandatory in
Books/Both mode even if the optional general skill check was disabled. Unknown
levels skip restricted routes. Existing completed books can still be sold.

Crafting checks the recipe's collection, skill, Slayer, Heart of the Mountain
and reputation requirements before queueing and before opening the craft menu
or moving ingredients. Collections and other unlocks come from the selected
Hypixel profile through the bundled companion and the shared private-key
service. No personal API key is required in the mod. The lookup must match your
username and observed Enchanting level, have one selected profile, and be less
than five minutes old. Missing/private API data or unrecognized requirements
block the recipe with a reason. Open Your Skills and allow the profile lookup
to finish before queueing a restricted craft. Disconnects and profile-switch
messages clear observations; switching profiles requires restarting trading.
The server action's requirements are checked again before submission.

Rules come from the bundled [wiki-derived enchant data](../tools/bazaar-calc/licenses/enchants.json)
and [NEU recipe data](../integrations/goofyaddons/src/main/resources/goofyaddons/production-recipes.json),
including parsed item-lore restrictions. `node tools/bazaar-calc/build-requirements.mjs`
rebuilds the gates. The wiki Enchanting minimum can describe using an enchantment;
the trader conservatively uses it as an entry restriction as requested. Catalogs
can lag game updates, so server rejection still blocks execution. These checks
cover the existing inventory-backed crafting executor; automatic craft input
buying and the full Kat/Forge trading loops remain unfinished.

The calculator, dashboard, continuous market collector and optional Discord
companion now ship **inside the mod JAR**. Install the combined JAR in your
Minecraft instance's `mods` folder, alongside Fabric API. No calculator folder,
Node installation or PowerShell startup command is needed.

The background service starts automatically with Minecraft. If Node 22 or newer
is already available, it uses it. Otherwise it downloads a private Node 24.14.1
runtime from **nodejs.org**, verifies a pinned SHA-256 checksum, and caches it for
later launches. This supports Windows, macOS and Linux on x64/ARM64. First launch
requires internet access and may take longer; it does not block the game thread
or install anything system-wide. Runtime failures appear in the Macros page.

Use **G → Macros → Market and account checks**:

- Valid settings save automatically: toggles and mode choices save immediately;
  text fields save after 750 ms without editing, when leaving the field, or when
  closing the screen. Invalid fields stay unsaved with an explanation. Changes
  made during trading wait until trading stops while the screen is open. Failed
  writes remain pending; **Retry save** retries, and **Discard** drops pending edits.
  Calculator restarts happen only after a validated save. Private contributor-key
  enrollment retains its separate explicit save control.
- **Background service** shows startup/download/running/failure status. When a
  start fails, the status carries the exit code and the last line of
  `companion-error.log`, and the detailed diagnostics export includes the last few
  log lines with keys and tokens removed. If another calculator already answers on
  the port with a different version, the status says so instead of using it silently.
- **Dashboard** opens the local site in your browser.
- **Retry / restart** retries startup or restarts the service owned by this mod.
- **Calculator port** changes the local port for the service, dashboard, account
  lookup and trade feed together. If 8789 is occupied, stop trading, choose a free
  port such as 8790; the bundled calculator restarts once on the new port. The mod identifies
  unrelated listeners without stopping them or repeatedly launching a conflicting
  process. Existing history and private keys stay in the same data folder.
- **Auto-start** can be disabled when you prefer a separately managed calculator.
- **Calculator**, **Automatic routes**, and **Account dashboard** still control
  analysis, route selection and account publication independently. Installing or
  starting the service does not start trading or enable Discord uploads.

Public Bazaar prices continue collecting every 20 seconds while Minecraft is
open, including scheduled rests. A compatible calculator already listening on
the configured port is reused and never terminated by the mod. Close that older
service once to switch to the bundled version. Only one Minecraft instance can
own a managed collector against the same persistent data directory.

The mod closes its own service when Minecraft exits. A parent pipe also shuts it
down after a client crash. Saved history and private Discord/community settings
stay in **%LOCALAPPDATA%\GoofyAddons\bazaar-calc** on Windows,
`~/Library/Application Support/GoofyAddons/bazaar-calc` on macOS, or
`~/.local/share/GoofyAddons/bazaar-calc` on Linux (or your existing absolute
`GOOFY_BAZAAR_DATA_DIR` override). Replacing the mod preserves these files.
Versioned extracted programs and the private runtime are under `managed` in that
data folder; user history stays outside those versioned programs.

For Discord, open **G → Macros → Discord companion → Open settings**, fill in the
private configuration, and use **Retry / restart**. Missing pairing files are
created automatically; existing settings and keys are preserved. See
[DISCORD.md](DISCORD.md) for bot/webhook setup. Startup errors are recorded in
`companion-error.log` in the persistent data folder.

The source companion remains available for development or collection while
Minecraft is closed: `node tools/bazaar-calc/server.mjs`.

## Navigator ownership and NPC access

The trader uses A*'s real navigator for a **loaded Bazaar NPC**. It discovers the
actual NPC/hologram, chooses a nearby standable point with interaction visibility,
and plans a walking route. It does not hardcode another island's coordinates.
Failed approach points are not repeated indefinitely, and the trader retains its
existing navigation timeout.

Menu opening, stop, manual input, world changes and trading pauses release owned
navigation. Trading and a manual A* route/trace playback cannot own movement at
the same time. The adapter never cancels an unrelated replacement route as though
it owned it. Teleport movement is not enabled for NPC access in this stage.

An NPC outside loaded entities still requires coming into range. Whole-Hub
landmark travel, Kat/Forge/AH physical access, and background wandering remain
separate integration work.

## Forecasts, decisions and capability labels

Calculator reports carry one provenance block (`forecast`): quote and history
times, the scoring order, the calibration model and how many personal and shared
trades calibrated the rows, plus the mode, capital, inventory and slots the
forecast assumed. Every row says whether the trader would run it (`AUTOMATIC`) or
shows it for research only (`RESEARCH`). Every skipped route is counted under one
reason in `filterReasons` (for example `minimum-margin`, `enchanting-level`,
`not-in-automatic-catalog`), and routes you configured, or that the automatic
catalog supports, are listed in `deferred` with their reason. Rankings are still
computed against your total trading capital with one free slot per engine, so
full position slots never hide them.

When automatic selection hands a route to an engine, **Last route decision** on
the Macros page says why: its coins/hour, its place among executable and all
ranked routes, whether the estimate is calibrated, and which higher-scored routes
were passed over and for what reason. The same record is in the detailed
diagnostics export and the `market.automatic_decision` event. The engine still
applies every live check; the decision explains, it does not authorize.

**What runs automatically** on the Macros page lists each feature as Research,
Queued or Automatic with the boundary that justifies the label. These labels come
from one registry, so the screen, diagnostics and documentation cannot disagree.

## Auction House prices (Coflnet)

Automatic `production test` sales price from matching BINs observed in `/ah`.
The bundled calculator fetches Coflnet prices for validation and initial fee budgeting. To use your Coflnet API token, put it on the first line of
`coflnet-token.txt` in the calculator data folder (on Windows
`%LOCALAPPDATA%\GoofyAddons\bazaar-calc\coflnet-token.txt`), or set the
`COFLNET_TOKEN` environment variable before starting Minecraft. The token stays on your
computer: it is sent only to sky.coflnet.com, never written to logs, and must never be
committed or built into the mod, since anything in the repository or the jar is public.
Without a token the public, rate-limited API is used.

## Production scope and validation

Existing book/general trading, inventory crafting, opt-in BIN listing commands,
saved arrays, scheduled rests and gameplay evidence collection are retained.

A production run chains the existing, separately tested steps into one loop: get
the inputs, process them, wait for and claim a timed result, and optionally list
the output as a BIN. Queue one, then use the trading toggle:

| Command (under `.a* goofyaddon production`) | Loop |
| --- | --- |
| `run <ITEM> <batches> [binPrice maxFee]` | Inputs, craft, optional BIN listing |
| `test <ITEM> [binPrice]` | Runs at once, without the trading toggle (traders stay off; the rest schedule and transfers do not gate it; the toggle or `stop` ends it). One craft batch end to end: buys missing inputs for this run only and crafts. With a price it lists the result as a BIN with a fee ceiling for that price; without one, a Bazaar product is sold instantly on the Bazaar (at no less than 97% of the fresh quote) and anything else is listed one coin under the matching BIN observed in `/ah`, validated against a fresh Coflnet price |
| `forge <ITEM> <slot> [binPrice maxFee]` | Inputs, Forge submission once you open The Forge, wait, claim, optional listing |
| `kat <PET;rarity>` | Kat upgrade once you open the Pet Sitter with the pet placed, wait, claim |
| `claim <job> [binPrice maxFee]` | Claims a Forge or Kat job already waiting in `production jobs` |
| `status` | The run's stage and what it waits on |

- Inputs come from your inventory. **Production buys inputs** (Spending limits)
  lets a run instant-buy only what is missing, within spendable capital and at most
  3% above the fresh Bazaar depth quote. It is off by default; leave it off until
  the in-game checklist passes. `test` buys missing inputs for its one run even
  while the setting is off, with the same limits.
- Every stage boundary is saved in the production journal. A buy, craft, submission,
  claim or listing whose effect is not proven sends the run to review and pauses
  trading. Nothing is ever repeated automatically, and a restart turns an
  interrupted step into review, as for every other production job. A Forge or Kat
  timer survives a restart; use `claim` to finish it.
- A BIN price lists the whole output stack, so keep exactly one stack of the output.
  Sale proceeds are not counted as profit until the sale is seen.
- Choosing which item to produce stays with you: production recommendations are
  still research only.

See the preserved production and auction guides under `integrations/goofyaddons/docs`.

The combined build runs the A* core/pathing/movement tests, the trader regression
tests, keyboard migration/config-write tests, and a real Java-to-Node calculator
integration. Compilation and headless checks are verified. A live Minecraft GUI
and Hypixel NPC/transaction session have not been exercised in this cloud environment.

## Build and provenance

```powershell
.\gradlew.bat -p client test calculatorIntegrationTest build
```

The resulting mod is `client/build/libs/astar-client-0.2.14-BETA.jar`. The existing
top-level headless library build remains separate.

GoofyAddons source is retained under `integrations/goofyaddons`; `UPSTREAM.json`
records the imported repository and commit. Its standalone Fabric descriptor is
excluded from the combined resources. A*'s descriptor initializes the trader once,
then A*, and registers the trader's packet mirror mixin. Both license notices are
included in the JAR: A*'s PolyForm Noncommercial license and the trader's MIT notice.

### Versions and support bundles

One release manifest lists what an install is made of: the mod, Minecraft, Fabric,
Java, the calculator protocol and bundle digest, the upstream calculator commit,
the forecast contract, the saved-file layout and the journal and config schemas.
The running calculator reports its bundle, upstream commit and forecast contract
on `/health`, and the manifest lists every disagreement with what this mod bundled
(for example an older calculator still answering on the port). See it with
`.a* goofyaddon debug version` or **Versions** on the Macros page.

`.a* goofyaddon debug export` now also carries the manifest, the calculator
supervisor state and the last 40 lines of `companion.log` and
`companion-error.log`. Keys, tokens, passwords and long secret-like strings are
removed when the tails are read and again when the bundle is written; contributor
keys and Discord credentials are never included.

### Keybinds

In **G → Macros → Keybinds**, click **Change** next to an action, then press its new key. Escape cancels. Changes are saved immediately while trading is stopped and also appear in Minecraft Controls. Defaults are F6 for trading on/off, F7 for Debug export, and F8 for reloading. Paused or recovering trading and armed rest schedules count as on: the toggle stops them. Starting requires a connected world with no menu open. Old default J/K/M controls migrate to the new defaults; a customized old start key becomes the toggle.

### Unified commands

Type `.a* goofyaddon` for help. All trader commands now use this local namespace and A*'s Tab completion and Up/Down history:

- `.a* goofyaddon start`, `stop`, `toggle`, or `status`
- `.a* goofyaddon mode books`, `general`, or `both`
- `.a* goofyaddon reload`
- `.a* goofyaddon debug export`
- `.a* goofyaddon profit` (and `hud`, `reset`, `left`, `right`, `scale`)
- `.a* goofyaddon schedule` (and `on`, `off`)
- `.a* goofyaddon craft PRODUCT_ID [BATCHES]`
- `.a* goofyaddon production jobs` or `recipes PRODUCT_ID`
- `.a* goofyaddon auction inspect`, `prepare PRODUCT_ID PRICE`, or `sell PRODUCT_ID PRICE MAXIMUM_FEE`

The old standalone `/goofy...` commands are no longer registered. `.astar goofyaddon ...` is also accepted through A*'s existing prefix alias.

### Unloaded Bazaar icons

During book or ordinary-item transaction navigation, a server menu action appearing as vanilla bedrock is treated as unloaded. The trader waits 1.5 seconds before reopening the product flow, with a maximum of three reopens per operation. It then revalidates the menu, item and transaction normally. Inventory bedrock does not trigger recovery, and a held cursor item prevents closing. Transactions already submitted stay in verification and are never restarted by this recovery.

### Discord and transfer recovery

The optional **Discord companion** card enables paired status updates, contact alerts, and manual remote controls. Follow [DISCORD.md](DISCORD.md) for setup. Discord credentials live in the companion's persistent private data folder. Active server transfers now trigger normal saved-position recovery after the new SkyBlock world stabilizes; no forced Hub/island round trip is used.

### Contributor settings

**G → Macros → Shared gameplay learning** provides the collector address, public
dataset repository, masked contributor-key input, upload toggle, save and forget
controls, and sync status. Uploads default off; public downloads need no key.
Keys are saved only in the companion's private persistent data folder. See
[SHARED-LEARNING.md](SHARED-LEARNING.md) for tester setup, importing keys from
other generators, and owner approval/revocation.

The full manual calculator is available from the dashboard’s **Full calculator** link.
See [Account calculator](ACCOUNT-CALCULATOR.md) for username/profile imports,
private Worker provisioning, budgets and standalone/public website use.

## Cancellation race recovery (0.2.8)

If a buy order fills while its cancellation is being processed and SkyBlock
reports “You have goods to claim on this order!”, the general trader reads the
owned order again, claims its filled units, waits for the inventory delta, and
cancels the remaining order. It retains the position until the remainder is
verified absent and sells only acknowledged inventory. A blocked book suggestion
in a calculator response is filtered without discarding valid General proposals;
all other response validation and pre-purchase requirement gates remain active.

## Aged buy-order exits (0.2.10)

The general trader claims filled units and cancels the remainder of an aged buy
order instead of pausing before cancellation. A fully refunded aged order is
retired rather than immediately replaced. Claimed stock may still be listed at
the current offer after the age limit; quantity, ownership, price and drawdown
checks remain active. Existing saved positions and costs are preserved.

If a sell offer completes while cancellation is being processed, the trader
rechecks the owned offer's full fill and claims its coins instead of waiting
for items to return. It releases the position only after the sale receipt and
the order's disappearance are confirmed.

## Cleanup and connection fixes (0.2.14)

Book cleanup accepts truncated Bazaar product titles only when the full product
icon and instant-sale control lore agree on the exact book level. Ownership,
quantity, receipt and inventory-disappearance checks still apply. A schedule
cancelled by a trading safety block keeps the original trading error visible.

Health checks, market analysis, account lookup, trade telemetry and Discord's
local bridge use direct loopback HTTP/1.1 connections instead of inheriting a JVM
proxy or attempting protocol upgrades. Public API and download networking stays
unchanged. A genuine occupied port still requires changing Calculator port or
closing the program that owns it; an old unhealthy process is never terminated
by the mod.

## Debug export from a GUI

Press **F7** (or your assigned **Debug export** key) while the target GUI is open.
The GUI stays open. The equivalent command is `.a* goofyaddon debug export`.
The resulting ZIP is saved in the Minecraft instance's
`logs/goofyaddons/bundles/` directory; chat reports its full path.

The ZIP includes `current-menu.json` with the menu title, slot numbers (including
empty slots), item names, product IDs, hover descriptions, cursor stack, and
visible Personal Compactor settings. It also includes `snapshot.json`, diagnostic
events, trading and recovery state, profit/execution records, release versions,
and bounded calculator log tails. Known credentials are redacted and raw item
NBT/item UUIDs are excluded from the menu capture. No collection command sends
chat to the server or clicks a menu control.

This captures any currently observed GUI, including compactor, crafting, Bazaar,
and Auction House menus. It does not scrape unopened menus or account data that
hasn't been observed. When reporting a menu bug, export with the affected screen
open and share the ZIP. The individual auction/compactor inspect commands remain
available for focused captures.

## Automatic craft flips (0.2.27)

Choose **CRAFT** under **G → Macros → Mode**, then start with F6. This mode runs
production, crafting and sale verification; book/general order selection is disabled.
Set **Craft flips → Sale market** to **BAZAAR** or **BOTH** for automatic Bazaar
crafts. AH routes currently appear as previews for individual production tests.

The local dashboard's **Craft production plan** and `.a* goofyaddon production flips`
show current candidates, batch costs, conservative net profit, prerequisites and
blocked-route reasons. The ranking updates every 20 seconds even while stopped.
See [craft discovery and execution](CRAFT-FLIP-PIPELINE.md) for the source contracts,
fee/depth model, profit accounting and remaining AH settlement work.
