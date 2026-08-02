import { StructureCategory } from "./StructureRegistry.js";

function call(generator, names, args) {
  for (const name of names) {
    if (typeof generator?.[name] === "function") return generator[name](...args);
  }
  return null;
}

export class CategoryPlacementEngines {
  constructor(generator) { this.generator = generator; }

  async sky(candidate, context) {
    return call(this.generator, ["placeSkyStructure", "placeSky", "placeTemplate"], [candidate.structure ?? candidate.id, context.dimension, context.location, context.options, context.placementKey]);
  }

  async water(candidate, context) {
    return call(this.generator, ["placeWaterStructure", "placeWater", "placeTemplate"], [candidate.structure ?? candidate.id, context.dimension, context.location, { ...context.options, waterlogged: true }, context.placementKey]);
  }

  async underground(candidate, context) {
    return call(this.generator, ["placeUndergroundStructure", "placeUnderground", "placeTemplate"], [candidate.structure ?? candidate.id, context.dimension, context.location, { ...context.options, underground: true }, context.placementKey]);
  }

  async village(candidate, context) {
    return call(this.generator, ["placeGroundVillage", "placeVillage", "placeTemplate"], [candidate.structure ?? candidate.id, context.dimension, context.location, { ...context.options, village: true }, context.placementKey]);
  }

  async stronghold(candidate, context) {
    return call(this.generator, ["placeStronghold", "placeNative", "placeTemplate"], [candidate.structure ?? candidate.id, context.dimension, context.location, { ...context.options, stronghold: true }, context.placementKey]);
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
