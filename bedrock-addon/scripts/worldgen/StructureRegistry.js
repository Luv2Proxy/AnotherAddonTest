import { world } from "@minecraft/server";

/**
 * Bedrock-side equivalent of the base mod's StructureWhitelist + placement policy.
 * Only structures explicitly allowed here may be selected by the Sky Archipelago
 * generator.  Converted .mcstructure files are addressed by their pack-relative
 * structure IDs.
 */
export const StructureCategory = Object.freeze({
  DEFAULT: "DEFAULT",
  SKY: "SKY",
  SURFACE_SKY: "SURFACE_SKY",
  SMALL_SKY: "SMALL_SKY",
  HAMLET_SKY: "HAMLET_SKY",
  GROUND_VILLAGE: "GROUND_VILLAGE",
  STRONGHOLD: "STRONGHOLD",
  UNDERGROUND: "UNDERGROUND",
  WATER: "WATER"
});

const ALLOWED_FAMILIES = Object.freeze([
  "village/",
  "pillageroutpost/",
  "ruined_portal/",
  "shipwreck/",
  "ruin/",
  "underwater_ruin/",
  "igloo/",
  "trail_ruins/",
  "ancient_city/",
  "trial_chambers/",
  "mineshaft/"
]);

const CATEGORY_RULES = Object.freeze([
  { prefix: "village/", category: StructureCategory.GROUND_VILLAGE },
  { prefix: "pillageroutpost/", category: StructureCategory.SURFACE_SKY },
  { prefix: "ruined_portal/", category: StructureCategory.SMALL_SKY },
  { prefix: "shipwreck/", category: StructureCategory.WATER },
  { prefix: "ruin/", category: StructureCategory.WATER },
  { prefix: "underwater_ruin/", category: StructureCategory.WATER },
  { prefix: "igloo/", category: StructureCategory.SMALL_SKY },
  { prefix: "trail_ruins/", category: StructureCategory.GROUND_VILLAGE },
  { prefix: "ancient_city/", category: StructureCategory.UNDERGROUND },
  { prefix: "trial_chambers/", category: StructureCategory.UNDERGROUND },
  { prefix: "mineshaft/", category: StructureCategory.UNDERGROUND }
]);

const FALLBACK_IDS = Object.freeze([
  "village",
  "village/plains",
  "village/desert",
  "village/savanna",
  "village/snowy",
  "village/taiga"
]);

function normalize(id) {
  return String(id ?? "").replace(/\\/g, "/").replace(/^.*?:/, "").replace(/\.mcstructure$/i, "");
}

function categoryFor(id) {
  const normalized = normalize(id);
  for (const rule of CATEGORY_RULES) if (normalized.startsWith(rule.prefix)) return rule.category;
  return StructureCategory.DEFAULT;
}

function familyFor(id) {
  const normalized = normalize(id);
  return ALLOWED_FAMILIES.find(prefix => normalized.startsWith(prefix)) ?? null;
}

function isPlaceable(id) {
  return Boolean(familyFor(id));
}

export class StructureRegistry {
  constructor() {
    this.entries = new Map();
    this.initialized = false;
  }

  refresh() {
    this.entries.clear();
    const sm = world.structureManager;
    if (!sm) return this.entries;

    let ids = [];
    try { ids = sm.getPackStructureIds?.() ?? []; } catch (e) { console.warn(`[Sky Archipelago] structure ID enumeration failed: ${e}`); }

    for (const raw of ids) {
      const id = normalize(raw);
      if (!isPlaceable(id)) continue;
      this.entries.set(id, {
        id: raw,
        normalized: id,
        category: categoryFor(id),
        family: familyFor(id)
      });
    }

    // Some Bedrock versions expose pack IDs with a namespace or don't enumerate
    // the converted pack until its first access. Keep the known base-mod village
    // roots as lazy candidates; actual placement still validates through get().
    for (const id of FALLBACK_IDS) {
      if (!this.entries.has(id) && isPlaceable(id)) {
        this.entries.set(id, { id, normalized: id, category: categoryFor(id), family: familyFor(id), lazy: true });
      }
    }

    this.initialized = true;
    return this.entries;
  }

  ensure() { return this.initialized ? this.entries : this.refresh(); }

  isAllowed(id) { return isPlaceable(id); }

  get(id) {
    this.ensure();
    const normalized = normalize(id);
    const entry = this.entries.get(normalized);
    if (!entry) return null;
    try {
      const structure = world.structureManager.get?.(entry.id);
      return structure ? { ...entry, structure } : null;
    } catch { return null; }
  }

  byCategory(category) {
    return [...this.ensure().values()].filter(e => e.category === category);
  }

  byFamily(family) {
    return [...this.ensure().values()].filter(e => e.family === family);
  }

  select(seed, category, family = null) {
    let candidates = family ? this.byFamily(family) : this.byCategory(category);
    if (!candidates.length && family === "village/") candidates = this.byCategory(StructureCategory.GROUND_VILLAGE);
    if (!candidates.length) return null;
    const index = Math.abs(Number(seed) % candidates.length);
    return candidates[index];
  }

  snapshot() {
    return [...this.ensure().values()].map(e => ({ id: e.id, category: e.category, family: e.family }));
  }
}

export const STRUCTURE_FAMILIES = ALLOWED_FAMILIES;
