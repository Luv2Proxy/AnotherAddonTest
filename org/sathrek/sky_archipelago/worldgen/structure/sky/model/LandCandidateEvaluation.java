package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

public record LandCandidateEvaluation(
   boolean repairable,
   String outcome,
   int centerX,
   int centerZ,
   int topY,
   int baseY,
   int offsetX,
   int offsetZ,
   int stableCells,
   int groundedCells,
   double groundedRatio,
   int blocksToAdd,
   int blocksToRemove,
   int verticalAdjustment,
   int unsupportedCoreCells,
   int edgeExtension,
   int reliefSpan,
   double score,
   String primaryBudgetCounter
) {
}
