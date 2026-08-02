import generatedData from "./generated/jigsaw-data.js";

/**
 * Runtime access to the build-generated vanilla jigsaw registry.
 *
 * Bedrock Script modules cannot rely on Node's filesystem APIs or arbitrary
 * JSON-file loading at runtime. The importer therefore emits a JavaScript
 * module beside the human-readable JSON artifacts. This loader normalizes the
 * generated schema and provides a single source for all worldgen systems.
 */
export function normalizeGeneratedJigsawData(data = generatedData) {
  const source = data && typeof data === "object" ? data : {};
  return {
    schema_version: source.schema_version ?? 0,
    template_pools: source.template_pools ?? source.pools ?? {},
    structures: source.structures ?? source.jigsaw_structures ?? {},
    jigsaw_structures: source.jigsaw_structures ?? source.structures ?? {},
    processors: source.processors ?? {},
    structure_sets: source.structure_sets ?? source.structureSets ?? {},
    pieces: source.pieces ?? {},
    resolved_templates: source.resolved_templates ?? {},
    template_aliases: source.template_aliases ?? {},
    connectors: source.connectors ?? source.jigsaw_connectors ?? {},
    metadata: source.metadata ?? {}
  };
}

export const GENERATED_JIGSAW_DATA = Object.freeze(normalizeGeneratedJigsawData());

export function getGeneratedJigsawData() {
  return GENERATED_JIGSAW_DATA;
}

export function generatedTemplateId(id) {
  if (!id) return null;
  const value = String(id).replace(/\\/g, "/").replace(/\\.mcstructure$/i, "").replace(/\\.nbt$/i, "");
  return value.includes(":") ? value : `minecraft:${value}`;
}

export function generatedPiece(id) {
  const data = GENERATED_JIGSAW_DATA;
  const key = generatedTemplateId(id);
  if (!key) return null;
  return data.pieces[key] ?? data.pieces[String(id)] ?? null;
}

export function generatedPool(id) {
  const data = GENERATED_JIGSAW_DATA;
  const key = generatedTemplateId(id);
  if (!key) return null;
  const value = data.template_pools[key] ?? data.template_pools[String(id)];
  return value?.definition ?? value ?? null;
}

export function generatedStructure(id) {
  const data = GENERATED_JIGSAW_DATA;
  const key = generatedTemplateId(id);
  if (!key) return null;
  const value = data.structures[key] ?? data.jigsaw_structures[key] ?? data.structures[String(id)] ?? data.jigsaw_structures[String(id)];
  return value?.definition ?? value ?? null;
}

export function generatedResolvedTemplatePath(id) {
  const data = GENERATED_JIGSAW_DATA;
  const key = generatedTemplateId(id);
  return key ? data.resolved_templates[key] ?? null : null;
}
