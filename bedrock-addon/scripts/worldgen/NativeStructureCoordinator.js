import { StructureHostSelector } from "./StructureHostSelector.js";
import { StructureCandidateEvaluator } from "./StructureCandidateEvaluator.js";
import { StructureOverlapGuard } from "./StructureOverlapGuard.js";
import { StructureStartRelocator } from "./StructureStartRelocator.js";
import { StrongholdPlacementEngine } from "./StrongholdPlacementEngine.js";
import { DynamicUndergroundPlacement } from "./DynamicUndergroundPlacement.js";
import { NativeStructurePlacement } from "./NativeStructurePlacement.js";
import { NativeStructureAdapter } from "./NativeStructureAdapter.js";
import { StructurePlacementPolicy } from "./StructurePlacementPolicy.js";
import { StructureSupportSystem } from "./StructureSupportSystem.js";
import { StructurePiecePlacementPipeline } from "./pieces/StructurePiecePlacementPipeline.js";
import { MineshaftPlacementCoordinator } from "./pieces/MineshaftPieceArchitecture.js";
import { JigsawPlacementCoordinator } from "./pieces/JigsawPieceArchitecture.js";
import { VillageLayoutTrimmer, VillagePieceGraphAdapter } from "./pieces/VillagePieceArchitecture.js";
import { StrongholdPlacementCoordinator, OceanMonumentBufferedPieceFactory, OceanMonumentPiecePlacement } from "./pieces/StrongholdPieceArchitecture.js";

export class NativeStructureCoordinator {
  constructor(generator,dimension,overlapGuard=null){this.generator=generator;this.dimension=dimension;this.overlap=overlapGuard??new StructureOverlapGuard();this.hosts=new StructureHostSelector(generator);this.evaluator=new StructureCandidateEvaluator(generator);this.nativePlacement=new NativeStructurePlacement(null,this.hosts);this.strongholdPlanner=new StrongholdPlacementEngine(generator,this.hosts,this.evaluator,this.overlap);this.underground=new DynamicUndergroundPlacement(generator,this.nativePlacement,this.hosts,this.evaluator,this.overlap);this.adapter=new NativeStructureAdapter(dimension,generator,this.overlap);this.relocator=new StructureStartRelocator();this.policy=new StructurePlacementPolicy();this.support=new StructureSupportSystem(generator,this.policy);this.pipeline=new StructurePiecePlacementPipeline({adapter:this.adapter,overlap:this.overlap,generator});this.mineshaft=new MineshaftPlacementCoordinator(generator,this.policy);this.jigsaw=new JigsawPlacementCoordinator(generator);this.strongholdPieces=new StrongholdPlacementCoordinator(generator);this.villageTrim=new VillageLayoutTrimmer();this.villageGraph=new VillagePieceGraphAdapter();this.monumentFactory=new OceanMonumentBufferedPieceFactory();this.monumentPlacement=new OceanMonumentPiecePlacement(this.adapter,this.overlap);}
  plan(type,request){switch(type){case "stronghold":return this.strongholdPlanner.plan(request);case "mineshaft":return this.underground.planMineshaft(request);case "jigsaw":return this.underground.planJigsaw(request,request.footprint);default:return{accepted:false,reason:"unsupported_native_structure"};}}
  commit(type,plan,generatedStart=null){if(!plan?.accepted)return null;if(generatedStart){const moved=this.relocator.relocate(generatedStart,plan.target);if(!moved)return null;const id=`${type}:${plan.target.x}:${plan.target.y}:${plan.target.z}`;if(moved.bounds&&!this.overlap.canReserve(id,moved.bounds,8))return null;if(moved.bounds)this.overlap.reserve(id,moved.bounds,8,{type:"relocated_native",structureType:type});return{type,native:true,relocated:moved};}if(type==="stronghold")return this.commitStronghold(plan);if(type==="mineshaft")return this.commitMineshaft(plan);return{type,planned:true,target:plan.target};}
  commitStronghold(plan){const target=plan.target??plan.origin??plan;const built=this.strongholdPieces.build({x:target.x,y:target.y,z:target.z},{pieces:plan.pieces??18,radius:plan.radius??48});return this.pipeline.placeGraph(built.graph,{structureId:`stronghold:${target.x}:${target.z}`,dimension:this.dimension,authorityAnchorY:target.y,yLockEnabled:true,padding:8});}
  commitMineshaft(plan){const target=plan.target??plan.origin??plan;const built=this.mineshaft.build(plan.start??{valid:true,bounds:{min:{x:target.x-4,y:target.y,z:target.z-4},max:{x:target.x+4,y:target.y+4,z:target.z+4}}},{host:plan.host??null});if(!built.accepted)return built;return this.pipeline.placeGraph(built.graph,{structureId:`mineshaft:${target.x}:${target.z}`,dimension:this.dimension,authorityAnchorY:target.y,yLockEnabled:true,padding:6});}
  commitJigsaw(start,options={}){const built=this.jigsaw.build(start,options);if(!built.accepted)return built;return this.pipeline.placeGraph(built.graph,{structureId:`jigsaw:${built.anchor.x}:${built.anchor.z}`,dimension:this.dimension,authorityAnchorY:built.anchor.baseY,yLockEnabled:true,padding:2});}
  commitVillage(pieces,host,origin){const trimmed=this.villageTrim.trim(pieces,host);if(!trimmed.accepted)return trimmed;return{...trimmed,placement:this.pipeline.placeGraph(this.villageGraph.toGraph(trimmed.pieces,origin),{structureId:`village:${origin.x}:${origin.z}`,dimension:this.dimension,authorityAnchorY:origin.y,yLockEnabled:true,padding:2})};}
  commitOceanMonument(data){const piece=this.monumentFactory.create(data),result=this.monumentPlacement.place(piece,this.dimension,`monument:${piece.minX}:${piece.minZ}`);return{...result,piece:piece.serialize()};}
  snapshot(){return{overlap:this.overlap.serialize(),version:2};}restore(snapshot){this.overlap.restore(snapshot?.overlap);}
}
