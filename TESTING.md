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
