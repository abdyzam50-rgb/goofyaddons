# GoofyAddons documentation

## Using it

| | |
|---|---|
| [guides/GENERAL_FLIPPER.md](guides/GENERAL_FLIPPER.md) | Trading modes, keys and configuration |
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

Per-release repair records, oldest first:

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
