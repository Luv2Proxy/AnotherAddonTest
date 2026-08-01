package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementCommitCoordinator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.ReservationContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureOverlapGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureStartRelocator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureLocateIndex;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public final class UndergroundPlacementCommitter {
   private final StructureStartRelocator relocator;
   private final PlacementCommitCoordinator commitCoordinator;

   public UndergroundPlacementCommitter() {
      this(new StructureStartRelocator());
   }

   UndergroundPlacementCommitter(StructureStartRelocator relocator) {
      this.relocator = relocator;
      this.commitCoordinator = new PlacementCommitCoordinator(new StructureOverlapGuard());
   }

   public StructureStart commitStatic(PlacementRequest request, StructureStart structureStart, SkyStructurePlacementTarget target, int verticalDelta) {
      if (!StructureRegistryGuard.canCommit(request, "underground_static_committer")) {
         return null;
      }

      if (target == null) {
         PlacementCommitCoordinator.Decision reservationDecision = this.commitCoordinator
            .reserveOrConflict(
               structureStart,
               structureStart.getBoundingBox(),
               StructurePlacementCategory.UNDERGROUND,
               new ReservationContext(request.levelSeed(), request.structureId(), request.chunkPos())
            );
         if (!reservationDecision.accepted()) {
            return null;
         }

         request.structureManager().setStartForStructure(request.sectionPos(), request.structure(), structureStart, request.chunk());
         return structureStart;
      } else {
         BlockPos vanillaPos = RelocatedStructureLocateIndex.centerOf(structureStart.getBoundingBox());
         StructureStart relocated = request.supportPlane() != null
            ? this.relocator.relocate(structureStart, request.chunkPos(), request.supportPlane().rawFootprint(), request.supportPlane(), target)
            : this.relocator.relocateByOffsets(structureStart, request.chunkPos(), target.localOffsetX(), verticalDelta, target.localOffsetZ());
         PlacementCommitCoordinator.Decision reservationDecision = this.commitCoordinator
            .reserveOrConflict(
               relocated,
               relocated.getBoundingBox(),
               StructurePlacementCategory.UNDERGROUND,
               new ReservationContext(request.levelSeed(), request.structureId(), request.chunkPos())
            );
         if (!reservationDecision.accepted()) {
            return null;
         }

         request.structureManager().setStartForStructure(request.sectionPos(), request.structure(), relocated, request.chunk());
         RelocatedStructureLocateIndex.recordCommittedRelocation(
            request.structureId(),
            request.dimension(),
            request.chunkPos(),
            vanillaPos,
            RelocatedStructureLocateIndex.centerOf(relocated.getBoundingBox()),
            request.chunkPos()
         );
         return relocated;
      }
   }

   public StructureStart commitDynamic(PlacementRequest request, StructureStart structureStart, SkyStructurePlacementTarget target, int verticalDelta) {
      if (!StructureRegistryGuard.canCommit(request, "underground_dynamic_committer")) {
         return null;
      }

      BlockPos vanillaPos = RelocatedStructureLocateIndex.centerOf(structureStart.getBoundingBox());
      StructureStart relocated = target == null
         ? structureStart
         : this.relocator.relocateByOffsets(structureStart, request.chunkPos(), target.localOffsetX(), verticalDelta, target.localOffsetZ());
      PlacementCommitCoordinator.Decision reservationDecision = this.commitCoordinator
         .reserveOrConflict(
            relocated,
            relocated.getBoundingBox(),
            StructurePlacementCategory.UNDERGROUND,
            new ReservationContext(request.levelSeed(), request.structureId(), request.chunkPos())
         );
      if (!reservationDecision.accepted()) {
         return null;
      }

      request.structureManager().setStartForStructure(request.sectionPos(), request.structure(), relocated, request.chunk());
      if (target != null) {
         RelocatedStructureLocateIndex.recordCommittedRelocation(
            request.structureId(),
            request.dimension(),
            request.chunkPos(),
            vanillaPos,
            RelocatedStructureLocateIndex.centerOf(relocated.getBoundingBox()),
            request.chunkPos()
         );
      }

      return relocated;
   }
}
