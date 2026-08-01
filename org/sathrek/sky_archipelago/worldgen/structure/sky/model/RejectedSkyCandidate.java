package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

public record RejectedSkyCandidate(
   IslandField.IslandPreview preview,
   int targetCenterX,
   int targetCenterZ,
   int localOffsetX,
   int localOffsetZ,
   String rejectionReason,
   int stableTopCells,
   int requiredStableTopCells,
   int groundedSamples,
   int groundingSampleCount,
   double groundedRatio,
   double groundedThreshold,
   int distanceSquared,
   int searchRadiusBlocks
) {
}
