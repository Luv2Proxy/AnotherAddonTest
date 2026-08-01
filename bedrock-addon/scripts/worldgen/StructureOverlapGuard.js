export class StructureOverlapGuard {
  constructor() { this.occupancy = new Map(); }

  key(x, y, z) { return `${Math.floor(x)},${Math.floor(y)},${Math.floor(z)}`; }

  normalizedBounds(bounds, padding = 0) {
    if (!bounds?.min || !bounds?.max) return null;
    return {
      min: { x: Math.floor(bounds.min.x) - padding, y: Math.floor(bounds.min.y) - padding, z: Math.floor(bounds.min.z) - padding },
      max: { x: Math.floor(bounds.max.x) + padding, y: Math.floor(bounds.max.y) + padding, z: Math.floor(bounds.max.z) + padding }
    };
  }

  *sample(bounds, padding = 0) {
    const b = this.normalizedBounds(bounds, padding);
    if (!b) return;
    const stepX = 4, stepY = 8, stepZ = 4;
    for (let x = b.min.x; x <= b.max.x; x += stepX) {
      for (let y = b.min.y; y <= b.max.y; y += stepY) {
        for (let z = b.min.z; z <= b.max.z; z += stepZ) yield this.key(x, y, z);
      }
    }
    // Always include the far boundary so thin structures do not miss an edge.
    for (const x of [b.min.x, b.max.x]) for (const y of [b.min.y, b.max.y]) for (const z of [b.min.z, b.max.z]) yield this.key(x, y, z);
  }

  canReserve(id, bounds, padding = 0) {
    if (!bounds) return false;
    for (const k of this.sample(bounds, padding)) {
      const owners = this.occupancy.get(k);
      if (owners && owners.some(o => o !== id)) return false;
    }
    return true;
  }

  reserve(id, bounds, padding = 0) {
    if (!this.canReserve(id, bounds, padding)) return false;
    for (const k of this.sample(bounds, padding)) {
      const owners = this.occupancy.get(k) ?? [];
      if (!owners.includes(id)) owners.push(id);
      this.occupancy.set(k, owners);
    }
    return true;
  }

  release(id) {
    for (const [k, owners] of this.occupancy) {
      const next = owners.filter(o => o !== id);
      if (next.length) this.occupancy.set(k, next); else this.occupancy.delete(k);
    }
  }

  clear() { this.occupancy.clear(); }
  serialize() { return [...this.occupancy.entries()]; }
  restore(data) { this.occupancy = new Map(Array.isArray(data) ? data : []); }
}
