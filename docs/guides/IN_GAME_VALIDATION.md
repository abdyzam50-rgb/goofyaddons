# In-game validation checklist (cleanup stages 1–9)

Every stage of the cleanup passed its unit, integration and CI checks, but **none of it
has met a live Hypixel menu**. This checklist closes that gap cheapest first. Work
through it in order and stop at the first failure: each step says what to do, what
success looks like and what to send back.

**Before you start**

- Use a build from the cleanup PR. Keep a copy of `config/` (all of it, including
  `config/goofyaddons/`) before the first launch, so any surprise can be rolled back.
- Start with small limits: Capital limit 2,000,000, Book slots 1, Item slots 1.
- Leave **Production buys inputs** off until section F tells you to turn it on.
- After every failure, run `.a* goofyaddon debug export` before doing anything else
  and send the file from `logs/goofyaddons`. It now contains the release manifest,
  the calculator supervisor state and redacted calculator log tails.

---

## A. Startup, settings and versions (no trading)

1. Launch and join SkyBlock. Run `.a* goofyaddon debug version`.
   **Pass:** "components agree" and no Mismatch lines. **Send:** the line if not.
2. Open the Macros page. Change **Calculator port** by typing `8790` one digit at a time.
   **Pass:** **Unsaved changes** shows the draft and "On Apply, the bundled calculator
   restarts on port 8790"; **Background service** does not change while you type.
3. Type `87x` into Book slots. **Pass:** the field keeps `87x`, the summary says what to
   fix and **Apply** is disabled. Press **Discard**: fields return to saved values.
4. Set the port to 8790 again and press **Apply**. **Pass:** one restart, then
   "Running · live market collection" on 8790; dashboard opens on the new port.
5. Start any other program on port 8789, set the port back to 8789 and Apply.
   **Pass:** "Port 8789 is occupied by another service". Nothing is restarted in a loop.
   **Send:** the export, including `errorLogTail`, if the status is vague.
6. Change a keybind while a settings draft is open, then Apply the draft.
   **Pass:** both the keybind and the draft change are saved.

## B. Accounts and profiles

1. On a profile with **no** open positions from an older build, start trading.
   **Pass:** chat says "Adopted N file(s) with no open positions into …"; `config/goofyaddons/accounts/<you>/<profile>/`
   now exists; the old files in `config/` are unchanged.
2. If you have open positions from an older build: start trading on the profile that owns them.
   **Pass:** trading waits and asks for `profiles adopt` or `profiles setaside`. Run
   `.a* goofyaddon profiles adopt`. **Pass:** positions are verified read-only before trading.
3. Switch to another SkyBlock profile without restarting Minecraft and try to start.
   **Pass:** trading refuses and asks for a restart. Nothing from the first profile moves.

## C. Lifecycle

1. Start trading, then trigger a server transfer (warp or limbo) mid-order.
   **Pass:** trading pauses for travel and resumes by itself after the transfer.
2. Cause a safety pause (for example, close the Bazaar menu by hand while the bot is
   entering an order). **Pass:** the first reason stays visible; only your own
   start or resume clears it. The rest schedule and transfers cannot.
3. **Send:** the `lifecycle` block from an export after each, if anything surprised you.

## D. Trading under stress (books and items)

Run each with one slot per engine, and watch the chat and the Macros page.

| Scenario | How to cause it | Pass |
| --- | --- | --- |
| Lag | Play during a busy hour, or throttle your connection | No double click, no second order; slow menus only slow the bot down |
| Partial fill | Let a large buy order fill only partly, then let it be outbid | Only the filled units are claimed and counted; the rest is cancelled or repriced once |
| Cancelled order | Cancel one of the bot's orders yourself from **Manage Orders** | The bot notices the missing order, claims what filled, and never re-cancels |
| Disconnect | Pull the network cable right after a confirmation click | On rejoin the run waits for evidence; nothing is placed twice |
| Crash | Kill Minecraft from the task manager mid-claim | On restart the claim is checked against inventory, profit counts once |

After each: `.a* goofyaddon profit` shows no double-counted receipt, and
`config/goofyaddons/accounts/<you>/<profile>/goofyaddons-transactions.jsonl` has one
line per change. **Send:** the export after any row that fails.

## E. Forecasts and decisions

1. Turn on **Automatic routes** with free slots. Wait for a route to start.
   **Pass:** **Last route decision** names the route, its coins/hour and rank, and any
   higher routes it passed over with a reason.
2. Fill every slot. **Pass:** rankings on the dashboard stay populated (full slots do not
   hide them); the decision line explains what is deferred.
3. Configure a route you know is unprofitable. **Pass:** the export's report lists it
   under `deferred` with a reason such as `minimum-margin`.

## F. Production loops

Start with an item you can craft from what you already hold.

1. `.a* goofyaddon production run <ITEM> 1`, then the trading toggle.
   **Pass:** the craft runs once and the output is verified; `production status` says done.
2. Remove one ingredient and queue again. **Pass:** "Production waiting: Missing …";
   nothing is bought; adding the ingredient lets it continue.
3. `production run <ITEM> 1 <price> <maxFee>` with one stack's worth of inputs.
   **Pass:** the craft, then one BIN listing at that price with the fee within the limit.
4. Forge: hold the inputs, `production forge <ITEM> <slot>`, start trading, then open
   **The Forge**. **Pass:** the recipe is chosen and submitted once; `production status`
   shows the remaining time. Restart Minecraft, wait for completion, run
   `production jobs`, then `production claim <job>` with The Forge open. **Pass:** one claim.
5. Kat: place the pet in Kat's menu, `production kat <PET;rarity>`, start trading.
   **Pass:** one upgrade submission; later `production claim <job>` with the pet shown.
6. Quick end-to-end test: `production test <ITEM> <price>` with one ingredient missing,
   then the trading toggle. **Pass:** one instant buy of the missing amount, one craft,
   one BIN listing at that price; `production status` says done. Without a price, a
   Bazaar item is sold with one "Sell Instantly" click (hold none of it beforehand, since
   that sells every unit held); anything else stops after the craft. This buys even with **Production buys inputs** off, so use a
   cheap item.
7. Only after 1–6 pass: turn on **Production buys inputs**, keep Capital limit small, and
   queue a craft with one ingredient missing. **Pass:** exactly one instant buy of the
   missing amount, then the craft. **Watch for:** a confirmation screen after the amount
   sign. If one appears, the run stops in review by design; send a screenshot of it.

## G. What to send back

- For each section: pass, or the step that failed.
- The export from every failure, and screenshots of any menu the bot did not expect.
- `.a* goofyaddon debug version` output once at the end.

Automating Bazaar and Auction House menus is against Hypixel's rules; the realistic
risk is to the account as well as the coins. Decide that before section D.
