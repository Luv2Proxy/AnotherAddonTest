package org.sathrek.sky_archipelago.worldgen.structure.sky;

import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

public record SkyStructurePlacementTarget(
   int x,
   int y,
   int z,
   int topY,
   int topOffset,
   int localOffsetX,
   int localOffsetZ,
   int stableTopCells,
   int groundedSamples,
   double groundedRatio,
   int searchRadiusBlocks,
   IslandField.IslandFamily family,
   IslandField.ClusterHeightBand heightBand
) {
}
