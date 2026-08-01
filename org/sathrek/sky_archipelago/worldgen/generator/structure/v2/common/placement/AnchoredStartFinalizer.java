package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureLocateIndex;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureReferenceRegistry;

public final class AnchoredStartFinalizer {
   public AnchoredStartFinalizer.FinalizationResult finalizeAnchoredStart(
      ResourceLocation structureId,
      Structure structure,
      StructureManager structureManager,
      SectionPos sectionPos,
      ChunkAccess chunk,
      StructureStart structureStart,
      BoundingBox finalBounds,
      ChunkPos sourceChunk,
      ChunkPos anchorChunk,
      ResourceKey<Level> dimension,
      RegistryAccess registryAccess,
      BlockPos vanillaPos,
      String commitContext
   ) {
      if (structureManager == null || sectionPos == null || chunk == null || structureStart == null || finalBounds == null) {
         return AnchoredStartFinalizer.FinalizationResult.rejected("anchor_finalize_missing_commit_dependencies", "missing_commit_dependencies");
      }

      if (!StructureRegistryGuard.canCommit(structureId, structure, commitContext)) {
         return AnchoredStartFinalizer.FinalizationResult.rejected("unregistered_structure", "unregistered_structure");
      }

      structureManager.setStartForStructure(sectionPos, structure, structureStart, chunk);
      RelocatedStructureReferenceRegistry.RegistrationResult references = RelocatedStructureReferenceRegistry.registerTouchedChunks(
         structureManager,
         structureId,
         structure,
         structureStart,
         finalBounds,
         chunk,
         dimension == null ? Level.OVERWORLD : dimension,
         registryAccess,
         commitContext
      );
      BlockPos relocatedPos = RelocatedStructureLocateIndex.centerOf(finalBounds);
      RelocatedStructureLocateIndex.recordCommittedRelocation(
         structureId,
         dimension == null ? Level.OVERWORLD : dimension,
         sourceChunk == null ? chunk.getPos() : sourceChunk,
         vanillaPos == null ? relocatedPos : vanillaPos,
         relocatedPos,
         anchorChunk == null ? references.anchorChunk() : anchorChunk
      );
      return AnchoredStartFinalizer.FinalizationResult.accepted(structureStart, references, relocatedPos);
   }

   public record FinalizationResult(
      boolean accepted,
      String stage,
      String details,
      StructureStart structureStart,
      RelocatedStructureReferenceRegistry.RegistrationResult references,
      BlockPos relocatedPos
   ) {
      static AnchoredStartFinalizer.FinalizationResult accepted(
         StructureStart structureStart, RelocatedStructureReferenceRegistry.RegistrationResult references, BlockPos relocatedPos
      ) {
         return new AnchoredStartFinalizer.FinalizationResult(true, "anchor_finalize_accepted", "accepted", structureStart, references, relocatedPos);
      }

      static AnchoredStartFinalizer.FinalizationResult rejected(String stage, String details) {
         return new AnchoredStartFinalizer.FinalizationResult(false, stage, details, null, null, null);
      }
   }
}
