import { world } from "@minecraft/server";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { JigsawLayoutPlanner } from "./JigsawLayoutPlanner.js";
import { StructureSetGenerator } from "./StructureSetGenerator.js";
import { getGeneratedJigsawData, generatedStructure } from "./JigsawDataLoader.js";
import { ProcessorEngine } from "./ProcessorEngine.js";
import { TerrainProjection } from "./TerrainProjection.js";

export class JigsawGenerator {
  constructor(dimension, options = {}) {
    this.dimension=dimension; this.manager=world.structureManager;
    this.registry=options.registry??new JigsawRegistry(options.data??getGeneratedJigsawData());
    this.planner=options.planner??new JigsawLayoutPlanner(this.registry,options);
    this.structureSets=options.structureSets??new StructureSetGenerator(this.registry);
    this.overlap=options.overlapGuard??null; this.layoutSeed=options.layoutSeed??0;
    this.processors=options.processorEngine??new ProcessorEngine(options.data??getGeneratedJigsawData(),this.layoutSeed);
    this.projection=options.projection??new TerrainProjection(dimension,options);
  }
  dataSnapshot(){return this.registry.snapshot();}
  definition(identifier){return generatedStructure(identifier)??this.registry.structure(identifier);}
  resolveStructureIdentifier(identifier){const v=String(identifier??"");if(!v)return null;if(this.registry.structure(v)||this.definition(v))return v;return v.includes(":")?v:`minecraft:${v}`;}
  plan(identifier,location={x:0,y:0,z:0},seed=this.layoutSeed,options={}){return this.planner.planStructure(this.resolveStructureIdentifier(identifier),location,seed,options);}
  planStructureSet(setId,seed=this.layoutSeed,options={}){return this.structureSets.plan(setId,seed,options);}
  async projectPiece(origin,size,projection="rigid",options={}){return this.projection.project(origin,size,projection,options);}
  async projectPlan(plan,options={}){if(!plan?.pieces)return plan;const projection=options.projection??plan.projection??"rigid";if(projection==="rigid")return plan;const pieces=[];for(const piece of plan.pieces){const size=piece.size??piece.bounds?.size??{x:1,y:1,z:1};const origin=piece.origin??piece.location??{x:0,y:0,z:0};pieces.push({...piece,origin:await this.projectPiece(origin,size,projection,{...options,piece})});}return{...plan,pieces,projection};}
  placeStructure(identifier,location,options={}){const resolved=this.resolveStructureIdentifier(identifier);if(typeof this.manager?.placeJigsawStructure!=="function")return{placed:false,native:false,reason:"native_jigsaw_structure_api_unavailable",plan:this.plan(resolved,location,options.seed??this.layoutSeed,options)};const bounds=this.manager.placeJigsawStructure(resolved,this.dimension,location,{includeEntities:true,keepJigsaws:false,...options});return{placed:true,native:true,identifier:resolved,location,bounds};}
  placePool(pool,target,maxDepth,location,options={}){const poolId=String(pool).includes(":")?pool:`minecraft:${pool}`,depth=Math.max(1,Math.min(20,Number(maxDepth)||1));if(typeof this.manager?.placeJigsaw!=="function")return{placed:false,native:false,reason:"native_jigsaw_pool_api_unavailable",plan:this.planner.planPool(poolId,location,options.seed??this.layoutSeed,{...options,maxDepth:depth})};const bounds=this.manager.placeJigsaw(poolId,target??"",depth,this.dimension,location,{includeEntities:true,keepJigsaws:false,...options});return{placed:true,native:true,pool:poolId,target,maxDepth:depth,location,bounds};}
  processBlock(block,processorList,context={}){return this.processors.apply(block,{...context,processorList});}
  processTemplateBlocks(blocks,processorList,context={}){return this.processors.applyTemplateBlocks(blocks,{...context,processorList});}
  placeRoot(identifier,location,options={}){return this.placeStructure(identifier,location,options);}
  placeByPool(pool,target="",maxDepth=5,location={x:0,y:0,z:0},options={}){return this.placePool(pool,target,maxDepth,location,options);}
  validatePieceGraph(identifier,maxDepth=20){const root=this.registry.piece(identifier);if(!root)return{valid:false,errors:[`Missing piece: ${identifier}`],nodes:[]};const errors=[],nodes=[],seen=new Set();const visit=(piece,depth)=>{if(!piece||depth>maxDepth)return;const k=`${piece.id??piece.source}:${depth}`;if(seen.has(k))return;seen.add(k);nodes.push(piece.id??piece.source);for(const c of piece.connectors??piece.jigsaws??[]){const pool=c.pool??c.target_pool??c.targetPool;if(!pool||pool==="unknown"){errors.push(`${piece.id??piece.source}: connector has no target pool`);continue;}const graph=this.registry.validatePoolGraph(pool,maxDepth-depth);errors.push(...graph.errors.map(e=>`${piece.id??piece.source}: ${e}`));for(const candidate of this.registry.candidates(pool,c.name))if(candidate.piece)visit(candidate.piece,depth+1);}};visit(root,0);return{valid:errors.length===0,errors,nodes};}
  validateStructure(identifier,maxDepth=20){const d=this.definition(identifier);if(!d)return{valid:false,errors:[`Missing jigsaw structure: ${identifier}`],nodes:[]};const startPool=d.start_pool??d.startPool;if(!startPool)return{valid:false,errors:[`${identifier}: missing start_pool`],nodes:[]};return{...this.registry.validatePoolGraph(startPool,maxDepth),identifier,startPool};}
  canReserve(bounds,padding=0,key="jigsaw"){if(!this.overlap)return true;return this.overlap.canReserve?.(key,bounds,padding)??true;}
}
