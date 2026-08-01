import { world, BlockPermutation } from "@minecraft/server";
import { SETTINGS } from "../config/SkyIslandSettings.js";
import { IslandNoise } from "./IslandNoise.js";
import { IslandClusterSampler } from "./IslandClusterSampler.js";
import { IslandDescriptorFactory } from "./IslandDescriptorFactory.js";
import { IslandShapeSampler } from "./IslandShapeSampler.js";
import { IslandDensityEvaluator } from "./IslandDensityEvaluator.js";

const DB = "sky_archipelago:generated_v4";

export class IslandGenerator {
  constructor() { this.noise=null; this.clusters=null; this.factory=null; this.shape=null; this.density=null; this.generated=new Set(); this.queue=[]; this.queued=new Set(); this.jobs=[]; }
  load() {
    if (this.noise) return;
    this.seed = BigInt(world.seed);
    this.noise = new IslandNoise(this.seed);
    this.clusters = new IslandClusterSampler(this.noise);
    this.factory = new IslandDescriptorFactory(this.noise);
    this.shape = new IslandShapeSampler(this.noise);
    this.density = new IslandDensityEvaluator(this.noise, this.shape);
    const raw = world.getDynamicProperty(DB);
    try {
      if (typeof raw === "string") {
        const d = JSON.parse(raw);
        if (d.version === 4 && d.seed === this.seed.toString() && Array.isArray(d.generated)) this.generated = new Set(d.generated);
      }
    } catch (e) { console.warn(`[Sky Archipelago] Failed to load generated chunks: ${e}`); }
  }
  save() { world.setDynamicProperty(DB, JSON.stringify({version:4, seed:this.seed.toString(), generated:[...this.generated]})); }
  cluster(cx,cz) { return this.clusters.cluster(cx,cz,this.seed,SETTINGS.spacing); }
  descriptorsForCell(cx,cz) { const c=this.cluster(cx,cz),out=[this.factory.anchor(c)]; for(let i=0;i<c.satelliteCount;i++)out.push(this.factory.satellite(c,i)); for(let i=0;i<c.spireCount;i++)out.push(this.factory.spire(c,i)); return out; }
  descriptorsNear(x,z) { const cx=Math.floor(x/SETTINGS.spacing),cz=Math.floor(z/SETTINGS.spacing),out=[]; for(let ix=cx-3;ix<=cx+3;ix++)for(let iz=cz-3;iz<=cz+3;iz++)for(const d of this.descriptorsForCell(ix,iz)){const reach=Math.max(d.maxR*3+96,Math.max(Math.abs(d.hangX)+d.tailX*2,Math.abs(d.hangZ)+d.tailZ*2)+96),dx=x-d.x,dz=z-d.z;if(dx*dx+dz*dz<=reach*reach)out.push(d);} return out; }
  column(x,z,minY=-64,maxY=320) { const ds=this.descriptorsNear(x,z),segments=[]; if(!ds.length)return segments; let active=false,top=0; for(let y=maxY;y>=minY;y--){let solid=false;for(const d of ds){const h=this.shape.sample(d,x,z);if(h.influence&&this.density.density(d,h,x,y,z)>0){solid=true;break;}}if(solid&&!active){top=y;active=true;}if(!solid&&active){segments.push([y+1,top]);active=false;}}if(active)segments.push([minY,top]);return segments; }
  enqueue(cx,cz){const key=`${cx},${cz}`;if(this.generated.has(key)||this.queued.has(key))return;this.queued.add(key);this.queue.push({cx,cz});}
  requestAround(player){this.load();const cx=Math.floor(player.location.x/16),cz=Math.floor(player.location.z/16);for(let dx=-8;dx<=8;dx++)for(let dz=-8;dz<=8;dz++)this.enqueue(cx+dx,cz+dz);this.queue.sort((a,b)=>((a.cx-cx)**2+(a.cz-cz)**2)-((b.cx-cx)**2+(b.cz-cz)**2));}
  startNext(){if(this.jobs.length||!this.queue.length)return;const q=this.queue.shift();this.jobs.push({cx:q.cx,cz:q.cz,x:0,z:0});}
  tick(){this.load();this.startNext();const j=this.jobs[0];if(!j)return;const dim=world.getDimension("sky_archipelago:archipelago");let count=0;while(count<24&&j.x<16){const wx=j.cx*16+j.x,wz=j.cz*16+j.z,seg=this.column(wx,wz);for(const [bottom,top] of seg){if(top<bottom)continue;dim.fillBlocks({from:{x:wx,y:bottom,z:wz},to:{x:wx,y:top,z:wz}},BlockPermutation.resolve("minecraft:stone"));if(top-bottom>=5){dim.setBlockType({x:wx,y:top,z:wz},"minecraft:grass_block");for(let d=1;d<=4;d++)dim.setBlockType({x:wx,y:top-d,z:wz},"minecraft:dirt");}}j.z++;count++;if(j.z>=16){j.z=0;j.x++;}}if(j.x>=16){const key=`${j.cx},${j.cz}`;this.generated.add(key);this.queued.delete(key);this.jobs.shift();this.save();}}
  reset(){this.generated.clear();this.queue=[];this.queued.clear();this.jobs=[];this.save();}
}
