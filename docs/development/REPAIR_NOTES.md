# Reliability changes (1.1.23-BETA)

## Behavior

- Bazaar HTTP calls have connect/request timeouts and validate the HTTP status
  and API success flag. Failed calculations can retry rather than staying busy.
- Mutable API results and order notifications are applied on the Minecraft
  client thread. Stop/reset invalidates callbacks from previous runs.
- Empty candidate lists retry every 20 seconds. Idle tasks also refresh quotes.
- Equal-price competitors no longer cause cancellation. Buy orders require a
  higher competing bid; sell orders require a lower competing ask.
- Pause suspends macro actions and API monitoring. Resume reconciles inventory
  and orders through startup. World changes pause the macro; J resumes it.
  Disconnects stop it. Scheduled reboot state resets on an explicit stop.
- Candidate profit includes the configured sale tax and minimum net-profit
  threshold. Pricing uses the top order prices rather than weighted quick-status
  prices; ranking uses weekly executed volume on both sides.
- Candidate selection reserves unplaced purchase costs against the purse.
  Outstanding buy orders are not deducted twice. The buy-order price screen
  checks affordability again and stops on insufficient coins or an invalid quote.
- Only one task can own an enchantment at a time, including pending sell orders.
  This prevents overlapping level routes from claiming the same inventory.
  Existing sell tasks are no longer deleted solely because book names match.
- Config reload rejects invalid delays, keys, levels, percentages, and null book
  entries. A rejected config preserves the file and last working settings.
  Reload with backslash is allowed only while stopped, once per key press.
- Config key values supply the initial Minecraft keybinding defaults. Existing
  bindings in Minecraft Controls still take precedence; restart after changing
  the JSON key defaults.
- Empty enchantment tags, short menus, and invalid assignment quantities are
  guarded. Malformed purse text returns an unknown purse instead of throwing.

## Added JSON settings

Existing JSON files remain compatible. Missing fields use these defaults:

```json
{
  "bazaarTaxPercentage": 1.25,
  "minNetProfit": 0
}
```

Set the tax to your actual account's sale tax. The minimum is coins per completed
book, after the assumed sale tax and input quote. Cancellation/listing fees,
anvil costs, price movement, and execution time are not included. Profit is a
selection estimate, not a guarantee of realized returns.

## Remaining work

The settings screen and AntiStuck implementation remain placeholders. Full
recipe validation, pending-order caps, realized profit accounting, have not been implemented. General item flips are added in the subsequent
1.2.0 beta; see ../guides/GENERAL_FLIPPER.md. Only configure known combinable routes.
Instant buy/sell inventory-confirmation paths still require an in-game audit;
use order-based transactions for initial validation. Existing unrelated orders
and exact GUI labels also require testing against the live server.

The regression suite exercises helper calculations, request recovery, callback
invalidation, order competition, budget reservation, and config preservation.
It does not simulate Minecraft menus, anvil clicks, or server travel. Test a
small batch in-game before increasing the trading purse.
