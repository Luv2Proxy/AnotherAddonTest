import { BlockPermutation } from "@minecraft/server";
import { StructureCategory } from "./StructureRegistry.js";

const AIR = "minecraft:air";
const SOLID_FALLBACK = ["minecraft:stone", "minecraft:dirt"];
const WATER = new Set(["minecraft:water", "minecraft:flowing_water"]);

function xyz(p) { return { x: Math.floor(Number(p?.x ?? 0)), y: Math.floor(Number(p?.y ?? 0)), z: Math.floor(Number(p?.z ?? 0)) }; }
function add(a,b) { return { x:a.x+b.x, y:a.y+b.y, z:a.z+b.z }; }
function key(p) { return `${p.x},${p.y},${p.z}`; }
function blockId(block) { try { return block?.typeId ?? block?.permutation?.type?.id ?? null; } catch { return null; } }
function safeBlock(dimension,p) { try { return dimension.getBlock(p); } catch { return null; } }
function isAir(block) { const id=blockId(block); return !block || id===AIR || id===undefined || id===null; }
function isWater(block) { return WATER.has(blockId(block)); }
function isReplaceable(block) { return isAir(block) || isWater(block); }
function resolvePermutation(id,states={}) { try { return BlockPermutation.resolve(id,states); } catch { return null; } }

export class TerrainAdaptationEngine {
  constructor(dimension, options={}) { this.dimension=dimension; this.options=options; this.changes=[]; }
  reset() { this.changes.length=0; return this; }
  setBlock(position,id,states={}) {
    const p=xyz(position), permutation=typeof id === "string" ? resolvePermutation(id,states) : id;
    if(!permutation)return false;
    try { this.dimension.setBlockPermutation(p,permutation); this.changes.push({position:p,id:typeof id === "string'?id:null'}); return true; } catch { return false; }
  }
  fillColumn(x,z,fromY,toY,id,states={}) {
    const a=Math.min(fromY,toY), b=Math.max(fromY,toY), permutation=resolvePermutation(id,states); if(!permutation)return 0; let count=0;
    for(let y=a;y<=b;y++) { try { this.dimension.setBlockPermutation({x,y,z},permutation); count++; } catch {} }
    return count;
  }
  sampleSurface(x,z,range=64) {
    const base=Number(this.options.minY ?? -64), max=Number(this.options.maxY ?? 320), start=Math.min(max,Math.max(base,Number(range??64))); 
    for(let y=Math.min(max,start);y>=base;y--) { const b=safeBlock(this.dimension,{x,y,z}); if(b && !isAir(b) && !isWater(b)) return y; }
    return base;
  }
  footprint(candidate, origin) {
    const size=candidate?.size ?? candidate?.transformedSize ?? {x:1,y:1,z:1};
    const radius=Number(candidate?.footprintRadius ?? candidate?.footprint?.radius ?? Math.max(size.x,size.z)/2);
    const halfX=Math.max(1,Math.ceil(Number(candidate?.footprint?.x ?? radius))), halfZ=Math.max(1,Math.ceil(Number(candidate?.footprint?.z ?? radius)));
    const points=[]; for(let x=-halfX;x<=halfX;x++) for(let z=-halfZ;z<=halfZ;z++) points.push({x:origin.x+x,z:origin.z+z}); return {points,halfX,halfZ};
  }
  adapt(candidate, context={}) {
    const mode=String(context.mode ?? candidate?.terrain_adaptation ?? candidate?.terrainAdaptation?.mode ?? "none").toLowerCase();
    const origin=xyz(context.location ?? candidate?.location ?? candidate ?? {}), footprint=this.footprint(candidate,origin);
    const targetY=Number(context.targetY ?? candidate?.targetY ?? origin.y), maxDepth=Number(context.depth ?? candidate?.buryDepth ?? candidate?.terrainAdaptation?.depth ?? 8);
    const foundationId=context.foundationBlock ?? candidate?.foundationBlock ?? "minecraft:dirt";
    const surfaceIds=context.surfaceBlocks ?? candidate?.terrainAdaptation?.surfaceBlocks ?? SOLID_FALLBACK;
    const result={mode,origin,targetY,changed:0,columns:0,skipped:0,operations:[]};
    if(mode==="none")return result;
    if(mode==="bury") {
      for(const p of footprint.points) {
        const surface=this.sampleSurface(p.x,p.z), top=Math.min(surface,targetY-1), bottom=Math.max(-64,top-maxDepth);
        const count=this.fillColumn(p.x,p.z,bottom,top,foundationId); if(count)result.changed+=count; result.columns++;
      }
      result.operations.push("bury"); return result;
    }
    if(mode==="beard_thin" || mode==="beard_box") {
      for(const p of footprint.points) {
        const surface=this.sampleSurface(p.x,p.z), delta=targetY-surface;
        if(Math.abs(delta)>maxDepth && mode==="beard_thin"){result.skipped++;continue;}
        const depth=mode==="beard_box" ? Math.min(maxDepth,Math.max(1,Math.abs(delta)+2)) : Math.min(maxDepth,Math.max(1,Math.abs(delta)));
        const top=targetY-1, bottom=Math.min(surface,top-depth);
        const count=this.fillColumn(p.x,p.z,bottom,top,foundationId); if(count)result.changed+=count; result.columns++;
      }
      result.operations.push(mode); return result;
    }
    if(mode==="encapsulate") {
      const minY=targetY-maxDepth, maxY=targetY+Number(candidate?.size?.y ?? candidate?.transformedSize?.y ?? 1);
      for(const p of footprint.points) {
        const below=safeBlock(this.dimension,{x:p.x,y:minY,z:p.z}); if(isReplaceable(below))this.setBlock({x:p.x,y:minY,z:p.z},foundationId);
        const above=safeBlock(this.dimension,{x:p.x,y:maxY,z:p.z}); if(isAir(above))this.setBlock({x:p.x,y:maxY,z:p.z},surfaceIds[0]??SOLID_FALLBACK[0]);
        result.columns++;
      }
      result.operations.push("encapsulate"); return result;
    }
    return result;
  }
  flatten(candidate,context={}) {
    const origin=xyz(context.location ?? candidate?.location ?? candidate ?? {}), footprint=this.footprint(candidate,origin), targetY=Number(context.targetY ?? origin.y), foundation=context.foundationBlock ?? "minecraft:dirt"; let changed=0;
    for(const p of footprint.points) { const surface=this.sampleSurface(p.x,p.z); if(surface<targetY) changed+=this.fillColumn(p.x,p.z,surface,targetY-1,foundation); else if(surface>targetY) { for(let y=targetY;y<surface;y++){const b=safeBlock(this.dimension,{x:p.x,y,z:p.z});if(b&&!isAir(b))try{this.dimension.setBlockPermutation({x:p.x,y,z:p.z},resolvePermutation(AIR));changed++;}catch{}} } }
    return {mode:"flatten",targetY,changed};
  }
  waterline(candidate,context={}) {
    const origin=xyz(context.location ?? candidate?.location ?? candidate ?? {}), footprint=this.footprint(candidate,origin), waterLevel=Number(context.waterLevel ?? 63), floorY=Number(context.seaFloorY ?? waterLevel-1), waterPermutation=resolvePermutation("minecraft:water"); let changed=0;
    if(!waterPermutation)return {mode:"waterline",changed:0};
    for(const p of footprint.points) for(let y=floorY+1;y<=waterLevel;y++){const b=safeBlock(this.dimension,{x:p.x,y,z:p.z});if(isAir(b)){try{this.dimension.setBlockPermutation({x:p.x,y,z:p.z},waterPermutation);changed++;}catch{}}}
    return {mode:"waterline",waterLevel,floorY,changed};
  }
}

export function applyTerrainAdaptation(dimension,candidate,context={}) {
  const engine=new TerrainAdaptationEngine(dimension,context.options ?? {}), adaptation=engine.adapt(candidate,context);
  if(context.flatten || adaptation.mode === "beard_thin" || adaptation.mode === "beard_box") adaptation.flatten=engine.flatten(candidate,{...context,targetY:adaptation.targetY});
  if(context.waterline) adaptation.waterline=engine.waterline(candidate,context);
  return adaptation;
}
