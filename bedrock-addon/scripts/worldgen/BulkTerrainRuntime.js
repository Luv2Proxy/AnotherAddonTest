import { system } from "@minecraft/server";
import { planColumn } from "./ExactMaterialPlan.js";
import { terrainProfile } from "./BiomeTerrainProfiles.js";
import { greedyCuboids, writeBulk } from "./BulkWorldWriter.js";

const AIR="minecraft:air";
const STONE="minecraft:stone";
const DEEPSLATE="minecraft:deepslate";
const BEDROCK="minecraft:bedrock";
const WATER="minecraft:water";
const VARIANTS=["minecraft:granite","minecraft:diorite","minecraft:andesite"];
const ORES=[
 {id:"minecraft:coal_ore",deepslate:"minecraft:deepslate_coal_ore",count:30,size:17,min:136,max:320,kind:"uniform"},
 {id:"minecraft:coal_ore",deepslate:"minecraft:deepslate_coal_ore",count:20,size:17,min:0,max:192,kind:"uniform"},
 {id:"minecraft:iron_ore",deepslate:"minecraft:deepslate_iron_ore",count:90,size:9,min:80,max:320,kind:"trapezoid"},
 {id:"minecraft:iron_ore",deepslate:"minecraft:deepslate_iron_ore",count:10,size:9,min:-64,max:72,kind:"trapezoid"},
 {id:"minecraft:iron_ore",deepslate:"minecraft:deepslate_iron_ore",count:20,size:9,min:-24,max:56,kind:"uniform"},
 {id:"minecraft:copper_ore",deepslate:"minecraft:deepslate_copper_ore",count:16,size:10,min:-16,max:112,kind:"trapezoid"},
 {id:"minecraft:gold_ore",deepslate:"minecraft:deepslate_gold_ore",count:4,size:9,min:-64,max:32,kind:"trapezoid"},
 {id:"minecraft:gold_ore",deepslate:"minecraft:deepslate_gold_ore",count:2,size:9,min:-64,max:32,kind:"uniform"},
 {id:"minecraft:redstone_ore",deepslate:"minecraft:deepslate_redstone_ore",count:4,size:8,min:-64,max:16,kind:"uniform"},
 {id:"minecraft:redstone_ore",deepslate:"minecraft:deepslate_redstone_ore",count:8,size:8,min:-64,max:32,kind:"trapezoid"},
 {id:"minecraft:lapis_ore",deepslate:"minecraft:deepslate_lapis_ore",count:2,size:7,min:-64,max:64,kind:"trapezoid"},
 {id:"minecraft:diamond_ore",deepslate:"minecraft:deepslate_diamond_ore",count:7,size:4,min:-64,max:16,kind:"trapezoid"},
 {id:"minecraft:emerald_ore",deepslate:"minecraft:deepslate_emerald_ore",count:100,size:3,min:-16,max:256,kind:"trapezoid",emerald:true}
];

function hash32(x,z,seed,salt=0){let h=(Number(seed)&0xffffffff)^Math.imul(x|0,0x45d9f3b)^Math.imul(z|0,0x119de1f3)^salt;h=Math.imul(h^(h>>>16),0x45d9f3b);h=Math.imul(h^(h>>>16),0x45d9f3b);return(h^(h>>>16))>>>0;}
function rand(seed){let s=(seed>>>0)||1;return()=>{s^=s<<13;s^=s>>>17;s^=s<<5;return(s>>>0)/4294967296;};}
function materialId(p){return p?.type?.id??p?.typeId??null;}
function key(x,y,z){return`${x},${y},${z}`;}
function setVoxel(map,x,y,z,id){if(!id)return;const k=key(x,y,z);if(map.has(k))map.get(k).id=id;}
function getVoxel(map,x,y,z){return map.get(key(x,y,z));}
function eligible(id){return id===STONE||id===DEEPSLATE||id==="minecraft:granite"||id==="minecraft:diorite"||id==="minecraft:andesite"||id==="minecraft:tuff";}
function variantFor(x,y,z,seed){const r=hash32(x,y^z,seed,0x9e3779b9)/4294967296;if(y<0&&r<.035)return"minecraft:tuff";if(r<.055)return VARIANTS[Math.floor(r*1000)%3];return null;}
function heightY(feature,rng){const span=feature.max-feature.min;if(feature.kind==="trapezoid"){const a=rng(),b=rng();return Math.floor(feature.min+((a+b)/2)*span);}return feature.min+Math.floor(rng()*Math.max(1,span+1));}
function placeVein(map,feature,x,y,z,rng){const points=Math.max(1,feature.size),angle=rng()*Math.PI*2;let px=x,py=y,pz=z;for(let i=0;i<points;i++){const radius=Math.max(.7,1.75*(1-i/points)+rng()*.7),rx=Math.floor(px+(rng()-.5)*radius),ry=Math.floor(py+(rng()-.5)*radius),rz=Math.floor(pz+(rng()-.5)*radius);for(let dx=-1;dx<=1;dx++)for(let dy=-1;dy<=1;dy++)for(let dz=-1;dz<=1;dz++){if(rng()>.38)continue;const v=getVoxel(map,rx+dx,ry+dy,rz+dz);if(!v||!eligible(v.id))continue;setVoxel(map,rx+dx,ry+dy,rz+dz,v.id===DEEPSLATE?feature.deepslate:feature.id);}px+=Math.cos(angle)*.55;py+=(rng()-.5)*.9;pz+=Math.sin(angle)*.55;}}
function oreAllowed(feature,biome){if(!feature.emerald)return true;return /mountain|hills|windswept|peak|grove|meadow|stony/i.test(String(biome));}
function applyOres(map,chunk,seed,biomeByColumn){for(const feature of ORES){const rng=rand(hash32(chunk.cx,chunk.cz,seed,feature.id.length*0x9e37));for(let i=0;i<feature.count;i++){const x=chunk.cx*16+Math.floor(rng()*16),z=chunk.cz*16+Math.floor(rng()*16),biome=biomeByColumn.get(`${x},${z}`)||"minecraft:plains";if(!oreAllowed(feature,biome))continue;const y=heightY(feature,rng);placeVein(map,feature,x,y,z,rng);}}}
function toOps(map){const blocks=[...map.values()];return greedyCuboids(blocks,{exactIds:new Set([AIR,WATER,BEDROCK])});}

export class BulkTerrainRuntime{
 constructor(generator,options={}){this.generator=generator;this.options=options;this.pending=new Map();this.flushJobs=new Set();this.originalGenerate=null;this.originalTick=null;this.installed=false;this.stats={columns:0,chunks:0,fills:0,singles:0,failed:0,ores:0,variants:0};}
 install(){if(this.installed)return this;this.installed=true;this.originalGenerate=this.generator.generateColumn.bind(this.generator);this.originalTick=this.generator.tick.bind(this.generator);const self=this;this.generator.generateColumn=function(dim,x,z,segments){return self.collect(dim,x,z,segments);};this.generator.tick=function(){return self.tick();};return this;}
 collect(dim,x,z,segments){if(!segments?.length)return;const cx=Math.floor(x/16),cz=Math.floor(z/16),k=`${cx},${cz}`;let chunk=this.pending.get(k);if(!chunk){chunk={cx,cz,dim,map:new Map(),biomeByColumn:new Map(),columns:new Set(),seed:this.generator.layoutSeed};this.pending.set(k,chunk);}const top=Math.max(...segments.map(s=>s[1])),biome=this.generator.biomeAt(dim,x,top,z),profile=terrainProfile(biome),plan=planColumn(segments,-64,321,{...this.generator.settings,deepslateStartY:this.generator.settings.deepslateStartY-(profile[0]-1)*3},x,z,this.generator.layoutSeed);for(let y=-64;y<=320;y++){const p=plan.materialAt(y),id=materialId(p);if(id)chunk.map.set(key(x,y,z),{x,y,z,id});else if(this.generator.settings.oceanEnabled&&y<=plan.oceanTop&&y>top)chunk.map.set(key(x,y,z),{x,y,z,id:WATER});}chunk.biomeByColumn.set(`${x},${z}`,biome);chunk.columns.add(`${x},${z}`);this.stats.columns++;if(chunk.columns.size>=16)this.flush(k);}
 flush(k){const chunk=this.pending.get(k);if(!chunk||this.flushJobs.has(k))return;this.pending.delete(k);this.flushJobs.add(k);const self=this;const job=function*(){try{for(const v of chunk.map.values()){if(eligible(v.id)){const variant=variantFor(v.x,v.y,v.z,chunk.seed);if(variant){v.id=variant;self.stats.variants++;}}yield;}applyOres(chunk.map,chunk,{valueOf:()=>Number(chunk.seed)&0xffffffff},chunk.biomeByColumn);yield;const ops=toOps(chunk.map);const result=writeBulk(chunk.dim,ops,{strict:false});self.stats.fills+=result.fills;self.stats.singles+=result.singles;self.stats.failed+=result.failed;self.stats.chunks++;yield;}finally{self.flushJobs.delete(k);}};system.runJob(job());}
 tick(){this.originalTick();for(const [k,chunk] of this.pending){if(chunk.columns.size>=16)this.flush(k);}}
 flushAll(){for(const k of this.pending.keys())this.flush(k);}
 snapshot(){return{...this.stats,pendingChunks:this.pending.size,activeBulkJobs:this.flushJobs.size};}
}

export function installBulkTerrainRuntime(generator,options={}){return new BulkTerrainRuntime(generator,options).install();}
