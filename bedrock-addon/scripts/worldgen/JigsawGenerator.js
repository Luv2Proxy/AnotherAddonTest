import { world } from "@minecraft/server";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { JigsawLayoutPlanner } from "./JigsawLayoutPlanner.js";
import { StructureSetGenerator } from "./StructureSetGenerator.js";
import { getGeneratedJigsawData, generatedStructure } from "./JigsawDataLoader.js";

/**
 * Runtime facade for generated vanilla Jigsaw metadata.
 *
 * Runtime input is intentionally limited to generated jigsaw-data.js and
 * packaged .mcstructure assets. The native StructureManager is used for
 * final placement when the current Bedrock API exposes the relevant Jigsaw
 * methods; otherwise the deterministic layout planner can produce a complete
 * piece graph for a custom placement backend.
 */
export class JigsawGenerator {
  constructor(dimension, options = {}) {
    this.dimension = dimension;
    this.manager = world.structureManager;
    this.registry = options.registry ?? new JigsawRegistry(options.data ?? getGeneratedJigsawData());
    this.planner = options.planner ?? new JigsawLayoutPlanner(this.registry, options);
    this.structureSets = options.structureSets ?? new StructureSetGenerator(this.registry);
    this.overlap = options.overlapGuard ?? null;
    this.layoutSeed = options.layoutSeed ?? 0;
  }

  dataSnapshot() { return this.registry.snapshot(); }

  definition(identifier) { return generatedStructure(identifier) ?? this.registry.structure(identifier); }

  resolveStructureIdentifier(identifier) {
    const value = String(identifier ?? "");
    if (!value) return null;
    if (this.registry.structure(value) || this.definition(value)) return value;
    return value.includes(":") ? value : `minecraft:${value}`;
  }

  /** Deterministically plan a Jigsaw graph without touching the world. */
  plan(identifier, location = { x: 0, y: 0, z: 0 }, seed = this.layoutSeed, options = {}) {
    return this.planner.planStructure(this.resolveStructureIdentifier(identifier), location, seed, options);
  }

  /** Deterministically plan a structure-set placement. */
  planStructureSet(setId, seed = this.layoutSeed, options = {}) {
    return this.structureSets.plan(setId, seed, options);
  }

  /**
   * Place a complete Jigsaw structure using the native Bedrock API.
   * Bedrock's native implementation handles the actual recursive assembly,
   * processor lists and terrain matching when available.
   */
  placeStructure(identifier, location, options = {}) {
    const resolved = this.resolveStructureIdentifier(identifier);
    if (typeof this.manager?.placeJigsawStructure !== "function") {
      return { placed: false, native: false, reason: "native_jigsaw_structure_api_unavailable", plan: this.plan(resolved, location, options.seed ?? this.layoutSeed, options) };
    }
    const bounds = this.manager.placeJigsawStructure(resolved, this.dimension, location, {
      includeEntities: true,
      keepJigsaws: false,
      ...options
    });
    return { placed: true, native: true, identifier: resolved, location, bounds };
  }

  placePool(pool, target, maxDepth, location, options = {}) {
    const poolId = String(pool).includes(":") ? pool : `minecraft:${pool}`;
    const depth = Math.max(1, Math.min(20, Number(maxDepth) || 1));
    if (typeof this.manager?.placeJigsaw !== "function") {
      return { placed: false, native: false, reason: "native_jigsaw_pool_api_unavailable", plan: this.planner.planPool(poolId, location, options.seed ?? this.layoutSeed, { ...options, maxDepth: depth }) };
    }
    const bounds = this.manager.placeJigsaw(poolId, target ?? "", depth, this.dimension, location, {
      includeEntities: true,
      keepJigsaws: false,
      ...options
    });
    return { placed: true, native: true, pool: poolId, target, maxDepth: depth, location, bounds };
  }

  placeRoot(identifier, location, options = {}) { return this.placeStructure(identifier, location, options); }

  placeByPool(pool, target = "", maxDepth = 5, location = { x: 0, y: 0, z: 0 }, options = {}) {
    return this.placePool(pool, target, maxDepth, location, options);
  }

  validatePieceGraph(identifier, maxDepth = 20) {
    const root = this.registry.piece(identifier);
    if (!root) return { valid: false, errors: [`Missing piece: ${identifier}`], nodes: [] };
    const errors = [], nodes = [], seen = new Set();
    const visitPiece = (piece, depth) => {
      if (!piece || depth > maxDepth) return;
      const pieceKey = piece.id ?? piece.source;
      const key = `${pieceKey}:${depth}`;
      if (seen.has(key)) return;
      seen.add(key); nodes.push(pieceKey);
      for (const connector of piece.connectors ?? piece.jigsaws ?? []) {
        const poolId = connector.pool ?? connector.target_pool ?? connector.targetPool;
        if (!poolId || poolId === "unknown") { errors.push(`${pieceKey}: connector has no target pool`); continue; }
        const graph = this.registry.validatePoolGraph(poolId, maxDepth - depth);
        errors.push(...graph.errors.map(error => `${pieceKey}: ${error}`));
        for (const candidate of this.registry.candidates(poolId, connector.name)) if (candidate.piece) visitPiece(candidate.piece, depth + 1);
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
    return { ...this.registry.validatePoolGraph(startPool, maxDepth), identifier, startPool };
  }

  canReserve(bounds, padding = 0, key = "jigsaw") {
    if (!this.overlap) return true;
    return this.overlap.canReserve?.(key, bounds, padding) ?? true;
  }
}
