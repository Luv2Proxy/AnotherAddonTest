import { JigsawGenerator } from "./JigsawGenerator.js";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { getGeneratedJigsawData } from "./JigsawDataLoader.js";
import { StructureSetRuntime } from "./StructureSetRuntime.js";
import { GeneratedStructurePlanner } from "./GeneratedStructurePlanner.js";
import { StructureDensityField } from "./StructureDensityField.js";

/**
 * Single entry point for addon world-generation code.
 * Generated jigsaw-data.js is the only runtime artifact derived from the
 * extracted JSON worldgen database. Native Bedrock Jigsaw placement is used
 * whenever available; the extracted connector graph is the fallback.
 */
export class WorldgenJigsawRuntime {
  constructor(dimension, options = {}) {
    this.dimension = dimension;
    this.data = options.data ?? getGeneratedJigsawData();
    this.registry = options.registry ?? new JigsawRegistry(this.data);
    this.generator = options.generator ?? new JigsawGenerator(dimension, { ...options, data: this.data, registry: this.registry });
    this.generatedPlanner = options.generatedPlanner ?? new GeneratedStructurePlanner({ registry: this.registry });
    this.densityField = options.densityField ?? new StructureDensityField();
    this.structureSets = options.structureSets ?? new StructureSetRuntime(this.generator, {
      ...options,
      registry: this.registry,
      data: this.data,
      densityField: this.densityField,
      dimensionId: dimension?.id ?? options.dimensionId
    });
  }

  snapshot() {
    return {
      registry: this.registry.snapshot(),
      structureSets: this.structureSets.sets.length,
      densityBoxes: this.densityField.boxes.length,
      densityJunctions: this.densityField.junctions.length
    };
  }

  refresh() {
    this.structureSets.refresh(this.registry);
    return this;
  }

  planStructure(identifier, origin, seed, options = {}) {
    return this.generator.plan(identifier, origin, seed, options);
  }

  planStructureSet(identifier, seed, options = {}) {
    return this.generator.planStructureSet(identifier, seed, options);
  }

  generate(identifier, origin = { x: 0, y: 0, z: 0 }, seed = 0, options = {}) {
    const plan = this.planStructure(identifier, origin, seed, options);
    if (!plan.ok || options.place === false) return plan;
    return { ...plan, placement: this.#placePlan(plan, options) };
  }

  generateStructureSet(setId, seed = 0, options = {}) {
    return this.structureSets.adapter.plan({ setId, x: options.x ?? 0, y: options.y ?? 128, z: options.z ?? 0 }, seed);
  }

  enqueueAround(x, z) {
    return this.structureSets.enqueueAround(x, z);
  }

  async process() {
    return this.structureSets.process();
  }

  tick() {
    return this.structureSets.tick();
  }

  densityAt(x, y, z) {
    return this.densityField.densityAt(x, y, z);
  }

  validate(identifier) {
    return this.generator.validateStructure(identifier);
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
