package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public record PlacementResult(
   boolean attempted,
   boolean successful,
   SkyStructurePlacementTarget target,
   String failureReason,
   StructureFootprint rawFootprint,
   StructureFootprint effectiveFootprint,
   ResolvedStructureSupportPlane supportPlane,
   boolean islandCenteredFallbackUsed,
   int qualifiedHosts,
   int attemptedHosts,
   int coarseOffsetsEvaluated,
   int fineOffsetsEvaluated,
   boolean hostAttemptCapHit,
   boolean offsetCapHit
) {
   public static PlacementResult notAttempted() {
      return new PlacementResult(false, false, null, null, null, null, null, false, 0, 0, 0, 0, false, false);
   }

   public static PlacementResult failed(String failureReason) {
      return new PlacementResult(true, false, null, failureReason, null, null, null, false, 0, 0, 0, 0, false, false);
   }

   public static PlacementResult success(SkyStructurePlacementTarget target, StructureFootprint rawFootprint, StructureFootprint effectiveFootprint) {
      return new PlacementResult(true, true, target, null, rawFootprint, effectiveFootprint, null, false, 0, 0, 0, 0, false, false);
   }
}
