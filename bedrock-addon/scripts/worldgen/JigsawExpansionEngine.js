import { JigsawPoolExpander } from "./JigsawPoolExpander.js";
import { JigsawConnectorResolver } from "./JigsawConnectorResolver.js";
import { JigsawCollisionValidator } from "./JigsawCollisionValidator.js";

export class JigsawExpansionEngine {
  constructor(registry, options = {}) {
    this.registry = registry;
    this.poolExpander = options.poolExpander ?? new JigsawPoolExpander(registry, options);
    this.connectorResolver = options.connectorResolver ?? new JigsawConnectorResolver(registry, options);
    this.collision = options.collision ?? new JigsawCollisionValidator({ padding: options.collisionPadding ?? 1 });
    this.maxDepth = Math.max(1, Number(options.maxDepth ?? 8));
    this.maxPieces = Math.max(1, Number(options.maxPieces ?? 64));
  }

  expand(startPieceId, startLocation, seed = "0", options = {}) {
    const placed = [];
    const queue = [{ id: startPieceId, location: { ...startLocation }, depth: 0, rotation: "none", parent: null }];
    const visited = new Set();
    const maxPieces = Math.min(this.maxPieces, Number(options.maxPieces ?? this.maxPieces));

    while (queue.length && placed.length < maxPieces) {
      const current = queue.shift();
      const key = `${current.id}:${current.location.x}:${current.location.y}:${current.location.z}:${current.rotation}`;
      if (visited.has(key) || current.depth > this.maxDepth) continue;
      visited.add(key);

      const piece = this.registry?.piece?.(current.id);
      if (!piece) continue;

      const collision = this.collision.canPlace(piece, current.location, current.rotation, placed);
      if (!collision.valid) continue;

      const record = { ...current, piece, bounds: collision.bounds };
      placed.push(record);

      for (const connector of this.connectorResolver.connectors(piece)) {
        const poolId = connector?.pool ?? connector?.target_pool ?? connector?.targetPool;
        const targetName = connector?.target_name ?? connector?.targetName ?? connector?.name;
        if (!poolId) continue;

        const candidates = this.connectorResolver.resolveTargets(connector, poolId, targetName, options);
        if (!candidates.length) continue;

        const selected = candidates[Math.floor(this.random(seed, current.depth, placed.length) * candidates.length)];
        const childConnector = selected.connectors[0];
        const rotation = childConnector?.rotation ?? "none";
        const location = this.connectorResolver.attachPosition(connector, childConnector, rotation);
        location.x += current.location.x;
        location.y += current.location.y;
        location.z += current.location.z;

        queue.push({
          id: selected.id,
          location,
          depth: current.depth + 1,
          rotation,
          parent: current.id
        });
      }
    }

    return placed;
  }

  random(seed, depth, index) {
    let h = 2166136261 >>> 0;
    const value = `${seed}:${depth}:${index}`;
    for (let i = 0; i < value.length; i++) {
      h ^= value.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return (h >>> 0) / 4294967296;
  }
}