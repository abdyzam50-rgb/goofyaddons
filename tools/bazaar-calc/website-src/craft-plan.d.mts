export interface Recipe {key:string;kind:string;outputId:string;outputCount:number;ingredients:Record<string,number>;requirement:string}
export interface Catalog {names:Record<string,string>;recipes:Recipe[];coverage?:{sourceCraftRows:number;importedCraftRows:number;unsupportedCraftRows:number;retainedCraftRows:number};unsupportedCrafts?:{key:string;outputId:string;name:string;reason:string}[]}
export interface CraftRow {key:string;output:string;name:string;batches:number;units:number;venue:string;capital:number|null;profit:number|null;score:number;eligible:boolean;reason:string;requirement:string;purchases:Record<string,number>;steps:{output:string;batches:number;units:number}[];binPrice:number|null;sourceAt:number|null}
export function fresh(at:unknown,now:number,ttl?:number):boolean;
export function planCrafts(options:{catalog:Catalog;market:unknown;ah:unknown;now:number;budget:number;minProfit:number;maxBatches:number;tax:number;requirements:Record<string,string[]>}):CraftRow[];
