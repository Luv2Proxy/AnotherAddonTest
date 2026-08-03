import { getGeneratedJigsawData } from "./JigsawDataLoader.js";
import { JigsawRegistry } from "./JigsawRegistry.js";
import { GeneratedStructurePlanner } from "./GeneratedStructurePlanner.js";
import { StructureSetPlacementPlanner } from "./StructureSetPlacementPlanner.js";
import { StructureDensityField } from "./StructureDensityField.js";

/**
 * Runtime diagnostics.
 *
 * `deep=false` is safe for startup: it validates generated metadata and structure
 * records but does not invoke the potentially expensive structure-set placement
 * planner. `deep=true` is intended for an explicit diagnostic ScriptEvent.
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
  for (const id of Object.keys(data.structures ?? {})) {
    try {
      const result = registry.validateStructure(id, 20);
      structures.push({ id, valid: result.valid, errors: result.errors ?? [] });
      if (!result.valid) warnings.push(...(result.errors ?? []).slice(0, 10).map(e => `${id}: ${e}`));
    } catch (e) {
      errors.push(`Structure ${id}: ${String(e)}`);
    }
  }

  const sets = [];
  if (deep) {
    // This is deliberately opt-in. The placement planner can perform substantial
    // deterministic search and should never run automatically during world load.
    const placement = new StructureSetPlacementPlanner(registry);
    for (const id of Object.keys(data.structure_sets ?? data.structureSets ?? {})) {
      try {
        const candidates = placement.plan(id, { x: 0, y: 128, z: 0 }, options.seed ?? 0, { radius: 512, count: 2 });
        sets.push({ id, candidates: candidates.length, sample: candidates.slice(0, 2).map(c => ({ structure: c.structure, x: c.x, z: c.z, native: c.native ?? false })) });
      } catch (e) {
        errors.push(`Structure Set ${id}: ${String(e)}`);
      }
    }
  } else {
    for (const id of Object.keys(data.structure_sets ?? data.structureSets ?? {})) sets.push({ id, candidates: null, deferred: true });
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
    deep
  };
}
