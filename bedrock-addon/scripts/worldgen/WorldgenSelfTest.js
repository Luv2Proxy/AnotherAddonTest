import { getGeneratedJigsawData } from "./JigsawDataLoader.js";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { StructureDensityField } from "./StructureDensityField.js";
import { vanillaFallbackIds } from "./VanillaStartPoolFallbacks.js";

const KNOWN_MISSING_TEMPLATE_BUGS = new Set([
  "minecraft:ancient_city/walls/intact_horizontal_wall_stairs_5"
]);

export function runWorldgenSelfTest(options = {}) {
  const data = options.data ?? getGeneratedJigsawData();
  const registry = options.registry ?? new JigsawRegistry(data);
  const errors = [];
  const warnings = [];
  const snapshot = registry.snapshot();
  const deep = options.deep === true;
  const fallbackIds = new Set(vanillaFallbackIds());

  if (!snapshot.pieces) warnings.push("No generated Jigsaw pieces found.");
  if (!snapshot.pools) warnings.push("No generated template pools found.");
  if (!snapshot.structures) warnings.push("No generated Jigsaw structures found.");
  if (!snapshot.structureSets) warnings.push("No generated Structure Sets found.");

  const structures = [];
  const structureIds = Object.keys(data.structures ?? {});
  for (const id of structureIds) {
    try {
      const result = registry.validateStructure(id, 20);
      const fallback = result.fallback ?? null;
      const diagnostic = {
        id,
        valid: result.valid,
        errors: result.errors ?? [],
        fallbackKind: fallback?.kind ?? null,
        fallbackSource: fallback?.source ?? null,
        fallbackAvailable: fallback?.available ?? false,
        startPool: result.startPool ?? fallback?.startPool ?? null,
        rootPiece: fallback?.rootPiece ?? null
      };
      structures.push(diagnostic);

      // Native/legacy vanilla structures do not have Java Jigsaw start_pool
      // fields. They are expected to use the native compatibility adapter.
      if (fallback?.kind === "native") continue;

      for (const error of result.errors ?? []) {
        const match = /missing piece (.+)$/.exec(error);
        if (match && KNOWN_MISSING_TEMPLATE_BUGS.has(match[1])) continue;
        warnings.push(`${id}: ${error}`);
      }
    } catch (e) {
      errors.push(`Structure ${id}: ${String(e)}`);
    }
  }

  // Report fallback coverage without treating absent native root-piece names
  // as fatal: native Bedrock structures can still be handled by the native
  // adapter even when their Java implementation is not a Jigsaw pool graph.
  for (const id of fallbackIds) {
    if (structures.some(x => x.id === id)) continue;
    const fallback = registry.resolveStartPool(id);
    structures.push({
      id,
      valid: fallback.kind === "native" || fallback.available,
      errors: [],
      fallbackKind: fallback.kind,
      fallbackSource: fallback.source,
      fallbackAvailable: fallback.available,
      startPool: fallback.startPool ?? null,
      rootPiece: fallback.rootPiece ?? null
    });
  }

  const sets = [];
  const setIds = Object.keys(data.structure_sets ?? data.structureSets ?? {});
  for (const id of setIds) {
    const definition = registry.structureSet?.(id) ?? data.structure_sets?.[id] ?? data.structureSets?.[id];
    const entries = definition?.structures ?? definition?.elements ?? definition?.entries ?? [];
    if (!entries.length) warnings.push(`${id}: structure set has no entries`);
    sets.push({ id, candidates: null, deferred: true, entries: entries.length, deepRequested: deep });
  }

  const missing = data.missing_templates ?? data.missing ?? [];
  if (missing.length) warnings.push(`${missing.length} missing template records are present; they will be treated as empty/unsupported.`);

  const density = new StructureDensityField();
  return {
    ok: errors.length === 0,
    errors,
    warnings,
    snapshot,
    structures,
    sets,
    density: { boxes: density.boxes.length, junctions: density.junctions.length, sample: density.densityAt(0, 128, 0) },
    generatedSchema: data.schema_version ?? null,
    deep,
    plannerSimulation: "deferred_to_live_runtime"
  };
}
