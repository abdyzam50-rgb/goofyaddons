# Order-menu rechecks — 1.3.5-BETA

## Observed failure

Uploaded diagnostics-1790914468676-328740fe.zip, latest session 519a93a9-8afb-4105-a090-aaf459402848, running 1.3.4-BETA. The earlier Ink Sac sell offer was verified (sequence 92). The engine then submitted 30 Ectoplasm at 192,341.8 per unit (sequence 131) and immediately reopened orders. Hypixel subsequently reported escrow, submission and Sell Offer Setup! 30x Ectoplasm for 5,705,339 coins (sequence 143). That setup receipt is acceptance evidence, not realized profit.

The engine remained on orders container 12, containing only the Ink Sac offer, until its 30-second verification timeout. It did not close/reopen that stale order list. Ectoplasm had left inventory, but the tracked position was correctly retained as uncertain rather than released or automatically resubmitted.

## New behavior

MenuRecheck is a shared bounded observation policy. The initial unexpected result gets a 1.5-second settling interval, then the engine closes the menu and requests a fresh orders list. Subsequent refreshes are separated by at least 2.5 seconds. Up to three refreshes are allowed per transaction step/task; changing the diagnostic reason does not reset the budget. Existing absolute transaction deadlines remain in force. Rechecks never repeat confirmation, claim or cancel actions themselves.

General placement waits two seconds after submission before opening verification. Missing submitted orders, incorrect/incomplete verification menus, cancellation outcome/inventory mismatches, unsettled sale claims and unexpected absence of tracked positions use the refresh policy. The original step, trade ID, submission intent, quantity and capital reservation remain intact. Successful fresh observations follow the existing strict quantity/creator checks. Missing quantity/creator evidence also refreshes before rejection. Genuine identity conflicts still pause immediately. If evidence never resolves, the original timeout/reconciliation checks retain ownership and pause.

Book confirmation now enters VERIFY_PLACEMENT rather than marking the order placed immediately. It waits two seconds, loads an orders menu with footer controls and validates exact order quantity, price, creator/duplicate guards before moving the task forward. Capital is marked purchased only after buy-order verification. Missing placement gets the same refresh policy, then pauses without repeating confirmation. Pending intent remains covered by the conservative recovery journal; this does not implement a full replayable journal migration.

Existing book buy-order inspection/outbid and sell/reprice paths also refresh an unexpectedly missing order before their next decision, and require an actual supported orders title rather than any Bazaar title. Those legacy workflows still have separately audited reconciliation limitations; refreshing does not certify every existing post-retry decision. Storage/anvil transfer verification is not changed by this patch and remains its own repair stage.

Each refresh logs order.observation_recheck with attempt, trade, reason and detailed evidence before closing. Book diagnostics expose submitted trade/quantity/price/side and retry count. A cancellation fallback no longer infers order absence from an arbitrary non-orders menu.

## Validation

103 tests passed; production build and git diff --check passed. Five added MenuRecheckTest cases cover bounded close/reopen behavior without repeat submission, new-operation/reset budgets, a fresh Ectoplasm offer observation, mismatched book orders and invalid price evidence. Tests exercise the shared policy and predicates; whole-engine packet/menu replay and live-server validation remain outstanding. Version 1.3.5-BETA. No tracked ownership files were deleted or migrated.
