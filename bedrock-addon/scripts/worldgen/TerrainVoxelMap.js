const AIR = "minecraft:air";
const WATER_IDS = new Set(["minecraft:water", "minecraft:flowing_water"]);

function key(x, y, z) { return `${x},${y},${z}`; }
function columnKey(x, z) { return `${x},${z}`; }
function normalizeBlock(value) {
  if (!value) return null;
  if (typeof value === "string") return { id: value };
  const id = value.id ?? value.typeId ?? value.type?.id;
  return id ? { ...value, id } : null;
}

/**
 * In-memory terrain representation used by terrain adaptation and bulk output.
 * It deliberately has no dependency on Dimension.getBlock().
 */
export class TerrainVoxelMap {
  constructor(options = {}) {
    this.minY = Number(options.minY ?? -64);
    this.maxY = Number(options.maxY ?? 320);
    this.blocks = new Map();
    this.surface = new Map();
    this.columns = new Set();
    this.dirty = true;
    this.meta = new Map();
  }

  clear() {
    this.blocks.clear();
    this.surface.clear();
    this.columns.clear();
    this.meta.clear();
    this.dirty = true;
    return this;
  }

  has(x, y, z) { return this.blocks.has(key(x, y, z)); }

  get(x, y, z) {
    return this.blocks.get(key(Math.floor(x), Math.floor(y), Math.floor(z))) ?? null;
  }

  set(x, y, z, value) {
    x = Math.floor(x); y = Math.floor(y); z = Math.floor(z);
    if (y < this.minY || y > this.maxY) return false;
    const block = normalizeBlock(value);
    if (!block) return this.delete(x, y, z);
    this.blocks.set(key(x, y, z), { ...block, x, y, z });
    this.columns.add(columnKey(x, z));
    this.dirty = true;
    return true;
  }

  delete(x, y, z) {
    const removed = this.blocks.delete(key(Math.floor(x), Math.floor(y), Math.floor(z)));
    if (removed) this.dirty = true;
    return removed;
  }

  fill(min, max, value) {
    const block = normalizeBlock(value);
    if (!block) return 0;
    const minX = Math.floor(Math.min(min.x, max.x));
    const maxX = Math.floor(Math.max(min.x, max.x));
    const minY = Math.max(this.minY, Math.floor(Math.min(min.y, max.y)));
    const maxY = Math.min(this.maxY, Math.floor(Math.max(min.y, max.y)));
    const minZ = Math.floor(Math.min(min.z, max.z));
    const maxZ = Math.floor(Math.max(min.z, max.z));
    let count = 0;
    for (let x = minX; x <= maxX; x++) {
      for (let z = minZ; z <= maxZ; z++) {
        this.columns.add(columnKey(x, z));
        for (let y = minY; y <= maxY; y++) {
          this.blocks.set(key(x, y, z), { ...block, x, y, z });
          count++;
        }
      }
    }
    this.dirty = true;
    return count;
  }

  isAir(x, y, z) {
    const b = this.get(x, y, z);
    return !b || b.id === AIR;
  }

  isWater(x, y, z) {
    return WATER_IDS.has(this.get(x, y, z)?.id);
  }

  isSolid(x, y, z) {
    const b = this.get(x, y, z);
    return !!b && b.id !== AIR && !WATER_IDS.has(b.id);
  }

  surfaceY(x, z, fallback = this.minY) {
    x = Math.floor(x); z = Math.floor(z);
    const cached = this.surface.get(columnKey(x, z));
    if (cached != null) return cached;
    for (let y = this.maxY; y >= this.minY; y--) {
      if (this.isSolid(x, y, z)) {
        this.surface.set(columnKey(x, z), y);
        return y;
      }
    }
    this.surface.set(columnKey(x, z), fallback);
    return fallback;
  }

  rebuildSurface(x, z) {
    this.surface.delete(columnKey(Math.floor(x), Math.floor(z)));
    return this.surfaceY(x, z);
  }

  setSurfaceY(x, z, y) {
    this.surface.set(columnKey(Math.floor(x), Math.floor(z)), Math.floor(y));
  }

  updateSurfaceAfterSet(x, y, z) {
    const current = this.surfaceY(x, z);
    if (this.isSolid(x, y, z) && y >= current) this.setSurfaceY(x, z, y);
    else if (y === current && !this.isSolid(x, y, z)) this.rebuildSurface(x, z);
  }

  *column(x, z) {
    for (let y = this.minY; y <= this.maxY; y++) {
      const b = this.get(x, y, z);
      if (b) yield b;
    }
  }

  *columnsInBounds(minX, maxX, minZ, maxZ) {
    for (let x = Math.floor(minX); x <= Math.floor(maxX); x++) {
      for (let z = Math.floor(minZ); z <= Math.floor(maxZ); z++) yield [x, z];
    }
  }

  *blocksInBounds(min, max) {
    const minX = Math.floor(Math.min(min.x, max.x));
    const maxX = Math.floor(Math.max(min.x, max.x));
    const minY = Math.max(this.minY, Math.floor(Math.min(min.y, max.y)));
    const maxY = Math.min(this.maxY, Math.floor(Math.max(min.y, max.y)));
    const minZ = Math.floor(Math.min(min.z, max.z));
    const maxZ = Math.floor(Math.max(min.z, max.z));
    for (let x = minX; x <= maxX; x++) for (let y = minY; y <= maxY; y++) for (let z = minZ; z <= maxZ; z++) {
      const b = this.get(x, y, z);
      if (b) yield b;
    }
  }

  bounds() {
    if (!this.blocks.size) return null;
    let minX = Infinity, minY = Infinity, minZ = Infinity;
    let maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity;
    for (const b of this.blocks.values()) {
      minX = Math.min(minX, b.x); minY = Math.min(minY, b.y); minZ = Math.min(minZ, b.z);
      maxX = Math.max(maxX, b.x); maxY = Math.max(maxY, b.y); maxZ = Math.max(maxZ, b.z);
    }
    return { minX, minY, minZ, maxX, maxY, maxZ };
  }

  toArray() { return [...this.blocks.values()]; }

  snapshot() {
    return {
      blocks: this.blocks.size,
      columns: this.columns.size,
      cachedSurfaceColumns: this.surface.size,
      bounds: this.bounds()
    };
  }
}

export function voxelKey(x, y, z) { return key(x, y, z); }
