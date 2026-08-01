// Pack settings are authored in the native manifest settings UI.
// The scripting API does not expose these values directly; this module provides
// the canonical setting IDs used by resource-pack Molang and documents the
// defaults used by the server-side generator.
export const PACK_SETTINGS = Object.freeze({
  terrainRelief: "sky_archipelago:terrain_relief",
  surfaceEnabled: "sky_archipelago:surface_enabled",
  vegetationEnabled: "sky_archipelago:vegetation_enabled",
  oceanEnabled: "sky_archipelago:ocean_enabled"
});
