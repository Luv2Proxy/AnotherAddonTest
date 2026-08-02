import { world } from "@minecraft/server";
import { JigsawGenerator } from "./JigsawGenerator.js";
import { getGeneratedJigsawData, generatedPiece, generatedResolvedTemplatePath } from "./JigsawDataLoader.js";

function normalizeStructureId(value) {
  return String(value ?? "").replace(/\\/g, "/").replace(/\.mcstructure$/i, "").replace(/\.nbt$/i, "").replace(/^.*?:/, "").replace(/^\/+|\/+$/g, "").toLowerCase();
}

function rotationName(q) {
  return ["None", "90_degrees", "180_degrees", "270_degrees"][((Number(q) % 4) + 4) % 4];
}

function unionBounds(a, b) {
  if (!a) return b;
  if (!b) return a;
  return {
    min: { x: Math.min(a.min.x, b.min.x), y: Math.min(a.min.y, b.min.y), z: Math.min(a.min.z, b.min.z) },
    max: { x: Math.max(a.max.x, b.max.x), y: Math.max(a.max.y, b.max.y), z: Math.max(a.max.z, b.max.z) }
  };
}

export class NativeStructureAdapter {
  constructor(dimension, generator, overlapGuard) {
    this.dimension = dimension;
    this.generator = generator;
    this.overlapGuard = overlapGuard;
    this.manager = world.structureManager;
    this.jigsaw = new JigsawGenerator(dimension, { overlapGuard, registry: generator?.registry });
    this.generatedData = getGeneratedJigsawData();
  }

  packStructureIds() {
    try { return this.manager.getPackStructureIds?.() ?? []; } catch { return []; }
  }

  resolvePackStructureId(id) {
    if (!id) return null;
    const direct = String(id);
    if (this.manager.get?.(direct)) return direct;

    const piece = generatedPiece(id);
    const candidates = [generatedResolvedTemplatePath(id), piece?.source, piece?.id, direct]
      .filter(Boolean).map(normalizeStructureId);
    const ids = this.packStructureIds();

    for (const packId of ids) {
      const normalized = normalizeStructureId(packId);
      if (candidates.includes(normalized)) return packId;
    }
    for (const candidate of candidates) {
      const suffixMatches = ids.filter(packId => {
        const normalized = normalizeStructureId(packId);
        return normalized === candidate || normalized.endsWith(`/${candidate}`) || candidate.endsWith(`/${normalized}`);
      });
      if (suffixMatches.length === 1) return suffixMatches[0];
    }
    return direct;
  }

  placeTemplate(id, location, options = {}, reservationId = `template:${id}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    const resolvedId = this.resolvePackStructureId(id);
    const structure = this.manager.get(resolvedId);
    if (!structure) throw new Error(`[Sky Archipelago] Structure not found: ${id} (resolved: ${resolvedId})`);

    const transformedSize = this.transformedSize(structure.size, options.rotation);
    const bounds = this.boundsFromSize(location, transformedSize);
    if (!this.overlapGuard.canReserve(reservationId, bounds, padding)) return { placed: false, reason: "overlap", bounds };

    this.manager.place(structure, this.dimension, location, { includeBlocks: true, includeEntities: true, ...options });
    if (!this.overlapGuard.reserve(reservationId, bounds, padding, { type: "template", structureId: resolvedId, generatedId: id })) {
      return { placed: false, reason: "reservation_failed", bounds };
    }
    return { placed: true, id: resolvedId, generatedId: id, location, bounds, transformedSize, native: false, reservationId };
  }

  placeJigsawStructure(id, location, options = {}, reservationId = `jigsaw:${id}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    try {
      const resolvedId = this.resolvePackStructureId(id);
      const hasNativeStructure = Boolean(this.manager.get?.(resolvedId));
      if (!hasNativeStructure) return this.placeGeneratedJigsaw(id, location, options, reservationId, padding);

      if (typeof this.manager.placeJigsawStructure !== "function") {
        return this.placeGeneratedJigsaw(id, location, options, reservationId, padding);
      }

      const bounds = this.jigsaw.placeStructure(resolvedId, location, {
        includeEntities: true,
        keepJigsaws: false,
        ...options
      }).bounds;
      if (!bounds) return { placed: false, reason: "no_bounds_returned" };
      this.overlapGuard.replace(reservationId, bounds, padding, { type: "jigsaw", structureId: resolvedId });
      return { placed: true, id: resolvedId, generatedId: id, bounds, native: true, reservationId };
    } catch (e) {
      this.overlapGuard.release(reservationId);
      throw e;
    }
  }

  /**
   * Fully materialize the generated Jigsaw plan using ordinary .mcstructure
   * templates. This is the important fallback for imported Java/Bedrock data:
   * metadata drives assembly, while StructureManager.place() materializes each
   * resolved template. Processor lists remain an explicit extension point.
   */
  placeGeneratedJigsaw(id, location, options = {}, reservationId, padding = 2) {
    const definition = this.jigsaw.definition(id);
    if (!definition) return { placed: false, reason: "missing_generated_jigsaw_definition", id };
    const planner = this.generator?.planner ?? this.jigsaw.planner;
    if (!planner) return { placed: false, reason: "missing_layout_planner", id };

    const plan = planner.planStructure(id, location, options.seed ?? this.generator?.layoutSeed ?? 0, {
      maxDepth: options.maxDepth ?? definition.size ?? 7,
      padding,
      allowOverlap: false
    });
    if (!plan.ok || !plan.pieces?.length) {
      return { placed: false, native: false, generated: true, reason: "generated_plan_failed", plan };
    }

    let allBounds = null;
    const placed = [];
    const localReservations = [];
    try {
      for (const piece of plan.pieces) {
        const pieceId = piece.id;
        const pieceReservation = `${reservationId}:piece:${placed.length}`;
        const resolvedId = this.resolvePackStructureId(pieceId);
        const structure = this.manager.get?.(resolvedId);
        if (!structure) throw new Error(`Missing materialized structure template: ${pieceId} (resolved ${resolvedId})`);

        const bounds = this.boundsFromSize(piece.origin, this.transformedSize(structure.size, rotationName(piece.rotation)));
        if (!this.overlapGuard.canReserve(pieceReservation, bounds, padding)) {
          return { placed: false, native: false, generated: true, reason: "overlap", plan, placed };
        }

        this.manager.place(structure, this.dimension, piece.origin, {
          includeBlocks: options.includeEntities !== false,
          includeEntities: options.includeEntities ?? true,
          rotation: rotationName(piece.rotation),
          ...options
        });
        if (!this.overlapGuard.reserve(pieceReservation, bounds, padding, { type: "generated_jigsaw_piece", parent: id, structureId: resolvedId })) {
          return { placed: false, native: false, generated: true, reason: "reservation_failed", plan, placed };
        }
        localReservations.push(pieceReservation);
        placed.push({ id: pieceId, resolvedId, location: piece.origin, rotation: piece.rotation, bounds });
        allBounds = unionBounds(allBounds, bounds);
      }

      this.overlapGuard.replace(reservationId, allBounds, padding, { type: "generated_jigsaw", structureId: id, pieceCount: placed.length });
      return { placed: true, native: false, generated: true, identifier: id, bounds: allBounds, pieces: placed, plan, reservationId };
    } catch (e) {
      for (const key of localReservations) this.overlapGuard.release(key);
      this.overlapGuard.release(reservationId);
      return { placed: false, native: false, generated: true, reason: "generated_piece_placement_failed", error: String(e), plan, placed };
    }
  }

  placeJigsaw(pool, target, maxDepth, location, options = {}, reservationId = `jigsaw:${pool}:${target}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    if (typeof this.manager.placeJigsaw !== "function") throw new Error("StructureManager.placeJigsaw is unavailable");
    try {
      const poolId = pool.includes(":") ? pool : `minecraft:${pool}`;
      const bounds = this.jigsaw.placePool(poolId, target, maxDepth, location, { includeEntities: true, keepJigsaws: false, ...options }).bounds;
      if (!bounds) return { placed: false, reason: "no_bounds_returned" };
      this.overlapGuard.replace(reservationId, bounds, padding, { type: "jigsaw", pool: poolId, target });
      return { placed: true, pool: poolId, target, bounds, native: true, reservationId };
    } catch (e) {
      this.overlapGuard.release(reservationId);
      throw e;
    }
  }

  placeStronghold(plan) { return { placed: false, procedural: true, plan, reason: "requires_ported_procedural_generator" }; }
  placeMineshaft(plan) { return { placed: false, procedural: true, plan, reason: "requires_ported_procedural_generator" }; }

  transformedSize(size, rotation = "None") {
    const x = Number(size?.x ?? 0), y = Number(size?.y ?? 0), z = Number(size?.z ?? 0);
    return rotation === "90_degrees" || rotation === "270_degrees" ? { x: z, y, z: x } : { x, y, z };
  }

  boundsFromSize(location, size) {
    return {
      min: { x: Math.floor(location.x), y: Math.floor(location.y), z: Math.floor(location.z) },
      max: { x: Math.floor(location.x + Math.max(0, size.x - 1)), y: Math.floor(location.y + Math.max(0, size.y - 1)), z: Math.floor(location.z + Math.max(0, size.z - 1)) }
    };
  }
}
