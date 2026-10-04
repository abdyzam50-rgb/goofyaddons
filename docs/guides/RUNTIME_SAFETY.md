# Runtime safeguards (1.3.25-BETA)

Both engines pause together on uncertain ownership, failed state writes, or transaction errors. They retain positions instead of treating missing orders or unrelated purse increases as proof of completion.

## Order and inventory checks

General trading only inspects a recognized, settled Manage Orders screen with a footer. Duplicate orders, pagination, and unreadable/mismatched quantities stop execution. A completed sale needs a claim initiated by this engine, an exact item/quantity claim receipt, a missing sell order in the loaded orders screen, and no remaining inventory. Cancellation no longer accepts unrelated purse increases. General sale claims allow ten seconds for the receipt and order/inventory packets to agree. An unchanged claim may be retried up to three times after a stable observation window; placement and cancellation confirmations are never blindly repeated.

Book buy claims wait for the expected input books to arrive in inventory. Book sell ownership is retained until a matching sale claim is seen and the order/inventory is absent. Blind restart and missing-order task deletion were removed. New book purchases, including final buy confirmation, must meet the configured minimum net profit. Completed held books may sell or reprice at the verified current offer price, including below that target or at a loss; holding age/drawdown limits remain. Unreadable prices, stale quotes and mismatched confirmations still block the transaction.

Unknown general products are treated as unstackable when sizing buys, with four empty inventory slots reserved. Claims/cancellations check available space using actual matching item stack limits when available. This deliberately reduces quantities; `maxItemsPerOrder` is a ceiling, not a promised batch size.

API source timestamps must be within 60 seconds and no more than five seconds in the future. Both engines check source age before buying. Book confirmations reject expired quotes. General sales of already-owned stock may use the verified live menu price even when API quotes expire; item, quantity, price and holding-limit checks still apply.

## Stalls and holding limits

The book watchdog pauses after 60 seconds without observable transaction progress or five minutes in one transaction. Idle order waits are excluded. General menu steps retain their 30-second timeout. The empty AntiStuck stub was removed; the watchdog runs inside the book engine.

General settings `maxHoldingSeconds` (default 21600) and `maxDrawdownPercentage` (default 15) pause trading when a holding exceeds six hours or its current net sell-offer reference falls 15% below recorded cost. Book settings `maxBookHoldingSeconds` and `maxBookDrawdownPercentage` have the same defaults. These are review triggers, not automatic liquidation or guaranteed executable exit prices. Held stock may be sold or repriced below the entry profit target, including at a loss within these limits. Already-completed general sales may be claimed even if the market subsequently falls, because claiming earned proceeds does not liquidate more stock.

## Restart/recovery

Outstanding book ownership is written to `config/goofyaddons-book-orders.json`
before menu work and after state changes. Stops/disconnects retain the journal;
state-write failures preserve it and block trading. An empty journal clears a
stale in-memory recovery flag on the next start.

A readable non-empty book journal starts an automatic **read-only verification**:
closed inventory (including offhand), both configured storage pages, then one
settled order-management visit. Each complete layout must remain stable for 1.5
seconds. Verification runs before either engine may buy, claim, cancel, combine
or sell; saved capital stays reserved while checking. It covers the saved routes
even if they are no longer in the config or fail current entry-profit filters.

Records absent from all those observations are retired automatically, their
capital released, and normal startup continues if no records remain. The original
file is saved as a `.verified-<id>.bak` sibling first, and the journal is reread
before replacement to reject concurrent changes. This does not invent sale
receipts, profit or a history of what happened to those books.

If matching owned books or orders are actually found, their records stay reserved
and the pause names those enchantments. Resolve those positions, then press **J**
to recheck. No file deletion, manual archiving or Minecraft restart is needed for
verified stale entries. Unreadable identities/creators, pagination, incomplete
menus and a 45-second timeout preserve the journal and explain why verification
could not finish. Co-op order icons do not count as physical holdings; another
player's readable order is excluded from this player's ownership check.

Corrupt journals still require fixing the file before restarting. General
positions use `goofyaddons-general-orders.json`; their reconciliation behavior is
unchanged. The legacy book journal lacks exact trade ids, quantities and receipt
state, so actual outstanding positions still cannot be replayed automatically
from that file. Before upgrading from a build without any book journal, reconcile
pre-existing book positions manually: unrecorded ownership cannot be reconstructed.

Switching modes at a safe boundary preserves book tasks in the same world. Travel with outstanding books requires review; scheduled reboot recovery can therefore pause instead of automatically continuing. Instant book buy/sell paths are blocked for manual handling until their transaction confirmations are implemented and verified. The supplied order-based config uses zero instant thresholds.

## Validation and remaining limits

Regression tests cover source age, missing/duplicate/paged orders, exact receipt matching, conservative capacity, holding thresholds, watchdog timing, and journal persistence/corruption. Automated tests do not validate Hypixel's live menu titles, lore, slot numbers, chat format, or network delivery behavior. Unrecognized formats pause. Supported saved book positions resume from live verified evidence in 1.3.29; legacy purchase costs remain unknown. Full automatic crash replay, multi-page order reconciliation, stack-aware optimal sizing of unknown items, and automatic loss liquidation are not implemented.
