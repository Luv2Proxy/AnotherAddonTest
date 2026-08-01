import { BlockPermutation } from "@minecraft/server";

export class IslandSurfacePass {
  constructor(settings, noise){ this.settings=settings; this.noise=noise; }
  apply(dim,x,z,bottom,top,seed){
    if(!this.settings.surfaceEnabled)return;
    const depth=Math.max(1,Math.min(5,Math.floor(2+this.noise.sample01(seed,91)*3)));
    for(let d=0;d<depth;d++){
      const y=top-d;if(y<bottom)break;
      dim.setBlockPermutation({x,y,z},BlockPermutation.resolve(d===0?"minecraft:grass_block":"minecraft:dirt"));
    }
  }
  vegetation(dim,x,z,top,seed){
    if(!this.settings.vegetationEnabled||this.noise.sample01(seed,113)>.08)return;
    const kind=this.noise.sample01(seed,127);
    if(kind<.72){
      if(dim.getBlock({x,y:top+1,z})?.isAir)dim.setBlockType({x,y:top+1,z},"minecraft:short_grass");
    }else if(kind<.92){
      if(dim.getBlock({x,y:top+1,z})?.isAir)dim.setBlockType({x,y:top+1,z},"minecraft:dandelion");
    }
  }
}
