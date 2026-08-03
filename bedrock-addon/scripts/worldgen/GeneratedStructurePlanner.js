import { createGeneratedWorldgenBridge } from "./GeneratedWorldgenBridge.js";
import { StructureCategory } from "./StructureRegistry.js";

const DEFAULT_FOOTPRINT={x:32,y:24,z:32};
const NATIVE_LEGACY=new Set(["village","bastion","mansion","pillageroutpost"]);

function familyOf(id){const s=String(id??"").toLowerCase();for(const f of ["village","bastion","mansion","pillageroutpost","trail_ruins","trial_chambers","ancient_city","stronghold","mineshaft","monument","shipwreck","underwater_ruin","ruin","endcity","igloo","ruined_portal","fossils"]){if(s.includes(f))return f;}return null;}
function footprintFromMetadata(m){const d=m?.max_distance_from_center;if(d)return{x:Math.max(16,Number(d.horizontal??80)*2),y:Math.max(16,Number(d.vertical??80)*2),z:Math.max(16,Number(d.horizontal??80)*2)};return DEFAULT_FOOTPRINT;}

/**
 * Single source of truth for structure planning. All structure-set/Jigsaw
 * metadata originates from generated/jigsaw-data.js. Hardcoded values are
 * used only for legacy systems that Bedrock's data-driven Jigsaw JSON cannot
 * replace (notably villages and bastions).
 */
export class GeneratedStructurePlanner {
 constructor({bridge=null,registry=null,generator=null}={}){this.bridge=bridge??createGeneratedWorldgenBridge({registry});this.generator=generator;this.cache=new Map();}
 metadata(id){return this.bridge.resolveStructureMetadata(id);}
 sets(){return Object.keys(this.bridge.data.structure_sets??this.bridge.data.structureSets??{});}
 structuresInSet(id){return this.bridge.resolveStructureSetMetadata(id).structures??[];}
 planSet(id,center,seed,options={}){return this.bridge.candidateFromStructureSet(id,center,seed,options);}
 allNativeCandidates(center,seed,options={}){const result=[];for(const id of this.sets())result.push(...this.planSet(id,center,seed,options));return result;}
 isLegacy(id){return NATIVE_LEGACY.has(familyOf(id));}
 planCandidate(candidate,host={}){
  const m=this.metadata(candidate.structure??candidate.id),legacy=this.isLegacy(candidate.structure??candidate.id);
  return {
   ...candidate,
   metadata:m,
   family:familyOf(candidate.structure??candidate.id),
   category:candidate.category??this.categoryFor(m),
   footprint:candidate.footprint??footprintFromMetadata(m),
   terrain_adaptation:m.terrain_adaptation,
   heightmap_projection:m.heightmap_projection,
   start_pool:m.start_pool,
   max_depth:m.max_depth,
   native:this.bridge.isNativeSupported(candidate.structure??candidate.id)&&!legacy,
   legacy,
   fallback:this.bridge.shouldUseFallback(candidate.structure??candidate.id)||legacy,
   terrainContext:this.bridge.terrainContextForCandidate(candidate,host)
  };
 }
 categoryFor(m){const step=String(m?.step??"").toLowerCase();if(step.includes("underground"))return StructureCategory.UNDERGROUND;if(step.includes("water")||step.includes("ocean"))return StructureCategory.WATER;if(step.includes("stronghold"))return StructureCategory.STRONGHOLD;return StructureCategory.SURFACE_SKY;}
 selectBest(center,seed,options={}){
  const candidates=this.allNativeCandidates(center,seed,options).map(c=>this.planCandidate(c,options.host));
  candidates.sort((a,b)=>Math.hypot(a.x-center.x,a.z-center.z)-Math.hypot(b.x-center.x,b.z-center.z));
  return candidates[0]??null;
 }
 validateAll(){const results=[];for(const id of Object.keys(this.bridge.data.structures??{}))results.push({id,...this.bridge.validate(id)});return results;}
 snapshot(){return{sets:this.sets().length,structureCount:Object.keys(this.bridge.data.structures??{}).length,nativeCandidates:this.allNativeCandidates({x:0,y:0,z:0},0,{radius:0}).length,legacy:[...NATIVE_LEGACY]};}
}
export function createGeneratedStructurePlanner(options){return new GeneratedStructurePlanner(options);}
