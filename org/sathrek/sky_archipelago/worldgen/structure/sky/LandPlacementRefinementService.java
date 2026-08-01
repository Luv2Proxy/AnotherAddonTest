package org.sathrek.sky_archipelago.worldgen.structure.sky;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandBudget;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandCandidateEvaluation;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandRefinementResult;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandSizeTier;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandSupportMask;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LocalOffset;

public final class LandPlacementRefinementService {
   private static final double LAND_MIN_RELOCATE_IMPROVEMENT = 0.05;
   private final LandSupportMaskFactory landSupportMaskFactory;
   private final LandCandidateEvaluator landCandidateEvaluator;
   private final LocalOffsetSampler localOffsetSampler;
   private final LandRejectionClassifier rejectionClassifier;

   public LandPlacementRefinementService(
      LandSupportMaskFactory landSupportMaskFactory,
      LandCandidateEvaluator landCandidateEvaluator,
      LocalOffsetSampler localOffsetSampler,
      LandRejectionClassifier rejectionClassifier
   ) {
      this.landSupportMaskFactory = landSupportMaskFactory;
      this.landCandidateEvaluator = landCandidateEvaluator;
      this.localOffsetSampler = localOffsetSampler;
      this.rejectionClassifier = rejectionClassifier;
   }

   public LandRefinementResult refineLandPlacement(StructureSupportContext context, StructureStart structureStart, ResolvedStructureSupportPlane supportPlane) {
      StructureFootprint rawFootprint = supportPlane.rawFootprint();
      StructureFootprint effectiveFootprint = supportPlane.effectiveFootprint();
      LandSizeTier sizeTier = LandSizeTier.forArea(effectiveFootprint.area());
      LandBudget budget = sizeTier.budget();
      LandSupportMask supportMask = this.landSupportMaskFactory.buildLandSupportMask(structureStart, rawFootprint, effectiveFootprint, budget, context);
      LandEvaluationCache evaluationCache = new LandEvaluationCache();
      int originCenterX = rawFootprint.centerX();
      int originCenterZ = rawFootprint.centerZ();
      TerrainColumn originColumn = evaluationCache.sampleColumn(context, originCenterX, originCenterZ);
      int originTopY = originColumn.exists() ? originColumn.topY() : supportPlane.baseY();
      int originBaseY = supportPlane.baseY();
      LandCandidateEvaluation origin = this.landCandidateEvaluator
         .evaluateLandCandidate(
            context, rawFootprint, effectiveFootprint, originCenterX, originCenterZ, originBaseY, originTopY, 0, 0, budget, supportMask, evaluationCache
         );
      LandCandidateEvaluation best = origin.repairable() ? origin : null;
      LandCandidateEvaluation bestObserved = origin;
      int samplesEvaluated = 0;
      boolean originOnlyEvaluated = true;
      if (sizeTier != LandSizeTier.SMALL) {
         for (LandCandidateEvaluation anchorCandidate : this.mediumLargeOriginAnchors(
            originCenterX, originCenterZ, originBaseY, originTopY, budget, context, rawFootprint, effectiveFootprint, supportMask, evaluationCache
         )) {
            originOnlyEvaluated = false;
            samplesEvaluated++;
            if (bestObserved == null || anchorCandidate.score() > bestObserved.score()) {
               bestObserved = anchorCandidate;
            }

            if (anchorCandidate.repairable() && (best == null || anchorCandidate.score() > best.score())) {
               best = anchorCandidate;
            }
         }
      }

      for (LocalOffset offset : this.localOffsetSampler.localOffsets(budget.searchRadiusBlocks(), budget.searchStepBlocks(), budget.fineSearchStepBlocks())) {
         int centerX = originCenterX + offset.offsetX();
         int centerZ = originCenterZ + offset.offsetZ();
         TerrainColumn column = evaluationCache.sampleColumn(context, centerX, centerZ);
         if (column.exists()) {
            originOnlyEvaluated = false;
            int candidateTopY = column.topY();
            int candidateBaseY = originBaseY + (candidateTopY - originTopY);
            if (Math.abs(candidateBaseY - originBaseY) <= budget.maxVerticalAdjustment()) {
               LandCandidateEvaluation candidate = this.landCandidateEvaluator
                  .evaluateLandCandidate(
                     context,
                     rawFootprint,
                     effectiveFootprint,
                     centerX,
                     centerZ,
                     candidateBaseY,
                     candidateTopY,
                     offset.offsetX(),
                     offset.offsetZ(),
                     budget,
                     supportMask,
                     evaluationCache
                  );
               samplesEvaluated++;
               if (bestObserved == null || candidate.score() > bestObserved.score()) {
                  bestObserved = candidate;
               }

               if (candidate.repairable() && (best == null || candidate.score() > best.score())) {
                  best = candidate;
               }
            }
         }
      }

      boolean usedSecondarySweep = false;
      if (best == null && sizeTier != LandSizeTier.SMALL && bestObserved != null && this.rejectionClassifier.isNearPassCandidate(bestObserved, budget)) {
         for (LandCandidateEvaluation retryCandidate : this.secondarySweepCandidates(
            bestObserved, originBaseY, originTopY, context, rawFootprint, effectiveFootprint, budget, supportMask, evaluationCache
         )) {
            usedSecondarySweep = true;
            samplesEvaluated++;
            if (retryCandidate.score() > bestObserved.score()) {
               bestObserved = retryCandidate;
            }

            if (retryCandidate.repairable() && (best == null || retryCandidate.score() > best.score())) {
               best = retryCandidate;
            }
         }
      }

      if (best == null) {
         String legacyOutcome = bestObserved != null ? bestObserved.outcome() : "rejected_no_candidate";
         String primaryBudgetCounter = bestObserved != null ? bestObserved.primaryBudgetCounter() : "none";
         String outcome = this.rejectionClassifier.rejectionTaxonomy(origin, legacyOutcome, primaryBudgetCounter, samplesEvaluated);
         return LandRefinementResult.rejected(
            outcome,
            legacyOutcome,
            sizeTier,
            origin.outcome(),
            origin.score(),
            bestObserved != null ? bestObserved.outcome() : "none",
            bestObserved != null ? bestObserved.score() : origin.score(),
            bestObserved != null ? bestObserved.score() - origin.score() : 0.0,
            samplesEvaluated,
            originOnlyEvaluated,
            primaryBudgetCounter,
            bestObserved,
            usedSecondarySweep
         );
      } else {
         double improvement = best.score() - (origin.repairable() ? origin.score() : -1.0);
         boolean moved = Math.abs(best.offsetX()) > 0 || Math.abs(best.offsetZ()) > 0;
         if (moved && !(improvement < 0.05)) {
            SkyStructurePlacementTarget target = new SkyStructurePlacementTarget(
               best.centerX(),
               best.baseY(),
               best.centerZ(),
               best.topY(),
               best.baseY() - best.topY(),
               best.offsetX(),
               best.offsetZ(),
               best.stableCells(),
               best.groundedCells(),
               best.groundedRatio(),
               budget.searchRadiusBlocks(),
               IslandField.IslandFamily.ANCHOR_PLATEAU,
               IslandField.ClusterHeightBand.MID_HIGH
            );
            return LandRefinementResult.acceptedMoved(
               best, target, rawFootprint, effectiveFootprint, supportPlane, samplesEvaluated, improvement, sizeTier, origin, bestObserved, originOnlyEvaluated
            );
         } else {
            return LandRefinementResult.acceptedNoMove(best, samplesEvaluated, sizeTier, origin, bestObserved, originOnlyEvaluated);
         }
      }
   }

   private List<LandCandidateEvaluation> mediumLargeOriginAnchors(
      int originCenterX,
      int originCenterZ,
      int originBaseY,
      int originTopY,
      LandBudget budget,
      StructureSupportContext context,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      LandSupportMask supportMask,
      LandEvaluationCache evaluationCache
   ) {
      int anchorNear = Math.max(4, budget.searchRadiusBlocks() / 4);
      int anchorFar = Math.max(anchorNear + budget.searchStepBlocks(), budget.searchRadiusBlocks() / 2);
      int[][] anchorOffsets = new int[][]{
         {anchorNear, 0},
         {-anchorNear, 0},
         {0, anchorNear},
         {0, -anchorNear},
         {anchorNear, anchorNear},
         {anchorNear, -anchorNear},
         {-anchorNear, anchorNear},
         {-anchorNear, -anchorNear},
         {anchorFar, 0},
         {-anchorFar, 0},
         {0, anchorFar},
         {0, -anchorFar}
      };
      List<LandCandidateEvaluation> results = new ArrayList<>();

      for (int[] pair : anchorOffsets) {
         int centerX = originCenterX + pair[0];
         int centerZ = originCenterZ + pair[1];
         TerrainColumn column = evaluationCache.sampleColumn(context, centerX, centerZ);
         if (column.exists()) {
            int candidateTopY = column.topY();
            int candidateBaseY = originBaseY + (candidateTopY - originTopY);
            if (Math.abs(candidateBaseY - originBaseY) <= budget.maxVerticalAdjustment()) {
               results.add(
                  this.landCandidateEvaluator
                     .evaluateLandCandidate(
                        context,
                        rawFootprint,
                        effectiveFootprint,
                        centerX,
                        centerZ,
                        candidateBaseY,
                        candidateTopY,
                        pair[0],
                        pair[1],
                        budget,
                        supportMask,
                        evaluationCache
                     )
               );
            }
         }
      }

      return results;
   }

   private List<LandCandidateEvaluation> secondarySweepCandidates(
      LandCandidateEvaluation baseCandidate,
      int originBaseY,
      int originTopY,
      StructureSupportContext context,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      LandBudget budget,
      LandSupportMask supportMask,
      LandEvaluationCache evaluationCache
   ) {
      List<LandCandidateEvaluation> evaluations = new ArrayList<>();
      int sweepRadius = Math.max(8, budget.searchStepBlocks() * 2);
      int sweepStep = Math.max(2, budget.fineSearchStepBlocks());
      int maxSamples = 16;

      for (LocalOffset offset : this.localOffsetSampler.localOffsets(sweepRadius, sweepStep, 0)) {
         if (evaluations.size() >= maxSamples) {
            break;
         }

         int centerX = baseCandidate.centerX() + offset.offsetX();
         int centerZ = baseCandidate.centerZ() + offset.offsetZ();
         TerrainColumn column = evaluationCache.sampleColumn(context, centerX, centerZ);
         if (column.exists()) {
            int candidateTopY = column.topY();
            int candidateBaseY = originBaseY + (candidateTopY - originTopY);
            if (Math.abs(candidateBaseY - originBaseY) <= budget.maxVerticalAdjustment() + 2) {
               evaluations.add(
                  this.landCandidateEvaluator
                     .evaluateLandCandidate(
                        context,
                        rawFootprint,
                        effectiveFootprint,
                        centerX,
                        centerZ,
                        candidateBaseY,
                        candidateTopY,
                        centerX - rawFootprint.centerX(),
                        centerZ - rawFootprint.centerZ(),
                        budget,
                        supportMask,
                        evaluationCache
                     )
               );
            }
         }
      }

      return evaluations;
   }
}
