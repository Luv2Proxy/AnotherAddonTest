import { JigsawRegistry } from "./JigsawRegistry.js";

function hashSeed(seed){let h=2166136261>>>0;for(const c of String(seed??0)){h^=c.charCodeAt(0);h=Math.imul(h,16777619)>>>0;}return h>>>0;}
function rng(seed){let s=hashSeed(seed)||1;return()=>{s^=s<<13;s^=s>>>17;s^=s<<5;s>>>=0;return s/4294967296;};}
function weighted(entries,r){if(!entries.length)return null;const total=entries.reduce((n,e)=>n+Math.max(0,Number(e.weight??e.inclusion_weight??1)),0);if(total<=0)return entries[0];let c=r()*total;for(const e of entries){c-=Math.max(0,Number(e.weight??e.inclusion_weight??1));if(c<0)return e;}return entries.at(-1);}
function placement(definition){const p=definition?.placement??definition?.placement_settings??definition?.placementSettings??definition??{};return{type:p.type??p.placement_type??"minecraft:random_spread",salt:Number(p.salt??0),spacing:Math.max(1,Math.floor(Number(p.spacing??p.grid_spacing??34))),separation:Math.max(0,Math.floor(Number(p.separation??p.min_separation??8))),spread_type:p.spread_type??p.spreadType??"linear"};}
function spreadOffset(r,span,type){if(span<=0)return 0;return type==="triangular"?Math.floor((r()*span+r()*span)/2):Math.floor(r()*span);}
function floorDiv(n,d){return Math.floor(n/d);}

export class StructureSetGenerator{
 constructor(registry=new JigsawRegistry()){this.registry=registry;}
 get(id){return this.registry.structureSet(id);}
 entries(id){const s=this.get(id);return s?(s.structures??s.elements??s.entries??[]):[];}
 cell(seed,setId,cx,cz,p){const r=rng(`${seed}:${setId}:${cx}:${cz}:${p.salt}`);const span=Math.max(1,p.spacing-p.separation);return{x:cx*p.spacing+Math.floor(p.separation/2)+spreadOffset(r,span,p.spread_type),z:cz*p.spacing+Math.floor(p.separation/2)+spreadOffset(r,span,p.spread_type),seed:hashSeed(`${seed}:${setId}:${cx}:${cz}:${p.salt}`)};}
 plan(id,seed,options={}){
  const set=this.get(id);if(!set)return{ok:false,errors:[`Missing structure set: ${id}`],placements:[]};
  const p=placement(set),structures=this.entries(id);if(!structures.length)return{ok:false,errors:[`${id}: structure set has no structures`],placements:[]};
  if(p.type!=="minecraft:random_spread"&&p.type!=="random_spread")return{ok:false,errors:[`${id}: unsupported placement type ${p.type}`],placements:[]};
  const origin=options.origin??{x:0,y:0,z:0},minX=Number(options.minX??floorDiv(origin.x,p.spacing)),maxX=Number(options.maxX??minX),minZ=Number(options.minZ??floorDiv(origin.z,p.spacing)),maxZ=Number(options.maxZ??minZ),r=rng(`${seed}:${id}:selection`),placements=[];
  for(let cx=minX;cx<=maxX;cx++)for(let cz=minZ;cz<=maxZ;cz++){
   const point=this.cell(seed,id,cx,cz,p),selected=weighted(structures,r);if(!selected)continue;
   const structure=selected.structure??selected.id??selected.location??selected.name;if(!structure)continue;
   placements.push({structure,x:origin.x+point.x-origin.x%p.spacing,y:origin.y,z:origin.z+point.z-origin.z%p.spacing,cell:{x:cx,z:cz},salt:p.salt,spacing:p.spacing,separation:p.separation,spread_type:p.spread_type,weight:Number(selected.weight??1),seed:point.seed});
  }
  return{ok:true,structureSet:id,seed,placement:p,placements,errors:[]};
 }
 planAround(id,center,seed,options={}){
  const p=placement(this.get(id)??{}),radius=Math.max(1,Number(options.radius??512)),minX=floorDiv(center.x-radius,p.spacing),maxX=floorDiv(center.x+radius,p.spacing),minZ=floorDiv(center.z-radius,p.spacing),maxZ=floorDiv(center.z+radius,p.spacing);
  const plan=this.plan(id,seed,{...options,origin:{x:0,y:center.y??0,z:0},minX,maxX,minZ,maxZ});
  const filtered=plan.placements.filter(x=>Math.abs(x.x-center.x)<=radius&&Math.abs(x.z-center.z)<=radius);if(options.count&&filtered.length>options.count){const r=rng(`${seed}:${id}:limit`);for(let i=filtered.length-1;i>0;i--){const j=Math.floor(r()*(i+1));[filtered[i],filtered[j]]=[filtered[j],filtered[i]];}plan.placements=filtered.slice(0,options.count);}return plan;
 }
 materialize(plan,callback){if(!plan?.placements)return[];return plan.placements.map((p,i)=>{const record={...p,index:i,seed:p.seed??hashSeed(`${plan.seed}:${plan.structureSet}:${i}:${p.x}:${p.z}`)};if(callback)callback(record);return record;});}
}
