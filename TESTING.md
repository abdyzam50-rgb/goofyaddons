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
