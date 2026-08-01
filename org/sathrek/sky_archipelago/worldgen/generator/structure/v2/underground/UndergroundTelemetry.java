package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public final class UndergroundTelemetry {
   public void accepted(
      ResourceLocation structureId,
      ChunkPos chunkPos,
      UndergroundStrategySelector.Strategy strategy,
      String stage,
      SkyStructurePlacementTarget islandAwareTarget,
      SkyStructurePlacementTarget finalTarget,
      int verticalDelta,
      int generated,
      int pruned,
      int evaluated,
      int samples,
      UndergroundConstraintEvaluator.ConstraintSummary constraints,
      UndergroundCandidateRanker.RankSummary rank
   ) {
      debug(
         "UG_V2_ACCEPT id={} chunk=[{}, {}] strategy={} stage={} islandAwareTarget={} finalTarget={} verticalDelta={} generated={} pruned={} evaluated={} samples={} prunedNoColumnAtY={} prunedOutsideHost={} prunedOverburden={} prunedDepth={} prunedSupport={} prunedStone={} rank={} tieBreak={}",
         structureId,
         chunkPos.x,
         chunkPos.z,
         strategy,
         stage,
         formatTarget(islandAwareTarget),
         formatTarget(finalTarget),
         verticalDelta,
         generated,
         pruned,
         evaluated,
         samples,
         constraints.noColumnAtY(),
         constraints.outsideHostDominance(),
         constraints.insufficientOverburden(),
         constraints.insufficientDepth(),
         constraints.insufficientSupport(),
         constraints.insufficientStone(),
         rank.rankMetrics(),
         rank.tieBreakPath()
      );
   }

   public void rejected(
      ResourceLocation structureId,
      ChunkPos chunkPos,
      UndergroundStrategySelector.Strategy strategy,
      String stage,
      String reason,
      SkyStructurePlacementTarget islandAwareTarget,
      int generated,
      int pruned,
      int evaluated,
      int samples,
      UndergroundConstraintEvaluator.ConstraintSummary constraints,
      UndergroundCandidateRanker.RankSummary rank
   ) {
      debug(
         "UG_V2_REJECT id={} chunk=[{}, {}] strategy={} stage={} reason={} islandAwareTarget={} generated={} pruned={} evaluated={} samples={} prunedNoColumnAtY={} prunedOutsideHost={} prunedOverburden={} prunedDepth={} prunedSupport={} prunedStone={} rank={} tieBreak={}",
         structureId,
         chunkPos.x,
         chunkPos.z,
         strategy,
         stage,
         reason,
         formatTarget(islandAwareTarget),
         generated,
         pruned,
         evaluated,
         samples,
         constraints.noColumnAtY(),
         constraints.outsideHostDominance(),
         constraints.insufficientOverburden(),
         constraints.insufficientDepth(),
         constraints.insufficientSupport(),
         constraints.insufficientStone(),
         rank.rankMetrics(),
         rank.tieBreakPath()
      );
   }

   private static String formatTarget(SkyStructurePlacementTarget target) {
      return target == null
         ? "none"
         : "(" + target.x() + "," + target.y() + "," + target.z() + ";local=" + target.localOffsetX() + "," + target.localOffsetZ() + ")";
   }

   private static void debug(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }
}
