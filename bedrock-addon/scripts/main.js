import { world, system, BlockPermutation } from "@minecraft/server";

const DIMENSION_ID = "sky_archipelago:archipelago";
const DB_PROPERTY = "sky_archipelago:generated_v2";
const SEED_PROPERTY = "sky_archipelago:seed_v2";
const GENERATOR_VERSION = 2;
const CHUNK_SIZE = 16;
const VIEW_CHUNKS = 8;
const COLUMNS_PER_SLICE = 64;
const TICKING_MARGIN = 2;

const CONFIG = {
  islandDensity: 0.40,
  clusterSpacing: 96,
  minIslandRadius: 24,
  maxIslandRadius: 75,
  minIslandY: 20,
  maxIslandY: 170,
  maxIslandThickness: 140,
  minY: -64,
  maxY: 320,
  materials: {
    top: "minecraft:grass_block",
    dirt: "minecraft:dirt",
    stone: "minecraft:stone",
    deepslate: "minecraft:deepslate",
    deepslateStartY: 6
  }
};

const state = { loaded: false, seed: 0, generated: new Set(), queued: new Set(), jobs: [] };

// Custom dimension registration follows Microsoft's documented Custom Dimension API pattern.
system.beforeEvents.startup.subscribe((event) => {
  event.dimensionRegistry.registerCustomDimension(DIMENSION_ID);
});

function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
function lerp(a, b, t) { return a + (b - a) * t; }
function smoothstep(t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }
function hash32(a) { a |= 0; a = Math.imul(a ^ (a >>> 16), 0x45d9f3b); a = Math.imul(a ^ (a >>> 16), 0x45d9f3b); return (a ^ (a >>> 16)) | 0; }
function hash4(seed, x, z, salt = 0) { let h = hash32(seed ^ 0x6d2b79f5); h = hash32(h ^ Math.imul(x | 0, 0x1f123bb5)); h = hash32(h ^ Math.imul(z | 0, 0x5bd1e995)); h = hash32(h ^ Math.imul(salt | 0, 0x27d4eb2d)); return (h >>> 0) / 4294967296; }
function valueNoise(seed, x, z, scale, salt = 0) {
  const sx = x * scale, sz = z * scale, x0 = Math.floor(sx), z0 = Math.floor(sz);
  const tx = smoothstep(sx - x0), tz = smoothstep(sz - z0);
  const a = hash4(seed, x0, z0, salt), b = hash4(seed, x0 + 1, z0, salt), c = hash4(seed, x0, z0 + 1, salt), d = hash4(seed, x0 + 1, z0 + 1, salt);
  return lerp(lerp(a, b, tx), lerp(c, d, tx), tz) * 2 - 1;
}
function fractalNoise(seed, x, z) { return valueNoise(seed, x, z, 0.018, 11) * 0.55 + valueNoise(seed, x, z, 0.045, 23) * 0.30 + valueNoise(seed, x, z, 0.10, 37) * 0.15; }
function weightedChoice(seed, x, z, salt, choices) {
  let total = 0; for (const c of choices) if (c.enabled !== false) total += Math.max(0, c.weight);
  let target = hash4(seed, x, z, salt) * Math.max(total, 1);
  for (const c of choices) { if (c.enabled === false) continue; target -= Math.max(0, c.weight); if (target <= 0) return c.name; }
  return choices[choices.length - 1].name;
}
function chooseRadius(seed, x, z) { const r = hash4(seed, x, z, 301); if (r < 0.34) return lerp(24, 40, hash4(seed, x, z, 302)); if (r < 0.67) return lerp(41, 58, hash4(seed, x, z, 302)); return lerp(59, 75, hash4(seed, x, z, 302)); }
function makeIsland(seed, cellX, cellZ) {
  if (hash4(seed, cellX, cellZ, 401) > CONFIG.islandDensity) return null;
  const radius = chooseRadius(seed, cellX, cellZ), jitter = Math.max(0, CONFIG.clusterSpacing * 0.5 - radius * 0.55);
  const centerX = Math.round(cellX * CONFIG.clusterSpacing + lerp(-jitter, jitter, hash4(seed, cellX, cellZ, 402)));
  const centerZ = Math.round(cellZ * CONFIG.clusterSpacing + lerp(-jitter, jitter, hash4(seed, cellX, cellZ, 403)));
  const centerY = Math.round(lerp(CONFIG.minIslandY, CONFIG.maxIslandY, hash4(seed, cellX, cellZ, 404)));
  const archetype = weightedChoice(seed, cellX, cellZ, 405, [{ name: "classic", weight: 1 }, { name: "bowlCrater", weight: 1 }, { name: "crescent", weight: 1 }, { name: "terrace", weight: 1 }]);
  return { centerX, centerZ, centerY, radius, archetype, rotation: hash4(seed, cellX, cellZ, 406) * Math.PI * 2, seed };
}
function rotate(x, z, angle) { const c = Math.cos(angle), s = Math.sin(angle); return { x: x * c - z * s, z: x * s + z * c }; }
function islandMask(island, x, z) {
  const dx = x - island.centerX, dz = z - island.centerZ, r = Math.sqrt(dx * dx + dz * dz) / island.radius;
  if (r > 1.15) return 0;
  const local = rotate(dx, dz, -island.rotation); let mask = 1 - smoothstep(r);
  mask += Math.sin(local.x * 0.055 + local.z * 0.023) * 0.045 * (1 - r);
  if (island.archetype === "bowlCrater") mask *= 0.82 + Math.exp(-r * r * 5) * 0.18;
  else if (island.archetype === "crescent") mask *= 1 - clamp(Math.max(0, local.x / island.radius + 0.15) * 0.85, 0, 0.82);
  else if (island.archetype === "terrace") mask = mask * 0.65 + Math.floor((1 - r) * 5) / 5 * 0.35;
  return clamp(mask + fractalNoise(island.seed, x, z) * 0.12 * (1 - r), 0, 1);
}
function topHeight(island, x, z, mask) {
  let h = island.centerY + fractalNoise(island.seed ^ 0x51f2a3, x, z) * island.radius * 0.22, radialRise = Math.pow(mask, 0.55);
  if (island.archetype === "bowlCrater") { const dx = x - island.centerX, dz = z - island.centerZ, r = Math.sqrt(dx * dx + dz * dz) / island.radius; h -= Math.max(0, 1 - r * 1.35) * island.radius * 0.10; }
  else if (island.archetype === "terrace") h += Math.floor(radialRise * 6) * 1.4;
  return Math.round(h + (1 - radialRise) * 2);
}
function bottomHeight(island, x, z, mask, topY) {
  const thickness = CONFIG.maxIslandThickness * (0.25 + 0.75 * Math.pow(mask, 0.55)); let bottom = topY - thickness + fractalNoise(island.seed ^ 0x7a31, x, z) * 5;
  const dx = x - island.centerX, dz = z - island.centerZ, distance = Math.sqrt(dx * dx + dz * dz) / island.radius;
  if (island.archetype === "crescent") bottom += distance * 12; if (island.archetype === "terrace") bottom += Math.sin(distance * Math.PI * 4) * 3;
  return Math.floor(bottom);
}
function islandForColumn(seed, x, z) {
  const cellX = Math.floor(x / CONFIG.clusterSpacing), cellZ = Math.floor(z / CONFIG.clusterSpacing); let best = null;
  for (let ox = -1; ox <= 1; ox++) for (let oz = -1; oz <= 1; oz++) {
    const island = makeIsland(seed, cellX + ox, cellZ + oz); if (!island) continue;
    const dx = x - island.centerX, dz = z - island.centerZ; if (dx * dx + dz * dz > island.radius * island.radius * 1.35) continue;
    const mask = islandMask(island, x, z); if (mask <= 0.035) continue; if (!best || mask > best.mask) best = { island, mask };
  }
  return best;
}
function chunkKey(cx, cz) { return `${cx},${cz}`; }
function chunkOf(v) { return Math.floor(v / CHUNK_SIZE); }
function loadDatabase() {
  if (state.loaded) return; state.loaded = true;
  try {
    const raw = world.getDynamicProperty(DB_PROPERTY); if (typeof raw === "string") { const db = JSON.parse(raw); if (db.version === GENERATOR_VERSION && Array.isArray(db.generated)) for (const key of db.generated) state.generated.add(key); }
    const savedSeed = world.getDynamicProperty(SEED_PROPERTY);
    if (typeof savedSeed === "number") state.seed = savedSeed | 0; else { state.seed = hash32(Date.now() | 0); world.setDynamicProperty(SEED_PROPERTY, state.seed); }
  } catch (e) { world.sendMessage(`§cSky Archipelago database error: ${e}`); }
}
function saveDatabase() { world.setDynamicProperty(DB_PROPERTY, JSON.stringify({ version: GENERATOR_VERSION, generated: [...state.generated] })); }
function enqueue(cx, cz, distance) { const key = chunkKey(cx, cz); if (state.generated.has(key) || state.queued.has(key)) return; state.queued.add(key); state.jobs.push({ cx, cz, distance, x: 0, z: 0, prepared: false, areaId: `sky_archipelago_${cx}_${cz}` }); }
function scanPlayers() {
  loadDatabase(); const requested = [];
  for (const player of world.getAllPlayers()) if (player.dimension.id === DIMENSION_ID) {
    const pcx = chunkOf(player.location.x), pcz = chunkOf(player.location.z);
    for (let dx = -VIEW_CHUNKS; dx <= VIEW_CHUNKS; dx++) for (let dz = -VIEW_CHUNKS; dz <= VIEW_CHUNKS; dz++) requested.push({ cx: pcx + dx, cz: pcz + dz, distance: dx * dx + dz * dz });
  }
  requested.sort((a, b) => a.distance - b.distance); for (const r of requested) enqueue(r.cx, r.cz, r.distance);
}
async function prepare(job) {
  const dim = world.getDimension(DIMENSION_ID);
  await world.tickingAreaManager.createTickingArea(job.areaId, { dimension: dim, from: { x: job.cx * 16 - TICKING_MARGIN, y: CONFIG.minY, z: job.cz * 16 - TICKING_MARGIN }, to: { x: job.cx * 16 + 15 + TICKING_MARGIN, y: CONFIG.maxY, z: job.cz * 16 + 15 + TICKING_MARGIN } });
  job.dimension = dim; job.seed = state.seed; job.prepared = true;
}
function writeColumn(job, x, z) {
  const result = islandForColumn(job.seed, x, z); if (!result) return;
  const topY = clamp(topHeight(result.island, x, z, result.mask), CONFIG.minY, CONFIG.maxY), bottomY = clamp(bottomHeight(result.island, x, z, result.mask, topY), CONFIG.minY, topY - 1); if (bottomY >= topY) return;
  job.dimension.fillBlocks({ from: { x, y: bottomY, z }, to: { x, y: topY, z } }, BlockPermutation.resolve(CONFIG.materials.stone));
  job.dimension.setBlockType({ x, y: topY, z }, CONFIG.materials.top);
  for (let d = 1; d <= 4 && topY - d > bottomY; d++) job.dimension.setBlockType({ x, y: topY - d, z }, CONFIG.materials.dirt);
  if (bottomY <= CONFIG.materials.deepslateStartY) { const deepslateTop = Math.min(topY - 5, CONFIG.materials.deepslateStartY); if (deepslateTop >= bottomY) job.dimension.fillBlocks({ from: { x, y: bottomY, z }, to: { x, y: deepslateTop, z } }, BlockPermutation.resolve(CONFIG.materials.deepslate)); }
}
function processSlice(job) { let processed = 0; while (processed < COLUMNS_PER_SLICE && job.x < 16) { writeColumn(job, job.cx * 16 + job.x, job.cz * 16 + job.z); processed++; job.z++; if (job.z >= 16) { job.z = 0; job.x++; } } return job.x >= 16; }
async function processJobs() {
  const job = state.jobs[0]; if (!job) return;
  try { if (!job.prepared) await prepare(job); if (!processSlice(job)) return; world.tickingAreaManager.removeTickingArea(job.areaId); state.jobs.shift(); state.queued.delete(chunkKey(job.cx, job.cz)); state.generated.add(chunkKey(job.cx, job.cz)); saveDatabase(); } catch (e) { }
}
world.afterEvents.worldLoad.subscribe(() => { loadDatabase(); world.sendMessage("§aSky Archipelago loaded. Run §e/scriptevent sky_archipelago:enter§a to enter."); });
system.runInterval(() => { scanPlayers(); void processJobs(); }, 1);
world.afterEvents.scriptEventReceive.subscribe((event) => {
  if (event.id === "sky_archipelago:enter") { const player = event.sourceEntity; if (!player || player.typeId !== "minecraft:player") return; player.teleport({ x: 0.5, y: 120, z: 0.5 }, { dimension: world.getDimension(DIMENSION_ID) }); }
  if (event.id === "sky_archipelago:reset") { state.generated.clear(); state.queued.clear(); state.jobs.length = 0; world.setDynamicProperty(DB_PROPERTY, JSON.stringify({ version: GENERATOR_VERSION, generated: [] })); world.sendMessage("§eSky Archipelago generated-chunk database reset. Existing blocks are not erased."); }
});
