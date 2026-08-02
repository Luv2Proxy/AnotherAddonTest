import { JigsawGenerator } from "./JigsawGenerator.js";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { getGeneratedJigsawData } from "./JigsawDataLoader.js";

/**
 * Single entry point for addon world-generation code.
 * All metadata comes from generated/jigsaw-data.js. No source JSON is read at
 * runtime. Structure binaries remain normal behavior-pack .mcstructure assets.
 */
export class WorldgenJigsawRuntime {
  constructor(dimension, options = {}) {
    this.dimension = dimension;
    this.data = options.data ?? getGeneratedJigsawData();
    this.registry = options.registry ?? new JigsawRegistry(this.data);
    this.generator = options.generator ?? new JigsawGenerator(dimension, { ...options, data: this.data, registry: this.registry });
  }

  snapshot() { return this.registry.snapshot(); }

  planStructure(identifier, origin, seed, options = {}) {
    return this.generator.plan(identifier, origin, seed, options);
  }

  planStructureSet(identifier, seed, options = {}) {
    return this.generator.planStructureSet(identifier, seed, options);
  }

  generate(identifier, origin = { x: 0, y: 0, z: 0 }, seed = 0, options = {}) {
    const plan = this.planStructure(identifier, origin, seed, options);
    if (!plan.ok) return plan;
    if (options.place === false) return plan;
    return { ...plan, placement: this.#placePlan(plan, options) };
  }

  generateStructureSet(setId, seed = 0, options = {}) {
    const setPlan = this.planStructureSet(setId, seed, options);
    if (!setPlan.ok || options.place === false) return setPlan;
    const results = [];
    for (const item of setPlan.placements) {
      results.push(this.generator.placeStructure(item.structure, { x: item.x, y: item.y, z: item.z }, { seed, ...options }));
    }
    return { ...setPlan, results };
  }

  #placePlan(plan, options) {
    const results = [];
    for (const piece of plan.pieces ?? []) {
      const id = piece.id;
      const result = this.generator.placeStructure(id, piece.origin, {
        rotation: piece.rotation,
        includeEntities: options.includeEntities ?? true,
        keepJigsaws: options.keepJigsaws ?? false,
        ...options
      });
      results.push({ piece: id, ...result });
    }
    return results;
  }
}

export function createWorldgenJigsawRuntime(dimension, options = {}) {
  return new WorldgenJigsawRuntime(dimension, options);
}
