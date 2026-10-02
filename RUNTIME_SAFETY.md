# Runtime safeguards (1.2.1-BETA)

Both engines pause together on uncertain ownership, failed state writes, or transaction errors. They retain positions instead of treating missing orders or unrelated purse increases as proof of completion.

## Order and inventory checks

General trading only inspects a recognized, settled Manage Orders screen with a footer. Duplicate orders, pagination, and unreadable/mismatched quantities stop execution. A completed sale needs a claim initiated by this engine, an exact item/quantity claim receipt, a missing sell order in the loaded orders screen, and no remaining inventory. Cancellation no longer accepts unrelated purse increases.

Book buy claims wait for the expected input books to arrive in inventory. Book sell ownership is retained until a matching sale claim is seen and the order/inventory is absent. Blind restart and missing-order task deletion were removed. Price changes must still meet the configured minimum net profit when choosing book prices.

Unknown general products are treated as unstackable when sizing buys, with four empty inventory slots reserved. Claims/cancellations check available space using actual matching item stack limits when available. This deliberately reduces quantities; `maxItemsPerOrder` is a ceiling, not a promised batch size.

API source timestamps must be within 60 seconds and no more than five seconds in the future. Both engines check source age; book confirmations and general confirmations reject expired quotes.

## Stalls and holding limits

The book watchdog pauses after 60 seconds without observable transaction progress or five minutes in one transaction. Idle order waits are excluded. General menu steps retain their 30-second timeout. The empty AntiStuck stub was removed; the watchdog runs inside the book engine.

General settings `maxHoldingSeconds` (default 21600) and `maxDrawdownPercentage` (default 15) pause trading when a holding exceeds six hours or its current net sell-offer reference falls 15% below recorded cost. Book settings `maxBookHoldingSeconds` and `maxBookDrawdownPercentage` have the same defaults. These are review triggers, not automatic liquidation or guaranteed executable exit prices. Sell offers can remain outstanding until reviewed; no automatic loss sale is introduced.

## Restart/recovery

Outstanding book ownership is written to `config/goofyaddons-book-orders.json` before menu work and after state changes. Stops and disconnects retain this journal. State-write failure blocks trading. A cold start with book positions, or ambiguous travel/transaction recovery, blocks new trading and keeps their capital reserved.

To resolve a book recovery barrier:

1. Keep macros stopped. Inspect all Bazaar orders, inventory, cursor item, and both configured storage pages for the recorded enchantments.
2. Cancel/claim outstanding orders manually and account for the books and proceeds. Finish or remove the outstanding positions from the macro's working inventory/storage yourself.
3. Close Minecraft. Archive the book journal as evidence, then restart with a clean journal only after those positions have been accounted for.

Never clear a journal just to bypass the barrier while orders or inventory remain. General positions continue to use `goofyaddons-general-orders.json`; uncertain positions require manual reconciliation rather than automatic deletion. Before upgrading from a build without the book journal, reconcile any pre-existing book positions manually: this build cannot reconstruct ownership that was never recorded.

Switching modes at a safe boundary preserves book tasks in the same world. Travel with outstanding books requires review; scheduled reboot recovery can therefore pause instead of automatically continuing. Instant book buy/sell paths are blocked for manual handling until their transaction confirmations are implemented and verified. The supplied order-based config uses zero instant thresholds.

## Validation and remaining limits

Regression tests cover source age, missing/duplicate/paged orders, exact receipt matching, conservative capacity, holding thresholds, watchdog timing, and journal persistence/corruption. Automated tests do not validate Hypixel's live menu titles, lore, slot numbers, chat format, or network delivery behavior. Unrecognized formats pause. Full automatic crash replay, multi-page order reconciliation, stack-aware optimal sizing of unknown items, and automatic loss liquidation are not implemented.
