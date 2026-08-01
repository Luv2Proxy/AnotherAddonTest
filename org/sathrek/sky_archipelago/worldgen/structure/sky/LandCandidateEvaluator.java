package org.sathrek.sky_archipelago.worldgen.structure.sky;

import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandBudget;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandCandidateEvaluation;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandSupportMask;

public final class LandCandidateEvaluator {
   public LandCandidateEvaluation evaluateLandCandidate(
      StructureSupportContext context,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      int candidateCenterX,
      int candidateCenterZ,
      int candidateBaseY,
      int candidateTopY,
      int offsetX,
      int offsetZ,
      LandBudget budget,
      LandSupportMask supportMask,
      LandEvaluationCache evaluationCache
   ) {
      StructureFootprint shiftedRaw = rawFootprint.translate(candidateCenterX - rawFootprint.centerX(), candidateCenterZ - rawFootprint.centerZ());
      StructureFootprint shiftedEffective = effectiveFootprint.translate(
         candidateCenterX - effectiveFootprint.centerX(), candidateCenterZ - effectiveFootprint.centerZ()
      );
      StructureFootprint coreFootprint = shiftedEffective.insetByRatio(budget.coreInsetRatio());
      StructureFootprint centralCoreFootprint = shiftedEffective.insetByRatio(budget.centralCoreInsetRatio());
      StructureFootprint innerMargin = shiftedRaw.insetByRatio(budget.innerMarginInsetRatio());
      StructureFootprint innerAdjustZone = expandFootprint(shiftedRaw, budget.innerAdjustRadiusBlocks());
      StructureFootprint outerFeatherZone = expandFootprint(innerAdjustZone, budget.outerFeatherRadiusBlocks());
      int supportedCore = 0;
      int totalCore = 0;
      int supportedCentralCore = 0;
      int totalCentralCore = 0;
      int supportedInner = 0;
      int totalInner = 0;
      int minTop = Integer.MAX_VALUE;
      int maxTop = Integer.MIN_VALUE;
      int edgeUnsupported = 0;
      int innerAdjustUnsupported = 0;
      int innerAdjustTotal = 0;
      int outerFeatherUnsupported = 0;
      int outerFeatherTotal = 0;

      for (StructureFootprint.GridPoint point : supportMask.coreRequiredPoints(
         candidateCenterX, candidateCenterZ, coreFootprint, Math.max(3, context.settings().structureSupport().supportSampleGridSize() - 1)
      )) {
         totalCore++;
         boolean hasSupport = evaluationCache.hasSupportBelow(context, point.x(), point.z(), candidateBaseY);
         if (hasSupport) {
            supportedCore++;
         }

         TerrainColumn col = evaluationCache.sampleColumn(context, point.x(), point.z());
         if (col.exists()) {
            minTop = Math.min(minTop, col.topY());
            maxTop = Math.max(maxTop, col.topY());
         }
      }

      int unsupportedCore = Math.max(0, totalCore - supportedCore);

      for (StructureFootprint.GridPoint point : supportMask.centralCorePoints(
         candidateCenterX, candidateCenterZ, centralCoreFootprint, Math.max(3, context.settings().structureSupport().supportSampleGridSize() - 1)
      )) {
         totalCentralCore++;
         if (evaluationCache.hasSupportBelow(context, point.x(), point.z(), candidateBaseY)) {
            supportedCentralCore++;
         }
      }

      int unsupportedCentralCore = Math.max(0, totalCentralCore - supportedCentralCore);

      for (StructureFootprint.GridPoint point : supportMask.edgeOptionalPoints(
         candidateCenterX, candidateCenterZ, innerMargin, Math.max(4, context.settings().structureSupport().supportSampleGridSize())
      )) {
         totalInner++;
         boolean hasSupport = evaluationCache.hasSupportBelow(context, point.x(), point.z(), candidateBaseY);
         if (hasSupport) {
            supportedInner++;
         } else {
            edgeUnsupported++;
         }

         TerrainColumn col = evaluationCache.sampleColumn(context, point.x(), point.z());
         if (col.exists()) {
            minTop = Math.min(minTop, col.topY());
            maxTop = Math.max(maxTop, col.topY());
         }
      }

      if (budget.innerAdjustRadiusBlocks() > 0) {
         int[] innerAdjustTotalRef = new int[]{innerAdjustTotal};
         int[] innerAdjustUnsupportedRef = new int[]{innerAdjustUnsupported};
         int[] minTopRef = new int[]{minTop};
         int[] maxTopRef = new int[]{maxTop};
         innerAdjustZone.forEachGridPoint(Math.max(4, context.settings().structureSupport().supportSampleGridSize()), (x, z) -> {
            innerAdjustTotalRef[0]++;
            boolean hasSupport = evaluationCache.hasSupportBelow(context, x, z, candidateBaseY);
            if (!hasSupport) {
               innerAdjustUnsupportedRef[0]++;
            }

            TerrainColumn col = evaluationCache.sampleColumn(context, x, z);
            if (col.exists()) {
               minTopRef[0] = Math.min(minTopRef[0], col.topY());
               maxTopRef[0] = Math.max(maxTopRef[0], col.topY());
            }
         });
         innerAdjustTotal = innerAdjustTotalRef[0];
         innerAdjustUnsupported = innerAdjustUnsupportedRef[0];
         minTop = minTopRef[0];
         maxTop = maxTopRef[0];
      }

      if (budget.outerFeatherRadiusBlocks() > 0) {
         int[] outerFeatherTotalRef = new int[]{outerFeatherTotal};
         int[] outerFeatherUnsupportedRef = new int[]{outerFeatherUnsupported};
         outerFeatherZone.forEachGridPoint(Math.max(3, context.settings().structureSupport().supportSampleGridSize() - 1), (x, z) -> {
            outerFeatherTotalRef[0]++;
            boolean hasSupport = evaluationCache.hasSupportBelow(context, x, z, candidateBaseY);
            if (!hasSupport) {
               outerFeatherUnsupportedRef[0]++;
            }
         });
         outerFeatherTotal = outerFeatherTotalRef[0];
         outerFeatherUnsupported = outerFeatherUnsupportedRef[0];
      }

      int reliefSpan = minTop == Integer.MAX_VALUE ? budget.maxReliefSpan() + 10 : Math.max(0, maxTop - minTop);
      int verticalAdjustment = candidateBaseY - candidateTopY;
      int blocksToAdd = Math.max(0, edgeUnsupported * 2 + unsupportedCore * 4 + innerAdjustUnsupported + outerFeatherUnsupported / 2);
      int blocksToRemove = Math.max(0, reliefSpan * 2);
      int edgeExtension = Math.min(budget.maxEdgeExtension() + 1, Math.max(Math.abs(offsetX), Math.abs(offsetZ)));
      int stableCells = supportedInner;
      int groundedCells = supportedInner;
      double groundedRatio = totalInner == 0 ? 0.0 : (double)groundedCells / totalInner;
      double innerAdjustRatio = innerAdjustTotal == 0 ? groundedRatio : 1.0 - (double)innerAdjustUnsupported / innerAdjustTotal;
      double outerFeatherRatio = outerFeatherTotal == 0 ? innerAdjustRatio : 1.0 - (double)outerFeatherUnsupported / outerFeatherTotal;
      String outcome = "accepted_with_repair";
      boolean repairable = true;
      String primaryBudgetCounter = "none";
      if (unsupportedCentralCore > budget.maxUnsupportedCoreCells()) {
         outcome = "rejected_core_missing_support";
         repairable = false;
      } else if (totalCentralCore > 0 && unsupportedCentralCore > totalCentralCore / 2) {
         outcome = "rejected_large_core_void";
         repairable = false;
      } else if (reliefSpan > budget.maxReliefSpan()) {
         outcome = "rejected_steep_slope_budget";
         repairable = false;
         primaryBudgetCounter = "relief_span";
      } else if (totalCentralCore > 0 && supportedCentralCore < Math.max(1, totalCentralCore / 2)) {
         outcome = "rejected_thin_island";
         repairable = false;
      } else if (blocksToAdd > budget.maxBlocksToAdd()
         || blocksToRemove > budget.maxBlocksToRemove()
         || Math.abs(verticalAdjustment) > budget.maxVerticalAdjustment()
         || edgeExtension > budget.maxEdgeExtension()) {
         outcome = "rejected_budget_exceeded";
         repairable = false;
         if (blocksToAdd > budget.maxBlocksToAdd()) {
            primaryBudgetCounter = "blocks_to_add";
         } else if (blocksToRemove > budget.maxBlocksToRemove()) {
            primaryBudgetCounter = "blocks_to_remove";
         } else if (Math.abs(verticalAdjustment) > budget.maxVerticalAdjustment()) {
            primaryBudgetCounter = "vertical_adjustment";
         } else {
            primaryBudgetCounter = "edge_extension";
         }
      }

      double coreRatio = (double)supportedCore / Math.max(1, totalCore);
      double centralCoreRatio = (double)supportedCentralCore / Math.max(1, totalCentralCore);
      double budgetPressure = (double)blocksToAdd / Math.max(1, budget.maxBlocksToAdd())
         + (double)blocksToRemove / Math.max(1, budget.maxBlocksToRemove())
         + (double)Math.abs(verticalAdjustment) / Math.max(1, budget.maxVerticalAdjustment())
         + (double)reliefSpan / Math.max(1, budget.maxReliefSpan());
      double score = centralCoreRatio * budget.centralCoreWeight()
         + coreRatio * 0.3
         + groundedRatio * 0.18
         + innerAdjustRatio * 0.08
         + outerFeatherRatio * 0.04
         - budgetPressure * 0.08
         - (double)(Math.abs(offsetX) + Math.abs(offsetZ)) / Math.max(1, budget.searchRadiusBlocks() * 2) * 0.02;
      return new LandCandidateEvaluation(
         repairable,
         outcome,
         candidateCenterX,
         candidateCenterZ,
         candidateTopY,
         candidateBaseY,
         offsetX,
         offsetZ,
         stableCells,
         groundedCells,
         groundedRatio,
         blocksToAdd,
         blocksToRemove,
         verticalAdjustment,
         unsupportedCentralCore,
         edgeExtension,
         reliefSpan,
         score,
         primaryBudgetCounter
      );
   }

   private static StructureFootprint expandFootprint(StructureFootprint footprint, int radius) {
      return radius <= 0
         ? footprint
         : new StructureFootprint(footprint.minX() - radius, footprint.maxX() + radius, footprint.minZ() - radius, footprint.maxZ() + radius);
   }
}
