// Vanilla Java 1.20.x/1.21.x compatibility metadata.
// These are NOT synthetic pools. A Jigsaw fallback is used only when the
// generated Bedrock runtime data actually contains the mapped pool.
// Non-Jigsaw vanilla structures use their native/legacy structure path and
// are represented here by their known Java root-piece identifiers for parity
// diagnostics only.

const JIGSAW_START_POOLS = Object.freeze({
  "minecraft:ancient_city": ["minecraft:ancient_city/city_center"],
  "minecraft:bastion_remnant": ["minecraft:bastion/starts"],
  "minecraft:pillager_outpost": ["minecraft:pillager_outpost/base_plates"],
  "minecraft:trail_ruins": ["minecraft:trail_ruins/tower"],
  "minecraft:village_plains": ["minecraft:village/plains/town_centers"],
  "minecraft:village_desert": ["minecraft:village/desert/town_centers"],
  "minecraft:village_savanna": ["minecraft:village/savanna/town_centers"],
  "minecraft:village_snowy": ["minecraft:village/snowy/town_centers"],
  "minecraft:village_taiga": ["minecraft:village/taiga/town_centers"]
});

// These names identify the first/root family used by the Java generator.
// They are never treated as Jigsaw pools. They are used to compare the
// generated JS extraction against the vanilla structure family and to choose
// the native/legacy adapter when the structure is not data-driven Jigsaw.
const NATIVE_ROOT_PIECES = Object.freeze({
  "minecraft:buried_treasure": ["minecraft:buried_treasure"],
  "minecraft:desert_pyramid": ["minecraft:desert_pyramid"],
  "minecraft:end_city": ["minecraft:end_city/base_floor"],
  "minecraft:fortress": ["minecraft:fortress/bridge"],
  "minecraft:igloo": ["minecraft:igloo"],
  "minecraft:jungle_pyramid": ["minecraft:jungle_temple"],
  "minecraft:mineshaft": ["minecraft:mineshaft/corridor"],
  "minecraft:mineshaft_mesa": ["minecraft:mineshaft/corridor"],
  "minecraft:monument": ["minecraft:monument/core"],
  "minecraft:nether_fossil": ["minecraft:nether_fossil/fossil_1"],
  "minecraft:ocean_ruin_cold": ["minecraft:ocean_ruin/cold_ruin_1"],
  "minecraft:ocean_ruin_warm": ["minecraft:ocean_ruin/warm_ruin_1"],
  "minecraft:stronghold": ["minecraft:stronghold/portal_room"],
  "minecraft:swamp_hut": ["minecraft:swamp_hut"],
  "minecraft:shipwreck": ["minecraft:shipwreck"],
  "minecraft:shipwreck_beached": ["minecraft:shipwreck_beached"],
  "minecraft:mansion": ["minecraft:mansion"]
});

export function vanillaStartPoolFallback(structureId) {
  const id = String(structureId ?? "");
  const pools = JIGSAW_START_POOLS[id] ?? [];
  return { structure: id, kind: "jigsaw", javaStartPools: pools.slice() };
}

export function vanillaNativeRootFallback(structureId) {
  const id = String(structureId ?? "");
  const roots = NATIVE_ROOT_PIECES[id] ?? [];
  return { structure: id, kind: "native", javaRootPieces: roots.slice() };
}

export function vanillaFallbackInfo(structureId) {
  const jigsaw = vanillaStartPoolFallback(structureId);
  if (jigsaw.javaStartPools.length) return jigsaw;
  return vanillaNativeRootFallback(structureId);
}

export function vanillaFallbackIds() {
  return [...new Set([...Object.keys(JIGSAW_START_POOLS), ...Object.keys(NATIVE_ROOT_PIECES)])];
}

export function vanillaJigsawFallbacks() { return JIGSAW_START_POOLS; }
export function vanillaNativeFallbacks() { return NATIVE_ROOT_PIECES; }
