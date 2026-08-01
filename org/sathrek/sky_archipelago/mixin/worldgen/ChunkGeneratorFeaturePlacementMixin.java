package org.sathrek.sky_archipelago.mixin.worldgen;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.FeaturePlacementCrashShield;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorFeaturePlacementMixin {
   @Redirect(
      method = {"applyBiomeDecoration", "lambda$applyBiomeDecoration$8"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/levelgen/structure/StructureStart;placeInChunk(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;)V"
      ),
      require = 0
   )
   private void sky_archipelago$guardStructurePlacement(
      StructureStart structureStart,
      WorldGenLevel level,
      StructureManager structureManager,
      ChunkGenerator generator,
      RandomSource random,
      BoundingBox box,
      ChunkPos chunkPos
   ) {
      FeaturePlacementCrashShield.placeStructureStartSafely(structureStart, level, structureManager, generator, random, box, chunkPos);
   }
}
