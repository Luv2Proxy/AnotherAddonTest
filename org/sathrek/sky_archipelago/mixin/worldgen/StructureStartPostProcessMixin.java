package org.sathrek.sky_archipelago.mixin.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PostProcessDriftDecision;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.RelocatedPlacementContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.RelocatedPlacementContextManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StructureStart.class)
public abstract class StructureStartPostProcessMixin {
   @Redirect(
      method = "placeInChunk",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/levelgen/structure/StructurePiece;postProcess(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/core/BlockPos;)V"
      )
   )
   private void sky_archipelago$enforcePostProcessAuthority(
      StructurePiece piece,
      WorldGenLevel level,
      StructureManager structureManager,
      ChunkGenerator generator,
      RandomSource random,
      BoundingBox box,
      ChunkPos chunkPos,
      BlockPos pos
   ) {
      BoundingBox before = copy(piece);
      piece.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
      BoundingBox after = copy(piece);
      RelocatedPlacementContext context = RelocatedPlacementContextManager.active();
      if (context != null && before != null && after != null) {
         PostProcessDriftDecision decision = context.evaluateDrift(piece, before, after);
         context.recordDrift(piece, before, after, decision);
         int correctionDy = decision.correctionDy();
         if (correctionDy != 0) {
            piece.move(0, correctionDy, 0);
         }
      }
   }

   private static BoundingBox copy(StructurePiece piece) {
      if (piece != null && piece.getBoundingBox() != null) {
         BoundingBox bounds = piece.getBoundingBox();
         return new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
      } else {
         return null;
      }
   }
}
