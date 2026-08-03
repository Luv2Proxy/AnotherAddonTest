import { computeTerrainAdaptation } from "./StructurePlacementPolicies.js";

const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const smooth=(a,b,x)=>{const t=clamp((x-a)/Math.max(1e-9,b-a),0,1);return t*t*(3-2*t);};

function boxDistance(box,x,y,z){
  const dx=Math.max(box.minX-x,0,x-box.maxX),dy=Math.max(box.minY-y,0,y-box.maxY),dz=Math.max(box.minZ-z,0,z-box.maxZ);
  return Math.sqrt(dx*dx+dy*dy+dz*dz);
}
function boxInside(box,x,y,z){return x>=box.minX&&x<=box.maxX&&y>=box.minY&&y<=box.maxY&&z>=box.minZ&&z<=box.maxZ;}
function normalizeBox(b){
  if(!b)return null;
  if(b.minX!=null)return{minX:+b.minX,minY:+b.minY,minZ:+b.minZ,maxX:+b.maxX,maxY:+b.maxY,maxZ:+b.maxZ};
  if(b.min&&b.max)return{minX:+b.min.x,minY:+b.min.y,minZ:+b.min.z,maxX:+b.max.x,maxY:+b.max.y,maxZ:+b.max.z};
  return null;
}

/** Approximation of Java Beardifier + Jigsaw junction density.
 * Positive values make terrain more likely to be solid; negative values carve.
 * The field is deterministic and evaluated before the island's final block
 * materialization. Post-placement adaptation remains a fallback for pieces
 * whose transformed bounds are unavailable during density evaluation.
 */
export class StructureDensityField {
 constructor(options={}){this.options=options;this.boxes=[];this.junctions=[];}
 clear(){this.boxes.length=0;this.junctions.length=0;return this;}
 addBox(box,options={}){const b=normalizeBox(box);if(!b)return this;this.boxes.push({...b,mode:options.mode??"beard_thin",weight:Number(options.weight??1),radius:Number(options.radius??12),verticalWeight:Number(options.verticalWeight??1),foundationBlock:options.foundationBlock??"minecraft:dirt"});return this;}
 addJunction(junction){if(junction)this.junctions.push({...junction});return this;}
 addStructure(candidate,host={}){
  const adaptation=computeTerrainAdaptation({category:candidate.category,candidate,host,location:candidate.location??{x:0,y:0,z:0}});
  for(const raw of candidate.pieceBounds??candidate.bounds?[candidate.bounds]:[]){this.addBox(raw,{mode:adaptation.mode,radius:candidate.beardRadius??12,weight:candidate.densityWeight??1});}
  for(const j of candidate.jigsawJunctions??candidate.junctions??[])this.addJunction(j);
  return this;
 }
 beardBox(box,x,y,z){
  const r=Math.max(1,box.radius),d=boxDistance(box,x,y,z);if(d>r)return 0;
  const edge=smooth(0,r,d),vertical=boxInside(box,x,y,z)?1:Math.max(0,1-Math.abs(y-(box.minY+box.maxY)*.5)/Math.max(1,(box.maxY-box.minY)*.65));
  if(box.mode==="bury")return -0.16*box.weight*edge;
  if(box.mode==="beard_box")return (0.62*edge+0.18*vertical)*box.weight;
  if(box.mode==="beard_thin")return (0.38*edge+0.28*vertical)*box.weight;
  if(box.mode==="encapsulate")return (0.48*edge+0.36*vertical)*box.weight;
  return 0;
 }
 junction(j,x,y,z){
  const jx=Number(j.x??j.pos?.x??0),jy=Number(j.y??j.pos?.y??0),jz=Number(j.z??j.pos?.z??0),r=Number(j.radius??j.sourceRadius??6),d=Math.hypot(x-jx,y-jy,z-jz);if(d>r)return 0;
  const strength=Number(j.weight??j.depth??1),fall=1-d/r;
  return strength*0.22*fall*fall;
 }
 densityAt(x,y,z,boxes=this.boxes,junctions=this.junctions,mode="beard_thin"){
  let v=0;
  for(const b of boxes){const box={...b,mode:b.mode??mode};v+=this.beardBox(box,x,y,z);}
  for(const j of junctions)v+=this.junction(j,x,y,z);
  return v;
 }
 contributionForColumn(x,z,minY,maxY,options={}){
  const out=[];for(let y=maxY;y>=minY;y--){const v=this.densityAt(x,y,z);if(Math.abs(v)>Number(options.threshold??0.02))out.push({y,density:v});}return out;
 }
}
