import { world } from "@minecraft/server";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { StructureSetGenerator } from "./StructureSetGenerator.js";

const RUNTIME_DB = "sky_archipelago:structure_set_runtime_v2";

function surfaceY(generator, x, z, fallback = 128) {
  // IslandGenerator.column() returns [bottom, top] material intervals, not
  // arbitrary segment objects. Prefer the highest actual surface interval.
  try {
    const segments = generator?.column?.(Math.floor(x), Math.floor(z), -64, 320) ?? [];
    if (Array.isArray(segments) && segments.length) {
      let top = -Infinity;
      for (const segment of segments) {
        if (Array.isArray(segment) && segment.length >= 2) top = Math.max(top, Number(segment[1]));
        else if (segment && Number.isFinite(segment.top)) top = Math.max(top, Number(segment.top));
      }
      if (Number.isFinite(top)) return Math.floor(top);
    }
  } catch {}
  return Math.floor(fallback);
}

function familyOf(candidate) {
  const id = String(candidate?.structure ?? candidate?.id ?? "").toLowerCase();
  return candidate?.family ?? (id.includes("ancient_city") ? "ancient_city/" : id.includes("trial_chambers") ? "trial_chambers/" : id.includes("village/") ? "village/" : id.includes("bastion") ? "bastion/" : "");
}

export class StructureSetRuntime {
  constructor(generator, options = {}) {
    this.generator = generator;
    this.registry = options.registry ?? new JigsawRegistry();
    this.sets = options.sets ?? this.registry.structureSetIds?.() ?? [...this.registry.structureSets.keys()];
    this.planner = options.planner ?? new StructureSetGenerator(this.registry);
    this.dimensionId = options.dimensionId ?? "sky_archipelago:archipelago";
    this.radius = Number(options.radius ?? 512);
    this.maxPlansPerTick = Math.max(1, Number(options.maxPlansPerTick ?? 1));
    this.maxPlacementsPerTick = Math.max(1, Number(options.maxPlacementsPerTick ?? 2));
    this.active = new Map(); this.pending = []; this.pendingKeys = new Set(); this.seed = generator?.layoutSeed ?? 0n;
    this.completed = new Set(); this.failed = new Map(); this.load();
  }
  load(){try{const raw=world.getDynamicProperty(RUNTIME_DB);if(typeof raw!=="string")return;const d=JSON.parse(raw);if(String(d.seed)!==String(this.seed))return;this.completed=new Set(d.completed??[]);this.failed=new Map(d.failed??[]);}catch(e){console.warn(`[Sky Archipelago] structure-set persistence load failed: ${e}`);}}
  save(){try{world.setDynamicProperty(RUNTIME_DB,JSON.stringify({version:2,seed:String(this.seed),completed:[...this.completed],failed:[...this.failed].slice(-256)}));}catch(e){console.warn(`[Sky Archipelago] structure-set persistence save failed: ${e}`);}}
  refresh(){this.seed=this.generator?.layoutSeed??this.seed;const candidate=this.generator?.jigsawRegistry;if(candidate?.structureSetIds){this.registry=candidate;this.planner.registry=candidate;}this.sets=this.registry.structureSetIds?.()??[...this.registry.structureSets.keys()];return this;}
  key(setId,x,z){return`${setId}:${Math.floor(x/16)}:${Math.floor(z/16)}`;}
  enqueueAround(x,z){if(!this.sets.length)return 0;let added=0;for(const setId of this.sets){const key=this.key(setId,x,z);if(this.pendingKeys.has(key)||this.active.has(key)||this.completed.has(key))continue;this.pendingKeys.add(key);this.pending.push({setId,x,z,key});added++;}return added;}
  resolveLocation(candidate){const family=familyOf(candidate),category=String(candidate?.category??"").toLowerCase();let y=surfaceY(this.generator,candidate.x,candidate.z,candidate.y);if(category.includes("underground")||family.includes("ancient_city")||family.includes("trial_chambers"))y=Math.max(-32,y-24);if(category.includes("water")||family.includes("shipwreck")||family.includes("underwater")||family.includes("coral"))y=Math.max(0,y-8);return{x:Math.floor(candidate.x),y,z:Math.floor(candidate.z)};}
  async process(){if(!this.generator||!this.pending.length)return{planned:0,placed:0,skipped:0};const dimension=world.getDimension(this.dimensionId);let planned=0,placed=0,skipped=0;for(let i=0;i<this.maxPlansPerTick&&this.pending.length;i++){const job=this.pending.shift();this.pendingKeys.delete(job.key);if(this.completed.has(job.key))continue;const seed=`${this.seed}:${job.setId}:${Math.floor(job.x/16)}:${Math.floor(job.z/16)}`,plan=this.planner.planAround(job.setId,{x:job.x,y:128,z:job.z},seed,{radius:this.radius,count:this.maxPlacementsPerTick});if(!plan?.placements?.length){this.completed.add(job.key);this.save();continue;}planned++;for(const candidate of plan.placements.slice(0,this.maxPlacementsPerTick)){const id=candidate.structure;if(!id)continue;const placementKey=`${job.setId}:${id}:${Math.floor(candidate.x/16)}:${Math.floor(candidate.z/16)}`;if(this.completed.has(placementKey)||this.generator.placedStructureKeys?.has(placementKey))continue;const location=this.resolveLocation(candidate);let result;try{const family=familyOf(candidate),isJigsaw=Boolean(candidate.jigsaw||candidate.assetKind==="jigsaw"||family);if(isJigsaw&&this.generator.native?.placeJigsawStructure)result=this.generator.native.placeJigsawStructure(id,location,{ignoreStartHeight:true,includeEntities:true,keepJigsaws:false,seed:candidate.seed},placementKey,2);else result=await this.generator.placement.placeTemplate(id,dimension,location,{rotation:"None",overlapPadding:2,seed:candidate.seed},placementKey);}catch(error){result={placed:false,reason:"exception",error:String(error)};}if(result?.placed){placed++;this.completed.add(placementKey);this.generator.placedStructureKeys?.add(placementKey);}else skipped++;}this.completed.add(job.key);this.active.set(job.key,{planned:true,timestamp:Date.now()});this.save();}return{planned,placed,skipped};}
  tick(){if(!this.generator)return;this.refresh();for(const player of world.getAllPlayers())if(player.dimension?.id===this.dimensionId)this.enqueueAround(player.location.x,player.location.z);}
}
