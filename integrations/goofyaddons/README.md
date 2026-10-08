# Imported trader module

This directory preserves GoofyAddons source, regression tests, fixtures and license.
`UPSTREAM.json` records the exact imported revision. It is compiled by the A* client
build, with Minecraft 26.3 API and keyboard-code migration changes.

This is not a second standalone mod installation. Its Fabric metadata is excluded
from the combined resources; the A* host registers initialization and the packet
mirror mixin exactly once. Existing config/data file paths remain unchanged.

See [the combined-mod guide](../../docs/GOOFYADDONS.md).
