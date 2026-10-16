export interface Recipe {key:string;kind:string;outputId:string;outputCount:number;ingredients:Record<string,number>;requirement:string;coins?:number}
export interface Catalog {names:Record<string,string>;recipes:Recipe[];coverage?:{sourceCraftRows:number;importedCraftRows:number;unsupportedCraftRows:number;retainedCraftRows:number};unsupportedCrafts?:{key:string;outputId:string;name:string;reason:string}[];unstackable?:string[]}
export interface CraftRow {pricing:'HISTORY'|'LIVE'|'UNPRICED'|'RESEARCH';strategy:string|null;buySell:string|null;coinsPerHour:number|null;inventorySlots?:number;key:string;output:string;name:string;batches:number;units:number;venue:string;capital:number|null;profit:number|null;score:number;eligible:boolean;reason:string;requirement:string;purchases:Record<string,number>;steps:{output:string;batches:number;units:number}[];binPrice:number|null;sourceAt:number|null}
export function fresh(at:unknown,now:number,ttl?:number):boolean;
export function planCrafts(options:{catalog:Catalog;market:unknown;ah:unknown;history?:unknown;now:number;budget:number;minProfit:number;maxBatches:number;tax:number;requirements:Record<string,string[]>;venue?:'BOTH'|'BAZAAR'|'AH';strategy?:string;freeSlots?:number}):CraftRow[];
export const STRATEGIES:readonly string[];
export function strategyLabel(strategy:string):string;
export function indexRecipes(recipes:Recipe[]):Map<string,Recipe[]>;
export function dependencies(byOutput:Map<string,Recipe[]>,inputs:Iterable<string>):Set<string>;
