import { world } from "@minecraft/server";
import { readPackSettings } from "../config/SkyIslandSettings.js";
import { IslandNoise } from "./IslandNoise.js";
import { IslandClusterSampler } from "./IslandClusterSampler.js";
import { IslandDescriptorFactory } from "./IslandDescriptorFactory.js";
import { IslandShapeSampler } from "./IslandShapeSampler.js";
import { IslandDensityEvaluator } from "./IslandDensityEvaluator.js";
import { planColumn } from "./ExactMaterialPlan.js";
import { ExactSurfacePipeline } from "./ExactSurfacePipeline.js";
import { terrainProfile } from "./BiomeTerrainProfiles.js";
import { StructureDetection } from "./StructureDetection.js";
import { StructurePlacement } from "./StructurePlacement.js";
import { StructureRegistry, StructureCategory } from "./StructureRegistry.js";
import { StructureOverlapGuard } from "./StructureOverlapGuard.js";
import { NativeStructureCoordinator } from "./NativeStructureCoordinator.js";
import { TerrainAdaptationEngine } from "./TerrainAdaptationEngine.js";
import { assetRole } from "./StructureAssetMapping.js";

const DB="sky_archipelago:generated_v14",DIMENSION="sky_archipelago:archipelago",WATER="minecraft:water";

const CATEGORY_CONFIG=Object.freeze({
  [StructureCategory.SMALL_SKY]:[
    {family:"ruined_portal/",maxDepth:1,chance:.006},
    {family:"igloo/",maxDepth:1,chance:.002}
  ],
  [StructureCategory.SURFACE_SKY]:[
    {family:"pillageroutpost/",maxDepth:1,chance:.006},
    {family:"endcity/",maxDepth:8,chance:.002},
    {family:"mansion/",maxDepth:1,chance:.0008},
    {family:"bastion/",maxDepth:4,chance:.002}
  ],
  [StructureCategory.GROUND_VILLAGE]:[
    {family:"village/",maxDepth:7,chance:.010},
    {family:"trail_ruins/",maxDepth:4,chance:.002}
  ],
  [StructureCategory.WATER]:[
    {family:"shipwreck/",maxDepth:1,chance:.0025},
    {family:"ruin/",maxDepth:1,chance:.002},
    {family:"underwater_ruin/",maxDepth:1,chance:.002},
    {family:"coralcrust/",maxDepth:1,chance:.001}
  ],
  [StructureCategory.UNDERGROUND]:[
    {family:"ancient_city/",maxDepth:1,chance:.002},
    {family:"trial_chambers/",maxDepth:8,chance:.0015},
    {family:"fossils/",maxDepth:1,chance:.0015},
    {family:"nether_fossils/",maxDepth:1,chance:.0008}
  ]
});

const NATIVE_STRUCTURE_CHANCES=Object.freeze([
  {id:"monument",category:StructureCategory.WATER,chance:.00035,footprint:{x:56,y:24,z:56}},
  {id:"stronghold",category:StructureCategory.STRONGHOLD,chance:.00020,footprint:{x:96,y:48,z:96}},
  {id:"mineshaft",category:StructureCategory.UNDERGROUND,chance:.00055,footprint:{x:64,y:24,z:64}}
]);

function flattenCategoryConfig(){const out=[];for(const [category,entries] of Object.entries(CATEGORY_CONFIG))for(const entry of entries)out.push({...entry,category});return out;}
const ALL_STRUCTURE_CONFIG=Object.freeze(flattenCategoryConfig());

function normalizeBounds(value){
  if(!value)return null;
  if(value.minX!=null)return{minX:Number(value.minX),minY:Number(value.minY),minZ:Number(value.minZ),maxX:Number(value.maxX),maxY:Number(value.maxY),maxZ:Number(value.maxZ)};
  if(value.min&&value.max)return{minX:Number(value.min.x),minY:Number(value.min.y),minZ:Number(value.min.z),maxX:Number(value.max.x),maxY:Number(value.max.y),maxZ:Number(value.max.z)};
  return null;
}

export class IslandGenerator{
 constructor(){Object.assign(this,{dimension:null,noise:null,clusters:null,factory:null,shape:null,density:null,settings:null,surface:null,structures:null,placement:null,registry:null,native:null,terrain:null,layoutSeed:0n,generated:new Set(),queue:[],queued:new Set(),jobs:[],structureJobs:[],placedStructureKeys:new Set(),state:new Map(),overlap:new StructureOverlapGuard()});}
 load(){if(this.noise)return;this.settings=readPackSettings();this.layoutSeed=BigInt(world.seed);this.dimension=world.getDimension(DIMENSION);this.noise=new IslandNoise(this.layoutSeed);this.clusters=new IslandClusterSampler(this.noise,this.settings);this.factory=new IslandDescriptorFactory(this.noise);this.shape=new IslandShapeSampler(this.noise);this.density=new IslandDensityEvaluator(this.noise,this.shape);this.surface=new ExactSurfacePipeline(this.settings,this.noise);this.structures=new StructureDetection(this.settings);this.placement=new StructurePlacement(this.structures,{generator:this,overlapGuard:this.overlapGuard,terrainOptions:{minY:-64,maxY:320}});this.registry=new StructureRegistry();this.registry.refresh();this.overlap.load();this.terrain=new TerrainAdaptationEngine(this.dimension,{minY:-64,maxY:320});this.native=new NativeStructureCoordinator(this,this.dimension,this.overlap);try{const raw=world.getDynamicProperty(DB);if(typeof raw==="string"){const d=JSON.parse(raw);if(d.seed===this.layoutSeed.toString()&&d.settingsHash===this.settingsHash()){this.generated=new Set(d.generated||[]);this.placedStructureKeys=new Set(d.placedStructureKeys||[]);this.state=new Map(d.state||[]);this.structureJobs=d.structureJobs||[];}}}catch(e){console.warn(`[Sky Archipelago] persistence load failed: ${e}`);}for(const[k,v]of this.state)if(v!=="COMPLETE"&&!this.queued.has(k)){const[cx,cz]=k.split(",").map(Number);this.queued.add(k);this.queue.push({cx,cz});}}
 settingsHash(){return JSON.stringify(this.settings);}save(){try{world.setDynamicProperty(DB,JSON.stringify({version:14,seed:this.layoutSeed.toString(),settingsHash:this.settingsHash(),generated:[...this.generated],placedStructureKeys:[...this.placedStructureKeys],state:[...this.state],structureJobs:this.structureJobs.slice(0,512),native:this.native?.snapshot()}));}catch(e){console.warn(`[Sky Archipelago] persistence save failed: ${e}`);}}
 cluster(cx,cz){return this.clusters.cluster(cx,cz,this.layoutSeed,this.settings.spacing);}descriptorsForCell(cx,cz){const c=this.cluster(cx,cz),out=[this.factory.anchor(c)];for(let i=0;i<c.satelliteCount;i++)out.push(this.factory.satellite(c,i));for(let i=0;i<c.spireCount;i++)out.push(this.factory.spire(c,i));const scale=Math.max(.35,Math.min(2.2,(this.settings.minIslandRadius/18+this.settings.maxIslandRadius/72)/2)),ss=this.settings.spacing/96;for(const d of out){d.x=c.centerX+(d.x-c.centerX)*ss;d.z=c.centerZ+(d.z-c.centerZ)*ss;d.rx=Math.max(4,d.rx*scale);d.rz=Math.max(4,d.rz*scale);d.maxR=Math.max(d.rx,d.rz);d.plateau=Math.max(2,d.plateau*this.settings.terrainReliefScale);d.cliff=Math.max(4,d.cliff*this.settings.terrainReliefScale);d.hang=Math.max(8,Math.min(this.settings.maxIslandThickness,d.hang));}return out;}
 descriptorsNear(x,z){const s=this.settings.spacing,cx=Math.floor(x/s),cz=Math.floor(z/s),out=[];for(let ix=cx-3;ix<=cx+3;ix++)for(let iz=cz-3;iz<=cz+3;iz++)for(const d of this.descriptorsForCell(ix,iz)){const reach=Math.max(d.maxR*3+96,Math.max(Math.abs(d.hangX)+d.tailX*2,Math.abs(d.hangZ)+d.tailZ*2)+96),dx=x-d.x,dz=z-d.z;if(dx*dx+dz*dz<=reach*reach)out.push(d);}return out;}
 column(x,z,minY=-64,maxY=320){const ds=this.descriptorsNear(x,z);if(!ds.length)return[];const raw=[];let active=false,top=0;for(let y=maxY-1;y>=minY;y--){let den=-Infinity;for(const d of ds){const h=this.shape.sample(d,x,z);if(h.influence)den=Math.max(den,this.density.density(d,h,x,y,z));}const solid=den>0;if(solid&&!active){top=y;active=true;}if(!solid&&active){raw.push([y+1,top]);active=false;}}if(active)raw.push([minY,top]);return this.resolveOverlapExact(raw);}
 resolveOverlapExact(raw){if(raw.length<2)return raw;const mode=this.settings.terrainOverlapMode;if(mode==="overlap")return raw;if(mode==="void")return[raw.reduce((a,b)=>a[1]>b[1]?a:b)];const sorted=[...raw].sort((a,b)=>b[1]-a[1]),keep=[sorted[0]];for(let i=1;i<sorted.length;i++){const lower=sorted[i],upper=keep[keep.length-1],gap=upper[0]-lower[1];if(gap>=8){const carving=Math.min(28,Math.max(2,Math.floor((upper[1]-upper[0]+1)*.5))),end=lower[1]-carving;if(end-lower[0]+1>=4)keep.push([lower[0],end]);}}return keep.sort((a,b)=>a[0]-b[0]);}
 biomeAt(dim,x,y,z){try{return dim.getBiome({x,y,z})?.id??"minecraft:plains";}catch{return"minecraft:plains";}}
 generateColumn(dim,x,z,segments){if(!segments.length)return;const top=Math.max(...segments.map(s=>s[1])),biome=this.biomeAt(dim,x,top,z),profile=terrainProfile(biome),plan=planColumn(segments,-64,321,{...this.settings,deepslateStartY:this.settings.deepslateStartY-(profile[0]-1)*3},x,z,this.layoutSeed);for(let y=-64;y<=320;y++){const p=plan.materialAt(y);if(p)dim.setBlockPermutation({x,y,z},p);else if(this.settings.oceanEnabled&&y<=plan.oceanTop&&y>top)dim.setBlockType({x,y,z},WATER);else if(y>=top+1)dim.setBlockType({x,y,z},"minecraft:air");}this.surface.apply(dim,x,z,segments,biome,profile,this.layoutSeed);}
 structureSeed(x,z,type="structure"){let v=this.layoutSeed^BigInt(Math.trunc(x)*0x9E3779B1)^BigInt(Math.trunc(z)*0x85EBCA77);for(const c of String(type))v^=BigInt(c.charCodeAt(0));v^=v>>33n;v*=0xff51afd7ed558ccdn;v^=v>>33n;return Number(v&0x7fffffffn)/0x80000000;}
 chooseStructure(d){const nativeR=this.structureSeed(Math.floor(d.x),Math.floor(d.z),"native");let nativeCursor=0;for(const native of NATIVE_STRUCTURE_CHANCES){nativeCursor+=native.chance;if(nativeR<nativeCursor){const key=`native:${native.id}:${Math.floor(d.x/16)}:${Math.floor(d.z/16)}`;if(!this.placedStructureKeys.has(key))return{native:native.id,category:native.category,footprint:native.footprint};break;}}const r=this.structureSeed(Math.floor(d.x),Math.floor(d.z),"structure");let cursor=0;for(const cfg of ALL_STRUCTURE_CONFIG){cursor+=cfg.chance;if(r>=cursor)continue;const entry=this.registry.select(Math.floor(r*0x7fffffff),cfg.category,cfg.family);if(entry)return{entry,category:cfg.category,maxDepth:cfg.maxDepth,family:cfg.family,assetRole:assetRole(entry.id),assetKind:entry.kind};}return null;}
 structureCandidate(x,z){for(const d of this.descriptorsNear(x,z)){if(Math.hypot(x-d.x,z-d.z)>d.maxR*.8)continue;const selected=this.chooseStructure(d);if(!selected)continue;if(selected.native){const key=`native:${selected.native}:${Math.floor(d.x/16)}:${Math.floor(d.z/16)}`;if(this.placedStructureKeys.has(key))continue;return{id:selected.native,key,x:Math.floor(d.x),z:Math.floor(d.z),y:Math.floor(d.plateau||128),category:selected.category,native:true,footprint:selected.footprint};}const e=selected.entry,key=`${e.normalized}:${Math.floor(d.x/16)}:${Math.floor(d.z/16)}`;if(!this.placedStructureKeys.has(key))return{id:e.id,key,x:Math.floor(d.x),z:Math.floor(d.z),y:Math.floor(d.plateau||128),category:selected.category,maxDepth:selected.maxDepth,family:e.family,assetRole:e.role,assetKind:e.kind};}return null;}
 queueStructuresForChunk(cx,cz){for(let dx=-3;dx<=3;dx++)for(let dz=-3;dz<=3;dz++){const c=this.structureCandidate(cx*16+8+dx*16,cz*16+8+dz*16);if(c&&!this.structureJobs.some(j=>j.key===c.key))this.structureJobs.push(c);}}
 adaptPlacedResult(job,result,location,dim){if(!result?.placed||!result.bounds||!this.terrain)return result;const mode=job.terrain_adaptation??(job.category===StructureCategory.GROUND_VILLAGE?"beard_thin":job.category===StructureCategory.STRONGHOLD?"bury":job.category===StructureCategory.UNDERGROUND?"beard_box":"none");if(mode==="none")return result;const b=normalizeBounds(result.bounds);if(!b)return result;const candidate={id:job.id,pieceBounds:[b],terrain_adaptation:mode,foundationBlock:"minecraft:dirt",jigsawJunctions:result.junctions??result.plan?.junctions??[]};const terrain=this.terrain.adapt(candidate,{mode,targetY:location.y,location,foundationBlock:"minecraft:dirt"});if(mode==="beard_thin"||mode==="beard_box")terrain.flatten=this.terrain.flatten(candidate,{targetY:location.y,location,foundationBlock:"minecraft:dirt"});return{...result,terrain};}
 placeQueuedStructures(){if(!this.structureJobs.length)return;const job=this.structureJobs.shift(),dim=this.dimension??world.getDimension(DIMENSION);try{let r;const location={x:job.x,y:job.y,z:job.z};if(job.native==="stronghold"){const plan=this.native.plan("stronghold",{...location,footprint:job.footprint,footprintRadius:48,searchRadius:32});r=this.native.commit("stronghold",plan);}else if(job.native==="mineshaft"){const plan=this.native.plan("mineshaft",{...location,footprint:job.footprint,footprintRadius:32,searchRadius:24});r=this.native.commit("mineshaft",plan);}else if(job.native==="monument"){const plan=this.native.plan("jigsaw",{...location,footprint:job.footprint,footprintRadius:28,searchRadius:32});r=this.native.commitOceanMonument({x:plan?.target?.x??job.x,y:plan?.target?.y??job.y,z:plan?.target?.z??job.z});}else if(job.assetKind==="jigsaw"){r=this.placement.placeJigsawStructure(job.id,dim,location,{maxDepth:job.maxDepth,overlapPadding:2},job.category);}else if(job.assetKind==="composite"){r=this.placeCompositeStructure(job,dim);}else if(job.category===StructureCategory.UNDERGROUND)r=this.placement.placeTemplate(job.id,dim,{x:job.x,y:Math.max(-64,job.y-24),z:job.z},{rotation:"None"},job.category);else if(job.category===StructureCategory.WATER)r=this.placeWaterStructure(job,dim);else r=this.placement.placeTemplate(job.id,dim,location,{rotation:"None"},job.category);r=this.adaptPlacedResult(job,r,location,dim);if(r?.placed||r?.accepted){this.placedStructureKeys.add(job.key);this.save();}}catch(e){console.warn(`[Sky Archipelago] structure placement failed for ${job.id}: ${e}`);}}
 placeWaterStructure(job,dim){const id=String(job.id).toLowerCase();let y=job.y;if(id.includes("shipwreck"))y=Math.max(0,job.y-6);else if(id.includes("ruin")||id.includes("coral"))y=Math.max(0,job.y-10);return this.placement.placeTemplate(job.id,dim,{x:job.x,y,z:job.z},{rotation:"None",overlapPadding:2},job.category);}
 placeCompositeStructure(job,dim){const root=this.registry.jigsawRoot(job.family);if(root?.definition){const nativeResult=this.placement.placeJigsawStructure(root.id,dim,{x:job.x,y:job.y,z:job.z},{maxDepth:job.maxDepth,overlapPadding:2},job.category);if(nativeResult?.placed)return nativeResult;}const pieces=this.registry.composite(job.family),roles=Object.keys(pieces);if(!roles.length)return{placed:false,reason:"empty_composite_mapping"};const role=roles[Math.floor(this.structureSeed(job.x,job.z,`role:${job.family}`)*roles.length)],candidates=pieces[role];if(!candidates?.length)return{placed:false,reason:`empty_role:${role}`};const entry=candidates[Math.floor(this.structureSeed(job.x,job.z,`piece:${job.family}:${role}`)*candidates.length)];if(!entry)return{placed:false,reason:"missing_piece"};const offsets={entrance:[0,0,0],center:[0,0,0],structure:[8,0,8],wall:[-8,0,-8],bridge:[0,0,0],hoglin_stable:[8,0,8],treasure:[-8,0,8],units:[0,0,-8],room:[0,0,0]},o=offsets[role]??[0,0,0];return this.placement.placeTemplate(entry.id,dim,{x:job.x+o[0],y:job.y+o[1],z:job.z+o[2]},{rotation:"None",overlapPadding:2},job.category);}
 enqueue(cx,cz){const k=`${cx},${cz}`;if(this.generated.has(k)||this.queued.has(k))return;this.queued.add(k);this.state.set(k,"QUEUED");this.queue.push({cx,cz});}
 requestAround(player){this.load();const cx=Math.floor(player.location.x/16),cz=Math.floor(player.location.z/16);for(let dx=-8;dx<=8;dx++)for(let dz=-8;dz<=8;dz++)this.enqueue(cx+dx,cz+dz);this.queue.sort((a,b)=>((a.cx-cx)**2+(a.cz-cz)**2)-((b.cx-cx)**2+(b.cz-cz)**2));this.queueStructuresForChunk(cx,cz);this.save();}
 startNext(){if(this.jobs.length||!this.queue.length)return;const q=this.queue.shift(),k=`${q.cx},${q.cz}`;this.state.set(k,"GENERATING");this.jobs.push({cx:q.cx,cz:q.cz,x:0,z:0});this.save();}
 tick(){this.load();this.placeQueuedStructures();this.startNext();const j=this.jobs[0];if(!j)return;const dim=this.dimension??world.getDimension(DIMENSION);let count=0;while(count<4&&j.x<16){this.generateColumn(dim,j.cx*16+j.x,j.cz*16+j.z,this.column(j.cx*16+j.x,j.cz*16+j.z));j.z++;count++;if(j.z>=16){j.z=0;j.x++;}}if(j.x>=16){const k=`${j.cx},${j.cz}`;this.generated.add(k);this.queued.delete(k);this.state.set(k,"COMPLETE");this.jobs.shift();this.save();}}
 reset(){this.generated.clear();this.queue=[];this.queued.clear();this.jobs=[];this.structureJobs=[];this.placedStructureKeys.clear();this.state.clear();this.overlap.clear();this.save();}}
