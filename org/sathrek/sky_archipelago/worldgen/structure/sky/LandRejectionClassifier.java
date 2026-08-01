package org.sathrek.sky_archipelago.worldgen.structure.sky;

import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandBudget;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandCandidateEvaluation;

public final class LandRejectionClassifier {
   public String rejectionTaxonomy(LandCandidateEvaluation origin, String legacyOutcome, String primaryBudgetCounter, int samplesEvaluated) {
      if (legacyOutcome.equals("rejected_core_missing_support")
         || legacyOutcome.equals("rejected_large_core_void")
         || legacyOutcome.equals("rejected_thin_island")) {
         return "rejected_origin_core";
      } else if (legacyOutcome.equals("rejected_budget_exceeded") || legacyOutcome.equals("rejected_steep_slope_budget")) {
         return "rejected_budget_primary_" + primaryBudgetCounter;
      } else {
         return !origin.repairable() && samplesEvaluated <= 0 ? "rejected_origin_core" : "rejected_search_no_repairable";
      }
   }

   public boolean isNearPassCandidate(LandCandidateEvaluation candidate, LandBudget budget) {
      if (candidate.repairable()) {
         return false;
      }

      if (candidate.unsupportedCoreCells() > budget.maxUnsupportedCoreCells()) {
         return false;
      }

      return switch (candidate.primaryBudgetCounter()) {
         case "edge_extension" -> candidate.edgeExtension() == budget.maxEdgeExtension() + 1;
         case "vertical_adjustment" -> Math.abs(candidate.verticalAdjustment()) <= budget.maxVerticalAdjustment() + 2;
         case "relief_span" -> candidate.reliefSpan() <= budget.maxReliefSpan() + 2;
         case "blocks_to_add" -> candidate.blocksToAdd() <= budget.maxBlocksToAdd() + 12;
         case "blocks_to_remove" -> candidate.blocksToRemove() <= budget.maxBlocksToRemove() + 24;
         default -> false;
      };
   }
}
