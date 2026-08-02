import { StructureCategory } from "./StructureRegistry.js";
import { computeTerrainAdaptation, projectHeight } from "./StructurePlacementPolicies.js";

function call(generator, names, args) { for (const name of names) if (typeof generator?.[name] === "function") return generator[name](...args); return null; }

export class CategoryPlacementEngines {
  constructor(generator) { this.generator = generator; }
  async sky(candidate, context) { return call(this.generator, ["placeSkyStructure", "placeSky", "placeTemplate"], [candidate.structure ?? candidate.id, context.dimension, context.location, context.options, context.placementKey]); }

  async water(candidate, context) {
    const host = context.host ?? {};
    const adaptation = computeTerrainAdaptation({ category: StructureCategory.WATER, candidate, host, location: context.location });
    const waterLevel = Number(host.waterLevel ?? host.seaLevel ?? 63), floorY = Number(host.seaFloorY ?? host.floorY ?? context.location.y ?? waterLevel);
    const location = { ...context.location, y: candidate?.heightmap_projection === "sea_floor" ? floorY : Math.max(floorY, waterLevel - Number(candidate?.waterDepth ?? 0)) };
    return call(this.generator, ["placeWaterStructure", "placeWater", "placeTemplate"], [candidate.structure ?? candidate.id, context.dimension, location, { ...context.options, waterlogged: true, waterLevel, seaFloorY: floorY, terrainAdaptation: adaptation }, context.placementKey]);
  }

  async underground(candidate, context) {
    const adaptation = computeTerrainAdaptation({ category: StructureCategory.UNDERGROUND, candidate, host: context.host ?? {}, location: context.location });
    const location = { ...context.location, y: candidate?.heightmap_projection === "world_surface" ? projectHeight(candidate, context.host ?? {}, StructureCategory.UNDERGROUND) : context.location.y };
    return call(this.generator, ["placeUndergroundStructure", "placeUnderground", "placeTemplate"], [candidate.structure ?? candidate.id, context.dimension, location, { ...context.options, underground: true, terrainAdaptation: adaptation }, context.placementKey]);
  }

  async village(candidate, context) {
    const adaptation = computeTerrainAdaptation({ category: StructureCategory.GROUND_VILLAGE, candidate, host: context.host ?? {}, location: context.location });
    const location = { ...context.location, y: adaptation.targetY };
    return call(this.generator, ["placeGroundVillage", "placeVillage", "placeTemplate"], [candidate.structure ?? candidate.id, context.dimension, location, { ...context.options, village: true, terrainAdaptation: adaptation, flattenTerrain: true, foundationMode: "beard_thin" }, context.placementKey]);
  }

  async stronghold(candidate, context) { return call(this.generator, ["placeStronghold", "placeNative", "placeTemplate"], [candidate.structure ?? candidate.id, context.dimension, context.location, { ...context.options, stronghold: true }, context.placementKey]); }

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
