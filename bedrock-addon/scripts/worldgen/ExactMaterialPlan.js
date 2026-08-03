import { BlockPermutation } from "@minecraft/server";

let MATERIALS = null;

function getMaterials() {
  if (MATERIALS) return MATERIALS;
  MATERIALS = {
    STONE: BlockPermutation.resolve("minecraft:stone"),
    DEEPSLATE: BlockPermutation.resolve("minecraft:deepslate"),
    WATER: BlockPermutation.resolve("minecraft:water"),
    BEDROCK: BlockPermutation.resolve("minecraft:bedrock")
  };
  return MATERIALS;
}

function hash01(x,y,z,seed){let h=BigInt.asUintN(64,BigInt(seed)^BigInt(x)*-7046029254386353131n^BigInt(y)*-4417276706812531889n^BigInt(z)*1609587929392839161n);h^=h>>33n;h*= -49064778989728563n;h^=h>>33n;h*=-4265267296055464877n;h^=h>>33n;return Number(h>>11n)/9007199254740992;}

export function planColumn(segments,minY,maxY,settings,x,z,layoutSeed){
  const { STONE, DEEPSLATE, WATER, BEDROCK } = getMaterials();
  const solid=[];let highest=minY-1;
  for(const s of segments){const b=Math.max(minY,s[0]),t=Math.min(maxY-1,s[1]);if(b<=t){solid.push([b,t]);highest=Math.max(highest,t);}}
  solid.sort((a,b)=>a[0]-b[0]);
  const merged=[];
  for(const r of solid){const p=merged[merged.length-1];if(p&&r[0]<=p[1]+1)p[1]=Math.max(p[1],r[1]);else merged.push([...r]);}
  const ocean=settings.oceanEnabled, oceanTop=ocean?Math.min(maxY-1,settings.oceanLevelY):minY-1, floorTop=ocean?minY:minY;
  return {solid:merged,highest,ocean,oceanTop,floorTop,minY,maxY,materialAt(y){
    if(y<minY||y>=maxY)return null;
    if(contains(merged,y))return y<=settings.deepslateStartY?DEEPSLATE:(y<=settings.deepslateStartY+8&&hash01(x,y,z,layoutSeed)<1-(y-settings.deepslateStartY-1)/7?DEEPSLATE:STONE);
    if(ocean&&y===minY)return BEDROCK;
    if(ocean&&y>minY&&y<=floorTop)return STONE;
    if(ocean&&y>minY&&y<=oceanTop)return WATER;
    return null;
  }};
}

function contains(rs,y){for(const r of rs)if(y>=r[0]&&y<=r[1])return true;return false;}
