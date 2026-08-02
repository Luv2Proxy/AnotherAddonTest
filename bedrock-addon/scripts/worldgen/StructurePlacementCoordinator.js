import { PlacementEngineOrchestrator } from "./PlacementEngineOrchestrator.js";
import { applyPlacementPolicy } from "./PlacementEngineBatchAdapters.js";
import { CategoryPlacementEngines } from "./CategoryPlacementEngines.js";
import { classifyPlacement } from "./PlacementEngineAdapters.js";
import { computeTerrainAdaptation, validateIslandPlacement } from "./StructurePlacementPolicies.js";
import { ProcessorPipeline } from "./ProcessorPipeline.js";

export class StructurePlacementCoordinator {
  constructor(generator, options = {}) {
    this.generator = generator;
    this.registry = options.registry ?? generator?.structureRegistry ?? generator?.jigsawRegistry;
    this.orchestrator = options.orchestrator ?? new PlacementEngineOrchestrator(generator, { registry: this.registry });
    this.engines = options.engines ?? new CategoryPlacementEngines(generator);
    this.processors = options.processors ?? new ProcessorPipeline(this.registry);
    this.placed = new Set();
    this.failed = new Map();
  }

  refresh(registry) {
    if (registry) this.registry = registry;
    this.processors.registry = this.registry;
    this.orchestrator.refresh(this.registry);
    this.engines.generator = this.generator;
  }

  key(candidate, location, context = {}) {
    const id = candidate?.structure ?? candidate?.id ?? "unknown";
    return `${context.setId ?? "default"}:${id}:${Math.floor(location.x / 16)}:${Math.floor(location.z / 16)}`;
  }

  applyProcessors(candidate, context) {
    const processorId = candidate?.processor_list ?? candidate?.processors ?? context?.options?.processor_list;
    if (!processorId || !candidate?.blocks) return candidate;
    const blocks = [];
    for (let i = 0; i < candidate.blocks.length; i++) {
      const processed = this.processors.apply(candidate.blocks[i], processorId, {
        ...context,
        seed: context.seed ?? 0,
        position: candidate.blocks[i].position,
        rotation: context.options?.rotation ?? 0
      });
      if (processed) blocks.push(processed);
    }
    return { ...candidate, blocks };
  }

  async place(candidate, context = {}) {
    const classification = classifyPlacement(this.registry, candidate);
    const rawLocation = context.location ?? { x: candidate?.x ?? 0, y: candidate?.y ?? 128, z: candidate?.z ?? 0 };
    const policyResult = applyPlacementPolicy(candidate, classification.category, rawLocation);
    const location = policyResult.location;
    const placementKey = context.placementKey ?? this.key(candidate, location, context);

    if (this.placed.has(placementKey)) {
      return { placed: false, reason: "already_placed", placementKey, classification };
    }

    const host = context.host ?? candidate?.host;
    const validation = validateIslandPlacement({
      category: classification.category,
      host,
      footprintRadius: Number(candidate?.footprintRadius ?? candidate?.footprint?.radius ?? 4),
      slope: Number(candidate?.slope ?? 0),
      clearance: Number(candidate?.clearance ?? Infinity)
    });

    if (!validation.valid && validation.reason !== "no_host_island" && classification.category !== "WATER") {
      const result = { placed: false, reason: validation.reason, placementKey, classification, validation };
      this.failed.set(placementKey, { ...result, timestamp: Date.now() });
      return result;
    }

    const terrainAdaptation = computeTerrainAdaptation({
      category: classification.category,
      candidate,
      host: host ?? {},
      location
    });

    const adaptedLocation = {
      ...location,
      y: terrainAdaptation.mode === "none" ? location.y : terrainAdaptation.targetY
    };

    const processedCandidate = this.applyProcessors(candidate, { ...context, location: adaptedLocation });
    const engineContext = {
      ...context,
      location: adaptedLocation,
      placementKey,
      host,
      options: {
        ...(context.options ?? {}),
        ...policyResult.policy,
        classification,
        validation,
        terrainAdaptation
      }
    };

    let result = await this.engines.place(classification.category, processedCandidate, engineContext);
    if (result == null) result = await this.orchestrator.place(processedCandidate, engineContext);
    if (result && typeof result !== "object") result = { placed: result !== false };
    result = result ?? { placed: false, reason: "no_placement_adapter" };

    if (result.placed) this.placed.add(placementKey);
    else this.failed.set(placementKey, { ...result, timestamp: Date.now() });

    return {
      ...result,
      placementKey,
      location: adaptedLocation,
      policy: policyResult.policy,
      classification,
      validation,
      terrainAdaptation
    };
  }
}
