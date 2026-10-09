# Website overlay

`ProfileLookup.tsx` adds optional read-only username/profile import to the complete
upstream calculator. `../build-website.mjs` applies it to a pinned upstream
checkout and produces `../calculator/`, including licenses and provenance.
The username, profile selector and budgets use calculator state only; they cannot
start the trader or mutate its saved orders.

`CraftPlanner.tsx` adds live production plans above the existing craft research
view. `craft-plan.mjs` is the pure, tested browser planner, using the mod's verified
production recipe catalog. The public Worker supplies fixed, cached Bazaar and
Coflnet discovery/BIN endpoints; visitors supply no credentials. Profile import
and manual settings recalculate costs, whole batches and prerequisite gates.

To rebuild without changing the existing published reference/history snapshot,
copy `calculator/data/` to a directory outside the output folder and run:

```
node tools/bazaar-calc/build-website.mjs UPSTREAM_CHECKOUT COPIED_DATA_DIRECTORY -
```

The upstream revision must be the pinned commit in the build script, with its
frozen dependencies installed. Passing a NEU checkout instead of `-` regenerates
the original research recipes. The verified production catalog always comes from
the mod's resource file. Do not use the output data directory as the input: Vite
clears its output before copying reference files.
