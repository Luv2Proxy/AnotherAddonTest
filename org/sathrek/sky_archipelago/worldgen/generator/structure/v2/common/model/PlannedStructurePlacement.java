package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model;

import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public record PlannedStructurePlacement(
   IslandPlacementSite site, int dx, int dy, int dz, StructureFootprint finalRawFootprint, StructureFootprint finalEffectiveFootprint
) {
}
