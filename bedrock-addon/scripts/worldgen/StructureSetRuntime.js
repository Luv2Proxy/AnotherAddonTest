import { world } from "@minecraft/server";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { StructureSetGenerator } from "./StructureSetGenerator.js";
import { StructureSetRuntimeAdapter } from "./StructureSetRuntimeAdapter.js";
import { StructurePlacementCoordinator } from "./StructurePlacementCoordinator.js";
import { JigsawExpansionEngine } from "./JigsawExpansionEngine.js";
import { JigsawCollisionValidator } from "./JigsawCollisionValidator.js";
import { StructureTerrainValidation, validateTerrain, validateWater, validateUnderground } from "./StructureTerrainValidation.js";

const RUNTIME_DB = "sky_archipelago:structure_set_runtime_v3";

function surfaceY(generator, x, z, fallback = 128) {
  try {
    const segments = generator?.column?.(Math.floor(x), Math.floor(z), -64, 320) ?? [];
    if (Array.isArray(segments) && segments.length) {
      let top = -Infinity;
      for (const segment of segments) {
        if (Array.isArray(segment) && segment.length >= 2) top = Math.max(top, Number(segment[1]));
        else if (segment && Number.isFinite(segment.top)) top = Math.max(top, Number(segment.top));
      }
      if (Number.isFinite(top)) return Math.floor(top);
    }
  } catch {}
  return Math.floor(fallback);
}

function familyOf(candidate) {
  const id = String(candidate?.structure ?? candidate?.id ?? "").toLowerCase();
  return candidate?.family ?? (id.includes("ancient_city") ? "ancient_city/" : id.includes("trial_chambers") ? "trial_chambers/" : id.includes("village/") ? "village/" : id.includes("bastion") ? "bastion/" : id.includes("shipwreck") ? "shipwreck/" : "");
}

function isJigsaw(candidate) {
  return Boolean(candidate?.jigsaw || candidate?.assetKind === "jigsaw" || candidate?.jigsawPool || familyOf(candidate));
}

export class StructureSetRuntime {
  constructor(generator, options = {}) {
    this.generator = generator;
    this.registry = options.registry ?? new JigsawRegistry();
    this.sets = options.sets ?? this.registry.structureSetIds?.() ?? [...(this.registry.structureSets?.keys?.() ?? [])];
    this.planner = options.planner ?? new StructureSetGenerator(this.registry);
    this.adapter = options.adapter ?? new StructureSetRuntimeAdapter(this, { registry: this.registry, radius: options.radius });
    this.placementCoordinator = options.placementCoordinator ?? new StructurePlacementCoordinator(generator, { registry: this.registry });
    this.placementQueue = options.placementQueue;
    this.jigsaw = options.jigsaw ?? new JigsawExpansionEngine(this.registry, { maxDepth: 12, maxPieces: 96 });
    this.collision = options.collision ?? new JigsawCollisionValidator({ padding: 1 });
    this.dimensionId = options.dimensionId ?? "sky_archipelago:archipelago";
    this.radius = Number(options.radius ?? 512);
    this.maxPlansPerTick = Math.max(1, Number(options.maxPlansPerTick ?? 1));
    this.maxPlacementsPerTick = Math.max(1, Number(options.maxPlacementsPerTick ?? 2));
    this.active = new Map();
    this.pending = [];
    this.pendingKeys = new Set();
    this.seed = generator?.layoutSeed ?? 0n;
    this.completed = new Set();
    this.failed = new Map();
    this.jigsawBounds = new Map();
    this.load();
  }

  load() {
    try {
      const raw = world.getDynamicProperty(RUNTIME_DB);
      if (typeof raw !== "string") return;
      const d = JSON.parse(raw);
      if (String(d.seed) !== String(this.seed)) return;
      this.completed = new Set(d.completed ?? []);
      this.failed = new Map(d.failed ?? []);
    } catch (e) { console.warn(`[Sky Archipelago] structure persistence load failed: ${e}`); }
  }

  save() {
    try {
      world.setDynamicProperty(RUNTIME_DB, JSON.stringify({ version: 3, seed: String(this.seed), completed: [...this.completed].slice(-2048), failed: [...this.failed].slice(-512) }));
    } catch (e) { console.warn(`[Sky Archipelago] structure persistence save failed: ${e}`); }
  }

  refresh(registry) {
    this.seed = this.generator?.layoutSeed ?? this.seed;
    if (registry) this.registry = registry;
    else if (this.generator?.jigsawRegistry) this.registry = this.generator.jigsawRegistry;
    this.planner.registry = this.registry;
    this.adapter.refresh(this.registry);
    this.placementCoordinator.refresh(this.registry);
    this.jigsaw.registry = this.registry;
    this.jigsaw.poolExpander.registry = this.registry;
    this.jigsaw.connectorResolver.registry = this.registry;
    this.sets = this.registry.structureSetIds?.() ?? [...(this.registry.structureSets?.keys?.() ?? [])];
    return this;
  }

  key(setId, x, z) { return `${setId}:${Math.floor(x / 16)}:${Math.floor(z / 16)}`; }

  enqueueAround(x, z) {
    let added = 0;
    for (const setId of this.sets) {
      const key = this.key(setId, x, z);
      if (this.pendingKeys.has(key) || this.active.has(key) || this.completed.has(key)) continue;
      this.pendingKeys.add(key);
      this.pending.push({ setId, x, z, key });
      added++;
    }
    return added;
  }

  resolveLocation(candidate) {
    const family = familyOf(candidate);
    const category = String(candidate?.category ?? "").toLowerCase();
    let y = surfaceY(this.generator, candidate.x, candidate.z, candidate.y);
    if (category.includes("underground") || family.includes("ancient_city") || family.includes("trial_chambers")) y = Math.max(-32, y - Number(candidate.depth ?? 24));
    if (category.includes("water") || family.includes("shipwreck") || family.includes("underwater") || family.includes("coral")) y = Math.max(0, y - 8);
    return { x: Math.floor(candidate.x), y: Math.floor(y), z: Math.floor(candidate.z) };
  }

  terrainCheck(candidate, location) {
    const category = String(candidate?.category ?? "").toLowerCase();
    if (category.includes("water")) return validateWater(this.generator, location, { waterY: candidate.waterY ?? 62, tolerance: 12 });
    if (category.includes("underground")) return validateUnderground(this.generator, location, { depth: candidate.depth ?? 24 });
    return validateTerrain(this.generator, location, { radius: candidate.footprintRadius ?? 4, maxSlope: candidate.maxSlope ?? 8 });
  }

  async enqueueCandidate(candidate, context) {
    const location = this.resolveLocation(candidate);
    const terrain = this.terrainCheck(candidate, location);
    if (!terrain.valid && String(candidate.category ?? "").toLowerCase() !== "water") return { placed: false, reason: "terrain_invalid", terrain };
    const placementKey = `${context.setId ?? "default"}:${candidate.structure}:${Math.floor(location.x / 16)}:${Math.floor(location.z / 16)}`;
    if (this.completed.has(placementKey)) return { placed: false, reason: "already_completed" };
    const item = { candidate: { ...candidate, y: location.y }, context: { ...context, location, placementKey, terrain } };
    if (this.placementQueue) {
      this.placementQueue.enqueue(item.candidate, item.context);
      return { placed: false, queued: true, placementKey, terrain };
    }
    return this.placeCandidate(item.candidate, item.context);
  }

  async placeCandidate(candidate, context) {
    if (isJigsaw(candidate)) return this.placeJigsaw(candidate, context);
    return this.placementCoordinator.place(candidate, context);
  }

  async placeJigsaw(candidate, context) {
    const startId = candidate.structure ?? candidate.id;
    const expansion = this.jigsaw.expand(startId, context.location, candidate.seed ?? String(this.seed), { maxPieces: 96 });
    const accepted = [];
    for (const piece of expansion) {
      const pieceBounds = this.collision.canPlace(piece.piece, piece.location, piece.rotation, accepted);
      if (!pieceBounds.valid) continue;
      const terrain = this.terrainCheck(candidate, piece.location);
      if (!terrain.valid && piece.depth === 0) continue;
      accepted.push({ ...piece, bounds: pieceBounds.bounds });
    }
    if (!accepted.length) return { placed: false, reason: "jigsaw_no_valid_pieces", expansionCount: expansion.length };
    let placed = 0;
    for (const piece of accepted) {
      const result = await this.placementCoordinator.place({ ...candidate, structure: piece.id, x: piece.location.x, y: piece.location.y, z: piece.location.z }, { ...context, location: piece.location, options: { ...(context.options ?? {}), jigsawDepth: piece.depth, jigsawParent: piece.parent, rotation: piece.rotation } });
      if (result?.placed) placed++;
    }
    return { placed: placed > 0, placedPieces: placed, expandedPieces: expansion.length, acceptedPieces: accepted.length };
  }

  async process() {
    if (!this.generator || !this.pending.length) return { planned: 0, placed: 0, queued: 0, skipped: 0 };
    let planned = 0, placed = 0, queued = 0, skipped = 0;
    for (let i = 0; i < this.maxPlansPerTick && this.pending.length; i++) {
      const job = this.pending.shift();
      this.pendingKeys.delete(job.key);
      if (this.completed.has(job.key)) continue;
      const seed = `${this.seed}:${job.setId}:${Math.floor(job.x / 16)}:${Math.floor(job.z / 16)}`;
      const plan = this.adapter.plan(job, seed);
      if (!plan?.placements?.length) { this.completed.add(job.key); continue; }
      planned++;
      for (const candidate of plan.placements.slice(0, this.maxPlacementsPerTick)) {
        try {
          const result = await this.enqueueCandidate(candidate, { setId: job.setId, seed, dimension: world.getDimension(this.dimensionId) });
          if (result?.queued) queued++;
          else if (result?.placed) placed++;
          else skipped++;
        } catch (error) {
          skipped++;
          this.failed.set(`${job.key}:${candidate.structure}`, { reason: "exception", error: String(error), timestamp: Date.now() });
        }
      }
      this.completed.add(job.key);
      this.active.set(job.key, { planned: true, timestamp: Date.now() });
    }
    this.save();
    return { planned, placed, queued, skipped };
  }

  tick() {
    this.refresh();
    for (const player of world.getAllPlayers()) if (player.dimension?.id === this.dimensionId) this.enqueueAround(player.location.x, player.location.z);
  }
}
