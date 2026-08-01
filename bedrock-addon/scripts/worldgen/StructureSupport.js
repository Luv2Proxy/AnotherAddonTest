const CATEGORIES={village:1,hamlet:2,monument:3,mineshaft:4,generic:0};
export function classifyStructure(id=""){const s=String(id).toLowerCase();if(s.includes("village"))return CATEGORIES.village;if(s.includes("monument"))return CATEGORIES.monument;if(s.includes("mineshaft"))return CATEGORIES.mineshaft;return CATEGORIES.generic;}
export function footprint(x,z,radius){return{minX:x-radius,maxX:x+radius,minZ:z-radius,maxZ:z+radius};}
export function supportBlend(dim,fp,baseY,opts={}){const maxGap=opts.maxGap??2,cut=opts.cutDepth??0,top=opts.top??"minecraft:grass_block",under=opts.under??"minecraft:dirt",deep=opts.deep??"minecraft:stone";for(let x=fp.minX;x<=fp.maxX;x++)for(let z=fp.minZ;z<=fp.maxZ;z++){const terrain=dim.getHeight("world_surface",x,z)-1,gap=baseY-1-terrain;if(gap>0&&gap<=maxGap){for(let y=terrain+1;y<baseY;y++)dim.setBlockType({x,y,z},y===baseY-1?top:y===baseY-2?under:deep);}else if(gap<0&&-gap<=cut){for(let y=baseY;y<=terrain;y++)dim.setBlockType({x,y,z},"minecraft:air");}}}
export function villageSupport(dim,fp,baseY){supportBlend(dim,fp,baseY,{maxGap:10,cutDepth:3});}
export function hamletSupport(dim,fp,baseY){supportBlend(dim,fp,baseY,{maxGap:4,cutDepth:2});}
export function monumentSupport(dim,fp,baseY){supportBlend(dim,fp,baseY,{maxGap:7,cutDepth:4,top:"minecraft:stone",under:"minecraft:stone",deep:"minecraft:stone"});}
export function mineshaftSupport(dim,points,baseY){for(const p of points){const terrain=dim.getHeight("world_surface",p.x,p.z)-1;if(baseY-1-terrain<=3&&baseY>terrain)for(let y=terrain+1;y<baseY;y++)dim.setBlockType({x:p.x,y,z:p.z},"minecraft:stone");}}
export {CATEGORIES};
