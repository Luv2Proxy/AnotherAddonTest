import { world } from "@minecraft/server";

/**
 * Real Bedrock StructureManager integration.
 *
 * Saved structures use StructureManager.place(). Jigsaw structures use
 * placeJigsawStructure()/placeJigsaw(), whose returned bounding boxes are
 * immediately committed to the shared overlap guard. Strongholds and
 * mineshafts remain procedural plans because StructureManager.place() accepts
 * saved Structure templates, not vanilla StructureStart/Piece graphs.
 */
export class NativeStructureAdapter {
  constructor(dimension, generator, overlapGuard) {
    this.dimension = dimension;
    this.generator = generator;
    this.overlapGuard = overlapGuard;
    this.manager = world.structureManager;
  }

  placeTemplate(id, location, options = {}, reservationId = `template:${id}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    const structure = this.manager.get(id);
    if (!structure) throw new Error(`[Sky Archipelago] Structure not found: ${id}`);
    const bounds = this.boundsFromSize(location, structure.size);
    if (!this.overlapGuard.canReserve(reservationId, bounds, padding)) return { placed: false, reason: "overlap", bounds };
    this.manager.place(structure, this.dimension, location, {
      includeBlocks: true,
      includeEntities: true,
      ...options
    });
    this.overlapGuard.reserve(reservationId, bounds, padding);
    return { placed: true, id, location, bounds, native: false };
  }

  placeJigsawStructure(id, location, options = {}, reservationId = `jigsaw:${id}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    if (typeof this.manager.placeJigsawStructure !== "function") throw new Error("StructureManager.placeJigsawStructure is unavailable");
    const bounds = this.manager.placeJigsawStructure(id, this.dimension, location, {
      includeEntities: true,
      keepJigsaws: false,
      ...options
    });
    if (!bounds) return { placed: false, reason: "no_bounds_returned" };
    if (!this.overlapGuard.canReserve(reservationId, bounds, padding)) {
      // The API has already placed the structure, so this is a post-placement
      // collision report rather than a rollback. Future planning should reserve
      // the footprint before placement when exact bounds are known.
      return { placed: true, collision: true, id, bounds, native: true };
    }
    this.overlapGuard.reserve(reservationId, bounds, padding);
    return { placed: true, id, bounds, native: true };
  }

  placeJigsaw(pool, target, maxDepth, location, options = {}, reservationId = `jigsaw:${pool}:${target}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    if (typeof this.manager.placeJigsaw !== "function") throw new Error("StructureManager.placeJigsaw is unavailable");
    const depth = Math.max(1, Math.min(20, maxDepth));
    const bounds = this.manager.placeJigsaw(pool, target, depth, this.dimension, location, {
      includeEntities: true,
      keepJigsaws: false,
      ...options
    });
    if (!bounds) return { placed: false, reason: "no_bounds_returned" };
    if (!this.overlapGuard.canReserve(reservationId, bounds, padding)) return { placed: true, collision: true, pool, target, bounds, native: true };
    this.overlapGuard.reserve(reservationId, bounds, padding);
    return { placed: true, pool, target, bounds, native: true };
  }

  placeStronghold(plan) {
    return { placed: false, procedural: true, plan, reason: "requires_native_structure_start" };
  }

  placeMineshaft(plan) {
    return { placed: false, procedural: true, plan, reason: "requires_native_structure_start" };
  }

  boundsFromSize(location, size) {
    const sx = Number(size?.x ?? 0), sy = Number(size?.y ?? 0), sz = Number(size?.z ?? 0);
    return {
      min: { x: Math.floor(location.x), y: Math.floor(location.y), z: Math.floor(location.z) },
      max: { x: Math.floor(location.x + Math.max(0, sx - 1)), y: Math.floor(location.y + Math.max(0, sy - 1)), z: Math.floor(location.z + Math.max(0, sz - 1)) }
    };
  }
}
