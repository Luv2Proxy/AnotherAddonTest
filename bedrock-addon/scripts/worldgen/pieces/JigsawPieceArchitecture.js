import { StructurePieceModel, StructurePieceGraph } from "./StructurePieceModel.js";
import { JigsawRegistry } from "../JigsawRegistry.js";
import { getGeneratedJigsawData } from "../JigsawDataLoader.js";
import { oppositeFacing, rotateFacing, transformPoint } from "../JigsawTransform.js";

function boundsFor(position, size) {
  const x = Math.floor(position?.x ?? 0);
  const y = Math.floor(position?.y ?? 0);
  const z = Math.floor(position?.z ?? 0);
  const sx = Math.max(1, Number(size?.x ?? 1));
  const sy = Math.max(1, Number(size?.y ?? 1));
  const sz = Math.max(1, Number(size?.z ?? 1));
  return {
    min: { x, y, z },
    max: { x: x + sx - 1, y: y + sy - 1, z: z + sz - 1 }
  };
}

function centerOf(bounds) {
  return {
    x: Math.floor((bounds.min.x + bounds.max.x) / 2),
    y: Math.floor((bounds.min.y + bounds.max.y) / 2),
    z: Math.floor((bounds.min.z + bounds.max.z) / 2)
  };
}

function connectorPosition(connector) {
  return connector?.position ?? { x: 0, y: 0, z: 0 };
}

function connectorFacing(connector) {
  return connector?.facing ?? connector?.facing_direction ?? "unknown";
}

export class JigsawAnchorResolver {
  resolve(start) {
    if (start?.valid === false) return { x: 0, baseY: 0, z: 0, source: "invalid_start_fallback" };
    const pieces = start?.pieces ?? [];
    const entry = pieces.reduce((best, piece) => {
      if (!best) return piece;
      return (piece.bounds?.min?.y ?? Infinity) < (best.bounds?.min?.y ?? Infinity) ? piece : best;
    }, null);
    if (entry?.bounds) {
      const center = centerOf(entry.bounds);
      return { x: center.x, baseY: entry.bounds.min.y, z: center.z, source: "entry_piece_center" };
    }
    if (start?.bounds) {
      const center = centerOf(start.bounds);
      return { x: center.x, baseY: start.bounds.min.y, z: center.z, source: "bounds_center_fallback" };
    }
    return { x: 0, baseY: 0, z: 0, source: "origin_fallback" };
  }

  fromBounds(bounds, source = "bounds") {
    const center = centerOf(bounds);
    return { x: center.x, baseY: bounds.min.y, z: center.z, source };
  }
}

export class JigsawAnchorStrategy {
  constructor(resolver = new JigsawAnchorResolver()) { this.resolver = resolver; }
  resolve(start) { return this.resolver.resolve(start); }
}

/** Creates real piece models from generated resolved-pieces.json data. */
export class JigsawPieceFactory {
  constructor(registry = new JigsawRegistry(getGeneratedJigsawData())) {
    this.registry = registry;
  }

  create({ id, role = "piece", position = { x: 0, y: 0, z: 0 }, rotation = 0, metadata = {} } = {}) {
    const piece = this.registry.piece(id) ?? this.registry.findPieceByLocation(id);
    const templateId = piece?.id ?? id;
    const size = piece?.size ?? { x: 1, y: 1, z: 1 };
    const bounds = boundsFor(position, size);
    const rotationName = ["None", "90_degrees", "180_degrees", "270_degrees"][((Number(rotation) % 4) + 4) % 4];
    return new StructurePieceModel({
      id: templateId,
      role,
      template: templateId,
      position,
      rotation: rotationName,
      bounds,
      metadata: {
        source: piece?.source ?? null,
        size,
        connectors: piece?.connectors ?? [],
        ...metadata
      }
    });
  }
}

/**
 * Deterministic planner over the extracted vanilla pool graph.
 * It is intentionally a planning/validation layer; native Bedrock remains the
 * authoritative final assembler when placeJigsaw/placeJigsawStructure exists.
 */
export class JigsawPieceGraphBuilder {
  constructor(seed = 0n, registry = new JigsawRegistry(getGeneratedJigsawData()), factory = null) {
    this.seed = BigInt(seed ?? 0);
    this.registry = registry;
    this.factory = factory ?? new JigsawPieceFactory(registry);
  }

  random(x, z, i) {
    let v = this.seed ^ BigInt(Math.floor(x)) * 0x9E3779B97F4A7C15n ^ BigInt(Math.floor(z)) * 0xC2B2AE3D27D4EB4Fn ^ BigInt(i);
    v ^= v >> 29n;
    v *= 0x94D049BB133111EBn;
    v ^= v >> 31n;
    return Number(v & 0xffffffffn) / 4294967296;
  }

  choose(poolId, index, x, z, targetName = null) {
    return this.registry.weightedCandidates(poolId, this.random(x, z, index), targetName);
  }

  build(anchor, { pool = null, startPool = null, target = "", depth = 7, maxDepth = null, maxPieces = 256, allowOverlap = false } = {}) {
    const rootPool = startPool ?? pool;
    const graph = new StructurePieceGraph("jigsaw", anchor);
    if (!rootPool) return graph;

    const limit = Math.max(1, Math.min(20, Number(maxDepth ?? depth) || 1));
    const queue = [{
      pool: rootPool,
      target,
      x: anchor.x,
      y: anchor.baseY,
      z: anchor.z,
      depth: 0,
      parentConnector: null,
      rotation: 0
    }];
    const occupied = [];
    const seen = new Set();
    let index = 0;

    while (queue.length && index < maxPieces) {
      const node = queue.shift();
      if (node.depth > limit) continue;

      const candidate = this.choose(node.pool, index, node.x, node.z, node.target);
      if (!candidate) continue;
      const element = candidate.element ?? {};

      if (element.element_type === "minecraft:empty_pool_element") {
        index++;
        continue;
      }

      if (element.element_type === "minecraft:list_pool_element") {
        const nested = Array.isArray(element.elements) ? element.elements : [];
        for (let i = 0; i < nested.length; i++) {
          const nestedElement = nested[i]?.element ?? nested[i];
          if (nestedElement?.location) {
            queue.unshift({
              pool: node.pool,
              target: node.target,
              x: node.x,
              y: node.y,
              z: node.z,
              depth: node.depth,
              parentConnector: node.parentConnector,
              rotation: (node.rotation + i) % 4,
              forcedElement: nestedElement
            });
          }
        }
        index++;
        continue;
      }

      const pieceId = element.location;
      const piece = this.registry.piece(pieceId) ?? this.registry.findPieceByLocation(pieceId);
      if (!piece) {
        index++;
        continue;
      }

      const pieceModel = this.factory.create({
        id: piece.id,
        position: { x: node.x, y: node.y, z: node.z },
        rotation: node.rotation,
        role: node.depth === 0 ? "root" : (node.depth >= limit ? "terminal" : "piece"),
        metadata: {
          pool: node.pool,
          target: node.target,
          depth: node.depth,
          weight: candidate.weight,
          processor: element.processors ?? null,
          projection: element.projection ?? "rigid",
          parentConnector: node.parentConnector
        }
      });

      if (!allowOverlap && occupied.some(other => this.overlap(other.bounds, pieceModel.bounds))) {
        index++;
        continue;
      }

      graph.add(pieceModel);
      occupied.push(pieceModel);
      seen.add(`${piece.id}:${node.x}:${node.y}:${node.z}:${node.rotation}`);
      index++;

      if (node.depth >= limit) continue;

      const connectors = piece.connectors ?? [];
      for (let c = 0; c < connectors.length; c++) {
        const connector = connectors[c];
        const childPool = connector.pool ?? connector.target_pool;
        if (!childPool || childPool === "unknown") continue;

        const next = this.choose(childPool, index + c, node.x + c, node.z - c, connector.target ?? connector.target_name ?? null);
        const childElement = next?.element;
        if (!childElement?.location) continue;

        const child = this.registry.piece(childElement.location) ?? this.registry.findPieceByLocation(childElement.location);
        if (!child) continue;

        const parentFacing = connectorFacing(connector);
        const desiredFacing = oppositeFacing(parentFacing);
        const childConnector = (child.connectors ?? []).find(candidateConnector => {
          const name = candidateConnector.name;
          const targetName = connector.target ?? connector.target_name;
          if (targetName && name && targetName !== name) return false;
          return connectorFacing(candidateConnector) === desiredFacing || desiredFacing === "unknown";
        });

        const childPos = childConnector ? connectorPosition(childConnector) : { x: 0, y: 0, z: 0 };
        const rotatedChildPos = transformPoint(childPos, child.size ?? { x: 1, y: 1, z: 1 }, node.rotation);
        const parentPos = connectorPosition(connector);
        const dx = node.x + parentPos.x - rotatedChildPos.x;
        const dy = node.y + parentPos.y - rotatedChildPos.y;
        const dz = node.z + parentPos.z - rotatedChildPos.z;
        const childRotation = (node.rotation + (parentFacing === "north" || parentFacing === "south" ? 2 : 0)) % 4;
        const key = `${child.id}:${dx}:${dy}:${dz}:${childRotation}`;
        if (seen.has(key)) continue;

        queue.push({
          pool: childPool,
          target: connector.target ?? connector.target_name ?? "",
          x: dx,
          y: dy,
          z: dz,
          depth: node.depth + 1,
          parentConnector: `${piece.id}:${c}`,
          rotation: childRotation
        });
      }
    }

    return graph;
  }

  overlap(a, b) {
    return a.min.x <= b.max.x && a.max.x >= b.min.x &&
      a.min.y <= b.max.y && a.max.y >= b.min.y &&
      a.min.z <= b.max.z && a.max.z >= b.min.z;
  }
}

export class JigsawPlacementCoordinator {
  constructor(generator) {
    this.generator = generator;
    this.registry = generator.registry ?? new JigsawRegistry(getGeneratedJigsawData());
    this.anchorResolver = new JigsawAnchorResolver();
    this.anchorStrategy = new JigsawAnchorStrategy(this.anchorResolver);
    this.graphBuilder = new JigsawPieceGraphBuilder(generator.layoutSeed ?? 0n, this.registry);
  }

  build(start, options = {}) {
    const anchor = this.anchorStrategy.resolve(start);
    const definition = options.structure ? this.registry.structure(options.structure) : null;
    const pool = options.startPool ?? options.pool ?? definition?.start_pool ?? definition?.startPool;
    const graph = this.graphBuilder.build(anchor, { ...options, pool });
    return {
      accepted: anchor.source !== "invalid_start_fallback",
      anchor,
      pool,
      graph,
      bounds: graph.bounds(),
      metadata: this.registry.snapshot()
    };
  }
}
