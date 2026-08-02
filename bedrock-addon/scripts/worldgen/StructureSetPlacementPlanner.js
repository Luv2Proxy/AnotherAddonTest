import { StructureCategory } from "./StructureRegistry.js";
import { getStructurePlacementPolicy } from "./StructurePlacementPolicies.js";

function hashString(value) {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < String(value).length; i++) { h ^= String(value).charCodeAt(i); h = Math.imul(h, 16777619); }
  return h >>> 0;
}

function random(seed) {
  let state = hashString(seed) || 1;
  return () => { state ^= state << 13; state ^= state >>> 17; state ^= state << 5; return (state >>> 0) / 4294967296; };
}

export class StructureSetPlacementPlanner {
  constructor(registry, options = {}) {
    this.registry = registry;
    this.minDistance = Number(options.minDistance ?? 32);
    this.maxDistance = Number(options.maxDistance ?? 512);
  }

  entries(setId) {
    const set = this.registry?.structureSet?.(setId) ?? this.registry?.structureSets?.[setId] ?? this.registry?.generatedStructureSets?.[setId];
    return set?.structures ?? set?.entries ?? [];
  }

  spacing(setId) {
    const set = this.registry?.structureSet?.(setId) ?? this.registry?.structureSets?.[setId] ?? this.registry?.generatedStructureSets?.[setId];
    return Math.max(this.minDistance, Number(set?.spacing ?? set?.placement?.spacing ?? 32));
  }

  separation(setId) {
    const set = this.registry?.structureSet?.(setId) ?? this.registry?.structureSets?.[setId] ?? this.registry?.generatedStructureSets?.[setId];
    return Math.max(0, Number(set?.separation ?? set?.placement?.separation ?? 8));
  }

  category(candidate) {
    const explicit = String(candidate?.category ?? "").toUpperCase();
    if (Object.values(StructureCategory).includes(explicit)) return explicit;
    const id = String(candidate?.structure ?? candidate?.id ?? "").toLowerCase();
    if (id.includes("village/") || id.includes("trail_ruins")) return StructureCategory.GROUND_VILLAGE;
    if (id.includes("ancient_city") || id.includes("trial_chambers") || id.includes("mineshaft")) return StructureCategory.UNDERGROUND;
    if (id.includes("monument") || id.includes("shipwreck") || id.includes("underwater_ruin") || id.includes("coral")) return StructureCategory.WATER;
    if (id.includes("stronghold")) return StructureCategory.STRONGHOLD;
    return StructureCategory.SKY;
  }

  plan(setId, origin, seed, options = {}) {
    const rng = random(`${seed}:${setId}`);
    const spacing = this.spacing(setId);
    const separation = this.separation(setId);
    const radius = Math.min(this.maxDistance, Number(options.radius ?? this.maxDistance));
    const entries = this.entries(setId);
    if (!entries.length) return [];

    const count = Math.max(1, Number(options.count ?? 1));
    const placements = [];
    for (let i = 0; i < count * 3 && placements.length < count; i++) {
      const angle = rng() * Math.PI * 2;
      const distance = spacing + rng() * Math.max(0, radius - spacing);
      const x = Math.floor(origin.x + Math.cos(angle) * distance);
      const z = Math.floor(origin.z + Math.sin(angle) * distance);
      if (placements.some(p => Math.hypot(p.x - x, p.z - z) < spacing + separation)) continue;
      const entry = entries[Math.floor(rng() * entries.length)];
      const id = typeof entry === "string" ? entry : (entry.structure ?? entry.id ?? entry.name);
      if (!id) continue;
      const candidate = { structure: id, x, z, category: typeof entry === "object" ? entry.category : undefined, weight: typeof entry === "object" ? entry.weight : undefined };
      candidate.category = this.category(candidate);
      candidate.policy = getStructurePlacementPolicy(candidate.category);
      placements.push(candidate);
    }
    return placements;
  }
}
