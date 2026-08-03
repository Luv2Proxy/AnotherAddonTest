import { StructureCategory } from "./StructureRegistry.js";
import { computeTerrainAdaptation, projectHeight } from "./StructurePlacementPolicies.js";

function call(generator, names, args) { for (const name of names) if (typeof generator?.[name] === "function") return generator[name](...args); return null; }
function candidateId(candidate) { return candidate?.structure ?? candidate?.id ?? candidate?.template ?? candidate?.name; }
function dimensionOf(context) { return context.dimension ?? context.host?.dimension ?? context.generator?.dimension ?? null; }

export class CategoryPlacementEngines {
  constructor(generator) { this.generator = generator; }

  async sky(candidate, context) {
    return call(this.generator, ["placeSkyStructure", "placeSky", "placeTemplate"], [candidateId(candidate), context.dimension, context.location, context.options, context.placementKey]);
  }

  async water(candidate, context) {
    const host = context.host ?? {};
    const adaptation = computeTerrainAdaptation({ category: StructureCategory.WATER, candidate, host, location: context.location });
    const waterLevel = Number(host.waterLevel ?? host.seaLevel ?? 63);
    const floorY = Number(host.seaFloorY ?? host.floorY ?? context.location.y ?? waterLevel);
    const projection = String(candidate?.heightmap_projection ?? candidate?.heightmapProjection ?? "").toLowerCase();
    const depth = Number(candidate?.waterDepth ?? candidate?.depth ?? 0);
    const y = projection === "sea_floor" ? floorY : Math.max(floorY, waterLevel - depth);
    // Terrain work is scheduled by StructurePlacementCoordinator after the
    // structure has been placed, so we do not synchronously scan blocks here.
    return call(this.generator, ["placeWaterStructure", "placeWater", "placeTemplate"], [candidateId(candidate), context.dimension, { ...context.location, y }, { ...context.options, waterlogged: true, waterLevel, seaFloorY: floorY, terrainAdaptation: adaptation, terrainOperations: { scheduledByCoordinator: true } }, context.placementKey]);
  }

  async underground(candidate, context) {
    const host = context.host ?? {};
    const adaptation = computeTerrainAdaptation({ category: StructureCategory.UNDERGROUND, candidate, host, location: context.location });
    const projection = String(candidate?.heightmap_projection ?? candidate?.heightmapProjection ?? "").toLowerCase();
    let y = context.location.y;
    if (projection === "world_surface" || projection === "world_surface_wg") y = projectHeight(candidate, host, StructureCategory.UNDERGROUND);
    if (adaptation.mode === "bury") y += Number(candidate?.buryDepth ?? candidate?.depth ?? 0);
    return call(this.generator, ["placeUndergroundStructure", "placeUnderground", "placeTemplate"], [candidateId(candidate), context.dimension, { ...context.location, y }, { ...context.options, underground: true, terrainAdaptation: adaptation, terrainOperations: { scheduledByCoordinator: true } }, context.placementKey]);
  }

  async village(candidate, context) {
    const host = context.host ?? {};
    const adaptation = computeTerrainAdaptation({ category: StructureCategory.GROUND_VILLAGE, candidate, host, location: context.location });
    const y = adaptation.targetY;
    return call(this.generator, ["placeGroundVillage", "placeVillage", "placeTemplate"], [candidateId(candidate), context.dimension, { ...context.location, y }, { ...context.options, village: true, terrainAdaptation: adaptation, terrainOperations: { scheduledByCoordinator: true }, flattenTerrain: true, foundationMode: adaptation.foundation }, context.placementKey]);
  }

  async stronghold(candidate, context) {
    return call(this.generator, ["placeStronghold", "placeNative", "placeTemplate"], [candidateId(candidate), context.dimension, context.location, { ...context.options, stronghold: true }, context.placementKey]);
  }

  async place(category, candidate, context) {
    switch (category) {
      case StructureCategory.WATER: return this.water(candidate, context);
      case StructureCategory.UNDERGROUND: return this.underground(candidate, context);
      case StructureCategory.GROUND_VILLAGE: return this.village(candidate, context);
      case StructureCategory.STRONGHOLD: return this.stronghold(candidate, context);
      default: return this.sky(candidate, context);
    }
  }
}
