# GoofyAddons

Runtime safeguards and recovery instructions: [RUNTIME_SAFETY.md](RUNTIME_SAFETY.md).
Profit HUD, commands, and accounting details: [PROFIT_TRACKER.md](PROFIT_TRACKER.md).

## Trading modes (1.2.0-BETA)

Books, General, and Both modes share a trading budget and serialize menu actions.
J starts, K stops, and M switches modes at a safe transaction boundary. See
[the mode and configuration guide](GENERAL_FLIPPER.md) and the
[combined example config](examples/goofyaddons-both.json).

This is a draft beta: build and helper tests pass, but live server menus and order
confirmation timing still need validation with small orders.

**Trying it out:** a built jar and what testers need to know are in
[TESTING.md](TESTING.md). It has not been run against a live server by its authors — start
with an amount you would not mind losing.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.

### Diagnostics

Version 1.2.5 records structured JSONL events in `.minecraft/logs/goofyaddons/`.
Each event carries a timestamp, session ID and sequence number. Events cover
state/menu transitions, slot clicks and recognised commands, Bazaar receipts,
verified acquisitions/sales, API failures, config/journal/profit persistence
failures and safety pauses. Failure snapshots and exports include both engines'
retained positions, pending claims, recovery flags, inventory counts and capital.
Waiting states are recorded when they change, plus a 30-second heartbeat.

Use `/goofydebug` to check logging health and `/goofydebug export` to save a ZIP
under `logs/goofyaddons/bundles/`. Send that ZIP with the problem description and
approximate time of the failure. No automatic uploads occur. Private chat,
authentication credentials, player identity and server addresses are not collected;
known credential patterns and the home directory are redacted. Game item names,
prices, trading receipts and exception stacks remain in reports, so inspect an
export before sharing. Reports do not include raw Minecraft logs or raw save files.

Disk writes run on a bounded background queue. Files rotate at 2 MiB with five
archives; at most five exported bundles are retained. Queue drops and write errors
are surfaced by `/goofydebug`. Book progress no longer floods in-game chat.
Diagnostics help reconstruct observed failures; they cannot guarantee capture of
an abrupt process termination, JVM crash, or events lost to disk/queue failure.
