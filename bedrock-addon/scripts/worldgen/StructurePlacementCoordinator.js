import { PlacementEngineOrchestrator } from "./PlacementEngineOrchestrator.js";
import { applyPlacementPolicy } from "./PlacementEngineBatchAdapters.js";
import { CategoryPlacementEngines } from "./CategoryPlacementEngines.js";
import { classifyPlacement } from "./PlacementEngineAdapters.js";
import { validateIslandPlacement } from "./StructurePlacementPolicies.js";

export class StructurePlacementCoordinator {
  constructor(generator, options = {}) {
    this.generator = generator;
    this.registry = options.registry ?? generator?.structureRegistry ?? generator?.jigsawRegistry;
    this.orchestrator = options.orchestrator ?? new PlacementEngineOrchestrator(generator, { registry: this.registry });
    this.engines = options.engines ?? new CategoryPlacementEngines(generator);
    this.placed = new Set();
    this.failed = new Map();
  }

  refresh(registry) {
    if (registry) this.registry = registry;
    this.orchestrator.refresh(this.registry);
    this.engines.generator = this.generator;
  }

  key(candidate, location, context = {}) {
    const id = candidate?.structure ?? candidate?.id ?? "unknown";
    return `${context.setId ?? "default"}:${id}:${Math.floor(location.x / 16)}:${Math.floor(location.z / 16)}`;
  }

  async place(candidate, context = {}) {
    const classification = classifyPlacement(this.registry, candidate);
    const rawLocation = context.location ?? { x: candidate?.x ?? 0, y: candidate?.y ?? 128, z: candidate?.z ?? 0 };
    const policyResult = applyPlacementPolicy(candidate, classification.category, rawLocation);
    const location = policyResult.location;
    const placementKey = context.placementKey ?? this.key(candidate, location, context);
    if (this.placed.has(placementKey)) return { placed: false, reason: "already_placed", placementKey, classification };

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

    const engineContext = {
      ...context,
      location,
      placementKey,
      options: { ...(context.options ?? {}), ...policyResult.policy, classification, validation }
    };
    let result = await this.engines.place(classification.category, candidate, engineContext);
    if (result == null) result = await this.orchestrator.place(candidate, engineContext);
    if (result && typeof result !== "object") result = { placed: result !== false };
    result = result ?? { placed: false, reason: "no_placement_adapter" };

    if (result.placed) this.placed.add(placementKey);
    else this.failed.set(placementKey, { ...result, timestamp: Date.now() });
    return { ...result, placementKey, location, policy: policyResult.policy, classification, validation };
  }
}