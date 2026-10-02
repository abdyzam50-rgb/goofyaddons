# Testing build 1.3.7-BETA

A jar built from the `claude/refactor-plan` branch is in [`dist/`](dist/). Download it from
the GitHub file view (**Raw** / the download button), not by copying the page.

```
dist/goofyaddons-1.3.7-BETA.jar
sha256 094b343017778b15a4583bcf319a43affcded152b968fa3e3eb2f7e692b303f2
```

## What you need

| | |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.2 or newer |
| Fabric API | 0.147.0+26.1.2 |
| Java | 25 |

Drop the jar and Fabric API into `.minecraft/mods/`. The mod writes its config to
`.minecraft/config/goofyaddons.json` on first launch; edit it, then press `\` (backslash)
while stopped to reload it.

Keys: **J** start, **K** stop, **M** cycle mode (Books / General / Both).

## Please read this before running it

This build has **never been run against a live server by its authors.** The test suite
passes (193 tests) and it compiles and builds, but nothing here proves the menus, order
timings or tooltip formats behave the way the code assumes. Treat it as a first live trial,
not a release.

Specifically:

- **Start with an amount you would not mind losing.** Set `maxTradingCapital` low and
  `purseReserve` to most of your purse, and use one cheap book route.
- **Expect it to pause and ask you to reconcile.** That is the designed behaviour when
  anything is unclear, and it is the good outcome. "Trading paused: ..." means it stopped
  rather than guessed.
- **A known open issue can lose books.** When a book disappears from a storage page the
  engine currently treats that as a successful move. If a menu is slow or the wrong page
  opens, a book can be recorded in the wrong place. Watch your storage.
- **Automated Bazaar trading is against Hypixel's rules.** This clicks menus for you. The
  realistic risk is to your account, not just your coins. That is your decision to make,
  but make it knowingly.

## What changed in this build

Fixes to the parts that stopped it completing a cycle:

- Task ordering was inverted, so collecting a finished sale ranked below everything and new
  buy orders ranked near the top. Sales should now be collected promptly instead of the
  engine drifting into fresh positions.
- A placed order that nothing looked at used to park forever if its chat notice was missed.
  A read-only re-check now re-reads it after 3 minutes (`bookOrderRecheckSeconds`).
- A completed flip left its whole cost committed against any leftover book, which slowly
  drained available capital until nothing could be bought.
- Full storage on both pages, and a store/retrieve cycle at the anvil, both used to spin
  until a watchdog noticed. Both now stop with a reason.
- Stopping used to leave claim state behind, which could silently wedge the engine on the
  next start.

The status panel was also rebuilt: one card instead of two, confirmed profit as the single
large number, signed so a loss reads as a loss without relying on colour, and rows that are
dropped rather than silently clipped when the window is short or the HUD scale is high.

## Reporting a problem

Run `/goofydebug export`, which writes a ZIP under `.minecraft/logs/goofyaddons/bundles/`,
and send it with roughly when the problem happened. **Look inside the ZIP first** — it
contains item names, prices and trade receipts. It does not contain your account details,
chat, or server addresses.

Useful events to look for if you want to check the new behaviour yourself:
`books.order_recheck_due`, `books.order_rechecked`, `books.extra_exposure_resized`.

## Building it yourself instead

```
./gradlew build      # needs JDK 25; jar lands in build/libs/
```
