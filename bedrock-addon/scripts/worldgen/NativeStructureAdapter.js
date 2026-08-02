import { world } from "@minecraft/server";
import { JigsawGenerator } from "./JigsawGenerator.js";
import { getGeneratedJigsawData, generatedPiece, generatedResolvedTemplatePath } from "./JigsawDataLoader.js";

function normalizeStructureId(value) {
  return String(value ?? "").replace(/\\/g, "/").replace(/\\.mcstructure$/i, "").replace(/^.*?:/, "").replace(/^\/+|\/+$/g, "").toLowerCase();
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
    const candidates = [
      generatedResolvedTemplatePath(id),
      piece?.source,
      piece?.id,
      direct
    ].filter(Boolean).map(normalizeStructureId);

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

    this.manager.place(structure, this.dimension, location, {
      includeBlocks: true,
      includeEntities: true,
      ...options
    });

    if (!this.overlapGuard.reserve(reservationId, bounds, padding, { type: "template", structureId: resolvedId, generatedId: id })) {
      return { placed: false, reason: "reservation_failed", bounds };
    }
    return { placed: true, id: resolvedId, generatedId: id, location, bounds, transformedSize, native: false, reservationId };
  }

  /** Use Bedrock's native jigsaw assembler when the requested jigsaw structure exists in the active pack. */
  placeJigsawStructure(id, location, options = {}, reservationId = `jigsaw:${id}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    if (typeof this.manager.placeJigsawStructure !== "function") throw new Error("StructureManager.placeJigsawStructure is unavailable");
    try {
      const resolvedId = this.resolvePackStructureId(id);
      const hasNativeStructure = Boolean(this.manager.get?.(resolvedId));
      if (!hasNativeStructure) {
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
   * Generated Java/Bedrock metadata fallback. This is intentionally a
   * piece-by-piece planner: processors and terrain matching are not silently
   * faked. When the active Bedrock pack exposes a native jigsaw structure,
   * placeJigsawStructure remains authoritative instead.
   */
  placeGeneratedJigsaw(id, location, options = {}, reservationId, padding = 2) {
    const definition = this.jigsaw.definition(id);
    if (!definition) return { placed: false, reason: "missing_generated_jigsaw_definition", id };

    const plan = this.generator?.registry ? this.generator : this.jigsaw;
    const planned = this.generator?.registry
      ? this.generator.registry.validatePoolGraph(definition.start_pool ?? definition.startPool, options.maxDepth ?? definition.size ?? 7)
      : null;

    return {
      placed: false,
      native: false,
      generated: true,
      planned: true,
      identifier: id,
      location,
      reservationId,
      padding,
      validation: planned,
      reason: "generated_metadata_requires_custom_piece_placement"
    };
  }

  placeJigsaw(pool, target, maxDepth, location, options = {}, reservationId = `jigsaw:${pool}:${target}:${location.x}:${location.y}:${location.z}`, padding = 2) {
    if (typeof this.manager.placeJigsaw !== "function") throw new Error("StructureManager.placeJigsaw is unavailable");
    try {
      const poolId = pool.includes(":") ? pool : `minecraft:${pool}`;
      const bounds = this.jigsaw.placePool(poolId, target, maxDepth, location, {
        includeEntities: true,
        keepJigsaws: false,
        ...options
      }).bounds;
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
      max: {
        x: Math.floor(location.x + Math.max(0, size.x - 1)),
        y: Math.floor(location.y + Math.max(0, size.y - 1)),
        z: Math.floor(location.z + Math.max(0, size.z - 1))
      }
    };
  }
}
