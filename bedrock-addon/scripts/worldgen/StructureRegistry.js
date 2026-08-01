import { world } from "@minecraft/server";

export const StructureCategory = Object.freeze({DEFAULT:"DEFAULT",SKY:"SKY",SURFACE_SKY:"SURFACE_SKY",SMALL_SKY:"SMALL_SKY",HAMLET_SKY:"HAMLET_SKY",GROUND_VILLAGE:"GROUND_VILLAGE",STRONGHOLD:"STRONGHOLD",UNDERGROUND:"UNDERGROUND",WATER:"WATER"});

// Families below are the families actually present in the copied vanilla structure
// pack. Strongholds/mineshafts remain native because they are not template families.
const TEMPLATE_FAMILIES=Object.freeze(["village/","pillageroutpost/","ruined_portal/","shipwreck/","ruin/","underwater_ruin/","igloo/","trail_ruins/","ancient_city/","trial_chambers/","endcity/","mansion/","bastion/","coralcrust/","fossils/","nether_fossils/"]);
const CATEGORY_RULES=Object.freeze([{prefix:"village/",category:StructureCategory.GROUND_VILLAGE},{prefix:"pillageroutpost/",category:StructureCategory.SURFACE_SKY},{prefix:"ruined_portal/",category:StructureCategory.SMALL_SKY},{prefix:"igloo/",category:StructureCategory.SMALL_SKY},{prefix:"shipwreck/",category:StructureCategory.WATER},{prefix:"ruin/",category:StructureCategory.WATER},{prefix:"underwater_ruin/",category:StructureCategory.WATER},{prefix:"trail_ruins/",category:StructureCategory.GROUND_VILLAGE},{prefix:"ancient_city/",category:StructureCategory.UNDERGROUND},{prefix:"trial_chambers/",category:StructureCategory.UNDERGROUND},{prefix:"endcity/",category:StructureCategory.SKY},{prefix:"mansion/",category:StructureCategory.SKY},{prefix:"bastion/",category:StructureCategory.SKY},{prefix:"coralcrust/",category:StructureCategory.WATER},{prefix:"fossils/",category:StructureCategory.UNDERGROUND},{prefix:"nether_fossils/",category:StructureCategory.UNDERGROUND}]);
const NATIVE_FAMILIES=Object.freeze({"minecraft:stronghold":StructureCategory.STRONGHOLD,stronghold:StructureCategory.STRONGHOLD,"minecraft:mineshaft":StructureCategory.UNDERGROUND,mineshaft:StructureCategory.UNDERGROUND});
function normalize(id){return String(id??"").replace(/\\/g,"/").replace(/\.mcstructure$/i,"").replace(/^.*?:/,"");}
function categoryFor(id){const raw=String(id??""),native=NATIVE_FAMILIES[raw]??NATIVE_FAMILIES[normalize(raw)];if(native)return native;const n=normalize(raw);return CATEGORY_RULES.find(r=>n.startsWith(r.prefix))?.category??StructureCategory.DEFAULT;}
function familyFor(id){const n=normalize(id);return TEMPLATE_FAMILIES.find(prefix=>n.startsWith(prefix))??null;}
function isActualPackStructure(raw){const n=normalize(raw);return n.length>0&&!n.endsWith("/index")&&!n.includes("/index/");}

export class StructureRegistry{
 constructor(){this.entries=new Map();this.native=new Map();this.initialized=false;}
 refresh(){this.entries.clear();this.native.clear();const sm=world.structureManager;let ids=[];try{ids=sm?.getPackStructureIds?.()??[];}catch(e){console.warn(`[Sky Archipelago] pack structure enumeration failed: ${e}`);}
 for(const raw of ids){if(!isActualPackStructure(raw))continue;const n=normalize(raw),family=familyFor(raw);if(!family)continue;this.entries.set(n,{id:raw,normalized:n,category:categoryFor(raw),family,kind:"template"});}
 for(const [id,category] of Object.entries(NATIVE_FAMILIES))this.native.set(normalize(id),{id,normalized:normalize(id),category,kind:id.includes("mineshaft")?"native_mineshaft":"native_stronghold"});
 this.initialized=true;return this.entries;}
 ensure(){return this.initialized?this.entries:this.refresh();}
 isAllowedTemplate(id){return Boolean(familyFor(id));}
 isNative(id){return this.native.has(normalize(id));}
 nativeEntry(id){this.ensure();return this.native.get(normalize(id))??null;}
 get(id){this.ensure();const entry=this.entries.get(normalize(id));if(!entry)return null;try{const structure=world.structureManager?.get?.(entry.id);return structure?{...entry,structure}:null;}catch{return null;}}
 byCategory(category){return[...this.ensure().values()].filter(e=>e.category===category);}
 byFamily(family){return[...this.ensure().values()].filter(e=>e.family===family);}
 select(seed,category,family=null){let candidates=family?this.byFamily(family):this.byCategory(category);if(!candidates.length)candidates=this.byCategory(category);if(!candidates.length)return null;return candidates[Math.abs(Number(seed)%candidates.length)];}
 snapshot(){return{templates:[...this.ensure().values()].map(e=>({id:e.id,category:e.category,family:e.family})),native:[...this.native.values()]};}
}
export const STRUCTURE_FAMILIES=TEMPLATE_FAMILIES;
export const NATIVE_STRUCTURE_CATEGORIES=NATIVE_FAMILIES;
