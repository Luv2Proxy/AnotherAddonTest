export class JigsawPoolExpander {
  constructor(registry, options = {}) {
    this.registry = registry;
    this.maxDepth = Math.max(1, Number(options.maxDepth ?? 16));
    this.maxPieces = Math.max(1, Number(options.maxPieces ?? 64));
  }

  expand(poolId, seed = "0") {
    const result = [];
    const visited = new Set();
    const walk = (id, depth, weight = 1) => {
      if (!id || depth > this.maxDepth || result.length >= this.maxPieces) return;
      const key = String(id);
      if (visited.has(`${key}:${depth}`)) return;
      visited.add(`${key}:${depth}`);
      const pool = this.registry?.pool?.(id);
      if (!pool) return;
      for (const wrapper of pool.elements ?? []) {
        if (result.length >= this.maxPieces) break;
        const element = wrapper?.element ?? wrapper;
        const w = Math.max(0, Number(wrapper?.weight ?? 1)) * weight;
        if (!element) continue;
        const type = element.element_type;
        if (type === "minecraft:single_pool_element" || type === "minecraft:legacy_single_pool_element") {
          const piece = this.registry.piece?.(element.location);
          result.push({ id: element.location, piece, element, weight: w, depth, pool: id, seed: `${seed}:${id}:${result.length}` });
        } else if (type === "minecraft:list_pool_element") {
          for (const child of element.elements ?? []) {
            const childId = child?.location ?? child?.element?.location;
            if (childId) walk(childId, depth + 1, w);
          }
        } else if (element.location) walk(element.location, depth + 1, w);
      }
      if (pool.fallback) walk(pool.fallback, depth + 1, weight * 0.5);
    };
    walk(poolId, 0);
    return result;
  }

  weighted(poolId, random = Math.random(), seed = "0") {
    const pieces = this.expand(poolId, seed);
    if (!pieces.length) return null;
    const total = pieces.reduce((s, p) => s + Math.max(0, p.weight), 0);
    if (total <= 0) return pieces[0];
    let cursor = Math.max(0, Math.min(0.999999999, Number(random))) * total;
    for (const piece of pieces) {
      cursor -= Math.max(0, piece.weight);
      if (cursor < 0) return piece;
    }
    return pieces[pieces.length - 1];
  }
}
