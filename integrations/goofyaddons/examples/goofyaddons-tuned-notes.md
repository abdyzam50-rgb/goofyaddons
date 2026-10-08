# Config notes (from recent-14d-summary.json)

Only products seen on all 15 days were used (550 of 1,812 items; 21 of 547 book routes). The other 1,260 items were seen for one day only.

## How the bot really sizes general orders
A general buy order holds at most (empty inventory slots - 4) units, so usually around 28. It is also capped by 8M coins per item and by one hour of weekly flow. Batch profit is therefore about 28 x net per unit. Cheap items with a tiny net per unit can never reach the 75k batch minimum.

## Books (capital 65M)
| Route | Cost | Net/craft (p10 / median) | ROI | Sell-side weekly flow (p10) | Note |
|---|---|---|---|---|---|
| Overload 1->5 | 11.0M | 4.62M / 7.37M | 67% | 23 | kept; slow to sell |
| Duplex (ULTIMATE_REITERATE) 1->5 | 10.5M | 4.00M / 5.26M | 50% | 22 | **added**; slow to sell |
| Swarm 1->5 | 11.1M | 2.79M / 4.62M | 41% | 49 | kept |
| Soul Eater 1->5 | 15.0M | 2.56M / 2.93M | 20% | 70 | kept; only 24 competing sell offers |
| Green Thumb 1->5 | 31.0M | 2.33M / 4.29M | 14% | 73 | **added** |
| Wisdom 1->5 | 2.6M | 0.48M / 0.67M | 26% | 397 | kept; very crowded (1,007 offers) |

Left out:
- Smoldering, Ice Cold, Chimera, Bobbin Time, Divine Gift and Habanero: each costs more than 50M or sells fewer than 10 per week.
- Legion, Feast and Crop Fever: ROI is about 10%.
- Dedication 3->4 shows a 1,300% ROI that held for all 15 days. A gap that size almost certainly means the combine is impossible in game, so do not add it.
- Sunset 1->5 (9.5M cost, 27% ROI, 471 sold per week) looks good, but I could not confirm its in-game name.

`maxBookHoldingSeconds` went from 6h to 24h. Overload and Duplex V sell only about 3 per day, so a 6h limit would trip the safety pause on most runs. The 15% drawdown limit still applies.

## General items
Removed:
- Kismet Feather: its median margin is 2.8%, below your 3% minimum.
- Scorched Power Crystal and Corleonite: their weekly flow is usually under your 10,000 minimum.

Added (flow p10 >= 10k, margin p10 >= 4.5%, few competing orders):

| Item | Margin p10 | Flow p10/week |
|---|---|---|
| Blue Ring | 15% | 28k |
| Broken Radar | 11% | 11k |
| Duskbloom | 16% | 10k |
| Scorched Crab Stick | 8% | 12k |
| Bejeweled Handle | 10% | 15k |
| Cactus Flower | 7% | 62k |
| Shellfruit | 52% | 41k |
| Hunk of Blue Ice | 5% | 30k |
| Carrot Zest | 4.6% | 58k |
| Plasma | 4.8% | 44k |
| Enchanted Blaze Rod | 4.9% | 48k |
| Figstone | 4.6% | 23k |

Precursor Gear and Super Compactor 3000 earn well but are crowded: 632 and 391 competing buy orders.

Your other settings are unchanged except `maxActiveItems`, which went from 2 to 3.

## Biggest gap not filled: shards
About 10 of the 40 best liquid items are shards (Mafioso, Apex Dragon, Scatha, Burningsoul, Power Dragon and others), but the data has no display names for them. The bot searches and matches orders by name, so send their Bazaar names and I'll add them.

Caveat: these are 14-day observations, not guaranteed fills. Queue competition and combine time are not modeled.
