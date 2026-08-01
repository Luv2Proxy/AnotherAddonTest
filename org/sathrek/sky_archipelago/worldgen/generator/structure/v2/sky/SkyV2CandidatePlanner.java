package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.sky;

import java.util.List;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementCandidate;

public final class SkyV2CandidatePlanner {
   public PlacementCandidate selectBest(List<PlacementCandidate> candidates) {
      return candidates != null && !candidates.isEmpty() ? candidates.get(0) : null;
   }
}
