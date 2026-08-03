import { IslandNoise } from "./IslandNoise.js";
import { Family, Archetype } from "./IslandClusterSampler.js";

const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const smoothstep=(a,b,x)=>{const t=clamp((x-a)/Math.max(1e-9,b-a),0,1);return t*t*(3-2*t);};

/**
 * Structure-aware island density field.
 *
 * The original Java generator evaluates terrain density before blocks exist.
 * We reproduce that ordering as far as the Bedrock Script API allows by
 * allowing structure/jigsaw metadata to contribute directly to the density
 * field.  Runtime placement can still use TerrainAdaptationEngine as a
 * fallback for structures whose exact bounds are only known after placement.
 */
export class IslandDensityEvaluator{
 constructor(noise,shape,options={}){this.noise=noise;this.shape=shape;this.options=options;this.structureField=null;}
 setStructureField(field){this.structureField=field;return this;}

 structureContribution(x,y,z,context={}){
  const field=context.structureField??this.structureField;if(!field)return 0;
  if(typeof field==="function")return Number(field(x,y,z,context))||0;
  if(typeof field.densityAt==="function")return Number(field.densityAt(x,y,z,context.boxes??[],context.junctions??[],context.mode??"beard_thin"))||0;
  return 0;
 }

 density(d,h,x,y,z,context={}){
  let coverage=clamp(h.coverage,-.4,1.2);
  const macro=this.noise.fbm2D(d.seed+23n,x*.006,z*.006,4,.5),ridge=this.noise.ridgedFbm2D(d.seed+29n,x*.016,z*.016,3,.55),shelf=this.noise.fbm2D(d.seed+31n,x*.024,z*.024,3,.56);
  let relief=macro*7.5+ridge*4.5+shelf*2.2;
  if(d.family===Family.ANCHOR){
   if(d.archetype===Archetype.BOWL_CRATER){const c=Math.max(0,IslandNoise.ellipseDensity(h.lx,h.lz,d.rx*.42,d.rz*.42));relief-=c*10;}
   else if(d.archetype===Archetype.CRESCENT){const b=h.lx/Math.max(10,d.rx);relief+=b<-.35?2:b>.35?.8:0;}
   else if(d.archetype===Archetype.TERRACE){const radial=1-clamp(Math.max(Math.abs(h.lx)/d.rx,Math.abs(h.lz)/d.rz),0,1);relief+=Math.floor(radial*3)/3*16-radial*9;}
  }
  const top=Math.floor(d.y+d.plateau+relief),effective=Math.min(d.hang,d.family===Family.ANCHOR?16:d.family===Family.SATELLITE?20:24),shoulder=Math.floor(d.y-d.cliff*(.55+clamp(coverage,0,1)*.42)-Math.max(0,shelf)*6)+(d.archetype===Archetype.BOWL_CRATER?2:d.archetype===Archetype.CRESCENT?-1:d.archetype===Archetype.TERRACE?4:0),under=shoulder-effective,span=Math.max(16,top-shoulder),vp=clamp((top-y)/span,0,1.35),inset=(d.family===Family.ANCHOR?.34:d.family===Family.SATELLITE?.28:.22)+Math.pow(vp,d.family===Family.ANCHOR?1.08:.92)*(d.family===Family.SPIRE?.42:.58),topWindow=(top-y)/Math.max(4,d.plateau+10),bottomWindow=(y-shoulder)/Math.max(8,d.cliff+effective*.18),main=Math.min(topWindow,bottomWindow)+coverage-inset,edgeVoid=Math.max(0,this.noise.fbm3D(d.seed+5n,x*.02,y*.036,z*.02,3,.56)-.18)*(.38+h.edgeDistance*.8),channel=Math.max(0,this.noise.ridgedFbm3D(d.seed+7n,x*.03,y*.03,z*.03,3,.55)-.26)*(.3+h.edgeDistance*.9),cavern=Math.max(0,this.noise.ridgedFbm3D(d.seed+9n,x*.04,y*.04,z*.04,2,.52)-.34)*.22;
  let value=main-edgeVoid-channel-cavern;
  if(y<under-4)value-=.55+(under-4-y)*.055;
  if(y>top+3)value-=.4+(y-top)*.05;

  // Structure adaptation is applied before the final solid/air decision.
  // This is the key architectural difference from post-generation patching.
  const structure=context.structureField??this.structureField;
  if(structure){
   const contribution=this.structureContribution(x,y,z,context);
   const strength=Number(context.structureStrength??this.options.structureStrength??1);
   const signed=Number(context.structureSign??1);
   value+=contribution*strength*signed;
  }
  return value;
 }

 densityColumn(d,h,x,minY,maxY,z,context={}){
  const segments=[];let active=false,start=maxY;
  for(let y=maxY-1;y>=minY;y--){const solid=this.density(d,h,x,y,z,context)>0;if(solid&&!active){start=y;active=true;}if(!solid&&active){segments.push([y+1,start]);active=false;}}
  if(active)segments.push([minY,start]);return segments;
 }
}
