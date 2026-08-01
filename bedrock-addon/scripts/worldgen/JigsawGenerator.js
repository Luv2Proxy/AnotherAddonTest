import { world } from "@minecraft/server";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { boxesOverlap } from "./JigsawTransform.js";

/**
 * Connector-aware jigsaw entry point.
 *
 * The Bedrock Script API now exposes the same native jigsaw assembler used by
 * vanilla structures. We deliberately use it for final placement: this keeps
 * vanilla pool fallback, processors, projections, terrain matching, aliases,
 * list/feature/empty elements, selection/placement priorities and jigsaw
 * cleanup in the engine instead of reimplementing those semantics incorrectly.
 *
 * The extracted registry is still used for validation, diagnostics, previews,
 * collision reservations, and for projects that need a custom planner.
 */
export class JigsawGenerator {
  constructor(dimension, options = {}) {
    this.dimension = dimension;
    this.manager = world.structureManager;
    this.registry = options.registry ?? new JigsawRegistry(options.data ?? null);
    this.overlap = options.overlapGuard ?? null;
  }

  placeStructure(identifier, location, options = {}) {
    if (typeof this.manager.placeJigsawStructure !== "function") {
      throw new Error("StructureManager.placeJigsawStructure is unavailable in this Bedrock version");
    }
    const bounds = this.manager.placeJigsawStructure(identifier, this.dimension, location, {
      includeEntities: true,
      keepJigsaws: false,
      ...options
    });
    return { placed: true, native: true, identifier, location, bounds };
  }

  placePool(pool, target, maxDepth, location, options = {}) {
    if (typeof this.manager.placeJigsaw !== "function") {
      throw new Error("StructureManager.placeJigsaw is unavailable in this Bedrock version");
    }
    const bounds = this.manager.placeJigsaw(pool, target, Math.max(1, Math.min(20, maxDepth)), this.dimension, location, {
      includeEntities: true,
      keepJigsaws: false,
      ...options
    });
    return { placed: true, native: true, pool, target, maxDepth, location, bounds };
  }

  placeRoot(identifier, location, options = {}) {
    return this.placeStructure(identifier, location, options);
  }

  placeByPool(pool, target = "", maxDepth = 5, location = { x: 0, y: 0, z: 0 }, options = {}) {
    return this.placePool(pool, target, maxDepth, location, options);
  }

  validatePieceGraph(identifier, maxDepth = 20) {
    const root = this.registry.piece(identifier);
    if (!root) return { valid: false, errors: [`Missing piece: ${identifier}`], nodes: [] };
    const errors = [], nodes = [], seen = new Set();
    const visit = (piece, depth) => {
      if (!piece || depth > maxDepth) return;
      if (seen.has(`${piece.id}:${depth}`)) return;
      seen.add(`${piece.id}:${depth}`); nodes.push(piece.id);
      for (const c of piece.jigsaws ?? []) {
        if (!c.pool || c.pool === "unknown") { errors.push(`${piece.id}: connector has no target pool`); continue; }
        const pool = this.registry.pool(c.pool);
        if (!pool) { errors.push(`${piece.id}: missing pool ${c.pool}`); continue; }
        for (const e of pool.elements ?? []) {
          const el = e.element ?? {};
          if (el.element_type === "minecraft:empty_pool_element") continue;
          if (el.location) {
            const child = this.registry.piece(el.location.includes(":") ? el.location : `unknown:${el.location}`);
            if (child) visit(child, depth + 1);
          }
        }
        if (pool.fallback && !this.registry.pool(pool.fallback)) errors.push(`${piece.id}: missing fallback pool ${pool.fallback}`);
      }
    };
    visit(root, 0);
    return { valid: errors.length === 0, errors, nodes };
  }

  canReserve(bounds, padding = 0) {
    if (!this.overlap) return true;
    return this.overlap.canReserve?.(`jigsaw:${Date.now()}`, bounds, padding) ?? true;
  }
}
