package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.PlannedStructurePlacement;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureLocateIndex;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureReferenceRegistry;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class StructureCommitter {
   private final StructureOverlapGuard overlapGuard;
   private final StructureStartRelocator relocator;
   private final PlacementCommitCoordinator commitCoordinator;
   private final AnchorBoundsResolver legacyBoundsResolver;
   private final AnchorBoundsResolver rootPieceBoundsResolver;
   private final AnchorBoundsPolicy anchorBoundsPolicy;

   public StructureCommitter(StructureOverlapGuard overlapGuard, StructureStartRelocator relocator) {
      this(overlapGuard, relocator, new LegacyStartBoundsResolver(), AnchorBoundsPolicy.rootPieceTrialPolicy());
   }

   StructureCommitter(
      StructureOverlapGuard overlapGuard, StructureStartRelocator relocator, AnchorBoundsResolver legacyBoundsResolver, AnchorBoundsPolicy anchorBoundsPolicy
   ) {
      this.overlapGuard = overlapGuard;
      this.relocator = relocator;
      this.commitCoordinator = new PlacementCommitCoordinator(overlapGuard);
      this.legacyBoundsResolver = legacyBoundsResolver;
      this.rootPieceBoundsResolver = new RootPieceAnchorBoundsResolver(legacyBoundsResolver);
      this.anchorBoundsPolicy = anchorBoundsPolicy;
   }

   public StructureCommitter.CommitResult commit(
      StructureStart originalStart,
      Structure structure,
      StructurePlacementCategory category,
      PlannedStructurePlacement plan,
      StructureManager structureManager,
      SectionPos sectionPos,
      ChunkAccess chunk,
      ChunkPos sourceChunkPos,
      ReservationContext reservationContext,
      ResourceKey<Level> dimension,
      RegistryAccess registryAccess
   ) {
      if (!StructureRegistryGuard.canCommit(reservationContext.structureId(), structure, registryAccess, "v2_common_committer", sourceChunkPos)) {
         return StructureCommitter.CommitResult.rejected("unregistered_structure", "unregistered_structure", null);
      }

      BoundingBox originalAnchorBounds = this.anchorBoundsFor(category, structure, originalStart);
      BlockPos vanillaPos = centerOf(originalAnchorBounds);
      StructureStart relocated = this.relocator.relocateByOffsets(originalStart, sourceChunkPos, plan.dx(), plan.dy(), plan.dz());
      PlacementCommitCoordinator.Decision reservationDecision = this.commitCoordinator
         .reserveOrConflict(relocated, relocated.getBoundingBox(), category, reservationContext);
      if (!reservationDecision.accepted()) {
         return StructureCommitter.CommitResult.rejected(reservationDecision.stage(), reservationDecision.details(), reservationDecision);
      }

      structureManager.setStartForStructure(sectionPos, structure, relocated, chunk);
      RelocatedStructureReferenceRegistry.RegistrationResult references = RelocatedStructureReferenceRegistry.registerTouchedChunks(
         structureManager,
         reservationContext.structureId(),
         structure,
         relocated,
         relocated.getBoundingBox(),
         chunk,
         dimension,
         registryAccess,
         "v2_common_committer_references"
      );
      BoundingBox relocatedAnchorBounds = this.anchorBoundsFor(category, structure, relocated);
      int authorityAnchorY = relocatedAnchorBounds == null ? relocated.getBoundingBox().minY() : relocatedAnchorBounds.minY();
      RelocatedPlacementContextManager.registerRelocatedStart(
         relocated, reservationContext.structureId(), sourceChunkPos, dimension == null ? Level.OVERWORLD : dimension, authorityAnchorY, true
      );
      RelocatedStructureLocateIndex.recordCommittedRelocation(
         reservationContext.structureId(), dimension, sourceChunkPos, vanillaPos, centerOf(relocatedAnchorBounds), references.anchorChunk()
      );
      return StructureCommitter.CommitResult.accepted(relocated, references, reservationDecision);
   }

   private BoundingBox anchorBoundsFor(StructurePlacementCategory category, Structure structure, StructureStart structureStart) {
      AnchorBoundsResolver resolver = this.legacyBoundsResolver;
      if (this.anchorBoundsPolicy.useRootPieceBounds(category, structure)) {
         resolver = this.rootPieceBoundsResolver;
      }

      BoundingBox resolved = resolver.resolve(structureStart);
      if (resolved != null) {
         return resolved;
      } else {
         return structureStart == null ? null : structureStart.getBoundingBox();
      }
   }

   private static BlockPos centerOf(BoundingBox bounds) {
      return bounds == null ? new BlockPos(0, 0, 0) : RelocatedStructureLocateIndex.centerOf(bounds);
   }

   public record CommitResult(
      boolean accepted,
      String reason,
      String details,
      StructureStart structureStart,
      RelocatedStructureReferenceRegistry.RegistrationResult references,
      PlacementCommitCoordinator.Decision reservationDecision
   ) {
      static StructureCommitter.CommitResult accepted(
         StructureStart structureStart,
         RelocatedStructureReferenceRegistry.RegistrationResult references,
         PlacementCommitCoordinator.Decision reservationDecision
      ) {
         return new StructureCommitter.CommitResult(true, "accepted", "accepted", structureStart, references, reservationDecision);
      }

      static StructureCommitter.CommitResult rejected(String reason, String details, PlacementCommitCoordinator.Decision reservationDecision) {
         return new StructureCommitter.CommitResult(false, reason, details, null, null, reservationDecision);
      }
   }
}
