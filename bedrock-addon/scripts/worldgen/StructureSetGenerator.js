import { JigsawRegistry } from "./JigsawRegistry.js";

function hashSeed(seed) {
  let h = 2166136261 >>> 0;
  for (const c of String(seed ?? 0)) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619) >>> 0; }
  return h >>> 0;
}

function random(seed) {
  let s = hashSeed(seed) || 1;
  return () => { s ^= s << 13; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
}

function weighted(entries, r) {
  if (!entries.length) return null;
  const total = entries.reduce((n, e) => n + Math.max(0, Number(e.weight ?? e.inclusion_weight ?? 1)), 0);
  if (total <= 0) return entries[0];
  let cursor = r() * total;
  for (const e of entries) { cursor -= Math.max(0, Number(e.weight ?? e.inclusion_weight ?? 1)); if (cursor < 0) return e; }
  return entries[entries.length - 1];
}

function normalizePlacement(definition) {
  const placement = definition?.placement ?? definition?.placement_settings ?? definition?.placementSettings ?? definition ?? {};
  return {
    type: placement.type ?? placement.placement_type ?? "random_spread",
    salt: Number(placement.salt ?? 0),
    spacing: Math.max(1, Number(placement.spacing ?? placement.grid_spacing ?? 32)),
    separation: Math.max(0, Number(placement.separation ?? placement.min_separation ?? 8)),
    frequency: Number(placement.frequency ?? placement.frequency_reduction_method === "legacy_type_1" ? 1 : 1),
    frequencyReductionMethod: placement.frequency_reduction_method ?? placement.frequencyReductionMethod ?? null
  };
}

export class StructureSetGenerator {
  constructor(registry = new JigsawRegistry()) { this.registry = registry; }

  get(id) { return this.registry.structureSet(id); }

  entries(id) {
    const set = this.get(id);
    if (!set) return [];
    return set.structures ?? set.elements ?? set.entries ?? [];
  }

  plan(id, seed, options = {}) {
    const set = this.get(id);
    if (!set) return { ok: false, errors: [`Missing structure set: ${id}`], placements: [] };
    const placement = normalizePlacement(set);
    const structures = this.entries(id);
    if (!structures.length) return { ok: false, errors: [`${id}: structure set has no structures`], placements: [] };

    const r = random(`${seed}:${placement.salt}`);
    const count = Math.max(1, Number(options.count ?? 1));
    const origin = options.origin ?? { x: 0, y: 0, z: 0 };
    const placements = [];
    const usedCells = new Set();

    for (let i = 0; i < count; i++) {
      let selected = null;
      let cell = null;
      for (let attempt = 0; attempt < 32; attempt++) {
        const gx = Math.floor((r() * 2000) - 1000);
        const gz = Math.floor((r() * 2000) - 1000);
        const key = `${gx},${gz}`;
        if (usedCells.has(key)) continue;
        usedCells.add(key);
        selected = weighted(structures, r);
        cell = { gx, gz };
        break;
      }
      if (!selected || !cell) continue;

      const structure = selected.structure ?? selected.id ?? selected.location ?? selected.name;
      const x = origin.x + cell.gx * placement.spacing + Math.floor(r() * Math.max(1, placement.spacing - placement.separation));
      const z = origin.z + cell.gz * placement.spacing + Math.floor(r() * Math.max(1, placement.spacing - placement.separation));
      const y = origin.y;
      placements.push({ structure, x, y, z, cell, salt: placement.salt, spacing: placement.spacing, separation: placement.separation });
    }

    return { ok: placements.length > 0, structureSet: id, seed, placement, placements, errors: [] };
  }
}
