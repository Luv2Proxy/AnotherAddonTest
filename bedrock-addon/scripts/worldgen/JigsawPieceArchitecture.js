import { StructurePieceModel, StructurePieceGraph } from "./StructurePieceModel.js";
import { JigsawRegistry } from "../JigsawRegistry.js";
import { getGeneratedJigsawData } from "../JigsawDataLoader.js";
import { oppositeFacing, transformPoint, enumerateConnectorTransforms, boxesOverlap } from "../JigsawTransform.js";
import { ProcessorPipeline } from "../ProcessorPipeline.js";

function boundsFor(position, size, rotation = 0) {
  const origin = { x: Math.floor(position?.x ?? 0), y: Math.floor(position?.y ?? 0), z: Math.floor(position?.z ?? 0) };
  const sx = Math.max(1, Number(size?.x ?? 1)), sy = Math.max(1, Number(size?.y ?? 1)), sz = Math.max(1, Number(size?.z ?? 1));
  return {
    min: { x: origin.x, y: origin.y, z: origin.z },
    max: { x: origin.x + (rotation % 2 ? sz : sx) - 1, y: origin.y + sy - 1, z: origin.z + (rotation % 2 ? sx : sz) - 1 }
  };
}
function centerOf(bounds) { return { x: Math.floor((bounds.min.x + bounds.max.x) / 2), y: Math.floor((bounds.min.y + bounds.max.y) / 2), z: Math.floor((bounds.min.z + bounds.max.z) / 2) }; }
function facing(connector) { return connector?.facing ?? connector?.facing_direction ?? "unknown"; }
function connectorPosition(connector) { return connector?.position ?? connector?.pos ?? { x: 0, y: 0, z: 0 }; }

export class JigsawAnchorResolver {
  resolve(start) {
    if (start?.valid === false) return { x: 0, baseY: 0, z: 0, source: "invalid_start_fallback" };
    const pieces = start?.pieces ?? [];
    const entry = pieces.reduce((best, piece) => !best || (piece.bounds?.min?.y ?? Infinity) < (best.bounds?.min?.y ?? Infinity) ? piece : best, null);
    if (entry?.bounds) return { ...centerOf(entry.bounds), baseY: entry.bounds.min.y, source: "entry_piece_center" };
    if (start?.bounds) return { ...centerOf(start.bounds), baseY: start.bounds.min.y, source: "bounds_center_fallback" };
    return { x: 0, baseY: 0, z: 0, source: "origin_fallback" };
  }
}
export class JigsawAnchorStrategy { constructor(resolver = new JigsawAnchorResolver()) { this.resolver = resolver; } resolve(start) { return this.resolver.resolve(start); } }

export class JigsawPieceFactory {
  constructor(registry = new JigsawRegistry(getGeneratedJigsawData()), processorPipeline = null) { this.registry = registry; this.processorPipeline = processorPipeline ?? new ProcessorPipeline(registry); }
  create({ id, role = "piece", position = { x: 0, y: 0, z: 0 }, rotation = 0, metadata = {} } = {}) {
    const piece = this.registry.piece(id) ?? this.registry.findPieceByLocation(id);
    const template = piece?.id ?? id, size = piece?.size ?? { x: 1, y: 1, z: 1 };
    return new StructurePieceModel({ id: template, role, template, position, rotation: ((rotation % 4) + 4) % 4, bounds: boundsFor(position, size, rotation), metadata: { source: piece?.source ?? null, size, connectors: piece?.connectors ?? [], ...metadata } });
  }
}

export class JigsawPieceGraphBuilder {
  constructor(seed = 0n, registry = new JigsawRegistry(getGeneratedJigsawData()), factory = null) { this.seed = BigInt(seed ?? 0); this.registry = registry; this.factory = factory ?? new JigsawPieceFactory(registry); }
  random(x, z, i) { let v = this.seed ^ BigInt(Math.floor(x)) * 0x9E3779B97F4A7C15n ^ BigInt(Math.floor(z)) * 0xC2B2AE3D27D4EB4Fn ^ BigInt(i); v ^= v >> 29n; v *= 0x94D049BB133111EBn; v ^= v >> 31n; return Number(v & 0xffffffffn) / 4294967296; }
  choose(pool, index, x, z, target = null) { return this.registry.weightedCandidates(pool, this.random(x, z, index), target); }

  build(anchor, { pool = null, startPool = null, target = "", maxDepth = 7, maxPieces = 256, allowOverlap = false } = {}) {
    const rootPool = startPool ?? pool, graph = new StructurePieceGraph("jigsaw", anchor);
    if (!rootPool) return graph;
    const limit = Math.max(1, Math.min(20, Number(maxDepth) || 1));
    const queue = [{ pool: rootPool, target, x: anchor.x, y: anchor.baseY, z: anchor.z, depth: 0, rotation: 0, parentConnector: null }];
    const occupied = [], seen = new Set(); let index = 0;
    while (queue.length && index < maxPieces) {
      const node = queue.shift(); if (node.depth > limit) continue;
      const candidate = this.choose(node.pool, index, node.x, node.z, node.target); if (!candidate) continue;
      const element = candidate.element ?? {};
      if (element.element_type === "minecraft:empty_pool_element") { index++; continue; }
      if (!String(element.element_type ?? "").includes("single_pool_element")) { index++; continue; }
      const piece = this.registry.piece(element.location) ?? this.registry.findPieceByLocation(element.location);
      if (!piece) { index++; continue; }
      const model = this.factory.create({ id: piece.id, position: { x: node.x, y: node.y, z: node.z }, rotation: node.rotation, role: node.depth === 0 ? "root" : node.depth >= limit ? "terminal" : "piece", metadata: { pool: node.pool, target: node.target, depth: node.depth, weight: candidate.weight, processors: element.processors ?? null, projection: element.projection ?? "rigid", parentConnector: node.parentConnector } });
      if (!allowOverlap && occupied.some(other => boxesOverlap(other.bounds, model.bounds, 0))) { index++; continue; }
      graph.add(model); occupied.push(model); seen.add(`${piece.id}:${node.x}:${node.y}:${node.z}:${node.rotation}`); index++;
      if (node.depth >= limit) continue;
      for (let connectorIndex = 0; connectorIndex < (piece.connectors ?? []).length; connectorIndex++) {
        const parent = piece.connectors[connectorIndex], childPool = parent.pool ?? parent.target_pool; if (!childPool || childPool === "unknown") continue;
        const next = this.choose(childPool, index + connectorIndex, node.x + connectorIndex, node.z - connectorIndex, parent.target ?? parent.target_name ?? null), childElement = next?.element; if (!childElement?.location) continue;
        const child = this.registry.piece(childElement.location) ?? this.registry.findPieceByLocation(childElement.location); if (!child) continue;
        const wantedFacing = oppositeFacing(facing(parent)), parentWorld = { position: { x: node.x + connectorPosition(parent).x, y: node.y + connectorPosition(parent).y, z: node.z + connectorPosition(parent).z }, facing: facing(parent) };
        const childConnectors = (child.connectors ?? []).filter(c => { const targetName = parent.target ?? parent.target_name; return !targetName || !c.name || targetName === c.name; });
        const transforms = childConnectors.flatMap(c => enumerateConnectorTransforms(parentWorld, { connector: c, size: child.size }, { })).filter(Boolean);
        const transform = transforms.find(t => t.rotation >= 0 && (wantedFacing === "unknown" || facing(childConnectors.find(c => c === c)) === wantedFacing)) ?? transforms[0];
        if (!transform) continue;
        const origin = transform.origin, childRotation = transform.rotation, key = `${child.id}:${origin.x}:${origin.y}:${origin.z}:${childRotation}`;
        if (seen.has(key)) continue;
        queue.push({ pool: childPool, target: parent.target ?? parent.target_name ?? "", x: origin.x, y: origin.y, z: origin.z, depth: node.depth + 1, rotation: childRotation, parentConnector: `${piece.id}:${connectorIndex}` });
      }
    }
    return graph;
  }
}

export class JigsawPlacementCoordinator {
  constructor(generator) { this.generator = generator; this.registry = generator.registry ?? new JigsawRegistry(getGeneratedJigsawData()); this.anchorStrategy = new JigsawAnchorStrategy(); this.graphBuilder = new JigsawPieceGraphBuilder(generator.layoutSeed ?? 0n, this.registry); }
  build(start, options = {}) { const anchor = this.anchorStrategy.resolve(start), definition = options.structure ? this.registry.structure(options.structure) : null, pool = options.startPool ?? options.pool ?? definition?.start_pool ?? definition?.startPool, graph = this.graphBuilder.build(anchor, { ...options, pool }); return { accepted: anchor.source !== "invalid_start_fallback", anchor, pool, graph, bounds: graph.bounds(), metadata: this.registry.snapshot() }; }
}
