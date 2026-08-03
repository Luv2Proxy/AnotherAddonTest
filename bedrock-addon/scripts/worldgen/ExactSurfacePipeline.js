import { BlockPermutation } from "@minecraft/server";

let PERMUTATIONS = null;

function getPermutations() {
  if (PERMUTATIONS) return PERMUTATIONS;
  PERMUTATIONS = {
    AIR: BlockPermutation.resolve("minecraft:air"),
    STONE: BlockPermutation.resolve("minecraft:stone"),
    DIRT: BlockPermutation.resolve("minecraft:dirt"),
    GRASS: BlockPermutation.resolve("minecraft:grass_block"),
    SAND: BlockPermutation.resolve("minecraft:sand"),
    GRAVEL: BlockPermutation.resolve("minecraft:gravel"),
    SNOW: BlockPermutation.resolve("minecraft:snow"),
    SNOW_BLOCK: BlockPermutation.resolve("minecraft:snow_block")
  };
  return PERMUTATIONS;
}

function hash01(x, z, seed) {
  let h = BigInt.asUintN(64, BigInt(seed ?? 0) ^ BigInt(Math.trunc(x)) * -7046029254386353131n ^ BigInt(Math.trunc(z)) * -4417276706812531889n);
  h ^= h >> 33n;
  h *= -49064778989728563n;
  h ^= h >> 33n;
  return Number(BigInt.asUintN(53, h >> 11n)) / 9007199254740992;
}

function profileValues(profile) {
  if (Array.isArray(profile)) return { top: profile[0] ?? 1, filler: profile[1] ?? 3, beach: profile[2] ?? false };
  return { top: Number(profile?.topDepth ?? profile?.surfaceDepth ?? 1), filler: Number(profile?.fillerDepth ?? profile?.subsurfaceDepth ?? 3), beach: Boolean(profile?.beach ?? profile?.isBeach) };
}

function chooseSurface(biome, profile, x, z, seed, perms) {
  const id = String(biome ?? "minecraft:plains").toLowerCase();
  const p = profileValues(profile);
  if (id.includes("desert") || id.includes("badlands") || id.includes("mesa")) return { top: perms.SAND, filler: perms.SAND, depth: Math.max(2, p.filler) };
  if (id.includes("beach") || p.beach) return { top: perms.SAND, filler: perms.SAND, depth: Math.max(2, p.filler) };
  if (id.includes("snow") || id.includes("ice") || id.includes("grove") || id.includes("frozen")) return { top: perms.SNOW_BLOCK, filler: perms.STONE, depth: Math.max(1, p.top) };
  if (id.includes("gravel")) return { top: perms.GRAVEL, filler: perms.GRAVEL, depth: Math.max(1, p.filler) };
  if (id.includes("mountain") || id.includes("peaks") || id.includes("stony")) return hash01(x, z, seed) < 0.18 ? { top: perms.GRAVEL, filler: perms.STONE, depth: 2 } : { top: perms.STONE, filler: perms.STONE, depth: 2 };
  return { top: perms.GRASS, filler: perms.DIRT, depth: Math.max(1, p.filler) };
}

function topOfSegments(segments) {
  let top = -Infinity;
  for (const segment of segments ?? []) {
    if (Array.isArray(segment)) top = Math.max(top, Number(segment[1]));
    else if (segment && Number.isFinite(segment.top)) top = Math.max(top, Number(segment.top));
  }
  return Number.isFinite(top) ? Math.floor(top) : null;
}

export class ExactSurfacePipeline {
  constructor(settings = {}, noise = null) { this.settings = settings; this.noise = noise; }

  apply(dimension, x, z, segments, biome, profile, seed = 0) {
    const perms = getPermutations();
    const top = topOfSegments(segments);
    if (top == null || !dimension) return { applied: false, reason: "no_surface" };
    const surface = chooseSurface(biome, profile, x, z, seed, perms);
    const depth = Math.max(1, Math.min(8, surface.depth));
    let changed = 0;
    for (let i = 0; i < depth; i++) {
      const y = top - i;
      if (y < -64) break;
      try { dimension.setBlockPermutation({ x: Math.floor(x), y, z: Math.floor(z) }, i === 0 ? surface.top : surface.filler); changed++; }
      catch { break; }
    }
    const biomeId = String(biome ?? "").toLowerCase();
    if ((biomeId.includes("snow") || biomeId.includes("ice") || biomeId.includes("frozen")) && !biomeId.includes("ocean")) {
      try {
        const above = { x: Math.floor(x), y: top + 1, z: Math.floor(z) };
        const block = dimension.getBlock(above);
        if (block && block.typeId === "minecraft:air") { dimension.setBlockPermutation(above, perms.SNOW); changed++; }
      } catch {}
    }
    return { applied: changed > 0, changed, top, surface: surface.top?.type?.id ?? null };
  }
}

export function createExactSurfacePipeline(settings, noise) { return new ExactSurfacePipeline(settings, noise); }
