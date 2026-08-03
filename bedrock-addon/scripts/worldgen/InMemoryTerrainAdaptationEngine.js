import { TerrainVoxelMap } from "./TerrainVoxelMap.js";

const AIR = "minecraft:air";
const WATER = "minecraft:water";
const LAVA = "minecraft:lava";
const DEFAULT_FOUNDATION = "minecraft:dirt";
const WATER_IDS = new Set([WATER, "minecraft:flowing_water"]);
const LAVA_IDS = new Set([LAVA, "minecraft:flowing_lava"]);

function idOf(value) { return value?.id ?? value?.typeId ?? value?.type?.id ?? null; }
function solid(value) { const id = idOf(value); return !!id && id !== AIR && !WATER_IDS.has(id) && !LAVA_IDS.has(id); }
function replaceable(value) { const id = idOf(value); return !id || id === AIR || WATER_IDS.has(id) || LAVA_IDS.has(id); }
function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
function boundsForJob(job, options = {}) {
  if (job?.pieceBounds?.length) return job.pieceBounds;
  const fp = job?.footprint ?? { x: options.defaultFootprintX ?? 16, y: options.defaultFootprintY ?? 16, z: options.defaultFootprintZ ?? 16 };
  const rx = Math.max(1, Math.ceil(Number(fp.x ?? fp.width ?? 16) / 2));
  const rz = Math.max(1, Math.ceil(Number(fp.z ?? fp.depth ?? 16) / 2));
  const h = Math.max(1, Math.floor(Number(fp.y ?? fp.height ?? 16)));
  const x = Math.floor(Number(job?.x ?? job?.location?.x ?? 0));
  const y = Math.floor(Number(job?.y ?? job?.location?.y ?? 0));
  const z = Math.floor(Number(job?.z ?? job?.location?.z ?? 0));
  return [{ minX: x - rx, minY: y, minZ: z - rz, maxX: x + rx, maxY: y + h - 1, maxZ: z + rz }];
}
function set(map, x, y, z, id) { map.set(x, y, z, { id }); map.updateSurfaceAfterSet(x, y, z); }
function clear(map, x, y, z) { map.set(x, y, z, { id: AIR }); map.updateSurfaceAfterSet(x, y, z); }

/** Terrain adaptation that never queries the live Dimension. */
export class InMemoryTerrainAdaptationEngine {
  constructor(options = {}) {
    this.options = options;
    this.stats = { jobs: 0, columns: 0, changed: 0, flattened: 0, buried: 0, carved: 0, fluids: 0, foundations: 0 };
  }

  resetStats() { for (const k of Object.keys(this.stats)) this.stats[k] = 0; return this; }

  modeForJob(job) {
    if (job?.terrain_adaptation || job?.terrainAdaptation?.mode) return String(job.terrain_adaptation ?? job.terrainAdaptation.mode).toLowerCase();
    const category = String(job?.category ?? "").toLowerCase();
    if (job?.native === "stronghold" || category.includes("stronghold")) return "bury";
    if (category.includes("underground") || category.includes("fossil")) return "beard_box";
    if (category.includes("ground") || category.includes("village")) return "flatten";
    if (category.includes("water") || category.includes("ocean")) return "waterline";
    return "none";
  }

  surfaceTarget(map, x, z, box, job) {
    const explicit = Number(job?.targetY ?? job?.groundY ?? job?.y);
    if (Number.isFinite(explicit)) return Math.floor(explicit);
    const samples = [];
    const radius = Math.max(1, Math.min(8, Number(this.options.matchRadius ?? 3)));
    for (let dx = -radius; dx <= radius; dx++) for (let dz = -radius; dz <= radius; dz++) {
      const sx = x + dx, sz = z + dz;
      if (sx >= box.minX && sx <= box.maxX && sz >= box.minZ && sz <= box.maxZ) continue;
      samples.push(map.surfaceY(sx, sz));
    }
    if (!samples.length) return map.surfaceY(x, z);
    samples.sort((a, b) => a - b);
    return samples[Math.floor(samples.length / 2)];
  }

  flattenBox(map, box, job) {
    const foundation = job?.foundationBlock ?? DEFAULT_FOUNDATION;
    const targetBias = Number(job?.groundLevelDelta ?? 0);
    for (let x = box.minX; x <= box.maxX; x++) for (let z = box.minZ; z <= box.maxZ; z++) {
      const current = map.surfaceY(x, z);
      const target = clamp(this.surfaceTarget(map, x, z, box, job) + targetBias, map.minY + 1, map.maxY - 1);
      this.stats.columns++;
      if (current < target) {
        for (let y = current + 1; y <= target; y++) {
          if (replaceable(map.get(x, y, z))) { set(map, x, y, z, foundation); this.stats.changed++; this.stats.foundations++; }
        }
        set(map, x, target, z, foundation); this.stats.flattened++;
      } else if (current > target) {
        for (let y = target + 1; y <= current; y++) {
          if (solid(map.get(x, y, z))) { clear(map, x, y, z); this.stats.changed++; }
        }
        set(map, x, target, z, foundation); this.stats.flattened++;
      }
      map.setSurfaceY(x, z, target);
    }
  }

  buryBox(map, box, job) {
    const foundation = job?.foundationBlock ?? DEFAULT_FOUNDATION;
    const depth = Math.max(1, Math.min(32, Number(job?.buryDepth ?? this.options.buryDepth ?? 8)));
    for (let x = box.minX; x <= box.maxX; x++) for (let z = box.minZ; z <= box.maxZ; z++) {
      const top = Math.floor(box.minY) - 1, bottom = Math.max(map.minY, top - depth + 1);
      for (let y = bottom; y <= top; y++) if (replaceable(map.get(x, y, z))) { set(map, x, y, z, foundation); this.stats.changed++; this.stats.foundations++; }
      this.stats.columns++; this.stats.buried++;
    }
  }

  carveBox(map, box, job) {
    const padding = Math.max(0, Math.floor(Number(job?.carvePadding ?? 0)));
    const minX = box.minX - padding, maxX = box.maxX + padding, minY = box.minY, maxY = box.maxY, minZ = box.minZ - padding, maxZ = box.maxZ + padding;
    for (let x = minX; x <= maxX; x++) for (let y = minY; y <= maxY; y++) for (let z = minZ; z <= maxZ; z++) if (solid(map.get(x, y, z))) { clear(map, x, y, z); this.stats.changed++; }
    this.stats.carved++;
  }

  fluidBox(map, box, fluid) {
    const id = fluid === "lava" ? LAVA : WATER;
    for (let x = box.minX; x <= box.maxX; x++) for (let y = box.minY; y <= box.maxY; y++) for (let z = box.minZ; z <= box.maxZ; z++) if (replaceable(map.get(x, y, z))) { set(map, x, y, z, id); this.stats.changed++; }
    this.stats.fluids++;
  }

  adaptJob(map, job) {
    const mode = this.modeForJob(job);
    if (mode === "none") return;
    for (const box of boundsForJob(job, this.options)) {
      if (mode === "flatten" || mode === "beard_thin" || mode === "beard_box" || mode === "terrain_match") this.flattenBox(map, box, job);
      else if (mode === "bury" || mode === "buried") this.buryBox(map, box, job);
      else if (mode === "carve") this.carveBox(map, box, job);
      else if (mode === "waterline") this.fluidBox(map, box, "water");
      else if (mode === "lav aline" || mode === "lava_line") this.fluidBox(map, box, "lava");
    }
    this.stats.jobs++;
  }

  adaptCombined(map, jobs = []) {
    if (!(map instanceof TerrainVoxelMap)) throw new TypeError("adaptCombined requires TerrainVoxelMap");
    for (const job of jobs ?? []) this.adaptJob(map, job);
    return this.stats;
  }

  snapshot() { return { ...this.stats }; }
}

export function adaptTerrainMap(map, jobs, options = {}) { return new InMemoryTerrainAdaptationEngine(options).adaptCombined(map, jobs); }
