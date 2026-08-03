import { PlacementEngineOrchestrator } from "./PlacementEngineOrchestrator.js";
import { applyPlacementPolicy } from "./PlacementEngineBatchAdapters.js";
import { CategoryPlacementEngines } from "./CategoryPlacementEngines.js";
import { classifyPlacement } from "./PlacementEngineAdapters.js";
import { computeTerrainAdaptation, validateIslandPlacement } from "./StructurePlacementPolicies.js";
import { ProcessorPipeline } from "./ProcessorPipeline.js";
import { TerrainAdaptationEngine } from "./TerrainAdaptationEngine.js";

function normalizeBounds(value, location = { x: 0, y: 0, z: 0 }) {
  if (!value) return null;
  if (value.minX != null) return { minX: Number(value.minX), minY: Number(value.minY), minZ: Number(value.minZ), maxX: Number(value.maxX), maxY: Number(value.maxY), maxZ: Number(value.maxZ) };
  const min = value.min ?? value.minimum ?? value.from, max = value.max ?? value.maximum ?? value.to;
  if (min && max) return { minX: Number(min.x), minY: Number(min.y), minZ: Number(min.z), maxX: Number(max.x), maxY: Number(max.y), maxZ: Number(max.z) };
  if (value.size) return { minX: Number(value.x ?? location.x), minY: Number(value.y ?? location.y), minZ: Number(value.z ?? location.z), maxX: Number(value.x ?? location.x) + Number(value.size.x ?? 1) - 1, maxY: Number(value.y ?? location.y) + Number(value.size.y ?? 1) - 1, maxZ: Number(value.z ?? location.z) + Number(value.size.z ?? 1) - 1 };
  return null;
}

function boundsFromResult(result, location, candidate) {
  const direct = normalizeBounds(result?.bounds, location) ?? normalizeBounds(result?.boundingBox, location) ?? normalizeBounds(result?.box, location);
  if (direct) return [direct];
  const pieces = result?.pieces ?? result?.placedPieces;
  if (Array.isArray(pieces)) {
    const boxes = pieces.map(piece => normalizeBounds(piece?.bounds ?? piece?.boundingBox ?? piece?.box, piece?.origin ?? location)).filter(Boolean);
    if (boxes.length) return boxes;
  }
  const candidateBounds = candidate?.pieceBounds ?? candidate?.boundingBoxes;
  if (Array.isArray(candidateBounds)) return candidateBounds.map(b => normalizeBounds(b, location)).filter(Boolean);
  return [];
}

export class StructurePlacementCoordinator {
  constructor(generator, options = {}) {
    this.generator = generator;
    this.registry = options.registry ?? generator?.structureRegistry ?? generator?.jigsawRegistry;
    this.orchestrator = options.orchestrator ?? new PlacementEngineOrchestrator(generator, { registry: this.registry });
    this.engines = options.engines ?? new CategoryPlacementEngines(generator);
    this.processors = options.processors ?? new ProcessorPipeline(this.registry);
    this.terrain = options.terrain ?? new TerrainAdaptationEngine(generator?.dimension ?? null, options.terrainOptions ?? {});
    this.placed = new Set();
    this.failed = new Map();
  }

  refresh(registry) { if (registry) this.registry = registry; this.processors.registry = this.registry; this.orchestrator.refresh(this.registry); this.engines.generator = this.generator; this.terrain.dimension = this.generator?.dimension ?? this.terrain.dimension; return this; }
  key(candidate, location, context = {}) { const id = candidate?.structure ?? candidate?.id ?? "unknown"; return `${context.setId ?? "default"}:${id}:${Math.floor(location.x / 16)}:${Math.floor(location.z / 16)}`; }

  applyProcessors(candidate, context) {
    const processorId = candidate?.processor_list ?? candidate?.processors ?? context?.options?.processor_list;
    if (!processorId || !candidate?.blocks) return candidate;
    const blocks = [];
    for (let i = 0; i < candidate.blocks.length; i++) {
      const processed = this.processors.apply(candidate.blocks[i], processorId, { ...context, seed: context.seed ?? 0, position: candidate.blocks[i].position, rotation: context.options?.rotation ?? 0 });
      if (processed) blocks.push(processed);
    }
    return { ...candidate, blocks };
  }

  applyTerrain(result, candidate, context, terrainAdaptation, location) {
    if (!this.terrain?.dimension) return { result, terrain: null };
    const boxes = boundsFromResult(result, location, candidate);
    if (!boxes.length) return { result, terrain: null, reason: "placement_result_has_no_bounds" };
    const mode = String(terrainAdaptation?.mode ?? "none").toLowerCase();
    if (!mode || mode === "none") return { result, terrain: null, boxes };

    const adaptationCandidate = { ...candidate, pieceBounds: boxes, terrain_adaptation: mode, targetY: terrainAdaptation.targetY, foundationBlock: context.options?.foundationBlock ?? "minecraft:dirt" };
    // Never execute TerrainAdaptationEngine.adapt() synchronously from a placement
    // promise. It performs native getBlock/setBlock calls and can exceed the script
    // watchdog. scheduleAdaptation() hands the generator to Bedrock's job system.
    const terrain = this.terrain.scheduleAdaptation(adaptationCandidate, {
      ...context,
      location,
      mode,
      targetY: terrainAdaptation.targetY,
      depth: candidate?.buryDepth ?? candidate?.depth,
      foundationBlock: context.options?.foundationBlock ?? "minecraft:dirt",
      maxTerrainColumns: context.options?.maxTerrainColumns ?? 32
    });
    return { result, terrain, boxes };
  }

  async place(candidate, context = {}) {
    const classification = classifyPlacement(this.registry, candidate);
    const rawLocation = context.location ?? { x: candidate?.x ?? 0, y: candidate?.y ?? 128, z: candidate?.z ?? 0 };
    const policyResult = applyPlacementPolicy(candidate, classification.category, rawLocation);
    const location = policyResult.location;
    const placementKey = context.placementKey ?? this.key(candidate, location, context);
    if (this.placed.has(placementKey)) return { placed: false, reason: "already_placed", placementKey, classification };

    const host = context.host ?? candidate?.host;
    const validation = validateIslandPlacement({ category: classification.category, host, footprintRadius: Number(candidate?.footprintRadius ?? candidate?.footprint?.radius ?? 4), slope: Number(candidate?.slope ?? 0), clearance: Number(candidate?.clearance ?? Infinity) });
    if (!validation.valid && validation.reason !== "no_host_island" && classification.category !== "WATER") {
      const result = { placed: false, reason: validation.reason, placementKey, classification, validation };
      this.failed.set(placementKey, { ...result, timestamp: Date.now() });
      return result;
    }

    const terrainAdaptation = computeTerrainAdaptation({ category: classification.category, candidate, host: host ?? {}, location });
    const adaptedLocation = { ...location, y: terrainAdaptation.mode === "none" ? location.y : terrainAdaptation.targetY };
    const processedCandidate = this.applyProcessors(candidate, { ...context, location: adaptedLocation });
    const engineContext = { ...context, location: adaptedLocation, placementKey, host, dimension: context.dimension ?? this.generator?.dimension, options: { ...(context.options ?? {}), classification, validation, terrainAdaptation } };

    let result = await this.engines.place(classification.category, processedCandidate, engineContext);
    if (result == null) result = await this.orchestrator.place(processedCandidate, engineContext);
    if (result && typeof result !== "object") result = { placed: result !== false };
    result = result ?? { placed: false, reason: "no_placement_adapter" };
    if (!result.placed) {
      this.failed.set(placementKey, { ...result, timestamp: Date.now() });
      return { ...result, placementKey, location: adaptedLocation, policy: policyResult.policy, classification, validation, terrainAdaptation };
    }

    const terrainApplied = this.applyTerrain(result, processedCandidate, engineContext, terrainAdaptation, adaptedLocation);
    this.placed.add(placementKey);
    return { ...result, placementKey, location: adaptedLocation, policy: policyResult.policy, classification, validation, terrainAdaptation, terrain: terrainApplied.terrain, terrainBounds: terrainApplied.boxes, terrainReason: terrainApplied.reason };
  }
}
