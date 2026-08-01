const DB = "sky_archipelago:structure_occupancy_v2";

export class StructureOverlapGuard {
  constructor() { this.records = new Map(); this.cells = new Map(); this.loaded = false; }

  key(x, y, z) { return `${Math.floor(x)},${Math.floor(y)},${Math.floor(z)}`; }
  cellKey(x, y, z) { return `${Math.floor(x / 16)},${Math.floor(y / 16)},${Math.floor(z / 16)}`; }

  normalize(bounds, padding = 0) {
    if (!bounds?.min || !bounds?.max) return null;
    return { min: { x: Math.floor(Math.min(bounds.min.x, bounds.max.x)) - padding, y: Math.floor(Math.min(bounds.min.y, bounds.max.y)) - padding, z: Math.floor(Math.min(bounds.min.z, bounds.max.z)) - padding }, max: { x: Math.floor(Math.max(bounds.min.x, bounds.max.x)) + padding, y: Math.floor(Math.max(bounds.min.y, bounds.max.y)) + padding, z: Math.floor(Math.max(bounds.min.z, bounds.max.z)) + padding } };
  }

  intersects(a, b) {
    return a.min.x <= b.max.x && a.max.x >= b.min.x && a.min.y <= b.max.y && a.max.y >= b.min.y && a.min.z <= b.max.z && a.max.z >= b.min.z;
  }

  touchedCells(bounds) {
    const out = [];
    for (let x = Math.floor(bounds.min.x / 16); x <= Math.floor(bounds.max.x / 16); x++) for (let y = Math.floor(bounds.min.y / 16); y <= Math.floor(bounds.max.y / 16); y++) for (let z = Math.floor(bounds.min.z / 16); z <= Math.floor(bounds.max.z / 16); z++) out.push(`${x},${y},${z}`);
    return out;
  }

  load() {
    if (this.loaded) return;
    this.loaded = true;
    try {
      const raw = world?.getDynamicProperty?.(DB);
      const data = typeof raw === "string" ? JSON.parse(raw) : null;
      for (const r of data?.records ?? []) if (r?.id && r?.bounds) this.records.set(r.id, r);
      this.rebuildIndex();
    } catch (e) { console.warn(`[Sky Archipelago] overlap state load failed: ${e}`); }
  }

  persist() {
    try {
      const records = [...this.records.values()];
      world.setDynamicProperty(DB, JSON.stringify({ version: 2, records }));
    } catch (e) { console.warn(`[Sky Archipelago] overlap state save failed: ${e}`); }
  }

  rebuildIndex() {
    this.cells.clear();
    for (const r of this.records.values()) for (const cell of this.touchedCells(r.bounds)) {
      const list = this.cells.get(cell) ?? []; if (!list.includes(r.id)) list.push(r.id); this.cells.set(cell, list);
    }
  }

  candidateRecords(bounds) {
    const ids = new Set();
    for (const cell of this.touchedCells(bounds)) for (const id of this.cells.get(cell) ?? []) ids.add(id);
    return [...ids].map(id => this.records.get(id)).filter(Boolean);
  }

  canReserve(id, bounds, padding = 0) {
    this.load(); const b = this.normalize(bounds, padding); if (!b) return false;
    return !this.candidateRecords(b).some(r => r.id !== id && this.intersects(b, r.bounds));
  }

  reserve(id, bounds, padding = 0, meta = {}) {
    this.load(); const b = this.normalize(bounds, padding); if (!b || !this.canReserve(id, b)) return false;
    this.records.set(id, { id, bounds: b, ...meta }); this.rebuildIndex(); this.persist(); return true;
  }

  replace(id, bounds, padding = 0, meta = {}) {
    this.load(); this.records.delete(id); this.rebuildIndex();
    const ok = this.reserve(id, bounds, padding, meta); if (!ok) this.persist(); return ok;
  }

  release(id) { this.load(); this.records.delete(id); this.rebuildIndex(); this.persist(); }
  get(id) { this.load(); return this.records.get(id) ?? null; }
  all() { this.load(); return [...this.records.values()]; }
  clear() { this.records.clear(); this.cells.clear(); this.persist(); }
  serialize() { this.load(); return [...this.records.values()]; }
  restore(data) { this.records = new Map((Array.isArray(data) ? data : []).filter(r => r?.id && r?.bounds).map(r => [r.id, r])); this.rebuildIndex(); this.loaded = true; }
}
