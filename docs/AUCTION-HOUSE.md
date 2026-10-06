# Auction House navigation and BIN listings

Mod **1.3.57-BETA** adds an opt-in listing operation for a stack already in
inventory. Companion **1.3.53-BETA** remains compatible. Keep the existing config
and saved trading files when replacing the JAR.

The listing navigator supports both creation paths:

- **Auction House → Create Auction → Switch to BIN → Create BIN Auction**
- **Auction House → Manage Auctions → Create Auction → Switch to BIN → Create BIN Auction**

If the game opens the BIN form directly, the bidding-form step is skipped.
Manage Auctions can contain existing items. Navigation finds the uniquely named
create control; it does not click an existing listing, claim it, or cancel it.

## Using a listing operation

1. Stop trading with K. Resolve retained order/config errors first.
2. Keep exactly one stack of the chosen product in ordinary inventory. Items
   reserved by a trader cannot be adopted. The complete stack is listed; the price
   argument is the **total price for the stack**, not a per-item price.
3. Run `/goofyauction sell PRODUCT_ID TOTAL_PRICE MAXIMUM_CREATION_FEE`.
   Prices and the fee limit are whole coins. For example,
   `/goofyauction sell ASPECT_OF_THE_END 300000 10000` requests a listing at
   300,000 coins with a creation fee ceiling of 10,000 coins. This is a command
   example, not a recommendation for an item's current market price.
4. Close unrelated menus and press J. The operation takes priority through the
   same foreground menu scheduler used by the traders. The configured book/general
   mode also starts as usual and continues after a verified listing.

The navigator opens `/ah`, reaches the BIN form, shift-transfers the exact stack,
writes the price sign and reads the server-confirmed item and price. It retains
the form's existing duration; it does not invent or alter a duration. Publication
requires a readable duration between one hour and fourteen days, an exact matching
item/price on **Confirm BIN Auction**, and an unambiguous fee on its confirmation
button. Both the user-provided fee ceiling and the current spendable budget apply.

`/goofyauction prepare PRODUCT_ID TOTAL_PRICE` performs the reversible preparation
and pauses before creation/publication for manual review instead.

## Lag, confirmation and recovery

Every item movement uses the independent server packet snapshot. Local predicted
inventory changes do not prove a transfer. Navigation controls can retry only when
the same control remains present; retries and total operation time are bounded.
Item transfers, create actions and final publication are not blindly repeated.

The journal saves listing intent before item transfer and before publication.
Listing success requires a newly observed matching entry in Manage Auctions,
the item absent from inventory, and the exact quoted creation-fee debit. It does
not count a clicked button as success. The saved state becomes SELLING; a listing
is not a sale receipt and does not count as realized profit. Existing material
cost remains unknown when no verified purchase record exists.

Unexpected screens, changed items/prices, missing fee/duration evidence and
unacknowledged transactions pause for review. Keep the current menu and inspect
the actual item before retrying. An interrupted saved intent is not automatically
replayed on the next session.

## Capturing a menu that pauses

The latest auction screen is kept in memory so opening chat to type a command
does not lose the menu. After viewing the relevant screen, run `/goofyauction inspect`.
An unrecognized transaction screen is also captured automatically when the listing
executor stops for review.
It writes `config/goofyaddons-auction-menu.json` with the title, slot numbers,
button/item names, quantities and descriptions. It excludes player inventory,
raw NBT and item/pet UUIDs. Capturing another screen replaces this inspection file;
it does not replace the production journal.

The Auction House, Auctions Browser, Create Auction and Manage Auctions layout
images supplied in this conversation informed navigation. The form's item/price/
create slots were also cross-checked against public NEU source. Confirmation and
seller-listing formats are strict supported contracts tested through simulated
server snapshots; they have **not** been tested in a live Hypixel session here.
If the current game uses different wording, the operation stops and the inspection
file supplies the facts needed to support that layout.

## Remaining automatic flipping integration

This update connects existing-inventory BIN listing navigation to the live menu
scheduler. It does not yet choose profitable AH routes, buy their components,
queue craft/Kat jobs automatically, or claim and account for completed AH sales.
BIN purchasing remains an isolated transaction component. Auction bids remain
unsupported. Full automatic buy → process → list → collect integration is still
pending, as described in [PRODUCTION-FLIPS.md](PRODUCTION-FLIPS.md).
