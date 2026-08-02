import generatedData from "./generated/jigsaw-data.js";

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

export function getGeneratedJigsawData() { return GENERATED_JIGSAW_DATA; }

export function generatedTemplateId(id) {
  if (!id) return null;
  const value = String(id).replace(/\\/g, "/").replace(/\.mcstructure$/i, "").replace(/\.nbt$/i, "");
  return value.includes(":") ? value : `minecraft:${value}`;
}

export function generatedPiece(id) {
  const key = generatedTemplateId(id);
  if (!key) return null;
  return GENERATED_JIGSAW_DATA.pieces[key] ?? GENERATED_JIGSAW_DATA.pieces[String(id)] ?? null;
}

export function generatedPool(id) {
  const key = generatedTemplateId(id);
  if (!key) return null;
  const value = GENERATED_JIGSAW_DATA.template_pools[key] ?? GENERATED_JIGSAW_DATA.template_pools[String(id)];
  return value?.definition ?? value ?? null;
}

export function generatedStructure(id) {
  const key = generatedTemplateId(id);
  if (!key) return null;
  const value = GENERATED_JIGSAW_DATA.structures[key] ?? GENERATED_JIGSAW_DATA.jigsaw_structures[key] ?? GENERATED_JIGSAW_DATA.structures[String(id)] ?? GENERATED_JIGSAW_DATA.jigsaw_structures[String(id)];
  return value?.definition ?? value ?? null;
}

export function generatedProcessor(id) {
  const key = generatedTemplateId(id);
  if (!key) return null;
  const value = GENERATED_JIGSAW_DATA.processors[key] ?? GENERATED_JIGSAW_DATA.processors[String(id)];
  return value?.definition ?? value ?? null;
}

export function generatedStructureSet(id) {
  const key = generatedTemplateId(id);
  if (!key) return null;
  const value = GENERATED_JIGSAW_DATA.structure_sets[key] ?? GENERATED_JIGSAW_DATA.structure_sets[String(id)];
  return value?.definition ?? value ?? null;
}

export function generatedResolvedTemplatePath(id) {
  const key = generatedTemplateId(id);
  if (!key) return null;
  return GENERATED_JIGSAW_DATA.resolved_templates[key] ?? GENERATED_JIGSAW_DATA.template_aliases[key]?.source ?? null;
}

export function generatedAlias(id) {
  const key = generatedTemplateId(id);
  return key ? GENERATED_JIGSAW_DATA.template_aliases[key] ?? null : null;
}
