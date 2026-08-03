import { getGeneratedJigsawData } from "./JigsawDataLoader.js";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { StructureDensityField } from "./StructureDensityField.js";

/**
 * Worldgen diagnostics intentionally avoid running the procedural placement
 * planner. The planner is a gameplay system and may scan thousands of cells;
 * running it synchronously from a ScriptEvent can trip Bedrock's watchdog.
 *
 * deep=true now means "include bounded diagnostic metadata", not "simulate
 * world generation". Actual generation is exercised by the live runtime.
 */
export function runWorldgenSelfTest(options = {}) {
  const data = options.data ?? getGeneratedJigsawData();
  const registry = options.registry ?? new JigsawRegistry(data);
  const errors = [];
  const warnings = [];
  const snapshot = registry.snapshot();
  const deep = options.deep === true;

  if (!snapshot.pieces) warnings.push("No generated Jigsaw pieces found.");
  if (!snapshot.pools) warnings.push("No generated template pools found.");
  if (!snapshot.structures) warnings.push("No generated Jigsaw structures found.");
  if (!snapshot.structureSets) warnings.push("No generated Structure Sets found.");

  const structures = [];
  const structureIds = Object.keys(data.structures ?? {});
  for (const id of structureIds) {
    try {
      const result = registry.validateStructure(id, 20);
      structures.push({ id, valid: result.valid, errors: result.errors ?? [] });
      if (!result.valid) warnings.push(...(result.errors ?? []).slice(0, 10).map(e => `${id}: ${e}`));
    } catch (e) {
      errors.push(`Structure ${id}: ${String(e)}`);
    }
  }

  const sets = [];
  const setIds = Object.keys(data.structure_sets ?? data.structureSets ?? {});
  for (const id of setIds) {
    const definition = registry.structureSet?.(id) ?? data.structure_sets?.[id] ?? data.structureSets?.[id];
    const entries = definition?.structures ?? definition?.elements ?? definition?.entries ?? [];
    if (!entries.length) warnings.push(`${id}: structure set has no entries`);
    // Do not call StructureSetPlacementPlanner.plan() here. It is a synchronous
    // search operation and is the source of watchdog hangs during diagnostics.
    sets.push({
      id,
      candidates: null,
      deferred: true,
      entries: entries.length,
      deepRequested: deep
    });
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
    density: {
      boxes: density.boxes.length,
      junctions: density.junctions.length,
      sample: density.densityAt(0, 128, 0)
    },
    generatedSchema: data.schema_version ?? null,
    deep,
    plannerSimulation: "deferred_to_live_runtime"
  };
}
