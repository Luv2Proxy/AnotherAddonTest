import { world } from "@minecraft/server";
import { classifyStructure, villageSupport, hamletSupport, monumentSupport, mineshaftSupport } from "./StructureSupport.js";

const DB = "sky_archipelago:structure_detection_v4";
const AIR = "minecraft:air";
const SIGNATURES = {
  monument: { ids: ["minecraft:prismarine", "minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:sea_lantern"], min: 12, radius: 24 },
  mineshaft: { ids: ["minecraft:rail", "minecraft:oak_fence", "minecraft:oak_planks", "minecraft:torch"], min: 6, radius: 20 },
  village: { ids: ["minecraft:crafting_table", "minecraft:hay_block", "minecraft:composter", "minecraft:oak_log", "minecraft:oak_planks", "minecraft:chest"], min: 8, radius: 24 }
};
const box = (minX, minY, minZ, maxX, maxY, maxZ) => ({ min: { x: minX, y: minY, z: minZ }, max: { x: maxX, y: maxY, z: maxZ } });
const footprint = b => ({ minX: b.min.x, maxX: b.max.x, minZ: b.min.z, maxZ: b.max.z });
const expand = (b, n) => box(b.min.x - n, b.min.y, b.min.z - n, b.max.x + n, b.max.y, b.max.z + n);

export class StructureDetection {
  constructor(settings) { this.settings = settings; this.seen = new Set(); this.records = new Map(); this.load(); }

  load() {
    try {
      const raw = world.getDynamicProperty(DB);
      if (typeof raw === "string") {
        const d = JSON.parse(raw);
        this.seen = new Set(d.seen || []);
        this.records = new Map((d.records || []).map(r => [r.key, r]));
      }
    } catch { this.seen = new Set(); this.records = new Map(); }
  }

  save() {
    try { world.setDynamicProperty(DB, JSON.stringify({ version: 4, seen: [...this.seen], records: [...this.records.values()] })); }
    catch (e) { console.warn(`[Sky Archipelago] structure persistence failed: ${e}`); }
  }

  stableKey(category, id, center, bounds) {
    return `${category}:${id}:${Math.floor(center.x / 16)}:${Math.floor(center.z / 16)}:${bounds.min.x}:${bounds.min.y}:${bounds.min.z}`;
  }

  detectPlacedStructure(structureId, origin, size, categoryHint = null) {
    if (!origin || !size) return null;
    const b = box(origin.x, origin.y, origin.z, origin.x + size.x - 1, origin.y + size.y - 1, origin.z + size.z - 1);
    const category = categoryHint ?? classifyStructure(structureId);
    return this.register({ id: structureId, category, center: { x: Math.floor((b.min.x + b.max.x) / 2), z: Math.floor((b.min.z + b.max.z) / 2) }, baseY: b.min.y, box: b, exact: true, confidence: 1 });
  }

  registerJigsaw(structureId, boundingBox, categoryHint = null) {
    if (!boundingBox?.min || !boundingBox?.max) return null;
    const b = box(boundingBox.min.x, boundingBox.min.y, boundingBox.min.z, boundingBox.max.x, boundingBox.max.y, boundingBox.max.z);
    const category = categoryHint ?? classifyStructure(structureId);
    return this.register({ id: structureId, category, center: { x: Math.floor((b.min.x + b.max.x) / 2), z: Math.floor((b.min.z + b.max.z) / 2) }, baseY: b.min.y, box: b, exact: true, confidence: 1 });
  }

  register(r) {
    if (!r?.box) return null;
    const key = this.stableKey(r.category, r.id, r.center, r.box);
    const stored = { key, ...r };
    this.seen.add(key); this.records.set(key, stored); this.save();
    return stored;
  }

  discoverKnownStructures() {
    const sm = world.structureManager;
    if (!sm) return { pack: [], world: [] };
    let pack = [], worldIds = [];
    try { pack = sm.getPackStructureIds?.() ?? []; } catch {}
    try { worldIds = sm.getWorldStructureIds?.() ?? []; } catch {}
    return { pack, world: worldIds };
  }

  scan(dim, centerX, centerZ, radius = 8) {
    const found = [];
    const minX = centerX - radius * 16, maxX = centerX + radius * 16;
    const minZ = centerZ - radius * 16, maxZ = centerZ + radius * 16;
    for (let x = Math.floor(minX / 16) * 16; x <= maxX; x += 16) for (let z = Math.floor(minZ / 16) * 16; z <= maxZ; z += 16) {
      const candidate = this.infer(dim, x + 8, z + 8);
      if (!candidate) continue;
      const existing = this.findNearby(candidate.center.x, candidate.center.z, candidate.category, 32);
      if (existing) { this.merge(existing, candidate); continue; }
      const r = this.register(candidate); if (r) found.push(r);
    }
    if (found.length) this.save();
    return found;
  }

  infer(dim, cx, cz) {
    const centerY = dim.getHeight("world_surface", cx, cz) - 1;
    if (centerY < -63) return null;
    const candidates = [];
    for (const [kind, sig] of Object.entries(SIGNATURES)) {
      const points = [];
      for (let dx = -sig.radius; dx <= sig.radius; dx += 2) for (let dz = -sig.radius; dz <= sig.radius; dz += 2) {
        const y = dim.getHeight("world_surface", cx + dx, cz + dz) - 1;
        for (let dy = -3; dy <= 5; dy++) {
          const b = dim.getBlock({ x: cx + dx, y: y + dy, z: cz + dz });
          if (b && b.typeId !== AIR && sig.ids.includes(b.typeId)) { points.push({ x: cx + dx, y: y + dy, z: cz + dz }); break; }
        }
      }
      if (points.length < sig.min) continue;
      const b = bounds(points);
      const density = points.length / Math.max(1, (b.max.x - b.min.x + 1) * (b.max.z - b.min.z + 1));
      candidates.push({ id: `inferred:${kind}`, category: classifyStructure(kind), center: { x: Math.floor((b.min.x + b.max.x) / 2), z: Math.floor((b.min.z + b.max.z) / 2) }, baseY: b.min.y, box: b, exact: false, confidence: Math.min(.95, .35 + points.length / (sig.min * 20) + density * 20), points });
    }
    candidates.sort((a, b) => b.confidence - a.confidence);
    return candidates[0] ?? null;
  }

  findNearby(x, z, category, radius) {
    for (const r of this.records.values()) if (r.category === category && Math.hypot(r.center.x - x, r.center.z - z) <= radius) return r;
    return null;
  }

  merge(a, b) {
    if (a.exact) return a;
    a.box = union(a.box, b.box); a.center = { x: Math.floor((a.box.min.x + a.box.max.x) / 2), z: Math.floor((a.box.min.z + a.box.max.z) / 2) };
    a.baseY = Math.min(a.baseY, b.baseY); a.confidence = Math.max(a.confidence ?? 0, b.confidence ?? 0); a.exact = Boolean(a.exact || b.exact);
    this.records.set(a.key, a); this.save(); return a;
  }

  apply(dim, r) {
    if (!r?.box) return;
    const fp = footprint(r.box);
    switch (r.category) {
      case 1: villageSupport(dim, fp, r.baseY); break;
      case 2: hamletSupport(dim, fp, r.baseY); break;
      case 3: monumentSupport(dim, footprint(expand(r.box, 1)), r.baseY); break;
      case 4: mineshaftSupport(dim, r.points || [], r.baseY); break;
    }
  }
}

function bounds(points) {
  let minX = points[0].x, maxX = points[0].x, minY = points[0].y, maxY = points[0].y, minZ = points[0].z, maxZ = points[0].z;
  for (const p of points) { minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y); minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z); }
  return box(minX, minY, minZ, maxX, maxY, maxZ);
}
function union(a, b) { return box(Math.min(a.min.x, b.min.x), Math.min(a.min.y, b.min.y), Math.min(a.min.z, b.min.z), Math.max(a.max.x, b.max.x), Math.max(a.max.y, b.max.y), Math.max(a.max.z, b.max.z)); }
