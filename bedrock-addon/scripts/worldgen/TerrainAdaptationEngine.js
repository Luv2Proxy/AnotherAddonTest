import { BlockPermutation } from "@minecraft/server";

const AIR = "minecraft:air";
const WATER = new Set(["minecraft:water", "minecraft:flowing_water"]);
const BEARD_KERNEL_RADIUS = 12;
const BEARD_KERNEL_SIZE = 24;
const BEARD_KERNEL = new Float64Array(BEARD_KERNEL_SIZE ** 3);

// Vanilla's Beardifier uses a precomputed 24x24x24 Gaussian kernel centered
// around an offset of 12. Bedrock Script API cannot inject this into the
// terrain density pipeline, so we expose the same contribution primitives
// and apply their result to generated terrain as the closest runtime bridge.
for (let x = 0; x < BEARD_KERNEL_SIZE; x++) {
  for (let y = 0; y < BEARD_KERNEL_SIZE; y++) {
    for (let z = 0; z < BEARD_KERNEL_SIZE; z++) {
      const dx = x - BEARD_KERNEL_RADIUS;
      const dy = y - BEARD_KERNEL_RADIUS;
      const dz = z - BEARD_KERNEL_RADIUS;
      BEARD_KERNEL[(x * BEARD_KERNEL_SIZE + y) * BEARD_KERNEL_SIZE + z] = Math.exp(-(dx * dx + dz * dz) / 16.0 - (dy * dy) / 2.0);
    }
  }
}

function p3(p) { return { x: Math.floor(Number(p?.x ?? 0)), y: Math.floor(Number(p?.y ?? 0)), z: Math.floor(Number(p?.z ?? 0)) }; }
function id(block) { try { return block?.typeId ?? null; } catch { return null; } }
function blockAt(d, p) { try { return d?.getBlock(p) ?? null; } catch { return null; } }
function perm(idValue, states) { try { return typeof idValue === "string" ? BlockPermutation.resolve(idValue, states) : idValue; } catch { return null; } }
function air(b) { return !b || id(b) === AIR; }
function water(b) { return WATER.has(id(b)); }
function replaceable(b) { return air(b) || water(b); }
function sq(v) { return v * v; }

export class TerrainAdaptationEngine {
  constructor(dimension, options = {}) { this.dimension = dimension; this.options = options; this.changes = []; }
  reset() { this.changes.length = 0; return this; }

  setBlock(position, blockId, states = {}) {
    const pos = p3(position), permutation = perm(blockId, states);
    if (!permutation || !this.dimension) return false;
    try { this.dimension.setBlockPermutation(pos, permutation); this.changes.push({ position: pos, id: typeof blockId === "string" ? blockId : null }); return true; } catch { return false; }
  }

  surfaceY(x, z) {
    const minY = Number(this.options.minY ?? -64), maxY = Number(this.options.maxY ?? 320);
    for (let y = maxY; y >= minY; y--) {
      const b = blockAt(this.dimension, { x, y, z });
      if (b && !air(b) && !water(b)) return y;
    }
    return minY;
  }

  boxes(candidate, origin) {
    const list = candidate?.pieceBounds ?? candidate?.boundingBoxes ?? candidate?.pieces?.map(p => p.boundingBox ?? p.bounds).filter(Boolean) ?? [];
    if (list.length) return list.map(b => ({
      minX: Number(b.minX ?? b.x ?? origin.x), minY: Number(b.minY ?? b.y ?? origin.y), minZ: Number(b.minZ ?? b.z ?? origin.z),
      maxX: Number(b.maxX ?? (b.x ?? origin.x) + (b.sizeX ?? b.width ?? 1) - 1),
      maxY: Number(b.maxY ?? (b.y ?? origin.y) + (b.sizeY ?? b.height ?? 1) - 1),
      maxZ: Number(b.maxZ ?? (b.z ?? origin.z) + (b.sizeZ ?? b.depth ?? 1) - 1),
      groundLevelDelta: Number(b.groundLevelDelta ?? b.ground_level_delta ?? 0),
      terrainAdjustment: b.terrainAdjustment ?? b.terrain_adjustment
    }));
    const s = candidate?.size ?? candidate?.transformedSize ?? { x: 1, y: 1, z: 1 };
    const hx = Math.max(1, Math.ceil(Number(candidate?.footprint?.x ?? candidate?.footprintRadius ?? s.x / 2)));
    const hz = Math.max(1, Math.ceil(Number(candidate?.footprint?.z ?? candidate?.footprintRadius ?? s.z / 2)));
    return [{ minX: origin.x - hx, minY: origin.y, minZ: origin.z - hz, maxX: origin.x + hx, maxY: origin.y + Number(s.y ?? 1) - 1, maxZ: origin.z + hz, groundLevelDelta: Number(candidate?.groundLevelDelta ?? 0), terrainAdjustment: candidate?.terrain_adaptation }];
  }

  union(boxes) {
    if (!boxes?.length) return { minX: 0, minY: 0, minZ: 0, maxX: 0, maxY: 0, maxZ: 0 };
    return boxes.reduce((a, b) => ({ minX: Math.min(a.minX, b.minX), minY: Math.min(a.minY, b.minY), minZ: Math.min(a.minZ, b.minZ), maxX: Math.max(a.maxX, b.maxX), maxY: Math.max(a.maxY, b.maxY), maxZ: Math.max(a.maxZ, b.maxZ) }), boxes[0]);
  }

  isInKernelRange(value) { return value >= -BEARD_KERNEL_RADIUS && value < BEARD_KERNEL_RADIUS; }

  kernelAt(x, y, z) {
    if (!this.isInKernelRange(x) || !this.isInKernelRange(y) || !this.isInKernelRange(z)) return 0;
    const ix = Math.floor(x) + BEARD_KERNEL_RADIUS, iy = Math.floor(y) + BEARD_KERNEL_RADIUS, iz = Math.floor(z) + BEARD_KERNEL_RADIUS;
    return BEARD_KERNEL[(ix * BEARD_KERNEL_SIZE + iy) * BEARD_KERNEL_SIZE + iz] ?? 0;
  }

  getBuryContribution(x, y, z) {
    const vertical = Number(y) + 0.5;
    const magnitude = Math.sqrt(sq(x) + sq(vertical) + sq(z));
    return magnitude >= 6.0 ? 0 : 1.0 - magnitude / 6.0;
  }

  getBeardContribution(x, y, z, height = 0) {
    const kernel = this.kernelAt(x, y, z);
    if (kernel === 0) return 0;
    return kernel * Math.max(0, 1.0 - Number(height) / 6.0);
  }

  computeBeardContribution(x, y, z) { return this.kernelAt(Math.trunc(x), Math.trunc(y), Math.trunc(z)); }

  computeBeardContributionInterpolated(x, y, z) {
    const ix = Math.trunc(x), iz = Math.trunc(z), lower = Math.floor(y), upper = lower + 1, t = y - lower;
    const a = this.kernelAt(ix, lower, iz), b = this.kernelAt(ix, upper, iz);
    return a + (b - a) * t;
  }

  nearest(box, x, y, z) {
    return {
      x: x < box.minX ? box.minX - x : x > box.maxX ? x - box.maxX : 0,
      y: y < box.minY ? box.minY - y : y > box.maxY ? y - box.maxY : 0,
      z: z < box.minZ ? box.minZ - z : z > box.maxZ ? z - box.maxZ : 0
    };
  }

  getStructureWeight(x, y, z, box, defaultMode = "beard_thin") {
    const d = this.nearest(box, x, y, z);
    const mode = String(box.terrainAdjustment ?? defaultMode).toLowerCase();
    if (mode === "bury" || mode === "buried") return this.getBuryContribution(d.x, d.y, d.z);
    return this.getBeardContribution(d.x, d.y, d.z, box.groundLevelDelta ?? 0);
  }

  junctionWeight(x, y, z, junction) {
    const jx = Number(junction.sourceX ?? junction.x ?? 0), jy = Number(junction.sourceY ?? junction.y ?? 0), jz = Number(junction.sourceZ ?? junction.z ?? 0);
    return this.computeBeardContributionInterpolated(x - jx, y - jy, z - jz);
  }

  // Core equivalent of Beardifier.compute() at one point. Vanilla feeds this
  // value into terrain density before terrain blocks exist; the addon uses it
  // after placement to deform the existing terrain around actual piece bounds.
  densityAt(x, y, z, boxes, junctions = [], mode = "beard_thin") {
    let value = 0;
    for (const box of boxes ?? []) value += this.getStructureWeight(x, y, z, box, mode);
    for (const junction of junctions ?? []) value += this.junctionWeight(x, y, z, junction);
    return value;
  }

  calculateStructureWeight(x, y, z, boxes, junctions, mode) { return Math.max(0, Math.min(1, this.densityAt(x, y, z, boxes, junctions, mode))); }

  adapt(candidate, context = {}) {
    const mode = String(context.mode ?? candidate?.terrain_adaptation ?? candidate?.terrainAdaptation?.mode ?? "none").toLowerCase();
    const origin = p3(context.location ?? candidate?.location ?? candidate ?? {}), boxes = this.boxes(candidate, origin), bounds = this.union(boxes);
    const targetY = Number(context.targetY ?? candidate?.targetY ?? bounds.minY), foundation = context.foundationBlock ?? candidate?.foundationBlock ?? "minecraft:dirt";
    const junctions = candidate?.jigsawJunctions ?? candidate?.junctions ?? [];
    const radius = mode === "beard_box" || mode === "beard_thin" ? BEARD_KERNEL_RADIUS : 6;
    const result = { mode, targetY, bounds, changed: 0, columns: 0, densitySamples: 0, operations: [], junctions: junctions.length };
    if (mode === "none") return result;

    if (mode === "bury" || mode === "buried") {
      const depth = Number(context.depth ?? candidate?.buryDepth ?? 8);
      for (let x = bounds.minX - 6; x <= bounds.maxX + 6; x++) for (let z = bounds.minZ - 6; z <= bounds.maxZ + 6; z++) {
        const surface = this.surfaceY(x, z);
        for (let y = Math.max(-64, bounds.minY - depth); y < Math.min(surface, bounds.minY); y++) {
          let contribution = 0;
          for (const box of boxes) contribution = Math.max(contribution, this.getStructureWeight(x, y, z, { ...box, terrainAdjustment: "bury" }, "bury"));
          if (contribution <= 0.05) continue;
          const b = blockAt(this.dimension, { x, y, z });
          if (replaceable(b) && this.setBlock({ x, y, z }, foundation)) result.changed++;
        }
        result.columns++;
      }
      result.operations.push("bury");
      return result;
    }

    for (let x = bounds.minX - radius; x <= bounds.maxX + radius; x++) for (let z = bounds.minZ - radius; z <= bounds.maxZ + radius; z++) {
      const surface = this.surfaceY(x, z);
      for (let y = Math.max(-64, Math.min(targetY - BEARD_KERNEL_RADIUS, surface - BEARD_KERNEL_RADIUS)); y <= Math.min(320, surface + 2); y++) {
        const density = this.calculateStructureWeight(x, y, z, boxes, junctions, mode);
        if (density <= 0) continue;
        result.densitySamples++;
        const desired = targetY + Math.round((surface - targetY) * (1 - density));
        if (y < desired) {
          const b = blockAt(this.dimension, { x, y, z });
          if (replaceable(b) && this.setBlock({ x, y, z }, foundation)) result.changed++;
        } else if (y > desired && y <= surface && density > 0.85) {
          const b = blockAt(this.dimension, { x, y, z });
          if (b && !air(b) && !water(b)) {
            const ap = perm(AIR);
            try { this.dimension.setBlockPermutation({ x, y, z }, ap); result.changed++; } catch {}
          }
        }
      }
      result.columns++;
    }
    result.operations.push(mode);
    return result;
  }

  flatten(candidate, context = {}) {
    const origin = p3(context.location ?? candidate?.location ?? candidate ?? {}), bounds = this.union(this.boxes(candidate, origin)), targetY = Number(context.targetY ?? bounds.minY), foundation = context.foundationBlock ?? "minecraft:dirt", ap = perm(AIR);
    let changed = 0;
    for (let x = bounds.minX; x <= bounds.maxX; x++) for (let z = bounds.minZ; z <= bounds.maxZ; z++) {
      const s = this.surfaceY(x, z);
      if (s < targetY) for (let y = s; y < targetY; y++) if (this.setBlock({ x, y, z }, foundation)) changed++;
      if (s > targetY && ap) for (let y = targetY; y < s; y++) {
        const b = blockAt(this.dimension, { x, y, z });
        if (b && !air(b) && !water(b)) try { this.dimension.setBlockPermutation({ x, y, z }, ap); changed++; } catch {}
      }
    }
    return { mode: "flatten", targetY, changed, bounds };
  }

  waterline(candidate, context = {}) {
    const origin = p3(context.location ?? candidate?.location ?? candidate ?? {}), bounds = this.union(this.boxes(candidate, origin)), waterLevel = Number(context.waterLevel ?? 63), floorY = Number(context.seaFloorY ?? waterLevel - 1), wp = perm("minecraft:water");
    let changed = 0;
    if (!wp) return { mode: "waterline", changed: 0, bounds };
    for (let x = bounds.minX; x <= bounds.maxX; x++) for (let z = bounds.minZ; z <= bounds.maxZ; z++) for (let y = floorY + 1; y <= waterLevel; y++) {
      const b = blockAt(this.dimension, { x, y, z });
      if (air(b)) try { this.dimension.setBlockPermutation({ x, y, z }, wp); changed++; } catch {}
    }
    return { mode: "waterline", waterLevel, floorY, changed, bounds };
  }
}

export function applyTerrainAdaptation(dimension, candidate, context = {}) {
  if (!dimension) return { mode: context.mode ?? "none", changed: 0, skipped: "no_dimension" };
  const engine = new TerrainAdaptationEngine(dimension, context.options ?? {}), adaptation = engine.adapt(candidate, context);
  if (context.flatten || adaptation.mode === "beard_thin" || adaptation.mode === "beard_box") adaptation.flatten = engine.flatten(candidate, { ...context, targetY: adaptation.targetY });
  if (context.waterline) adaptation.waterline = engine.waterline(candidate, context);
  return adaptation;
}
