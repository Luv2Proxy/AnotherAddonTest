import { world } from "@minecraft/server";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { getGeneratedJigsawData, generatedStructure } from "./JigsawDataLoader.js";

/**
 * Common jigsaw placement facade.
 *
 * Generated vanilla metadata is used for validation, graph inspection and
 * custom planning. Final assembly remains delegated to Bedrock's native
 * StructureManager whenever its jigsaw APIs are available. This preserves
 * the engine's weighted selection, fallbacks, processors, projections,
 * terrain matching and cleanup semantics.
 */
export class JigsawGenerator {
  constructor(dimension, options = {}) {
    this.dimension = dimension;
    this.manager = world.structureManager;
    this.registry = options.registry ?? new JigsawRegistry(options.data ?? getGeneratedJigsawData());
    this.overlap = options.overlapGuard ?? null;
    this.layoutSeed = options.layoutSeed ?? 0n;
  }

  dataSnapshot() {
    return this.registry.snapshot();
  }

  definition(identifier) {
    return generatedStructure(identifier) ?? this.registry.structure(identifier);
  }

  resolveStructureIdentifier(identifier) {
    const value = String(identifier ?? "");
    if (!value) return null;
    if (this.registry.structure(value)) return value;
    const definition = this.definition(value);
    if (definition) return value;
    return value.includes(":") ? value : `minecraft:${value}`;
  }

  placeStructure(identifier, location, options = {}) {
    if (typeof this.manager.placeJigsawStructure !== "function") {
      throw new Error("StructureManager.placeJigsawStructure is unavailable in this Bedrock version");
    }
    const resolved = this.resolveStructureIdentifier(identifier);
    const bounds = this.manager.placeJigsawStructure(resolved, this.dimension, location, {
      includeEntities: true,
      keepJigsaws: false,
      ...options
    });
    return { placed: true, native: true, identifier: resolved, location, bounds };
  }

  placePool(pool, target, maxDepth, location, options = {}) {
    if (typeof this.manager.placeJigsaw !== "function") {
      throw new Error("StructureManager.placeJigsaw is unavailable in this Bedrock version");
    }
    const poolId = pool.includes(":") ? pool : `minecraft:${pool}`;
    const depth = Math.max(1, Math.min(20, Number(maxDepth) || 1));
    const bounds = this.manager.placeJigsaw(poolId, target ?? "", depth, this.dimension, location, {
      includeEntities: true,
      keepJigsaws: false,
      ...options
    });
    return { placed: true, native: true, pool: poolId, target, maxDepth: depth, location, bounds };
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

    const errors = [];
    const nodes = [];
    const seen = new Set();

    const visitPiece = (piece, depth) => {
      if (!piece || depth > maxDepth) return;
      const pieceKey = piece.id ?? piece.source;
      if (seen.has(`${pieceKey}:${depth}`)) return;
      seen.add(`${pieceKey}:${depth}`);
      nodes.push(pieceKey);

      for (const connector of piece.connectors ?? piece.jigsaws ?? []) {
        const poolId = connector.pool ?? connector.target_pool ?? connector.targetPool;
        if (!poolId || poolId === "unknown") {
          errors.push(`${pieceKey}: connector has no target pool`);
          continue;
        }
        const graph = this.registry.validatePoolGraph(poolId, maxDepth - depth);
        errors.push(...graph.errors.map(error => `${pieceKey}: ${error}`));
        for (const candidate of this.registry.candidates(poolId, connector.name)) {
          if (candidate.piece) visitPiece(candidate.piece, depth + 1);
        }
        const fallback = this.registry.fallback(poolId);
        if (fallback && !this.registry.pool(fallback)) errors.push(`${pieceKey}: missing fallback pool ${fallback}`);
      }
    };

    visitPiece(root, 0);
    return { valid: errors.length === 0, errors, nodes };
  }

  validateStructure(identifier, maxDepth = 20) {
    const definition = this.definition(identifier);
    if (!definition) return { valid: false, errors: [`Missing jigsaw structure: ${identifier}`], nodes: [] };
    const startPool = definition.start_pool ?? definition.startPool;
    if (!startPool) return { valid: false, errors: [`${identifier}: missing start_pool`], nodes: [] };
    const graph = this.registry.validatePoolGraph(startPool, maxDepth);
    return { ...graph, identifier, startPool };
  }

  canReserve(bounds, padding = 0) {
    if (!this.overlap) return true;
    return this.overlap.canReserve?.(`jigsaw:${Date.now()}`, bounds, padding) ?? true;
  }
}
