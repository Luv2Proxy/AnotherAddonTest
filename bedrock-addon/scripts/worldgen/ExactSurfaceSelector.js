// Bedrock-side equivalent of the Java SurfaceBlockSelector profile rules.
// It deliberately mirrors the original's biome categories and deterministic
// shoreline blending rather than using a generic grass/dirt rule.
import { BlockPermutation } from "@minecraft/server";

let SURFACE = null;

function getSurface() {
  if (SURFACE) return SURFACE;
  const P = (id) => BlockPermutation.resolve(id);
  SURFACE = Object.freeze({
    grass: [P("minecraft:grass_block"), P("minecraft:dirt"), P("minecraft:stone"), 5],
    snow: [P("minecraft:snow_block"), P("minecraft:dirt"), P("minecraft:stone"), 4],
    sand: [P("minecraft:sand"), P("minecraft:sand"), P("minecraft:sandstone"), 4],
    redSand: [P("minecraft:red_sand"), P("minecraft:orange_terracotta"), P("minecraft:terracotta"), 4],
    gravel: [P("minecraft:gravel"), P("minecraft:gravel"), P("minecraft:stone"), 3],
    coarse: [P("minecraft:coarse_dirt"), P("minecraft:dirt"), P("minecraft:stone"), 4],
    mushroom: [P("minecraft:mycelium"), P("minecraft:dirt"), P("minecraft:dirt"), 5]
  });
  return SURFACE;
}

export function profileForBiome(biomeId, cold=false, x=0, z=0){
  const SURFACE = getSurface();
  const id=String(biomeId??"").toLowerCase();
  if(id.includes("mushroom"))return SURFACE.mushroom;
  if(id.includes("badlands")||id.includes("mesa"))return SURFACE.redSand;
  if(id.includes("desert")||id.includes("beach"))return SURFACE.sand;
  if(id.includes("stony_shore")||id.includes("stony_peaks")||id.includes("jagged_peaks")||id.includes("windswept_gravelly"))return SURFACE.gravel;
  if(id.includes("snowy")||id.includes("frozen")||id.includes("grove")||cold)return SURFACE.snow;
  if(id.includes("river")||id.includes("swamp")||id.includes("mangrove"))return shoreline(id,x,z,cold,SURFACE);
  return SURFACE.grass;
}

function shoreline(id,x,z,cold,SURFACE){
  const river=id.includes("river")||id.includes("swamp")||id.includes("mangrove");
  const h=hash01(x,z,6026299900829495408n), warmth=river?0.55+h*.55:h*1.6;
  let sand=smooth(.3,1.05,warmth);if(!river)sand=Math.max(.78,sand);if(cold)sand*=.35;if(river)sand*=.52;
  const gravel=1-sand, pick=hash01(x,z,6026299900829495376n), bad=id.includes("badlands");
  let top;if(bad&&warmth>1.2&&pick>.72)top=SURFACE.redSand;else if(pick<gravel*(river?.75:.6))top=SURFACE.gravel;else if(pick<gravel+sand*(river?.64:.92))top=SURFACE.sand;else top=river&&hash01(x,z,6026299900829495392n)>.33?SURFACE.grass:SURFACE.sand;
  return top;
}
function smooth(a,b,x){x=Math.max(0,Math.min(1,(x-a)/(b-a)));return x*x*(3-2*x);}
function hash01(x,z,s){let h=BigInt.asUintN(64,BigInt(x)*-7046029254386353131n^BigInt(z)*-4417276706812531889n^s);h^=h>>33n;h*=1099511628211n;h^=h>>29n;return Number(h&0xffffffffn)/4294967295;}
