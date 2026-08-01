package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model;

public record StructurePlacementPolicy(
   int searchRadius,
   int minHostRadius,
   int topOffset,
   double groundedRatio,
   int maxTopVariation,
   int margin,
   int minSameStructureSpacingChunks,
   int maxPerHostIsland,
   int maxSameStructurePerHostIsland
) {
}
