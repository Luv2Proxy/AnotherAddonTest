import { system } from "@minecraft/server";
import { planColumn } from "./ExactMaterialPlan.js";
import { terrainProfile } from "./BiomeTerrainProfiles.js";
import { greedyCuboids, writeBulk } from "./BulkWorldWriter.js";
import { applyOreFeatures } from "./ore/OreFeatureEngine.js";

const AIR = "minecraft:air";
const STONE = "minecraft:stone";
const DEEPSLATE = "minecraft:deepslate";
const BEDROCK = "minecraft:bedrock";
const WATER = "minecraft:water";
const VARIANTS = ["minecraft:granite", "minecraft:diorite", "minecraft:andesite"];

function hash32(x, z, seed, salt = 0) {
  let h = (Number(seed) & 0xffffffff) ^ Math.imul(x | 0, 0x45d9f3b) ^ Math.imul(z | 0, 0x119de1f3) ^ salt;
  h = Math.imul(h ^ (h >>> 16), 0x45d9f3b);
  h = Math.imul(h ^ (h >>> 16), 0x45d9f3b);
  return (h ^ (h >>> 16)) >>> 0;
}

function materialId(p) { return p?.type?.id ?? p?.typeId ?? null; }
function key(x, y, z) { return `${x},${y},${z}`; }
function eligibleStone(id) {
  return id === STONE || id === DEEPSLATE || id === "minecraft:tuff" || id === "minecraft:granite" || id === "minecraft:diorite" || id === "minecraft:andesite";
}

function variantFor(x, y, z, seed) {
  const r = hash32(x ^ y, z ^ (y * 31), seed, 0x9e3779b9) / 4294967296;
  if (y < 0 && r < 0.035) return "minecraft:tuff";
  if (r < 0.055) return VARIANTS[Math.floor(r * 1000) % VARIANTS.length];
  return null;
}

function toOps(map) {
  return greedyCuboids([...map.values()], {
    exactIds: new Set([AIR, WATER, BEDROCK])
  });
}

export class BulkTerrainRuntime {
  constructor(generator, options = {}) {
    this.generator = generator;
    this.options = options;
    this.pending = new Map();
    this.flushJobs = new Set();
    this.originalGenerate = null;
    this.originalTick = null;
    this.installed = false;
    this.stats = {
      columns: 0,
      chunks: 0,
      fills: 0,
      singles: 0,
      failed: 0,
      normalVeins: 0,
      normalOreBlocks: 0,
      largeVeins: 0,
      largeOreBlocks: 0,
      variants: 0
    };
  }

  install() {
    if (this.installed) return this;
    this.installed = true;
    this.originalGenerate = this.generator.generateColumn.bind(this.generator);
    this.originalTick = this.generator.tick.bind(this.generator);
    const self = this;

    this.generator.generateColumn = function (dimension, x, z, segments) {
      return self.collect(dimension, x, z, segments);
    };

    this.generator.tick = function () {
      return self.tick();
    };

    return this;
  }

  collect(dimension, x, z, segments) {
    if (!segments?.length) return;

    const cx = Math.floor(x / 16);
    const cz = Math.floor(z / 16);
    const k = `${cx},${cz}`;
    let chunk = this.pending.get(k);

    if (!chunk) {
      chunk = {
        cx,
        cz,
        dimension,
        map: new Map(),
        biomeByColumn: new Map(),
        columns: new Set(),
        seed: this.generator.layoutSeed
      };
      this.pending.set(k, chunk);
    }

    const top = Math.max(...segments.map(s => s[1]));
    const biome = this.generator.biomeAt(dimension, x, top, z);
    const profile = terrainProfile(biome);
    const plan = planColumn(
      segments,
      -64,
      321,
      {
        ...this.generator.settings,
        deepslateStartY: this.generator.settings.deepslateStartY - (profile[0] - 1) * 3
      },
      x,
      z,
      this.generator.layoutSeed
    );

    for (let y = -64; y <= 320; y++) {
      const p = plan.materialAt(y);
      const id = materialId(p);

      if (id) {
        chunk.map.set(key(x, y, z), { x, y, z, id });
      } else if (this.generator.settings.oceanEnabled && y <= plan.oceanTop && y > top) {
        chunk.map.set(key(x, y, z), { x, y, z, id: WATER });
      }
    }

    chunk.biomeByColumn.set(`${x},${z}`, biome);
    chunk.columns.add(`${x},${z}`);
    this.stats.columns++;

    if (chunk.columns.size >= 16) this.flush(k);
  }

  flush(k) {
    const chunk = this.pending.get(k);
    if (!chunk || this.flushJobs.has(k)) return;

    this.pending.delete(k);
    this.flushJobs.add(k);
    const self = this;

    const job = function* () {
      try {
        // Stage 1: deterministic stone variants. No world reads are required.
        for (const v of chunk.map.values()) {
          if (eligibleStone(v.id)) {
            const variant = variantFor(v.x, v.y, v.z, chunk.seed);
            if (variant) {
              v.id = variant;
              self.stats.variants++;
            }
          }
          yield;
        }

        // Stage 2: all normal placed-feature ore distributions plus large
        // cross-chunk vein origins. Everything operates on the in-memory voxel
        // map, so no getBlock()/getBlocks() calls are made in the hot path.
        const oreStats = applyOreFeatures(
          chunk.map,
          chunk,
          chunk.seed,
          chunk.biomeByColumn,
          { crossChunkRadius: 2 }
        );
        self.stats.normalVeins += oreStats.normalVeins;
        self.stats.normalOreBlocks += oreStats.normalBlocks;
        self.stats.largeVeins += oreStats.largeVeins;
        self.stats.largeOreBlocks += oreStats.largeBlocks;
        yield;

        // Stage 3: merge all identical terrain/ore voxels into rectangular
        // cuboids and perform bulk writes. Exact/stateful structure placement
        // remains on the existing non-bulk structure pipeline.
        const ops = toOps(chunk.map);
        const result = writeBulk(chunk.dimension, ops, { strict: false });
        self.stats.fills += result.fills;
        self.stats.singles += result.singles;
        self.stats.failed += result.failed;
        self.stats.chunks++;
        yield;
      } finally {
        self.flushJobs.delete(k);
      }
    };

    system.runJob(job());
  }

  tick() {
    this.originalTick();

    for (const [k, chunk] of this.pending) {
      if (chunk.columns.size >= 16) this.flush(k);
    }
  }

  flushAll() {
    for (const k of this.pending.keys()) this.flush(k);
  }

  snapshot() {
    return {
      ...this.stats,
      pendingChunks: this.pending.size,
      activeBulkJobs: this.flushJobs.size
    };
  }
}

export function installBulkTerrainRuntime(generator, options = {}) {
  return new BulkTerrainRuntime(generator, options).install();
}
