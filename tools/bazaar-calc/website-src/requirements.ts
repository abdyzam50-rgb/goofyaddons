// Recipe / action requirements. Parsed from NEU `crafttext` ("Requires: Diamond IV", "Requires: Gemstone X & HotM 5",
// "Requires Coal IV"), `slayer_req` ("WOLF_6") and `reputation_req` ("BARBARIAN:7500").

export type Requirement =
  | { type: "catacombs"; level: number; text: string }
  | { type: "dungeon_floor"; name: string; text: string }
  | { type: "mutation"; id: string; text: string }
  | { type: "analyzer"; level: number; text: string }
  | { type: "collection"; name: string; tier: number; text: string }
  | { type: "hotm"; tier: number; text: string }
  | { type: "slayer"; name: string; level: number; text: string }
  | { type: "reputation"; faction: string; amount: number; text: string }
  | { type: "enchanting"; level: number; text: string }
  | { type: "skill"; name: string; level: number; text: string } // Taming for Kat, Foraging for Galatea (wiki)
  | { type: "xp_levels"; levels: number; text: string }
  | { type: "forge"; text: string }
  | { type: "unverified"; text: string };

export interface Profile {
  catacombsLevel?: number | null;
  dungeonCompletions?: Record<string, number>;
  inspectedMutations?: string[];
  cropAnalyzerMilestone?: number;
  hotmTier: number;
  quickForgeLevel: number;
  enchantingLevel: number;
  collections: Record<string, number>;   // collection name (e.g. "Diamond") -> unlocked tier
  slayers: Record<string, number>;       // "Zombie" / "Wolf" / ... -> level
  skills: Record<string, number>;        // "Taming" / "Foraging" -> level
  reputation: Record<string, number>;    // "Barbarian" -> reputation
  xpLevels: number;
  coleMoltenForge: boolean;              // auto-set from the current mayor when known
  quadTaxes: boolean;                    // Derpy's QUAD TAXES!!! (bazaar tax x4): auto-set from the current mayor
  npcShoppingSpree: boolean;             // Diaz's Shopping Spree (NPC buy limits x10): auto-set from the current mayor
  ignoreRequirements: boolean;           // show everything regardless of unlocks
}

export const DEFAULT_PROFILE: Profile = {
  hotmTier: 0, quickForgeLevel: 0, enchantingLevel: 0, collections: {}, slayers: {}, skills: {}, reputation: {},
  xpLevels: 0, coleMoltenForge: false, quadTaxes: false, npcShoppingSpree: false, ignoreRequirements: true,
};

const ROMAN: Record<string, number> = { I: 1, V: 5, X: 10, L: 50, C: 100 };
export function roman(s: string): number | null {
  if (/^\d+$/.test(s)) return Number(s);
  if (!/^[IVXLC]+$/.test(s)) return null;
  let total = 0;
  for (let i = 0; i < s.length; i++) {
    const v = ROMAN[s[i]!]!, next = ROMAN[s[i + 1] ?? ""] ?? 0;
    total += v < next ? -v : v;
  }
  return total;
}

const SLAYER_NAMES: Record<string, string> = { ZOMBIE: "Zombie", SPIDER: "Spider", WOLF: "Wolf", EMAN: "Enderman", ENDERMAN: "Enderman", BLAZE: "Blaze", VAMPIRE: "Vampire" };

export function parseCraftText(text: string | null | undefined): Requirement[] {
  if (!text) return [];
  const body = text.replace(/^Requires:?\s*/i, "").trim();
  if (!body) return [];
  return body.split(/\s*&\s*|\s*,\s*/).filter(Boolean).map((part): Requirement => {
    let gate = /^(?:Catacombs|Cata|Dungeoneering) (?:level )?([IVXLC\d]+)$/i.exec(part);
    if(gate)return {type:"catacombs",level:roman(gate[1]!)??0,text:part};
    gate=/^(Master )?(?:The )?Catacombs Floor ([IVXLC\d]+) (?:Completion|completed)$/i.exec(part.replace(/^Master Mode /i,'Master '));
    if(gate)return {type:"dungeon_floor",name:`${gate[1]?'Master ':''}Catacombs Floor ${roman(gate[2]!)}`,text:part};
    gate=/^Mutation ([A-Z0-9_]+) (?:inspected|analyzed)$/i.exec(part);
    if(gate)return {type:"mutation",id:gate[1]!.toUpperCase(),text:part};
    gate=/^Crop Analyzer Milestone ([IVXLC\d]+)$/i.exec(part);
    if(gate)return {type:"analyzer",level:roman(gate[1]!)??0,text:part};
    let m = /^HotM\s+(\d+)$/i.exec(part);
    if (m) return { type: "hotm", tier: Number(m[1]), text: part };
    m = /^(.+?) Slayer\s+([IVXLC\d]+)$/.exec(part);
    if (m) return { type: "slayer", name: m[1]!, level: roman(m[2]!) ?? 0, text: part };
    m = /^(.+?)\s+([IVXLC]+|\d+)$/.exec(part);
    if (m && roman(m[2]!) != null) return { type: "collection", name: m[1]!, tier: roman(m[2]!)!, text: `${part} collection` };
    return { type: "unverified", text: part };
  });
}

export function parseSlayerReq(req: string | null | undefined): Requirement[] {
  if (!req) return [];
  const m = /^([A-Z]+)_(\d+)$/.exec(req);
  if (!m) return [{ type: "unverified", text: `slayer ${req}` }];
  const name = SLAYER_NAMES[m[1]!] ?? m[1]!;
  return [{ type: "slayer", name, level: Number(m[2]), text: `${name} Slayer ${m[2]}` }];
}

export function parseReputationReq(req: string | null | undefined): Requirement[] {
  if (!req) return [];
  const m = /^([A-Z_]+):(\d+)$/.exec(req);
  if (!m) return [{ type: "unverified", text: `reputation ${req}` }];
  const faction = m[1]!.charAt(0) + m[1]!.slice(1).toLowerCase();
  return [{ type: "reputation", faction, amount: Number(m[2]), text: `${faction} reputation ${m[2]}` }];
}

/** true = met, false = not met, null = cannot be checked (unverified text or unknown profile value). */
export function isMet(r: Requirement, p: Profile): boolean | null {
  switch (r.type) {
    case "catacombs": return p.catacombsLevel==null?null:p.catacombsLevel>=r.level;
    case "dungeon_floor": return p.dungeonCompletions?.[r.name]==null?null:p.dungeonCompletions[r.name]!>=1;
    case "mutation": return p.inspectedMutations==null?null:p.inspectedMutations.includes(r.id);
    case "analyzer": return p.cropAnalyzerMilestone==null?null:p.cropAnalyzerMilestone>=r.level;
    case "hotm": return p.hotmTier >= r.tier;
    case "collection": {
      const have = p.collections[r.name];
      return have == null ? false : have >= r.tier;
    }
    case "slayer": return (p.slayers[r.name] ?? 0) >= r.level;
    case "skill": return (p.skills?.[r.name] ?? 0) >= r.level;
    case "reputation": return (p.reputation[r.faction] ?? 0) >= r.amount;
    case "enchanting": return p.enchantingLevel >= r.level;
    case "xp_levels": return p.xpLevels >= r.levels;
    case "forge": return p.hotmTier >= 2;
    default: return null;
  }
}

export function unmet(reqs: Requirement[], p: Profile): Requirement[] {
  if (p.ignoreRequirements) return [];
  return reqs.filter(r => isMet(r, p) !== true);
}

export function dedupeRequirements(reqs: Requirement[]): Requirement[] {
  const seen = new Map<string, Requirement>();
  for (const r of reqs) {
    const key = r.type === "collection" ? `c:${r.name}` : r.type === "hotm" ? "hotm" : r.type === "slayer" ? `s:${r.name}` : r.type === "skill" ? `k:${r.name}`
      : r.type === "enchanting" ? "ench" : r.type === "xp_levels" ? "xp" : r.text;
    const prev = seen.get(key);
    const rank = (x: Requirement) => ("tier" in x ? x.tier : "level" in x ? x.level : "levels" in x ? x.levels : "amount" in x ? x.amount : 0);
    if (!prev || rank(r) > rank(prev)) seen.set(key, r);
  }
  return [...seen.values()];
}

// Account progression controls trade access; a public quote cannot establish it.
export function tradeRequirements(id:string):Requirement[] {
 if(/^ESSENCE_(WITHER|UNDEAD|DRAGON|SPIDER|ICE|CRIMSON|GOLD|DIAMOND)$/.test(id))return [{type:"catacombs",level:20,text:"Catacombs 20 for essence trading"}];
 const mutations=new Set(["ALL_IN_ALOE", "ASHWREATH", "BLASTBERRY", "CHEESEBITE", "CHLORONITE", "CHOCOBERRY", "CHOCONUT", "CHORUS_FRUIT", "CINDERSHADE", "COALROOT", "CREAMBLOOM", "DEVOURER", "DO_NOT_EAT_SHROOM", "DUSKBLOOM", "DUSTGRAIN", "FLESHTRAP", "GLASSCORN", "GLOOMGOURD", "GODSEED", "JERRYFLOWER", "LONELILY", "MAGIC_JELLYBEAN", "NOCTILUME", "PHANTOMLEAF", "PLANTBOY_ADVANCE", "PUFFERCLOUD", "SCOURROOT", "SHADEVINE", "SHELLFRUIT", "SNOOZLING", "SOGGYBUD", "STARTLEVINE", "STOPLIGHT_PETAL", "THORNSHADE", "THUNDERLING", "TIMESTALK", "TURTLELLINI", "VEILSHROOM", "WITHERBLOOM", "ZOMBUD"]);
 return mutations.has(id)?[{type:"mutation",id,text:`Mutation ${id} inspected`}]:[];
}
