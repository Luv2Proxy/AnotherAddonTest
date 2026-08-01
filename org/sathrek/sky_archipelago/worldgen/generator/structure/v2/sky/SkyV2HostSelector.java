package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.sky;

import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementResolver;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementCandidate;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementFailureDiagnostics;

public final class SkyV2HostSelector {
   private final SkyStructurePlacementResolver resolver;

   public SkyV2HostSelector(SkyStructurePlacementResolver resolver) {
      this.resolver = resolver;
   }

   public SkyV2HostSelector.Selection select(StructureSupportContext supportContext, StructureStart structureStart, ChunkPos sourceChunkPos) {
      List<PlacementCandidate> candidates = this.resolver.resolveOrderedPlacementCandidates(supportContext, structureStart, sourceChunkPos);
      PlacementFailureDiagnostics diagnostics = candidates.isEmpty()
         ? this.resolver.diagnoseRejectedPlacement(supportContext, structureStart, sourceChunkPos)
         : PlacementFailureDiagnostics.empty();
      return new SkyV2HostSelector.Selection(candidates, diagnostics);
   }

   public record Selection(List<PlacementCandidate> candidates, PlacementFailureDiagnostics failureDiagnostics) {
   }
}
