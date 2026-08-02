import { StructureCategory } from "./StructureRegistry.js";

const FAMILY_RULES = [
  ["stronghold", StructureCategory.STRONGHOLD], ["mineshaft", StructureCategory.UNDERGROUND], ["ancient_city", StructureCategory.UNDERGROUND], ["trial_chambers", StructureCategory.UNDERGROUND],
  ["monument", StructureCategory.WATER], ["shipwreck", StructureCategory.WATER], ["underwater_ruin", StructureCategory.WATER], ["coral", StructureCategory.WATER],
  ["village/", StructureCategory.GROUND_VILLAGE], ["trail_ruins", StructureCategory.GROUND_VILLAGE], ["pillageroutpost", StructureCategory.SURFACE_SKY], ["igloo", StructureCategory.SMALL_SKY]
];
function idOf(candidate) { return String(candidate?.structure ?? candidate?.id ?? "").toLowerCase(); }
export function classifyStructure(candidate) {
  const explicit = String(candidate?.category ?? "").toUpperCase();
  if (Object.values(StructureCategory).includes(explicit)) return explicit;
  const id = idOf(candidate);
  for (const [needle, category] of FAMILY_RULES) if (id.includes(needle)) return category;
  return StructureCategory.SKY;
}
export function hasJigsawMetadata(registry, candidate) {
  if (candidate?.jigsaw === true || candidate?.assetKind === "jigsaw") return true;
  const id = candidate?.structure ?? candidate?.id, family = candidate?.family ?? id;
  try {
    if (registry?.hasGeneratedJigsaw?.(family) || registry?.hasGeneratedJigsaw?.(id) || registry?.generatedJigsaw?.(id)) return true;
  } catch {}
  return false;
}
export function classifyPlacement(registry, candidate) {
  const category = classifyStructure(candidate), id = idOf(candidate);
  if (candidate?.native === true || category === StructureCategory.STRONGHOLD || id.includes("mineshaft") || id.includes("monument")) return { category, kind: "native" };
  if (hasJigsawMetadata(registry, candidate)) return { category, kind: "jigsaw" };
  return { category, kind: "template" };
}
function adapter(generator, names) { for (const name of names) { const fn = generator?.[name]; if (typeof fn === "function") return fn.bind(generator); } return null; }
export function createPlacementAdapters(generator) {
  return { sky: adapter(generator,["placeSkyStructure","placeSky","placeTemplate"]), surface: adapter(generator,["placeSurfaceStructure","placeSurface","placeTemplate"]), village: adapter(generator,["placeGroundVillage","placeVillage","placeTemplate"]), water: adapter(generator,["placeWaterStructure","placeWater","placeTemplate"]), underground: adapter(generator,["placeUndergroundStructure","placeUnderground","placeTemplate"]), stronghold: adapter(generator,["placeStronghold","placeNative"]), jigsaw: adapter(generator,["placeJigsawStructure","placeJigsaw"]), template: adapter(generator,["placeTemplate"]) };
}
export function adapterFor(adapters, classification) {
  const { category, kind } = classification;
  if (kind === "jigsaw" && adapters.jigsaw) return adapters.jigsaw;
  if (category === StructureCategory.STRONGHOLD && adapters.stronghold) return adapters.stronghold;
  if (category === StructureCategory.UNDERGROUND && adapters.underground) return adapters.underground;
  if (category === StructureCategory.WATER && adapters.water) return adapters.water;
  if (category === StructureCategory.GROUND_VILLAGE && adapters.village) return adapters.village;
  if (category === StructureCategory.SURFACE_SKY && adapters.surface) return adapters.surface;
  return adapters.sky ?? adapters.template;
}
