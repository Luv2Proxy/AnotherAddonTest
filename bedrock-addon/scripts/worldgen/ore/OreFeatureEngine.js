import { greedyCuboids, writeBulk } from "../BulkWorldWriter.js";

const STONE = "minecraft:stone";
const DEEPSLATE = "minecraft:deepslate";
const TUFF = "minecraft:tuff";
const GRANITE = "minecraft:granite";
const REPLACEABLE = new Set([STONE, DEEPSLATE, TUFF, GRANITE, "minecraft:diorite", "minecraft:andesite"]);

const FEATURES = [
  { id:"coal", ore:"minecraft:coal_ore", deep:"minecraft:deepslate_coal_ore", size:17, count:30, min:136, max:320, distribution:"uniform" },
  { id:"coal_lower", ore:"minecraft:coal_ore", deep:"minecraft:deepslate_coal_ore", size:17, count:20, min:0, max:192, distribution:"uniform" },
  { id:"iron_upper", ore:"minecraft:iron_ore", deep:"minecraft:deepslate_iron_ore", size:9, count:90, min:80, max:320, distribution:"triangle" },
  { id:"iron_middle", ore:"minecraft:iron_ore", deep:"minecraft:deepslate_iron_ore", size:9, count:10, min:-64, max:72, distribution:"triangle" },
  { id:"iron_lower", ore:"minecraft:iron_ore", deep:"minecraft:deepslate_iron_ore", size:9, count:20, min:-24, max:56, distribution:"uniform" },
  { id:"copper", ore:"minecraft:copper_ore", deep:"minecraft:deepslate_copper_ore", size:10, count:16, min:-16, max:112, distribution:"triangle" },
  { id:"gold", ore:"minecraft:gold_ore", deep:"minecraft:deepslate_gold_ore", size:9, count:4, min:-64, max:32, distribution:"triangle" },
  { id:"gold_extra", ore:"minecraft:gold_ore", deep:"minecraft:deepslate_gold_ore", size:9, count:2, min:-64, max:32, distribution:"uniform" },
  { id:"redstone", ore:"minecraft:redstone_ore", deep:"minecraft:deepslate_redstone_ore", size:8, count:4, min:-64, max:16, distribution:"uniform" },
  { id:"redstone_deep", ore:"minecraft:redstone_ore", deep:"minecraft:deepslate_redstone_ore", size:8, count:8, min:-64, max:32, distribution:"triangle" },
  { id:"lapis", ore:"minecraft:lapis_ore", deep:"minecraft:deepslate_lapis_ore", size:7, count:2, min:-64, max:64, distribution:"triangle" },
  { id:"diamond", ore:"minecraft:diamond_ore", deep:"minecraft:deepslate_diamond_ore", size:4, count:7, min:-64, max:16, distribution:"triangle", discardAir:0.70 },
  { id:"emerald", ore:"minecraft:emerald_ore", deep:"minecraft:deepslate_emerald_ore", size:3, count:100, min:-16, max:256, distribution:"triangle", emerald:true }
];

// The rare large-vein features use a deterministic origin grid so a vein is
// generated once and can cross chunk boundaries without being cut at 16x16.
const LARGE_VEINS = [
  { id:"copper_large", ore:"minecraft:raw_copper_block", filler:"minecraft:granite", min:0, max:48, spacing:96, chance:0.055, length:48, radius:5.5 },
  { id:"iron_large", ore:"minecraft:raw_iron_block", filler:"minecraft:tuff", min:-64, max:0, spacing:96, chance:0.055, length:48, radius:5.5 }
];

function hash32(a,b,c,d=0){let h=(Number(c)&0xffffffff)^Math.imul(a|0,0x45d9f3b)^Math.imul(b|0,0x119de1f3)^d;h=Math.imul(h^(h>>>16),0x45d9f3b);h=Math.imul(h^(h>>>16),0x45d9f3b);return(h^(h>>>16))>>>0;}
function rng(seed){let s=(seed>>>0)||1;return()=>{s^=s<<13;s^=s>>>17;s^=s<<5;return(s>>>0)/4294967296;};}
function key(x,y,z){return `${x},${y},${z}`;}
function replacement(id){return REPLACEABLE.has(id);}
function isDeep(id,y){return id===DEEPSLATE || (y<0 && id===STONE);}
function pickY(f,r){const u=r(),v=f.distribution==="triangle"?r():u;return Math.floor(f.min+((u+v)/2)*(f.max-f.min+1));}
function exposed(map,x,y,z){for(const [dx,dy,dz] of [[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]]){const b=map.get(key(x+dx,y+dy,z+dz));if(!b||b.id==="minecraft:air")return true;}return false;}
function write(map,x,y,z,id){const b=map.get(key(x,y,z));if(!b||!replacement(b.id))return false;b.id=id;return true;}

function normalVein(map,f,x,y,z,r){
  if(f.discardAir&&r()<f.discardAir&&exposed(map,x,y,z))return 0;
  const count=Math.max(1,f.size), angle=r()*Math.PI*2, tilt=(r()-.5)*0.9;
  let placed=0;
  for(let i=0;i<count;i++){
    const t=count===1?0:i/(count-1), cx=x+Math.cos(angle)*t*count*.55, cz=z+Math.sin(angle)*t*count*.55, cy=y+tilt*t*count;
    const rx=Math.max(.65,1.8*(1-Math.abs(t-.5)*1.35)+r()*.65), ry=rx*(.65+r()*.55);
    const minX=Math.floor(cx-rx),maxX=Math.ceil(cx+rx),minY=Math.floor(cy-ry),maxY=Math.ceil(cy+ry),minZ=Math.floor(cz-rx),maxZ=Math.ceil(cz+rx);
    for(let xx=minX;xx<=maxX;xx++)for(let yy=minY;yy<=maxY;yy++)for(let zz=minZ;zz<=maxZ;zz++){
      const qx=(xx-cx)/rx,qy=(yy-cy)/ry,qz=(zz-cz)/rx;
      if(qx*qx+qy*qy+qz*qz>1.0 || r()>.58)continue;
      const b=map.get(key(xx,yy,zz));if(!b||!replacement(b.id))continue;
      const id=isDeep(b.id,yy)?f.deep:f.ore;if(write(map,xx,yy,zz,id))placed++;
    }
  }
  return placed;
}

function largeVein(map,f,originX,originY,originZ,seed){
  const r=rng(seed), angle=r()*Math.PI*2, pitch=(r()-.5)*.55, length=f.length*(.75+r()*.5), radius=f.radius*(.8+r()*.4);
  let placed=0;
  // Large veins are a chain of overlapping ellipsoids. The filler is placed
  // first, then an irregular ore core is carved through it. This preserves
  // the characteristic granite/copper and tuff/iron massive-vein appearance.
  for(let i=0;i<=Math.ceil(length);i++){
    const t=i/length, cx=originX+Math.cos(angle)*(t-.5)*length, cz=originZ+Math.sin(angle)*(t-.5)*length, cy=originY+pitch*(t-.5)*length;
    const localR=radius*(.55+Math.sin(Math.PI*t)*.7+r()*.25);
    for(let dx=Math.floor(-localR);dx<=Math.ceil(localR);dx++)for(let dy=Math.floor(-localR*.65);dy<=Math.ceil(localR*.65);dy++)for(let dz=Math.floor(-localR);dz<=Math.ceil(localR);dz++){
      const q=(dx*dx+dz*dz)/(localR*localR)+(dy*dy)/(localR*localR*.42);
      if(q>1||r()>.72)continue;
      const x=Math.floor(cx)+dx,y=Math.floor(cy)+dy,z=Math.floor(cz)+dz,b=map.get(key(x,y,z));
      if(!b||!replacement(b.id))continue;
      if(write(map,x,y,z,f.filler))placed++;
      if(r()<.72){const oreId=f.ore;if(write(map,x,y,z,oreId))placed++;}
    }
  }
  return placed;
}

export function applyOreFeatures(map,region,seed,biomeByColumn,options={}){
  const stats={normalVeins:0,normalBlocks:0,largeVeins:0,largeBlocks:0};
  const cx0=region.cx*16,cz0=region.cz*16;
  for(const f of FEATURES){
    const r=rng(hash32(region.cx,region.cz,seed,f.id.length*0x9e3779b9));
    for(let i=0;i<f.count;i++){
      const x=cx0+Math.floor(r()*16),z=cz0+Math.floor(r()*16),biome=String(biomeByColumn.get(`${x},${z}`)||"");
      if(f.emerald&&!/mountain|hills|windswept|peak|grove|meadow|stony/i.test(biome))continue;
      const y=pickY(f,r),n=normalVein(map,f,x,y,z,r);if(n){stats.normalVeins++;stats.normalBlocks+=n;}
    }
  }
  const gridRadius=2;
  for(let gx=region.cx-gridRadius;gx<=region.cx+gridRadius;gx++)for(let gz=region.cz-gridRadius;gz<=region.cz+gridRadius;gz++)for(const f of LARGE_VEINS){
    const cellX=Math.floor(gx*16/f.spacing),cellZ=Math.floor(gz*16/f.spacing),originSeed=hash32(cellX,cellZ,seed,f.id.length*0x7f4a7c15),r=rng(originSeed);
    if(r()>=f.chance)continue;
    const ox=cellX*f.spacing+Math.floor(r()*f.spacing),oz=cellZ*f.spacing+Math.floor(r()*f.spacing),oy=f.min+Math.floor(r()*(f.max-f.min+1));
    const n=largeVein(map,f,ox,oy,oz,originSeed^0x6d2b79f5);if(n){stats.largeVeins++;stats.largeBlocks+=n;}
  }
  return stats;
}

export function bulkWriteTerrain(dimension,map,options={}){
  const blocks=[...map.values()];
  const ops=greedyCuboids(blocks,{exactIds:new Set(["minecraft:air","minecraft:water","minecraft:bedrock"])});
  return writeBulk(dimension,ops,{strict:false});
}

export { FEATURES as NORMAL_ORE_FEATURES, LARGE_VEINS };
