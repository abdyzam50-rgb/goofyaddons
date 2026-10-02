# Confirmation evidence and loading fix — 1.3.4-BETA

Source: uploaded diagnostics-1790913528118-44e49a04.zip, latest session 71485b49-f34f-49f2-a66e-8f82851a24fd running 1.3.3-BETA. Older sessions in the same file are historical and were excluded from the current failure diagnosis.

At 03:58:40.088 UTC the book engine selected the top price for 16 Overload I. At 03:58:40.290 UTC it paused because confirmation preview or net-profit validation failed. The latest session has zero dropped diagnostic events and no log error. No book confirmation click follows this price-selection action. General had acquired 30 Ectoplasm earlier; this is a separate event and is not book confirmation submission.

The safety snapshot has no menuItems because BazaarFlipper.safetyHalt closed the menu before FeatureManager.safetyPause captured its context. Consequently the bundle cannot establish which preview field failed, whether the tooltip format was unsupported, or whether it was evaluated during loading. No unsupported server format was guessed or enabled.

The patch latches the book engine and records books.transaction_blocked with detailed context before cleanup closes the screen. books.confirmation_check additionally records expected item/quantity/unit price, trade ID, previewMatches and profitAllowed. These booleans produce distinct user-facing preview and profit rejection reasons. Both engines now require the confirmation button/lore fingerprint to remain unchanged in the same container for 750ms before evaluation. Empty previews remain waiting under the existing transaction watchdog/time limits. A changing tooltip or container resets the wait. Safety validation remains strict.

Validation: 98 tests passed with zero failures/errors/skips; production build and git diff --check passed. Two new MenuObservationStabilityTest cases cover partial/changing contents, container replacement, unloaded contents, reset and backwards time. This is a loading/evidence fix, not verification of the unknown live tooltip format. Retained ownership files were not deleted or migrated.
