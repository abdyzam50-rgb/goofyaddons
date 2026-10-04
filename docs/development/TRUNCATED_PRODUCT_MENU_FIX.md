# Truncated product menu navigation — 1.3.30

In diagnostics-1791072514884-3a225546.zip, the latest session used mod 1.3.29.
At 00:06:31 UTC the general trader searched Super Compactor 3000 and reached its
product menu. At 00:07:01 it paused in PRODUCT with a 31-unit PLANNED position,
submitted=false and purchasePriceKnown=false. No buy was submitted in this path.
The final snapshot retains approximately 4.59m in provisional capital.

The loaded title was `Compactors ➜ Super Compactor 30`. Slot 13 carried ID
SUPER_COMPACTOR_3000 and full item name Super Compactor 3000. Slot 15 was Create
Buy Order and slot 16 Create Sell Offer; both carried the full product name in
lore. The previous openProduct check required the full name in the shortened title
and ignored this stronger evidence, waiting until the transaction deadline.

GeneralFlipper now recognizes the product page in both OPEN_PRODUCT and PRODUCT
from the icon ID and selected control's full-name lore. This works after a search
result or when the command opens the item directly. Readable conflicting IDs are
rejected even if the title matches. A readable full icon name plus control lore
supports layouts without a native icon ID. Existing full-title layouts without
an identity icon remain supported. Search-result navigation runs only when no
order-creation control is present; contradictory product evidence cannot trigger
a fallback click on the product icon. Confirmation and submission checks remain.

Four new production-state-machine regressions cover the captured shortened Super
Compactor page after search and direct navigation, a shortened sell title, and
conflicting IDs/lore or missing product metadata. A simulated search click receives
a server menu acknowledgement. The full build passes 402 Java tests, plus separate
Java-to-Node integration. The unchanged companion's prior 23 tests remain applicable.
Live Minecraft validation is still needed.

This fixes the latest PRODUCT timeout. Earlier rotated sessions also contain
separate missing-order and travel pauses; they are not evidence that this product
menu submitted an order, nor are they automatically resolved by this change.
