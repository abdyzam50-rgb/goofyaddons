# Craft catalog import

The shipped catalog is derived from MIT-licensed
[NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO).
The 0.2.35 snapshot uses commit
`c4f7d4757641f3a11c74d3d77cec76c100abaf98`.
The existing NEU license remains bundled with the mod and website.

Import a local, checked-out source revision:

```sh
python3 tools/recipes/build_catalog.py /path/to/NotEnoughUpdates-REPO
python3 -m unittest discover -s tools/recipes -v
```

The importer reads both top-level `recipe` and crafting entries in `recipes`,
including items that also have NPC trade recipes. Identical grids inside an item
are counted once. Legacy `LOG-1` becomes Hypixel `LOG:1`; known enchantment
`NAME;level` becomes `ENCHANTMENT_NAME_level`. Pet/shard identifiers are never
assumed to be books. An ingredient without a quantity means one, matching NEU's
format. Grids and yields are bounded to the executor's real slot capacities.

Crafttext, slayer/reputation fields, parsed calculator requirements and existing
reviewed gates are merged. Roman and numeric versions of the same gate are
coalesced. Unknown requirements remain blocking. Current recipes retain stable
keys; an older duplicate key is retained only if its exact grid/yield still
matches a current source recipe. Removed source recipes aren't silently enabled.
Forge and Kat records are preserved; this importer expands crafting only.

Every current source grid is either imported or recorded under
`unsupportedCrafts`, with its source filename, original grid and reason. Coverage
records are machine-readable in `production-recipes.json`. The current snapshot
accounts for 2,561 source grids: 2,528 imported and 33 excluded. One compatibility
key makes 2,529 executable catalog rows. The exclusions are 32 pet/shard variants
and one conversion that consumes its own output ID. They need separate identity
and yield contracts before automation can use them.

The catalog is bundled, not fetched from third parties during a crafting run.
Live prices, market depth, account unlocks, available funds and menu receipts still
control eligibility. A catalog entry does not imply available Bazaar ingredients,
AH component procurement, guaranteed profit or automatic AH settlement support.

After changing the catalog, rebuild the calculator using the pinned upstream
checkout and a data snapshot **outside** its output folder (Vite clears output):

```sh
node tools/bazaar-calc/build-website.mjs UPSTREAM_CHECKOUT COPIED_DATA_DIRECTORY NEU_CHECKOUT
```

This updates the website research recipes, production catalog and provenance.
The mod build bundles that same website, so build the website before the JAR.
Compare `integrations/goofyaddons/src/main/resources/goofyaddons/production-recipes.json`
and `tools/bazaar-calc/calculator/data/production-recipes.json` after generation.
