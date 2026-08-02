import { JigsawRegistry } from "./JigsawRegistry.js";

function hashSeed(seed) { let h=2166136261>>>0; for(const c of String(seed??0)){h^=c.charCodeAt(0);h=Math.imul(h,16777619)>>>0;} return h>>>0; }
function random(seed){let s=hashSeed(seed)||1;return()=>{s^=s<<13;s^=s>>>17;s^=s<<5;s>>>=0;return s/4294967296;};}
function weighted(entries,r){if(!entries.length)return null;const total=entries.reduce((n,e)=>n+Math.max(0,Number(e.weight??e.inclusion_weight??1)),0);if(total<=0)return entries[0];let c=r()*total;for(const e of entries){c-=Math.max(0,Number(e.weight??e.inclusion_weight??1));if(c<0)return e;}return entries.at(-1);}
function normalizePlacement(definition){const p=definition?.placement??definition?.placement_settings??definition?.placementSettings??definition??{};return{type:p.type??p.placement_type??"minecraft:random_spread",salt:Number(p.salt??0),spacing:Math.max(1,Number(p.spacing??p.grid_spacing??34)),separation:Math.max(0,Number(p.separation??p.min_separation??8)),spread_type:p.spread_type??p.spreadType??"linear",frequency:Number(p.frequency??1),frequencyReductionMethod:p.frequency_reduction_method??p.frequencyReductionMethod??null};}
function cellOffset(r,span,spread){if(span<=0)return 0;if(spread==="triangular")return Math.floor((r()*span+r()*span)/2);return Math.floor(r()*span);}

export class StructureSetGenerator{
 constructor(registry=new JigsawRegistry()){this.registry=registry;}
 get(id){return this.registry.structureSet(id);}
 entries(id){const s=this.get(id);return s?(s.structures??s.elements??s.entries??[]):[];}
 plan(id,seed,options={}){
  const set=this.get(id);if(!set)return{ok:false,errors:[`Missing structure set: ${id}`],placements:[]};
  const p=normalizePlacement(set),structures=this.entries(id);if(!structures.length)return{ok:false,errors:[`${id}: structure set has no structures`],placements:[]};
  const r=random(`${seed}:${p.salt}`),origin=options.origin??{x:0,y:0,z:0},count=Math.max(1,Number(options.count??1)),placements=[];
  const spacing=p.spacing,separation=Math.min(p.separation,spacing-1),span=Math.max(1,spacing-separation),frequency=Math.max(0,Math.min(1,p.frequency)),used=new Set();
  for(let i=0;i<count;i++){
   if(frequency<1&&r()>frequency)continue;
   let selected=null,cell=null;
   for(let attempt=0;attempt<64;attempt++){const gx=Math.floor(r()*2000)-1000,gz=Math.floor(r()*2000)-1000,key=`${gx},${gz}`;if(used.has(key))continue;used.add(key);cell={gx,gz};selected=weighted(structures,r);break;}
   if(!selected||!cell)continue;
   const structure=selected.structure??selected.id??selected.location??selected.name,x=origin.x+cell.gx*spacing+cellOffset(r,span,p.spread_type),z=origin.z+cell.gz*spacing+cellOffset(r,span,p.spread_type);
   placements.push({structure,x,y:origin.y,z,cell,salt:p.salt,spacing,separation,spread_type:p.spread_type,weight:Number(selected.weight??1),seed:hashSeed(`${seed}:${id}:${i}:${x}:${z}`)});
  }
  return{ok:placements.length>0,structureSet:id,seed,placement:p,placements,errors:[]};
 }
 planAround(id,center,seed,options={}){const radius=Math.max(1,Number(options.radius??512)),spacing=Math.max(1,Number(options.spacing??this.get(id)?.placement?.spacing??34)),count=Math.max(1,Number(options.count??Math.floor((radius*2/spacing)**2*0.15)));return this.plan(id,seed,{...options,count,origin:{x:center.x-radius,y:center.y??0,z:center.z-radius}});}
 materialize(plan,callback){if(!plan?.placements)return[];return plan.placements.map((p,i)=>{const record={...p,index:i,seed:p.seed??hashSeed(`${plan.seed}:${plan.structureSet}:${i}:${p.x}:${p.z}`)};if(callback)callback(record);return record;});}
}
