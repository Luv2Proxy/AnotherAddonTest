/**
 * Terrain projection helpers for generated Jigsaw placement.
 *
 * Bedrock does not expose Java's WORLD_SURFACE_WG/OCEAN_FLOOR_WG heightmaps
 * directly through Script API, so this module emulates the relevant heightmap
 * semantics by scanning blocks and explicitly selecting the surface/fluid
 * behavior required by each projection mode.
 */
export class TerrainProjection {
  constructor(dimension, options = {}) { this.dimension = dimension; this.options = options; }

  async sampleHeight(x, z, options = {}) {
    const minY = Number(options.minY ?? this.options.minY ?? -64);
    const maxY = Number(options.maxY ?? this.options.maxY ?? 320);
    const mode = String(options.heightmap ?? options.mode ?? "WORLD_SURFACE_WG").toUpperCase();
    const includeFluids = mode.includes("WORLD_SURFACE") || mode === "MOTION_BLOCKING" || mode === "MOTION_BLOCKING_NO_LEAVES";
    const ignoreLeaves = mode === "MOTION_BLOCKING_NO_LEAVES";
    const step = Math.max(1, Number(options.step ?? 1));

    for (let y = maxY; y >= minY; y -= step) {
      const block = this.dimension.getBlock({ x: Math.floor(x), y, z: Math.floor(z) });
      if (!block || block.isAir) continue;
      if (ignoreLeaves && (block.typeId?.includes("leaves") || block.hasTag?.("minecraft:is_leaves"))) continue;
      if (!includeFluids && block.isLiquid) continue;
      if (mode.includes("OCEAN_FLOOR") && block.isLiquid) continue;
      return y + 1;
    }
    return null;
  }

  async matchY(bounds, options = {}) {
    const samples = [];
    const x0 = Math.floor(bounds.min.x), x1 = Math.floor(bounds.max.x);
    const z0 = Math.floor(bounds.min.z), z1 = Math.floor(bounds.max.z);
    const stride = Math.max(1, Number(options.sampleStride ?? 4));
    for (let x = x0; x <= x1; x += stride) {
      for (let z = z0; z <= z1; z += stride) {
        const y = await this.sampleHeight(x, z, options);
        if (y != null) samples.push(y);
      }
    }
    if (!samples.length) return null;
    const mode = String(options.aggregate ?? "median").toLowerCase();
    if (mode === "min") return Math.min(...samples);
    if (mode === "max") return Math.max(...samples);
    if (mode === "mean" || mode === "average") return samples.reduce((a, b) => a + b, 0) / samples.length;
    samples.sort((a, b) => a - b);
    return samples[Math.floor(samples.length / 2)];
  }

  projectionHeightmap(projection) {
    const p = String(projection ?? "rigid").toLowerCase();
    if (p === "world_surface" || p === "world_surface_wg" || p === "surface") return "WORLD_SURFACE_WG";
    if (p === "ocean_floor" || p === "ocean_floor_wg" || p === "sea_floor") return "OCEAN_FLOOR_WG";
    if (p === "motion_blocking") return "MOTION_BLOCKING";
    if (p === "motion_blocking_no_leaves") return "MOTION_BLOCKING_NO_LEAVES";
    return null;
  }

  async project(origin, size, projection = "rigid", options = {}) {
    if (!projection || projection === "rigid") return { ...origin, offsetY: 0, projection: "rigid" };
    const bounds = {
      min: { x: origin.x, y: origin.y, z: origin.z },
      max: { x: origin.x + Math.max(0, size.x - 1), y: origin.y + Math.max(0, size.y - 1), z: origin.z + Math.max(0, size.z - 1) }
    };
    const heightmap = options.heightmap ?? this.projectionHeightmap(projection) ?? "WORLD_SURFACE_WG";
    const terrainY = await this.matchY(bounds, { ...options, heightmap });
    if (terrainY == null) return { ...origin, offsetY: 0, projection, heightmap, terrainY: null };
    const anchor = Number(options.anchorY ?? 0);
    const offsetY = terrainY - (origin.y + anchor);
    return { ...origin, y: origin.y + offsetY, offsetY, projection, heightmap, terrainY };
  }
}

export function createTerrainProjection(dimension, options) { return new TerrainProjection(dimension, options); }
