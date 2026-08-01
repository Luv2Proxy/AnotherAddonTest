import { StructurePieceModel, StructurePieceGraph } from "./StructurePieceModel.js";

export class JigsawAnchorResolver {
 resolve(start){if(start?.valid!==false){const pieces=start?.pieces??[];const entry=pieces.reduce((best,p)=>!best||((p.bounds?.min?.y??Infinity)<(best.bounds?.min?.y??Infinity))?p:best,null);if(entry?.bounds)return this.fromBounds(entry.bounds,"entry_piece_center");if(start?.bounds)return this.fromBounds(start.bounds,"bounds_center_fallback");}return{x:0,baseY:0,z:0,source:"invalid_start_fallback"};}
 fromBounds(b,source){return{x:Math.floor((b.min.x+b.max.x)*.5),baseY:b.min.y,z:Math.floor((b.min.z+b.max.z)*.5),source};}
}
export class JigsawAnchorStrategy {constructor(resolver=new JigsawAnchorResolver()){this.resolver=resolver;}resolve(start){return this.resolver.resolve(start);}}
export class JigsawPieceFactory {
 constructor(prefix="sky_archipelago:jigsaw/"){this.prefix=prefix;}
 create({pool,type="piece",x,y,z,rotation=0,index=0,bounds=null,connector=null}={}){return new StructurePieceModel({id:`jigsaw_${index}`,role:type,template:`${this.prefix}${pool}/${type}`,position:{x,y,z},rotation:["None","90_degrees","180_degrees","270_degrees"][rotation%4],bounds,metadata:{pool,connector,index}});}
}
export class JigsawPieceGraphBuilder {
 constructor(seed,factory=new JigsawPieceFactory()){this.seed=BigInt(seed);this.factory=factory;}
 random(x,z,i){let v=this.seed^BigInt(x)*0x9E3779B97F4A7C15n^BigInt(z)*0xC2B2AE3D27D4EB4Fn^BigInt(i);v^=v>>29n;v*=0x94D049BB133111EBn;v^=v>>31n;return Number(v&0xffffffffn)/4294967296;}
 build(anchor,{pool="default",depth=7,maxPieces=64}={}){const g=new StructurePieceGraph("jigsaw",anchor),queue=[{x:anchor.x,y:anchor.baseY,z:anchor.z,depth,rotation:0,connector:"root"}];let index=0;while(queue.length&&index<maxPieces){const n=queue.shift();const type=n.depth<=1?"terminal":(index%5===0?"hub":"piece"),piece=this.factory.create({pool,type,x:n.x,y:n.y,z:n.z,rotation:n.rotation,index,bounds:{min:{x:n.x-4,y:n.y-3,z:n.z-4},max:{x:n.x+4,y:n.y+3,z:n.z+4}},connector:n.connector});g.add(piece);index++;if(n.depth>1){const branches=1+(this.random(n.x,n.z,index)<.35?1:0);for(let b=0;b<branches;b++){const dir=Math.floor(this.random(n.x+b,n.z-b,index+20)*4),len=7+Math.floor(this.random(n.x,n.z,index+40)*8),dx=[1,0,-1,0][dir],dz=[0,1,0,-1][dir];queue.push({x:n.x+dx*len,y:n.y+Math.floor(this.random(n.x,n.z,index+60)*3)-1,z:n.z+dz*len,depth:n.depth-1,rotation:dir,connector:`${index}:${b}`});}}}return g;}
}
export class JigsawPlacementCoordinator {
 constructor(generator){this.generator=generator;this.anchorResolver=new JigsawAnchorResolver();this.anchorStrategy=new JigsawAnchorStrategy(this.anchorResolver);this.graphBuilder=new JigsawPieceGraphBuilder(generator.layoutSeed);}
 build(start,options={}){const anchor=this.anchorStrategy.resolve(start);return{accepted:anchor.source!=="invalid_start_fallback",anchor,graph:this.graphBuilder.build(anchor,options)};}
}
