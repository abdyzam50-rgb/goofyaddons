# Book cleanup instant-sale warning (1.3.47)

Diagnostic session `2f0b90f3-2700-4e6b-b2a2-e2150c540c94` retrieved Karma III,
opened its exact product menu, and clicked Sell Instantly (18373). Hypixel then
opened `Confirm`, warning that the current instant-sale price was far below the
seven-day average. The button explicitly listed Karma III, `Matched Amount: 1x`
and `Price: 464,374 coins`. The book remained in inventory, but the cleanup
controller only waited for a Sold receipt, so it timed out after three minutes
(18398). This was an unhandled warning screen, not an ignored navigation click.

Cleanup now handles this exact historical-price warning in its sale phase. It
requires a new loaded container, 1.5 seconds of stable contents, empty cursor,
all 36 observed inventory slots, one Confirm button at slot 13, exact product
identity and warning text, exact owned inventory/Matched Amount agreement, and
a finite positive displayed price. It checkpoints intent before confirming once.
It still requires the matching Sold receipt and observed inventory disappearance
before accounting for the sale or releasing ownership. A stale/unacknowledged
confirmation is not clicked again. Wrong item, quantity, unreadable price or
unsaved intent blocks without selling.

The existing user-authorized cleanup policy allows instant selling abandoned or
stalled holdings at current prices, including a loss. The warning compares against
historical prices; it does not introduce a new minimum-profit rule for exits.
Final confirmation handling is limited to this verified cleanup warning.
Diagnostics now include cleanup phase, product level/quantity, warning status
and receipt status to make future stalls identifiable.

Tests cover the captured Karma III warning alongside an unrelated Last Stand II,
intermediate-level cost units, receipt/disappearance settlement, one-shot click,
wrong product/amount, unreadable price, extra unowned books and persistence failure.
Build and calculator integration checks pass; live Minecraft verification remains.

Install the 1.3.47 JAR and restart Minecraft, preserving configs and journals.
The 1.3.46 companion remains current and does not need another update.
