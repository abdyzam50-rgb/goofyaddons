# GoofyAddons documentation

## Using it

| | |
|---|---|
| [PRODUCTION-FLIPS.md](PRODUCTION-FLIPS.md) | Inventory auto-crafting and Forge/Kat/BIN implementation status |
| [BAZAAR-ACCESS.md](BAZAAR-ACCESS.md) | Bazaar NPC access, skill checks and pathfinder integration |
| [SESSION-SCHEDULE.md](SESSION-SCHEDULE.md) | Daily local-time rest windows and reconnect controls |
| [guides/GENERAL_FLIPPER.md](guides/GENERAL_FLIPPER.md) | Trading modes, keys and configuration |
| [../tools/bazaar-calc/README.md](../tools/bazaar-calc/README.md) | Starting the read-only Bazaar Calc companion |
| [guides/PROFIT_TRACKER.md](guides/PROFIT_TRACKER.md) | The profit HUD, its commands and how it accounts |
| [guides/RUNTIME_SAFETY.md](guides/RUNTIME_SAFETY.md) | What the safety pauses mean and how to recover |
| [../TESTING.md](../TESTING.md) | Installing a test build, and what testers should know first |
| [guides/FIELD_TEST.md](guides/FIELD_TEST.md) | Staged protocol for a first live run, cheapest stage first |

## Development

Current state and what is left:

| | |
|---|---|
| [development/FULL_AUDIT.md](development/FULL_AUDIT.md) | Findings A01–A33 and R01–R15, with a dated status section per release |
| [development/AUDIT_COVERAGE.md](development/AUDIT_COVERAGE.md) | Which source files were reviewed, and under which findings |
| [development/FULL_REPAIR_PLAN.md](development/FULL_REPAIR_PLAN.md) | The staged repair plan and its acceptance gates |
| [development/REFACTOR_PLAN.md](development/REFACTOR_PLAN.md) | Structure and per-tick cost: stages, what shipped, what is deferred |

Earlier plans, kept because the audit refers back to them:

- [development/REPAIR_PLAN.md](development/REPAIR_PLAN.md) · [development/REPAIR_NOTES.md](development/REPAIR_NOTES.md)
- [development/GENERAL_FLIPPER_PLAN.md](development/GENERAL_FLIPPER_PLAN.md) · [development/RUNTIME_SAFETY_PLAN.md](development/RUNTIME_SAFETY_PLAN.md)
- [development/SEQUENCE_AUDIT.md](development/SEQUENCE_AUDIT.md) — superseded by FULL_AUDIT.md

Per-release repair records:

- [development/BOOK_RECOVERY_VERIFICATION.md](development/BOOK_RECOVERY_VERIFICATION.md) — 1.3.25 verifies saved book ownership and automatically retires stale records

- [development/MARKET_ANALYSIS_INTEGRATION.md](development/MARKET_ANALYSIS_INTEGRATION.md) — 1.3.24 local account dashboard, continuous market collection and Bazaar Calc recommendations

- [development/GENERAL_LOOP_REPAIR.md](development/GENERAL_LOOP_REPAIR.md) — 1.3.21 automatic general exits, acknowledged claims and complete loop simulations

- [development/BOOK_LOOP_STRENGTHENING.md](development/BOOK_LOOP_STRENGTHENING.md) — 1.3.20 shared observations, validated storage retries and faster sale settlement

- [development/STARTUP_ORDER_SCAN_FIX.md](development/STARTUP_ORDER_SCAN_FIX.md) — 1.3.19 accounts for missing startup orders in one settled visit

- [development/OUTBID_CLOSED_GUI_FIX.md](development/OUTBID_CLOSED_GUI_FIX.md) — 1.3.18 recognizes inventory observations when the GUI is closed

- [development/BOOK_EXIT_POLICY_FIX.md](development/BOOK_EXIT_POLICY_FIX.md) — 1.3.17 profit targets gate new book purchases rather than held-book sales

- [development/ANVIL_RETRY_FIX.md](development/ANVIL_RETRY_FIX.md) — 1.3.16 validates unchanged anvil states before retrying rejected actions

- [development/OUTBID_NAVIGATION_FIX.md](development/OUTBID_NAVIGATION_FIX.md) — 1.3.15 item-first outbid navigation and cancellation acknowledgement

- [development/ANVIL_COLLECTION_FIX.md](development/ANVIL_COLLECTION_FIX.md) — 1.3.14 acknowledges the displayed result before claiming it

- [development/ANVIL_PREVIEW_FIX.md](development/ANVIL_PREVIEW_FIX.md) — 1.3.13 corrects the captured anvil preview/button layout

- [development/LOST_FOUND_POLICY.md](development/LOST_FOUND_POLICY.md) — 1.3.12 permanent physical write-offs, found-book adoption and session continuation status

- [development/SLOT_MEMORY.md](development/SLOT_MEMORY.md) — 1.3.11 previous/current slot maps, transfer intent and automatic location reconciliation

- [development/PURSE_OBSERVATION_FIX.md](development/PURSE_OBSERVATION_FIX.md) — 1.3.10 separates unreadable balances from insufficient capital

- [development/BOOK_LOOP_REPAIR.md](development/BOOK_LOOP_REPAIR.md) — 1.3.9 log evidence, combine/transfer repair and remaining loop work

- [development/STAGE_1_REPAIR.md](development/STAGE_1_REPAIR.md) · [development/STAGE_2_REPAIR.md](development/STAGE_2_REPAIR.md)
- [development/CONFIRMATION_DIAGNOSTIC_FIX.md](development/CONFIRMATION_DIAGNOSTIC_FIX.md) — 1.3.4-BETA
- [development/ORDER_RECHECK_FIX.md](development/ORDER_RECHECK_FIX.md) — 1.3.5-BETA
- [development/BOOK_CONFIRMATION_FORMAT_FIX.md](development/BOOK_CONFIRMATION_FORMAT_FIX.md) — 1.3.6-BETA
- [development/BOOK_PLAN_RECOVERY_FIX.md](development/BOOK_PLAN_RECOVERY_FIX.md)

## Building and testing

```
./gradlew test     # the suite
./gradlew build    # suite plus the jar in build/libs/
```

Needs a JDK 25. In a Claude Code cloud session, `.claude/hooks/session-start.sh` provisions
one and warms the dependency cache automatically; see that script's header for why Maven
Central needs a mirror and retries there. It writes the JDK choice and the mirror into the
Gradle user home rather than into `build.gradle` or `settings.gradle`, so a local workaround
can never change how anyone else builds.

## Layout

```
README.md              what the mod is, and where to start
TESTING.md             install a test build; read before running it
docs/guides/           using the mod
docs/development/      audits, plans and per-release repair records
examples/              example configuration files
dist/                  a built jar for testers
src/main, src/client   the mod itself; src/test the suite
```

- [Dashboard profit visibility (1.3.27)](development/DASHBOARD_PROFIT_VISIBILITY.md)

- [Spendable book selection (1.3.28)](development/SPENDABLE_BOOK_SELECTION.md)

- [Book session continuation (1.3.29)](development/BOOK_SESSION_RESUME.md)

- [Truncated product menu navigation (1.3.30)](development/TRUNCATED_PRODUCT_MENU_FIX.md)

- [Shared trading pipeline preview (1.3.31)](development/TRADING_PIPELINE.md)
