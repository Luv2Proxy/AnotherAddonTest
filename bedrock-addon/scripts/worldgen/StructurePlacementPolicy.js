import { StructurePlacementCategory, WaterPlacementMode, StructureFootprint } from "./StructurePlacementModel.js";

const DEFAULT_WATER_TOKENS=["ocean","water","shipwreck","underwater_ruin","ocean_ruin","coralcrust","monument","ruin"];
const DEFAULT_SKY_TOKENS=["sky","sky_village","airship","floating","cloud","aerial","endcity","end_city","mansion","bastion","pillageroutpost","pillager_outpost","ruined_portal"];
const DEFAULT_UNDERGROUND_TOKENS=["mineshaft","trial_chambers","ancient_city","underground","cave","fossil","nether_fossil"];
const DEFAULT_VILLAGE_TOKENS=["village","trail_ruins","trail_ruin"];
const DEFAULT_STRONGHOLD_TOKENS=["stronghold"];
const WATER_OVERRIDES={
  "minecraft:shipwreck":WaterPlacementMode.SURFACE,
  "minecraft:shipwreck_beached":WaterPlacementMode.SURFACE,
  "minecraft:ocean_ruin_cold":WaterPlacementMode.OCEAN_FLOOR,
  "minecraft:ocean_ruin_warm":WaterPlacementMode.OCEAN_FLOOR,
  "minecraft:monument":WaterPlacementMode.OCEAN_FLOOR,
  "minecraft:ruined_portal_ocean":WaterPlacementMode.OCEAN_FLOOR,
  "minecraft:coralcrust":WaterPlacementMode.OCEAN_FLOOR,
  "minecraft:underwater_ruin":WaterPlacementMode.OCEAN_FLOOR
};
const UNDERGROUND_IDS=new Set(["minecraft:mineshaft","minecraft:trial_chambers","minecraft:ancient_city"]);

export class StructurePlacementPolicy {
 constructor(o={}){
  this.deny=new Set(o.denylist??[]);
  this.overrides=new Map(Object.entries(o.categoryOverrides??{}));
  this.tokens={water:o.waterTokens??DEFAULT_WATER_TOKENS,sky:o.skyTokens??DEFAULT_SKY_TOKENS,underground:o.undergroundTokens??DEFAULT_UNDERGROUND_TOKENS,village:o.villageTokens??DEFAULT_VILLAGE_TOKENS,stronghold:o.strongholdTokens??DEFAULT_STRONGHOLD_TOKENS};
  this.surfaceSkySupportThreshold=o.surfaceSkySupportThreshold??.62;
  this.smallSkySupportThreshold=o.smallSkySupportThreshold??.62;
  this.surfaceSkyFootprintInsetRatio=o.surfaceSkyFootprintInsetRatio??.12;
  this.smallSkyFootprintInsetRatio=o.smallSkyFootprintInsetRatio??.12;
  this.surfaceSkySearchRadiusChunks=o.surfaceSkySearchRadiusChunks??8;
  this.smallSkySearchRadiusChunks=o.smallSkySearchRadiusChunks??8;
  this.surfaceSkyMinStableTopCells=o.surfaceSkyMinStableTopCells??18;
  this.smallSkyMinStableTopCells=o.smallSkyMinStableTopCells??18;
  this.surfaceSkyTopOffset=o.surfaceSkyTopOffset??1;
  this.smallSkyTopOffset=o.smallSkyTopOffset??1;
  this.surfaceSkyLocalSearchStepBlocks=o.surfaceSkyLocalSearchStepBlocks??6;
  this.smallSkyLocalSearchStepBlocks=o.smallSkyLocalSearchStepBlocks??6;
  this.surfaceSkyLocalSearchRadiusBlocks=o.surfaceSkyLocalSearchRadiusBlocks??36;
  this.smallSkyLocalSearchRadiusBlocks=o.smallSkyLocalSearchRadiusBlocks??36;
  this.surfaceSkyGroundedSampleThreshold=o.surfaceSkyGroundedSampleThreshold??.84;
  this.smallSkyGroundedSampleThreshold=o.smallSkyGroundedSampleThreshold??.84;
  this.surfaceSkyMaxGroundGapBlocks=o.surfaceSkyMaxGroundGapBlocks??2;
  this.smallSkyMaxGroundGapBlocks=o.smallSkyMaxGroundGapBlocks??2;
  this.surfaceSkyMinHostIslandRadius=o.surfaceSkyMinHostIslandRadius??48;
  this.smallSkyMinHostIslandRadius=o.smallSkyMinHostIslandRadius??48;
  this.surfaceSkyMinHostStableTopCells=o.surfaceSkyMinHostStableTopCells??18;
  this.smallSkyMinHostStableTopCells=o.smallSkyMinHostStableTopCells??18;
  this.nearMissFallbackEnabled=o.nearMissFallbackEnabled??true;
 }
 isDenied(id){return this.deny.has(id)}
 categoryFor(id){
  if(this.overrides.has(id))return this.overrides.get(id);
  const x=String(id).toLowerCase();
  if(DEFAULT_STRONGHOLD_TOKENS.some(t=>x.includes(t)))return StructurePlacementCategory.STRONGHOLD;
  if(UNDERGROUND_IDS.has(id)||this.tokens.underground.some(t=>x.includes(t)))return StructurePlacementCategory.UNDERGROUND;
  if(this.tokens.village.some(t=>x.includes(t)))return StructurePlacementCategory.GROUND_VILLAGE;
  if(this.tokens.water.some(t=>x.includes(t)))return StructurePlacementCategory.WATER;
  if(this.tokens.sky.some(t=>x.includes(t)))return StructurePlacementCategory.SURFACE_SKY;
  return StructurePlacementCategory.DEFAULT;
 }
 waterMode(id){return WATER_OVERRIDES[id]??(this.categoryFor(id)===StructurePlacementCategory.WATER?WaterPlacementMode.OCEAN_FLOOR:null)}
 findSupportedHost(generator,x,z,footprint,category,opts={}){
  const inset=category===StructurePlacementCategory.SMALL_SKY?this.smallSkyFootprintInsetRatio:this.surfaceSkyFootprintInsetRatio,
    fp=footprint instanceof StructureFootprint?footprint.insetByRatio(inset):footprint,
    step=category===StructurePlacementCategory.SMALL_SKY?this.smallSkyLocalSearchStepBlocks:this.surfaceSkyLocalSearchStepBlocks,
    radius=category===StructurePlacementCategory.SMALL_SKY?this.smallSkyLocalSearchRadiusBlocks:this.surfaceSkyLocalSearchRadiusBlocks,
    best=[];
  for(let dx=-radius;dx<=radius;dx+=step)for(let dz=-radius;dz<=radius;dz+=step){const c=this.evaluateHost(generator,x+dx,z+dz,fp,category,opts);if(c)best.push(c);}
  best.sort((a,b)=>b.score-a.score);
  return best[0]??null;
 }
 evaluateHost(generator,x,z,fp,category,opts={}){
  const samples=fp.sampleGrid(category===StructurePlacementCategory.SMALL_SKY?6:7),tops=[];
  for(const p of samples){const top=generator.surfaceY?.(p.x,p.z)??generator.columnTop?.(p.x,p.z);if(top==null)return null;tops.push(top);}
  const avg=tops.reduce((a,b)=>a+b,0)/tops.length,min=Math.min(...tops),max=Math.max(...tops),stable=tops.filter(y=>Math.abs(y-avg)<=2).length,ratio=stable/tops.length;
  const threshold=category===StructurePlacementCategory.SMALL_SKY?this.smallSkySupportThreshold:this.surfaceSkySupportThreshold;
  if(ratio<threshold)return null;
  const score=ratio-(max-min)*.01;
  return{anchor:{x,y:min+(category===StructurePlacementCategory.SMALL_SKY?this.smallSkyTopOffset:this.surfaceSkyTopOffset),z},score,stableTopCells:stable,top:min,groundGap:0};
 }
}
