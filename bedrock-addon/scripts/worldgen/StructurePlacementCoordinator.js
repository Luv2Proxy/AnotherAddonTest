import { PlacementEngineOrchestrator } from "./PlacementEngineOrchestrator.js";
import { applyPlacementPolicy } from "./PlacementEngineBatchAdapters.js";

export class StructurePlacementCoordinator {
  constructor(generator, options = {}) {
    this.generator = generator;
    this.registry = options.registry ?? generator?.structureRegistry ?? generator?.jigsawRegistry;
    this.orchestrator = options.orchestrator ?? new PlacementEngineOrchestrator(generator, { registry: this.registry });
    this.placed = new Set();
    this.failed = new Map();
  }

  refresh(registry) {
    if (registry) this.registry = registry;
    this.orchestrator.refresh(this.registry);
  }

  key(candidate, location, context = {}) {
    const id = candidate?.structure ?? candidate?.id ?? "unknown";
    return `${context.setId ?? "default"}:${id}:${Math.floor(location.x / 16)}:${Math.floor(location.z / 16)}`;
  }

  async place(candidate, context = {}) {
    const classification = this.orchestrator.registry
      ? (await import("./PlacementEngineAdapters.js")).classifyPlacement(this.orchestrator.registry, candidate)
      : { category: candidate?.category, kind: "template" };
    const rawLocation = context.location ?? { x: candidate?.x ?? 0, y: candidate?.y ?? 128, z: candidate?.z ?? 0 };
    const policyResult = applyPlacementPolicy(candidate, classification.category, rawLocation);
    const location = policyResult.location;
    const placementKey = context.placementKey ?? this.key(candidate, location, context);

    if (this.placed.has(placementKey)) return { placed: false, reason: "already_placed", placementKey, classification };

    const result = await this.orchestrator.place(candidate, {
      ...context,
      location,
      placementKey,
      options: { ...(context.options ?? {}), ...policyResult.policy }
    });

    if (result.placed) this.placed.add(placementKey);
    else this.failed.set(placementKey, { ...result, timestamp: Date.now() });
    return { ...result, placementKey, location, policy: policyResult.policy };
  }
}
