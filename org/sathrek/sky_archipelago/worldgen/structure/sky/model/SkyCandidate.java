package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public record SkyCandidate(
   IslandField.IslandPreview preview,
   StructureFootprint rawFootprint,
   StructureFootprint effectiveFootprint,
   int targetCenterX,
   int targetCenterZ,
   int localOffsetX,
   int localOffsetZ,
   int stableTopCells,
   int groundedSamples,
   double groundedRatio,
   int distanceSquared,
   int topY
) {
   public int movementCost() {
      return Math.abs(this.localOffsetX) + Math.abs(this.localOffsetZ);
   }

   public int bandPriority() {
      return switch (this.preview.heightBand()) {
         case MID_HIGH -> 3;
         case VERY_HIGH -> 2;
         case LOW -> 1;
      };
   }

   public int familyPriority() {
      return switch (this.preview.family()) {
         case ANCHOR_PLATEAU -> 2;
         case SATELLITE -> 1;
         case SPIRE -> 0;
      };
   }
}
