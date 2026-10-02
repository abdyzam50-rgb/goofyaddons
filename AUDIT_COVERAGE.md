# Audit coverage

Current workspace source review, 2026-10-02. Related finding IDs identify the repair backlog; “No specific new finding” means no additional confirmed defect was recorded, not proof of runtime safety. Tests are reviewed as tests, not substitutes for exercising the engines.

| File | Review | Related findings |
|---|---|---|
| `src/client/java/com/goofy/goofyaddons/GoofyAddonsClient.java` | Source reviewed | A02 |
| `src/client/java/com/goofy/goofyaddons/config/GoofyConfig.java` | Source reviewed | A22, A24 |
| `src/client/java/com/goofy/goofyaddons/diagnostics/DiagnosticLog.java` | Source reviewed | A27, R12, R13 |
| `src/client/java/com/goofy/goofyaddons/diagnostics/Diagnostics.java` | Source reviewed | A26, A28 |
| `src/client/java/com/goofy/goofyaddons/event/ChatHook.java` | Source reviewed | R10 |
| `src/client/java/com/goofy/goofyaddons/failsafes/Failsafe.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/failsafes/FailsafeManager.java` | Source reviewed | R14 |
| `src/client/java/com/goofy/goofyaddons/failsafes/ScheduledReboot.java` | Source reviewed | A25 |
| `src/client/java/com/goofy/goofyaddons/features/CapitalManager.java` | Source reviewed | R01 |
| `src/client/java/com/goofy/goofyaddons/features/Feature.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/FeatureManager.java` | Source reviewed | A01, A12 |
| `src/client/java/com/goofy/goofyaddons/features/MenuScheduler.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/TradingMode.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/TradingSafety.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/TransactionWatchdog.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java` | Source reviewed | A03, A05, A06, A07, A08, A09, R05, R07, R08, R09 |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/BazaarApi.java` | Source reviewed | A19, R15 |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/BazaarData.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/BazaarMonitor.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/Book.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/BookJournal.java` | Source reviewed | A11, A21 |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/BookList.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/FlipCalculator.java` | Source reviewed | A10, A20 |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/FlipItem.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/Task.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/TradeBudget.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralCalculator.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralFlipper.java` | Source reviewed | A04, A13, A14, A15, A16, A17, R02, R03 |
| `src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralItem.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralSettings.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/generalflipper/OrderLore.java` | Source reviewed | R04 |
| `src/client/java/com/goofy/goofyaddons/features/profit/ProfitDisplay.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/features/profit/ProfitHud.java` | Source reviewed | A31, R11 |
| `src/client/java/com/goofy/goofyaddons/features/profit/ProfitLedger.java` | Source reviewed | A30 |
| `src/client/java/com/goofy/goofyaddons/features/profit/ProfitTracker.java` | Source reviewed | A29 |
| `src/client/java/com/goofy/goofyaddons/features/profit/TradeReceipts.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/keybinds/GoofyKeybinds.java` | Source reviewed | A23 |
| `src/client/java/com/goofy/goofyaddons/render/gui/GoofyGui.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/utils/ChatUtils.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/utils/Clock.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/utils/InventoryScanner.java` | Source reviewed | R06 |
| `src/client/java/com/goofy/goofyaddons/utils/InventoryUtils.java` | Source reviewed | No specific new finding |
| `src/client/java/com/goofy/goofyaddons/utils/ScoreboardUtils.java` | Source reviewed | A18 |
| `src/main/java/com/goofy/goofyaddons/GoofyAddons.java` | Source reviewed | No specific new finding |
| `src/main/java/com/goofy/goofyaddons/GoofyAddonsDataGenerator.java` | Source reviewed | No specific new finding |
| `src/main/java/com/goofy/goofyaddons/mixin/ExampleMixin.java` | Source reviewed | No specific new finding |
| `src/main/resources/assets/goofyaddons/icon.png` | Binary inventoried; visual/content validation deferred | No specific new finding |
| `src/main/resources/assets/goofyaddons/lang/en_us.json` | Text resource reviewed | No specific new finding |
| `src/main/resources/assets/goofyaddons/textures/gui/header.jpg` | Binary inventoried; visual/content validation deferred | No specific new finding |
| `src/main/resources/assets/goofyaddons/textures/gui/header.png` | Binary inventoried; visual/content validation deferred | No specific new finding |
| `src/main/resources/fabric.mod.json` | Text resource reviewed | No specific new finding |
| `src/main/resources/goofyaddons.mixins.json` | Text resource reviewed | No specific new finding |
| `src/test/java/com/goofy/goofyaddons/config/ConfigRegressionTest.java` | Source reviewed | No specific new finding |
| `src/test/java/com/goofy/goofyaddons/diagnostics/DiagnosticLogTest.java` | Source reviewed | No specific new finding |
| `src/test/java/com/goofy/goofyaddons/features/CombinedTradingTest.java` | Source reviewed | No specific new finding |
| `src/test/java/com/goofy/goofyaddons/features/RuntimeSafetyTest.java` | Source reviewed | A32 |
| `src/test/java/com/goofy/goofyaddons/features/bookflipper/helper/BookJournalTest.java` | Source reviewed | No specific new finding |
| `src/test/java/com/goofy/goofyaddons/features/bookflipper/helper/TradingRegressionTest.java` | Source reviewed | No specific new finding |
| `src/test/java/com/goofy/goofyaddons/features/generalflipper/GeneralCalculatorTest.java` | Source reviewed | No specific new finding |
| `src/test/java/com/goofy/goofyaddons/features/generalflipper/OrderLoreTest.java` | Source reviewed | No specific new finding |
| `src/test/java/com/goofy/goofyaddons/features/profit/ProfitLedgerTest.java` | Source reviewed | No specific new finding |
| `src/test/java/com/goofy/goofyaddons/features/profit/TradeReceiptsTest.java` | Source reviewed | No specific new finding |
| `build.gradle` | Configuration/build definition reviewed | A21–A24, A33 as applicable |
| `settings.gradle` | Configuration/build definition reviewed | A21–A24, A33 as applicable |
| `gradle.properties` | Configuration/build definition reviewed | A21–A24, A33 as applicable |
| `gradle/wrapper/gradle-wrapper.properties` | Configuration/build definition reviewed | A21–A24, A33 as applicable |
| `gradle/wrapper/gradle-wrapper.jar` | Binary inventoried | A21–A24, A33 as applicable |
| `.github/workflows/build.yml` | Configuration/build definition reviewed | A21–A24, A33 as applicable |
| `examples/goofyaddons-both.json` | Configuration/build definition reviewed | A21–A24, A33 as applicable |

## Automated baseline

Baseline rerun: 82 tests, 0 failures, 0 errors, 0 skipped. Gradle test --rerun-tasks completed successfully with Java 25 and Gradle 9.4.1 (offline cached dependencies). No new runtime implementation or committed tests were added during the audit.

## Interaction coverage

Inspected lifecycle → failsafes → feature manager → scheduler → engine → parser/inventory/action → journal/capital → receipt/profit → diagnostics/HUD. Source inspection covers these interactions; deterministic whole-engine replay and live server validation remain missing (A32).
