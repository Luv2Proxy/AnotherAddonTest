import { JigsawRegistry } from "./JigsawRegistry.js";
import { StructureSetGenerator } from "./StructureSetGenerator.js";

/**
 * Runtime bridge between generated jigsaw-data.js and the island generator.
 * It deliberately stays separate from IslandGenerator so the existing terrain
 * generator can keep its current lifecycle while structure-set generation is
 * added incrementally.
 */
export class StructureSetRuntime {
  constructor(generator, options = {}) {
    this.generator = generator;
    this.registry = options.registry ?? new JigsawRegistry();
    this.sets = options.sets ?? this.registry.structureSetIds?.() ?? [...this.registry.structureSets.keys()];
    this.planner = options.planner ?? new StructureSetGenerator(this.registry);
    this.dimensionId = options.dimensionId ?? "sky_archipelago:archipelago";
    this.radius = Number(options.radius ?? 512);
    this.maxPlansPerTick = Math.max(1, Number(options.maxPlansPerTick ?? 2));
    this.maxPlacementsPerTick = Math.max(1, Number(options.maxPlacementsPerTick ?? 4));
    this.active = new Map();
    this.pending = [];
    this.pendingKeys = new Set();
    this.seed = generator?.layoutSeed ?? 0n;
  }

  refresh() {
    this.registry = this.generator?.registry ?? this.registry;
    this.planner.registry = this.registry;
    this.sets = this.registry.structureSetIds?.() ?? [...this.registry.structureSets.keys()];
    return this;
  }

  key(setId, x, z) { return `${setId}:${Math.floor(x / 16)}:${Math.floor(z / 16)}`; }

  enqueueAround(x, z) {
    if (!this.sets.length) return 0;
    let added = 0;
    for (const setId of this.sets) {
      const key = this.key(setId, x, z);
      if (this.pendingKeys.has(key) || this.active.has(key)) continue;
      this.pendingKeys.add(key);
      this.pending.push({ setId, x, z, key });
      added++;
    }
    return added;
  }

  async process() {
    if (!this.generator || !this.pending.length) return { planned: 0, placed: 0 };
    const dimension = this.generator.native?.dimension ?? this.generator.placement?.adapter?.dimension;
    if (!dimension) return { planned: 0, placed: 0 };

    let planned = 0, placed = 0;
    for (let i = 0; i < this.maxPlansPerTick && this.pending.length; i++) {
      const job = this.pending.shift();
      this.pendingKeys.delete(job.key);
      const seed = `${this.seed}:${job.setId}:${Math.floor(job.x / 16)}:${Math.floor(job.z / 16)}`;
      const plan = this.planner.planAround(job.setId, { x: job.x, y: 128, z: job.z }, seed, {
        radius: this.radius,
        count: this.maxPlacementsPerTick
      });
      if (!plan?.placements?.length) continue;
      planned++;

      const candidates = plan.placements.slice(0, this.maxPlacementsPerTick);
      for (const candidate of candidates) {
        const id = candidate.structure;
        if (!id) continue;
        const placementKey = `${job.setId}:${id}:${Math.floor(candidate.x / 16)}:${Math.floor(candidate.z / 16)}`;
        if (this.generator.placedStructureKeys?.has(placementKey)) continue;

        const terrain = this.generator.column?.(candidate.x, candidate.z) ?? [];
        const top = terrain.length ? Math.max(...terrain.map(segment => segment[1])) : candidate.y;
        const location = { x: candidate.x, y: top, z: candidate.z };
        let result;
        try {
          result = await this.generator.placement.placeTemplate(id, dimension, location, {
            rotation: "None",
            overlapPadding: 2,
            seed: candidate.seed
          });
        } catch (error) {
          result = { placed: false, reason: "exception", error: String(error) };
        }
        if (result?.placed) {
          placed++;
          this.generator.placedStructureKeys?.add(placementKey);
        }
      }
      this.active.set(job.key, { planned: true, timestamp: Date.now() });
    }
    return { planned, placed };
  }

  tick() {
    if (!this.generator) return;
    this.refresh();
    for (const player of this.generator.constructor?.world?.getAllPlayers?.() ?? []) {
      if (player.dimension?.id === this.dimensionId) this.enqueueAround(player.location.x, player.location.z);
    }
  }
}
