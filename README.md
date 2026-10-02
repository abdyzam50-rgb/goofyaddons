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

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
