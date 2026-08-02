import { StructureCategory } from "./StructureRegistry.js";

export function placementPolicy(category) {
  switch (category) {
    case StructureCategory.STRONGHOLD:
      return { mode: "native", yMode: "underground", requireHost: true, maxSlope: 0.9 };
    case StructureCategory.UNDERGROUND:
      return { mode: "underground", yMode: "below_surface", requireHost: true, maxSlope: 0.8 };
    case StructureCategory.WATER:
      return { mode: "water", yMode: "waterline", requireHost: false, maxSlope: 1.0 };
    case StructureCategory.GROUND_VILLAGE:
      return { mode: "village", yMode: "surface", requireHost: true, maxSlope: 0.35 };
    case StructureCategory.SURFACE_SKY:
      return { mode: "surface", yMode: "surface", requireHost: true, maxSlope: 0.6 };
    case StructureCategory.SMALL_SKY:
      return { mode: "sky", yMode: "surface", requireHost: true, maxSlope: 0.7 };
    default:
      return { mode: "sky", yMode: "surface", requireHost: true, maxSlope: 0.8 };
  }
}

export function applyPlacementPolicy(candidate, category, location) {
  const policy = placementPolicy(category);
  const result = { ...location };
  const surface = Number(candidate?.surfaceY ?? candidate?.terrainY ?? location?.y ?? 128);
  if (policy.yMode === "below_surface") result.y = Math.max(-32, Math.floor(surface - Number(candidate?.depth ?? 24)));
  else if (policy.yMode === "underground") result.y = Math.max(-32, Math.floor(surface - Number(candidate?.depth ?? 24)));
  else if (policy.yMode === "waterline") result.y = Math.max(0, Math.floor(candidate?.waterY ?? surface - 1));
  else result.y = Math.floor(candidate?.y ?? surface);
  return { location: result, policy };
}
