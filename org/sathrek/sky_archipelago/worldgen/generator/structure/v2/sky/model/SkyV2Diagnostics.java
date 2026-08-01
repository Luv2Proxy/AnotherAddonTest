package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.sky.model;

import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementFailureDiagnostics;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementResult;

public record SkyV2Diagnostics(
   int qualifiedHosts,
   int attemptedHosts,
   int coarseOffsetsEvaluated,
   int fineOffsetsEvaluated,
   boolean hostCapHit,
   boolean offsetCapHit,
   int totalPreviews,
   int qualifiedPreviews,
   int rejectedHosts,
   int rejectedCandidates
) {
   public static SkyV2Diagnostics from(PlacementResult result, PlacementFailureDiagnostics failure) {
      PlacementFailureDiagnostics safeFailure = failure == null ? PlacementFailureDiagnostics.empty() : failure;
      return new SkyV2Diagnostics(
         result != null ? result.qualifiedHosts() : 0,
         result != null ? result.attemptedHosts() : 0,
         result != null ? result.coarseOffsetsEvaluated() : 0,
         result != null ? result.fineOffsetsEvaluated() : 0,
         result != null && result.hostAttemptCapHit(),
         result != null && result.offsetCapHit(),
         safeFailure.totalPreviews(),
         safeFailure.qualifiedPreviews(),
         safeFailure.rejectedHosts().size(),
         safeFailure.rejectedCandidates().size()
      );
   }

   public String summary() {
      return "qualifiedHosts="
         + this.qualifiedHosts
         + ", attemptedHosts="
         + this.attemptedHosts
         + ", coarseOffsetsEvaluated="
         + this.coarseOffsetsEvaluated
         + ", fineOffsetsEvaluated="
         + this.fineOffsetsEvaluated
         + ", hostCapHit="
         + this.hostCapHit
         + ", offsetCapHit="
         + this.offsetCapHit
         + ", totalPreviews="
         + this.totalPreviews
         + ", qualifiedPreviews="
         + this.qualifiedPreviews
         + ", rejectedHosts="
         + this.rejectedHosts
         + ", rejectedCandidates="
         + this.rejectedCandidates;
   }
}
