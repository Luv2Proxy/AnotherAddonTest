import { StructureCategory } from "./StructureRegistry.js";
import { getStructurePlacementPolicy } from "./StructurePlacementPolicies.js";
import { GeneratedStructurePlanner } from "./GeneratedStructurePlanner.js";

export class StructureSetPlacementPlanner {
  constructor(registry, options = {}) {
    this.registry = registry;
    this.minDistance = Number(options.minDistance ?? 32);
    this.maxDistance = Number(options.maxDistance ?? 512);
    this.generated = options.generatedPlanner ?? new GeneratedStructurePlanner({ registry });
  }

  refresh(registry = this.registry) {
    if (registry) {
      this.registry = registry;
      this.generated = new GeneratedStructurePlanner({ registry });
    }
    return this;
  }

  entries(setId) {
    const set = this.registry?.structureSet?.(setId) ?? this.registry?.structureSets?.get?.(setId) ?? this.registry?.structureSets?.[setId] ?? this.registry?.generatedStructureSets?.[setId];
    return set?.structures ?? set?.entries ?? [];
  }

  definition(setId) {
    return this.registry?.structureSet?.(setId) ?? this.registry?.structureSets?.get?.(setId) ?? this.registry?.structureSets?.[setId] ?? this.registry?.generatedStructureSets?.[setId] ?? null;
  }

  placement(setId) {
    const set = this.definition(setId) ?? {};
    const p = set.placement ?? set;
    return {
      type: p.type ?? "minecraft:random_spread",
      spacing: Math.max(this.minDistance, Number(p.spacing ?? 32)),
      separation: Math.max(0, Number(p.separation ?? 8)),
      salt: Number(p.salt ?? 0),
      spread_type: p.spread_type ?? p.spreadType ?? "linear"
    };
  }

  spacing(setId) { return this.placement(setId).spacing; }
  separation(setId) { return this.placement(setId).separation; }

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
    // Prefer the generated-JS-first placement algorithm. It reproduces the
    // Structure Set grid semantics (spacing/separation/salt) rather than the
    // old radial approximation.
    const generated = this.generated.planSet(setId, origin, seed, {
      ...options,
      radius: Math.min(this.maxDistance, Number(options.radius ?? this.maxDistance)),
      count: Math.max(1, Number(options.count ?? 1))
    });
    if (generated?.length) {
      return generated.map(candidate => {
        const planned = this.generated.planCandidate(candidate, options.host ?? {});
        return {
          ...planned,
          category: planned.category ?? this.category(planned),
          policy: getStructurePlacementPolicy(planned.category ?? this.category(planned)),
          generated: true
        };
      });
    }

    // Compatibility fallback for old/generated databases that predate native
    // Structure Set metadata.
    const entries = this.entries(setId);
    if (!entries.length) return [];
    const p = this.placement(setId);
    const count = Math.max(1, Number(options.count ?? 1));
    const placements = [];
    let state = hashSeed(`${seed}:${setId}:${p.salt}`) || 1;
    const next = () => { state ^= state << 13; state ^= state >>> 17; state ^= state << 5; state >>>= 0; return state / 4294967296; };
    const radius = Math.min(this.maxDistance, Number(options.radius ?? this.maxDistance));
    for (let i = 0; i < count * 4 && placements.length < count; i++) {
      const cx = Math.floor(origin.x / p.spacing) + Math.floor((next() - .5) * 2 * Math.max(1, Math.floor(radius / p.spacing)));
      const cz = Math.floor(origin.z / p.spacing) + Math.floor((next() - .5) * 2 * Math.max(1, Math.floor(radius / p.spacing)));
      const x = cx * p.spacing + Math.floor(p.separation / 2) + Math.floor(next() * Math.max(1, p.spacing - p.separation));
      const z = cz * p.spacing + Math.floor(p.separation / 2) + Math.floor(next() * Math.max(1, p.spacing - p.separation));
      if (placements.some(q => Math.hypot(q.x - x, q.z - z) < p.spacing + p.separation)) continue;
      let total = 0; for (const e of entries) total += Number(e.weight ?? 1);
      let cursor = next() * Math.max(1, total), selected = entries[0];
      for (const e of entries) { cursor -= Number(e.weight ?? 1); if (cursor < 0) { selected = e; break; } }
      const id = typeof selected === "string" ? selected : selected.structure ?? selected.id ?? selected.name;
      if (!id) continue;
      const candidate = { structure: id, x, z, category: typeof selected === "object" ? selected.category : undefined, weight: typeof selected === "object" ? selected.weight : undefined };
      candidate.category = this.category(candidate);
      candidate.policy = getStructurePlacementPolicy(candidate.category);
      placements.push(candidate);
    }
    return placements;
  }
}

function hashSeed(value) {
  let h = 2166136261 >>> 0;
  for (const c of String(value)) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619) >>> 0; }
  return h >>> 0;
}
