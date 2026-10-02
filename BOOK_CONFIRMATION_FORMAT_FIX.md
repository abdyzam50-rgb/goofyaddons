# Observed book confirmation format — 1.3.6-BETA

Uploaded diagnostics-1790914591940-7b9d9077.zip captures a 1.3.4-BETA BOOKS-mode Overload I confirmation. books.confirmation_check reports previewMatches=false and profitAllowed=true. Slot 13 is Buy Order, with exact fields:

```
Price per unit: 685,510.0 coins
Order: 16x Overload I
Total price: 10,968,160 coins
```

All values match the intended 16 Overload I at 685,510.0 each. The confirmation parser supported explicit Item/Product/Buying/Selling fields but not Hypixel's observed Order field, so it rejected valid identity/quantity evidence. No book confirmation was submitted in this attempt. The supplied bundle runs 1.3.4, and the parser omission also remained in 1.3.5.

ConfirmationCheck now reads Order: <quantity>x <exact item> as product/quantity evidence under the already verified side-specific confirmation title. It retains strict price/total validation, exact item-name matching, quantity agreement across fields and rejection of malformed/conflicting supported fields. The generic Buy Order button name is not treated as product identity. Both engines share this parser.

The sanitized observed slot-13 tooltip is committed in src/test/resources/fixtures/overload-buy-confirmation.json. Before the parser change, the two new focused tests produced one failure: the captured valid preview was rejected. After the fix, all 105 tests pass. Negative variants test wrong item/level, count, unit price, total, duplicate Order fields, conflicting Item fields, loading text and invalid comma grouping. Production build and git diff --check passed. Version 1.3.6-BETA includes the 1.3.5 stale-orders recheck/placement verification changes. Whole-engine/live execution remains unverified; no ownership files were deleted or migrated.
