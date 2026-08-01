package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground;

import org.sathrek.sky_archipelago.worldgen.structure.mineshafts.MineshaftPlacementDecision;
import org.sathrek.sky_archipelago.worldgen.structure.underground.UndergroundPlacementDecision;

public final class UndergroundConstraintEvaluator {
   public UndergroundConstraintEvaluator.ConstraintSummary summarize(UndergroundPlacementDecision decision) {
      return new UndergroundConstraintEvaluator.ConstraintSummary(
         decision.prunedNoColumnAtY(),
         decision.prunedOutsideHostDominance(),
         decision.prunedInsufficientOverburden(),
         decision.prunedInsufficientDepth(),
         decision.prunedInsufficientSupport(),
         decision.prunedInsufficientStone()
      );
   }

   public UndergroundConstraintEvaluator.ConstraintSummary summarize(MineshaftPlacementDecision decision) {
      return new UndergroundConstraintEvaluator.ConstraintSummary(
         decision.prunedNoColumnAtY(),
         decision.prunedOutsideHostDominance(),
         decision.prunedInsufficientOverburden(),
         0,
         decision.prunedInsufficientSupport(),
         decision.prunedInsufficientStone()
      );
   }

   public record ConstraintSummary(
      int noColumnAtY, int outsideHostDominance, int insufficientOverburden, int insufficientDepth, int insufficientSupport, int insufficientStone
   ) {
   }
}
