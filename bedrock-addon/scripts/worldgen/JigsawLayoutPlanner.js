import { JigsawRegistry } from "./JigsawRegistry.js";
import { add, sub, rotateY, rotateFacing, oppositeFacing, transformedSize, transformedBounds, boxesOverlap } from "./JigsawTransform.js";

function hashSeed(seed) {
  let h = 2166136261 >>> 0;
  for (const c of String(seed ?? 0)) {
    h ^= c.charCodeAt(0);
    h = Math.imul(h, 16777619) >>> 0;
  }
  return h >>> 0;
}

function rng(seed) {
  let s = hashSeed(seed) || 1;
  return () => {
    s ^= s << 13; s ^= s >>> 17; s ^= s << 5;
    s >>>= 0;
    return s / 4294967296;
  };
}

function asPoint(p) {
  return { x: Number(p?.x ?? 0), y: Number(p?.y ?? 0), z: Number(p?.z ?? 0) };
}

function connectorFacing(c) {
  return String(c?.facing ?? c?.direction ?? c?.orientation ?? "unknown").toLowerCase();
}

function connectorName(c) {
  return c?.name ?? c?.target_name ?? c?.targetName ?? "";
}

function connectorTarget(c) {
  return c?.target ?? c?.target_name ?? c?.targetName ?? "";
}

function connectorPool(c) {
  return c?.pool ?? c?.target_pool ?? c?.targetPool ?? "";
}

function connectorPosition(c) {
  return asPoint(c?.position ?? c?.pos ?? { x: 0, y: 0, z: 0 });
}

function pieceSize(piece) {
  return asPoint(piece?.size ?? piece?.dimensions ?? { x: 1, y: 1, z: 1 });
}

function pieceConnectors(piece) {
  return piece?.connectors ?? piece?.jigsaws ?? [];
}

function compatible(parent, child) {
  const parentTarget = connectorTarget(parent);
  const childName = connectorName(child);
  const parentName = connectorName(parent);
  const childTarget = connectorTarget(child);
  if (parentTarget && childName && parentTarget !== childName) return false;
  if (childTarget && parentName && childTarget !== parentName) return false;
  return true;
}

function connectorWorld(piecePlacement, connector) {
  const local = rotateY(connectorPosition(connector), piecePlacement.rotation);
  return {
    position: add(piecePlacement.origin, local),
    facing: rotateFacing(connectorFacing(connector), piecePlacement.rotation),
    name: connectorName(connector),
    target: connectorTarget(connector),
    pool: connectorPool(connector)
  };
}

export class JigsawLayoutPlanner {
  constructor(registry, options = {}) {
    this.registry = registry ?? new JigsawRegistry();
    this.maxDepth = Math.max(0, Number(options.maxDepth ?? 20));
    this.padding = Number(options.padding ?? 0);
    this.allowOverlap = Boolean(options.allowOverlap ?? false);
  }

  planStructure(identifier, origin = { x: 0, y: 0, z: 0 }, seed = 0, options = {}) {
    const definition = this.registry.structure(identifier);
    if (!definition) return { ok: false, errors: [`Missing structure: ${identifier}`], pieces: [], connectors: [] };
    const startPool = definition.start_pool ?? definition.startPool;
    if (!startPool) return { ok: false, errors: [`${identifier}: missing start_pool`], pieces: [], connectors: [] };
    return this.planPool(startPool, origin, seed, {
      ...options,
      maxDepth: options.maxDepth ?? definition.size ?? this.maxDepth,
      structureId: identifier
    });
  }

  planPool(poolId, origin = { x: 0, y: 0, z: 0 }, seed = 0, options = {}) {
    const random = rng(seed);
    const maxDepth = Math.max(0, Number(options.maxDepth ?? this.maxDepth));
    const pieces = [];
    const errors = [];
    const occupied = [];
    const queue = [];
    const root = this.#choose(poolId, random, options.targetName ?? null);
    if (!root?.piece) return { ok: false, errors: [`No resolvable root element in pool: ${poolId}`], pieces, connectors: [] };

    const rootPlacement = this.#placement(root.piece, origin, 0, root.element);
    pieces.push(rootPlacement);
    occupied.push(rootPlacement.bounds);
    queue.push({ placement: rootPlacement, depth: 0 });

    while (queue.length) {
      const current = queue.shift();
      if (current.depth >= maxDepth) continue;
      for (const parentConnector of pieceConnectors(current.placement.piece)) {
        const childPool = connectorPool(parentConnector);
        if (!childPool) continue;
        const selected = this.#choose(childPool, random, connectorTarget(parentConnector));
        if (!selected?.piece) {
          const fallback = this.registry.fallback(childPool);
          if (fallback && fallback !== childPool) {
            const fallbackChoice = this.#choose(fallback, random, connectorTarget(parentConnector));
            if (fallbackChoice?.piece) {
              this.#attach(selected ?? fallbackChoice, fallbackChoice, current, parentConnector, current.depth + 1, pieces, occupied, queue, random, errors);
            }
          }
          continue;
        }
        this.#attach(selected, selected, current, parentConnector, current.depth + 1, pieces, occupied, queue, random, errors);
      }
    }

    return {
      ok: errors.length === 0,
      structureId: options.structureId ?? null,
      rootPool: poolId,
      seed,
      pieces,
      connectors: pieces.flatMap(p => p.worldConnectors),
      errors
    };
  }

  #choose(poolId, random, targetName) {
    const candidates = this.registry.candidates(poolId, targetName).filter(c => c.piece && compatibleTarget(c, targetName));
    if (!candidates.length) return null;
    const total = candidates.reduce((n, c) => n + Math.max(0, Number(c.weight ?? 1)), 0);
    let cursor = random() * (total || candidates.length);
    for (const c of candidates) {
      cursor -= Math.max(0, Number(c.weight ?? 1));
      if (cursor < 0) return c;
    }
    return candidates[candidates.length - 1];
  }

  #placement(piece, origin, rotation, element) {
    const size = pieceSize(piece);
    const bounds = transformedBounds(origin, size, rotation);
    const placement = { piece, id: piece.id ?? element?.location ?? "unknown", origin: asPoint(origin), rotation, size, transformedSize: transformedSize(size, rotation), bounds };
    placement.worldConnectors = pieceConnectors(piece).map(c => connectorWorld(placement, c));
    return placement;
  }

  #attach(selected, chosen, current, parentConnector, depth, pieces, occupied, queue, random, errors) {
    const child = chosen?.piece ?? selected?.piece;
    if (!child) return;
    const parentWorld = connectorWorld(current.placement, parentConnector);
    const childConnectors = pieceConnectors(child).filter(c => compatible(parentConnector, c));
    if (!childConnectors.length) return;

    let placed = false;
    for (const childConnector of childConnectors) {
      const childFacing = connectorFacing(childConnector);
      for (let rotation = 0; rotation < 4 && !placed; rotation++) {
        const worldFacing = rotateFacing(childFacing, rotation);
        if (worldFacing !== oppositeFacing(parentWorld.facing)) continue;
        const local = rotateY(connectorPosition(childConnector), rotation);
        const origin = sub(parentWorld.position, local);
        const placement = this.#placement(child, origin, rotation, chosen?.element);
        if (!this.allowOverlap && occupied.some(b => boxesOverlap(b, placement.bounds, this.padding))) continue;
        pieces.push(placement);
        occupied.push(placement.bounds);
        queue.push({ placement, depth });
        placed = true;
      }
    }
    if (!placed && depth < this.maxDepth) errors.push(`Could not attach ${child.id ?? chosen?.element?.location ?? "piece"} at depth ${depth}`);
  }
}

function compatibleTarget(candidate, targetName) {
  if (!targetName) return true;
  const location = String(candidate?.element?.location ?? "");
  return Boolean(location);
}

export function createSeededRandom(seed) { return rng(seed); }
