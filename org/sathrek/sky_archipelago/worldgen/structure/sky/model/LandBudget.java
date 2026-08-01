package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

public record LandBudget(
   int searchRadiusBlocks,
   int searchStepBlocks,
   int fineSearchStepBlocks,
   int maxBlocksToAdd,
   int maxBlocksToRemove,
   int maxVerticalAdjustment,
   int maxUnsupportedCoreCells,
   int maxEdgeExtension,
   int maxReliefSpan,
   double coreInsetRatio,
   double innerMarginInsetRatio,
   double centralCoreInsetRatio,
   double centralCoreWeight,
   int innerAdjustRadiusBlocks,
   int outerFeatherRadiusBlocks
) {
}
