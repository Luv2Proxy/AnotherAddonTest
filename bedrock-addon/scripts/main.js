import { world, system, BlockPermutation } from "@minecraft/server";

// Bedrock runtime port of the Sky Archipelago terrain algorithm.
//
// The Java mod is a native chunk generator. Bedrock's public Add-On API does not
// expose an equivalent custom ChunkGenerator, so this implementation generates
// islands incrementally around active players. The procedural placement and island
// shape logic intentionally mirror the source project's exposed generator settings.

const CONFIG = {
  // Generation is incremental to avoid blocking the main thread.
  generationRadius: 192,
  cellsPerTick: 2,
  columnsPerTick: 900,
  minY: -64,
  maxY: 320,

  terrain: {
    islandDensity: 0.40,
    minIslandRadius: 24,
    maxIslandRadius: 75,
    minIslandY: 20,
    maxIslandY: 170,
    maxIslandThickness: 140,

    lowBandWeight: 0.15,
    midHighBandWeight: 0.75,
    veryHighBandWeight: 0.10,
    lowBandCenterOffset: -18,
    veryHighBandCenterOffset: 12,

    clusterSpacingMode: "consistent", // "consistent" or "dynamic"
    clusterSpacing: 96,
    minClusterSpacing: 96,
    maxClusterSpacing: 96,

    // Weighted size bands from the source project's specific-size mode.
    sizeBands: [
      { min: 24, max: 40, weight: 0.34 },
      { min: 41, max: 58, weight: 0.33 },
      { min: 59, max: 75, weight: 0.33 }
    ],

    archetypes: {
      classic: { enabled: true, weight: 1.0 },
      bowlCrater: { enabled: true, weight: 1.0 },
      crescent: { enabled: true, weight: 1.0 },
      terrace: { enabled: true, weight: 1.0 }
    },

    ocean: {
      enabled: false,
      levelY: 0,
      block: "minecraft:water"
    }
  },

  materials: {
    top: "minecraft:grass_block",
    dirt: "minecraft:dirt",
    stone: "minecraft:stone",
    deepslate: "minecraft:deepslate",
    deepslateStartY: 6
  }
};

const generatedCells = new Map();
const generatedWorlds = new Set();
const activeJobs = [];

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function lerp(a, b, t) {
  return a + (b - a) * t;
}

function smoothstep(t) {
  t = clamp(t, 0, 1);
  return t * t * (3 - 2 * t);
}

// Deterministic integer hash. Every world seed + candidate cell produces the same
// island position, radius, height, archetype, and relief regardless of load order.
function hash32(a) {
  a |= 0;
  a = Math.imul(a ^ (a >>> 16), 0x45d9f3b);
  a = Math.imul(a ^ (a >>> 16), 0x45d9f3b);
  return (a ^ (a >>> 16)) | 0;
}

function hash4(seed, x, y, salt = 0) {
  let h = hash32(seed ^ 0x6d2b79f5);
  h = hash32(h ^ Math.imul(x | 0, 0x1f123bb5));
  h = hash32(h ^ Math.imul(y | 0, 0x5bd1e995));
  h = hash32(h ^ Math.imul(salt | 0, 0x27d4eb2d));
  return (h >>> 0) / 4294967296;
}

function randomRange(seed, x, y, salt, min, max) {
  return lerp(min, max, hash4(seed, x, y, salt));
}

function weightedChoice(seed, x, y, salt, choices) {
  let total = 0;
  for (const choice of choices) {
    if (choice.enabled !== false) total += Math.max(0, choice.weight);
  }
  if (total <= 0) return choices[0];

  let target = hash4(seed, x, y, salt) * total;
  for (const choice of choices) {
    if (choice.enabled === false) continue;
    target -= Math.max(0, choice.weight);
    if (target <= 0) return choice;
  }
  return choices[choices.length - 1];
}

function valueNoise(seed, x, y, scale, salt = 0) {
  const sx = x * scale;
  const sy = y * scale;
  const x0 = Math.floor(sx);
  const y0 = Math.floor(sy);
  const tx = smoothstep(sx - x0);
  const ty = smoothstep(sy - y0);

  const a = hash4(seed, x0, y0, salt);
  const b = hash4(seed, x0 + 1, y0, salt);
  const c = hash4(seed, x0, y0 + 1, salt);
  const d = hash4(seed, x0 + 1, y0 + 1, salt);

  return lerp(lerp(a, b, tx), lerp(c, d, tx), ty) * 2 - 1;
}

function fractalNoise(seed, x, y) {
  // Broad relief + medium terrain variation + fine edge variation.
  return (
    valueNoise(seed, x, y, 0.018, 11) * 0.55 +
    valueNoise(seed, x, y, 0.045, 23) * 0.30 +
    valueNoise(seed, x, y, 0.10, 37) * 0.15
  );
}

function chooseSpacing(seed, cellX, cellZ) {
  if (CONFIG.terrain.clusterSpacingMode === "consistent") {
    return CONFIG.terrain.clusterSpacing;
  }
  return Math.round(randomRange(
    seed,
    cellX,
    cellZ,
    101,
    CONFIG.terrain.minClusterSpacing,
    CONFIG.terrain.maxClusterSpacing
  ));
}

function chooseBand(seed, cellX, cellZ) {
  const bands = [
    { weight: CONFIG.terrain.lowBandWeight, center: 0 },
    { weight: CONFIG.terrain.midHighBandWeight, center: 1 },
    { weight: CONFIG.terrain.veryHighBandWeight, center: 2 }
  ];

  let total = bands.reduce((sum, b) => sum + Math.max(0, b.weight), 0);
  let target = hash4(seed, cellX, cellZ, 201) * Math.max(total, 1);

  for (const band of bands) {
    target -= Math.max(0, band.weight);
    if (target <= 0) return band.center;
  }
  return 1;
}

function chooseRadius(seed, cellX, cellZ) {
  const bands = CONFIG.terrain.sizeBands;
  let total = bands.reduce((sum, band) => sum + band.weight, 0);
  let target = hash4(seed, cellX, cellZ, 301) * total;

  for (const band of bands) {
    target -= band.weight;
    if (target <= 0) {
      return randomRange(seed, cellX, cellZ, 302, band.min, band.max);
    }
  }

  return randomRange(seed, cellX, cellZ, 303, CONFIG.terrain.minIslandRadius, CONFIG.terrain.maxIslandRadius);
}

function makeIsland(seed, cellX, cellZ) {
  // Candidate rejection is the equivalent of island density in the source.
  if (hash4(seed, cellX, cellZ, 401) > CONFIG.terrain.islandDensity) return null;

  const spacing = chooseSpacing(seed, cellX, cellZ);
  const baseX = cellX * spacing;
  const baseZ = cellZ * spacing;

  // Deterministic jitter prevents a rigid square grid while preserving reproducibility.
  const radius = chooseRadius(seed, cellX, cellZ);
  const jitter = Math.max(0, spacing * 0.5 - radius * 0.55);
  const centerX = Math.round(baseX + randomRange(seed, cellX, cellZ, 402, -jitter, jitter));
  const centerZ = Math.round(baseZ + randomRange(seed, cellX, cellZ, 403, -jitter, jitter));

  const band = chooseBand(seed, cellX, cellZ);
  const normalizedHeight = hash4(seed, cellX, cellZ, 404);
  const lowY = CONFIG.terrain.minIslandY;
  const highY = CONFIG.terrain.maxIslandY;
  const centerRange = Math.max(1, highY - lowY);

  let centerY;
  if (band === 0) {
    centerY = lowY + centerRange * 0.20 + normalizedHeight * centerRange * 0.18 + CONFIG.terrain.lowBandCenterOffset;
  } else if (band === 2) {
    centerY = lowY + centerRange * 0.78 + normalizedHeight * centerRange * 0.18 + CONFIG.terrain.veryHighBandCenterOffset;
  } else {
    centerY = lowY + centerRange * 0.42 + normalizedHeight * centerRange * 0.32;
  }

  centerY = Math.round(clamp(centerY, lowY, highY));

  const archetypes = [
    { name: "classic", enabled: CONFIG.terrain.archetypes.classic.enabled, weight: CONFIG.terrain.archetypes.classic.weight },
    { name: "bowlCrater", enabled: CONFIG.terrain.archetypes.bowlCrater.enabled, weight: CONFIG.terrain.archetypes.bowlCrater.weight },
    { name: "crescent", enabled: CONFIG.terrain.archetypes.crescent.enabled, weight: CONFIG.terrain.archetypes.crescent.weight },
    { name: "terrace", enabled: CONFIG.terrain.archetypes.terrace.enabled, weight: CONFIG.terrain.archetypes.terrace.weight }
  ];

  const archetype = weightedChoice(seed, cellX, cellZ, 405, archetypes).name;

  return {
    cellX,
    cellZ,
    centerX,
    centerZ,
    centerY,
    radius,
    archetype,
    rotation: hash4(seed, cellX, cellZ, 406) * Math.PI * 2,
    relief: 0.75 + hash4(seed, cellX, cellZ, 407) * 0.65,
    seed
  };
}

function rotate(x, z, angle) {
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  return { x: x * c - z * s, z: x * s + z * c };
}

function islandMask(island, x, z) {
  const dx = x - island.centerX;
  const dz = z - island.centerZ;
  const r = Math.sqrt(dx * dx + dz * dz) / island.radius;
  if (r > 1.15) return 0;

  const local = rotate(dx, dz, -island.rotation);
  let mask = 1 - smoothstep(r);

  // Subtle anisotropy keeps islands organic without destroying their overall radius.
  const directional = Math.sin(local.x * 0.055 + local.z * 0.023) * 0.045;
  mask += directional * (1 - r);

  switch (island.archetype) {
    case "bowlCrater": {
      // Raised rim with a depressed center.
      const crater = Math.exp(-r * r * 5.0);
      mask *= 0.82 + crater * 0.18;
      break;
    }
    case "crescent": {
      // Remove one side of the circular landmass.
      const crescentCut = Math.max(0, local.x / island.radius + 0.15);
      mask *= 1 - clamp(crescentCut * 0.85, 0, 0.82);
      break;
    }
    case "terrace": {
      // Quantized stepped relief. The final terrain surface remains smoothed slightly.
      const steps = Math.floor((1 - r) * 5) / 5;
      mask = mask * 0.65 + steps * 0.35;
      break;
    }
    case "classic":
    default:
      break;
  }

  // Noisy edge erosion.
  const relief = fractalNoise(island.seed, x, z) * 0.12 * island.relief;
  mask += relief * (1 - r);

  return clamp(mask, 0, 1);
}

function topHeight(island, x, z, mask) {
  const terrainNoise = fractalNoise(island.seed ^ 0x51f2a3, x, z);
  const radialRise = Math.pow(mask, 0.55);
  let height = island.centerY + terrainNoise * island.radius * 0.22;

  if (island.archetype === "bowlCrater") {
    const dx = x - island.centerX;
    const dz = z - island.centerZ;
    const r = Math.sqrt(dx * dx + dz * dz) / island.radius;
    height -= Math.max(0, 1 - r * 1.35) * island.radius * 0.10;
  } else if (island.archetype === "terrace") {
    height += Math.floor(radialRise * 6) * 1.4;
  }

  return Math.round(height + (1 - radialRise) * 2);
}

function bottomHeight(island, x, z, mask, topY) {
  const dx = x - island.centerX;
  const dz = z - island.centerZ;
  const distance = Math.sqrt(dx * dx + dz * dz) / island.radius;

  // Full thickness near the core, tapering to a thin underside at the edge.
  const thicknessFactor = Math.pow(clamp(mask, 0, 1), 0.55);
  const edgeTaper = 0.25 + 0.75 * thicknessFactor;
  const targetThickness = CONFIG.terrain.maxIslandThickness * edgeTaper;
  const noise = fractalNoise(island.seed ^ 0x7a31, x, z) * 5;

  let bottom = topY - targetThickness + noise;

  // Make the underside more pointed for classic/crescent shapes.
  if (island.archetype === "crescent") bottom += distance * 12;
  if (island.archetype === "terrace") bottom += Math.sin(distance * Math.PI * 4) * 3;

  return Math.floor(bottom);
}

function blockForY(y, topY, island) {
  if (y === topY) return CONFIG.materials.top;
  if (y >= topY - 4) return CONFIG.materials.dirt;
  if (y <= CONFIG.materials.deepslateStartY) return CONFIG.materials.deepslate;
  return CONFIG.materials.stone;
}

function makeCellKey(dimensionId, cellX, cellZ) {
  return `${dimensionId}:${cellX}:${cellZ}`;
}

function getWorldSeed() {
  // The public Script API does not expose the numeric world seed on all supported
  // versions. Use the world identity when available, otherwise a stable constant.
  // This still gives deterministic generation within a world session/save.
  try {
    return hash32(world.getDynamicProperty("sky_archipelago_seed") ?? 0x13579bdf);
  } catch {
    return 0x13579bdf;
  }
}

function ensureWorldSeed() {
  try {
    let seed = world.getDynamicProperty("sky_archipelago_seed");
    if (typeof seed !== "number") {
      // A seed is created once and persisted as a dynamic property.
      seed = Math.floor(Math.random() * 0x7fffffff);
      world.setDynamicProperty("sky_archipelago_seed", seed);
    }
    return hash32(seed);
  } catch {
    return 0x13579bdf;
  }
}

function getCandidateCells(player) {
  const spacing = CONFIG.terrain.clusterSpacing;
  const radius = CONFIG.generationRadius + CONFIG.terrain.maxIslandRadius;
  const minCellX = Math.floor((player.location.x - radius) / spacing) - 1;
  const maxCellX = Math.floor((player.location.x + radius) / spacing) + 1;
  const minCellZ = Math.floor((player.location.z - radius) / spacing) - 1;
  const maxCellZ = Math.floor((player.location.z + radius) / spacing) + 1;
  const result = [];

  for (let cellX = minCellX; cellX <= maxCellX; cellX++) {
    for (let cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
      result.push([cellX, cellZ]);
    }
  }
  return result;
}

function enqueueIslandGeneration(dimension, island, player) {
  const key = makeCellKey(dimension.id, island.cellX, island.cellZ);
  if (generatedCells.has(key)) return;

  const dx = island.centerX - player.location.x;
  const dz = island.centerZ - player.location.z;
  const maxDistance = CONFIG.generationRadius + island.radius;
  if (dx * dx + dz * dz > maxDistance * maxDistance) return;

  generatedCells.set(key, "queued");
  activeJobs.push({ dimension, island, x: -island.radius, z: -island.radius });
}

function processIslandJob(job) {
  const island = job.island;
  let processed = 0;
  const radius = Math.ceil(island.radius * 1.15);

  while (job.x <= radius && processed < CONFIG.columnsPerTick) {
    const worldX = Math.floor(island.centerX + job.x);
    const worldZ = Math.floor(island.centerZ + job.z);
    const mask = islandMask(island, worldX, worldZ);

    if (mask > 0.035) {
      const topY = clamp(topHeight(island, worldX, worldZ, mask), CONFIG.minY, CONFIG.maxY);
      const bottomY = clamp(bottomHeight(island, worldX, worldZ, mask, topY), CONFIG.minY, topY - 1);

      // Use one vertical fill operation per column. This is much cheaper than setting
      // every block individually and preserves the procedural top/underside profile.
      try {
        job.dimension.fillBlocks(
          {
            from: { x: worldX, y: bottomY, z: worldZ },
            to: { x: worldX, y: topY, z: worldZ }
          },
          BlockPermutation.resolve(CONFIG.materials.stone)
        );

        if (topY - bottomY >= 1) {
          job.dimension.setBlockType({ x: worldX, y: topY, z: worldZ }, CONFIG.materials.top);
          for (let d = 1; d <= 4 && topY - d > bottomY; d++) {
            job.dimension.setBlockType({ x: worldX, y: topY - d, z: worldZ }, CONFIG.materials.dirt);
          }
        }

        if (bottomY <= CONFIG.materials.deepslateStartY) {
          const deepslateTop = Math.min(topY - 5, CONFIG.materials.deepslateStartY);
          if (deepslateTop >= bottomY) {
            job.dimension.fillBlocks(
              {
                from: { x: worldX, y: bottomY, z: worldZ },
                to: { x: worldX, y: deepslateTop, z: worldZ }
              },
              BlockPermutation.resolve(CONFIG.materials.deepslate)
            );
          }
        }
      } catch {
        // A column can fail if the dimension is unloading or the target Y is outside
        // the current world's height range. Continue generation rather than stopping.
      }
    }

    processed++;
    job.z++;
    if (job.z > radius) {
      job.z = -radius;
      job.x++;
    }
  }

  return job.x > radius;
}

function scheduleNearbyIslands() {
  const seed = ensureWorldSeed();

  for (const player of world.getAllPlayers()) {
    const dimension = player.dimension;
    if (dimension.id !== "minecraft:overworld") continue;

    for (const [cellX, cellZ] of getCandidateCells(player)) {
      const island = makeIsland(seed, cellX, cellZ);
      if (island) enqueueIslandGeneration(dimension, island, player);
    }
  }
}

system.runInterval(() => {
  scheduleNearbyIslands();

  let completed = 0;
  let processedCells = 0;
  while (activeJobs.length > 0 && processedCells < CONFIG.cellsPerTick) {
    const job = activeJobs[0];
    if (processIslandJob(job)) {
      activeJobs.shift();
      const key = makeCellKey(job.dimension.id, job.island.cellX, job.island.cellZ);
      generatedCells.set(key, "complete");
      completed++;
    }
    processedCells++;
  }
}, 1);

world.afterEvents.worldInitialize.subscribe(() => {
  // Force initialization of the persistent generator seed.
  ensureWorldSeed();
  generatedWorlds.add("minecraft:overworld");
});

world.afterEvents.playerSpawn.subscribe(event => {
  if (event.initialSpawn) {
    // The normal interval handles generation; this simply ensures the seed exists
    // before the first candidate scan.
    ensureWorldSeed();
  }
});
