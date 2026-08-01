export class StructureOverlapGuard {
  constructor() { this.occupancy = new Map(); }

  key(x, y, z) { return `${Math.floor(x)},${Math.floor(y)},${Math.floor(z)}`; }

  reserve(id, bounds, padding = 0) {
    if (!bounds) return false;
    const minX = Math.floor(bounds.min.x) - padding, maxX = Math.floor(bounds.max.x) + padding;
    const minY = Math.floor(bounds.min.y) - padding, maxY = Math.floor(bounds.max.y) + padding;
    const minZ = Math.floor(bounds.min.z) - padding, maxZ = Math.floor(bounds.max.z) + padding;
    for (let x = minX; x <= maxX; x += 4) for (let y = minY; y <= maxY; y += 8) for (let z = minZ; z <= maxZ; z += 4) {
      const owners = this.occupancy.get(this.key(x, y, z));
      if (owners && owners.some(o => o !== id)) return false;
    }
    for (let x = minX; x <= maxX; x += 4) for (let y = minY; y <= maxY; y += 8) for (let z = minZ; z <= maxZ; z += 4) {
      const k = this.key(x, y, z), owners = this.occupancy.get(k) ?? [];
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
