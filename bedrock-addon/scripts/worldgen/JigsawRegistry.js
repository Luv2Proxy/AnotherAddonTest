import { getGeneratedJigsawData, generatedTemplateId } from "./JigsawDataLoader.js";

function normalizeWeight(v){return Math.max(0,Number(v??1));}
function unwrap(v){return v?.definition??v??null;}

export class JigsawRegistry{
 constructor(data=getGeneratedJigsawData()){this.load(data);}
 clear(){this.pieces=new Map();this.pools=new Map();this.structures=new Map();this.processors=new Map();this.structureSets=new Map();this.resolvedTemplates=new Map();this.templateAliases=new Map();this.connectorIndex=new Map();this.native=new Map();return this;}
 load(data=getGeneratedJigsawData()){
  this.clear();if(!data||typeof data!=="object")return this;
  const put=(map,table)=>{for(const[id,value]of Object.entries(table??{})){const k=generatedTemplateId(id);if(k)map.set(k,unwrap(value));}};
  put(this.pieces,data.pieces);put(this.pools,data.template_pools??data.pools);put(this.structures,data.structures??data.jigsaw_structures);put(this.processors,data.processors);put(this.structureSets,data.structure_sets??data.structureSets);put(this.resolvedTemplates,data.resolved_templates);put(this.templateAliases,data.template_aliases);
  const connectors=data.connectors??data.jigsaw_connectors??{};for(const[name,entries]of Object.entries(connectors))this.connectorIndex.set(name,Array.isArray(entries)?entries:[]);
  for(const[k,table]of Object.entries(data.native??{}))for(const[id,value]of Object.entries(table??{})){const key=`${k}:${generatedTemplateId(id)}`;this.native.set(key,value);}
  return this;
 }
 piece(id){return this.pieces.get(generatedTemplateId(id))??null;}
 pool(id){return this.pools.get(generatedTemplateId(id))??null;}
 structure(id){return this.structures.get(generatedTemplateId(id))??null;}
 processor(id){return this.processors.get(generatedTemplateId(id))??null;}
 structureSet(id){return this.structureSets.get(generatedTemplateId(id))??null;}
 connectors(id){const p=this.piece(id);return p?.connectors??p?.jigsaws??[];}
 connectorsByName(name){return this.connectorIndex.get(name)??[];}
 poolElements(id){return this.pool(id)?.elements??[];}
 resolvedTemplate(id){return this.resolvedTemplates.get(generatedTemplateId(id))??null;}
 alias(id){return this.templateAliases.get(generatedTemplateId(id))??null;}
 fallback(id){return this.pool(id)?.fallback??null;}
 nativeJson(kind,id){return this.native.get(`${kind}:${generatedTemplateId(id)}`)??null;}
 candidates(poolId,targetName=null){
  const pool=this.pool(poolId);if(!pool)return[];const result=[];
  for(const wrapper of pool.elements??[]){const element=wrapper?.element??wrapper;if(!element||typeof element!=="object")continue;const type=element.element_type??element.elementType;
   if(type==="minecraft:empty_pool_element"||type==="minecraft:list_pool_element"){result.push({weight:normalizeWeight(wrapper?.weight),element,targetName});continue;}
   if((type==="minecraft:single_pool_element"||type==="minecraft:legacy_single_pool_element")&&element.location){result.push({weight:normalizeWeight(wrapper?.weight),element,targetName,piece:this.piece(element.location),resolvedPath:this.resolvedTemplate(element.location),processor:this.processor(element.processors??element.processor),projection:element.projection??"rigid"});}
  }
  return result;
 }
 weightedCandidates(poolId,random=Math.random(),targetName=null){const c=this.candidates(poolId,targetName);if(!c.length)return null;const total=c.reduce((s,x)=>s+x.weight,0);if(total<=0)return c[0];let cursor=Math.max(0,Math.min(.999999999,Number(random)))*total;for(const x of c){cursor-=x.weight;if(cursor<0)return x;}return c.at(-1);}
 findPieceByLocation(location){const key=generatedTemplateId(location);if(this.pieces.has(key))return this.pieces.get(key);for(const[id,path]of this.resolvedTemplates)if(path===location||String(path).replace(/\\/g,"/").replace(/\.mcstructure$/i,"")===String(location).replace(/\\/g,"/"))return this.pieces.get(id)??null;return null;}
 jigsawRoot(family){for(const[id,s]of this.structures){const f=String(family??"").replace(/\\/g,"/");if(id.includes(f)||s?.start_pool?.includes(f))return{id,definition:s};}return null;}
 composite(family){const out={};for(const[id,p]of this.pieces){const f=String(family??"").replace(/\\/g,"/");if(!id.includes(f))continue;const role=p?.role??p?.metadata?.role??"structure";(out[role]??=[]).push({id,piece:p,resolvedPath:this.resolvedTemplate(id)});}return out;}
 validatePoolGraph(poolId,maxDepth=20){const errors=[],visited=new Set(),stack=[];const visit=(id,depth)=>{if(!id||depth>maxDepth)return;const key=generatedTemplateId(id);if(visited.has(key))return;visited.add(key);const pool=this.pool(key);if(!pool){errors.push(`Missing pool: ${key}`);return;}if(pool.fallback&&!this.pool(pool.fallback))errors.push(`${key}: missing fallback ${pool.fallback}`);for(const c of this.candidates(key)){const location=c.element?.location;if(location&&!c.piece&&!c.resolvedPath)errors.push(`${key}: missing piece ${location}`);if(c.piece){for(const j of c.piece.connectors??c.piece.jigsaws??[]){const target=j.pool??j.target_pool??j.targetPool;if(target)visit(target,depth+1);}}}};visit(poolId,0);return{valid:errors.length===0,errors,pools:[...visited],stack};}
 validateStructure(id,maxDepth=20){const s=this.structure(id);if(!s)return{valid:false,errors:[`Missing structure: ${id}`]};const start=s.start_pool??s.startPool;if(!start)return{valid:false,errors:[`${id}: missing start_pool`]};return{...this.validatePoolGraph(start,maxDepth),identifier:generatedTemplateId(id),definition:s,startPool:start};}
 snapshot(){return{pieces:this.pieces.size,pools:this.pools.size,structures:this.structures.size,processors:this.processors.size,structureSets:this.structureSets.size,connectorNames:this.connectorIndex.size,resolvedTemplates:this.resolvedTemplates.size,aliases:this.templateAliases.size,native:this.native.size};}
}
export function structureLocationToId(location){return generatedTemplateId(location);}
