import { world } from "@minecraft/server";

export class NativeStructureAdapter {
  constructor(dimension, generator, overlapGuard) { this.dimension = dimension; this.generator = generator; this.overlapGuard = overlapGuard; this.manager = world.structureManager; }

  placeTemplate(id, location, options = {}, reservationId = `template:${id}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    const structure = this.manager.get(id); if (!structure) throw new Error(`[Sky Archipelago] Structure not found: ${id}`);
    const transformedSize = this.transformedSize(structure.size, options.rotation);
    const bounds = this.boundsFromSize(location, transformedSize);
    if (!this.overlapGuard.canReserve(reservationId, bounds, padding)) return { placed: false, reason: "overlap", bounds };
    this.manager.place(structure, this.dimension, location, { includeBlocks: true, includeEntities: true, ...options });
    if (!this.overlapGuard.reserve(reservationId, bounds, padding, { type: "template", structureId: id })) return { placed: false, reason: "reservation_failed", bounds };
    return { placed: true, id, location, bounds, transformedSize, native: false, reservationId };
  }

  placeJigsawStructure(id, location, options = {}, reservationId = `jigsaw:${id}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    if (typeof this.manager.placeJigsawStructure !== "function") throw new Error("StructureManager.placeJigsawStructure is unavailable");
    const estimated = this.estimateJigsawBounds(location, options);
    if (estimated && !this.overlapGuard.canReserve(reservationId, estimated, padding)) return { placed: false, reason: "overlap", bounds: estimated };
    if (estimated) this.overlapGuard.reserve(reservationId, estimated, 0, { type: "jigsaw_pending", structureId: id });
    try {
      const bounds = this.manager.placeJigsawStructure(id, this.dimension, location, { includeEntities: true, keepJigsaws: false, ...options });
      if (!bounds) { this.overlapGuard.release(reservationId); return { placed: false, reason: "no_bounds_returned" }; }
      this.overlapGuard.replace(reservationId, bounds, padding, { type: "jigsaw", structureId: id });
      return { placed: true, id, bounds, native: true, reservationId };
    } catch (e) { this.overlapGuard.release(reservationId); throw e; }
  }

  placeJigsaw(pool, target, maxDepth, location, options = {}, reservationId = `jigsaw:${pool}:${target}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    if (typeof this.manager.placeJigsaw !== "function") throw new Error("StructureManager.placeJigsaw is unavailable");
    const estimated = this.estimateJigsawBounds(location, { ...options, maxDepth });
    if (estimated && !this.overlapGuard.canReserve(reservationId, estimated, padding)) return { placed: false, reason: "overlap", bounds: estimated };
    if (estimated) this.overlapGuard.reserve(reservationId, estimated, 0, { type: "jigsaw_pending", pool, target });
    try {
      const bounds = this.manager.placeJigsaw(pool, target, Math.max(1, Math.min(20, maxDepth)), this.dimension, location, { includeEntities: true, keepJigsaws: false, ...options });
      if (!bounds) { this.overlapGuard.release(reservationId); return { placed: false, reason: "no_bounds_returned" }; }
      this.overlapGuard.replace(reservationId, bounds, padding, { type: "jigsaw", pool, target });
      return { placed: true, pool, target, bounds, native: true, reservationId };
    } catch (e) { this.overlapGuard.release(reservationId); throw e; }
  }

  placeStronghold(plan) { return { placed: false, procedural: true, plan, reason: "requires_ported_procedural_generator" }; }
  placeMineshaft(plan) { return { placed: false, procedural: true, plan, reason: "requires_ported_procedural_generator" }; }

  estimateJigsawBounds(location, options) {
    const depth = Math.max(1, Number(options?.maxDepth ?? 1));
    const horizontal = Math.min(256, 16 * (2 ** Math.min(depth, 4)));
    const vertical = Math.min(128, 16 * (1 + Math.min(depth, 7)));
    return { min: { x: Math.floor(location.x - horizontal / 2), y: Math.floor(location.y - vertical / 2), z: Math.floor(location.z - horizontal / 2) }, max: { x: Math.floor(location.x + horizontal / 2), y: Math.floor(location.y + vertical / 2), z: Math.floor(location.z + horizontal / 2) } };
  }

  transformedSize(size, rotation = "None") {
    const x = Number(size?.x ?? 0), y = Number(size?.y ?? 0), z = Number(size?.z ?? 0);
    return rotation === "90_degrees" || rotation === "270_degrees" ? { x: z, y, z: x } : { x, y, z };
  }

  boundsFromSize(location, size) {
    return { min: { x: Math.floor(location.x), y: Math.floor(location.y), z: Math.floor(location.z) }, max: { x: Math.floor(location.x + Math.max(0, size.x - 1)), y: Math.floor(location.y + Math.max(0, size.y - 1)), z: Math.floor(location.z + Math.max(0, size.z - 1)) } };
  }
}
