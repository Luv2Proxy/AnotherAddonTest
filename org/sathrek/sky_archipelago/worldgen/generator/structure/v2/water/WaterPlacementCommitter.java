package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementCommitCoordinator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.ReservationContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureOverlapGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureStartRelocator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureLocateIndex;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureReferenceRegistry;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class WaterPlacementCommitter {
   private final StructureStartRelocator relocator = new StructureStartRelocator();
   private final PlacementCommitCoordinator commitCoordinator = new PlacementCommitCoordinator(new StructureOverlapGuard());

   public StructureStart commit(PlacementRequest request, StructureStart structureStart, int dx, int dy, int dz) {
      if (!StructureRegistryGuard.canCommit(request, "water_v2_committer")) {
         return null;
      }

      BlockPos vanillaPos = RelocatedStructureLocateIndex.centerOf(structureStart.getBoundingBox());
      StructureStart relocated = this.relocator.relocateByOffsets(structureStart, request.chunkPos(), dx, dy, dz);
      PlacementCommitCoordinator.Decision reservationDecision = this.commitCoordinator
         .reserveOrConflict(
            relocated,
            relocated.getBoundingBox(),
            StructurePlacementCategory.WATER,
            new ReservationContext(request.levelSeed(), request.structureId(), request.chunkPos())
         );
      if (!reservationDecision.accepted()) {
         return null;
      }

      request.structureManager().setStartForStructure(request.sectionPos(), request.structure(), relocated, request.chunk());
      RelocatedStructureReferenceRegistry.RegistrationResult references = RelocatedStructureReferenceRegistry.registerTouchedChunks(
         request, relocated, relocated.getBoundingBox()
      );
      RelocatedStructureLocateIndex.recordCommittedRelocation(
         request.structureId(),
         request.dimension(),
         request.chunkPos(),
         vanillaPos,
         RelocatedStructureLocateIndex.centerOf(relocated.getBoundingBox()),
         references.anchorChunk()
      );
      return relocated;
   }
}
