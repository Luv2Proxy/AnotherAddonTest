// Bedrock port of the original structure placement model.
export const StructurePlacementCategory = Object.freeze({ DEFAULT: "DEFAULT", SKY: "SKY", SURFACE_SKY: "SURFACE_SKY", SMALL_SKY: "SMALL_SKY", HAMLET_SKY: "HAMLET_SKY", GROUND_VILLAGE: "GROUND_VILLAGE", STRONGHOLD: "STRONGHOLD", UNDERGROUND: "UNDERGROUND", WATER: "WATER" });
export const WaterPlacementMode = Object.freeze({ SURFACE: "SURFACE", OCEAN_FLOOR: "OCEAN_FLOOR", SUBMERGED: "SUBMERGED" });
export const LandSizeTier = Object.freeze({ SMALL: "SMALL", MEDIUM: "MEDIUM", LARGE: "LARGE" });
export class StructureFootprint {
  constructor(minX,maxX,minZ,maxZ){this.minX=minX;this.maxX=maxX;this.minZ=minZ;this.maxZ=maxZ;}
  centerX(){return Math.floor((this.minX+this.maxX)*.5)} centerZ(){return Math.floor((this.minZ+this.maxZ)*.5)}
  spanX(){return this.maxX-this.minX} spanZ(){return this.maxZ-this.minZ} area(){return Math.max(1,(this.maxX-this.minX+1)*(this.maxZ-this.minZ+1));}
  contains(x,z){return x>=this.minX&&x<=this.maxX&&z>=this.minZ&&z<=this.maxZ;}
  translate(x,z){return new StructureFootprint(this.minX+x,this.maxX+x,this.minZ+z,this.maxZ+z);}
  insetByRatio(r){r=Math.max(0,Math.min(.45,r));let ix=Math.min(Math.max(0,Math.floor(this.spanX()*r)),Math.floor(this.spanX()/2)),iz=Math.min(Math.max(0,Math.floor(this.spanZ()*r)),Math.floor(this.spanZ()/2));return new StructureFootprint(this.minX+ix,this.maxX-ix,this.minZ+iz,this.maxZ-iz);}
  sampleGrid(n){const out=[];this.forEachGridPoint(n,(x,z)=>out.push({x,z}));return out;}
  forEachGridPoint(n,fn){n=Math.max(1,n);for(let gx=0;gx<n;gx++){const x=n===1?.5:gx/(n-1),sx=Math.floor(this.minX+(this.maxX-this.minX)*x);for(let gz=0;gz<n;gz++){const z=n===1?.5:gz/(n-1);fn(sx,Math.floor(this.minZ+(this.maxZ-this.minZ)*z));}}}
}
export function categoryUsesIslandAwarePlacement(c){return ["SURFACE_SKY","SMALL_SKY","HAMLET_SKY","GROUND_VILLAGE","STRONGHOLD","UNDERGROUND","WATER"].includes(c);}
export function normalizeCategory(v){const x=String(v??"").trim().toUpperCase();return ({SMALL_GROUND:"SMALL_SKY",MEDIUM_GROUND:"SURFACE_SKY",LARGE_GROUND:"HAMLET_SKY",VILLAGE_GROUND:"GROUND_VILLAGE",STRONGHOLD_GROUND:"STRONGHOLD"})[x]??x;}
export function structureTier(radius){return radius<=32?LandSizeTier.SMALL:radius<=56?LandSizeTier.MEDIUM:LandSizeTier.LARGE;}
