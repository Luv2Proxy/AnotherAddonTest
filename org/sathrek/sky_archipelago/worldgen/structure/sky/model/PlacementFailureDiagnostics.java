package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

import java.util.List;

public record PlacementFailureDiagnostics(
   int totalPreviews, int qualifiedPreviews, List<RejectedHostCandidate> rejectedHosts, List<RejectedSkyCandidate> rejectedCandidates
) {
   public static PlacementFailureDiagnostics empty() {
      return new PlacementFailureDiagnostics(0, 0, List.of(), List.of());
   }
}
