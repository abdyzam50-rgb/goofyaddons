# Basic ingredient preparation audit (0.2.23)

The catalog already contains these recipes, but direct procurement previously
tried to buy their outputs. The preparation allowlist now covers the following
reviewed, requirement-free crafting chains. These operate only when an input
is missing for another production recipe; final outputs retain normal processing.

| Intermediate | Preparation | Yield per craft |
|---|---|---|
| Blaze Powder | Blaze Rod | 2 |
| Oak planks (`WOOD`) | Oak log (`LOG`) | 4 |
| Sticks | 2 planks | 4 |
| Paper | 3 Sugar Cane | 3 |
| Sugar | Sugar Cane | 1 |
| Book | 3 paper + leather | 1 |
| Bowl | 3 planks | 4 |
| Chest | 8 planks | 1 |
| Gold Nugget | Gold Ingot | 9 |
| Redstone Torch | Redstone + stick | 1 |
| Normal Eye of Ender | Ender Pearl + Blaze Powder | 1 |
| Glass Bottle | 3 glass | 3 |
| Crafting Table (`WORKBENCH`) | 4 planks | 1 |
| Empty Bucket | 3 Iron Ingots | 1 |
| Wooden pickaxe, axe, hoe, shovel, sword | Planks + sticks from logs | 1 each |

Inputs already held reduce purchases. Yield rounding and shared surplus are
handled by `IngredientPreparation`; preparation uses the existing executor,
intent journal, occupied-product guard, spendable capital and child-job recovery.
The allowlist remains explicit: a blank requirement or multiple outputs alone
is insufficient to select every SkyBlock recipe automatically.

Glass is a base input here. Held glass can produce bottles. There is no smelting
executor: if glass cannot be bought from a fresh Bazaar quote, procurement blocks
and asks for the missing base input. It does not invent a sand-to-glass crafting
recipe. Filled buckets and other recipes with container remainders are not added.

The bundled torch and arrow entries each report output count 1; standard vanilla
recipes produce 4. They are excluded from automatic ingredient preparation until
their SkyBlock output is verified and the catalog source is corrected. Colored
blocks require metadata-aware identity checks; they are not expanded here.
Reversible material/block recipes (coal, metal/gem blocks, redstone, wheat, slime)
are not forced into recursion: both directions would introduce cycles and their
purchase choice needs a separate price comparison. Market-traded components
such as Magma Cream retain direct procurement rather than assuming crafting is
always cheaper. This update does not compare every buy/craft choice by profit.

Tests check exact preparation quantities and ordering, shared cane/log surplus,
base materials for wooden tools, held glass, fully covered final inputs reserved
while genuine surplus stays usable, excluded reversible/unreviewed
recipes, and execution of all 16 newly enabled outputs in the simulated crafting
menu. The simulation validates code against catalog facts; it does not establish
live Hypixel menu behavior. Test the downloaded JAR in game before relying on a
new route.
