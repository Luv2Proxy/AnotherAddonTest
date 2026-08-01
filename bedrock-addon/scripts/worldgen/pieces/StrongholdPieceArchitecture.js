import { StructurePieceModel, StructurePieceGraph } from "./StructurePieceModel.js";

export const StrongholdPieceKind=Object.freeze({START:"start",CORRIDOR:"corridor",CROSSING:"crossing",ROOM:"room",LIBRARY:"library",PORTAL_ROOM:"portal_room"});
export class StrongholdPieceFactory {
 constructor(prefix="sky_archipelago:stronghold/"){this.prefix=prefix;}
 create(type,x,y,z,rotation=0,index=0){const size={start:[6,4,6],corridor:[4,3,8],crossing:[6,4,6],room:[8,4,8],library:[7,6,10],portal_room:[10,5,12]}[type]??[4,3,6], [w,h,d]=size;return new StructurePieceModel({id:`stronghold_${index}`,role:type,template:`${this.prefix}${type}`,position:{x,y,z},rotation:["None","90_degrees","180_degrees","270_degrees"][rotation%4],bounds:{min:{x:x-w,y:y-h,z:z-d},max:{x:x+w,y:y+h,z:z+d}},metadata:{index}});}
}
export class StrongholdPieceGraphBuilder {
 constructor(seed,factory=new StrongholdPieceFactory()){this.seed=BigInt(seed);this.factory=factory;}
 random(x,z,i){let v=this.seed^BigInt(x)*0x9E3779B97F4A7C15n^BigInt(z)*0xC2B2AE3D27D4EB4Fn^BigInt(i);v^=v>>29n;v*=0x94D049BB133111EBn;v^=v>>31n;return Number(v&0xffffffffn)/4294967296;}
 build(origin,{pieces=18,radius=48}={}){const g=new StructurePieceGraph("stronghold",origin),nodes=[{x:origin.x,y:origin.y,z:origin.z,rotation:0}],start=this.factory.create("start",origin.x,origin.y,origin.z,0,0);g.add(start);for(let i=1;i<pieces;i++){const n=nodes[Math.floor(this.random(origin.x+i,origin.z-i,i)*nodes.length)],turn=this.random(n.x,n.z,i+20);const dir=turn<.25?(n.rotation+1)%4:turn<.5?(n.rotation+3)%4:n.rotation,len=6+Math.floor(this.random(n.x+i,n.z,i+40)*9),dx=[1,0,-1,0][dir],dz=[0,1,0,-1][dir];let x=n.x+dx*len,z=n.z+dz*len;if(Math.hypot(x-origin.x,z-origin.z)>radius){x=origin.x;z=origin.z;}const roll=this.random(x,z,i+60),type=i===pieces-1?StrongholdPieceKind.PORTAL_ROOM:roll<.12?StrongholdPieceKind.LIBRARY:roll<.3?StrongholdPieceKind.CROSSING:roll<.42?StrongholdPieceKind.ROOM:StrongholdPieceKind.CORRIDOR,y=n.y+Math.floor(this.random(x,z,i+80)*3)-1,p=this.factory.create(type,x,y,z,dir,i);g.add(p);nodes.push({x,y,z,rotation:dir});}return g;}
}
export class StrongholdPlacementCoordinator {
 constructor(generator){this.generator=generator;this.builder=new StrongholdPieceGraphBuilder(generator.layoutSeed);}
 build(origin,options={}){return{accepted:true,graph:this.builder.build(origin,options)};}
}

export class BufferedOceanMonumentPiece {
 constructor({levelSeed,sourceChunk,minX,minZ,finalMinY,bodyFloorY,waterTopY,direction,footprint,template="sky_archipelago:ocean_monument"}={}){this.levelSeed=levelSeed;this.sourceChunk=sourceChunk;this.minX=minX;this.minZ=minZ;this.finalMinY=finalMinY;this.bodyFloorY=bodyFloorY;this.waterTopY=waterTopY;this.direction=direction;this.footprint=footprint;this.template=template;this.bounds={min:{x:minX,y:finalMinY,z:minZ},max:{x:minX+57,y:finalMinY+22,z:minZ+57}};}
 serialize(){return{levelSeed:this.levelSeed,sourceChunk:this.sourceChunk,minX:this.minX,minZ:this.minZ,finalMinY:this.finalMinY,bodyFloorY:this.bodyFloorY,waterTopY:this.waterTopY,direction:this.direction,footprint:this.footprint};}
}
export class OceanMonumentBufferedPieceFactory {
 create({levelSeed,sourceChunk,minX,minZ,finalMinY,bodyFloorY,waterTopY,direction,footprint}){return new BufferedOceanMonumentPiece({levelSeed,sourceChunk,minX,minZ,finalMinY,bodyFloorY,waterTopY,direction,footprint});}
}
export class OceanMonumentPiecePlacement {
 constructor(adapter,overlap){this.adapter=adapter;this.overlap=overlap;}
 place(piece,dimension,id){if(!this.overlap.canReserve(id,piece.bounds,2))return{placed:false,reason:"overlap"};const result=this.adapter.placeTemplate(piece.template,dimension,{x:piece.minX,y:piece.finalMinY,z:piece.minZ},{rotation:["None","90_degrees","180_degrees","270_degrees"][piece.direction%4]},id,0);if(result?.placed)this.overlap.reserve(id,piece.bounds,2,{type:"buffered_ocean_monument",bodyFloorY:piece.bodyFloorY,waterTopY:piece.waterTopY,footprint:piece.footprint});return result;}
}
