import { world } from "@minecraft/server";
import { classifyStructure, villageSupport, hamletSupport, monumentSupport, mineshaftSupport, footprint } from "./StructureSupport.js";
const DB="sky_archipelago:structure_detection_v1";
export class StructureDetection {
 constructor(settings){this.settings=settings;this.seen=new Set();this.load();}
 load(){try{const raw=world.getDynamicProperty(DB);if(typeof raw==="string")this.seen=new Set(JSON.parse(raw));}catch{this.seen=new Set();}}
 save(){world.setDynamicProperty(DB,JSON.stringify([...this.seen]));}
 key(id,x,z){return `${id}:${x>>4}:${z>>4}`;}
 scan(dim,centerX,centerZ,radius=8){const result=[],minX=centerX-radius*16,maxX=centerX+radius*16,minZ=centerZ-radius*16,maxZ=centerZ+radius*16;for(let cx=Math.floor(minX/16);cx<=Math.floor(maxX/16);cx++)for(let cz=Math.floor(minZ/16);cz<=Math.floor(maxZ/16);cz++){const x=cx*16+8,z=cz*16+8,candidate=this.infer(dim,x,z);if(candidate){const k=this.key(candidate.id,x,z);if(!this.seen.has(k)){this.seen.add(k);result.push(candidate);}}}if(result.length)this.save();return result;}
 infer(dim,x,z){const h=dim.getHeight("world_surface",x,z)-1;if(h<-63)return null;let chests=0,crafting=0,logs=0,prismarine=0,rails=0;for(let dx=-8;dx<=8;dx++)for(let dz=-8;dz<=8;dz++)for(let dy=0;dy<8;dy++){const b=dim.getBlock({x:x+dx,y:h+dy-4,z:z+dz});if(!b)continue;const id=b.typeId;if(id.includes("chest"))chests++;if(id.includes("crafting_table"))crafting++;if(id.includes("_log"))logs++;if(id.includes("prismarine"))prismarine++;if(id.includes("rail"))rails++;}if(prismarine>=8)return{id:"monument",x,z,baseY:h,footprint:footprint(x,z,16)};if(rails>=4)return{id:"mineshaft",x,z,baseY:h,points:[{x,z}],footprint:footprint(x,z,12)};if(chests>=2&&crafting>=1&&logs>=4)return{id:"village",x,z,baseY:h,footprint:footprint(x,z,12)};return null;}
 apply(dim,s){switch(classifyStructure(s.id)){case 1:villageSupport(dim,s.footprint,s.baseY);break;case 2:hamletSupport(dim,s.footprint,s.baseY);break;case 3:monumentSupport(dim,s.footprint,s.baseY);break;case 4:mineshaftSupport(dim,s.points,s.baseY);break;}}
}
