# Testing the combined client

Use Minecraft 26.3, Java 25, Fabric Loader 0.19.5 or newer and Fabric API
0.161.0+26.3. Install [the combined 0.2.27 JAR](dist/astar-client-0.2.27-BETA.jar)
and follow [installation and saved-data migration](docs/GOOFYADDONS.md).

Run `./gradlew -p client test calculatorIntegrationTest build` for the client
and `node --test tools/bazaar-calc/*.test.mjs tools/gameplay-collector/*.test.mjs`
for the calculator and collector.

The 0.2.27 craft pipeline update passed 1027 Java tests and 106 Node tests.
Coverage includes whole-batch depth/capital/volume/prerequisite gates, intermediate
craft costs, exact recipe pinning, profit rechecks before purchases, bounded sale
receipts, unknown historical cost basis, CRAFT-only engine composition, Coflnet
source validation and the local craft discovery endpoint.

A Chromium dashboard check passed at 1440px and 390px, including craft-plan
rendering, eligibility reasons, CRAFT-mode forecast separation, stale-data removal,
no overflow/XSS and no browser exceptions. Live Minecraft and Coflnet gateway
validation remain outstanding; see [the craft pipeline](docs/CRAFT-FLIP-PIPELINE.md).
Automatic compactor configuration, bulk receipts and cleanup remain unimplemented.
Automatic AH craft execution remains gated on sale/expiry/claim reconciliation.
See [the architecture review](docs/GOOFYADDONS-ARCHITECTURE-REVIEW.md) for wider follow-up work.

Older release notes under `docs/development/` describe the standalone trader.

The public craft-site update passed TypeScript/Vite builds and 113 Node tests,
including conservative depth/budget/requirements/volume gates, whole-batch sizing,
base preparation, fresh BIN validation, price-only fixed-destination Worker
requests, shared caching and failure handling. Run
`node tools/bazaar-calc/craft-planner-browser-check.mjs` for desktop/mobile profile
import, preparation steps, market filters, price refresh and stale-price removal.
Fixtures validate these flows; live owner deployment and real Coflnet access must
be verified separately after deploying the website assets and Worker together.

The 0.2.28 stacked-crafting tests simulate a 64-rod/128-powder bulk collection,
partial server collections using the same loaded grid, false outputs without
matching ingredient consumption, and capacity limits for unknown unstackable
outputs. Existing split, dropped-input, cursor, requirement, and journal tests
remain required. No test launches Minecraft or uses a real account inventory.
The final 0.2.28 client/test/calculatorIntegrationTest/build run passed 1032 Java
tests with no failures or errors. It also covers exact non-power-of-two loading
(47 items), the widened held-input craft command limit, and one-step intermediate
preparation before the final craft. The release JAR's version and crafting class
were checked. Live Hypixel behavior is not validated by these simulated tests.

0.2.29 account/startup checks cover direct fixed-HTTPS profile lookup without a
localhost request, Ender Pearl VI eligibility, unknown/expired/wrong-profile
rejection, service error/backoff and cancellation on profile changes. Node tests
prove the health port opens without reading website assets, and requested files
are cached after one read. A shipped-bundle integration fixture delays Node
startup for eight seconds, beyond the former roughly six-second window, then
checks it reaches READY without a failed launch. No real Minecraft inventory or
live Hypixel profile is used by these tests.
The final 0.2.29 run passed 1035 Java tests, 114 Node tests, and desktop/mobile
calculator and dashboard Chromium checks. The shipped resource ZIP and direct
profile-service URL were checked in the 0.2.29 JAR. The user's original Node
startup failure still requires its current diagnostics to identify precisely;
these tests validate the removed dependency and cold-start fixes, not access to
the user's live account or Cloudflare deployment.

0.2.30 adds a same-container blaze-powder → enchanted-eye crafting simulation and
a parent/child menu-ownership test. Both check verified output and handoff without
closing or reopening Craft Item. Existing transaction and shipped-calculator
integration checks remain required.

0.2.30 validation: 1,038 Java tests passed, including the shipped-calculator
integration suite and the owned Craft Item → Auction House transition. The release
JAR built successfully. These are simulated menu checks; live Hypixel execution
was not available in this workspace.

0.2.31 validation: 1,048 Java tests passed with no failures, including
shipped-calculator integration checks. Two sanitized live Compactor 7000 menu
captures validate populated and empty numbered controls. Simulations verify
all twelve removals, disabled devices, multiple devices, empty-menu closing,
menu ownership, durable intent failures, missing acknowledgements, unexpected
filter/inventory changes, saved-metadata verification and a production purchase
blocked until clearance finishes. The release JAR built successfully. Live
Hypixel opening/removal execution was not available in this workspace; keep
compactors in the hotbar for automatic opening. Bulk compaction remains disabled.

0.2.32 validation: 1,055 Java tests passed with no failures, including
the shipped-calculator integration suite. Four sanitized user AH GUI captures
validate co-op root navigation, empty BIN item insertion, compact fee/name
confirmation bound to the exact previously verified form, and receipt at the
player's own BIN view. Negative cases retain price/name/inventory/identity and
fee-debit checks. Recipe tests verify Aspect of the End's exact sword grid and
Ender Pearl VIII requirement; AH test input rejects unknown or multi-output
recipes. The 0.2.32 mod JAR built and its version/classes were checked. Live
Minecraft GUI execution and Coflnet requests were not available here.

0.2.33 validation: 1,058 Java tests passed with no failures, including
shipped-calculator integration checks. Selling now validates fresh Coflnet prices
and delegates directly to the existing Create Auction executor. Price tests
reject mismatched products, stale quotes and an explicit price outside the band.
The supplied underlying browser controls reproduce the expected search-sign
transition; search and BIN price signs are written once despite a transient
carried control, while a cursor still occupied on return blocks selection or
publication. Co-op creation, compact confirmation, own-listing identity and fee
receipt checks remain covered. The 0.2.33 JAR built and its version was checked.
Live Hypixel execution and live Coflnet responses remain for user testing.


0.2.34 validation: 1,061 Java tests passed, zero failures/errors, with the full
test suite, shipped-calculator integration checks and release build. Added
regressions cover ignored Bazaar amount/sign navigation, delayed AH creation
and confirmation controls, delayed browser controls/results, and bounded waits
for transient cursor state. Existing duplicate-transfer/publication and wrong
item/price/fee checks pass. Actual Minecraft server lag remains for live testing.

0.2.35 validation: 1,063 Java tests, 114 Node tests and 5 Python importer tests
passed with zero failures. The full Java suite, shipped-calculator integration
checks and release build passed. Catalog coverage checks account for every
source grid; new variants/books/collection gates and saved keys are verified.
Importer tests cover quantities, identity exclusions, unknown gates, removed
recipes, numeric/Roman gate deduplication and repeatable generation. Reimporting
the real source snapshot produced identical bytes. Desktop/mobile Chromium
checks passed for profile import, filters, quote refresh and expiry; the new
exclusion list wraps long IDs without horizontal overflow. The mod resource,
embedded calculator ZIP, website files and public website deployment ZIP contain
identical catalogs. Live crafting of all imported routes was not possible here;
server GUI evidence and account/market checks remain necessary during execution.

0.2.36 validation: 1,069 Java tests, 116 Node tests and 6 Python importer tests
passed with zero failures/errors. Full Java tests, shipped-calculator integration
checks and the release build passed. Regressions cover raw Slayer codes, HotM
aliases and boundaries, invalid/unknown account facts, published Slayer reward
claims, pre-purchase Forge access/slot/recipe gates and confirmation controls.
Desktop/mobile Chromium checks passed for profile import, ingredient preparation,
Bazaar/AH filters and quote refresh/expiry. Live Hypixel crafting and Forge
submission remain for user testing.

0.2.37 validation: 1,074 Java tests, 120 Node tests and 6 Python importer tests
passed with no failures/errors. The complete Java suite, shipped-calculator
integration tests and release build passed. New cases cover Catacombs boundaries,
normal/Master floor identity, unknown/invalid API data, all 48 product gates,
website requirement parsing, essence rankings and pre-purchase ingredient locks.
Desktop/mobile Chromium checks passed. Packaged catalogs and profile modules
match the source files. Live Hypixel transactions remain for user testing;
mutation analysis/analyzer reward evidence is unavailable from the profile API
and remains blocked rather than assumed.

0.2.38 validation: 1,075 Java tests and 122 Node tests passed without
failures/errors. Full Java tests, bundled-calculator integration tests and the
release build passed. Profile regressions cover the current skill-tree/nested
Slayer layout, legacy fallback, modern-field precedence, empty published maps,
invalid/unpublished fields, analyzed-versus-discovered Garden crops and claimed
analyzer milestones. The mod verifies published Garden unlocks against profile
identity, age and value bounds. Desktop/mobile browser checks passed. The live
Worker reproduced curedmc's missing progression in its prior parser; corrected
raw Hypixel data could not be fetched here without its private deployment key.
The field mappings are verified against the current SkyCrypt backend/schema;
a live user lookup must be checked after redeployment.
