import { StructurePieceModel, StructurePieceGraph } from "./StructurePieceModel.js";

export class MineshaftAnchorResolver {
 resolve(start){if(start?.valid!==false){const p=start?.pieces?.[0];if(p?.bounds)return this.fromBounds(p.bounds,start.bounds);if(start?.bounds)return this.fromBounds(null,start.bounds);}return{x:0,baseY:0,z:0,source:"invalid_start_fallback"};}
 fromBounds(first,fallback){const b=first??fallback;if(!b)return{x:0,baseY:0,z:0,source:"invalid_start_fallback"};return{x:Math.floor((b.min.x+b.max.x)*.5),baseY:b.min.y,z:Math.floor((b.min.z+b.max.z)*.5),source:first?"first_piece_center":"bounds_center_fallback"};}
}
export class MineshaftAnchorStrategy {constructor(resolver=new MineshaftAnchorResolver()){this.resolver=resolver;}resolve(start){const a=this.resolver.resolve(start);return{...a};}}
export class MineshaftPieceFactory {
 constructor(templatePrefix="sky_archipelago:mineshaft/"){this.prefix=templatePrefix;}
 create(type,x,y,z,rotation=0,index=0){const widths={corridor:4,cross:6,room:8,stair:4};const w=widths[type]??4;return new StructurePieceModel({id:`mineshaft_${index}`,role:type,template:`${this.prefix}${type}`,position:{x,y,z},rotation:["None","90_degrees","180_degrees","270_degrees"][rotation%4],bounds:{min:{x:x-w,y:y-3,z:z-w},max:{x:x+w,y:y+3,z:z+w}},metadata:{index}});}
}
export class MineshaftPieceGraphBuilder {
 constructor(seed,factory=new MineshaftPieceFactory()){this.seed=BigInt(seed);this.factory=factory;}
 random(x,z,i){let v=this.seed^BigInt(x)*0x9E3779B97F4A7C15n^BigInt(z)*0xC2B2AE3D27D4EB4Fn^BigInt(i);v^=v>>29n;v*=0x94D049BB133111EBn;v^=v>>31n;return Number(v&0xffffffffn)/4294967296;}
 build(anchor,{pieces=24,radius=64}={}){const g=new StructurePieceGraph("mineshaft",anchor),nodes=[anchor];for(let i=0;i<pieces;i++){const n=nodes[Math.floor(this.random(anchor.x+i,anchor.z-i,i)*nodes.length)],dir=Math.floor(this.random(n.x,n.z,i+100)*4),len=6+Math.floor(this.random(n.x+i,n.z+i,i+200)*10),dx=[1,0,-1,0][dir],dz=[0,1,0,-1][dir];let x=n.x+dx*len,z=n.z+dz*len;if(Math.hypot(x-anchor.x,z-anchor.z)>radius){x=anchor.x;z=anchor.z;}const roll=this.random(x,z,i+300),type=roll<.12?"room":roll<.24?"cross":roll<.31?"stair":"corridor",p=this.factory.create(type,x,n.y??anchor.baseY,z,dir,i);g.add(p);nodes.push({x,y:p.position.y,z});}return g;}
}
export class MineshaftPlacementCoordinator {
 constructor(generator,policy){this.generator=generator;this.policy=policy;this.anchorResolver=new MineshaftAnchorResolver();this.anchorStrategy=new MineshaftAnchorStrategy(this.anchorResolver);this.graphBuilder=new MineshaftPieceGraphBuilder(generator.layoutSeed);}
 decide(start,target){const anchor=this.anchorStrategy.resolve(start),host=target?.host;let penalty=0;if(host)penalty=this.edgeSoftCapPenalty(host,anchor.x,anchor.z,target.preferredInteriorMargin??16,target.edgePenaltyWeight??1);return{accepted:penalty<1,anchor,penalty};}
 build(start,target,options={}){const d=this.decide(start,target);if(!d.accepted)return{accepted:false,...d};return{accepted:true,...d,graph:this.graphBuilder.build(d.anchor,options)};}
 edgeSoftCapPenalty(host,x,z,preferredInteriorMargin,edgePenaltyWeight){const radius=Math.max(1,host.radius??1),dx=x-host.x,dz=z-host.z,interiorMargin=radius-Math.sqrt(dx*dx+dz*dz);if(interiorMargin>=preferredInteriorMargin)return 0;const deficit=preferredInteriorMargin-interiorMargin,normalized=Math.max(0,deficit)/Math.max(1,preferredInteriorMargin);return normalized*edgePenaltyWeight;}
}
