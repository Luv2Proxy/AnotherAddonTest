import { BlockPermutation, BlockVolume, system } from "@minecraft/server";
import { planColumn } from "./ExactMaterialPlan.js";
import { terrainProfile } from "./BiomeTerrainProfiles.js";
import { greedyCuboids } from "./BulkWorldWriter.js";
import { applyOreFeatures } from "./ore/OreFeatureEngine.js";
import { TerrainVoxelMap } from "./TerrainVoxelMap.js";
import { InMemoryTerrainAdaptationEngine } from "./InMemoryTerrainAdaptationEngine.js";

const AIR = "minecraft:air";
const WATER = "minecraft:water";
const BEDROCK = "minecraft:bedrock";
const STONE = "minecraft:stone";
const DEEPSLATE = "minecraft:deepslate";
const VARIANTS = ["minecraft:granite", "minecraft:diorite", "minecraft:andesite"];

function hash32(x, z, seed, salt = 0) {
  let h = (Number(seed) & 0xffffffff) ^ Math.imul(x | 0, 0x45d9f3b) ^ Math.imul(z | 0, 0x119de1f3) ^ salt;
  h = Math.imul(h ^ (h >>> 16), 0x45d9f3b);
  h = Math.imul(h ^ (h >>> 16), 0x45d9f3b);
  return (h ^ (h >>> 16)) >>> 0;
}
function materialId(p) { return p?.type?.id ?? p?.typeId ?? null; }
function eligibleStone(id) { return id === STONE || id === DEEPSLATE || id === "minecraft:tuff" || id === "minecraft:granite" || id === "minecraft:diorite" || id === "minecraft:andesite"; }
function variantFor(x, y, z, seed) {
  const r = hash32(x ^ y, z ^ (y * 31), seed, 0x9e3779b9) / 4294967296;
  if (y < 0 && r < 0.035) return "minecraft:tuff";
  if (r < 0.055) return VARIANTS[Math.floor(r * 1000) % VARIANTS.length];
  return null;
}
function permutation(id, states) { try { return BlockPermutation.resolve(id, states); } catch { return null; } }
function volume(op) { return new BlockVolume({ x: op.x1, y: op.y1, z: op.z1 }, { x: op.x2, y: op.y2, z: op.z2 }); }

function jobsForChunk(generator, chunk) {
  const minX = chunk.cx * 16 - 2, maxX = chunk.cx * 16 + 17;
  const minZ = chunk.cz * 16 - 2, maxZ = chunk.cz * 16 + 17;
  return (generator.structureJobs ?? []).filter(job => {
    const x = Number(job.x ?? job.location?.x ?? 0), z = Number(job.z ?? job.location?.z ?? 0);
    const rx = Number(job.footprint?.x ?? 16) / 2, rz = Number(job.footprint?.z ?? 16) / 2;
    return x + rx >= minX && x - rx <= maxX && z + rz >= minZ && z - rz <= maxZ;
  });
}

export class BulkTerrainRuntime {
  constructor(generator, options = {}) {
    this.generator = generator;
    this.options = options;
    this.pending = new Map();
    this.cache = new Map();
    this.ready = new Set();
    this.flushJobs = new Set();
    this.preparedStructureKeys = new Set();
    this.originalGenerate = null;
    this.originalTick = null;
    this.originalPlaceQueuedStructures = null;
    this.originalAdaptPlacedResult = null;
    this.installed = false;
    this.stats = {
      columns: 0, chunks: 0, fills: 0, singles: 0, failed: 0,
      normalVeins: 0, normalOreBlocks: 0, largeVeins: 0, largeOreBlocks: 0,
      variants: 0, adaptationJobs: 0, adaptationChanged: 0,
      cachedChunks: 0, lateStructureAdaptations: 0
    };
  }

  install() {
    if (this.installed) return this;
    this.installed = true;
    this.originalGenerate = this.generator.generateColumn.bind(this.generator);
    this.originalTick = this.generator.tick.bind(this.generator);
    this.originalPlaceQueuedStructures = this.generator.placeQueuedStructures?.bind(this.generator);
    this.originalAdaptPlacedResult = this.generator.adaptPlacedResult?.bind(this.generator);
    const self = this;

    this.generator.generateColumn = function (dimension, x, z, segments) {
      return self.collect(dimension, x, z, segments);
    };
    this.generator.tick = function () { return self.tick(); };

    if (this.originalPlaceQueuedStructures) {
      this.generator.placeQueuedStructures = function () {
        const job = self.generator.structureJobs?.[0];
        if (job) self.prepareLateStructure(job);
        return self.originalPlaceQueuedStructures();
      };
    }

    if (this.originalAdaptPlacedResult) {
      this.generator.adaptPlacedResult = function (job, result, location, dimension) {
        if (job?.key && self.preparedStructureKeys.has(job.key)) return result;
        return self.originalAdaptPlacedResult(job, result, location, dimension);
      };
    }
    return this;
  }

  collect(dimension, x, z, segments) {
    if (!segments?.length) return;
    const cx = Math.floor(x / 16), cz = Math.floor(z / 16), k = `${cx},${cz}`;
    let chunk = this.pending.get(k);
    if (!chunk) {
      chunk = {
        cx, cz, dimension,
        terrain: new TerrainVoxelMap({ minY: -64, maxY: 320 }),
        biomeByColumn: new Map(), columns: new Set(), seed: this.generator.layoutSeed,
        generated: false, written: false, adapted: false
      };
      this.pending.set(k, chunk);
    }

    const top = Math.max(...segments.map(s => s[1]));
    const biome = this.generator.biomeAt(dimension, x, top, z);
    const profile = terrainProfile(biome);
    const plan = planColumn(
      segments, -64, 321,
      { ...this.generator.settings, deepslateStartY: this.generator.settings.deepslateStartY - (profile[0] - 1) * 3 },
      x, z, this.generator.layoutSeed
    );

    for (let y = -64; y <= 320; y++) {
      const p = plan.materialAt(y), id = materialId(p);
      if (id) chunk.terrain.set(x, y, z, { x, y, z, id });
      else if (this.generator.settings.oceanEnabled && y <= plan.oceanTop && y > top) chunk.terrain.set(x, y, z, { x, y, z, id: WATER });
    }
    chunk.terrain.setSurfaceY(x, z, top);
    chunk.biomeByColumn.set(`${x},${z}`, biome);
    chunk.columns.add(`${x},${z}`);
    this.stats.columns++;

    if (chunk.columns.size >= 16) {
      chunk.generated = true;
      this.ready.add(k);
      this.cache.set(k, chunk);
      this.stats.cachedChunks = this.cache.size;
    }
  }

  applyDeterministicVariants(chunk) {
    for (const v of chunk.terrain.blocks.values()) {
      if (!eligibleStone(v.id)) continue;
      const variant = variantFor(v.x, v.y, v.z, chunk.seed);
      if (variant) { v.id = variant; this.stats.variants++; }
    }
  }

  applyCombinedAdaptation(chunk, jobs) {
    if (!jobs.length || chunk.adapted) return;
    const engine = new InMemoryTerrainAdaptationEngine({ minY: -64, maxY: 320, matchRadius: this.options.matchRadius ?? 3, buryDepth: this.options.buryDepth ?? 8 });
    const result = engine.adaptCombined(chunk.terrain, jobs);
    this.stats.adaptationJobs += result.jobs;
    this.stats.adaptationChanged += result.changed;
    chunk.adapted = true;
  }

  applyOres(chunk) {
    const map = new Map(chunk.terrain.blocks);
    const oreStats = applyOreFeatures(map, chunk, chunk.seed, chunk.biomeByColumn, { crossChunkRadius: 2 });
    for (const [k, block] of map) chunk.terrain.blocks.set(k, block);
    this.stats.normalVeins += oreStats.normalVeins;
    this.stats.normalOreBlocks += oreStats.normalBlocks;
    this.stats.largeVeins += oreStats.largeVeins;
    this.stats.largeOreBlocks += oreStats.largeBlocks;
  }

  makeOps(chunk) {
    return greedyCuboids(chunk.terrain.toArray(), { exactIds: new Set([AIR, WATER, BEDROCK]) });
  }

  *writeOpsJob(chunk, ops) {
    const permutations = new Map();
    for (const op of ops) {
      try {
        if (op.kind === "fill") {
          const cacheKey = `${op.id}|${JSON.stringify(op.states ?? {})}`;
          let p = permutations.get(cacheKey);
          if (!p) { p = permutation(op.id, op.states); if (p) permutations.set(cacheKey, p); }
          if (!p) { this.stats.failed++; yield; continue; }
          chunk.dimension.fillBlocks(volume(op), p, { ignoreChunkBoundErrors: true });
          this.stats.fills++;
        } else if (op.block) {
          const b = op.block, cacheKey = `${b.id}|${JSON.stringify(b.states ?? {})}`;
          let p = permutations.get(cacheKey);
          if (!p) { p = permutation(b.id, b.states); if (p) permutations.set(cacheKey, p); }
          if (!p) { this.stats.failed++; yield; continue; }
          chunk.dimension.setBlockPermutation({ x: b.x, y: b.y, z: b.z }, p);
          this.stats.singles++;
        }
      } catch (error) {
        this.stats.failed++;
        if (this.options.strict) throw error;
      }
      yield;
    }
    chunk.written = true;
    this.stats.chunks++;
  }

  flush(k) {
    const chunk = this.cache.get(k) ?? this.pending.get(k);
    if (!chunk || this.flushJobs.has(k) || !chunk.generated) return false;
    this.pending.delete(k); this.ready.delete(k); this.flushJobs.add(k);
    const jobs = jobsForChunk(this.generator, chunk), self = this;
    system.runJob((function* () {
      try {
        self.applyDeterministicVariants(chunk); yield;
        self.applyCombinedAdaptation(chunk, jobs); yield;
        self.applyOres(chunk); yield;
        yield* self.writeOpsJob(chunk, self.makeOps(chunk));
      } finally { self.flushJobs.delete(k); }
    })());
    return true;
  }

  prepareLateStructure(job) {
    if (!job?.key) return;
    const x = Number(job.x ?? 0), z = Number(job.z ?? 0), cx = Math.floor(x / 16), cz = Math.floor(z / 16);
    for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) {
      const k = `${cx + dx},${cz + dz}`, chunk = this.cache.get(k);
      if (!chunk || !chunk.written) continue;
      const minX = (cx + dx) * 16 - 16, maxX = (cx + dx) * 16 + 31;
      const minZ = (cz + dz) * 16 - 16, maxZ = (cz + dz) * 16 + 31;
      const rx = Number(job.footprint?.x ?? 16) / 2, rz = Number(job.footprint?.z ?? 16) / 2;
      if (x + rx < minX || x - rx > maxX || z + rz < minZ || z - rz > maxZ) continue;
      const engine = new InMemoryTerrainAdaptationEngine({ minY: -64, maxY: 320, matchRadius: 3, buryDepth: 8 });
      engine.adaptCombined(chunk.terrain, [job]);
      const ops = this.makeOps(chunk), self = this, handleKey = `${k}:late:${job.key}`;
      this.flushJobs.add(handleKey);
      system.runJob((function* () {
        try { yield* self.writeOpsJob(chunk, ops); }
        finally { self.flushJobs.delete(handleKey); }
      })());
      this.preparedStructureKeys.add(job.key);
      this.stats.lateStructureAdaptations++;
    }
  }

  tick() {
    this.originalTick();
    for (const k of [...this.ready]) this.flush(k);
  }

  flushAll() { for (const k of this.ready) this.flush(k); }
  snapshot() {
    return { ...this.stats, pendingChunks: this.pending.size, cachedChunks: this.cache.size, readyChunks: this.ready.size, activeBulkJobs: this.flushJobs.size, preparedStructures: this.preparedStructureKeys.size };
  }
}

export function installBulkTerrainRuntime(generator, options = {}) {
  return new BulkTerrainRuntime(generator, options).install();
}
