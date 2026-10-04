var __defProp = Object.defineProperty;
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};

// ../../tmp/bazaar-calc-review/packages/shared/dist/rules/bazaar.js
var BAZAAR = {
  baseOrderSlots: 14,
  slotsPerFlipperLevel: 7,
  maxFlipperLevel: 2,
  baseTax: 0.0125,
  taxReductionPerFlipperLevel: 125e-5,
  maxUnitsPerOrder: 71680,
  maxUnitsPerOrderUnstackable: 256,
  maxInstantBuyUnits: 2240,
  // most items (inventory limit)
  maxSellOfferValue: 1e9,
  instantBuyQuoteMarkup: 0.04,
  orderExpiryDays: 7,
  priceTick: 0.1,
  maxCustomPricePerUnit: 5e8,
  // community-derived (SkyHanni constants/Bazaar.json, Bazaar Utils): user setting
  dailyLimitDefault: 15e9,
  dailyLimitPerActionCap: 2147483647,
  dailyLimitResetUtcHour: 0,
  // NPC shops (not the bazaar): most sell at most 640 of an item per player per day, reset 00:00 UTC
  // (6,400 with Diaz's Shopping Spree perk). Source: hypixelskyblock.minecraft.wiki/w/Shops
  npcDailyBuyLimit: 640
};
function taxRate(flipperLevel) {
  const lvl = Math.max(0, Math.min(BAZAAR.maxFlipperLevel, Math.floor(flipperLevel)));
  return BAZAAR.baseTax - lvl * BAZAAR.taxReductionPerFlipperLevel;
}
function limitContribution(coins) {
  return Math.min(Math.max(0, coins), BAZAAR.dailyLimitPerActionCap);
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/rules/forge.js
var FORGE = {
  minHotm: 2,
  slotsAtMinHotm: 2,
  maxSlotTier: 7,
  // +1 slot per HotM tier up to tier 7
  coleMoltenForgeReduction: 0.25,
  quickForgeMaxLevel: 20
};
function forgeSlots(hotmTier) {
  if (hotmTier < FORGE.minHotm)
    return 0;
  return Math.min(FORGE.maxSlotTier, Math.floor(hotmTier));
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/rules/enchants.json
var enchants_default = {
  rules: {
    ENCHANTMENT_ABSORB: {
      id: "ENCHANTMENT_ABSORB",
      name: "Absorb",
      page: "Absorb",
      url: "https://hypixelskyblock.minecraft.wiki/w/Absorb",
      max_level: null,
      combine_cap: null,
      combine_status: "no_combine",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        10,
        10,
        10,
        10,
        10,
        10,
        10,
        10,
        10
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1
      ]
    },
    ENCHANTMENT_ANGLER: {
      id: "ENCHANTMENT_ANGLER",
      name: "Angler",
      page: "Angler",
      url: "https://hypixelskyblock.minecraft.wiki/w/Angler",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        75
      ],
      enchanting_req: 4,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_AQUA_AFFINITY: {
      id: "ENCHANTMENT_AQUA_AFFINITY",
      name: "Aqua Affinity",
      page: "Aqua Affinity",
      url: "https://hypixelskyblock.minecraft.wiki/w/Aqua_Affinity",
      max_level: 1,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        15
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1
      ]
    },
    ENCHANTMENT_BANE_OF_ARTHROPODS: {
      id: "ENCHANTMENT_BANE_OF_ARTHROPODS",
      name: "Bane of Arthropods",
      page: "Bane of Arthropods",
      url: "https://hypixelskyblock.minecraft.wiki/w/Bane_of_Arthropods",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_BIG_BRAIN: {
      id: "ENCHANTMENT_BIG_BRAIN",
      name: "Big Brain",
      page: "Big Brain",
      url: "https://hypixelskyblock.minecraft.wiki/w/Big_Brain",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        60,
        80,
        100
      ],
      enchanting_req: 21,
      ultimate: false,
      bazaar_levels: [
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_BLAST_PROTECTION: {
      id: "ENCHANTMENT_BLAST_PROTECTION",
      name: "Blast Protection",
      page: "Blast Protection",
      url: "https://hypixelskyblock.minecraft.wiki/w/Blast_Protection",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_BLESSING: {
      id: "ENCHANTMENT_BLESSING",
      name: "Blessing",
      page: "Blessing (Enchantment)",
      url: "https://hypixelskyblock.minecraft.wiki/w/Blessing_(Enchantment)",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0
      ],
      enchanting_req: 9,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_ULTIMATE_BOBBIN_TIME: {
      id: "ENCHANTMENT_ULTIMATE_BOBBIN_TIME",
      name: "Bobbin' Time",
      page: "Bobbin' Time",
      url: "https://hypixelskyblock.minecraft.wiki/w/Bobbin'_Time",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [],
      enchanting_req: 24,
      ultimate: true,
      bazaar_levels: [
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_BUG_BLENDER: {
      id: "ENCHANTMENT_BUG_BLENDER",
      name: "Bug Blender",
      page: "Bug Blender",
      url: "https://hypixelskyblock.minecraft.wiki/w/Bug_Blender",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_CASTER: {
      id: "ENCHANTMENT_CASTER",
      name: "Caster",
      page: "Caster",
      url: "https://hypixelskyblock.minecraft.wiki/w/Caster",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [],
      enchanting_req: 15,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_CAYENNE: {
      id: "ENCHANTMENT_CAYENNE",
      name: "Cayenne",
      page: "Cayenne",
      url: "https://hypixelskyblock.minecraft.wiki/w/Cayenne",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_CHAMPION: {
      id: "ENCHANTMENT_CHAMPION",
      name: "Champion",
      page: "Champion",
      url: "https://hypixelskyblock.minecraft.wiki/w/Champion",
      max_level: null,
      combine_cap: null,
      combine_status: "no_combine",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ]
    },
    ENCHANTMENT_CHANCE: {
      id: "ENCHANTMENT_CHANCE",
      name: "Chance",
      page: "Chance",
      url: "https://hypixelskyblock.minecraft.wiki/w/Chance",
      max_level: 5,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        15,
        30,
        45,
        100,
        200
      ],
      enchanting_req: 11,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_CHARM: {
      id: "ENCHANTMENT_CHARM",
      name: "Charm",
      page: "Charm",
      url: "https://hypixelskyblock.minecraft.wiki/w/Charm",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50,
        0
      ],
      enchanting_req: 25,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_CHIMERA: {
      id: "ENCHANTMENT_ULTIMATE_CHIMERA",
      name: "Chimera",
      page: "Chimera",
      url: "https://hypixelskyblock.minecraft.wiki/w/Chimera",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 31,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_CLEAVE: {
      id: "ENCHANTMENT_CLEAVE",
      name: "Cleave",
      page: "Cleave",
      url: "https://hypixelskyblock.minecraft.wiki/w/Cleave",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        200
      ],
      enchanting_req: 4,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_ULTIMATE_COMBO: {
      id: "ENCHANTMENT_ULTIMATE_COMBO",
      name: "Combo",
      page: "Combo",
      url: "https://hypixelskyblock.minecraft.wiki/w/Combo",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 24,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_COMPACT: {
      id: "ENCHANTMENT_COMPACT",
      name: "Compact",
      page: "Compact",
      url: "https://hypixelskyblock.minecraft.wiki/w/Compact",
      max_level: 10,
      combine_cap: null,
      combine_status: "no_combine",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ]
    },
    ENCHANTMENT_CORRUPTION: {
      id: "ENCHANTMENT_CORRUPTION",
      name: "Corruption",
      page: "Corruption",
      url: "https://hypixelskyblock.minecraft.wiki/w/Corruption",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50
      ],
      enchanting_req: 25,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_COUNTER_STRIKE: {
      id: "ENCHANTMENT_COUNTER_STRIKE",
      name: "Counter-Strike",
      page: "Counter-Strike",
      url: "https://hypixelskyblock.minecraft.wiki/w/Counter-Strike",
      max_level: 5,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        60,
        80,
        100
      ],
      enchanting_req: 22,
      ultimate: false,
      bazaar_levels: [
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_CRITICAL: {
      id: "ENCHANTMENT_CRITICAL",
      name: "Critical",
      page: "Critical",
      url: "https://hypixelskyblock.minecraft.wiki/w/Critical",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        75,
        100
      ],
      enchanting_req: 9,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_ULTIMATE_CROP_FEVER: {
      id: "ENCHANTMENT_ULTIMATE_CROP_FEVER",
      name: "Crop Fever",
      page: "Crop Fever",
      url: "https://hypixelskyblock.minecraft.wiki/w/Crop_Fever",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        45,
        91,
        136,
        179,
        223
      ],
      enchanting_req: 32,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_CUBISM: {
      id: "ENCHANTMENT_CUBISM",
      name: "Cubism",
      page: "Cubism",
      url: "https://hypixelskyblock.minecraft.wiki/w/Cubism",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        200
      ],
      enchanting_req: 3,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_CULTIVATING: {
      id: "ENCHANTMENT_CULTIVATING",
      name: "Cultivating",
      page: "Cultivating",
      url: "https://hypixelskyblock.minecraft.wiki/w/Cultivating",
      max_level: null,
      combine_cap: null,
      combine_status: "no_combine",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9
      ]
    },
    ENCHANTMENT_DEDICATION: {
      id: "ENCHANTMENT_DEDICATION",
      name: "Dedication",
      page: "Dedication",
      url: "https://hypixelskyblock.minecraft.wiki/w/Dedication",
      max_level: 4,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        100
      ],
      enchanting_req: 12,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4
      ]
    },
    ENCHANTMENT_DELICATE: {
      id: "ENCHANTMENT_DELICATE",
      name: "Delicate",
      page: "Delicate",
      url: "https://hypixelskyblock.minecraft.wiki/w/Delicate",
      max_level: 5,
      combine_cap: null,
      combine_status: "no_combine",
      combine_xp_cost: {},
      apply_xp_cost: [
        50
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        5
      ]
    },
    ENCHANTMENT_DIVINE_GIFT: {
      id: "ENCHANTMENT_DIVINE_GIFT",
      name: "Divine Gift",
      page: "Divine Gift",
      url: "https://hypixelskyblock.minecraft.wiki/w/Divine_Gift",
      max_level: 3,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3
      ]
    },
    ENCHANTMENT_AIMING: {
      id: "ENCHANTMENT_AIMING",
      name: "Dragon Tracer",
      page: "Dragon Tracer",
      url: "https://hypixelskyblock.minecraft.wiki/w/Dragon_Tracer",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50
      ],
      enchanting_req: 8,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_SYPHON: {
      id: "ENCHANTMENT_SYPHON",
      name: "Drain",
      page: "Drain",
      url: "https://hypixelskyblock.minecraft.wiki/w/Drain",
      max_level: null,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        50,
        200
      ],
      enchanting_req: 15,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_REITERATE: {
      id: "ENCHANTMENT_ULTIMATE_REITERATE",
      name: "Duplex",
      page: "Duplex",
      url: "https://hypixelskyblock.minecraft.wiki/w/Duplex",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 28,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_EFFICIENCY: {
      id: "ENCHANTMENT_EFFICIENCY",
      name: "Efficiency",
      page: "Efficiency",
      url: "https://hypixelskyblock.minecraft.wiki/w/Efficiency",
      max_level: 10,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_ENDER_SLAYER: {
      id: "ENCHANTMENT_ENDER_SLAYER",
      name: "Ender Slayer",
      page: "Ender Slayer",
      url: "https://hypixelskyblock.minecraft.wiki/w/Ender_Slayer",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        25,
        30,
        40,
        75,
        200
      ],
      enchanting_req: 11,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_EXECUTE: {
      id: "ENCHANTMENT_EXECUTE",
      name: "Execute",
      page: "Execute",
      url: "https://hypixelskyblock.minecraft.wiki/w/Execute",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50,
        200
      ],
      enchanting_req: 14,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_EXPERIENCE: {
      id: "ENCHANTMENT_EXPERIENCE",
      name: "Experience",
      page: "Experience (Enchantment)",
      url: "https://hypixelskyblock.minecraft.wiki/w/Experience_(Enchantment)",
      max_level: 5,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        15,
        30,
        45,
        75,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_EXPERTISE: {
      id: "ENCHANTMENT_EXPERTISE",
      name: "Expertise",
      page: "Expertise",
      url: "https://hypixelskyblock.minecraft.wiki/w/Expertise",
      max_level: null,
      combine_cap: null,
      combine_status: "no_combine",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ]
    },
    ENCHANTMENT_ULTIMATE_FATAL_TEMPO: {
      id: "ENCHANTMENT_ULTIMATE_FATAL_TEMPO",
      name: "Fatal Tempo",
      page: "Fatal Tempo",
      url: "https://hypixelskyblock.minecraft.wiki/w/Fatal_Tempo",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 37,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_FEAST: {
      id: "ENCHANTMENT_FEAST",
      name: "Feast",
      page: "Feast",
      url: "https://hypixelskyblock.minecraft.wiki/w/Feast",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50
      ],
      enchanting_req: 5,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_FEATHER_FALLING: {
      id: "ENCHANTMENT_FEATHER_FALLING",
      name: "Feather Falling",
      page: "Feather Falling",
      url: "https://hypixelskyblock.minecraft.wiki/w/Feather_Falling",
      max_level: 10,
      combine_cap: 10,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [],
      enchanting_req: null,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10,
        20
      ]
    },
    ENCHANTMENT_FIRE_PROTECTION: {
      id: "ENCHANTMENT_FIRE_PROTECTION",
      name: "Fire Protection",
      page: "Fire Protection",
      url: "https://hypixelskyblock.minecraft.wiki/w/Fire_Protection",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_ULTIMATE_FIRST_IMPRESSION: {
      id: "ENCHANTMENT_ULTIMATE_FIRST_IMPRESSION",
      name: "First Impression",
      page: "First Impression",
      url: "https://hypixelskyblock.minecraft.wiki/w/First_Impression",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 14,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_FIRST_STRIKE: {
      id: "ENCHANTMENT_FIRST_STRIKE",
      name: "First Strike",
      page: "First Strike",
      url: "https://hypixelskyblock.minecraft.wiki/w/First_Strike",
      max_level: null,
      combine_cap: 4,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        30,
        40,
        75,
        200
      ],
      enchanting_req: 10,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_FLASH: {
      id: "ENCHANTMENT_ULTIMATE_FLASH",
      name: "Flash",
      page: "Flash",
      url: "https://hypixelskyblock.minecraft.wiki/w/Flash",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 30,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_FLOWSTATE: {
      id: "ENCHANTMENT_ULTIMATE_FLOWSTATE",
      name: "Flowstate",
      page: "Flowstate",
      url: "https://hypixelskyblock.minecraft.wiki/w/Flowstate",
      max_level: 3,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {
        "1": 50,
        "2": 100,
        "3": 150
      },
      apply_xp_cost: [
        50,
        100,
        150
      ],
      enchanting_req: 15,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3
      ]
    },
    ENCHANTMENT_FOREST_PLEDGE: {
      id: "ENCHANTMENT_FOREST_PLEDGE",
      name: "Forest Pledge",
      page: "Forest Pledge",
      url: "https://hypixelskyblock.minecraft.wiki/w/Forest_Pledge",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        30,
        40,
        50,
        0
      ],
      enchanting_req: 12,
      ultimate: false,
      bazaar_levels: [
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_FORTUNE: {
      id: "ENCHANTMENT_FORTUNE",
      name: "Fortune",
      page: "Fortune",
      url: "https://hypixelskyblock.minecraft.wiki/w/Fortune",
      max_level: 4,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        15,
        30,
        45,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_FRAIL: {
      id: "ENCHANTMENT_FRAIL",
      name: "Frail",
      page: "Frail",
      url: "https://hypixelskyblock.minecraft.wiki/w/Frail",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50,
        75,
        0
      ],
      enchanting_req: 14,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_GIANT_KILLER: {
      id: "ENCHANTMENT_GIANT_KILLER",
      name: "Giant Killer",
      page: "Giant Killer",
      url: "https://hypixelskyblock.minecraft.wiki/w/Giant_Killer",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        100,
        200
      ],
      enchanting_req: 8,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_DRAGON_HUNTER: {
      id: "ENCHANTMENT_DRAGON_HUNTER",
      name: "Gravity",
      page: "Gravity",
      url: "https://hypixelskyblock.minecraft.wiki/w/Gravity",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        100
      ],
      enchanting_req: 16,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_GREAT_SPOOK: {
      id: "ENCHANTMENT_GREAT_SPOOK",
      name: "Great Spook",
      page: "Great Spook (Enchantment)",
      url: "https://hypixelskyblock.minecraft.wiki/w/Great_Spook_(Enchantment)",
      max_level: 1,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: []
    },
    ENCHANTMENT_GREEN_THUMB: {
      id: "ENCHANTMENT_GREEN_THUMB",
      name: "Green Thumb",
      page: "Green Thumb (Enchantment)",
      url: "https://hypixelskyblock.minecraft.wiki/w/Green_Thumb_(Enchantment)",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 24,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_GROWTH: {
      id: "ENCHANTMENT_GROWTH",
      name: "Growth",
      page: "Growth",
      url: "https://hypixelskyblock.minecraft.wiki/w/Growth",
      max_level: 7,
      combine_cap: 4,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        100,
        200
      ],
      enchanting_req: 5,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_ULTIMATE_HABANERO_TACTICS: {
      id: "ENCHANTMENT_ULTIMATE_HABANERO_TACTICS",
      name: "Habanero Tactics",
      page: "Habanero Tactics",
      url: "https://hypixelskyblock.minecraft.wiki/w/Habanero_Tactics",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        250,
        300
      ],
      enchanting_req: 28,
      ultimate: true,
      bazaar_levels: [
        4,
        5
      ]
    },
    ENCHANTMENT_HARDENED_MANA: {
      id: "ENCHANTMENT_HARDENED_MANA",
      name: "Hardened Vitality",
      page: "Hardened Vitality",
      url: "https://hypixelskyblock.minecraft.wiki/w/Hardened_Vitality",
      max_level: 10,
      combine_cap: 10,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        30,
        45,
        60,
        75,
        90,
        180,
        240,
        300,
        360,
        420
      ],
      enchanting_req: 22,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ]
    },
    ENCHANTMENT_HARVESTING: {
      id: "ENCHANTMENT_HARVESTING",
      name: "Harvesting",
      page: "Harvesting",
      url: "https://hypixelskyblock.minecraft.wiki/w/Harvesting",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        5,
        10,
        15,
        20,
        25,
        0
      ],
      enchanting_req: 2,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_HECATOMB: {
      id: "ENCHANTMENT_HECATOMB",
      name: "Hecatomb",
      page: "Hecatomb",
      url: "https://hypixelskyblock.minecraft.wiki/w/Hecatomb",
      max_level: null,
      combine_cap: null,
      combine_status: "no_combine",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ]
    },
    ENCHANTMENT_ICE_COLD: {
      id: "ENCHANTMENT_ICE_COLD",
      name: "Ice Cold",
      page: "Ice Cold",
      url: "https://hypixelskyblock.minecraft.wiki/w/Ice_Cold",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        27,
        41,
        55,
        68,
        82
      ],
      enchanting_req: 30,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_IMPALING: {
      id: "ENCHANTMENT_IMPALING",
      name: "Impaling",
      page: "Impaling",
      url: "https://hypixelskyblock.minecraft.wiki/w/Impaling",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30
      ],
      enchanting_req: 12,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3
      ]
    },
    ENCHANTMENT_ULTIMATE_INFERNO: {
      id: "ENCHANTMENT_ULTIMATE_INFERNO",
      name: "Inferno",
      page: "Inferno",
      url: "https://hypixelskyblock.minecraft.wiki/w/Inferno",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 37,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_INFINITE_QUIVER: {
      id: "ENCHANTMENT_INFINITE_QUIVER",
      name: "Infinite Quiver",
      page: "Infinite Quiver",
      url: "https://hypixelskyblock.minecraft.wiki/w/Infinite_Quiver",
      max_level: 10,
      combine_cap: 10,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        60,
        80,
        100,
        120,
        140
      ],
      enchanting_req: 2,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ]
    },
    ENCHANTMENT_KARMA: {
      id: "ENCHANTMENT_KARMA",
      name: "Karma",
      page: "Karma",
      url: "https://hypixelskyblock.minecraft.wiki/w/Karma",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        0,
        0,
        0,
        0,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_KNOCKBACK: {
      id: "ENCHANTMENT_KNOCKBACK",
      name: "Knockback",
      page: "Knockback (Enchantment)",
      url: "https://hypixelskyblock.minecraft.wiki/w/Knockback_(Enchantment)",
      max_level: null,
      combine_cap: 2,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        15,
        30
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2
      ]
    },
    ENCHANTMENT_LAPIDARY: {
      id: "ENCHANTMENT_LAPIDARY",
      name: "Lapidary",
      page: "Lapidary",
      url: "https://hypixelskyblock.minecraft.wiki/w/Lapidary",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        50,
        100,
        150,
        200
      ],
      enchanting_req: 22,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_LAST_STAND: {
      id: "ENCHANTMENT_ULTIMATE_LAST_STAND",
      name: "Last Stand",
      page: "Last Stand",
      url: "https://hypixelskyblock.minecraft.wiki/w/Last_Stand",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 30,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_LEGION: {
      id: "ENCHANTMENT_ULTIMATE_LEGION",
      name: "Legion",
      page: "Legion",
      url: "https://hypixelskyblock.minecraft.wiki/w/Legion",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 34,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_LETHALITY: {
      id: "ENCHANTMENT_LETHALITY",
      name: "Lethality",
      page: "Lethality",
      url: "https://hypixelskyblock.minecraft.wiki/w/Lethality",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50,
        200
      ],
      enchanting_req: 14,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_LIFE_STEAL: {
      id: "ENCHANTMENT_LIFE_STEAL",
      name: "Life Steal",
      page: "Life Steal",
      url: "https://hypixelskyblock.minecraft.wiki/w/Life_Steal",
      max_level: null,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        50,
        200
      ],
      enchanting_req: 5,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_LOOTING: {
      id: "ENCHANTMENT_LOOTING",
      name: "Looting",
      page: "Looting",
      url: "https://hypixelskyblock.minecraft.wiki/w/Looting",
      max_level: null,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        15,
        30,
        45,
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_LUCK: {
      id: "ENCHANTMENT_LUCK",
      name: "Luck",
      page: "Luck",
      url: "https://hypixelskyblock.minecraft.wiki/w/Luck",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        75,
        200
      ],
      enchanting_req: 3,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_LUCK_OF_THE_SEA: {
      id: "ENCHANTMENT_LUCK_OF_THE_SEA",
      name: "Luck of the Sea",
      page: "Luck of the Sea",
      url: "https://hypixelskyblock.minecraft.wiki/w/Luck_of_the_Sea",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        50,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_LURE: {
      id: "ENCHANTMENT_LURE",
      name: "Lure",
      page: "Lure",
      url: "https://hypixelskyblock.minecraft.wiki/w/Lure",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        50
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_MAGNET: {
      id: "ENCHANTMENT_MAGNET",
      name: "Magnet",
      page: "Magnet",
      url: "https://hypixelskyblock.minecraft.wiki/w/Magnet",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50,
        75
      ],
      enchanting_req: 13,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_MANA_STEAL: {
      id: "ENCHANTMENT_MANA_STEAL",
      name: "Mana Steal",
      page: "Mana Steal",
      url: "https://hypixelskyblock.minecraft.wiki/w/Mana_Steal",
      max_level: 3,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30
      ],
      enchanting_req: 20,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3
      ]
    },
    ENCHANTMENT_ULTIMATE_MISSILE: {
      id: "ENCHANTMENT_ULTIMATE_MISSILE",
      name: "Missile",
      page: "Missile",
      url: "https://hypixelskyblock.minecraft.wiki/w/Missile",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 14,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN: {
      id: "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN",
      name: "No Pain No Gain",
      page: "No Pain No Gain",
      url: "https://hypixelskyblock.minecraft.wiki/w/No_Pain_No_Gain",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 29,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_ONE_FOR_ALL: {
      id: "ENCHANTMENT_ULTIMATE_ONE_FOR_ALL",
      name: "One For All",
      page: "One For All",
      url: "https://hypixelskyblock.minecraft.wiki/w/One_For_All",
      max_level: 1,
      combine_cap: null,
      combine_status: "no_combine",
      combine_xp_cost: {},
      apply_xp_cost: [
        50
      ],
      enchanting_req: 38,
      ultimate: true,
      bazaar_levels: [
        0,
        1
      ]
    },
    ENCHANTMENT_OVERLOAD: {
      id: "ENCHANTMENT_OVERLOAD",
      name: "Overload",
      page: "Overload",
      url: "https://hypixelskyblock.minecraft.wiki/w/Overload",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 33,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_PALEONTOLOGIST: {
      id: "ENCHANTMENT_PALEONTOLOGIST",
      name: "Paleontologist",
      page: "Paleontologist",
      url: "https://hypixelskyblock.minecraft.wiki/w/Paleontologist",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        23,
        45,
        91,
        136,
        179
      ],
      enchanting_req: 27,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_PESTERMINATOR: {
      id: "ENCHANTMENT_PESTERMINATOR",
      name: "Pesterminator",
      page: "Pesterminator",
      url: "https://hypixelskyblock.minecraft.wiki/w/Pesterminator",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        5,
        9,
        13,
        18,
        23,
        0
      ],
      enchanting_req: 10,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_PETALFALL: {
      id: "ENCHANTMENT_PETALFALL",
      name: "Petalfall",
      page: "Petalfall",
      url: "https://hypixelskyblock.minecraft.wiki/w/Petalfall",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        50,
        75,
        100,
        150
      ],
      enchanting_req: 12,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_PIERCING: {
      id: "ENCHANTMENT_PIERCING",
      name: "Piercing",
      page: "Piercing",
      url: "https://hypixelskyblock.minecraft.wiki/w/Piercing",
      max_level: 1,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        30
      ],
      enchanting_req: 17,
      ultimate: false,
      bazaar_levels: [
        1
      ]
    },
    ENCHANTMENT_PISCARY: {
      id: "ENCHANTMENT_PISCARY",
      name: "Piscary",
      page: "Piscary",
      url: "https://hypixelskyblock.minecraft.wiki/w/Piscary",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        8,
        16,
        24,
        36,
        48,
        60,
        0
      ],
      enchanting_req: 8,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_POWER: {
      id: "ENCHANTMENT_POWER",
      name: "Power",
      page: "Power",
      url: "https://hypixelskyblock.minecraft.wiki/w/Power",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_PRISTINE: {
      id: "ENCHANTMENT_PRISTINE",
      name: "Prismatic",
      page: "Prismatic",
      url: "https://hypixelskyblock.minecraft.wiki/w/Prismatic",
      max_level: 1,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        50,
        100,
        150,
        200
      ],
      enchanting_req: 22,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_PROJECTILE_PROTECTION: {
      id: "ENCHANTMENT_PROJECTILE_PROTECTION",
      name: "Projectile Protection",
      page: "Projectile Protection",
      url: "https://hypixelskyblock.minecraft.wiki/w/Projectile_Protection",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_PROSECUTE: {
      id: "ENCHANTMENT_PROSECUTE",
      name: "Prosecute",
      page: "Prosecute",
      url: "https://hypixelskyblock.minecraft.wiki/w/Prosecute",
      max_level: 6,
      combine_cap: 4,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50,
        200
      ],
      enchanting_req: 25,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_PROSPERITY: {
      id: "ENCHANTMENT_PROSPERITY",
      name: "Prosperity",
      page: "Prosperity",
      url: "https://hypixelskyblock.minecraft.wiki/w/Prosperity",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        100,
        100,
        100,
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_PROTECTION: {
      id: "ENCHANTMENT_PROTECTION",
      name: "Protection",
      page: "Protection",
      url: "https://hypixelskyblock.minecraft.wiki/w/Protection",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_PUNCH: {
      id: "ENCHANTMENT_PUNCH",
      name: "Punch",
      page: "Punch",
      url: "https://hypixelskyblock.minecraft.wiki/w/Punch",
      max_level: 2,
      combine_cap: 2,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        15,
        30
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_MAGMARIZER: {
      id: "ENCHANTMENT_MAGMARIZER",
      name: "Pyroclasm",
      page: "Pyroclasm",
      url: "https://hypixelskyblock.minecraft.wiki/w/Pyroclasm",
      max_level: 6,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        100
      ],
      enchanting_req: 9,
      ultimate: false,
      bazaar_levels: [
        6
      ]
    },
    ENCHANTMENT_QUICK_BITE: {
      id: "ENCHANTMENT_QUICK_BITE",
      name: "Quick Bite",
      page: "Quick Bite",
      url: "https://hypixelskyblock.minecraft.wiki/w/Quick_Bite",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        18,
        23,
        27,
        36,
        45
      ],
      enchanting_req: 23,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_RAINBOW: {
      id: "ENCHANTMENT_RAINBOW",
      name: "Rainbow",
      page: "Rainbow",
      url: "https://hypixelskyblock.minecraft.wiki/w/Rainbow",
      max_level: 3,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        0,
        0
      ],
      enchanting_req: 6,
      ultimate: false,
      bazaar_levels: []
    },
    ENCHANTMENT_REFLECTION: {
      id: "ENCHANTMENT_REFLECTION",
      name: "Reflection",
      page: "Reflection",
      url: "https://hypixelskyblock.minecraft.wiki/w/Reflection",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        40,
        60,
        80,
        100
      ],
      enchanting_req: 24,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_REFRIGERATE: {
      id: "ENCHANTMENT_ULTIMATE_REFRIGERATE",
      name: "Refrigerate",
      page: "Refrigerate",
      url: "https://hypixelskyblock.minecraft.wiki/w/Refrigerate",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 20,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_REJUVENATE: {
      id: "ENCHANTMENT_REJUVENATE",
      name: "Rejuvenate",
      page: "Rejuvenate",
      url: "https://hypixelskyblock.minecraft.wiki/w/Rejuvenate",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50
      ],
      enchanting_req: 10,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_REND: {
      id: "ENCHANTMENT_ULTIMATE_REND",
      name: "Rend",
      page: "Rend",
      url: "https://hypixelskyblock.minecraft.wiki/w/Rend",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 32,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_REPLENISH: {
      id: "ENCHANTMENT_REPLENISH",
      name: "Replenish",
      page: "Replenish",
      url: "https://hypixelskyblock.minecraft.wiki/w/Replenish",
      max_level: 1,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        50
      ],
      enchanting_req: 7,
      ultimate: false,
      bazaar_levels: [
        1
      ]
    },
    ENCHANTMENT_RESPIRATION: {
      id: "ENCHANTMENT_RESPIRATION",
      name: "Respiration",
      page: "Respiration (Enchantment)",
      url: "https://hypixelskyblock.minecraft.wiki/w/Respiration_(Enchantment)",
      max_level: 4,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40
      ],
      enchanting_req: 7,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4
      ]
    },
    ENCHANTMENT_RESPITE: {
      id: "ENCHANTMENT_RESPITE",
      name: "Respite",
      page: "Respite",
      url: "https://hypixelskyblock.minecraft.wiki/w/Respite",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50
      ],
      enchanting_req: 23,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_SCAVENGER: {
      id: "ENCHANTMENT_SCAVENGER",
      name: "Scavenger",
      page: "Scavenger",
      url: "https://hypixelskyblock.minecraft.wiki/w/Scavenger",
      max_level: null,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0
      ],
      enchanting_req: 1,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_SCUBA: {
      id: "ENCHANTMENT_SCUBA",
      name: "Scuba",
      page: "Scuba",
      url: "https://hypixelskyblock.minecraft.wiki/w/Scuba",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0
      ],
      enchanting_req: 5,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_SHARPNESS: {
      id: "ENCHANTMENT_SHARPNESS",
      name: "Sharpness",
      page: "Sharpness",
      url: "https://hypixelskyblock.minecraft.wiki/w/Sharpness",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_SILK_TOUCH: {
      id: "ENCHANTMENT_SILK_TOUCH",
      name: "Silk Touch",
      page: "Silk Touch",
      url: "https://hypixelskyblock.minecraft.wiki/w/Silk_Touch",
      max_level: 1,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        10
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1
      ]
    },
    ENCHANTMENT_SMALL_BRAIN: {
      id: "ENCHANTMENT_SMALL_BRAIN",
      name: "Small Brain",
      page: "Small Brain",
      url: "https://hypixelskyblock.minecraft.wiki/w/Small_Brain",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        60,
        80,
        100
      ],
      enchanting_req: 21,
      ultimate: false,
      bazaar_levels: [
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_SMARTY_PANTS: {
      id: "ENCHANTMENT_SMARTY_PANTS",
      name: "Smarty Pants",
      page: "Smarty Pants",
      url: "https://hypixelskyblock.minecraft.wiki/w/Smarty_Pants",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        40,
        60,
        80,
        100
      ],
      enchanting_req: 21,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_SMELTING_TOUCH: {
      id: "ENCHANTMENT_SMELTING_TOUCH",
      name: "Smelting Touch",
      page: "Smelting Touch",
      url: "https://hypixelskyblock.minecraft.wiki/w/Smelting_Touch",
      max_level: 1,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        5
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1
      ]
    },
    ENCHANTMENT_SMITE: {
      id: "ENCHANTMENT_SMITE",
      name: "Smite",
      page: "Smite",
      url: "https://hypixelskyblock.minecraft.wiki/w/Smite",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        100,
        200
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_SMOLDERING: {
      id: "ENCHANTMENT_SMOLDERING",
      name: "Smoldering",
      page: "Smoldering",
      url: "https://hypixelskyblock.minecraft.wiki/w/Smoldering",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50
      ],
      enchanting_req: 23,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_SNIPE: {
      id: "ENCHANTMENT_SNIPE",
      name: "Snipe",
      page: "Snipe",
      url: "https://hypixelskyblock.minecraft.wiki/w/Snipe",
      max_level: 4,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        0
      ],
      enchanting_req: 6,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4
      ]
    },
    ENCHANTMENT_ULTIMATE_SOUL_EATER: {
      id: "ENCHANTMENT_ULTIMATE_SOUL_EATER",
      name: "Soul Eater",
      page: "Soul Eater",
      url: "https://hypixelskyblock.minecraft.wiki/w/Soul_Eater",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 36,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_SPIKED_HOOK: {
      id: "ENCHANTMENT_SPIKED_HOOK",
      name: "Spiked Hook",
      page: "Spiked Hook",
      url: "https://hypixelskyblock.minecraft.wiki/w/Spiked_Hook",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        75,
        0
      ],
      enchanting_req: 18,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_STEALTH: {
      id: "ENCHANTMENT_STEALTH",
      name: "Stealth",
      page: "Stealth",
      url: "https://hypixelskyblock.minecraft.wiki/w/Stealth",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_STRONG_MANA: {
      id: "ENCHANTMENT_STRONG_MANA",
      name: "Strong Vitality",
      page: "Strong Vitality",
      url: "https://hypixelskyblock.minecraft.wiki/w/Strong_Vitality",
      max_level: 10,
      combine_cap: 10,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        30,
        45,
        60,
        75,
        90,
        180,
        240,
        300,
        360,
        420
      ],
      enchanting_req: 22,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ]
    },
    ENCHANTMENT_SUGAR_RUSH: {
      id: "ENCHANTMENT_SUGAR_RUSH",
      name: "Sugar Rush",
      page: "Sugar Rush",
      url: "https://hypixelskyblock.minecraft.wiki/w/Sugar_Rush",
      max_level: 3,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30
      ],
      enchanting_req: 7,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3
      ]
    },
    ENCHANTMENT_SUNDER: {
      id: "ENCHANTMENT_SUNDER",
      name: "Sunder",
      page: "Sunder",
      url: "https://hypixelskyblock.minecraft.wiki/w/Sunder",
      max_level: 6,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        5,
        10,
        15,
        20,
        25,
        0
      ],
      enchanting_req: 2,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_ULTIMATE_SUNSET: {
      id: "ENCHANTMENT_ULTIMATE_SUNSET",
      name: "Sunset",
      page: "Sunset",
      url: "https://hypixelskyblock.minecraft.wiki/w/Sunset",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 27,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_SWARM: {
      id: "ENCHANTMENT_ULTIMATE_SWARM",
      name: "Swarm",
      page: "Swarm",
      url: "https://hypixelskyblock.minecraft.wiki/w/Swarm",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 35,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TABASCO: {
      id: "ENCHANTMENT_TABASCO",
      name: "Tabasco",
      page: "Tabasco",
      url: "https://hypixelskyblock.minecraft.wiki/w/Tabasco",
      max_level: 3,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        300,
        500
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3
      ]
    },
    ENCHANTMENT_TELEKINESIS: {
      id: "ENCHANTMENT_TELEKINESIS",
      name: "Telekinesis",
      page: "Telekinesis (Enchantment)",
      url: "https://hypixelskyblock.minecraft.wiki/w/Telekinesis_(Enchantment)",
      max_level: 1,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        5
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: []
    },
    ENCHANTMENT_ULTIMATE_THE_ONE: {
      id: "ENCHANTMENT_ULTIMATE_THE_ONE",
      name: "The One",
      page: "The One",
      url: "https://hypixelskyblock.minecraft.wiki/w/The_One",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        0,
        0,
        0,
        250,
        300
      ],
      enchanting_req: 14,
      ultimate: true,
      bazaar_levels: [
        4,
        5
      ]
    },
    ENCHANTMENT_THORNS: {
      id: "ENCHANTMENT_THORNS",
      name: "Thorns",
      page: "Thorns",
      url: "https://hypixelskyblock.minecraft.wiki/w/Thorns",
      max_level: 4,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        15,
        30,
        45,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3
      ]
    },
    ENCHANTMENT_THUNDERBOLT: {
      id: "ENCHANTMENT_THUNDERBOLT",
      name: "Thunderbolt",
      page: "Thunderbolt",
      url: "https://hypixelskyblock.minecraft.wiki/w/Thunderbolt",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50,
        200,
        250
      ],
      enchanting_req: 20,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_THUNDERLORD: {
      id: "ENCHANTMENT_THUNDERLORD",
      name: "Thunderlord",
      page: "Thunderlord",
      url: "https://hypixelskyblock.minecraft.wiki/w/Thunderlord",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        9,
        13,
        18,
        23,
        27,
        91,
        179
      ],
      enchanting_req: 14,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_TIDAL: {
      id: "ENCHANTMENT_TIDAL",
      name: "Tidal",
      page: "Tidal",
      url: "https://hypixelskyblock.minecraft.wiki/w/Tidal",
      max_level: 3,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        40,
        55
      ],
      enchanting_req: 13,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3
      ]
    },
    ENCHANTMENT_TITAN_KILLER: {
      id: "ENCHANTMENT_TITAN_KILLER",
      name: "Titan Killer",
      page: "Titan Killer",
      url: "https://hypixelskyblock.minecraft.wiki/w/Titan_Killer",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        100,
        200
      ],
      enchanting_req: 28,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7
      ]
    },
    ENCHANTMENT_TOXOPHILITE: {
      id: "ENCHANTMENT_TOXOPHILITE",
      name: "Toxophilite",
      page: "Toxophilite (Enchantment)",
      url: "https://hypixelskyblock.minecraft.wiki/w/Toxophilite_(Enchantment)",
      max_level: null,
      combine_cap: null,
      combine_status: "no_combine",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25,
        25
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1
      ]
    },
    ENCHANTMENT_TRANSYLVANIAN: {
      id: "ENCHANTMENT_TRANSYLVANIAN",
      name: "Transylvanian",
      page: "Transylvanian",
      url: "https://hypixelskyblock.minecraft.wiki/w/Transylvanian",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        100,
        150
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        4,
        5
      ]
    },
    ENCHANTMENT_TRIPLE_STRIKE: {
      id: "ENCHANTMENT_TRIPLE_STRIKE",
      name: "Triple-Strike",
      page: "Triple-Strike",
      url: "https://hypixelskyblock.minecraft.wiki/w/Triple-Strike",
      max_level: 5,
      combine_cap: 4,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        30,
        40,
        75,
        200
      ],
      enchanting_req: 19,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TRUE_PROTECTION: {
      id: "ENCHANTMENT_TRUE_PROTECTION",
      name: "True Protection",
      page: "True Protection",
      url: "https://hypixelskyblock.minecraft.wiki/w/True_Protection",
      max_level: 1,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        40
      ],
      enchanting_req: 15,
      ultimate: false,
      bazaar_levels: [
        1
      ]
    },
    ENCHANTMENT_TURBO_WHEAT: {
      id: "ENCHANTMENT_TURBO_WHEAT",
      name: "Turbo-Wheat",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_CARROT: {
      id: "ENCHANTMENT_TURBO_CARROT",
      name: "Turbo-Carrot",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_POTATO: {
      id: "ENCHANTMENT_TURBO_POTATO",
      name: "Turbo-Potato",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_PUMPKIN: {
      id: "ENCHANTMENT_TURBO_PUMPKIN",
      name: "Turbo-Pumpkin",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_MELON: {
      id: "ENCHANTMENT_TURBO_MELON",
      name: "Turbo-Melon",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_MUSHROOMS: {
      id: "ENCHANTMENT_TURBO_MUSHROOMS",
      name: "Turbo-Mushrooms",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_CACTUS: {
      id: "ENCHANTMENT_TURBO_CACTUS",
      name: "Turbo-Cacti",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_CANE: {
      id: "ENCHANTMENT_TURBO_CANE",
      name: "Turbo-Cane",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_WARTS: {
      id: "ENCHANTMENT_TURBO_WARTS",
      name: "Turbo-Warts",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_COCO: {
      id: "ENCHANTMENT_TURBO_COCO",
      name: "Turbo-Cocoa",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_SUNFLOWER: {
      id: "ENCHANTMENT_TURBO_SUNFLOWER",
      name: "Turbo-Sunflower",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_MOONFLOWER: {
      id: "ENCHANTMENT_TURBO_MOONFLOWER",
      name: "Turbo-Moonflower",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_TURBO_ROSE: {
      id: "ENCHANTMENT_TURBO_ROSE",
      name: "Turbo-Rose",
      page: "Turbo-Crop",
      url: "https://hypixelskyblock.minecraft.wiki/w/Turbo-Crop",
      max_level: 7,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30,
        40,
        50,
        0,
        0
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_JERRY: {
      id: "ENCHANTMENT_ULTIMATE_JERRY",
      name: "Ultimate Jerry",
      page: "Ultimate Jerry",
      url: "https://hypixelskyblock.minecraft.wiki/w/Ultimate_Jerry",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 18,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ULTIMATE_WISE: {
      id: "ENCHANTMENT_ULTIMATE_WISE",
      name: "Ultimate Wise",
      page: "Ultimate Wise",
      url: "https://hypixelskyblock.minecraft.wiki/w/Ultimate_Wise",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 20,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_MANA_VAMPIRE: {
      id: "ENCHANTMENT_MANA_VAMPIRE",
      name: "Vampiric Vitality",
      page: "Vampiric Vitality",
      url: "https://hypixelskyblock.minecraft.wiki/w/Vampiric_Vitality",
      max_level: 10,
      combine_cap: 10,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        30,
        45,
        60,
        75,
        90,
        180,
        240,
        300,
        360,
        420
      ],
      enchanting_req: 22,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ]
    },
    ENCHANTMENT_VAMPIRISM: {
      id: "ENCHANTMENT_VAMPIRISM",
      name: "Vampirism",
      page: "Vampirism",
      url: "https://hypixelskyblock.minecraft.wiki/w/Vampirism",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50,
        200
      ],
      enchanting_req: 15,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_VENOMOUS: {
      id: "ENCHANTMENT_VENOMOUS",
      name: "Venomous",
      page: "Venomous",
      url: "https://hypixelskyblock.minecraft.wiki/w/Venomous",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        20,
        25,
        30,
        40,
        50,
        200,
        0
      ],
      enchanting_req: 17,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ]
    },
    ENCHANTMENT_VICIOUS: {
      id: "ENCHANTMENT_VICIOUS",
      name: "Vicious",
      page: "Vicious",
      url: "https://hypixelskyblock.minecraft.wiki/w/Vicious",
      max_level: null,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        60,
        80,
        100
      ],
      enchanting_req: 26,
      ultimate: false,
      bazaar_levels: [
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_FEROCIOUS_MANA: {
      id: "ENCHANTMENT_FEROCIOUS_MANA",
      name: "Vivacious Vitality",
      page: "Vivacious Vitality",
      url: "https://hypixelskyblock.minecraft.wiki/w/Vivacious_Vitality",
      max_level: 10,
      combine_cap: 10,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        30,
        45,
        60,
        75,
        90,
        180,
        240,
        300,
        360,
        420
      ],
      enchanting_req: 22,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10
      ]
    },
    ENCHANTMENT_ULTIMATE_WISDOM: {
      id: "ENCHANTMENT_ULTIMATE_WISDOM",
      name: "Wisdom",
      page: "Wisdom (Enchantment)",
      url: "https://hypixelskyblock.minecraft.wiki/w/Wisdom_(Enchantment)",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 27,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_ARCANE: {
      id: "ENCHANTMENT_ARCANE",
      name: "Woodsplitter",
      page: "Woodsplitter",
      url: "https://hypixelskyblock.minecraft.wiki/w/Woodsplitter",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        15,
        20,
        25,
        30,
        100
      ],
      enchanting_req: 5,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5,
        6
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_ULTIMATE_BANK: {
      id: "ENCHANTMENT_ULTIMATE_BANK",
      name: "Bank",
      page: "Bank (Enchantment)",
      url: "https://hypixelskyblock.minecraft.wiki/w/Bank_(Enchantment)",
      max_level: 5,
      combine_cap: 5,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        100,
        150,
        200,
        250
      ],
      enchanting_req: 16,
      ultimate: true,
      bazaar_levels: [
        1,
        2,
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_DEPTH_STRIDER: {
      id: "ENCHANTMENT_DEPTH_STRIDER",
      name: "Depth Strider",
      page: "Depth Strider",
      url: "https://hypixelskyblock.minecraft.wiki/w/Depth_Strider",
      max_level: 3,
      combine_cap: 3,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        10,
        20,
        30
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_FROST_WALKER: {
      id: "ENCHANTMENT_FROST_WALKER",
      name: "Frost Walker",
      page: "Frost Walker",
      url: "https://hypixelskyblock.minecraft.wiki/w/Frost_Walker",
      max_level: 2,
      combine_cap: 2,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        15,
        30
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_QUANTUM: {
      id: "ENCHANTMENT_QUANTUM",
      name: "Quantum",
      page: "Quantum",
      url: "https://hypixelskyblock.minecraft.wiki/w/Quantum",
      max_level: 5,
      combine_cap: null,
      combine_status: "unknown",
      combine_xp_cost: {},
      apply_xp_cost: [
        50,
        50,
        100
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        3,
        4,
        5
      ]
    },
    ENCHANTMENT_FLAME: {
      id: "ENCHANTMENT_FLAME",
      name: "Flame",
      page: "Flame",
      url: "https://hypixelskyblock.minecraft.wiki/w/Flame",
      max_level: 2,
      combine_cap: 2,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [
        25,
        50
      ],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2
      ],
      cap_source: "enchantment_table_levels"
    },
    ENCHANTMENT_FIRE_ASPECT: {
      id: "ENCHANTMENT_FIRE_ASPECT",
      name: "Fire Aspect",
      page: "Fire Aspect",
      url: "https://hypixelskyblock.minecraft.wiki/w/Fire_Aspect",
      max_level: 3,
      combine_cap: 2,
      combine_status: "combinable",
      combine_xp_cost: {},
      apply_xp_cost: [],
      enchanting_req: 0,
      ultimate: false,
      bazaar_levels: [
        1,
        2,
        3
      ],
      cap_source: "enchantment_table_levels"
    }
  },
  unmapped_bazaar_ids: [
    "ENCHANTMENT_CURSE_OF_VANISHING",
    "ENCHANTMENT_WITHER_HUNTER"
  ],
  cap_rule_note: "combine_cap from the page's 'combined ... up to' sentence or anvil recipes; where a page has neither, cap_source=enchantment_table_levels: the highest level the page lists from the Enchantment Table (wiki 'Enchanted Book': levels not obtainable from the table cannot be combined)."
};

// ../../tmp/bazaar-calc-review/packages/shared/dist/rules/enchants.js
var RULES = enchants_default.rules;
function enchantRules() {
  return RULES;
}
var BOOK_RE = /^(ENCHANTMENT_[A-Z0-9_]+?)_(\d+)$/;
function parseBookId(id) {
  const m = BOOK_RE.exec(id);
  return m ? { enchant: m[1], level: Number(m[2]) } : null;
}
function canCombineInto(rule, level) {
  return rule.combine_status === "combinable" && rule.combine_cap != null && level >= 2 && level <= rule.combine_cap;
}
function combineXpCost(rule, inputLevel) {
  return rule.combine_xp_cost[String(inputLevel)] ?? 0;
}
function booksNeeded(rule, from, to) {
  if (to <= from)
    return null;
  for (let l = from + 1; l <= to; l++)
    if (!canCombineInto(rule, l))
      return null;
  return 2 ** (to - from);
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/rules/calendar.js
var SB_DAY = 20 * 60 * 1e3;
var SB_MONTH = 31 * SB_DAY;
var SB_YEAR = 12 * SB_MONTH;

// ../../tmp/bazaar-calc-review/packages/shared/dist/rules/timing.js
var SERVER_TICK_MS = 50;
var ACTIONS = {
  create_buy_order: {
    label: "Create buy order",
    steps: ["command", "click", "click", "sign", "click", "click"],
    menus: ["/bz <item>", "Bazaar \u279C <item>", "Create Buy Order", "How many do you want? (custom amount)", "How much do you want to pay? (top +0.1)", "Confirm Buy Order"]
  },
  create_sell_offer: {
    label: "Create sell offer",
    steps: ["command", "click", "click", "click", "click"],
    menus: ["/bz <item>", "Bazaar \u279C <item>", "Create Sell Offer", "At what price are you selling? (best -0.1)", "Confirm Sell Offer"]
  },
  instant_buy: {
    label: "Instant buy",
    steps: ["command", "click", "click", "sign", "click"],
    menus: ["/bz <item>", "Bazaar \u279C <item>", "Buy Instantly", "How many do you want? (custom amount)", "Confirm Instant Buy"]
  },
  npc_buy: {
    label: "Buy one stack from an NPC shop",
    steps: ["click", "click"],
    menus: ["NPC shop menu", "click the item (one stack)"]
  },
  instant_sell: {
    label: "Instant sell",
    steps: ["command", "click", "click"],
    menus: ["/bz <item>", "Bazaar \u279C <item>", "Sell Instantly"]
  },
  claim_order: {
    label: "Claim a filled order",
    steps: ["command", "click", "click"],
    menus: ["/bz", "Manage Orders", "Your Bazaar Orders: click order"]
  },
  cancel_order: {
    label: "Cancel an order",
    steps: ["command", "click", "click", "click"],
    menus: ["/bz", "Manage Orders", "Your Bazaar Orders: click order", "Order options: Cancel"]
  },
  flip_order: {
    label: "Flip a filled buy order to a sell offer",
    steps: ["command", "click", "click", "click", "click"],
    menus: ["/bz", "Manage Orders", "Your Bazaar Orders: click order", "Order options: Flip", "Price (best -0.1)"]
  },
  relist_buy_order: {
    label: "Relist a buy order (claim + cancel + create)",
    steps: ["command", "click", "click", "click", "click", "command", "click", "click", "sign", "click", "click"],
    menus: ["/bz", "Manage Orders", "claim filled part", "click order", "Cancel", "/bz <item>", "Bazaar \u279C <item>", "Create Buy Order", "amount", "price", "Confirm"]
  },
  relist_sell_offer: {
    label: "Relist a sell offer (claim + cancel + create)",
    steps: ["command", "click", "click", "click", "click", "command", "click", "click", "click", "click"],
    menus: ["/bz", "Manage Orders", "claim coins", "click order", "Cancel", "/bz <item>", "Bazaar \u279C <item>", "Create Sell Offer", "price", "Confirm"]
  },
  craft: {
    label: "Craft (crafting table, one recipe output)",
    steps: ["command", "click", "click"],
    menus: ["/craft", "recipe / quick craft slot", "take output"]
  },
  anvil_combine: {
    label: "Combine two books in an anvil",
    steps: ["click", "click", "click", "click"],
    menus: ["book 1 into anvil", "book 2 into anvil", "Combine", "take result"]
  },
  forge_start: {
    label: "Start a forge process",
    steps: ["command", "click", "click", "click"],
    menus: ["/forge", "empty slot", "recipe", "Confirm"]
  },
  forge_claim: {
    label: "Claim a forge slot",
    steps: ["command", "click"],
    menus: ["/forge", "finished slot"]
  }
};
function stepMs(step, t) {
  const base = t.pingMs + SERVER_TICK_MS;
  if (step === "click")
    return base + t.clickDelayMs;
  return base + t.typingMs;
}
function actionSeconds(action, t) {
  const def = ACTIONS[action];
  if (!def)
    return 0;
  return def.steps.reduce((s, step) => s + stepMs(step, t), 0) / 1e3;
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/rules/requirements.js
var DEFAULT_PROFILE = {
  hotmTier: 0,
  quickForgeLevel: 0,
  enchantingLevel: 0,
  collections: {},
  slayers: {},
  reputation: {},
  xpLevels: 0,
  coleMoltenForge: false,
  ignoreRequirements: true
};
function isMet(r, p) {
  switch (r.type) {
    case "hotm":
      return p.hotmTier >= r.tier;
    case "collection": {
      const have = p.collections[r.name];
      return have == null ? false : have >= r.tier;
    }
    case "slayer":
      return (p.slayers[r.name] ?? 0) >= r.level;
    case "reputation":
      return (p.reputation[r.faction] ?? 0) >= r.amount;
    case "enchanting":
      return p.enchantingLevel >= r.level;
    case "xp_levels":
      return p.xpLevels >= r.levels;
    case "forge":
      return p.hotmTier >= 2;
    default:
      return null;
  }
}
function unmet(reqs, p) {
  if (p.ignoreRequirements)
    return [];
  return reqs.filter((r) => isMet(r, p) === false);
}
function dedupeRequirements(reqs) {
  const seen = /* @__PURE__ */ new Map();
  for (const r of reqs) {
    const key2 = r.type === "collection" ? `c:${r.name}` : r.type === "hotm" ? "hotm" : r.type === "slayer" ? `s:${r.name}` : r.type === "enchanting" ? "ench" : r.type === "xp_levels" ? "xp" : r.text;
    const prev = seen.get(key2);
    const rank = (x) => "tier" in x ? x.tier : "level" in x ? x.level : "levels" in x ? x.levels : "amount" in x ? x.amount : 0;
    if (!prev || rank(r) > rank(prev))
      seen.set(key2, r);
  }
  return [...seen.values()];
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/market/signals.js
var FLOW_PRIOR_HOURS = 12;
function blendFlow(weekPerH, observedPerH, watchedHours) {
  if (observedPerH == null || !(watchedHours > 0))
    return weekPerH;
  return Math.min(weekPerH, (observedPerH * watchedHours + weekPerH * FLOW_PRIOR_HOURS) / (watchedHours + FLOW_PRIOR_HOURS));
}
var buyFlowH = (m) => blendFlow(m.isellWeek / 168, m.observedBuyFlowH, m.liveHours);
var sellFlowH = (m) => blendFlow(m.ibuyWeek / 168, m.observedSellFlowH, m.liveHours);
var TYPICAL_BAND = 1.1;
var TYPICAL_MIN_HOURS = { day: 6, week: 12 };
function typicalPrice(m, side) {
  const r = m.ref;
  if (!r)
    return null;
  const d = side === "ask" ? r.ask24 : r.bid24, w = side === "ask" ? r.ask7 : r.bid7;
  if (d != null && (r.n24 ?? 0) >= TYPICAL_MIN_HOURS.day)
    return { price: d, basis: "24 h median", hours: r.n24 ?? 0 };
  if (w != null && (r.n7 ?? 0) >= TYPICAL_MIN_HOURS.week)
    return { price: w, basis: "7-day median", hours: r.n7 ?? 0 };
  return null;
}
function computeFlags(m, hourAgo) {
  const flags = [], why = {};
  const { ask, bid } = m;
  if (m.bidOrders < 3 || m.askOrders < 3) {
    flags.push("dead_book");
    why.dead_book = `${m.bidOrders} buy orders, ${m.askOrders} sell offers (minimum 3 each)`;
  }
  const usualSpread = m.ref?.spreadMed ?? null;
  if (ask && bid && (ask - bid) / bid > 0.5 && (usualSpread == null || (ask - bid) / bid > 1.5 * usualSpread || (ask - bid) / bid > 2)) {
    flags.push("absurd_spread");
    why.absurd_spread = `sell offer ${ask.toFixed(1)} is ${((ask - bid) / bid * 100).toFixed(0)}% above buy order ${bid.toFixed(1)}${usualSpread != null ? ` (usual ${(usualSpread * 100).toFixed(0)}%)` : ""}`;
  }
  const r = m.ref;
  if (r && ask && bid && r.askMed && r.bidMed) {
    const off = (v, med) => v > 1.5 * med || v < med / 1.5;
    if (off(ask, r.askMed) || off(bid, r.bidMed)) {
      flags.push("price_off_median");
      why.price_off_median = `sell offer ${ask.toFixed(1)} vs ${r.days}-day median ${r.askMed.toFixed(1)}; buy order ${bid.toFixed(1)} vs ${r.bidMed.toFixed(1)}`;
    }
    const sp = (ask - bid) / bid;
    if (r.spreadMed && sp > 3 * r.spreadMed && sp > 0.02) {
      flags.push("margin_spike");
      why.margin_spike = `spread ${(sp * 100).toFixed(1)}% vs usual ${(r.spreadMed * 100).toFixed(1)}%`;
    }
  }
  if (hourAgo && ask && bid && hourAgo.ask && hourAgo.bid) {
    const ca = (ask - hourAgo.ask) / hourAgo.ask, cb = (bid - hourAgo.bid) / hourAgo.bid;
    if (Math.abs(ca) > 0.25 || Math.abs(cb) > 0.25) {
      flags.push("recent_jump");
      why.recent_jump = `last hour: sell offer ${(ca * 100).toFixed(0)}%, buy order ${(cb * 100).toFixed(0)}%`;
    }
  }
  for (const [side, levels] of [["buy", m.topBid], ["sell", m.topAsk]]) {
    if (!levels || levels.length <= 3)
      continue;
    const total = levels.reduce((s, l) => s + l.amount, 0);
    const top = levels[0];
    if (total && top.orders === 1 && top.amount / total > 0.8) {
      flags.push("wall");
      why.wall = `one ${side} order at ${top.price.toFixed(1)} holds ${(top.amount / total * 100).toFixed(0)}% of visible units`;
      break;
    }
  }
  const ta = typicalPrice(m, "ask"), tb = typicalPrice(m, "bid");
  const ev = [];
  let strong = false;
  const pct = (x) => `${(x * 100).toFixed(0)}%`;
  if (ask && ta && ask >= 1.4 * ta.price) {
    ev.push(`sell offers ${pct(ask / ta.price - 1)} above their typical ${ta.price.toFixed(1)} (${ta.basis}, ${ta.hours} h)`);
    strong ||= ask >= 2 * ta.price;
  }
  const refAsk = ta?.price ?? ask ?? 0;
  if (bid && tb && bid >= 1.4 * tb.price && bid >= 0.5 * refAsk) {
    ev.push(`buy orders ${pct(bid / tb.price - 1)} above their typical ${tb.price.toFixed(1)} (${tb.basis}, ${tb.hours} h)`);
    strong ||= bid >= 2 * tb.price;
  }
  if (ask && ta && ask >= 1.15 * ta.price && r?.askVol24 && m.askVolume < 0.3 * r.askVol24)
    ev.push(`only ${m.askVolume.toLocaleString("en-US")} units offered vs a typical ${Math.round(r.askVol24).toLocaleString("en-US")}: the cheap supply was bought out`);
  const b0 = m.topBid?.[0], b1 = m.topBid?.[1];
  if (b0 && b1 && b0.orders === 1 && b0.price >= 1.1 * b1.price && b0.price - b1.price >= 0.5 && b0.price >= 0.5 * refAsk && b0.amount * b0.price < 0.02 * Math.max(1, m.bidVolume * b1.price))
    ev.push(`a single small buy order (${b0.amount} at ${b0.price.toFixed(1)}) sits ${pct(b0.price / b1.price - 1)} above the rest: bait for instant sellers`);
  if (r?.spreadMed && ask && bid && (ask - bid) / bid > 3 * r.spreadMed && (ask - bid) / bid > 0.05)
    ev.push(`spread ${pct((ask - bid) / bid)} vs usual ${pct(r.spreadMed)}`);
  const d = r?.delists;
  if (d) {
    const sides = [];
    for (const [name, removed, traded, price] of [["buy orders", d.bidRemoved, d.bidTrades, bid], ["sell offers", d.askRemoved, d.askTrades, ask]])
      if (price && removed >= 5 * Math.max(1, traded) && (removed - traded) * price >= 5e6)
        sides.push(`${Math.round(removed).toLocaleString("en-US")} units pulled from ${name} vs ~${Math.round(traded).toLocaleString("en-US")} really traded in ${d.hours.toFixed(0)} h`);
    if (sides.length) {
      flags.push("mass_delists");
      why.mass_delists = sides.join("; ");
      ev.push(...sides);
    }
  }
  if (strong || ev.length >= 2) {
    flags.push("likely_manipulated");
    why.likely_manipulated = ev.join("; ");
  }
  if (m.liveHours < 2) {
    flags.push("low_history");
    why.low_history = `${m.liveHours.toFixed(1)} h of live data (2 h needed)`;
  }
  m.flags = flags;
  m.flagWhy = why;
}
var seriousFlags = (m) => m.flags.filter((f) => f !== "low_history" && f !== "mass_delists");
var BOOK = /^(ENCHANTMENT_.+)_(\d+)$/;
function bookCeiling(market, id) {
  const b = BOOK.exec(id);
  if (!b)
    return null;
  let best = null;
  for (let l = Number(b[2]) + 1; l <= Number(b[2]) + 10; l++) {
    const h = market.get(`${b[1]}_${l}`);
    if (h?.ask != null && (!best || h.ask < best.price))
      best = { price: h.ask, item: h.id };
  }
  return best;
}
function flagBookLadders(market) {
  for (const m of market.values()) {
    const c = m.ask != null ? bookCeiling(market, m.id) : null;
    if (!c || m.ask <= c.price)
      continue;
    m.flags.push("above_higher_level");
    m.flagWhy.above_higher_level = `sell offers at ${m.ask.toFixed(1)} cost more than ${market.get(c.item)?.name ?? c.item} at ${c.price.toFixed(1)}`;
  }
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/market/names.js
var ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"];
function prettyName(id, name) {
  if (name)
    return name.replace(/%%\w+%%|§./g, "").trim();
  const b = parseBookId(id);
  if (b) {
    const rule = enchantRules()[b.enchant];
    const base = rule?.name ?? b.enchant.replace(/^ENCHANTMENT_(ULTIMATE_)?/, "").replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
    return `${base} ${ROMAN[b.level] ?? b.level}`;
  }
  return id.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/market/book.js
var LEVEL = 8 + 8 + 4;

// ../../tmp/bazaar-calc-review/packages/shared/dist/market/assemble.js
function assembleMarket(inp) {
  const out = /* @__PURE__ */ new Map();
  for (const r of inp.quotes) {
    const s = inp.stats.get(r.id);
    const a = inp.ah.get(r.id);
    const m = {
      id: r.id,
      name: prettyName(r.id, inp.names.get(r.id)),
      ts: r.ts,
      ask: r.ask,
      bid: r.bid,
      askVolume: r.askVolume,
      bidVolume: r.bidVolume,
      askOrders: r.askOrders,
      bidOrders: r.bidOrders,
      ibuyWeek: r.ibuyWeek,
      isellWeek: r.isellWeek,
      undercutBuyH: s?.undercutBuyH ?? null,
      undercutSellH: s?.undercutSellH ?? null,
      liveHours: s?.liveHours ?? 0,
      observedBuyFlowH: s?.observedBuyFlowH ?? null,
      observedSellFlowH: s?.observedSellFlowH ?? null,
      ref: s ? {
        askMed: s.askMed,
        bidMed: s.bidMed,
        spreadMed: s.spreadMed,
        days: s.days,
        ask24: s.ask24 ?? null,
        bid24: s.bid24 ?? null,
        n24: s.n24 ?? 0,
        ask7: s.ask7 ?? null,
        bid7: s.bid7 ?? null,
        n7: s.n7 ?? 0,
        askVol24: s.askVol24 ?? null,
        bidVol24: s.bidVol24 ?? null,
        delists: s.delists ?? null
      } : null,
      topBid: r.bids,
      topAsk: r.asks,
      holdBid: inp.hold.get(r.id)?.bid ?? null,
      holdAsk: inp.hold.get(r.id)?.ask ?? null,
      ahLowestBin: a?.lowestBin ?? null,
      ahSales24h: a?.sales24h ?? 0,
      ahMedianSale24h: a?.medianSale24h ?? null,
      flags: [],
      flagWhy: {}
    };
    computeFlags(m, s?.hourAgo ?? void 0);
    out.set(m.id, m);
  }
  const newest = Math.max(0, ...[...out.values()].map((m) => m.ts));
  for (const m of out.values())
    if (newest - m.ts > 15 * 6e4) {
      m.ask = null;
      m.bid = null;
      m.flags.push("stale");
      m.flagWhy.stale = `no update from Hypixel for ${Math.round((newest - m.ts) / 6e4)} min (not listed on the bazaar right now)`;
    }
  flagBookLadders(out);
  for (const [key2, a] of inp.ah) {
    if (out.has(key2) || a.lowestBin == null)
      continue;
    out.set(key2, {
      id: key2,
      name: prettyName(key2, inp.names.get(key2)),
      ts: inp.now ?? Date.now(),
      ask: null,
      bid: null,
      askVolume: 0,
      bidVolume: 0,
      askOrders: 0,
      bidOrders: 0,
      ibuyWeek: 0,
      isellWeek: 0,
      undercutBuyH: null,
      undercutSellH: null,
      liveHours: 0,
      ahLowestBin: a.lowestBin,
      ahSales24h: a.sales24h,
      ahMedianSale24h: a.medianSale24h,
      flags: ["auction_only"],
      flagWhy: { auction_only: "not on the bazaar; auction-house price shown" }
    });
  }
  return out;
}
function quotesFromBazaar(d) {
  const lv = (o) => o.map((x) => ({ price: Math.round(x.pricePerUnit * 100) / 100, amount: Math.round(x.amount), orders: x.orders }));
  return Object.entries(d.products).map(([id, p]) => {
    const q = p.quick_status, bids = p.sell_summary ?? [], asks = p.buy_summary ?? [];
    return {
      id,
      ts: d.lastUpdated,
      ask: asks[0] ? Math.round(asks[0].pricePerUnit * 100) / 100 : null,
      bid: bids[0] ? Math.round(bids[0].pricePerUnit * 100) / 100 : null,
      askVolume: q.buyVolume ?? 0,
      bidVolume: q.sellVolume ?? 0,
      askOrders: q.buyOrders ?? 0,
      bidOrders: q.sellOrders ?? 0,
      ibuyWeek: q.buyMovingWeek ?? 0,
      isellWeek: q.sellMovingWeek ?? 0,
      bids: lv(bids),
      asks: lv(asks)
    };
  });
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/fill/sizing.js
var MIN_MEASURED_EPISODES = 8;
var SIZE_GRID = (() => {
  const g = /* @__PURE__ */ new Set();
  for (let k = 0; k <= 32; k++)
    g.add(Math.round(2 ** (k / 2)));
  g.add(BAZAAR.maxUnitsPerOrder);
  return [...g].sort((a, b) => a - b);
})();
function fillModel(stats, flowH, undercutsH, checkMin, unknownShare) {
  const perS = flowH / 3600;
  if (stats && stats.n >= MIN_MEASURED_EPISODES && stats.samples.length >= MIN_MEASURED_EPISODES) {
    const sorted = stats.samples.map(([, f]) => f).sort((a, b) => a - b);
    const clip = 3 * Math.max(1, sorted[Math.floor(0.9 * (sorted.length - 1))]);
    const clipped = stats.samples.map(([t, f, g]) => [t, Math.min(f, clip), g ?? 0]);
    stats = { ...stats, samples: clipped };
    const measured = stats.samples.reduce((a, [, f]) => a + f, 0) / Math.max(1e-9, stats.samples.reduce((a, [t]) => a + t, 0));
    const k = measured > 0 ? perS / measured : 0;
    return { basis: "measured", stats, flowH, samples: stats.samples.map(([t, f, g]) => [t, measured > 0 ? f * k : perS * t, g ?? 0]) };
  }
  const c = checkMin * 60;
  const mean = undercutsH != null ? undercutsH > 0 ? 3600 / undercutsH : 6 * 3600 : unknownShare / Math.max(0.01, 1 - unknownShare) * (c / 2);
  const n = 20, samples = [];
  for (let i = 0; i < n; i++) {
    const t = -mean * Math.log(1 - (i + 0.5) / n);
    samples.push([t, perS * t]);
  }
  return { basis: "estimated", stats: stats ?? null, flowH, samples };
}
function simulate(samples, qty, checkS) {
  let got = 0, cyc = 0, on = 0, full = 0, cycles = 0;
  for (let i = 0; i < samples.length; ) {
    let [t, f, g] = samples[i];
    let j = i + 1;
    while (g && f < qty && j < samples.length) {
      const nx = samples[j++];
      t += nx[0];
      f += nx[1];
      g = nx[2];
    }
    i = j;
    const fillAt = f >= qty && f > 0 ? t * (qty / f) : Infinity;
    const end = Math.min(t, fillAt);
    cyc += checkS * Math.max(1, Math.ceil(end / checkS - 1e-9));
    got += Math.min(qty, f);
    on += end;
    if (fillAt <= t)
      full++;
    cycles++;
  }
  if (cyc <= 0)
    return { qty, unitsH: 0, ordersH: 0, onTop: 0, fullShare: 0 };
  return { qty, unitsH: got / cyc * 3600, ordersH: cycles / cyc * 3600, onTop: on / cyc, fullShare: full / Math.max(1, cycles) };
}
var curves = /* @__PURE__ */ new WeakMap();
function curve(m, checkMin) {
  let byCheck = curves.get(m);
  if (!byCheck)
    curves.set(m, byCheck = /* @__PURE__ */ new Map());
  let c = byCheck.get(checkMin);
  if (!c) {
    let best = null;
    c = SIZE_GRID.map((q) => {
      const p = simulate(m.samples, q, checkMin * 60);
      p.unitsH = Math.min(p.unitsH, m.flowH);
      if (!best || p.unitsH > best.unitsH)
        best = p;
      return { ...best, qty: q };
    });
    byCheck.set(checkMin, c);
  }
  return c;
}
function at(c, qty) {
  if (qty <= c[0].qty) {
    const p = c[0];
    const k = qty / p.qty;
    return { ...p, qty, unitsH: p.unitsH * k };
  }
  for (let i = 1; i < c.length; i++) {
    const a = c[i - 1], b = c[i];
    if (qty <= b.qty) {
      const w = (Math.log(qty) - Math.log(a.qty)) / (Math.log(b.qty) - Math.log(a.qty));
      const mix = (x, y) => x + (y - x) * w;
      return { qty, unitsH: mix(a.unitsH, b.unitsH), ordersH: mix(a.ordersH, b.ordersH), onTop: mix(a.onTop, b.onTop), fullShare: mix(a.fullShare, b.fullShare) };
    }
  }
  return { ...c[c.length - 1], qty };
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/fill/toptrack.js
var key = (p) => Math.round(p * 100);
var better = (side, a, b) => side === "bid" ? a > b + 1e-9 : a < b - 1e-9;
var amounts = (levels) => new Map(levels.map((l) => [key(l.price), l.amount]));
var TopTracker = class {
  maxGapMs;
  open = /* @__PURE__ */ new Map();
  last = /* @__PURE__ */ new Map();
  constructor(maxGapMs = 15e4) {
    this.maxGapMs = maxGapMs;
  }
  /** One poll of one item. Returns the episodes that finished at this poll. */
  step(item, ts, bids, asks) {
    const prev = this.last.get(item);
    if (prev && ts <= prev.ts)
      return [];
    this.last.set(item, { ts, bids, asks });
    const out = [];
    const continuous = prev != null && ts - prev.ts <= this.maxGapMs;
    for (const side of ["bid", "ask"]) {
      const k = `${item}|${side}`;
      const cur = side === "bid" ? bids : asks;
      const st = this.open.get(k);
      if (!continuous) {
        if (st?.fresh)
          out.push(close(side, st, st.lastTs, "cut"));
        this.open.delete(k);
        if (cur[0])
          this.open.set(k, start(cur[0], ts, ts, false));
        continue;
      }
      const before = side === "bid" ? prev.bids : prev.asks;
      const top = cur[0];
      if (st) {
        const now = amounts(cur);
        const removedHere = Math.max(0, (amounts(before).get(key(st.price)) ?? 0) - (now.get(key(st.price)) ?? 0));
        let flow = removedHere;
        if (!top || better(side, st.price, top.price)) {
          flow = 0;
          for (const l of before)
            if (!top || !better(side, top.price, l.price))
              flow += Math.max(0, l.amount - (now.get(key(l.price)) ?? 0));
        }
        st.removed += removedHere;
        st.flow += flow;
        if (top && key(top.price) === key(st.price)) {
          st.polls++;
          st.lastTs = ts;
          continue;
        }
        if (st.fresh)
          out.push(close(side, st, ts, top && better(side, top.price, st.price) ? "outbid" : "gone"));
        this.open.delete(k);
        if (top)
          this.open.set(k, start(top, ts, prev.ts, better(side, top.price, st.price)));
      } else if (top) {
        const oldTop = before[0];
        this.open.set(k, start(top, ts, prev.ts, !oldTop || better(side, top.price, oldTop.price)));
      }
    }
    return out;
  }
  /** Close everything still open (end of a replay). */
  flush() {
    return this.flushItems().map((x) => x.e);
  }
  /** Like flush(), with the item of each episode. */
  flushItems() {
    const out = [];
    for (const [k, st] of this.open)
      if (st.fresh)
        out.push({ item: k.slice(0, k.lastIndexOf("|")), e: close(k.endsWith("|bid") ? "bid" : "ask", st, st.lastTs, "cut") });
    this.open.clear();
    return out;
  }
};
function start(top, ts, prevTs, fresh) {
  return { price: top.price, startTs: ts, prevTs, lastTs: ts, polls: 1, flow: 0, removed: 0, startAmount: top.amount, startOrders: top.orders, fresh };
}
function close(side, st, endTs, end) {
  const loS = (st.lastTs - st.startTs) / 1e3;
  const hiS = (end === "cut" ? st.lastTs - st.prevTs : endTs - st.prevTs) / 1e3;
  const durS = end === "cut" ? loS + (st.startTs - st.prevTs) / 2e3 : (loS + hiS) / 2;
  return { side, price: st.price, startTs: st.startTs, endTs, durS, loS, hiS, polls: st.polls, flow: st.flow, removedAtPrice: st.removed, startAmount: st.startAmount, startOrders: st.startOrders, end };
}
var QS = [0.05, 0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95];
var MAX_SAMPLES = 64;
function survival(eps) {
  const sorted = [...eps].sort((a, b) => a.durS - b.durS);
  let atRisk = sorted.length, s = 1;
  const out = [{ t: 0, s: 1 }];
  for (let i = 0; i < sorted.length; ) {
    const t = sorted[i].durS;
    let d = 0, c = 0;
    while (i < sorted.length && sorted[i].durS === t) {
      if (sorted[i].end === "cut")
        c++;
      else
        d++;
      i++;
    }
    if (d > 0) {
      s *= 1 - d / atRisk;
      out.push({ t, s });
    }
    atRisk -= d + c;
  }
  return out;
}
var kmQuantile = (km, q) => km.find((p) => p.s <= 1 - q)?.t ?? null;
function summarizeTop(eps, hours) {
  if (!eps.length)
    return null;
  const km = survival(eps);
  const sortedDur = eps.map((e) => e.durS).sort((a, b) => a - b);
  const pick = (xs, q) => xs[Math.min(xs.length - 1, Math.floor(q * xs.length))];
  const units = eps.map((e) => e.flow).sort((a, b) => a - b);
  const totalS = eps.reduce((a, e) => a + e.durS, 0);
  const ended = eps.filter((e) => e.end !== "cut");
  return {
    n: eps.length,
    censored: eps.length - ended.length,
    hours,
    p25: kmQuantile(km, 0.25),
    p50: kmQuantile(km, 0.5),
    p75: kmQuantile(km, 0.75),
    p90: kmQuantile(km, 0.9),
    holdQ: QS.map((q) => kmQuantile(km, q) ?? pick(sortedDur, q)),
    meanS: totalS / eps.length,
    beatenFast: ended.filter((e) => e.polls === 1).length / Math.max(1, ended.length),
    outbid: ended.filter((e) => e.end === "outbid").length / Math.max(1, ended.length),
    gone: ended.filter((e) => e.end === "gone").length / Math.max(1, ended.length),
    flowPerMin: totalS > 0 ? units.reduce((a, b) => a + b, 0) / totalS * 60 : 0,
    unitsP50: pick(units, 0.5),
    unitsMean: units.reduce((a, b) => a + b, 0) / units.length,
    // evenly spaced in time so a burst does not dominate; episodes must be in time order
    samples: Array.from({ length: Math.min(MAX_SAMPLES, eps.length) }, (_, i) => eps[Math.floor(i * eps.length / Math.min(MAX_SAMPLES, eps.length))]).map((e) => [Math.round(e.durS * 10) / 10, Math.round(e.flow), e.end === "outbid" ? 0 : 1])
  };
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/calc/engine.js
var DEFAULT_SETTINGS = {
  coins: 1e8,
  bazaarFlipperLevel: 0,
  checkIntervalMin: 5,
  hoursPerDay: 4,
  dailyLimit: BAZAAR.dailyLimitDefault,
  attention: 0.8,
  craftsPerHourMax: 1e3,
  unknownCompetitionShare: 0.5,
  minUnitsPerHour: 0,
  includeFlagged: false,
  pingMs: 80,
  clickDelayMs: 350,
  typingMs: 1500
};
var fmts = /* @__PURE__ */ new Map();
var fmt = (v, d = 1) => {
  let f = fmts.get(d);
  if (!f)
    fmts.set(d, f = new Intl.NumberFormat("en-US", { maximumFractionDigits: d }));
  return f.format(v);
};
function evaluate(route, s, p, capital = s.coins, limits) {
  const explain = [];
  const tax = taxRate(s.bazaarFlipperLevel);
  const costPerUnit = route.buys.reduce((a, b) => a + b.qty * b.price, 0);
  const profitPerUnit = route.sell.netPrice - costPerUnit;
  for (const b of route.buys)
    explain.push(`Per ${route.sell.name} sold: ${b.mode === "order" ? "buy order" : b.mode === "npc" ? `NPC shop (${b.source ?? "NPC"})` : "instant buy"} ${fmt(b.qty, 3)}x ${b.name} at ${fmt(b.price)} = ${fmt(b.qty * b.price)}`);
  for (const st of route.steps)
    explain.push(`${st.label}: ${fmt(st.opsPerUnit, 3)} operation(s) per unit`);
  explain.push(`${route.sell.mode === "offer" ? "Sell offer" : route.sell.mode === "instant" ? "Instant sell" : "AH reference price"} ${route.sell.name} at ${fmt(route.sell.grossPrice)} - ${(tax * 100).toFixed(3)}% tax = ${fmt(route.sell.netPrice)}`);
  if (route.sell.currentPrice != null)
    explain.push(`Sale priced at the ${route.sell.priceBasis}: right now it is listed at ${fmt(route.sell.currentPrice)} (${((route.sell.currentPrice / route.sell.grossPrice - 1) * 100).toFixed(0)}% higher), which buyers are unlikely to pay by the time you sell`);
  explain.push(`Profit per unit = ${fmt(route.sell.netPrice)} - ${fmt(costPerUnit)} = ${fmt(profitPerUnit)}`);
  const caps = [];
  for (const b of route.buys)
    caps.push({
      name: `${b.name} supply`,
      unitsH: b.flowH / b.qty,
      why: b.mode === "order" ? `your buy orders fill ~${fmt(b.flowH)}/h (${b.share == null ? "competition unknown" : `on top ${(b.share * 100).toFixed(0)}%`})` : b.mode === "npc" ? `NPC shops sell at most ${BAZAAR.npcDailyBuyLimit}/day per item = ${fmt(b.flowH)}/h over your ${s.hoursPerDay} h` : `sellers list ~${fmt(b.flowH)}/h`
    });
  caps.push({
    name: `${route.sell.name} demand`,
    unitsH: route.sell.flowH,
    why: route.sell.mode === "offer" ? `your sell offers fill ~${fmt(route.sell.flowH)}/h (${route.sell.share == null ? "competition unknown" : `on top ${(route.sell.share * 100).toFixed(0)}%`})` : `buyers take ~${fmt(route.sell.flowH)}/h`
  });
  const craftOps = route.steps.filter((x) => x.type === "craft").reduce((a, x) => a + x.opsPerUnit, 0);
  if (craftOps > 0)
    caps.push({ name: "your crafting speed", unitsH: s.craftsPerHourMax / craftOps, why: `${s.craftsPerHourMax} crafts/h max` });
  let forgeSlotsUsed = 0;
  const hoursPlayed = Math.max(0.1, s.hoursPerDay);
  const runsPerSlot = (dH) => Math.min(Math.floor(24 / dH + 1e-9), Math.floor(hoursPlayed / dH + 1e-9) + 1);
  for (const st of route.steps.filter((x) => x.type === "forge")) {
    const own = forgeSlots(p.hotmTier), slots = limits?.forgeSlots ?? (own || forgeSlots(FORGE.minHotm));
    forgeSlotsUsed = slots;
    const dH = Math.max(1 / 3600, (st.forgeSeconds ?? 1) / 3600);
    const runsPerSlotDay = runsPerSlot(dH);
    caps.push({
      name: "forge slots",
      unitsH: slots * runsPerSlotDay * st.outputPerOp / Math.max(1e-9, st.opsPerUnit * st.outputPerOp) / hoursPlayed,
      why: `${slots} slots${!own && limits?.forgeSlots == null ? ` (assumed: HotM ${FORGE.minHotm}, set your HotM tier)` : ""} x ${fmt(runsPerSlotDay, 1)} runs/day each (${fmt(dH, 2)} h per run: what fits in your ${hoursPlayed} h of play, plus one started before you log off)`
    });
  }
  const hours = Math.max(0.1, s.hoursPerDay);
  const checkMin = s.checkIntervalMin;
  const forgeHoldH = route.steps.filter((x) => x.type === "forge").reduce((a, x) => a + (x.forgeSeconds ?? 0) / 3600, 0);
  const stepSec = route.steps.reduce((a, st) => a + st.opsPerUnit * (st.type === "craft" ? actionSeconds("craft", s) : st.type === "combine" ? actionSeconds("anvil_combine", s) : actionSeconds("forge_start", s) + actionSeconds("forge_claim", s)), 0);
  const ordersUsed = route.buys.filter((b) => b.mode === "order").length + (route.sell.mode === "offer" ? 1 : 0);
  const valid = caps.filter((c) => Number.isFinite(c.unitsH));
  const nonOrder = valid.filter((c) => !route.buys.some((b) => b.mode === "order" && c.name === `${b.name} supply`) && !(route.sell.mode === "offer" && c.name === `${route.sell.name} demand`));
  const market = nonOrder.reduce((a, c) => c.unitsH < a.unitsH ? c : a, { name: "none", unitsH: Infinity, why: "" });
  const legsOL = [];
  for (const b of route.buys)
    if (b.mode === "order" && b.fill)
      legsOL.push({ side: "buy", item: b.item, name: b.name, price: b.price, perUnit: b.qty, lock: b.price, fill: b.fill, maxQty: b.maxQty ?? BAZAAR.maxUnitsPerOrder, c: curve(b.fill, checkMin) });
  if (route.sell.mode === "offer" && route.sell.fill)
    legsOL.push({ side: "sell", item: route.sell.item, name: route.sell.name, price: route.sell.grossPrice, perUnit: 1, lock: costPerUnit, fill: route.sell.fill, maxQty: route.sell.maxQty ?? BAZAAR.maxUnitsPerOrder, c: curve(route.sell.fill, checkMin) });
  const bMax = legsOL.length ? Math.max(1, Math.floor(Math.min(...legsOL.map((l) => l.maxQty / l.perUnit)))) : 1;
  const forgeCapital = (U2) => {
    let c = 0;
    for (const st of route.steps)
      if (st.type === "forge" && U2 > 0) {
        const dH = Math.max(1 / 3600, (st.forgeSeconds ?? 1) / 3600);
        const busy = Math.min(forgeSlotsUsed || 1, U2 * hoursPlayed * st.opsPerUnit / runsPerSlot(dH));
        c += busy * (costPerUnit / Math.max(1e-9, st.opsPerUnit));
      }
    return c;
  };
  const usageAt = (B, U2, seq = false) => {
    let limitH = 0, instantLimitH = 0, clickS = U2 * stepSec, capitalNow = forgeCapital(U2), fullBuysH = 0, buyLock = 0, sellLock = 0;
    const legs = [];
    for (const l of legsOL) {
      const total = Math.max(1, Math.round(B * l.perUnit));
      const parallel = Math.ceil(total / l.maxQty), qty = Math.ceil(total / parallel);
      const pt = at(l.c, total);
      const need = U2 * l.perUnit;
      const ordersH = pt.unitsH > 0 ? pt.ordersH * Math.min(1, need / pt.unitsH) : 0;
      const free = l.side === "sell" && route.kind === "bazaar" ? Math.min(ordersH, fullBuysH) : 0;
      const lim = (ordersH - free) * parallel * limitContribution(qty * l.price);
      if (l.side === "buy")
        fullBuysH += ordersH * pt.fullShare;
      limitH += lim;
      clickS += ordersH * parallel * actionSeconds(l.side === "buy" ? "relist_buy_order" : "relist_sell_offer", s);
      if (l.side === "buy")
        buyLock += total * l.lock;
      else
        sellLock += total * l.lock;
      const st = l.fill.stats;
      legs.push({
        side: l.side,
        item: l.item,
        name: l.name,
        price: l.price,
        qty,
        parallel,
        maxQty: l.maxQty,
        ordersH,
        unitsH: need,
        perOrder: pt.ordersH > 0 ? pt.unitsH / pt.ordersH : 0,
        fullShare: pt.fullShare,
        onTop: pt.onTop,
        limitCoinsH: lim,
        freeOrdersH: free,
        coinsLocked: total * l.lock,
        basis: l.fill.basis,
        options: [],
        hold: st ? { n: st.n, hours: st.hours, p50: st.p50, p90: st.p90, beatenFast: st.beatenFast, flowPerMin: st.flowPerMin } : null
      });
    }
    for (const b of route.buys) {
      if (b.mode === "order" && b.fill)
        continue;
      if (b.mode === "npc")
        clickS += U2 * b.qty / 64 * actionSeconds("npc_buy", s);
      else {
        instantLimitH += U2 * b.qty * b.price;
        clickS += U2 * b.qty / BAZAAR.maxInstantBuyUnits * actionSeconds("instant_buy", s);
      }
    }
    if (route.sell.mode === "instant") {
      instantLimitH += U2 * route.sell.grossPrice;
      clickS += U2 / BAZAAR.maxInstantBuyUnits * actionSeconds("instant_sell", s);
    }
    capitalNow += seq ? Math.max(buyLock, sellLock) : buyLock + sellLock;
    if (!legsOL.length && U2 > 0)
      capitalNow += Math.max(costPerUnit, U2 * costPerUnit * (checkMin / 60));
    return { limitH: limitH + instantLimitH, instantLimitH, clickS, capital: capitalNow, legs };
  };
  const limitBudgetH = (limits?.limitCoinsDay ?? s.dailyLimit) / hours;
  const activeBudget = Math.min(3600 * s.attention, limits?.activeSecondsH ?? Infinity);
  const hasBuyOrder = legsOL.some((l) => l.side === "buy"), hasSellOffer = legsOL.some((l) => l.side === "sell");
  const run = (B, seq = false) => {
    let U2 = Number.isFinite(market.unitsH) ? Math.max(0, market.unitsH) : Infinity;
    let by = { name: market.name, why: () => market.why };
    let buyRate = Infinity, sellRate = Infinity;
    for (const l of legsOL) {
      const total = Math.max(1, Math.round(B * l.perUnit)), pt = at(l.c, total), r = pt.unitsH / l.perUnit;
      if (l.side === "buy")
        buyRate = Math.min(buyRate, r);
      else
        sellRate = Math.min(sellRate, r);
      if (r < U2) {
        U2 = r;
        by = {
          name: `${l.name} ${l.side === "buy" ? "supply" : "demand"}`,
          why: () => `${l.side === "buy" ? "buy orders" : "sell offers"} of ${fmt(total, 0)}: on top ${(pt.onTop * 100).toFixed(0)}% of the time, ~${fmt(pt.unitsH, 1)} filled/h (${l.fill.basis})`
        };
      }
    }
    if (seq && Number.isFinite(buyRate) && Number.isFinite(sellRate) && buyRate > 0 && sellRate > 0) {
      const r = 1 / (1 / buyRate + 1 / sellRate);
      if (r < U2) {
        U2 = r;
        by = { name: "one batch at a time", why: () => `your coins cover one batch, not a buy order and unsold stock together: buy ${fmt(B, 0)} (~${fmt(B / buyRate * 60, 0)} min), then sell them (~${fmt(B / sellRate * 60, 0)} min), then buy again` };
      }
    }
    if (!Number.isFinite(U2))
      U2 = 0;
    const u0 = usageAt(B, U2, seq);
    const kLimit = u0.limitH > limitBudgetH ? limitBudgetH / u0.limitH : 1, kClick = u0.clickS > activeBudget ? activeBudget / u0.clickS : 1;
    if (U2 > 0 && Math.min(kLimit, kClick) < 1) {
      if (kLimit <= kClick)
        by = { name: "daily bazaar limit", why: () => `${fmt(s.dailyLimit / 1e9, 1)}B/day over ${hours} h = ${fmt(limitBudgetH / 1e6, 0)}M/h` };
      else
        by = { name: "your clicking time", why: () => `${fmt(activeBudget / 60)} min/h of clicking (relists, claims, crafts)` };
      U2 *= Math.min(kLimit, kClick);
    }
    const fixed = legsOL.length ? usageAt(B, 0, seq).capital : costPerUnit;
    if (fixed > capital) {
      if (!seq && hasBuyOrder && hasSellOffer)
        return run(B, true);
      return { B, U: 0, seq, by: { name: "your coins", why: () => `one batch of ${fmt(B, 0)} needs ${fmt(fixed, 0)} coins${seq ? "" : " in orders and stock"}; you have ${fmt(capital, 0)}` } };
    }
    const withU = usageAt(B, U2, seq).capital;
    if (withU > capital && U2 > 0) {
      U2 = U2 * ((capital - fixed) / (withU - fixed));
      by = { name: "your coins", why: () => `${fmt(capital, 0)} coins` };
    }
    return { B, U: Math.max(0, U2), by, seq };
  };
  const grid = [];
  if (legsOL.length) {
    for (let k = 0; k <= 40; k++) {
      const v = Math.round(bMax ** (k / 40));
      if (!grid.includes(v))
        grid.push(v);
    }
    for (const seq of [false, true]) {
      const perBatch = usageAt(1, 0, seq).capital;
      if (perBatch > 0) {
        const b = Math.floor(capital / perBatch);
        for (const v of [b - 1, b, b + 1])
          if (v >= 1 && v <= bMax && !grid.includes(v))
            grid.push(v);
      }
    }
    grid.sort((a, b) => a - b);
  } else
    grid.push(1);
  const runs = grid.map((b) => run(b));
  let top = runs.reduce((a, r) => r.U > a.U ? r : a, runs[0]);
  if (legsOL.length && top.U > 0) {
    const i = grid.indexOf(top.B), lo = grid[Math.max(0, i - 1)], hi = grid[Math.min(grid.length - 1, i + 1)];
    const step = Math.max(1, Math.ceil((hi - lo) / 60));
    for (let b = lo; b <= hi; b += step)
      if (!grid.includes(b)) {
        const r = run(b);
        runs.push(r);
        if (r.U > top.U)
          top = r;
      }
    runs.sort((a, b) => a.B - b.B);
  }
  const chosen = runs.find((r) => r.U >= top.U * 0.995) ?? top;
  const U = chosen.U, best = { name: chosen.by.name, unitsH: chosen.U, why: chosen.by.why() };
  const use = usageAt(chosen.B, U, chosen.seq);
  const batchOptions = [...new Set([Math.ceil(chosen.B / 4), Math.ceil(chosen.B / 2), chosen.B, chosen.B * 2, chosen.B * 4, bMax].map((b) => Math.max(1, Math.min(bMax, b))))].map((b) => {
    const r = run(b), u = usageAt(b, r.U, r.seq);
    return { batch: b, unitsH: r.U, coinsH: r.U * profitPerUnit, limitCoinsH: u.limitH, capital: u.capital, clickMinH: u.clickS / 60, limitedBy: r.by.name, oneAtATime: r.seq };
  });
  use.legs.forEach((l, i) => {
    l.options = batchOptions.map((o) => {
      const lu = usageAt(o.batch, o.unitsH, o.oneAtATime).legs[i];
      return { qty: lu.qty, unitsH: lu.unitsH, ordersH: lu.ordersH, perOrder: lu.perOrder, limitCoinsH: lu.limitCoinsH };
    });
  });
  caps.length = 0;
  caps.push(...nonOrder);
  for (const l of use.legs)
    caps.push({
      name: `${l.name} ${l.side === "buy" ? "supply" : "demand"}`,
      unitsH: at(legsOL.find((x) => x.item === l.item && x.side === l.side).c, l.qty).unitsH / legsOL.find((x) => x.item === l.item && x.side === l.side).perUnit,
      why: `${l.side === "buy" ? "buy orders" : "sell offers"} of ${fmt(l.qty, 0)}, on top ${(l.onTop * 100).toFixed(0)}% of the time (${l.basis})`
    });
  const head = (used, max) => used > 0 ? U * (max / used) : Infinity;
  caps.push({ name: "daily bazaar limit", unitsH: best.name === "daily bazaar limit" ? U : head(use.limitH, limitBudgetH), why: `${fmt(use.limitH / 1e6, 1)}M/h of ${fmt(limitBudgetH / 1e6, 0)}M/h (orders count their full value when created, relists again, instant trades their value)` });
  caps.push({ name: "your clicking time", unitsH: best.name === "your clicking time" ? U : head(use.clickS, activeBudget), why: `${fmt(use.clickS / 60)} of ${fmt(activeBudget / 60)} min/h: relisting when beaten, claiming, crafting (ping ${s.pingMs} ms)` });
  caps.push({ name: "your coins", unitsH: best.name === "your coins" ? U : head(use.capital, capital), why: `${fmt(use.capital, 0)} of ${fmt(capital, 0)} coins: ${chosen.seq ? "one batch at a time (buy, then sell)" : "money in buy orders + stock in sell offers"}${forgeHoldH ? " + inputs in the forge" : ""}` });
  const unitsH = U;
  const coinsH = unitsH * profitPerUnit;
  if (legsOL.length)
    explain.push(`Runs in batches of ${fmt(chosen.B, 0)}${chosen.seq ? ", one batch at a time" : ""}: ${use.legs.map((l) => `${l.side === "buy" ? "buy order" : "sell offer"} ${l.parallel > 1 ? `${l.parallel} x ` : ""}${fmt(l.qty, 0)}x ${l.name}`).join(", ")}`);
  for (const l of use.legs)
    explain.push(`${l.side === "buy" ? "Buy order" : "Sell offer"} ${l.name}: ${fmt(l.qty, 0)} per order, ~${fmt(l.ordersH, 1)} orders/h, ~${fmt(l.perOrder, 1)} filled per order, on top ${(l.onTop * 100).toFixed(0)}% of the time (${l.basis === "measured" ? `measured from ${l.hold?.n ?? 0} top-of-book episodes` : "estimated: not enough live data yet"})`);
  explain.push(`Units per hour played = ${fmt(unitsH, 2)} (limited by ${best.name}: ${best.why}); ${fmt(unitsH * hours, 1)} units/day over ${hours} h`);
  explain.push(`Coins/h = ${fmt(unitsH, 2)} x ${fmt(profitPerUnit)} = ${fmt(coinsH, 0)}${profitPerUnit <= 0 ? " (this route loses money at current prices)" : ""}`);
  const limitCoinsH = use.limitH;
  const reqs = dedupeRequirements(route.requirements);
  return {
    ...route,
    requirements: reqs,
    costPerUnit,
    profitPerUnit,
    marginPct: costPerUnit > 0 ? profitPerUnit / costPerUnit : 0,
    unitsH,
    coinsH,
    limitedBy: best.name,
    caps: caps.filter((c) => Number.isFinite(c.unitsH)).sort((a, b) => a.unitsH - b.unitsH),
    capitalAllocated: capital,
    capitalUsed: use.capital,
    ordersUsed: ordersUsed + use.legs.reduce((a, l) => a + l.parallel - 1, 0),
    oneAtATime: chosen.seq,
    // slots actually busy at this rate: runs needed per day / runs one slot does per day
    forgeSlotsUsed: unitsH > 0 && forgeSlotsUsed > 0 ? Math.min(forgeSlotsUsed, Math.ceil(Math.max(...route.steps.filter((x) => x.type === "forge").map((x) => {
      const dH = Math.max(1 / 3600, (x.forgeSeconds ?? 1) / 3600);
      return unitsH * hoursPlayed * x.opsPerUnit / runsPerSlot(dH);
    })) - 1e-9)) : 0,
    limitCoinsH,
    limitHoursLeft: limitCoinsH > 0 ? s.dailyLimit / limitCoinsH : Infinity,
    activeSecondsH: use.clickS,
    orderPlan: use.legs,
    instantLimitCoinsH: use.instantLimitH,
    batch: chosen.B,
    batchOptions,
    unmet: unmet(reqs, p),
    explain
  };
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/calc/routes.js
function walkBook(levels, units, fallback) {
  if (!levels?.length || units <= 0)
    return fallback;
  let left = units, cost = 0;
  for (const l of levels) {
    const take = Math.min(left, l.amount);
    cost += take * l.price;
    left -= take;
    if (left <= 0)
      break;
  }
  if (left > 0)
    cost += left * levels[levels.length - 1].price;
  return cost / units;
}
var instantBatch = (ctx, qty, flowH) => Math.max(qty, Math.min(BAZAAR.maxInstantBuyUnits, flowH * ctx.settings.checkIntervalMin / 60));
var models = /* @__PURE__ */ new WeakMap();
function modelFor(ctx, m, side) {
  let byKey = models.get(m);
  if (!byKey)
    models.set(m, byKey = /* @__PURE__ */ new Map());
  const k = `${side}|${ctx.settings.checkIntervalMin}|${ctx.settings.unknownCompetitionShare}`;
  let f = byKey.get(k);
  if (!f)
    byKey.set(k, f = side === "bid" ? fillModel(m.holdBid, buyFlowH(m), m.undercutBuyH, ctx.settings.checkIntervalMin, ctx.settings.unknownCompetitionShare) : fillModel(m.holdAsk, sellFlowH(m), m.undercutSellH, ctx.settings.checkIntervalMin, ctx.settings.unknownCompetitionShare));
  return f;
}
var maxOrderQty = (price, sell) => Math.max(1, Math.min(BAZAAR.maxUnitsPerOrder, sell ? Math.floor(BAZAAR.maxSellOfferValue / Math.max(price, 0.1)) : BAZAAR.maxUnitsPerOrder));
function buyLeg(ctx, m, qty, mode) {
  if (mode === "instant") {
    const flow = sellFlowH(m);
    return { item: m.id, name: m.name, qty, mode, price: walkBook(m.topAsk, instantBatch(ctx, qty, flow), m.ask), flowH: flow, share: null, undercutsH: null };
  }
  const fill = modelFor(ctx, m, "bid"), maxQty = maxOrderQty(m.bid + 0.1, false);
  const top = at(curve(fill, ctx.settings.checkIntervalMin), maxQty);
  return { item: m.id, name: m.name, qty, mode, price: m.bid + 0.1, flowH: top.unitsH, share: top.onTop, undercutsH: m.undercutBuyH, fill, maxQty };
}
function sellLeg(ctx, m, mode) {
  const tax = taxRate(ctx.settings.bazaarFlipperLevel);
  if (mode === "instant") {
    const flow = buyFlowH(m), now2 = walkBook(m.topBid, instantBatch(ctx, 1, flow), m.bid), tb = typicalPrice(m, "bid");
    const gross2 = tb && TYPICAL_BAND * tb.price < now2 ? TYPICAL_BAND * tb.price : now2;
    return {
      item: m.id,
      name: m.name,
      mode,
      grossPrice: gross2,
      netPrice: gross2 * (1 - tax),
      flowH: flow,
      share: null,
      undercutsH: null,
      ...gross2 < now2 ? { currentPrice: now2, priceBasis: `typical buy order + 10% (${tb.basis}, ${tb.hours} h of history)` } : {}
    };
  }
  const now = m.ask - 0.1, ta = typicalPrice(m, "ask"), ceil = bookCeiling(ctx.market, m.id);
  let gross = ta && TYPICAL_BAND * ta.price - 0.1 < now ? TYPICAL_BAND * ta.price - 0.1 : now;
  let basis = ta && gross < now ? `typical sell offer + 10% (${ta.basis}, ${ta.hours} h of history), minus 0.1` : "";
  if (ceil && ceil.price - 0.1 < gross) {
    gross = ceil.price - 0.1;
    basis = `price of ${ctx.market.get(ceil.item)?.name ?? ceil.item} (a higher level costs less), minus 0.1`;
  }
  const fill = modelFor(ctx, m, "ask"), maxQty = maxOrderQty(gross, true);
  const top = at(curve(fill, ctx.settings.checkIntervalMin), maxQty);
  return {
    item: m.id,
    name: m.name,
    mode,
    grossPrice: gross,
    netPrice: gross * (1 - tax),
    flowH: top.unitsH,
    share: top.onTop,
    undercutsH: m.undercutSellH,
    fill,
    maxQty,
    ...gross < now ? { currentPrice: now, priceBasis: basis } : {}
  };
}

// ../../tmp/bazaar-calc-review/packages/shared/dist/data/hypixel.js
function validateBazaar(d, now = Date.now()) {
  const b = d;
  if (!b || b.success !== true)
    return "success must be true";
  if (typeof b.lastUpdated !== "number")
    return "lastUpdated missing";
  if (b.lastUpdated > now + 12e4)
    return "lastUpdated is in the future";
  if (b.lastUpdated < now - 30 * 864e5)
    return "lastUpdated older than 30 days";
  const products = Object.values(b.products ?? {});
  if (products.length < 500)
    return `only ${products.length} products`;
  for (const p of products.slice(0, 50)) {
    if (!p.quick_status || !Array.isArray(p.sell_summary) || !Array.isArray(p.buy_summary))
      return `malformed product ${p.product_id}`;
  }
  return null;
}
function degradedBazaar(d, prevCount) {
  const products = Object.values(d.products);
  if (prevCount && products.length < 0.95 * prevCount)
    return `only ${products.length} products (last poll had ${prevCount})`;
  const zeroVolume = products.filter((p) => !p.quick_status?.buyMovingWeek && !p.quick_status?.sellMovingWeek).length;
  if (zeroVolume > 0.5 * products.length)
    return `${zeroVolume} of ${products.length} products report no weekly volume`;
  return null;
}
function bookFlow(prevBids, prevAsks, bids, asks) {
  const bestBid = bids[0]?.pricePerUnit ?? 0, bestAsk = asks[0]?.pricePerUnit ?? Infinity;
  const nowBid = new Map(bids.map((o) => [Math.round(o.pricePerUnit * 100), o.amount]));
  const nowAsk = new Map(asks.map((o) => [Math.round(o.pricePerUnit * 100), o.amount]));
  let bidRemoved = 0, askRemoved = 0;
  for (const l of prevBids)
    if (l.price >= bestBid)
      bidRemoved += Math.max(0, l.amount - (nowBid.get(Math.round(l.price * 100)) ?? 0));
  for (const l of prevAsks)
    if (l.price <= bestAsk)
      askRemoved += Math.max(0, l.amount - (nowAsk.get(Math.round(l.price * 100)) ?? 0));
  return {
    bidRemoved,
    askRemoved,
    outbid: prevBids[0] != null && bestBid > prevBids[0].price + 1e-9,
    undercut: prevAsks[0] != null && bestAsk < prevAsks[0].price - 1e-9
  };
}
var toLevels = (o) => o.map((x) => ({ price: Math.round(x.pricePerUnit * 100) / 100, amount: x.amount, orders: x.orders }));

// ../../tmp/bazaar-calc-review/packages/shared/dist/data/nbt.js
var Reader = class _Reader {
  b;
  v;
  o = 0;
  static td = new TextDecoder("utf-8");
  constructor(b) {
    this.b = b;
    this.v = new DataView(b.buffer, b.byteOffset, b.byteLength);
  }
  u8() {
    return this.v.getUint8(this.o++);
  }
  i8() {
    return this.v.getInt8(this.o++);
  }
  i16() {
    const x = this.v.getInt16(this.o);
    this.o += 2;
    return x;
  }
  u16() {
    const x = this.v.getUint16(this.o);
    this.o += 2;
    return x;
  }
  i32() {
    const x = this.v.getInt32(this.o);
    this.o += 4;
    return x;
  }
  i64() {
    const x = this.v.getBigInt64(this.o);
    this.o += 8;
    return x;
  }
  f32() {
    const x = this.v.getFloat32(this.o);
    this.o += 4;
    return x;
  }
  f64() {
    const x = this.v.getFloat64(this.o);
    this.o += 8;
    return x;
  }
  str() {
    const n = this.u16();
    const s = _Reader.td.decode(this.b.subarray(this.o, this.o + n));
    this.o += n;
    return s;
  }
  payload(t, depth) {
    if (depth > 64)
      throw new Error("NBT nested too deep");
    switch (t) {
      case 1:
        return this.i8();
      case 2:
        return this.i16();
      case 3:
        return this.i32();
      case 4:
        return this.i64();
      case 5:
        return this.f32();
      case 6:
        return this.f64();
      case 7: {
        const n = this.i32();
        const a = new Int8Array(this.b.buffer.slice(this.b.byteOffset + this.o, this.b.byteOffset + this.o + n));
        this.o += n;
        return a;
      }
      case 8:
        return this.str();
      case 9: {
        const et = this.u8(), n = this.i32(), out = [];
        for (let i = 0; i < n; i++)
          out.push(this.payload(et, depth + 1));
        return out;
      }
      case 10: {
        const out = {};
        for (; ; ) {
          const ct = this.u8();
          if (ct === 0)
            break;
          const name = this.str();
          out[name] = this.payload(ct, depth + 1);
        }
        return out;
      }
      case 11: {
        const n = this.i32(), a = new Int32Array(n);
        for (let i = 0; i < n; i++)
          a[i] = this.i32();
        return a;
      }
      case 12: {
        const n = this.i32(), a = new BigInt64Array(n);
        for (let i = 0; i < n; i++)
          a[i] = this.i64();
        return a;
      }
      default:
        throw new Error(`unknown NBT tag ${t}`);
    }
  }
};

// ../../tmp/bazaar-calc-review/node_modules/.pnpm/zod@3.25.76/node_modules/zod/v3/external.js
var external_exports = {};
__export(external_exports, {
  BRAND: () => BRAND,
  DIRTY: () => DIRTY,
  EMPTY_PATH: () => EMPTY_PATH,
  INVALID: () => INVALID,
  NEVER: () => NEVER,
  OK: () => OK,
  ParseStatus: () => ParseStatus,
  Schema: () => ZodType,
  ZodAny: () => ZodAny,
  ZodArray: () => ZodArray,
  ZodBigInt: () => ZodBigInt,
  ZodBoolean: () => ZodBoolean,
  ZodBranded: () => ZodBranded,
  ZodCatch: () => ZodCatch,
  ZodDate: () => ZodDate,
  ZodDefault: () => ZodDefault,
  ZodDiscriminatedUnion: () => ZodDiscriminatedUnion,
  ZodEffects: () => ZodEffects,
  ZodEnum: () => ZodEnum,
  ZodError: () => ZodError,
  ZodFirstPartyTypeKind: () => ZodFirstPartyTypeKind,
  ZodFunction: () => ZodFunction,
  ZodIntersection: () => ZodIntersection,
  ZodIssueCode: () => ZodIssueCode,
  ZodLazy: () => ZodLazy,
  ZodLiteral: () => ZodLiteral,
  ZodMap: () => ZodMap,
  ZodNaN: () => ZodNaN,
  ZodNativeEnum: () => ZodNativeEnum,
  ZodNever: () => ZodNever,
  ZodNull: () => ZodNull,
  ZodNullable: () => ZodNullable,
  ZodNumber: () => ZodNumber,
  ZodObject: () => ZodObject,
  ZodOptional: () => ZodOptional,
  ZodParsedType: () => ZodParsedType,
  ZodPipeline: () => ZodPipeline,
  ZodPromise: () => ZodPromise,
  ZodReadonly: () => ZodReadonly,
  ZodRecord: () => ZodRecord,
  ZodSchema: () => ZodType,
  ZodSet: () => ZodSet,
  ZodString: () => ZodString,
  ZodSymbol: () => ZodSymbol,
  ZodTransformer: () => ZodEffects,
  ZodTuple: () => ZodTuple,
  ZodType: () => ZodType,
  ZodUndefined: () => ZodUndefined,
  ZodUnion: () => ZodUnion,
  ZodUnknown: () => ZodUnknown,
  ZodVoid: () => ZodVoid,
  addIssueToContext: () => addIssueToContext,
  any: () => anyType,
  array: () => arrayType,
  bigint: () => bigIntType,
  boolean: () => booleanType,
  coerce: () => coerce,
  custom: () => custom,
  date: () => dateType,
  datetimeRegex: () => datetimeRegex,
  defaultErrorMap: () => en_default,
  discriminatedUnion: () => discriminatedUnionType,
  effect: () => effectsType,
  enum: () => enumType,
  function: () => functionType,
  getErrorMap: () => getErrorMap,
  getParsedType: () => getParsedType,
  instanceof: () => instanceOfType,
  intersection: () => intersectionType,
  isAborted: () => isAborted,
  isAsync: () => isAsync,
  isDirty: () => isDirty,
  isValid: () => isValid,
  late: () => late,
  lazy: () => lazyType,
  literal: () => literalType,
  makeIssue: () => makeIssue,
  map: () => mapType,
  nan: () => nanType,
  nativeEnum: () => nativeEnumType,
  never: () => neverType,
  null: () => nullType,
  nullable: () => nullableType,
  number: () => numberType,
  object: () => objectType,
  objectUtil: () => objectUtil,
  oboolean: () => oboolean,
  onumber: () => onumber,
  optional: () => optionalType,
  ostring: () => ostring,
  pipeline: () => pipelineType,
  preprocess: () => preprocessType,
  promise: () => promiseType,
  quotelessJson: () => quotelessJson,
  record: () => recordType,
  set: () => setType,
  setErrorMap: () => setErrorMap,
  strictObject: () => strictObjectType,
  string: () => stringType,
  symbol: () => symbolType,
  transformer: () => effectsType,
  tuple: () => tupleType,
  undefined: () => undefinedType,
  union: () => unionType,
  unknown: () => unknownType,
  util: () => util,
  void: () => voidType
});

// ../../tmp/bazaar-calc-review/node_modules/.pnpm/zod@3.25.76/node_modules/zod/v3/helpers/util.js
var util;
(function(util2) {
  util2.assertEqual = (_) => {
  };
  function assertIs(_arg) {
  }
  util2.assertIs = assertIs;
  function assertNever(_x) {
    throw new Error();
  }
  util2.assertNever = assertNever;
  util2.arrayToEnum = (items) => {
    const obj = {};
    for (const item of items) {
      obj[item] = item;
    }
    return obj;
  };
  util2.getValidEnumValues = (obj) => {
    const validKeys = util2.objectKeys(obj).filter((k) => typeof obj[obj[k]] !== "number");
    const filtered = {};
    for (const k of validKeys) {
      filtered[k] = obj[k];
    }
    return util2.objectValues(filtered);
  };
  util2.objectValues = (obj) => {
    return util2.objectKeys(obj).map(function(e) {
      return obj[e];
    });
  };
  util2.objectKeys = typeof Object.keys === "function" ? (obj) => Object.keys(obj) : (object) => {
    const keys = [];
    for (const key2 in object) {
      if (Object.prototype.hasOwnProperty.call(object, key2)) {
        keys.push(key2);
      }
    }
    return keys;
  };
  util2.find = (arr, checker) => {
    for (const item of arr) {
      if (checker(item))
        return item;
    }
    return void 0;
  };
  util2.isInteger = typeof Number.isInteger === "function" ? (val) => Number.isInteger(val) : (val) => typeof val === "number" && Number.isFinite(val) && Math.floor(val) === val;
  function joinValues(array, separator = " | ") {
    return array.map((val) => typeof val === "string" ? `'${val}'` : val).join(separator);
  }
  util2.joinValues = joinValues;
  util2.jsonStringifyReplacer = (_, value) => {
    if (typeof value === "bigint") {
      return value.toString();
    }
    return value;
  };
})(util || (util = {}));
var objectUtil;
(function(objectUtil2) {
  objectUtil2.mergeShapes = (first, second) => {
    return {
      ...first,
      ...second
      // second overwrites first
    };
  };
})(objectUtil || (objectUtil = {}));
var ZodParsedType = util.arrayToEnum([
  "string",
  "nan",
  "number",
  "integer",
  "float",
  "boolean",
  "date",
  "bigint",
  "symbol",
  "function",
  "undefined",
  "null",
  "array",
  "object",
  "unknown",
  "promise",
  "void",
  "never",
  "map",
  "set"
]);
var getParsedType = (data) => {
  const t = typeof data;
  switch (t) {
    case "undefined":
      return ZodParsedType.undefined;
    case "string":
      return ZodParsedType.string;
    case "number":
      return Number.isNaN(data) ? ZodParsedType.nan : ZodParsedType.number;
    case "boolean":
      return ZodParsedType.boolean;
    case "function":
      return ZodParsedType.function;
    case "bigint":
      return ZodParsedType.bigint;
    case "symbol":
      return ZodParsedType.symbol;
    case "object":
      if (Array.isArray(data)) {
        return ZodParsedType.array;
      }
      if (data === null) {
        return ZodParsedType.null;
      }
      if (data.then && typeof data.then === "function" && data.catch && typeof data.catch === "function") {
        return ZodParsedType.promise;
      }
      if (typeof Map !== "undefined" && data instanceof Map) {
        return ZodParsedType.map;
      }
      if (typeof Set !== "undefined" && data instanceof Set) {
        return ZodParsedType.set;
      }
      if (typeof Date !== "undefined" && data instanceof Date) {
        return ZodParsedType.date;
      }
      return ZodParsedType.object;
    default:
      return ZodParsedType.unknown;
  }
};

// ../../tmp/bazaar-calc-review/node_modules/.pnpm/zod@3.25.76/node_modules/zod/v3/ZodError.js
var ZodIssueCode = util.arrayToEnum([
  "invalid_type",
  "invalid_literal",
  "custom",
  "invalid_union",
  "invalid_union_discriminator",
  "invalid_enum_value",
  "unrecognized_keys",
  "invalid_arguments",
  "invalid_return_type",
  "invalid_date",
  "invalid_string",
  "too_small",
  "too_big",
  "invalid_intersection_types",
  "not_multiple_of",
  "not_finite"
]);
var quotelessJson = (obj) => {
  const json = JSON.stringify(obj, null, 2);
  return json.replace(/"([^"]+)":/g, "$1:");
};
var ZodError = class _ZodError extends Error {
  get errors() {
    return this.issues;
  }
  constructor(issues) {
    super();
    this.issues = [];
    this.addIssue = (sub) => {
      this.issues = [...this.issues, sub];
    };
    this.addIssues = (subs = []) => {
      this.issues = [...this.issues, ...subs];
    };
    const actualProto = new.target.prototype;
    if (Object.setPrototypeOf) {
      Object.setPrototypeOf(this, actualProto);
    } else {
      this.__proto__ = actualProto;
    }
    this.name = "ZodError";
    this.issues = issues;
  }
  format(_mapper) {
    const mapper = _mapper || function(issue) {
      return issue.message;
    };
    const fieldErrors = { _errors: [] };
    const processError = (error) => {
      for (const issue of error.issues) {
        if (issue.code === "invalid_union") {
          issue.unionErrors.map(processError);
        } else if (issue.code === "invalid_return_type") {
          processError(issue.returnTypeError);
        } else if (issue.code === "invalid_arguments") {
          processError(issue.argumentsError);
        } else if (issue.path.length === 0) {
          fieldErrors._errors.push(mapper(issue));
        } else {
          let curr = fieldErrors;
          let i = 0;
          while (i < issue.path.length) {
            const el = issue.path[i];
            const terminal = i === issue.path.length - 1;
            if (!terminal) {
              curr[el] = curr[el] || { _errors: [] };
            } else {
              curr[el] = curr[el] || { _errors: [] };
              curr[el]._errors.push(mapper(issue));
            }
            curr = curr[el];
            i++;
          }
        }
      }
    };
    processError(this);
    return fieldErrors;
  }
  static assert(value) {
    if (!(value instanceof _ZodError)) {
      throw new Error(`Not a ZodError: ${value}`);
    }
  }
  toString() {
    return this.message;
  }
  get message() {
    return JSON.stringify(this.issues, util.jsonStringifyReplacer, 2);
  }
  get isEmpty() {
    return this.issues.length === 0;
  }
  flatten(mapper = (issue) => issue.message) {
    const fieldErrors = {};
    const formErrors = [];
    for (const sub of this.issues) {
      if (sub.path.length > 0) {
        const firstEl = sub.path[0];
        fieldErrors[firstEl] = fieldErrors[firstEl] || [];
        fieldErrors[firstEl].push(mapper(sub));
      } else {
        formErrors.push(mapper(sub));
      }
    }
    return { formErrors, fieldErrors };
  }
  get formErrors() {
    return this.flatten();
  }
};
ZodError.create = (issues) => {
  const error = new ZodError(issues);
  return error;
};

// ../../tmp/bazaar-calc-review/node_modules/.pnpm/zod@3.25.76/node_modules/zod/v3/locales/en.js
var errorMap = (issue, _ctx) => {
  let message;
  switch (issue.code) {
    case ZodIssueCode.invalid_type:
      if (issue.received === ZodParsedType.undefined) {
        message = "Required";
      } else {
        message = `Expected ${issue.expected}, received ${issue.received}`;
      }
      break;
    case ZodIssueCode.invalid_literal:
      message = `Invalid literal value, expected ${JSON.stringify(issue.expected, util.jsonStringifyReplacer)}`;
      break;
    case ZodIssueCode.unrecognized_keys:
      message = `Unrecognized key(s) in object: ${util.joinValues(issue.keys, ", ")}`;
      break;
    case ZodIssueCode.invalid_union:
      message = `Invalid input`;
      break;
    case ZodIssueCode.invalid_union_discriminator:
      message = `Invalid discriminator value. Expected ${util.joinValues(issue.options)}`;
      break;
    case ZodIssueCode.invalid_enum_value:
      message = `Invalid enum value. Expected ${util.joinValues(issue.options)}, received '${issue.received}'`;
      break;
    case ZodIssueCode.invalid_arguments:
      message = `Invalid function arguments`;
      break;
    case ZodIssueCode.invalid_return_type:
      message = `Invalid function return type`;
      break;
    case ZodIssueCode.invalid_date:
      message = `Invalid date`;
      break;
    case ZodIssueCode.invalid_string:
      if (typeof issue.validation === "object") {
        if ("includes" in issue.validation) {
          message = `Invalid input: must include "${issue.validation.includes}"`;
          if (typeof issue.validation.position === "number") {
            message = `${message} at one or more positions greater than or equal to ${issue.validation.position}`;
          }
        } else if ("startsWith" in issue.validation) {
          message = `Invalid input: must start with "${issue.validation.startsWith}"`;
        } else if ("endsWith" in issue.validation) {
          message = `Invalid input: must end with "${issue.validation.endsWith}"`;
        } else {
          util.assertNever(issue.validation);
        }
      } else if (issue.validation !== "regex") {
        message = `Invalid ${issue.validation}`;
      } else {
        message = "Invalid";
      }
      break;
    case ZodIssueCode.too_small:
      if (issue.type === "array")
        message = `Array must contain ${issue.exact ? "exactly" : issue.inclusive ? `at least` : `more than`} ${issue.minimum} element(s)`;
      else if (issue.type === "string")
        message = `String must contain ${issue.exact ? "exactly" : issue.inclusive ? `at least` : `over`} ${issue.minimum} character(s)`;
      else if (issue.type === "number")
        message = `Number must be ${issue.exact ? `exactly equal to ` : issue.inclusive ? `greater than or equal to ` : `greater than `}${issue.minimum}`;
      else if (issue.type === "bigint")
        message = `Number must be ${issue.exact ? `exactly equal to ` : issue.inclusive ? `greater than or equal to ` : `greater than `}${issue.minimum}`;
      else if (issue.type === "date")
        message = `Date must be ${issue.exact ? `exactly equal to ` : issue.inclusive ? `greater than or equal to ` : `greater than `}${new Date(Number(issue.minimum))}`;
      else
        message = "Invalid input";
      break;
    case ZodIssueCode.too_big:
      if (issue.type === "array")
        message = `Array must contain ${issue.exact ? `exactly` : issue.inclusive ? `at most` : `less than`} ${issue.maximum} element(s)`;
      else if (issue.type === "string")
        message = `String must contain ${issue.exact ? `exactly` : issue.inclusive ? `at most` : `under`} ${issue.maximum} character(s)`;
      else if (issue.type === "number")
        message = `Number must be ${issue.exact ? `exactly` : issue.inclusive ? `less than or equal to` : `less than`} ${issue.maximum}`;
      else if (issue.type === "bigint")
        message = `BigInt must be ${issue.exact ? `exactly` : issue.inclusive ? `less than or equal to` : `less than`} ${issue.maximum}`;
      else if (issue.type === "date")
        message = `Date must be ${issue.exact ? `exactly` : issue.inclusive ? `smaller than or equal to` : `smaller than`} ${new Date(Number(issue.maximum))}`;
      else
        message = "Invalid input";
      break;
    case ZodIssueCode.custom:
      message = `Invalid input`;
      break;
    case ZodIssueCode.invalid_intersection_types:
      message = `Intersection results could not be merged`;
      break;
    case ZodIssueCode.not_multiple_of:
      message = `Number must be a multiple of ${issue.multipleOf}`;
      break;
    case ZodIssueCode.not_finite:
      message = "Number must be finite";
      break;
    default:
      message = _ctx.defaultError;
      util.assertNever(issue);
  }
  return { message };
};
var en_default = errorMap;

// ../../tmp/bazaar-calc-review/node_modules/.pnpm/zod@3.25.76/node_modules/zod/v3/errors.js
var overrideErrorMap = en_default;
function setErrorMap(map) {
  overrideErrorMap = map;
}
function getErrorMap() {
  return overrideErrorMap;
}

// ../../tmp/bazaar-calc-review/node_modules/.pnpm/zod@3.25.76/node_modules/zod/v3/helpers/parseUtil.js
var makeIssue = (params) => {
  const { data, path, errorMaps, issueData } = params;
  const fullPath = [...path, ...issueData.path || []];
  const fullIssue = {
    ...issueData,
    path: fullPath
  };
  if (issueData.message !== void 0) {
    return {
      ...issueData,
      path: fullPath,
      message: issueData.message
    };
  }
  let errorMessage = "";
  const maps = errorMaps.filter((m) => !!m).slice().reverse();
  for (const map of maps) {
    errorMessage = map(fullIssue, { data, defaultError: errorMessage }).message;
  }
  return {
    ...issueData,
    path: fullPath,
    message: errorMessage
  };
};
var EMPTY_PATH = [];
function addIssueToContext(ctx, issueData) {
  const overrideMap = getErrorMap();
  const issue = makeIssue({
    issueData,
    data: ctx.data,
    path: ctx.path,
    errorMaps: [
      ctx.common.contextualErrorMap,
      // contextual error map is first priority
      ctx.schemaErrorMap,
      // then schema-bound map if available
      overrideMap,
      // then global override map
      overrideMap === en_default ? void 0 : en_default
      // then global default map
    ].filter((x) => !!x)
  });
  ctx.common.issues.push(issue);
}
var ParseStatus = class _ParseStatus {
  constructor() {
    this.value = "valid";
  }
  dirty() {
    if (this.value === "valid")
      this.value = "dirty";
  }
  abort() {
    if (this.value !== "aborted")
      this.value = "aborted";
  }
  static mergeArray(status, results) {
    const arrayValue = [];
    for (const s of results) {
      if (s.status === "aborted")
        return INVALID;
      if (s.status === "dirty")
        status.dirty();
      arrayValue.push(s.value);
    }
    return { status: status.value, value: arrayValue };
  }
  static async mergeObjectAsync(status, pairs) {
    const syncPairs = [];
    for (const pair of pairs) {
      const key2 = await pair.key;
      const value = await pair.value;
      syncPairs.push({
        key: key2,
        value
      });
    }
    return _ParseStatus.mergeObjectSync(status, syncPairs);
  }
  static mergeObjectSync(status, pairs) {
    const finalObject = {};
    for (const pair of pairs) {
      const { key: key2, value } = pair;
      if (key2.status === "aborted")
        return INVALID;
      if (value.status === "aborted")
        return INVALID;
      if (key2.status === "dirty")
        status.dirty();
      if (value.status === "dirty")
        status.dirty();
      if (key2.value !== "__proto__" && (typeof value.value !== "undefined" || pair.alwaysSet)) {
        finalObject[key2.value] = value.value;
      }
    }
    return { status: status.value, value: finalObject };
  }
};
var INVALID = Object.freeze({
  status: "aborted"
});
var DIRTY = (value) => ({ status: "dirty", value });
var OK = (value) => ({ status: "valid", value });
var isAborted = (x) => x.status === "aborted";
var isDirty = (x) => x.status === "dirty";
var isValid = (x) => x.status === "valid";
var isAsync = (x) => typeof Promise !== "undefined" && x instanceof Promise;

// ../../tmp/bazaar-calc-review/node_modules/.pnpm/zod@3.25.76/node_modules/zod/v3/helpers/errorUtil.js
var errorUtil;
(function(errorUtil2) {
  errorUtil2.errToObj = (message) => typeof message === "string" ? { message } : message || {};
  errorUtil2.toString = (message) => typeof message === "string" ? message : message?.message;
})(errorUtil || (errorUtil = {}));

// ../../tmp/bazaar-calc-review/node_modules/.pnpm/zod@3.25.76/node_modules/zod/v3/types.js
var ParseInputLazyPath = class {
  constructor(parent, value, path, key2) {
    this._cachedPath = [];
    this.parent = parent;
    this.data = value;
    this._path = path;
    this._key = key2;
  }
  get path() {
    if (!this._cachedPath.length) {
      if (Array.isArray(this._key)) {
        this._cachedPath.push(...this._path, ...this._key);
      } else {
        this._cachedPath.push(...this._path, this._key);
      }
    }
    return this._cachedPath;
  }
};
var handleResult = (ctx, result) => {
  if (isValid(result)) {
    return { success: true, data: result.value };
  } else {
    if (!ctx.common.issues.length) {
      throw new Error("Validation failed but no issues detected.");
    }
    return {
      success: false,
      get error() {
        if (this._error)
          return this._error;
        const error = new ZodError(ctx.common.issues);
        this._error = error;
        return this._error;
      }
    };
  }
};
function processCreateParams(params) {
  if (!params)
    return {};
  const { errorMap: errorMap2, invalid_type_error, required_error, description } = params;
  if (errorMap2 && (invalid_type_error || required_error)) {
    throw new Error(`Can't use "invalid_type_error" or "required_error" in conjunction with custom error map.`);
  }
  if (errorMap2)
    return { errorMap: errorMap2, description };
  const customMap = (iss, ctx) => {
    const { message } = params;
    if (iss.code === "invalid_enum_value") {
      return { message: message ?? ctx.defaultError };
    }
    if (typeof ctx.data === "undefined") {
      return { message: message ?? required_error ?? ctx.defaultError };
    }
    if (iss.code !== "invalid_type")
      return { message: ctx.defaultError };
    return { message: message ?? invalid_type_error ?? ctx.defaultError };
  };
  return { errorMap: customMap, description };
}
var ZodType = class {
  get description() {
    return this._def.description;
  }
  _getType(input) {
    return getParsedType(input.data);
  }
  _getOrReturnCtx(input, ctx) {
    return ctx || {
      common: input.parent.common,
      data: input.data,
      parsedType: getParsedType(input.data),
      schemaErrorMap: this._def.errorMap,
      path: input.path,
      parent: input.parent
    };
  }
  _processInputParams(input) {
    return {
      status: new ParseStatus(),
      ctx: {
        common: input.parent.common,
        data: input.data,
        parsedType: getParsedType(input.data),
        schemaErrorMap: this._def.errorMap,
        path: input.path,
        parent: input.parent
      }
    };
  }
  _parseSync(input) {
    const result = this._parse(input);
    if (isAsync(result)) {
      throw new Error("Synchronous parse encountered promise.");
    }
    return result;
  }
  _parseAsync(input) {
    const result = this._parse(input);
    return Promise.resolve(result);
  }
  parse(data, params) {
    const result = this.safeParse(data, params);
    if (result.success)
      return result.data;
    throw result.error;
  }
  safeParse(data, params) {
    const ctx = {
      common: {
        issues: [],
        async: params?.async ?? false,
        contextualErrorMap: params?.errorMap
      },
      path: params?.path || [],
      schemaErrorMap: this._def.errorMap,
      parent: null,
      data,
      parsedType: getParsedType(data)
    };
    const result = this._parseSync({ data, path: ctx.path, parent: ctx });
    return handleResult(ctx, result);
  }
  "~validate"(data) {
    const ctx = {
      common: {
        issues: [],
        async: !!this["~standard"].async
      },
      path: [],
      schemaErrorMap: this._def.errorMap,
      parent: null,
      data,
      parsedType: getParsedType(data)
    };
    if (!this["~standard"].async) {
      try {
        const result = this._parseSync({ data, path: [], parent: ctx });
        return isValid(result) ? {
          value: result.value
        } : {
          issues: ctx.common.issues
        };
      } catch (err) {
        if (err?.message?.toLowerCase()?.includes("encountered")) {
          this["~standard"].async = true;
        }
        ctx.common = {
          issues: [],
          async: true
        };
      }
    }
    return this._parseAsync({ data, path: [], parent: ctx }).then((result) => isValid(result) ? {
      value: result.value
    } : {
      issues: ctx.common.issues
    });
  }
  async parseAsync(data, params) {
    const result = await this.safeParseAsync(data, params);
    if (result.success)
      return result.data;
    throw result.error;
  }
  async safeParseAsync(data, params) {
    const ctx = {
      common: {
        issues: [],
        contextualErrorMap: params?.errorMap,
        async: true
      },
      path: params?.path || [],
      schemaErrorMap: this._def.errorMap,
      parent: null,
      data,
      parsedType: getParsedType(data)
    };
    const maybeAsyncResult = this._parse({ data, path: ctx.path, parent: ctx });
    const result = await (isAsync(maybeAsyncResult) ? maybeAsyncResult : Promise.resolve(maybeAsyncResult));
    return handleResult(ctx, result);
  }
  refine(check, message) {
    const getIssueProperties = (val) => {
      if (typeof message === "string" || typeof message === "undefined") {
        return { message };
      } else if (typeof message === "function") {
        return message(val);
      } else {
        return message;
      }
    };
    return this._refinement((val, ctx) => {
      const result = check(val);
      const setError = () => ctx.addIssue({
        code: ZodIssueCode.custom,
        ...getIssueProperties(val)
      });
      if (typeof Promise !== "undefined" && result instanceof Promise) {
        return result.then((data) => {
          if (!data) {
            setError();
            return false;
          } else {
            return true;
          }
        });
      }
      if (!result) {
        setError();
        return false;
      } else {
        return true;
      }
    });
  }
  refinement(check, refinementData) {
    return this._refinement((val, ctx) => {
      if (!check(val)) {
        ctx.addIssue(typeof refinementData === "function" ? refinementData(val, ctx) : refinementData);
        return false;
      } else {
        return true;
      }
    });
  }
  _refinement(refinement) {
    return new ZodEffects({
      schema: this,
      typeName: ZodFirstPartyTypeKind.ZodEffects,
      effect: { type: "refinement", refinement }
    });
  }
  superRefine(refinement) {
    return this._refinement(refinement);
  }
  constructor(def) {
    this.spa = this.safeParseAsync;
    this._def = def;
    this.parse = this.parse.bind(this);
    this.safeParse = this.safeParse.bind(this);
    this.parseAsync = this.parseAsync.bind(this);
    this.safeParseAsync = this.safeParseAsync.bind(this);
    this.spa = this.spa.bind(this);
    this.refine = this.refine.bind(this);
    this.refinement = this.refinement.bind(this);
    this.superRefine = this.superRefine.bind(this);
    this.optional = this.optional.bind(this);
    this.nullable = this.nullable.bind(this);
    this.nullish = this.nullish.bind(this);
    this.array = this.array.bind(this);
    this.promise = this.promise.bind(this);
    this.or = this.or.bind(this);
    this.and = this.and.bind(this);
    this.transform = this.transform.bind(this);
    this.brand = this.brand.bind(this);
    this.default = this.default.bind(this);
    this.catch = this.catch.bind(this);
    this.describe = this.describe.bind(this);
    this.pipe = this.pipe.bind(this);
    this.readonly = this.readonly.bind(this);
    this.isNullable = this.isNullable.bind(this);
    this.isOptional = this.isOptional.bind(this);
    this["~standard"] = {
      version: 1,
      vendor: "zod",
      validate: (data) => this["~validate"](data)
    };
  }
  optional() {
    return ZodOptional.create(this, this._def);
  }
  nullable() {
    return ZodNullable.create(this, this._def);
  }
  nullish() {
    return this.nullable().optional();
  }
  array() {
    return ZodArray.create(this);
  }
  promise() {
    return ZodPromise.create(this, this._def);
  }
  or(option) {
    return ZodUnion.create([this, option], this._def);
  }
  and(incoming) {
    return ZodIntersection.create(this, incoming, this._def);
  }
  transform(transform) {
    return new ZodEffects({
      ...processCreateParams(this._def),
      schema: this,
      typeName: ZodFirstPartyTypeKind.ZodEffects,
      effect: { type: "transform", transform }
    });
  }
  default(def) {
    const defaultValueFunc = typeof def === "function" ? def : () => def;
    return new ZodDefault({
      ...processCreateParams(this._def),
      innerType: this,
      defaultValue: defaultValueFunc,
      typeName: ZodFirstPartyTypeKind.ZodDefault
    });
  }
  brand() {
    return new ZodBranded({
      typeName: ZodFirstPartyTypeKind.ZodBranded,
      type: this,
      ...processCreateParams(this._def)
    });
  }
  catch(def) {
    const catchValueFunc = typeof def === "function" ? def : () => def;
    return new ZodCatch({
      ...processCreateParams(this._def),
      innerType: this,
      catchValue: catchValueFunc,
      typeName: ZodFirstPartyTypeKind.ZodCatch
    });
  }
  describe(description) {
    const This = this.constructor;
    return new This({
      ...this._def,
      description
    });
  }
  pipe(target) {
    return ZodPipeline.create(this, target);
  }
  readonly() {
    return ZodReadonly.create(this);
  }
  isOptional() {
    return this.safeParse(void 0).success;
  }
  isNullable() {
    return this.safeParse(null).success;
  }
};
var cuidRegex = /^c[^\s-]{8,}$/i;
var cuid2Regex = /^[0-9a-z]+$/;
var ulidRegex = /^[0-9A-HJKMNP-TV-Z]{26}$/i;
var uuidRegex = /^[0-9a-fA-F]{8}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{12}$/i;
var nanoidRegex = /^[a-z0-9_-]{21}$/i;
var jwtRegex = /^[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_]*$/;
var durationRegex = /^[-+]?P(?!$)(?:(?:[-+]?\d+Y)|(?:[-+]?\d+[.,]\d+Y$))?(?:(?:[-+]?\d+M)|(?:[-+]?\d+[.,]\d+M$))?(?:(?:[-+]?\d+W)|(?:[-+]?\d+[.,]\d+W$))?(?:(?:[-+]?\d+D)|(?:[-+]?\d+[.,]\d+D$))?(?:T(?=[\d+-])(?:(?:[-+]?\d+H)|(?:[-+]?\d+[.,]\d+H$))?(?:(?:[-+]?\d+M)|(?:[-+]?\d+[.,]\d+M$))?(?:[-+]?\d+(?:[.,]\d+)?S)?)??$/;
var emailRegex = /^(?!\.)(?!.*\.\.)([A-Z0-9_'+\-\.]*)[A-Z0-9_+-]@([A-Z0-9][A-Z0-9\-]*\.)+[A-Z]{2,}$/i;
var _emojiRegex = `^(\\p{Extended_Pictographic}|\\p{Emoji_Component})+$`;
var emojiRegex;
var ipv4Regex = /^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])$/;
var ipv4CidrRegex = /^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\/(3[0-2]|[12]?[0-9])$/;
var ipv6Regex = /^(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$/;
var ipv6CidrRegex = /^(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))\/(12[0-8]|1[01][0-9]|[1-9]?[0-9])$/;
var base64Regex = /^([0-9a-zA-Z+/]{4})*(([0-9a-zA-Z+/]{2}==)|([0-9a-zA-Z+/]{3}=))?$/;
var base64urlRegex = /^([0-9a-zA-Z-_]{4})*(([0-9a-zA-Z-_]{2}(==)?)|([0-9a-zA-Z-_]{3}(=)?))?$/;
var dateRegexSource = `((\\d\\d[2468][048]|\\d\\d[13579][26]|\\d\\d0[48]|[02468][048]00|[13579][26]00)-02-29|\\d{4}-((0[13578]|1[02])-(0[1-9]|[12]\\d|3[01])|(0[469]|11)-(0[1-9]|[12]\\d|30)|(02)-(0[1-9]|1\\d|2[0-8])))`;
var dateRegex = new RegExp(`^${dateRegexSource}$`);
function timeRegexSource(args) {
  let secondsRegexSource = `[0-5]\\d`;
  if (args.precision) {
    secondsRegexSource = `${secondsRegexSource}\\.\\d{${args.precision}}`;
  } else if (args.precision == null) {
    secondsRegexSource = `${secondsRegexSource}(\\.\\d+)?`;
  }
  const secondsQuantifier = args.precision ? "+" : "?";
  return `([01]\\d|2[0-3]):[0-5]\\d(:${secondsRegexSource})${secondsQuantifier}`;
}
function timeRegex(args) {
  return new RegExp(`^${timeRegexSource(args)}$`);
}
function datetimeRegex(args) {
  let regex = `${dateRegexSource}T${timeRegexSource(args)}`;
  const opts = [];
  opts.push(args.local ? `Z?` : `Z`);
  if (args.offset)
    opts.push(`([+-]\\d{2}:?\\d{2})`);
  regex = `${regex}(${opts.join("|")})`;
  return new RegExp(`^${regex}$`);
}
function isValidIP(ip, version) {
  if ((version === "v4" || !version) && ipv4Regex.test(ip)) {
    return true;
  }
  if ((version === "v6" || !version) && ipv6Regex.test(ip)) {
    return true;
  }
  return false;
}
function isValidJWT(jwt, alg) {
  if (!jwtRegex.test(jwt))
    return false;
  try {
    const [header] = jwt.split(".");
    if (!header)
      return false;
    const base64 = header.replace(/-/g, "+").replace(/_/g, "/").padEnd(header.length + (4 - header.length % 4) % 4, "=");
    const decoded = JSON.parse(atob(base64));
    if (typeof decoded !== "object" || decoded === null)
      return false;
    if ("typ" in decoded && decoded?.typ !== "JWT")
      return false;
    if (!decoded.alg)
      return false;
    if (alg && decoded.alg !== alg)
      return false;
    return true;
  } catch {
    return false;
  }
}
function isValidCidr(ip, version) {
  if ((version === "v4" || !version) && ipv4CidrRegex.test(ip)) {
    return true;
  }
  if ((version === "v6" || !version) && ipv6CidrRegex.test(ip)) {
    return true;
  }
  return false;
}
var ZodString = class _ZodString extends ZodType {
  _parse(input) {
    if (this._def.coerce) {
      input.data = String(input.data);
    }
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.string) {
      const ctx2 = this._getOrReturnCtx(input);
      addIssueToContext(ctx2, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.string,
        received: ctx2.parsedType
      });
      return INVALID;
    }
    const status = new ParseStatus();
    let ctx = void 0;
    for (const check of this._def.checks) {
      if (check.kind === "min") {
        if (input.data.length < check.value) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_small,
            minimum: check.value,
            type: "string",
            inclusive: true,
            exact: false,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "max") {
        if (input.data.length > check.value) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_big,
            maximum: check.value,
            type: "string",
            inclusive: true,
            exact: false,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "length") {
        const tooBig = input.data.length > check.value;
        const tooSmall = input.data.length < check.value;
        if (tooBig || tooSmall) {
          ctx = this._getOrReturnCtx(input, ctx);
          if (tooBig) {
            addIssueToContext(ctx, {
              code: ZodIssueCode.too_big,
              maximum: check.value,
              type: "string",
              inclusive: true,
              exact: true,
              message: check.message
            });
          } else if (tooSmall) {
            addIssueToContext(ctx, {
              code: ZodIssueCode.too_small,
              minimum: check.value,
              type: "string",
              inclusive: true,
              exact: true,
              message: check.message
            });
          }
          status.dirty();
        }
      } else if (check.kind === "email") {
        if (!emailRegex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "email",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "emoji") {
        if (!emojiRegex) {
          emojiRegex = new RegExp(_emojiRegex, "u");
        }
        if (!emojiRegex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "emoji",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "uuid") {
        if (!uuidRegex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "uuid",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "nanoid") {
        if (!nanoidRegex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "nanoid",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "cuid") {
        if (!cuidRegex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "cuid",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "cuid2") {
        if (!cuid2Regex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "cuid2",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "ulid") {
        if (!ulidRegex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "ulid",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "url") {
        try {
          new URL(input.data);
        } catch {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "url",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "regex") {
        check.regex.lastIndex = 0;
        const testResult = check.regex.test(input.data);
        if (!testResult) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "regex",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "trim") {
        input.data = input.data.trim();
      } else if (check.kind === "includes") {
        if (!input.data.includes(check.value, check.position)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.invalid_string,
            validation: { includes: check.value, position: check.position },
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "toLowerCase") {
        input.data = input.data.toLowerCase();
      } else if (check.kind === "toUpperCase") {
        input.data = input.data.toUpperCase();
      } else if (check.kind === "startsWith") {
        if (!input.data.startsWith(check.value)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.invalid_string,
            validation: { startsWith: check.value },
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "endsWith") {
        if (!input.data.endsWith(check.value)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.invalid_string,
            validation: { endsWith: check.value },
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "datetime") {
        const regex = datetimeRegex(check);
        if (!regex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.invalid_string,
            validation: "datetime",
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "date") {
        const regex = dateRegex;
        if (!regex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.invalid_string,
            validation: "date",
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "time") {
        const regex = timeRegex(check);
        if (!regex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.invalid_string,
            validation: "time",
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "duration") {
        if (!durationRegex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "duration",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "ip") {
        if (!isValidIP(input.data, check.version)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "ip",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "jwt") {
        if (!isValidJWT(input.data, check.alg)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "jwt",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "cidr") {
        if (!isValidCidr(input.data, check.version)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "cidr",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "base64") {
        if (!base64Regex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "base64",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "base64url") {
        if (!base64urlRegex.test(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            validation: "base64url",
            code: ZodIssueCode.invalid_string,
            message: check.message
          });
          status.dirty();
        }
      } else {
        util.assertNever(check);
      }
    }
    return { status: status.value, value: input.data };
  }
  _regex(regex, validation, message) {
    return this.refinement((data) => regex.test(data), {
      validation,
      code: ZodIssueCode.invalid_string,
      ...errorUtil.errToObj(message)
    });
  }
  _addCheck(check) {
    return new _ZodString({
      ...this._def,
      checks: [...this._def.checks, check]
    });
  }
  email(message) {
    return this._addCheck({ kind: "email", ...errorUtil.errToObj(message) });
  }
  url(message) {
    return this._addCheck({ kind: "url", ...errorUtil.errToObj(message) });
  }
  emoji(message) {
    return this._addCheck({ kind: "emoji", ...errorUtil.errToObj(message) });
  }
  uuid(message) {
    return this._addCheck({ kind: "uuid", ...errorUtil.errToObj(message) });
  }
  nanoid(message) {
    return this._addCheck({ kind: "nanoid", ...errorUtil.errToObj(message) });
  }
  cuid(message) {
    return this._addCheck({ kind: "cuid", ...errorUtil.errToObj(message) });
  }
  cuid2(message) {
    return this._addCheck({ kind: "cuid2", ...errorUtil.errToObj(message) });
  }
  ulid(message) {
    return this._addCheck({ kind: "ulid", ...errorUtil.errToObj(message) });
  }
  base64(message) {
    return this._addCheck({ kind: "base64", ...errorUtil.errToObj(message) });
  }
  base64url(message) {
    return this._addCheck({
      kind: "base64url",
      ...errorUtil.errToObj(message)
    });
  }
  jwt(options) {
    return this._addCheck({ kind: "jwt", ...errorUtil.errToObj(options) });
  }
  ip(options) {
    return this._addCheck({ kind: "ip", ...errorUtil.errToObj(options) });
  }
  cidr(options) {
    return this._addCheck({ kind: "cidr", ...errorUtil.errToObj(options) });
  }
  datetime(options) {
    if (typeof options === "string") {
      return this._addCheck({
        kind: "datetime",
        precision: null,
        offset: false,
        local: false,
        message: options
      });
    }
    return this._addCheck({
      kind: "datetime",
      precision: typeof options?.precision === "undefined" ? null : options?.precision,
      offset: options?.offset ?? false,
      local: options?.local ?? false,
      ...errorUtil.errToObj(options?.message)
    });
  }
  date(message) {
    return this._addCheck({ kind: "date", message });
  }
  time(options) {
    if (typeof options === "string") {
      return this._addCheck({
        kind: "time",
        precision: null,
        message: options
      });
    }
    return this._addCheck({
      kind: "time",
      precision: typeof options?.precision === "undefined" ? null : options?.precision,
      ...errorUtil.errToObj(options?.message)
    });
  }
  duration(message) {
    return this._addCheck({ kind: "duration", ...errorUtil.errToObj(message) });
  }
  regex(regex, message) {
    return this._addCheck({
      kind: "regex",
      regex,
      ...errorUtil.errToObj(message)
    });
  }
  includes(value, options) {
    return this._addCheck({
      kind: "includes",
      value,
      position: options?.position,
      ...errorUtil.errToObj(options?.message)
    });
  }
  startsWith(value, message) {
    return this._addCheck({
      kind: "startsWith",
      value,
      ...errorUtil.errToObj(message)
    });
  }
  endsWith(value, message) {
    return this._addCheck({
      kind: "endsWith",
      value,
      ...errorUtil.errToObj(message)
    });
  }
  min(minLength, message) {
    return this._addCheck({
      kind: "min",
      value: minLength,
      ...errorUtil.errToObj(message)
    });
  }
  max(maxLength, message) {
    return this._addCheck({
      kind: "max",
      value: maxLength,
      ...errorUtil.errToObj(message)
    });
  }
  length(len, message) {
    return this._addCheck({
      kind: "length",
      value: len,
      ...errorUtil.errToObj(message)
    });
  }
  /**
   * Equivalent to `.min(1)`
   */
  nonempty(message) {
    return this.min(1, errorUtil.errToObj(message));
  }
  trim() {
    return new _ZodString({
      ...this._def,
      checks: [...this._def.checks, { kind: "trim" }]
    });
  }
  toLowerCase() {
    return new _ZodString({
      ...this._def,
      checks: [...this._def.checks, { kind: "toLowerCase" }]
    });
  }
  toUpperCase() {
    return new _ZodString({
      ...this._def,
      checks: [...this._def.checks, { kind: "toUpperCase" }]
    });
  }
  get isDatetime() {
    return !!this._def.checks.find((ch) => ch.kind === "datetime");
  }
  get isDate() {
    return !!this._def.checks.find((ch) => ch.kind === "date");
  }
  get isTime() {
    return !!this._def.checks.find((ch) => ch.kind === "time");
  }
  get isDuration() {
    return !!this._def.checks.find((ch) => ch.kind === "duration");
  }
  get isEmail() {
    return !!this._def.checks.find((ch) => ch.kind === "email");
  }
  get isURL() {
    return !!this._def.checks.find((ch) => ch.kind === "url");
  }
  get isEmoji() {
    return !!this._def.checks.find((ch) => ch.kind === "emoji");
  }
  get isUUID() {
    return !!this._def.checks.find((ch) => ch.kind === "uuid");
  }
  get isNANOID() {
    return !!this._def.checks.find((ch) => ch.kind === "nanoid");
  }
  get isCUID() {
    return !!this._def.checks.find((ch) => ch.kind === "cuid");
  }
  get isCUID2() {
    return !!this._def.checks.find((ch) => ch.kind === "cuid2");
  }
  get isULID() {
    return !!this._def.checks.find((ch) => ch.kind === "ulid");
  }
  get isIP() {
    return !!this._def.checks.find((ch) => ch.kind === "ip");
  }
  get isCIDR() {
    return !!this._def.checks.find((ch) => ch.kind === "cidr");
  }
  get isBase64() {
    return !!this._def.checks.find((ch) => ch.kind === "base64");
  }
  get isBase64url() {
    return !!this._def.checks.find((ch) => ch.kind === "base64url");
  }
  get minLength() {
    let min = null;
    for (const ch of this._def.checks) {
      if (ch.kind === "min") {
        if (min === null || ch.value > min)
          min = ch.value;
      }
    }
    return min;
  }
  get maxLength() {
    let max = null;
    for (const ch of this._def.checks) {
      if (ch.kind === "max") {
        if (max === null || ch.value < max)
          max = ch.value;
      }
    }
    return max;
  }
};
ZodString.create = (params) => {
  return new ZodString({
    checks: [],
    typeName: ZodFirstPartyTypeKind.ZodString,
    coerce: params?.coerce ?? false,
    ...processCreateParams(params)
  });
};
function floatSafeRemainder(val, step) {
  const valDecCount = (val.toString().split(".")[1] || "").length;
  const stepDecCount = (step.toString().split(".")[1] || "").length;
  const decCount = valDecCount > stepDecCount ? valDecCount : stepDecCount;
  const valInt = Number.parseInt(val.toFixed(decCount).replace(".", ""));
  const stepInt = Number.parseInt(step.toFixed(decCount).replace(".", ""));
  return valInt % stepInt / 10 ** decCount;
}
var ZodNumber = class _ZodNumber extends ZodType {
  constructor() {
    super(...arguments);
    this.min = this.gte;
    this.max = this.lte;
    this.step = this.multipleOf;
  }
  _parse(input) {
    if (this._def.coerce) {
      input.data = Number(input.data);
    }
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.number) {
      const ctx2 = this._getOrReturnCtx(input);
      addIssueToContext(ctx2, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.number,
        received: ctx2.parsedType
      });
      return INVALID;
    }
    let ctx = void 0;
    const status = new ParseStatus();
    for (const check of this._def.checks) {
      if (check.kind === "int") {
        if (!util.isInteger(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.invalid_type,
            expected: "integer",
            received: "float",
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "min") {
        const tooSmall = check.inclusive ? input.data < check.value : input.data <= check.value;
        if (tooSmall) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_small,
            minimum: check.value,
            type: "number",
            inclusive: check.inclusive,
            exact: false,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "max") {
        const tooBig = check.inclusive ? input.data > check.value : input.data >= check.value;
        if (tooBig) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_big,
            maximum: check.value,
            type: "number",
            inclusive: check.inclusive,
            exact: false,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "multipleOf") {
        if (floatSafeRemainder(input.data, check.value) !== 0) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.not_multiple_of,
            multipleOf: check.value,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "finite") {
        if (!Number.isFinite(input.data)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.not_finite,
            message: check.message
          });
          status.dirty();
        }
      } else {
        util.assertNever(check);
      }
    }
    return { status: status.value, value: input.data };
  }
  gte(value, message) {
    return this.setLimit("min", value, true, errorUtil.toString(message));
  }
  gt(value, message) {
    return this.setLimit("min", value, false, errorUtil.toString(message));
  }
  lte(value, message) {
    return this.setLimit("max", value, true, errorUtil.toString(message));
  }
  lt(value, message) {
    return this.setLimit("max", value, false, errorUtil.toString(message));
  }
  setLimit(kind, value, inclusive, message) {
    return new _ZodNumber({
      ...this._def,
      checks: [
        ...this._def.checks,
        {
          kind,
          value,
          inclusive,
          message: errorUtil.toString(message)
        }
      ]
    });
  }
  _addCheck(check) {
    return new _ZodNumber({
      ...this._def,
      checks: [...this._def.checks, check]
    });
  }
  int(message) {
    return this._addCheck({
      kind: "int",
      message: errorUtil.toString(message)
    });
  }
  positive(message) {
    return this._addCheck({
      kind: "min",
      value: 0,
      inclusive: false,
      message: errorUtil.toString(message)
    });
  }
  negative(message) {
    return this._addCheck({
      kind: "max",
      value: 0,
      inclusive: false,
      message: errorUtil.toString(message)
    });
  }
  nonpositive(message) {
    return this._addCheck({
      kind: "max",
      value: 0,
      inclusive: true,
      message: errorUtil.toString(message)
    });
  }
  nonnegative(message) {
    return this._addCheck({
      kind: "min",
      value: 0,
      inclusive: true,
      message: errorUtil.toString(message)
    });
  }
  multipleOf(value, message) {
    return this._addCheck({
      kind: "multipleOf",
      value,
      message: errorUtil.toString(message)
    });
  }
  finite(message) {
    return this._addCheck({
      kind: "finite",
      message: errorUtil.toString(message)
    });
  }
  safe(message) {
    return this._addCheck({
      kind: "min",
      inclusive: true,
      value: Number.MIN_SAFE_INTEGER,
      message: errorUtil.toString(message)
    })._addCheck({
      kind: "max",
      inclusive: true,
      value: Number.MAX_SAFE_INTEGER,
      message: errorUtil.toString(message)
    });
  }
  get minValue() {
    let min = null;
    for (const ch of this._def.checks) {
      if (ch.kind === "min") {
        if (min === null || ch.value > min)
          min = ch.value;
      }
    }
    return min;
  }
  get maxValue() {
    let max = null;
    for (const ch of this._def.checks) {
      if (ch.kind === "max") {
        if (max === null || ch.value < max)
          max = ch.value;
      }
    }
    return max;
  }
  get isInt() {
    return !!this._def.checks.find((ch) => ch.kind === "int" || ch.kind === "multipleOf" && util.isInteger(ch.value));
  }
  get isFinite() {
    let max = null;
    let min = null;
    for (const ch of this._def.checks) {
      if (ch.kind === "finite" || ch.kind === "int" || ch.kind === "multipleOf") {
        return true;
      } else if (ch.kind === "min") {
        if (min === null || ch.value > min)
          min = ch.value;
      } else if (ch.kind === "max") {
        if (max === null || ch.value < max)
          max = ch.value;
      }
    }
    return Number.isFinite(min) && Number.isFinite(max);
  }
};
ZodNumber.create = (params) => {
  return new ZodNumber({
    checks: [],
    typeName: ZodFirstPartyTypeKind.ZodNumber,
    coerce: params?.coerce || false,
    ...processCreateParams(params)
  });
};
var ZodBigInt = class _ZodBigInt extends ZodType {
  constructor() {
    super(...arguments);
    this.min = this.gte;
    this.max = this.lte;
  }
  _parse(input) {
    if (this._def.coerce) {
      try {
        input.data = BigInt(input.data);
      } catch {
        return this._getInvalidInput(input);
      }
    }
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.bigint) {
      return this._getInvalidInput(input);
    }
    let ctx = void 0;
    const status = new ParseStatus();
    for (const check of this._def.checks) {
      if (check.kind === "min") {
        const tooSmall = check.inclusive ? input.data < check.value : input.data <= check.value;
        if (tooSmall) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_small,
            type: "bigint",
            minimum: check.value,
            inclusive: check.inclusive,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "max") {
        const tooBig = check.inclusive ? input.data > check.value : input.data >= check.value;
        if (tooBig) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_big,
            type: "bigint",
            maximum: check.value,
            inclusive: check.inclusive,
            message: check.message
          });
          status.dirty();
        }
      } else if (check.kind === "multipleOf") {
        if (input.data % check.value !== BigInt(0)) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.not_multiple_of,
            multipleOf: check.value,
            message: check.message
          });
          status.dirty();
        }
      } else {
        util.assertNever(check);
      }
    }
    return { status: status.value, value: input.data };
  }
  _getInvalidInput(input) {
    const ctx = this._getOrReturnCtx(input);
    addIssueToContext(ctx, {
      code: ZodIssueCode.invalid_type,
      expected: ZodParsedType.bigint,
      received: ctx.parsedType
    });
    return INVALID;
  }
  gte(value, message) {
    return this.setLimit("min", value, true, errorUtil.toString(message));
  }
  gt(value, message) {
    return this.setLimit("min", value, false, errorUtil.toString(message));
  }
  lte(value, message) {
    return this.setLimit("max", value, true, errorUtil.toString(message));
  }
  lt(value, message) {
    return this.setLimit("max", value, false, errorUtil.toString(message));
  }
  setLimit(kind, value, inclusive, message) {
    return new _ZodBigInt({
      ...this._def,
      checks: [
        ...this._def.checks,
        {
          kind,
          value,
          inclusive,
          message: errorUtil.toString(message)
        }
      ]
    });
  }
  _addCheck(check) {
    return new _ZodBigInt({
      ...this._def,
      checks: [...this._def.checks, check]
    });
  }
  positive(message) {
    return this._addCheck({
      kind: "min",
      value: BigInt(0),
      inclusive: false,
      message: errorUtil.toString(message)
    });
  }
  negative(message) {
    return this._addCheck({
      kind: "max",
      value: BigInt(0),
      inclusive: false,
      message: errorUtil.toString(message)
    });
  }
  nonpositive(message) {
    return this._addCheck({
      kind: "max",
      value: BigInt(0),
      inclusive: true,
      message: errorUtil.toString(message)
    });
  }
  nonnegative(message) {
    return this._addCheck({
      kind: "min",
      value: BigInt(0),
      inclusive: true,
      message: errorUtil.toString(message)
    });
  }
  multipleOf(value, message) {
    return this._addCheck({
      kind: "multipleOf",
      value,
      message: errorUtil.toString(message)
    });
  }
  get minValue() {
    let min = null;
    for (const ch of this._def.checks) {
      if (ch.kind === "min") {
        if (min === null || ch.value > min)
          min = ch.value;
      }
    }
    return min;
  }
  get maxValue() {
    let max = null;
    for (const ch of this._def.checks) {
      if (ch.kind === "max") {
        if (max === null || ch.value < max)
          max = ch.value;
      }
    }
    return max;
  }
};
ZodBigInt.create = (params) => {
  return new ZodBigInt({
    checks: [],
    typeName: ZodFirstPartyTypeKind.ZodBigInt,
    coerce: params?.coerce ?? false,
    ...processCreateParams(params)
  });
};
var ZodBoolean = class extends ZodType {
  _parse(input) {
    if (this._def.coerce) {
      input.data = Boolean(input.data);
    }
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.boolean) {
      const ctx = this._getOrReturnCtx(input);
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.boolean,
        received: ctx.parsedType
      });
      return INVALID;
    }
    return OK(input.data);
  }
};
ZodBoolean.create = (params) => {
  return new ZodBoolean({
    typeName: ZodFirstPartyTypeKind.ZodBoolean,
    coerce: params?.coerce || false,
    ...processCreateParams(params)
  });
};
var ZodDate = class _ZodDate extends ZodType {
  _parse(input) {
    if (this._def.coerce) {
      input.data = new Date(input.data);
    }
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.date) {
      const ctx2 = this._getOrReturnCtx(input);
      addIssueToContext(ctx2, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.date,
        received: ctx2.parsedType
      });
      return INVALID;
    }
    if (Number.isNaN(input.data.getTime())) {
      const ctx2 = this._getOrReturnCtx(input);
      addIssueToContext(ctx2, {
        code: ZodIssueCode.invalid_date
      });
      return INVALID;
    }
    const status = new ParseStatus();
    let ctx = void 0;
    for (const check of this._def.checks) {
      if (check.kind === "min") {
        if (input.data.getTime() < check.value) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_small,
            message: check.message,
            inclusive: true,
            exact: false,
            minimum: check.value,
            type: "date"
          });
          status.dirty();
        }
      } else if (check.kind === "max") {
        if (input.data.getTime() > check.value) {
          ctx = this._getOrReturnCtx(input, ctx);
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_big,
            message: check.message,
            inclusive: true,
            exact: false,
            maximum: check.value,
            type: "date"
          });
          status.dirty();
        }
      } else {
        util.assertNever(check);
      }
    }
    return {
      status: status.value,
      value: new Date(input.data.getTime())
    };
  }
  _addCheck(check) {
    return new _ZodDate({
      ...this._def,
      checks: [...this._def.checks, check]
    });
  }
  min(minDate, message) {
    return this._addCheck({
      kind: "min",
      value: minDate.getTime(),
      message: errorUtil.toString(message)
    });
  }
  max(maxDate, message) {
    return this._addCheck({
      kind: "max",
      value: maxDate.getTime(),
      message: errorUtil.toString(message)
    });
  }
  get minDate() {
    let min = null;
    for (const ch of this._def.checks) {
      if (ch.kind === "min") {
        if (min === null || ch.value > min)
          min = ch.value;
      }
    }
    return min != null ? new Date(min) : null;
  }
  get maxDate() {
    let max = null;
    for (const ch of this._def.checks) {
      if (ch.kind === "max") {
        if (max === null || ch.value < max)
          max = ch.value;
      }
    }
    return max != null ? new Date(max) : null;
  }
};
ZodDate.create = (params) => {
  return new ZodDate({
    checks: [],
    coerce: params?.coerce || false,
    typeName: ZodFirstPartyTypeKind.ZodDate,
    ...processCreateParams(params)
  });
};
var ZodSymbol = class extends ZodType {
  _parse(input) {
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.symbol) {
      const ctx = this._getOrReturnCtx(input);
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.symbol,
        received: ctx.parsedType
      });
      return INVALID;
    }
    return OK(input.data);
  }
};
ZodSymbol.create = (params) => {
  return new ZodSymbol({
    typeName: ZodFirstPartyTypeKind.ZodSymbol,
    ...processCreateParams(params)
  });
};
var ZodUndefined = class extends ZodType {
  _parse(input) {
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.undefined) {
      const ctx = this._getOrReturnCtx(input);
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.undefined,
        received: ctx.parsedType
      });
      return INVALID;
    }
    return OK(input.data);
  }
};
ZodUndefined.create = (params) => {
  return new ZodUndefined({
    typeName: ZodFirstPartyTypeKind.ZodUndefined,
    ...processCreateParams(params)
  });
};
var ZodNull = class extends ZodType {
  _parse(input) {
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.null) {
      const ctx = this._getOrReturnCtx(input);
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.null,
        received: ctx.parsedType
      });
      return INVALID;
    }
    return OK(input.data);
  }
};
ZodNull.create = (params) => {
  return new ZodNull({
    typeName: ZodFirstPartyTypeKind.ZodNull,
    ...processCreateParams(params)
  });
};
var ZodAny = class extends ZodType {
  constructor() {
    super(...arguments);
    this._any = true;
  }
  _parse(input) {
    return OK(input.data);
  }
};
ZodAny.create = (params) => {
  return new ZodAny({
    typeName: ZodFirstPartyTypeKind.ZodAny,
    ...processCreateParams(params)
  });
};
var ZodUnknown = class extends ZodType {
  constructor() {
    super(...arguments);
    this._unknown = true;
  }
  _parse(input) {
    return OK(input.data);
  }
};
ZodUnknown.create = (params) => {
  return new ZodUnknown({
    typeName: ZodFirstPartyTypeKind.ZodUnknown,
    ...processCreateParams(params)
  });
};
var ZodNever = class extends ZodType {
  _parse(input) {
    const ctx = this._getOrReturnCtx(input);
    addIssueToContext(ctx, {
      code: ZodIssueCode.invalid_type,
      expected: ZodParsedType.never,
      received: ctx.parsedType
    });
    return INVALID;
  }
};
ZodNever.create = (params) => {
  return new ZodNever({
    typeName: ZodFirstPartyTypeKind.ZodNever,
    ...processCreateParams(params)
  });
};
var ZodVoid = class extends ZodType {
  _parse(input) {
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.undefined) {
      const ctx = this._getOrReturnCtx(input);
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.void,
        received: ctx.parsedType
      });
      return INVALID;
    }
    return OK(input.data);
  }
};
ZodVoid.create = (params) => {
  return new ZodVoid({
    typeName: ZodFirstPartyTypeKind.ZodVoid,
    ...processCreateParams(params)
  });
};
var ZodArray = class _ZodArray extends ZodType {
  _parse(input) {
    const { ctx, status } = this._processInputParams(input);
    const def = this._def;
    if (ctx.parsedType !== ZodParsedType.array) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.array,
        received: ctx.parsedType
      });
      return INVALID;
    }
    if (def.exactLength !== null) {
      const tooBig = ctx.data.length > def.exactLength.value;
      const tooSmall = ctx.data.length < def.exactLength.value;
      if (tooBig || tooSmall) {
        addIssueToContext(ctx, {
          code: tooBig ? ZodIssueCode.too_big : ZodIssueCode.too_small,
          minimum: tooSmall ? def.exactLength.value : void 0,
          maximum: tooBig ? def.exactLength.value : void 0,
          type: "array",
          inclusive: true,
          exact: true,
          message: def.exactLength.message
        });
        status.dirty();
      }
    }
    if (def.minLength !== null) {
      if (ctx.data.length < def.minLength.value) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.too_small,
          minimum: def.minLength.value,
          type: "array",
          inclusive: true,
          exact: false,
          message: def.minLength.message
        });
        status.dirty();
      }
    }
    if (def.maxLength !== null) {
      if (ctx.data.length > def.maxLength.value) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.too_big,
          maximum: def.maxLength.value,
          type: "array",
          inclusive: true,
          exact: false,
          message: def.maxLength.message
        });
        status.dirty();
      }
    }
    if (ctx.common.async) {
      return Promise.all([...ctx.data].map((item, i) => {
        return def.type._parseAsync(new ParseInputLazyPath(ctx, item, ctx.path, i));
      })).then((result2) => {
        return ParseStatus.mergeArray(status, result2);
      });
    }
    const result = [...ctx.data].map((item, i) => {
      return def.type._parseSync(new ParseInputLazyPath(ctx, item, ctx.path, i));
    });
    return ParseStatus.mergeArray(status, result);
  }
  get element() {
    return this._def.type;
  }
  min(minLength, message) {
    return new _ZodArray({
      ...this._def,
      minLength: { value: minLength, message: errorUtil.toString(message) }
    });
  }
  max(maxLength, message) {
    return new _ZodArray({
      ...this._def,
      maxLength: { value: maxLength, message: errorUtil.toString(message) }
    });
  }
  length(len, message) {
    return new _ZodArray({
      ...this._def,
      exactLength: { value: len, message: errorUtil.toString(message) }
    });
  }
  nonempty(message) {
    return this.min(1, message);
  }
};
ZodArray.create = (schema, params) => {
  return new ZodArray({
    type: schema,
    minLength: null,
    maxLength: null,
    exactLength: null,
    typeName: ZodFirstPartyTypeKind.ZodArray,
    ...processCreateParams(params)
  });
};
function deepPartialify(schema) {
  if (schema instanceof ZodObject) {
    const newShape = {};
    for (const key2 in schema.shape) {
      const fieldSchema = schema.shape[key2];
      newShape[key2] = ZodOptional.create(deepPartialify(fieldSchema));
    }
    return new ZodObject({
      ...schema._def,
      shape: () => newShape
    });
  } else if (schema instanceof ZodArray) {
    return new ZodArray({
      ...schema._def,
      type: deepPartialify(schema.element)
    });
  } else if (schema instanceof ZodOptional) {
    return ZodOptional.create(deepPartialify(schema.unwrap()));
  } else if (schema instanceof ZodNullable) {
    return ZodNullable.create(deepPartialify(schema.unwrap()));
  } else if (schema instanceof ZodTuple) {
    return ZodTuple.create(schema.items.map((item) => deepPartialify(item)));
  } else {
    return schema;
  }
}
var ZodObject = class _ZodObject extends ZodType {
  constructor() {
    super(...arguments);
    this._cached = null;
    this.nonstrict = this.passthrough;
    this.augment = this.extend;
  }
  _getCached() {
    if (this._cached !== null)
      return this._cached;
    const shape = this._def.shape();
    const keys = util.objectKeys(shape);
    this._cached = { shape, keys };
    return this._cached;
  }
  _parse(input) {
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.object) {
      const ctx2 = this._getOrReturnCtx(input);
      addIssueToContext(ctx2, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.object,
        received: ctx2.parsedType
      });
      return INVALID;
    }
    const { status, ctx } = this._processInputParams(input);
    const { shape, keys: shapeKeys } = this._getCached();
    const extraKeys = [];
    if (!(this._def.catchall instanceof ZodNever && this._def.unknownKeys === "strip")) {
      for (const key2 in ctx.data) {
        if (!shapeKeys.includes(key2)) {
          extraKeys.push(key2);
        }
      }
    }
    const pairs = [];
    for (const key2 of shapeKeys) {
      const keyValidator = shape[key2];
      const value = ctx.data[key2];
      pairs.push({
        key: { status: "valid", value: key2 },
        value: keyValidator._parse(new ParseInputLazyPath(ctx, value, ctx.path, key2)),
        alwaysSet: key2 in ctx.data
      });
    }
    if (this._def.catchall instanceof ZodNever) {
      const unknownKeys = this._def.unknownKeys;
      if (unknownKeys === "passthrough") {
        for (const key2 of extraKeys) {
          pairs.push({
            key: { status: "valid", value: key2 },
            value: { status: "valid", value: ctx.data[key2] }
          });
        }
      } else if (unknownKeys === "strict") {
        if (extraKeys.length > 0) {
          addIssueToContext(ctx, {
            code: ZodIssueCode.unrecognized_keys,
            keys: extraKeys
          });
          status.dirty();
        }
      } else if (unknownKeys === "strip") {
      } else {
        throw new Error(`Internal ZodObject error: invalid unknownKeys value.`);
      }
    } else {
      const catchall = this._def.catchall;
      for (const key2 of extraKeys) {
        const value = ctx.data[key2];
        pairs.push({
          key: { status: "valid", value: key2 },
          value: catchall._parse(
            new ParseInputLazyPath(ctx, value, ctx.path, key2)
            //, ctx.child(key), value, getParsedType(value)
          ),
          alwaysSet: key2 in ctx.data
        });
      }
    }
    if (ctx.common.async) {
      return Promise.resolve().then(async () => {
        const syncPairs = [];
        for (const pair of pairs) {
          const key2 = await pair.key;
          const value = await pair.value;
          syncPairs.push({
            key: key2,
            value,
            alwaysSet: pair.alwaysSet
          });
        }
        return syncPairs;
      }).then((syncPairs) => {
        return ParseStatus.mergeObjectSync(status, syncPairs);
      });
    } else {
      return ParseStatus.mergeObjectSync(status, pairs);
    }
  }
  get shape() {
    return this._def.shape();
  }
  strict(message) {
    errorUtil.errToObj;
    return new _ZodObject({
      ...this._def,
      unknownKeys: "strict",
      ...message !== void 0 ? {
        errorMap: (issue, ctx) => {
          const defaultError = this._def.errorMap?.(issue, ctx).message ?? ctx.defaultError;
          if (issue.code === "unrecognized_keys")
            return {
              message: errorUtil.errToObj(message).message ?? defaultError
            };
          return {
            message: defaultError
          };
        }
      } : {}
    });
  }
  strip() {
    return new _ZodObject({
      ...this._def,
      unknownKeys: "strip"
    });
  }
  passthrough() {
    return new _ZodObject({
      ...this._def,
      unknownKeys: "passthrough"
    });
  }
  // const AugmentFactory =
  //   <Def extends ZodObjectDef>(def: Def) =>
  //   <Augmentation extends ZodRawShape>(
  //     augmentation: Augmentation
  //   ): ZodObject<
  //     extendShape<ReturnType<Def["shape"]>, Augmentation>,
  //     Def["unknownKeys"],
  //     Def["catchall"]
  //   > => {
  //     return new ZodObject({
  //       ...def,
  //       shape: () => ({
  //         ...def.shape(),
  //         ...augmentation,
  //       }),
  //     }) as any;
  //   };
  extend(augmentation) {
    return new _ZodObject({
      ...this._def,
      shape: () => ({
        ...this._def.shape(),
        ...augmentation
      })
    });
  }
  /**
   * Prior to zod@1.0.12 there was a bug in the
   * inferred type of merged objects. Please
   * upgrade if you are experiencing issues.
   */
  merge(merging) {
    const merged = new _ZodObject({
      unknownKeys: merging._def.unknownKeys,
      catchall: merging._def.catchall,
      shape: () => ({
        ...this._def.shape(),
        ...merging._def.shape()
      }),
      typeName: ZodFirstPartyTypeKind.ZodObject
    });
    return merged;
  }
  // merge<
  //   Incoming extends AnyZodObject,
  //   Augmentation extends Incoming["shape"],
  //   NewOutput extends {
  //     [k in keyof Augmentation | keyof Output]: k extends keyof Augmentation
  //       ? Augmentation[k]["_output"]
  //       : k extends keyof Output
  //       ? Output[k]
  //       : never;
  //   },
  //   NewInput extends {
  //     [k in keyof Augmentation | keyof Input]: k extends keyof Augmentation
  //       ? Augmentation[k]["_input"]
  //       : k extends keyof Input
  //       ? Input[k]
  //       : never;
  //   }
  // >(
  //   merging: Incoming
  // ): ZodObject<
  //   extendShape<T, ReturnType<Incoming["_def"]["shape"]>>,
  //   Incoming["_def"]["unknownKeys"],
  //   Incoming["_def"]["catchall"],
  //   NewOutput,
  //   NewInput
  // > {
  //   const merged: any = new ZodObject({
  //     unknownKeys: merging._def.unknownKeys,
  //     catchall: merging._def.catchall,
  //     shape: () =>
  //       objectUtil.mergeShapes(this._def.shape(), merging._def.shape()),
  //     typeName: ZodFirstPartyTypeKind.ZodObject,
  //   }) as any;
  //   return merged;
  // }
  setKey(key2, schema) {
    return this.augment({ [key2]: schema });
  }
  // merge<Incoming extends AnyZodObject>(
  //   merging: Incoming
  // ): //ZodObject<T & Incoming["_shape"], UnknownKeys, Catchall> = (merging) => {
  // ZodObject<
  //   extendShape<T, ReturnType<Incoming["_def"]["shape"]>>,
  //   Incoming["_def"]["unknownKeys"],
  //   Incoming["_def"]["catchall"]
  // > {
  //   // const mergedShape = objectUtil.mergeShapes(
  //   //   this._def.shape(),
  //   //   merging._def.shape()
  //   // );
  //   const merged: any = new ZodObject({
  //     unknownKeys: merging._def.unknownKeys,
  //     catchall: merging._def.catchall,
  //     shape: () =>
  //       objectUtil.mergeShapes(this._def.shape(), merging._def.shape()),
  //     typeName: ZodFirstPartyTypeKind.ZodObject,
  //   }) as any;
  //   return merged;
  // }
  catchall(index) {
    return new _ZodObject({
      ...this._def,
      catchall: index
    });
  }
  pick(mask) {
    const shape = {};
    for (const key2 of util.objectKeys(mask)) {
      if (mask[key2] && this.shape[key2]) {
        shape[key2] = this.shape[key2];
      }
    }
    return new _ZodObject({
      ...this._def,
      shape: () => shape
    });
  }
  omit(mask) {
    const shape = {};
    for (const key2 of util.objectKeys(this.shape)) {
      if (!mask[key2]) {
        shape[key2] = this.shape[key2];
      }
    }
    return new _ZodObject({
      ...this._def,
      shape: () => shape
    });
  }
  /**
   * @deprecated
   */
  deepPartial() {
    return deepPartialify(this);
  }
  partial(mask) {
    const newShape = {};
    for (const key2 of util.objectKeys(this.shape)) {
      const fieldSchema = this.shape[key2];
      if (mask && !mask[key2]) {
        newShape[key2] = fieldSchema;
      } else {
        newShape[key2] = fieldSchema.optional();
      }
    }
    return new _ZodObject({
      ...this._def,
      shape: () => newShape
    });
  }
  required(mask) {
    const newShape = {};
    for (const key2 of util.objectKeys(this.shape)) {
      if (mask && !mask[key2]) {
        newShape[key2] = this.shape[key2];
      } else {
        const fieldSchema = this.shape[key2];
        let newField = fieldSchema;
        while (newField instanceof ZodOptional) {
          newField = newField._def.innerType;
        }
        newShape[key2] = newField;
      }
    }
    return new _ZodObject({
      ...this._def,
      shape: () => newShape
    });
  }
  keyof() {
    return createZodEnum(util.objectKeys(this.shape));
  }
};
ZodObject.create = (shape, params) => {
  return new ZodObject({
    shape: () => shape,
    unknownKeys: "strip",
    catchall: ZodNever.create(),
    typeName: ZodFirstPartyTypeKind.ZodObject,
    ...processCreateParams(params)
  });
};
ZodObject.strictCreate = (shape, params) => {
  return new ZodObject({
    shape: () => shape,
    unknownKeys: "strict",
    catchall: ZodNever.create(),
    typeName: ZodFirstPartyTypeKind.ZodObject,
    ...processCreateParams(params)
  });
};
ZodObject.lazycreate = (shape, params) => {
  return new ZodObject({
    shape,
    unknownKeys: "strip",
    catchall: ZodNever.create(),
    typeName: ZodFirstPartyTypeKind.ZodObject,
    ...processCreateParams(params)
  });
};
var ZodUnion = class extends ZodType {
  _parse(input) {
    const { ctx } = this._processInputParams(input);
    const options = this._def.options;
    function handleResults(results) {
      for (const result of results) {
        if (result.result.status === "valid") {
          return result.result;
        }
      }
      for (const result of results) {
        if (result.result.status === "dirty") {
          ctx.common.issues.push(...result.ctx.common.issues);
          return result.result;
        }
      }
      const unionErrors = results.map((result) => new ZodError(result.ctx.common.issues));
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_union,
        unionErrors
      });
      return INVALID;
    }
    if (ctx.common.async) {
      return Promise.all(options.map(async (option) => {
        const childCtx = {
          ...ctx,
          common: {
            ...ctx.common,
            issues: []
          },
          parent: null
        };
        return {
          result: await option._parseAsync({
            data: ctx.data,
            path: ctx.path,
            parent: childCtx
          }),
          ctx: childCtx
        };
      })).then(handleResults);
    } else {
      let dirty = void 0;
      const issues = [];
      for (const option of options) {
        const childCtx = {
          ...ctx,
          common: {
            ...ctx.common,
            issues: []
          },
          parent: null
        };
        const result = option._parseSync({
          data: ctx.data,
          path: ctx.path,
          parent: childCtx
        });
        if (result.status === "valid") {
          return result;
        } else if (result.status === "dirty" && !dirty) {
          dirty = { result, ctx: childCtx };
        }
        if (childCtx.common.issues.length) {
          issues.push(childCtx.common.issues);
        }
      }
      if (dirty) {
        ctx.common.issues.push(...dirty.ctx.common.issues);
        return dirty.result;
      }
      const unionErrors = issues.map((issues2) => new ZodError(issues2));
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_union,
        unionErrors
      });
      return INVALID;
    }
  }
  get options() {
    return this._def.options;
  }
};
ZodUnion.create = (types, params) => {
  return new ZodUnion({
    options: types,
    typeName: ZodFirstPartyTypeKind.ZodUnion,
    ...processCreateParams(params)
  });
};
var getDiscriminator = (type) => {
  if (type instanceof ZodLazy) {
    return getDiscriminator(type.schema);
  } else if (type instanceof ZodEffects) {
    return getDiscriminator(type.innerType());
  } else if (type instanceof ZodLiteral) {
    return [type.value];
  } else if (type instanceof ZodEnum) {
    return type.options;
  } else if (type instanceof ZodNativeEnum) {
    return util.objectValues(type.enum);
  } else if (type instanceof ZodDefault) {
    return getDiscriminator(type._def.innerType);
  } else if (type instanceof ZodUndefined) {
    return [void 0];
  } else if (type instanceof ZodNull) {
    return [null];
  } else if (type instanceof ZodOptional) {
    return [void 0, ...getDiscriminator(type.unwrap())];
  } else if (type instanceof ZodNullable) {
    return [null, ...getDiscriminator(type.unwrap())];
  } else if (type instanceof ZodBranded) {
    return getDiscriminator(type.unwrap());
  } else if (type instanceof ZodReadonly) {
    return getDiscriminator(type.unwrap());
  } else if (type instanceof ZodCatch) {
    return getDiscriminator(type._def.innerType);
  } else {
    return [];
  }
};
var ZodDiscriminatedUnion = class _ZodDiscriminatedUnion extends ZodType {
  _parse(input) {
    const { ctx } = this._processInputParams(input);
    if (ctx.parsedType !== ZodParsedType.object) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.object,
        received: ctx.parsedType
      });
      return INVALID;
    }
    const discriminator = this.discriminator;
    const discriminatorValue = ctx.data[discriminator];
    const option = this.optionsMap.get(discriminatorValue);
    if (!option) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_union_discriminator,
        options: Array.from(this.optionsMap.keys()),
        path: [discriminator]
      });
      return INVALID;
    }
    if (ctx.common.async) {
      return option._parseAsync({
        data: ctx.data,
        path: ctx.path,
        parent: ctx
      });
    } else {
      return option._parseSync({
        data: ctx.data,
        path: ctx.path,
        parent: ctx
      });
    }
  }
  get discriminator() {
    return this._def.discriminator;
  }
  get options() {
    return this._def.options;
  }
  get optionsMap() {
    return this._def.optionsMap;
  }
  /**
   * The constructor of the discriminated union schema. Its behaviour is very similar to that of the normal z.union() constructor.
   * However, it only allows a union of objects, all of which need to share a discriminator property. This property must
   * have a different value for each object in the union.
   * @param discriminator the name of the discriminator property
   * @param types an array of object schemas
   * @param params
   */
  static create(discriminator, options, params) {
    const optionsMap = /* @__PURE__ */ new Map();
    for (const type of options) {
      const discriminatorValues = getDiscriminator(type.shape[discriminator]);
      if (!discriminatorValues.length) {
        throw new Error(`A discriminator value for key \`${discriminator}\` could not be extracted from all schema options`);
      }
      for (const value of discriminatorValues) {
        if (optionsMap.has(value)) {
          throw new Error(`Discriminator property ${String(discriminator)} has duplicate value ${String(value)}`);
        }
        optionsMap.set(value, type);
      }
    }
    return new _ZodDiscriminatedUnion({
      typeName: ZodFirstPartyTypeKind.ZodDiscriminatedUnion,
      discriminator,
      options,
      optionsMap,
      ...processCreateParams(params)
    });
  }
};
function mergeValues(a, b) {
  const aType = getParsedType(a);
  const bType = getParsedType(b);
  if (a === b) {
    return { valid: true, data: a };
  } else if (aType === ZodParsedType.object && bType === ZodParsedType.object) {
    const bKeys = util.objectKeys(b);
    const sharedKeys = util.objectKeys(a).filter((key2) => bKeys.indexOf(key2) !== -1);
    const newObj = { ...a, ...b };
    for (const key2 of sharedKeys) {
      const sharedValue = mergeValues(a[key2], b[key2]);
      if (!sharedValue.valid) {
        return { valid: false };
      }
      newObj[key2] = sharedValue.data;
    }
    return { valid: true, data: newObj };
  } else if (aType === ZodParsedType.array && bType === ZodParsedType.array) {
    if (a.length !== b.length) {
      return { valid: false };
    }
    const newArray = [];
    for (let index = 0; index < a.length; index++) {
      const itemA = a[index];
      const itemB = b[index];
      const sharedValue = mergeValues(itemA, itemB);
      if (!sharedValue.valid) {
        return { valid: false };
      }
      newArray.push(sharedValue.data);
    }
    return { valid: true, data: newArray };
  } else if (aType === ZodParsedType.date && bType === ZodParsedType.date && +a === +b) {
    return { valid: true, data: a };
  } else {
    return { valid: false };
  }
}
var ZodIntersection = class extends ZodType {
  _parse(input) {
    const { status, ctx } = this._processInputParams(input);
    const handleParsed = (parsedLeft, parsedRight) => {
      if (isAborted(parsedLeft) || isAborted(parsedRight)) {
        return INVALID;
      }
      const merged = mergeValues(parsedLeft.value, parsedRight.value);
      if (!merged.valid) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_intersection_types
        });
        return INVALID;
      }
      if (isDirty(parsedLeft) || isDirty(parsedRight)) {
        status.dirty();
      }
      return { status: status.value, value: merged.data };
    };
    if (ctx.common.async) {
      return Promise.all([
        this._def.left._parseAsync({
          data: ctx.data,
          path: ctx.path,
          parent: ctx
        }),
        this._def.right._parseAsync({
          data: ctx.data,
          path: ctx.path,
          parent: ctx
        })
      ]).then(([left, right]) => handleParsed(left, right));
    } else {
      return handleParsed(this._def.left._parseSync({
        data: ctx.data,
        path: ctx.path,
        parent: ctx
      }), this._def.right._parseSync({
        data: ctx.data,
        path: ctx.path,
        parent: ctx
      }));
    }
  }
};
ZodIntersection.create = (left, right, params) => {
  return new ZodIntersection({
    left,
    right,
    typeName: ZodFirstPartyTypeKind.ZodIntersection,
    ...processCreateParams(params)
  });
};
var ZodTuple = class _ZodTuple extends ZodType {
  _parse(input) {
    const { status, ctx } = this._processInputParams(input);
    if (ctx.parsedType !== ZodParsedType.array) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.array,
        received: ctx.parsedType
      });
      return INVALID;
    }
    if (ctx.data.length < this._def.items.length) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.too_small,
        minimum: this._def.items.length,
        inclusive: true,
        exact: false,
        type: "array"
      });
      return INVALID;
    }
    const rest = this._def.rest;
    if (!rest && ctx.data.length > this._def.items.length) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.too_big,
        maximum: this._def.items.length,
        inclusive: true,
        exact: false,
        type: "array"
      });
      status.dirty();
    }
    const items = [...ctx.data].map((item, itemIndex) => {
      const schema = this._def.items[itemIndex] || this._def.rest;
      if (!schema)
        return null;
      return schema._parse(new ParseInputLazyPath(ctx, item, ctx.path, itemIndex));
    }).filter((x) => !!x);
    if (ctx.common.async) {
      return Promise.all(items).then((results) => {
        return ParseStatus.mergeArray(status, results);
      });
    } else {
      return ParseStatus.mergeArray(status, items);
    }
  }
  get items() {
    return this._def.items;
  }
  rest(rest) {
    return new _ZodTuple({
      ...this._def,
      rest
    });
  }
};
ZodTuple.create = (schemas, params) => {
  if (!Array.isArray(schemas)) {
    throw new Error("You must pass an array of schemas to z.tuple([ ... ])");
  }
  return new ZodTuple({
    items: schemas,
    typeName: ZodFirstPartyTypeKind.ZodTuple,
    rest: null,
    ...processCreateParams(params)
  });
};
var ZodRecord = class _ZodRecord extends ZodType {
  get keySchema() {
    return this._def.keyType;
  }
  get valueSchema() {
    return this._def.valueType;
  }
  _parse(input) {
    const { status, ctx } = this._processInputParams(input);
    if (ctx.parsedType !== ZodParsedType.object) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.object,
        received: ctx.parsedType
      });
      return INVALID;
    }
    const pairs = [];
    const keyType = this._def.keyType;
    const valueType = this._def.valueType;
    for (const key2 in ctx.data) {
      pairs.push({
        key: keyType._parse(new ParseInputLazyPath(ctx, key2, ctx.path, key2)),
        value: valueType._parse(new ParseInputLazyPath(ctx, ctx.data[key2], ctx.path, key2)),
        alwaysSet: key2 in ctx.data
      });
    }
    if (ctx.common.async) {
      return ParseStatus.mergeObjectAsync(status, pairs);
    } else {
      return ParseStatus.mergeObjectSync(status, pairs);
    }
  }
  get element() {
    return this._def.valueType;
  }
  static create(first, second, third) {
    if (second instanceof ZodType) {
      return new _ZodRecord({
        keyType: first,
        valueType: second,
        typeName: ZodFirstPartyTypeKind.ZodRecord,
        ...processCreateParams(third)
      });
    }
    return new _ZodRecord({
      keyType: ZodString.create(),
      valueType: first,
      typeName: ZodFirstPartyTypeKind.ZodRecord,
      ...processCreateParams(second)
    });
  }
};
var ZodMap = class extends ZodType {
  get keySchema() {
    return this._def.keyType;
  }
  get valueSchema() {
    return this._def.valueType;
  }
  _parse(input) {
    const { status, ctx } = this._processInputParams(input);
    if (ctx.parsedType !== ZodParsedType.map) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.map,
        received: ctx.parsedType
      });
      return INVALID;
    }
    const keyType = this._def.keyType;
    const valueType = this._def.valueType;
    const pairs = [...ctx.data.entries()].map(([key2, value], index) => {
      return {
        key: keyType._parse(new ParseInputLazyPath(ctx, key2, ctx.path, [index, "key"])),
        value: valueType._parse(new ParseInputLazyPath(ctx, value, ctx.path, [index, "value"]))
      };
    });
    if (ctx.common.async) {
      const finalMap = /* @__PURE__ */ new Map();
      return Promise.resolve().then(async () => {
        for (const pair of pairs) {
          const key2 = await pair.key;
          const value = await pair.value;
          if (key2.status === "aborted" || value.status === "aborted") {
            return INVALID;
          }
          if (key2.status === "dirty" || value.status === "dirty") {
            status.dirty();
          }
          finalMap.set(key2.value, value.value);
        }
        return { status: status.value, value: finalMap };
      });
    } else {
      const finalMap = /* @__PURE__ */ new Map();
      for (const pair of pairs) {
        const key2 = pair.key;
        const value = pair.value;
        if (key2.status === "aborted" || value.status === "aborted") {
          return INVALID;
        }
        if (key2.status === "dirty" || value.status === "dirty") {
          status.dirty();
        }
        finalMap.set(key2.value, value.value);
      }
      return { status: status.value, value: finalMap };
    }
  }
};
ZodMap.create = (keyType, valueType, params) => {
  return new ZodMap({
    valueType,
    keyType,
    typeName: ZodFirstPartyTypeKind.ZodMap,
    ...processCreateParams(params)
  });
};
var ZodSet = class _ZodSet extends ZodType {
  _parse(input) {
    const { status, ctx } = this._processInputParams(input);
    if (ctx.parsedType !== ZodParsedType.set) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.set,
        received: ctx.parsedType
      });
      return INVALID;
    }
    const def = this._def;
    if (def.minSize !== null) {
      if (ctx.data.size < def.minSize.value) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.too_small,
          minimum: def.minSize.value,
          type: "set",
          inclusive: true,
          exact: false,
          message: def.minSize.message
        });
        status.dirty();
      }
    }
    if (def.maxSize !== null) {
      if (ctx.data.size > def.maxSize.value) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.too_big,
          maximum: def.maxSize.value,
          type: "set",
          inclusive: true,
          exact: false,
          message: def.maxSize.message
        });
        status.dirty();
      }
    }
    const valueType = this._def.valueType;
    function finalizeSet(elements2) {
      const parsedSet = /* @__PURE__ */ new Set();
      for (const element of elements2) {
        if (element.status === "aborted")
          return INVALID;
        if (element.status === "dirty")
          status.dirty();
        parsedSet.add(element.value);
      }
      return { status: status.value, value: parsedSet };
    }
    const elements = [...ctx.data.values()].map((item, i) => valueType._parse(new ParseInputLazyPath(ctx, item, ctx.path, i)));
    if (ctx.common.async) {
      return Promise.all(elements).then((elements2) => finalizeSet(elements2));
    } else {
      return finalizeSet(elements);
    }
  }
  min(minSize, message) {
    return new _ZodSet({
      ...this._def,
      minSize: { value: minSize, message: errorUtil.toString(message) }
    });
  }
  max(maxSize, message) {
    return new _ZodSet({
      ...this._def,
      maxSize: { value: maxSize, message: errorUtil.toString(message) }
    });
  }
  size(size, message) {
    return this.min(size, message).max(size, message);
  }
  nonempty(message) {
    return this.min(1, message);
  }
};
ZodSet.create = (valueType, params) => {
  return new ZodSet({
    valueType,
    minSize: null,
    maxSize: null,
    typeName: ZodFirstPartyTypeKind.ZodSet,
    ...processCreateParams(params)
  });
};
var ZodFunction = class _ZodFunction extends ZodType {
  constructor() {
    super(...arguments);
    this.validate = this.implement;
  }
  _parse(input) {
    const { ctx } = this._processInputParams(input);
    if (ctx.parsedType !== ZodParsedType.function) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.function,
        received: ctx.parsedType
      });
      return INVALID;
    }
    function makeArgsIssue(args, error) {
      return makeIssue({
        data: args,
        path: ctx.path,
        errorMaps: [ctx.common.contextualErrorMap, ctx.schemaErrorMap, getErrorMap(), en_default].filter((x) => !!x),
        issueData: {
          code: ZodIssueCode.invalid_arguments,
          argumentsError: error
        }
      });
    }
    function makeReturnsIssue(returns, error) {
      return makeIssue({
        data: returns,
        path: ctx.path,
        errorMaps: [ctx.common.contextualErrorMap, ctx.schemaErrorMap, getErrorMap(), en_default].filter((x) => !!x),
        issueData: {
          code: ZodIssueCode.invalid_return_type,
          returnTypeError: error
        }
      });
    }
    const params = { errorMap: ctx.common.contextualErrorMap };
    const fn = ctx.data;
    if (this._def.returns instanceof ZodPromise) {
      const me = this;
      return OK(async function(...args) {
        const error = new ZodError([]);
        const parsedArgs = await me._def.args.parseAsync(args, params).catch((e) => {
          error.addIssue(makeArgsIssue(args, e));
          throw error;
        });
        const result = await Reflect.apply(fn, this, parsedArgs);
        const parsedReturns = await me._def.returns._def.type.parseAsync(result, params).catch((e) => {
          error.addIssue(makeReturnsIssue(result, e));
          throw error;
        });
        return parsedReturns;
      });
    } else {
      const me = this;
      return OK(function(...args) {
        const parsedArgs = me._def.args.safeParse(args, params);
        if (!parsedArgs.success) {
          throw new ZodError([makeArgsIssue(args, parsedArgs.error)]);
        }
        const result = Reflect.apply(fn, this, parsedArgs.data);
        const parsedReturns = me._def.returns.safeParse(result, params);
        if (!parsedReturns.success) {
          throw new ZodError([makeReturnsIssue(result, parsedReturns.error)]);
        }
        return parsedReturns.data;
      });
    }
  }
  parameters() {
    return this._def.args;
  }
  returnType() {
    return this._def.returns;
  }
  args(...items) {
    return new _ZodFunction({
      ...this._def,
      args: ZodTuple.create(items).rest(ZodUnknown.create())
    });
  }
  returns(returnType) {
    return new _ZodFunction({
      ...this._def,
      returns: returnType
    });
  }
  implement(func) {
    const validatedFunc = this.parse(func);
    return validatedFunc;
  }
  strictImplement(func) {
    const validatedFunc = this.parse(func);
    return validatedFunc;
  }
  static create(args, returns, params) {
    return new _ZodFunction({
      args: args ? args : ZodTuple.create([]).rest(ZodUnknown.create()),
      returns: returns || ZodUnknown.create(),
      typeName: ZodFirstPartyTypeKind.ZodFunction,
      ...processCreateParams(params)
    });
  }
};
var ZodLazy = class extends ZodType {
  get schema() {
    return this._def.getter();
  }
  _parse(input) {
    const { ctx } = this._processInputParams(input);
    const lazySchema = this._def.getter();
    return lazySchema._parse({ data: ctx.data, path: ctx.path, parent: ctx });
  }
};
ZodLazy.create = (getter, params) => {
  return new ZodLazy({
    getter,
    typeName: ZodFirstPartyTypeKind.ZodLazy,
    ...processCreateParams(params)
  });
};
var ZodLiteral = class extends ZodType {
  _parse(input) {
    if (input.data !== this._def.value) {
      const ctx = this._getOrReturnCtx(input);
      addIssueToContext(ctx, {
        received: ctx.data,
        code: ZodIssueCode.invalid_literal,
        expected: this._def.value
      });
      return INVALID;
    }
    return { status: "valid", value: input.data };
  }
  get value() {
    return this._def.value;
  }
};
ZodLiteral.create = (value, params) => {
  return new ZodLiteral({
    value,
    typeName: ZodFirstPartyTypeKind.ZodLiteral,
    ...processCreateParams(params)
  });
};
function createZodEnum(values, params) {
  return new ZodEnum({
    values,
    typeName: ZodFirstPartyTypeKind.ZodEnum,
    ...processCreateParams(params)
  });
}
var ZodEnum = class _ZodEnum extends ZodType {
  _parse(input) {
    if (typeof input.data !== "string") {
      const ctx = this._getOrReturnCtx(input);
      const expectedValues = this._def.values;
      addIssueToContext(ctx, {
        expected: util.joinValues(expectedValues),
        received: ctx.parsedType,
        code: ZodIssueCode.invalid_type
      });
      return INVALID;
    }
    if (!this._cache) {
      this._cache = new Set(this._def.values);
    }
    if (!this._cache.has(input.data)) {
      const ctx = this._getOrReturnCtx(input);
      const expectedValues = this._def.values;
      addIssueToContext(ctx, {
        received: ctx.data,
        code: ZodIssueCode.invalid_enum_value,
        options: expectedValues
      });
      return INVALID;
    }
    return OK(input.data);
  }
  get options() {
    return this._def.values;
  }
  get enum() {
    const enumValues = {};
    for (const val of this._def.values) {
      enumValues[val] = val;
    }
    return enumValues;
  }
  get Values() {
    const enumValues = {};
    for (const val of this._def.values) {
      enumValues[val] = val;
    }
    return enumValues;
  }
  get Enum() {
    const enumValues = {};
    for (const val of this._def.values) {
      enumValues[val] = val;
    }
    return enumValues;
  }
  extract(values, newDef = this._def) {
    return _ZodEnum.create(values, {
      ...this._def,
      ...newDef
    });
  }
  exclude(values, newDef = this._def) {
    return _ZodEnum.create(this.options.filter((opt) => !values.includes(opt)), {
      ...this._def,
      ...newDef
    });
  }
};
ZodEnum.create = createZodEnum;
var ZodNativeEnum = class extends ZodType {
  _parse(input) {
    const nativeEnumValues = util.getValidEnumValues(this._def.values);
    const ctx = this._getOrReturnCtx(input);
    if (ctx.parsedType !== ZodParsedType.string && ctx.parsedType !== ZodParsedType.number) {
      const expectedValues = util.objectValues(nativeEnumValues);
      addIssueToContext(ctx, {
        expected: util.joinValues(expectedValues),
        received: ctx.parsedType,
        code: ZodIssueCode.invalid_type
      });
      return INVALID;
    }
    if (!this._cache) {
      this._cache = new Set(util.getValidEnumValues(this._def.values));
    }
    if (!this._cache.has(input.data)) {
      const expectedValues = util.objectValues(nativeEnumValues);
      addIssueToContext(ctx, {
        received: ctx.data,
        code: ZodIssueCode.invalid_enum_value,
        options: expectedValues
      });
      return INVALID;
    }
    return OK(input.data);
  }
  get enum() {
    return this._def.values;
  }
};
ZodNativeEnum.create = (values, params) => {
  return new ZodNativeEnum({
    values,
    typeName: ZodFirstPartyTypeKind.ZodNativeEnum,
    ...processCreateParams(params)
  });
};
var ZodPromise = class extends ZodType {
  unwrap() {
    return this._def.type;
  }
  _parse(input) {
    const { ctx } = this._processInputParams(input);
    if (ctx.parsedType !== ZodParsedType.promise && ctx.common.async === false) {
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.promise,
        received: ctx.parsedType
      });
      return INVALID;
    }
    const promisified = ctx.parsedType === ZodParsedType.promise ? ctx.data : Promise.resolve(ctx.data);
    return OK(promisified.then((data) => {
      return this._def.type.parseAsync(data, {
        path: ctx.path,
        errorMap: ctx.common.contextualErrorMap
      });
    }));
  }
};
ZodPromise.create = (schema, params) => {
  return new ZodPromise({
    type: schema,
    typeName: ZodFirstPartyTypeKind.ZodPromise,
    ...processCreateParams(params)
  });
};
var ZodEffects = class extends ZodType {
  innerType() {
    return this._def.schema;
  }
  sourceType() {
    return this._def.schema._def.typeName === ZodFirstPartyTypeKind.ZodEffects ? this._def.schema.sourceType() : this._def.schema;
  }
  _parse(input) {
    const { status, ctx } = this._processInputParams(input);
    const effect = this._def.effect || null;
    const checkCtx = {
      addIssue: (arg) => {
        addIssueToContext(ctx, arg);
        if (arg.fatal) {
          status.abort();
        } else {
          status.dirty();
        }
      },
      get path() {
        return ctx.path;
      }
    };
    checkCtx.addIssue = checkCtx.addIssue.bind(checkCtx);
    if (effect.type === "preprocess") {
      const processed = effect.transform(ctx.data, checkCtx);
      if (ctx.common.async) {
        return Promise.resolve(processed).then(async (processed2) => {
          if (status.value === "aborted")
            return INVALID;
          const result = await this._def.schema._parseAsync({
            data: processed2,
            path: ctx.path,
            parent: ctx
          });
          if (result.status === "aborted")
            return INVALID;
          if (result.status === "dirty")
            return DIRTY(result.value);
          if (status.value === "dirty")
            return DIRTY(result.value);
          return result;
        });
      } else {
        if (status.value === "aborted")
          return INVALID;
        const result = this._def.schema._parseSync({
          data: processed,
          path: ctx.path,
          parent: ctx
        });
        if (result.status === "aborted")
          return INVALID;
        if (result.status === "dirty")
          return DIRTY(result.value);
        if (status.value === "dirty")
          return DIRTY(result.value);
        return result;
      }
    }
    if (effect.type === "refinement") {
      const executeRefinement = (acc) => {
        const result = effect.refinement(acc, checkCtx);
        if (ctx.common.async) {
          return Promise.resolve(result);
        }
        if (result instanceof Promise) {
          throw new Error("Async refinement encountered during synchronous parse operation. Use .parseAsync instead.");
        }
        return acc;
      };
      if (ctx.common.async === false) {
        const inner = this._def.schema._parseSync({
          data: ctx.data,
          path: ctx.path,
          parent: ctx
        });
        if (inner.status === "aborted")
          return INVALID;
        if (inner.status === "dirty")
          status.dirty();
        executeRefinement(inner.value);
        return { status: status.value, value: inner.value };
      } else {
        return this._def.schema._parseAsync({ data: ctx.data, path: ctx.path, parent: ctx }).then((inner) => {
          if (inner.status === "aborted")
            return INVALID;
          if (inner.status === "dirty")
            status.dirty();
          return executeRefinement(inner.value).then(() => {
            return { status: status.value, value: inner.value };
          });
        });
      }
    }
    if (effect.type === "transform") {
      if (ctx.common.async === false) {
        const base = this._def.schema._parseSync({
          data: ctx.data,
          path: ctx.path,
          parent: ctx
        });
        if (!isValid(base))
          return INVALID;
        const result = effect.transform(base.value, checkCtx);
        if (result instanceof Promise) {
          throw new Error(`Asynchronous transform encountered during synchronous parse operation. Use .parseAsync instead.`);
        }
        return { status: status.value, value: result };
      } else {
        return this._def.schema._parseAsync({ data: ctx.data, path: ctx.path, parent: ctx }).then((base) => {
          if (!isValid(base))
            return INVALID;
          return Promise.resolve(effect.transform(base.value, checkCtx)).then((result) => ({
            status: status.value,
            value: result
          }));
        });
      }
    }
    util.assertNever(effect);
  }
};
ZodEffects.create = (schema, effect, params) => {
  return new ZodEffects({
    schema,
    typeName: ZodFirstPartyTypeKind.ZodEffects,
    effect,
    ...processCreateParams(params)
  });
};
ZodEffects.createWithPreprocess = (preprocess, schema, params) => {
  return new ZodEffects({
    schema,
    effect: { type: "preprocess", transform: preprocess },
    typeName: ZodFirstPartyTypeKind.ZodEffects,
    ...processCreateParams(params)
  });
};
var ZodOptional = class extends ZodType {
  _parse(input) {
    const parsedType = this._getType(input);
    if (parsedType === ZodParsedType.undefined) {
      return OK(void 0);
    }
    return this._def.innerType._parse(input);
  }
  unwrap() {
    return this._def.innerType;
  }
};
ZodOptional.create = (type, params) => {
  return new ZodOptional({
    innerType: type,
    typeName: ZodFirstPartyTypeKind.ZodOptional,
    ...processCreateParams(params)
  });
};
var ZodNullable = class extends ZodType {
  _parse(input) {
    const parsedType = this._getType(input);
    if (parsedType === ZodParsedType.null) {
      return OK(null);
    }
    return this._def.innerType._parse(input);
  }
  unwrap() {
    return this._def.innerType;
  }
};
ZodNullable.create = (type, params) => {
  return new ZodNullable({
    innerType: type,
    typeName: ZodFirstPartyTypeKind.ZodNullable,
    ...processCreateParams(params)
  });
};
var ZodDefault = class extends ZodType {
  _parse(input) {
    const { ctx } = this._processInputParams(input);
    let data = ctx.data;
    if (ctx.parsedType === ZodParsedType.undefined) {
      data = this._def.defaultValue();
    }
    return this._def.innerType._parse({
      data,
      path: ctx.path,
      parent: ctx
    });
  }
  removeDefault() {
    return this._def.innerType;
  }
};
ZodDefault.create = (type, params) => {
  return new ZodDefault({
    innerType: type,
    typeName: ZodFirstPartyTypeKind.ZodDefault,
    defaultValue: typeof params.default === "function" ? params.default : () => params.default,
    ...processCreateParams(params)
  });
};
var ZodCatch = class extends ZodType {
  _parse(input) {
    const { ctx } = this._processInputParams(input);
    const newCtx = {
      ...ctx,
      common: {
        ...ctx.common,
        issues: []
      }
    };
    const result = this._def.innerType._parse({
      data: newCtx.data,
      path: newCtx.path,
      parent: {
        ...newCtx
      }
    });
    if (isAsync(result)) {
      return result.then((result2) => {
        return {
          status: "valid",
          value: result2.status === "valid" ? result2.value : this._def.catchValue({
            get error() {
              return new ZodError(newCtx.common.issues);
            },
            input: newCtx.data
          })
        };
      });
    } else {
      return {
        status: "valid",
        value: result.status === "valid" ? result.value : this._def.catchValue({
          get error() {
            return new ZodError(newCtx.common.issues);
          },
          input: newCtx.data
        })
      };
    }
  }
  removeCatch() {
    return this._def.innerType;
  }
};
ZodCatch.create = (type, params) => {
  return new ZodCatch({
    innerType: type,
    typeName: ZodFirstPartyTypeKind.ZodCatch,
    catchValue: typeof params.catch === "function" ? params.catch : () => params.catch,
    ...processCreateParams(params)
  });
};
var ZodNaN = class extends ZodType {
  _parse(input) {
    const parsedType = this._getType(input);
    if (parsedType !== ZodParsedType.nan) {
      const ctx = this._getOrReturnCtx(input);
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.nan,
        received: ctx.parsedType
      });
      return INVALID;
    }
    return { status: "valid", value: input.data };
  }
};
ZodNaN.create = (params) => {
  return new ZodNaN({
    typeName: ZodFirstPartyTypeKind.ZodNaN,
    ...processCreateParams(params)
  });
};
var BRAND = Symbol("zod_brand");
var ZodBranded = class extends ZodType {
  _parse(input) {
    const { ctx } = this._processInputParams(input);
    const data = ctx.data;
    return this._def.type._parse({
      data,
      path: ctx.path,
      parent: ctx
    });
  }
  unwrap() {
    return this._def.type;
  }
};
var ZodPipeline = class _ZodPipeline extends ZodType {
  _parse(input) {
    const { status, ctx } = this._processInputParams(input);
    if (ctx.common.async) {
      const handleAsync = async () => {
        const inResult = await this._def.in._parseAsync({
          data: ctx.data,
          path: ctx.path,
          parent: ctx
        });
        if (inResult.status === "aborted")
          return INVALID;
        if (inResult.status === "dirty") {
          status.dirty();
          return DIRTY(inResult.value);
        } else {
          return this._def.out._parseAsync({
            data: inResult.value,
            path: ctx.path,
            parent: ctx
          });
        }
      };
      return handleAsync();
    } else {
      const inResult = this._def.in._parseSync({
        data: ctx.data,
        path: ctx.path,
        parent: ctx
      });
      if (inResult.status === "aborted")
        return INVALID;
      if (inResult.status === "dirty") {
        status.dirty();
        return {
          status: "dirty",
          value: inResult.value
        };
      } else {
        return this._def.out._parseSync({
          data: inResult.value,
          path: ctx.path,
          parent: ctx
        });
      }
    }
  }
  static create(a, b) {
    return new _ZodPipeline({
      in: a,
      out: b,
      typeName: ZodFirstPartyTypeKind.ZodPipeline
    });
  }
};
var ZodReadonly = class extends ZodType {
  _parse(input) {
    const result = this._def.innerType._parse(input);
    const freeze = (data) => {
      if (isValid(data)) {
        data.value = Object.freeze(data.value);
      }
      return data;
    };
    return isAsync(result) ? result.then((data) => freeze(data)) : freeze(result);
  }
  unwrap() {
    return this._def.innerType;
  }
};
ZodReadonly.create = (type, params) => {
  return new ZodReadonly({
    innerType: type,
    typeName: ZodFirstPartyTypeKind.ZodReadonly,
    ...processCreateParams(params)
  });
};
function cleanParams(params, data) {
  const p = typeof params === "function" ? params(data) : typeof params === "string" ? { message: params } : params;
  const p2 = typeof p === "string" ? { message: p } : p;
  return p2;
}
function custom(check, _params = {}, fatal) {
  if (check)
    return ZodAny.create().superRefine((data, ctx) => {
      const r = check(data);
      if (r instanceof Promise) {
        return r.then((r2) => {
          if (!r2) {
            const params = cleanParams(_params, data);
            const _fatal = params.fatal ?? fatal ?? true;
            ctx.addIssue({ code: "custom", ...params, fatal: _fatal });
          }
        });
      }
      if (!r) {
        const params = cleanParams(_params, data);
        const _fatal = params.fatal ?? fatal ?? true;
        ctx.addIssue({ code: "custom", ...params, fatal: _fatal });
      }
      return;
    });
  return ZodAny.create();
}
var late = {
  object: ZodObject.lazycreate
};
var ZodFirstPartyTypeKind;
(function(ZodFirstPartyTypeKind2) {
  ZodFirstPartyTypeKind2["ZodString"] = "ZodString";
  ZodFirstPartyTypeKind2["ZodNumber"] = "ZodNumber";
  ZodFirstPartyTypeKind2["ZodNaN"] = "ZodNaN";
  ZodFirstPartyTypeKind2["ZodBigInt"] = "ZodBigInt";
  ZodFirstPartyTypeKind2["ZodBoolean"] = "ZodBoolean";
  ZodFirstPartyTypeKind2["ZodDate"] = "ZodDate";
  ZodFirstPartyTypeKind2["ZodSymbol"] = "ZodSymbol";
  ZodFirstPartyTypeKind2["ZodUndefined"] = "ZodUndefined";
  ZodFirstPartyTypeKind2["ZodNull"] = "ZodNull";
  ZodFirstPartyTypeKind2["ZodAny"] = "ZodAny";
  ZodFirstPartyTypeKind2["ZodUnknown"] = "ZodUnknown";
  ZodFirstPartyTypeKind2["ZodNever"] = "ZodNever";
  ZodFirstPartyTypeKind2["ZodVoid"] = "ZodVoid";
  ZodFirstPartyTypeKind2["ZodArray"] = "ZodArray";
  ZodFirstPartyTypeKind2["ZodObject"] = "ZodObject";
  ZodFirstPartyTypeKind2["ZodUnion"] = "ZodUnion";
  ZodFirstPartyTypeKind2["ZodDiscriminatedUnion"] = "ZodDiscriminatedUnion";
  ZodFirstPartyTypeKind2["ZodIntersection"] = "ZodIntersection";
  ZodFirstPartyTypeKind2["ZodTuple"] = "ZodTuple";
  ZodFirstPartyTypeKind2["ZodRecord"] = "ZodRecord";
  ZodFirstPartyTypeKind2["ZodMap"] = "ZodMap";
  ZodFirstPartyTypeKind2["ZodSet"] = "ZodSet";
  ZodFirstPartyTypeKind2["ZodFunction"] = "ZodFunction";
  ZodFirstPartyTypeKind2["ZodLazy"] = "ZodLazy";
  ZodFirstPartyTypeKind2["ZodLiteral"] = "ZodLiteral";
  ZodFirstPartyTypeKind2["ZodEnum"] = "ZodEnum";
  ZodFirstPartyTypeKind2["ZodEffects"] = "ZodEffects";
  ZodFirstPartyTypeKind2["ZodNativeEnum"] = "ZodNativeEnum";
  ZodFirstPartyTypeKind2["ZodOptional"] = "ZodOptional";
  ZodFirstPartyTypeKind2["ZodNullable"] = "ZodNullable";
  ZodFirstPartyTypeKind2["ZodDefault"] = "ZodDefault";
  ZodFirstPartyTypeKind2["ZodCatch"] = "ZodCatch";
  ZodFirstPartyTypeKind2["ZodPromise"] = "ZodPromise";
  ZodFirstPartyTypeKind2["ZodBranded"] = "ZodBranded";
  ZodFirstPartyTypeKind2["ZodPipeline"] = "ZodPipeline";
  ZodFirstPartyTypeKind2["ZodReadonly"] = "ZodReadonly";
})(ZodFirstPartyTypeKind || (ZodFirstPartyTypeKind = {}));
var instanceOfType = (cls, params = {
  message: `Input not instance of ${cls.name}`
}) => custom((data) => data instanceof cls, params);
var stringType = ZodString.create;
var numberType = ZodNumber.create;
var nanType = ZodNaN.create;
var bigIntType = ZodBigInt.create;
var booleanType = ZodBoolean.create;
var dateType = ZodDate.create;
var symbolType = ZodSymbol.create;
var undefinedType = ZodUndefined.create;
var nullType = ZodNull.create;
var anyType = ZodAny.create;
var unknownType = ZodUnknown.create;
var neverType = ZodNever.create;
var voidType = ZodVoid.create;
var arrayType = ZodArray.create;
var objectType = ZodObject.create;
var strictObjectType = ZodObject.strictCreate;
var unionType = ZodUnion.create;
var discriminatedUnionType = ZodDiscriminatedUnion.create;
var intersectionType = ZodIntersection.create;
var tupleType = ZodTuple.create;
var recordType = ZodRecord.create;
var mapType = ZodMap.create;
var setType = ZodSet.create;
var functionType = ZodFunction.create;
var lazyType = ZodLazy.create;
var literalType = ZodLiteral.create;
var enumType = ZodEnum.create;
var nativeEnumType = ZodNativeEnum.create;
var promiseType = ZodPromise.create;
var effectsType = ZodEffects.create;
var optionalType = ZodOptional.create;
var nullableType = ZodNullable.create;
var preprocessType = ZodEffects.createWithPreprocess;
var pipelineType = ZodPipeline.create;
var ostring = () => stringType().optional();
var onumber = () => numberType().optional();
var oboolean = () => booleanType().optional();
var coerce = {
  string: (arg) => ZodString.create({ ...arg, coerce: true }),
  number: (arg) => ZodNumber.create({ ...arg, coerce: true }),
  boolean: (arg) => ZodBoolean.create({
    ...arg,
    coerce: true
  }),
  bigint: (arg) => ZodBigInt.create({ ...arg, coerce: true }),
  date: (arg) => ZodDate.create({ ...arg, coerce: true })
};
var NEVER = INVALID;

// ../../tmp/bazaar-calc-review/packages/shared/dist/service/endpoints.js
var defined = (v) => Object.fromEntries(Object.entries(v).filter(([, x]) => x !== void 0));
var num = (min, max) => external_exports.preprocess((v) => {
  if (v === "" || v == null)
    return void 0;
  const n = Number(v);
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : void 0;
}, external_exports.number().optional());
var SettingsSchema = external_exports.object({
  coins: num(0, 1e13),
  bazaarFlipperLevel: num(0, 2),
  checkIntervalMin: num(0.5, 240),
  hoursPerDay: num(0.1, 24),
  dailyLimit: num(0, 1e12),
  attention: num(0.05, 1),
  craftsPerHourMax: num(0, 1e6),
  unknownCompetitionShare: num(0.01, 1),
  minUnitsPerHour: num(0, 1e9),
  includeFlagged: external_exports.coerce.boolean(),
  pingMs: num(0, 5e3),
  clickDelayMs: num(0, 1e4),
  typingMs: num(0, 3e4)
}).partial().transform((v) => ({ ...DEFAULT_SETTINGS, ...defined(v) }));
var ProfileSchema = external_exports.object({
  hotmTier: num(0, 10),
  quickForgeLevel: num(0, 20),
  enchantingLevel: num(0, 60),
  xpLevels: num(0, 1e4),
  collections: external_exports.record(external_exports.string(), num(0, 100)),
  slayers: external_exports.record(external_exports.string(), num(0, 10)),
  reputation: external_exports.record(external_exports.string(), num(0, 1e6)),
  coleMoltenForge: external_exports.coerce.boolean(),
  ignoreRequirements: external_exports.coerce.boolean()
}).partial().transform((v) => ({ ...DEFAULT_PROFILE, ...defined(v) }));
var FilterSchema = external_exports.object({
  q: external_exports.string().max(100).optional(),
  minCoinsH: num(0, 1e12).optional(),
  minProfit: num(-1e12, 1e12).optional(),
  minMargin: num(-10, 100).optional(),
  maxCapital: num(0, 1e13).optional(),
  maxOrders: num(0, 28).optional(),
  requirementsMet: external_exports.coerce.boolean().optional(),
  noFlags: external_exports.coerce.boolean().optional(),
  buyModes: external_exports.array(external_exports.enum(["instant", "order"])).optional(),
  sellModes: external_exports.array(external_exports.enum(["instant", "offer", "ah_reference"])).optional(),
  sort: external_exports.enum(["coinsH", "profitPerUnit", "marginPct", "unitsH", "capitalUsed"]).default("coinsH"),
  limit: num(1, 500).default(100),
  offset: num(0, 1e6).default(0),
  includeAhForge: external_exports.coerce.boolean().optional(),
  profitableOnly: external_exports.coerce.boolean().optional()
}).partial();
var CalcBody = external_exports.object({ settings: SettingsSchema.optional(), profile: ProfileSchema.optional(), filters: FilterSchema.optional() });
var PlanBody = CalcBody.extend({ options: external_exports.object({ kinds: external_exports.array(external_exports.enum(["bazaar", "craft", "book", "forge"])).optional(), requireMet: external_exports.boolean().optional(), maxPicks: external_exports.number().int().min(1).max(50).optional() }).optional() });

// <stdin>
function competition(c) {
  const n = Number(c?.n ?? 0), secs = Number(c?.secs ?? 0);
  if (!c || n < 15 || secs <= 0)
    return { undercutBuyH: null, undercutSellH: null, observedBuyFlowH: null, observedSellFlowH: null };
  const per = secs / n, rate = (k) => k <= 0 ? 0 : -Math.log(1 - Math.min(0.99, k / n)) * (3600 / per);
  return { undercutBuyH: rate(Number(c.ob)), undercutSellH: rate(Number(c.uc)), observedBuyFlowH: Number(c.br) / (secs / 3600), observedSellFlowH: Number(c.ar) / (secs / 3600) };
}
function delists(c, k) {
  const span = Number(k?.span ?? 0), watched = Number(c?.secs ?? 0) / 3600;
  if (!c || !k || span < 3 || watched < 3)
    return null;
  const trades = (w1, w2) => Math.max((w2 - w1 + w1 / 168 * span) * (watched / span), Math.max(w1, w2) / 168 * watched);
  return {
    hours: watched,
    bidRemoved: Number(c.br),
    bidTrades: trades(Number(k.s1), Number(k.s2)),
    // buy orders are hit by instant SELLS
    askRemoved: Number(c.ar),
    askTrades: trades(Number(k.b1), Number(k.b2))
  };
}
export {
  DEFAULT_PROFILE,
  DEFAULT_SETTINGS,
  TopTracker,
  actionSeconds,
  assembleMarket,
  at,
  bookFlow,
  booksNeeded,
  buyLeg,
  combineXpCost,
  competition,
  curve,
  degradedBazaar,
  delists,
  enchantRules,
  evaluate,
  parseBookId,
  quotesFromBazaar,
  sellLeg,
  seriousFlags,
  summarizeTop,
  toLevels,
  validateBazaar
};
