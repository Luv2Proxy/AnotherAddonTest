package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground;

import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public final class UndergroundCandidatePlanner {
   public UndergroundCandidatePlanner.AnchorHint anchorHint(SkyStructurePlacementTarget islandAwareTarget, int fallbackX, int fallbackZ) {
      int preferredX = islandAwareTarget != null ? islandAwareTarget.x() : fallbackX;
      int preferredZ = islandAwareTarget != null ? islandAwareTarget.z() : fallbackZ;
      return new UndergroundCandidatePlanner.AnchorHint(preferredX, preferredZ);
   }

   public record AnchorHint(int preferredX, int preferredZ) {
   }
}
