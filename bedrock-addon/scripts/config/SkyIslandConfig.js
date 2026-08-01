// Native pack settings are defined in manifest.json and are read by Molang.
// This module remains only as a small compatibility layer for generator code
// that may eventually receive settings through supported Bedrock APIs.
export const PACK_SETTING = Object.freeze({
  terrainRelief: "sky_archipelago:terrain_relief",
  surfaceEnabled: "sky_archipelago:surface_enabled",
  vegetationEnabled: "sky_archipelago:vegetation_enabled",
  oceanEnabled: "sky_archipelago:ocean_enabled"
});

export const DEFAULTS = Object.freeze({
  terrainRelief: 1.0,
  surfaceEnabled: true,
  vegetationEnabled: true,
  oceanEnabled: false
});
