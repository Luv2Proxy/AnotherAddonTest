package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public record LandRefinementResult(
   boolean attempted,
   boolean accepted,
   boolean moved,
   String outcome,
   SkyStructurePlacementTarget target,
   StructureFootprint rawFootprint,
   StructureFootprint effectiveFootprint,
   ResolvedStructureSupportPlane supportPlane,
   int blocksToAdd,
   int blocksToRemove,
   int verticalAdjustment,
   int unsupportedCoreCells,
   int edgeExtension,
   int reliefSpan,
   int samplesEvaluated,
   double score,
   double improvement,
   String sizeTier,
   String legacyOutcome,
   String originOutcome,
   double originScore,
   String bestObservedOutcome,
   double bestObservedScore,
   double bestObservedDelta,
   boolean originOnlyEvaluated,
   String primaryBudgetCounter,
   String decisionSource,
   int innerAdjustRadiusBlocks,
   int outerFeatherRadiusBlocks
) {
   public static LandRefinementResult notAttempted(String outcome) {
      return new LandRefinementResult(
         false,
         false,
         false,
         outcome,
         null,
         null,
         null,
         null,
         0,
         0,
         0,
         0,
         0,
         0,
         0,
         0.0,
         0.0,
         "N/A",
         outcome,
         "none",
         0.0,
         "none",
         0.0,
         0.0,
         true,
         "none",
         "not_attempted",
         0,
         0
      );
   }

   public static LandRefinementResult rejected(String outcome) {
      return new LandRefinementResult(
         true,
         false,
         false,
         outcome,
         null,
         null,
         null,
         null,
         0,
         0,
         0,
         0,
         0,
         0,
         0,
         0.0,
         0.0,
         "SMALL",
         outcome,
         "none",
         0.0,
         "none",
         0.0,
         0.0,
         true,
         "none",
         "rejected_missing_support_plane",
         0,
         0
      );
   }

   public static LandRefinementResult rejected(
      String outcome,
      String legacyOutcome,
      LandSizeTier tier,
      String originOutcome,
      double originScore,
      String bestObservedOutcome,
      double bestObservedScore,
      double bestObservedDelta,
      int samplesEvaluated,
      boolean originOnlyEvaluated,
      String primaryBudgetCounter,
      LandCandidateEvaluation bestObserved,
      boolean usedSecondarySweep
   ) {
      int add = bestObserved != null ? bestObserved.blocksToAdd() : 0;
      int remove = bestObserved != null ? bestObserved.blocksToRemove() : 0;
      int vertical = bestObserved != null ? bestObserved.verticalAdjustment() : 0;
      int unsupported = bestObserved != null ? bestObserved.unsupportedCoreCells() : 0;
      int edge = bestObserved != null ? bestObserved.edgeExtension() : 0;
      int relief = bestObserved != null ? bestObserved.reliefSpan() : 0;
      return new LandRefinementResult(
         true,
         false,
         false,
         outcome,
         null,
         null,
         null,
         null,
         add,
         remove,
         vertical,
         unsupported,
         edge,
         relief,
         samplesEvaluated,
         bestObservedScore,
         0.0,
         tier.name(),
         legacyOutcome,
         originOutcome,
         originScore,
         bestObservedOutcome,
         bestObservedScore,
         bestObservedDelta,
         originOnlyEvaluated,
         primaryBudgetCounter,
         usedSecondarySweep ? "secondary_sweep_exhausted" : (samplesEvaluated > 0 ? "search_exhausted" : "origin_rejected"),
         tier == LandSizeTier.LARGE ? tier.budget().innerAdjustRadiusBlocks() : 0,
         tier == LandSizeTier.LARGE ? tier.budget().outerFeatherRadiusBlocks() : 0
      );
   }

   public static LandRefinementResult acceptedNoMove(
      LandCandidateEvaluation candidate,
      int samplesEvaluated,
      LandSizeTier tier,
      LandCandidateEvaluation origin,
      LandCandidateEvaluation bestObserved,
      boolean originOnlyEvaluated
   ) {
      return new LandRefinementResult(
         true,
         true,
         false,
         "accepted_with_repair",
         null,
         null,
         null,
         null,
         candidate.blocksToAdd(),
         candidate.blocksToRemove(),
         candidate.verticalAdjustment(),
         candidate.unsupportedCoreCells(),
         candidate.edgeExtension(),
         candidate.reliefSpan(),
         samplesEvaluated,
         candidate.score(),
         0.0,
         tier.name(),
         candidate.outcome(),
         origin.outcome(),
         origin.score(),
         bestObserved.outcome(),
         bestObserved.score(),
         bestObserved.score() - origin.score(),
         originOnlyEvaluated,
         candidate.primaryBudgetCounter(),
         "origin_or_best_without_move",
         tier == LandSizeTier.LARGE ? tier.budget().innerAdjustRadiusBlocks() : 0,
         tier == LandSizeTier.LARGE ? tier.budget().outerFeatherRadiusBlocks() : 0
      );
   }

   public static LandRefinementResult acceptedMoved(
      LandCandidateEvaluation candidate,
      SkyStructurePlacementTarget target,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      ResolvedStructureSupportPlane supportPlane,
      int samplesEvaluated,
      double improvement,
      LandSizeTier tier,
      LandCandidateEvaluation origin,
      LandCandidateEvaluation bestObserved,
      boolean originOnlyEvaluated
   ) {
      return new LandRefinementResult(
         true,
         true,
         true,
         "accepted_with_repair",
         target,
         rawFootprint,
         effectiveFootprint,
         supportPlane,
         candidate.blocksToAdd(),
         candidate.blocksToRemove(),
         candidate.verticalAdjustment(),
         candidate.unsupportedCoreCells(),
         candidate.edgeExtension(),
         candidate.reliefSpan(),
         samplesEvaluated,
         candidate.score(),
         improvement,
         tier.name(),
         candidate.outcome(),
         origin.outcome(),
         origin.score(),
         bestObserved.outcome(),
         bestObserved.score(),
         bestObserved.score() - origin.score(),
         originOnlyEvaluated,
         candidate.primaryBudgetCounter(),
         "relocated_to_better_site",
         tier == LandSizeTier.LARGE ? tier.budget().innerAdjustRadiusBlocks() : 0,
         tier == LandSizeTier.LARGE ? tier.budget().outerFeatherRadiusBlocks() : 0
      );
   }

   public LandRefinementResult withDecisionSource(String value) {
      return new LandRefinementResult(
         this.attempted,
         this.accepted,
         this.moved,
         this.outcome,
         this.target,
         this.rawFootprint,
         this.effectiveFootprint,
         this.supportPlane,
         this.blocksToAdd,
         this.blocksToRemove,
         this.verticalAdjustment,
         this.unsupportedCoreCells,
         this.edgeExtension,
         this.reliefSpan,
         this.samplesEvaluated,
         this.score,
         this.improvement,
         this.sizeTier,
         this.legacyOutcome,
         this.originOutcome,
         this.originScore,
         this.bestObservedOutcome,
         this.bestObservedScore,
         this.bestObservedDelta,
         this.originOnlyEvaluated,
         this.primaryBudgetCounter,
         value,
         this.innerAdjustRadiusBlocks,
         this.outerFeatherRadiusBlocks
      );
   }
}
