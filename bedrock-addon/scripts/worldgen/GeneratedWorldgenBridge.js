import { getGeneratedJigsawData, generatedNativeJson, generatedStructure, generatedStructureSet, generatedPool } from "./JigsawDataLoader.js";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { StructureSetGenerator } from "./StructureSetGenerator.js";

function unwrap(v){return v?.definition??v??null;}
function idOf(v){return v?.description?.identifier??v?.identifier??null;}
function normalizeProjection(v){
  const p=String(v??"none").toLowerCase();
  if(p==="world_surface_wg"||p==="world_surface")return "world_surface";
  if(p==="ocean_floor_wg"||p==="ocean_floor"||p==="sea_floor")return "sea_floor";
  if(p==="motion_blocking_no_leaves")return "motion_blocking_no_leaves";
  if(p==="motion_blocking")return "motion_blocking";
  return "none";
}

export class GeneratedWorldgenBridge {
  constructor({ data = getGeneratedJigsawData(), registry = null } = {}) {
    this.data = data;
    this.registry = registry ?? new JigsawRegistry(data);
    this.structureSets = new StructureSetGenerator(this.registry);
    this.cache = new Map();
  }
  refresh(registry = this.registry) {
    if (registry) { this.registry = registry; this.structureSets.registry = registry; }
    this.cache.clear();
    return this;
  }
  structure(id) { return unwrap(this.registry.structure(id) ?? generatedStructure(id)); }
  structureSet(id) { return unwrap(this.registry.structureSet(id) ?? generatedStructureSet(id)); }
  pool(id) { return unwrap(this.registry.pool(id) ?? generatedPool(id)); }
  processor(id) { return unwrap(this.registry.processor(id)); }

  resolveStructureMetadata(id) {
    const key = String(id ?? "");
    if (this.cache.has(key)) return this.cache.get(key);
    const definition = this.structure(key) ?? {};
    const metadata = {
      id: idOf(definition) ?? key,
      step: definition.step ?? "surface_structures",
      terrain_adaptation: definition.terrain_adaptation ?? "none",
      start_pool: definition.start_pool ?? definition.startPool ?? null,
      start_jigsaw_name: definition.start_jigsaw_name ?? definition.startJigsawName ?? null,
      max_depth: Math.max(1, Number(definition.max_depth ?? definition.maxDepth ?? 1)),
      max_distance_from_center: definition.max_distance_from_center ?? definition.maxDistanceFromCenter ?? { horizontal: 80, vertical: 80 },
      heightmap_projection: normalizeProjection(definition.heightmap_projection ?? definition.heightmapProjection),
      biome_filters: definition.biome_filters ?? definition.biomeFilters ?? [],
      start_height: definition.start_height ?? definition.startHeight ?? null,
      native: Boolean(generatedNativeJson("structures", key) || this.data.native?.structures?.[key])
    };
    this.cache.set(key, metadata);
    return metadata;
  }

  resolveStructureSetMetadata(id) {
    const definition = this.structureSet(id) ?? {};
    const placement = definition.placement ?? {};
    return {
      id: idOf(definition) ?? id,
      placement: {
        type: placement.type ?? "minecraft:random_spread",
        salt: Number(placement.salt ?? 0),
        spacing: Math.max(1, Number(placement.spacing ?? 34)),
        separation: Math.max(0, Number(placement.separation ?? 8)),
        spread_type: placement.spread_type ?? placement.spreadType ?? "linear"
      },
      structures: definition.structures ?? definition.entries ?? []
    };
  }

  planStructureSets(ids, center, seed, options = {}) { return this.structureSets.allAround(ids, center, seed, options); }
  planSet(id, center, seed, options = {}) { return this.structureSets.planAround(id, center, seed, options); }
  candidateFromStructureSet(id, center, seed, options = {}) {
    const plan = this.planSet(id, center, seed, options);
    return (plan.placements ?? []).map(p => {
      const metadata = this.resolveStructureMetadata(p.structure);
      return { ...p, metadata, structure: p.structure, terrain_adaptation: metadata.terrain_adaptation, heightmap_projection: metadata.heightmap_projection, start_pool: metadata.start_pool };
    });
  }
  terrainContextForCandidate(candidate, host = {}) {
    const metadata = this.resolveStructureMetadata(candidate.structure ?? candidate.id);
    return { ...candidate, ...metadata, host, projection: metadata.heightmap_projection, terrain_adaptation: metadata.terrain_adaptation, start_pool: metadata.start_pool, jigsawJunctions: candidate.jigsawJunctions ?? candidate.junctions ?? [] };
  }
  isNativeSupported(id) { const m = this.resolveStructureMetadata(id); return Boolean(m.native && m.start_pool); }
  shouldUseFallback(id) { return !this.isNativeSupported(id); }
  validate(id) { return this.registry.validateStructure(id); }
  snapshot() { return { registry: this.registry.snapshot(), generatedSchema: this.data.schema_version, missingTemplates: this.data.missing_templates?.length ?? 0, nativeKinds: Object.keys(this.data.native ?? {}) }; }
}

export function createGeneratedWorldgenBridge(options) { return new GeneratedWorldgenBridge(options); }
