export class StructurePieceModel {
  constructor({id,role,template,position,rotation="None",bounds=null,expectedDy=null,metadata={}}={}){this.id=id;this.role=role??"unknown";this.template=template;this.position={x:Math.floor(position?.x??0),y:Math.floor(position?.y??0),z:Math.floor(position?.z??0)};this.rotation=rotation;this.bounds=bounds;this.expectedDy=expectedDy;this.metadata=metadata;}
  translated(dx,dy,dz){return new StructurePieceModel({...this,position:{x:this.position.x+dx,y:this.position.y+dy,z:this.position.z+dz},bounds:this.bounds?{min:{x:this.bounds.min.x+dx,y:this.bounds.min.y+dy,z:this.bounds.min.z+dz},max:{x:this.bounds.max.x+dx,y:this.bounds.max.y+dy,z:this.bounds.max.z+dz}}:null});}
}
export class StructurePieceGraph {
 constructor(type,origin){this.type=type;this.origin=origin;this.pieces=[];}
 add(piece){this.pieces.push(piece);return piece;}
 bounds(){if(!this.pieces.length)return null;let min={x:Infinity,y:Infinity,z:Infinity},max={x:-Infinity,y:-Infinity,z:-Infinity};for(const p of this.pieces){const b=p.bounds??{min:{x:p.position.x-4,y:p.position.y-4,z:p.position.z-4},max:{x:p.position.x+4,y:p.position.y+4,z:p.position.z+4}};min.x=Math.min(min.x,b.min.x);min.y=Math.min(min.y,b.min.y);min.z=Math.min(min.z,b.min.z);max.x=Math.max(max.x,b.max.x);max.y=Math.max(max.y,b.max.y);max.z=Math.max(max.z,b.max.z);}return{min,max};}
}
export class PieceRoleAdapter { constructor(map={}){this.map=map;} role(piece){return this.map[piece?.id]??piece?.role??"unknown";} }
