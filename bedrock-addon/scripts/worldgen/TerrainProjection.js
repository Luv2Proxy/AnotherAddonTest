/** Terrain projection helpers for generated Jigsaw placement. */
export class TerrainProjection {
  constructor(dimension, options = {}) {
    this.dimension = dimension;
    this.options = options;
  }

  async sampleHeight(x, z, options = {}) {
    const minY = options.minY ?? this.options.minY ?? -64;
    const maxY = options.maxY ?? this.options.maxY ?? 320;
    const step = Math.max(1, options.step ?? 1);
    const air = options.air ?? "minecraft:air";
    const fluid = options.fluid ?? true;
    for (let y = maxY; y >= minY; y -= step) {
      const block = this.dimension.getBlock({ x: Math.floor(x), y, z: Math.floor(z) });
      if (!block) continue;
      if (block.isAir) continue;
      if (!fluid && block.isLiquid) continue;
      if (block.typeId === air) continue;
      return y + 1;
    }
    return null;
  }

  async matchY(bounds, options = {}) {
    const samples = [];
    const x0 = Math.floor(bounds.min.x), x1 = Math.floor(bounds.max.x);
    const z0 = Math.floor(bounds.min.z), z1 = Math.floor(bounds.max.z);
    const stride = Math.max(1, options.sampleStride ?? 4);
    for (let x = x0; x <= x1; x += stride) {
      for (let z = z0; z <= z1; z += stride) {
        const y = await this.sampleHeight(x, z, options);
        if (y != null) samples.push(y);
      }
    }
    if (!samples.length) return null;
    const mode = options.mode ?? "median";
    if (mode === "min") return Math.min(...samples);
    if (mode === "max") return Math.max(...samples);
    samples.sort((a, b) => a - b);
    return samples[Math.floor(samples.length / 2)];
  }

  async project(origin, size, projection = "rigid", options = {}) {
    if (!projection || projection === "rigid") return { ...origin, offsetY: 0, projection: "rigid" };
    const bounds = {
      min: { x: origin.x, y: origin.y, z: origin.z },
      max: { x: origin.x + Math.max(0, size.x - 1), y: origin.y + Math.max(0, size.y - 1), z: origin.z + Math.max(0, size.z - 1) }
    };
    const terrainY = await this.matchY(bounds, options);
    if (terrainY == null) return { ...origin, offsetY: 0, projection, terrainY: null };
    const anchor = options.anchorY ?? 0;
    const offsetY = terrainY - (origin.y + anchor);
    return { ...origin, y: origin.y + offsetY, offsetY, projection, terrainY };
  }
}

export function createTerrainProjection(dimension, options) { return new TerrainProjection(dimension, options); }
