package org.sathrek.sky_archipelago.worldgen.generator.field.internal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.config.TerrainOverlapMode;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;

final class IslandOverlapResolver {
   private static final int MIN_LOWER_SEGMENT_THICKNESS = 4;
   private static final int MIN_UPPER_LOWER_GAP = 8;
   private static final int MIN_CRATER_DEPTH = 2;
   private static final int MAX_CRATER_DEPTH = 28;

   List<TerrainColumn> resolve(TerrainOverlapMode mode, List<IslandOverlapResolver.SegmentCandidate> rawSegments) {
      if (rawSegments.isEmpty()) {
         return List.of();
      }

      return switch (mode) {
         case VOID -> List.of(rawSegments.get(0).segment());
         case OVERLAP -> rawSegments.stream().map(IslandOverlapResolver.SegmentCandidate::segment).toList();
         case CRATER -> this.craterCut(rawSegments);
      };
   }

   private List<TerrainColumn> craterCut(List<IslandOverlapResolver.SegmentCandidate> rawSegments) {
      IslandOverlapResolver.SegmentCandidate upper = rawSegments.get(0);
      ArrayList<TerrainColumn> resolved = new ArrayList<>();
      resolved.add(upper.segment());

      for (int index = 1; index < rawSegments.size(); index++) {
         IslandOverlapResolver.SegmentCandidate lower = rawSegments.get(index);
         if (this.isSeparateLowerIsland(upper, lower)) {
            int craterDepth = this.craterDepth(upper);
            int cutTopY = lower.segment().topY() - craterDepth;
            TerrainColumn cut = new TerrainColumn(lower.segment().bottomY(), cutTopY);
            if (cut.thickness() >= 4) {
               resolved.add(cut);
            }
         }
      }

      return List.copyOf(resolved);
   }

   private boolean isSeparateLowerIsland(IslandOverlapResolver.SegmentCandidate upper, IslandOverlapResolver.SegmentCandidate lower) {
      if (upper.descriptor() != null && lower.descriptor() != null) {
         return upper.descriptor().equals(lower.descriptor()) ? false : upper.segment().bottomY() - lower.segment().topY() >= 8;
      } else {
         return false;
      }
   }

   private int craterDepth(IslandOverlapResolver.SegmentCandidate upper) {
      double coverage = Math.max(upper.horizontal().coverage(), upper.horizontal().branchCoverage() * 0.75);
      double normalized = Mth.clamp((coverage - 0.04) / 0.86, 0.0, 1.0);
      double falloff = normalized * normalized * (3.0 - 2.0 * normalized);
      int depthCap = Mth.clamp(upper.segment().thickness() / 2, 2, 28);
      return Mth.clamp(Mth.ceil(depthCap * falloff), 2, 28);
   }

   record SegmentCandidate(TerrainColumn segment, IslandField.IslandDescriptor descriptor, IslandField.HorizontalSample horizontal) {
   }
}
