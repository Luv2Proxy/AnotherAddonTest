package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument;

import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces.MonumentBuilding;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public final class OceanMonumentBufferedPlacer {
   public static final int VANILLA_MIN_Y = 39;

   public OceanMonumentBufferedPlacer.ReplayResult place(
      WorldGenLevel level,
      StructureManager structureManager,
      ChunkGenerator generator,
      BoundingBox chunkBox,
      ChunkPos chunkPos,
      BlockPos pieceAnchor,
      long levelSeed,
      int sourceChunkX,
      int sourceChunkZ,
      int minX,
      int minZ,
      int finalMinY,
      int waterTopY,
      Direction direction,
      StructureFootprint footprint
   ) {
      WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
      random.setLargeFeatureSeed(levelSeed, sourceChunkX, sourceChunkZ);
      int yShift = finalMinY - 39;
      MonumentBuilding vanilla = new MonumentBuilding(random, minX, minZ, direction);

      Map<BlockPos, BlockState> captured;
      try (OceanMonumentCaptureContext context = OceanMonumentCaptureContext.begin(level, yShift)) {
         vanilla.postProcess(level, structureManager, generator, random, chunkBox, chunkPos, pieceAnchor);
         captured = context.blocks();
      }

      int committed = 0;
      int skippedAir = 0;
      int skippedWaterAboveTop = 0;
      int skippedOutsideFootprint = 0;
      int skippedOutsideChunk = 0;
      int skippedOutsideBuildHeight = 0;
      MutableBlockPos target = new MutableBlockPos();

      for (Entry<BlockPos, BlockState> entry : captured.entrySet()) {
         BlockPos sourcePos = entry.getKey();
         BlockState state = entry.getValue();
         if (state.isAir()) {
            skippedAir++;
         } else {
            target.set(sourcePos.getX(), sourcePos.getY() + yShift, sourcePos.getZ());
            if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()) {
               skippedOutsideBuildHeight++;
            } else if (!footprint.contains(target.getX(), target.getZ())) {
               skippedOutsideFootprint++;
            } else if (!chunkBox.isInside(target)) {
               skippedOutsideChunk++;
            } else if (isWaterLike(state) && target.getY() > waterTopY) {
               skippedWaterAboveTop++;
            } else {
               level.setBlock(target, state, 2);
               if (!level.getFluidState(target).isEmpty()) {
                  level.scheduleTick(target.immutable(), level.getFluidState(target).getType(), 0);
               }

               committed++;
            }
         }
      }

      debug(
         "OCEAN_MONUMENT_DEBUG stage=buffered_replay chunk=[{}, {}] sourceChunk=[{}, {}] yShift={} captured={} committed={} skippedAir={} skippedWaterAboveTop={} skippedOutsideFootprint={} skippedOutsideChunk={} skippedOutsideBuildHeight={} waterTopY={} direction={}",
         chunkPos.x,
         chunkPos.z,
         sourceChunkX,
         sourceChunkZ,
         yShift,
         captured.size(),
         committed,
         skippedAir,
         skippedWaterAboveTop,
         skippedOutsideFootprint,
         skippedOutsideChunk,
         skippedOutsideBuildHeight,
         waterTopY,
         direction
      );
      return new OceanMonumentBufferedPlacer.ReplayResult(
         captured.size(), committed, skippedAir, skippedWaterAboveTop, skippedOutsideFootprint, skippedOutsideChunk, skippedOutsideBuildHeight
      );
   }

   static boolean isWaterLike(BlockState state) {
      return state.is(Blocks.WATER) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
   }

   private static void debug(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }

   public record ReplayResult(
      int captured,
      int committed,
      int skippedAir,
      int skippedWaterAboveTop,
      int skippedOutsideFootprint,
      int skippedOutsideChunk,
      int skippedOutsideBuildHeight
   ) {
   }
}
