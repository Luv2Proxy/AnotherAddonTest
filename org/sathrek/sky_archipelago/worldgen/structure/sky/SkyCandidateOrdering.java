package org.sathrek.sky_archipelago.worldgen.structure.sky;

import java.util.Comparator;
import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.SkyCandidate;

public final class SkyCandidateOrdering {
   public Comparator<SkyCandidate> orderingFor(ResourceLocation structureId) {
      int structureTieBreak = structureId == null ? 0 : structureId.hashCode();
      return Comparator.comparingInt(SkyCandidate::bandPriority)
         .reversed()
         .thenComparingInt(SkyCandidate::familyPriority)
         .reversed()
         .thenComparingDouble(SkyCandidate::groundedRatio)
         .reversed()
         .thenComparingInt(SkyCandidate::groundedSamples)
         .reversed()
         .thenComparingInt(SkyCandidate::stableTopCells)
         .reversed()
         .thenComparingInt(SkyCandidate::movementCost)
         .thenComparingInt(SkyCandidate::localOffsetX)
         .thenComparingInt(SkyCandidate::localOffsetZ)
         .thenComparingInt(SkyCandidate::topY)
         .thenComparingInt(candidate -> structureTieBreak);
   }
}
