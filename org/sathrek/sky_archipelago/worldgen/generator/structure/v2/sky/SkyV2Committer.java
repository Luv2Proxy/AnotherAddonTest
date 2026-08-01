package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.sky;

import java.util.List;
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
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementCandidate;

public final class SkyV2Committer {
   private final StructureStartRelocator relocator;
   private final PlacementCommitCoordinator commitCoordinator;

   public SkyV2Committer() {
      this(new StructureStartRelocator());
   }

   public SkyV2Committer(StructureStartRelocator relocator) {
      this.relocator = relocator;
      this.commitCoordinator = new PlacementCommitCoordinator(new StructureOverlapGuard());
   }

   public SkyV2Committer.CommitResult commit(PlacementRequest request, PlacementCandidate candidate) {
      if (!StructureRegistryGuard.canCommit(request, "sky_v2_committer")) {
         return new SkyV2Committer.CommitResult(null, new RelocatedStructureReferenceRegistry.RegistrationResult(List.of(), 0, 0, request.chunkPos()));
      }

      BlockPos vanillaPos = RelocatedStructureLocateIndex.centerOf(request.structureStart().getBoundingBox());
      StructureStart relocated = this.relocator
         .relocate(request.structureStart(), request.chunkPos(), candidate.rawFootprint(), request.supportPlane(), candidate.target());
      PlacementCommitCoordinator.Decision reservationDecision = this.commitCoordinator
         .reserveOrConflict(
            relocated,
            relocated.getBoundingBox(),
            StructurePlacementCategory.SKY,
            new ReservationContext(request.levelSeed(), request.structureId(), request.chunkPos())
         );
      if (!reservationDecision.accepted()) {
         return new SkyV2Committer.CommitResult(null, new RelocatedStructureReferenceRegistry.RegistrationResult(List.of(), 0, 0, request.chunkPos()));
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
      return new SkyV2Committer.CommitResult(relocated, references);
   }

   public record CommitResult(StructureStart relocatedStart, RelocatedStructureReferenceRegistry.RegistrationResult references) {
   }
}
