import { world } from "@minecraft/server";

/** Real Bedrock StructureManager integration for saved and Jigsaw structures. */
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
    const transformedSize = this.transformedSize(structure.size, options.rotation);
    const bounds = this.boundsFromSize(location, transformedSize);
    if (!this.overlapGuard.canReserve(reservationId, bounds, padding)) return { placed: false, reason: "overlap", bounds };
    this.manager.place(structure, this.dimension, location, {
      includeBlocks: true,
      includeEntities: true,
      ...options
    });
    if (!this.overlapGuard.reserve(reservationId, bounds, padding)) return { placed: false, reason: "reservation_failed", bounds };
    return { placed: true, id, location, bounds, transformedSize, native: false };
  }

  placeJigsawStructure(id, location, options = {}, reservationId = `jigsaw:${id}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    if (typeof this.manager.placeJigsawStructure !== "function") throw new Error("StructureManager.placeJigsawStructure is unavailable");
    const bounds = this.manager.placeJigsawStructure(id, this.dimension, location, {
      includeEntities: true,
      keepJigsaws: false,
      ...options
    });
    if (!bounds) return { placed: false, reason: "no_bounds_returned" };
    // The API returns exact bounds only after placement. Reserve them immediately
    // so subsequent structures cannot claim the same space.
    const reserved = this.overlapGuard.reserve(reservationId, bounds, padding);
    return { placed: true, collision: !reserved, id, bounds, native: true };
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
    const reserved = this.overlapGuard.reserve(reservationId, bounds, padding);
    return { placed: true, collision: !reserved, pool, target, bounds, native: true };
  }

  placeStronghold(plan) {
    return { placed: false, procedural: true, plan, reason: "requires_ported_procedural_generator" };
  }

  placeMineshaft(plan) {
    return { placed: false, procedural: true, plan, reason: "requires_ported_procedural_generator" };
  }

  transformedSize(size, rotation = "None") {
    const x = Number(size?.x ?? 0), y = Number(size?.y ?? 0), z = Number(size?.z ?? 0);
    return rotation === "90_degrees" || rotation === "270_degrees" ? { x: z, y, z: x } : { x, y, z };
  }

  boundsFromSize(location, size) {
    const sx = Math.max(0, Number(size?.x ?? 0) - 1), sy = Math.max(0, Number(size?.y ?? 0) - 1), sz = Math.max(0, Number(size?.z ?? 0) - 1);
    return {
      min: { x: Math.floor(location.x), y: Math.floor(location.y), z: Math.floor(location.z) },
      max: { x: Math.floor(location.x + sx), y: Math.floor(location.y + sy), z: Math.floor(location.z + sz) }
    };
  }
}
