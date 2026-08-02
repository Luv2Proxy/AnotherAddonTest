import { StructureCategory } from "./StructureRegistry.js";

const POLICIES = {
  [StructureCategory.SKY]: { minIslandMargin: 8, maxSlope: 0.8, minClearance: 4, allowEdge: false },
  [StructureCategory.SMALL_SKY]: { minIslandMargin: 4, maxSlope: 0.9, minClearance: 2, allowEdge: false },
  [StructureCategory.SURFACE_SKY]: { minIslandMargin: 8, maxSlope: 0.55, minClearance: 5, allowEdge: false },
  [StructureCategory.GROUND_VILLAGE]: { minIslandMargin: 12, maxSlope: 0.3, minClearance: 8, allowEdge: false },
  [StructureCategory.WATER]: { minIslandMargin: 0, maxSlope: 1.0, minClearance: 0, allowEdge: true },
  [StructureCategory.UNDERGROUND]: { minIslandMargin: 6, maxSlope: 0.9, minClearance: 3, allowEdge: false },
  [StructureCategory.STRONGHOLD]: { minIslandMargin: 20, maxSlope: 0.9, minClearance: 12, allowEdge: false },
};

export function getStructurePlacementPolicy(category) {
  return POLICIES[category] ?? POLICIES[StructureCategory.SKY];
}

export function validateIslandPlacement({ category, host, footprintRadius = 4, slope = 0, clearance = Infinity }) {
  const policy = getStructurePlacementPolicy(category);
  if (!host) return { valid: false, reason: "no_host_island", policy };
  const usableRadius = Number(host.usableRadius ?? host.radius ?? 0);
  const margin = Number(host.margin ?? 0);
  const available = usableRadius - margin - policy.minIslandMargin;
  if (!policy.allowEdge && available < footprintRadius) return { valid: false, reason: "insufficient_island_margin", policy, available };
  if (Math.abs(Number(slope)) > policy.maxSlope) return { valid: false, reason: "slope_too_high", policy, slope };
  if (Number(clearance) < policy.minClearance) return { valid: false, reason: "insufficient_clearance", policy, clearance };
  return { valid: true, policy, available };
}
