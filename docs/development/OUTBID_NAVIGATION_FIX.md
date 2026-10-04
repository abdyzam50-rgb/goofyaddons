# Outbid navigation and redundant visits — 1.3.15-BETA

Follow-up: [OUTBID_CLOSED_GUI_FIX.md](OUTBID_CLOSED_GUI_FIX.md) corrects the
closed-screen observation contract used by this navigator.

The uploaded `diagnostics-1791040767419-970f476a.zip` contains 1.3.14 session
`971997f3-1037-4c40-9930-6802aec8402d`. Duplex outbid work starts at event 341,
opens `/managebazaarorders` at 344, claims seven books, opens order options,
and cancels at 359. It then reopens the orders GUI three times (369, 378, 386)
before scheduling combination at 390. Overload repeats the pattern, with six
claimed books and repeated Cancel Order clicks at 433, 435, 436 and 437 while
the same options container remains visible. The order GUI is functional; the
unnecessary visits and stale repeated cancellation clicks are the defects.

Per the user's requested flow, outbid work enters with `/bz <book>` and selects
the exact level. On a loaded matching item menu, one uniquely named explicit
management control (Manage Orders, View Orders, Manage Buy Orders or Your Buy
Orders) is used. The controller never clicks Create Buy Order while an old
order is still live. Existing order identification, creator checks, inventory
claim acknowledgement, cancellation and remaining-quantity replacement remain.

The ZIP includes search/item titles but no detailed item-menu controls. These
management labels are supported navigation alternatives, not verified captured
fixtures. If the menu lacks a unique recognized control, the bot uses the
working `/managebazaarorders` path and emits `books.outbid_navigation_fallback`
with a detailed snapshot to establish the actual button name/lore. The server
may route a recognized item management button to its global order list; this
change does not claim to eliminate that GUI in every menu variant. Live
verification is needed for the direct management route.

Cancellation intent is recorded before clicking once. While it is pending,
stale order options and stale list entries cannot cause another claim/cancel.
After a different, loaded order-list container has passed the existing 750ms
settling check, absence of that buy order acknowledges removal without the
three generic missing-order reopen attempts. Unexplained absence before an
intent still uses the existing retry checks. Cancellation not verified within
30 seconds pauses without submitting a replacement.

Validation: Gradle build and all 295 tests pass with zero failures, errors or
skips. Five regressions cover exact-level navigation, single clicks per menu,
explicit fallback without new buying, wrong/unloaded item menus, ambiguous
controls, and freshness/timeout/reset behavior for cancellation. These tests
exercise the navigation and cancellation helper; they do not constitute a full
live Bazaar transaction replay.
