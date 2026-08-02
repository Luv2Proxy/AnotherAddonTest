import { createPlacementAdapters, classifyPlacement, adapterFor } from "./PlacementEngineAdapters.js";

export class PlacementEngineOrchestrator {
  constructor(generator, options = {}) {
    this.generator = generator;
    this.registry = options.registry ?? generator?.structureRegistry ?? generator?.jigsawRegistry;
    this.adapters = createPlacementAdapters(generator);
    this.defaultOptions = options.defaultOptions ?? {};
  }

  refresh(registry = this.generator?.structureRegistry ?? this.generator?.jigsawRegistry) {
    if (registry) this.registry = registry;
    this.adapters = createPlacementAdapters(this.generator);
    return this;
  }

  async place(candidate, context = {}) {
    const classification = classifyPlacement(this.registry, candidate);
    const adapter = adapterFor(this.adapters, classification);
    if (!adapter) return { placed: false, reason: "no_placement_adapter", classification };

    const id = candidate?.structure ?? candidate?.id;
    const location = context.location ?? { x: candidate?.x ?? 0, y: candidate?.y ?? 128, z: candidate?.z ?? 0 };
    const options = { ...this.defaultOptions, ...context.options, category: classification.category, kind: classification.kind };

    try {
      const result = await adapter(id, context.dimension, location, options, context.placementKey);
      if (result && typeof result === "object") return { ...result, classification };
      return { placed: result !== false, classification };
    } catch (error) {
      return { placed: false, reason: "placement_exception", error: String(error), classification };
    }
  }
}
