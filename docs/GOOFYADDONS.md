# Trading inside A*

The combined **A* with GoofyAddons 0.2.16-BETA** targets Minecraft **26.3**,
Fabric Loader **0.19.5 or newer**, Fabric API **0.161.0+26.3**, and Java **25**.
It includes the trader from GoofyAddons 1.3.57 in one client JAR.

The 0.2.15 foundation refactor introduces a separate trading-core module and
injects the general trader's runtime services and order repository. Existing
order files retain their format and paths. See the [foundation notes](../trading-core/README.md).

The 0.2.16 book-engine refactor adds the same dependency separation, replaces raw
game-slot reads with captured menu observations, and retains the book journal's
existing record fields. See the [book refactor log](BOOK-ENGINE-REFACTOR.md).

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
memory. Old default J/K/M bindings become F6/F7/F8; customized start and mode keys are retained. The new `keyCodeSchema: 2` marker is written when
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
transaction menus open. F6 toggles trading on and off; F7 switches mode and F8 reloads the config.

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

- Settings on this page are a draft. Typing never saves the file or restarts the
  calculator. **Unsaved changes** lists what changed, any field that needs fixing,
  and which restart Apply will cause; **Apply** validates and saves everything at
  once, and **Discard** drops the draft. A keybind or reload saved while a draft is
  open is kept when the draft is applied.
- **Background service** shows startup/download/running/failure status. When a
  start fails, the status carries the exit code and the last line of
  `companion-error.log`, and the detailed diagnostics export includes the last few
  log lines with keys and tokens removed. If another calculator already answers on
  the port with a different version, the status says so instead of using it silently.
- **Dashboard** opens the local site in your browser.
- **Retry / restart** retries startup or restarts the service owned by this mod.
- **Calculator port** changes the local port for the service, dashboard, account
  lookup and trade feed together. If 8789 is occupied, stop trading, choose a free
  port such as 8790, and press Apply; the bundled calculator restarts once on the new port. The mod identifies
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

## Production scope and validation

Existing book/general trading, inventory crafting, opt-in BIN listing commands,
saved arrays, scheduled rests and gameplay evidence collection are retained.
Automatic craft/Kat/Forge/AH buy/process/sell route selection remains unfinished;
the integration does not turn the previously research-only routes into executable
ones. See the preserved production and auction guides under
`integrations/goofyaddons/docs`.

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

### Keybinds

In **G → Macros → Keybinds**, click **Change** next to an action, then press its new key. Escape cancels. Changes are saved immediately while trading is stopped and also appear in Minecraft Controls. Defaults are F6 for trading on/off, F7 for switching mode, and F8 for reloading. Paused or recovering trading and armed rest schedules count as on: the toggle stops them. Starting requires a connected world with no menu open. Old default J/K/M controls migrate to the new defaults; a customized old start key becomes the toggle.

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
