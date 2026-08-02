import { JigsawRegistry } from "./JigsawRegistry.js";

function hashSeed(seed) {
  let h = 2166136261 >>> 0;
  for (const c of String(seed ?? 0)) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619) >>> 0; }
  return h >>> 0;
}
function random(seed) { let s = hashSeed(seed) || 1; return () => { s ^= s << 13; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; }; }
function weighted(entries, r) { if (!entries.length) return null; const total = entries.reduce((n,e)=>n+Math.max(0,Number(e.weight??e.inclusion_weight??1)),0); if(total<=0)return entries[0]; let c=r()*total; for(const e of entries){c-=Math.max(0,Number(e.weight??e.inclusion_weight??1));if(c<0)return e;}return entries.at(-1); }
function normalizePlacement(definition) {
  const p=definition?.placement??definition?.placement_settings??definition?.placementSettings??definition??{};
  return { type:p.type??p.placement_type??"random_spread", salt:Number(p.salt??0), spacing:Math.max(1,Number(p.spacing??p.grid_spacing??32)), separation:Math.max(0,Number(p.separation??p.min_separation??8)), frequency:Number(p.frequency??1), frequencyReductionMethod:p.frequency_reduction_method??p.frequencyReductionMethod??null };
}

export class StructureSetGenerator {
  constructor(registry=new JigsawRegistry()){this.registry=registry;}
  get(id){return this.registry.structureSet(id);}
  entries(id){const s=this.get(id);return s?(s.structures??s.elements??s.entries??[]):[];}

  plan(id,seed,options={}){
    const set=this.get(id); if(!set)return{ok:false,errors:[`Missing structure set: ${id}`],placements:[]};
    const placement=normalizePlacement(set),structures=this.entries(id); if(!structures.length)return{ok:false,errors:[`${id}: structure set has no structures`],placements:[]};
    const r=random(`${seed}:${placement.salt}`),count=Math.max(1,Number(options.count??1)),origin=options.origin??{x:0,y:0,z:0},placements=[];
    const usedCells=new Set(), minDistance=Math.max(0,Number(options.minDistance??placement.separation));
    const frequency=Math.max(0,Math.min(1,placement.frequency));
    for(let i=0;i<count;i++){
      if(frequency<1&&r()>frequency)continue;
      let selected=null,cell=null;
      for(let attempt=0;attempt<64;attempt++){
        const gx=Math.floor((r()*2000)-1000),gz=Math.floor((r()*2000)-1000),key=`${gx},${gz}`;
        if(usedCells.has(key))continue;
        selected=weighted(structures,r);cell={gx,gz};usedCells.add(key);break;
      }
      if(!selected||!cell)continue;
      const structure=selected.structure??selected.id??selected.location??selected.name;
      const jitter=Math.max(0,placement.spacing-placement.separation);
      placements.push({structure,x:origin.x+cell.gx*placement.spacing+Math.floor(r()*Math.max(1,jitter)),y:origin.y,z:origin.z+cell.gz*placement.spacing+Math.floor(r()*Math.max(1,jitter)),cell,salt:placement.salt,spacing:placement.spacing,separation:placement.separation});
    }
    return{ok:placements.length>0,structureSet:id,seed,placement,placements,errors:[]};
  }

  /** Generate candidates around a specific island/chunk instead of an unbounded world origin. */
  planAround(id,center,seed,options={}){
    const radius=Math.max(1,Number(options.radius??512)), spacing=Math.max(1,Number(options.spacing??this.get(id)?.placement?.spacing??32));
    const count=Math.max(1,Number(options.count??Math.floor((radius*2/spacing)**2*0.15)));
    return this.plan(id,seed,{...options,count,origin:{x:center.x-radius,y:center.y??0,z:center.z-radius}});
  }

  /** Convert planned placements into callback-ready records with deterministic seeds. */
  materialize(plan,callback){
    if(!plan?.placements)return[];
    return plan.placements.map((p,i)=>{const record={...p,index:i,seed:hashSeed(`${plan.seed}:${plan.structureSet}:${i}:${p.x}:${p.z}`)};if(callback)callback(record);return record;});
  }
}
