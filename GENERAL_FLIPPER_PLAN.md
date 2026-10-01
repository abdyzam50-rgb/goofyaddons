# Books, General, and Both

1. Add explicit modes with a key to switch at a safe menu boundary. In Both,
   both engines monitor orders, while a round-robin scheduler grants exclusive
   ownership of Minecraft menus to one engine at a time.
2. Share capital reservations and item ownership between engines. Outstanding
   orders count toward the trading cap; coin costs already deducted from the
   purse are not deducted again. Pending purchases reserve the remaining purse.
3. Add an allowlisted ordinary-item flipper with tax-aware margins, historical
   execution-volume filters, item/batch caps, partial-fill recovery, confirmation
   checks, and bounded repricing. Persist active orders across mode switches.
4. Expose Books/General/Both in config and Minecraft Controls. Add regression
   tests for scheduling, shared budgets, and general candidate filtering. Build
   and provide a combined-mode sample config and updated artifacts.

Server GUI workflows require live testing. Both mode means concurrent market
tasks, with serialized clicks rather than two engines clicking the same menu.

## Verification

Implemented Books / General / Both, exclusive menu scheduling, shared capital
reservations, persisted ordinary-item positions, partial-fill handling, bounded
repricing, and an example combined config. The Java 25 build and all 30 regression
tests passed. Server GUI execution has not been validated in-game; this remains a
draft beta for small-order testing. See GENERAL_FLIPPER.md.
