package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground;

import org.sathrek.sky_archipelago.worldgen.structure.mineshafts.MineshaftPlacementDecision;
import org.sathrek.sky_archipelago.worldgen.structure.underground.UndergroundPlacementDecision;

public final class UndergroundCandidateRanker {
   public UndergroundCandidateRanker.RankSummary summarize(UndergroundPlacementDecision decision) {
      return new UndergroundCandidateRanker.RankSummary(decision.rankMetrics(), decision.tieBreakPath());
   }

   public UndergroundCandidateRanker.RankSummary summarize(MineshaftPlacementDecision decision) {
      return new UndergroundCandidateRanker.RankSummary(decision.rankMetrics(), decision.tieBreakPath());
   }

   public record RankSummary(String rankMetrics, String tieBreakPath) {
   }
}
