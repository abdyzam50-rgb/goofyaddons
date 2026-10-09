# Testing the combined client

Use Minecraft 26.3, Java 25, Fabric Loader 0.19.5 or newer and Fabric API
0.161.0+26.3. Install [the combined 0.2.18 JAR](dist/astar-client-0.2.18-BETA.jar)
and follow [installation and saved-data migration](docs/GOOFYADDONS.md).

Run `./gradlew -p client test calculatorIntegrationTest build` for the client
and `node --test tools/bazaar-calc/*.test.mjs tools/gameplay-collector/*.test.mjs`
for the calculator and collector.

The search identity update passed 974 Java tests and 99 Node tests. Live Minecraft
validation remains outstanding; see [the architecture review](docs/GOOFYADDONS-ARCHITECTURE-REVIEW.md).

Older release notes under `docs/development/` describe the standalone trader.
