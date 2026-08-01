package org.sathrek.sky_archipelago.worldgen.generator.terrain;

import java.util.List;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.WorldgenPerformanceMetrics;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;

public final class SkyIslandTerrainPass {
   private SkyIslandTerrainPass() {
   }

   public static void fillChunk(ChunkAccess chunk, SkyIslandSettings settings, IslandField islandField, BlockState stone, BlockState water) {
      long passStartNanos = System.nanoTime();
      ChunkPos chunkPos = chunk.getPos();
      int minY = chunk.getMinBuildHeight();
      int maxY = chunk.getMaxBuildHeight();
      int chunkMinX = chunkPos.getMinBlockX();
      int chunkMinZ = chunkPos.getMinBlockZ();
      long snapshotStartNanos = System.nanoTime();
      SkyIslandChunkTerrainSnapshot snapshot = islandField.sampleChunkTerrainSnapshot(chunkPos, settings);
      long snapshotNanos = System.nanoTime() - snapshotStartNanos;
      Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Types.OCEAN_FLOOR_WG);
      Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Types.WORLD_SURFACE_WG);
      BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
      BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
      int activeColumns = 0;
      int skippedColumns = 0;
      long yIterations = 0L;
      long blockWrites = 0L;

      for (int localX = 0; localX < 16; localX++) {
         int worldX = chunkMinX + localX;

         for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkMinZ + localZ;
            List<TerrainColumn> segments = snapshot.segmentsAt(worldX, worldZ);
            SkyIslandColumnMaterialPlan plan = SkyIslandColumnMaterialPlan.create(segments, minY, maxY, settings, worldX, worldZ, islandField.layoutSeed());
            if (!segments.isEmpty() || settings.terrain().ocean().oceanEnabled() && plan.oceanTopY() >= minY) {
               activeColumns++;
               int activeSectionIndex = Integer.MIN_VALUE;
               LevelChunkSection activeSection = null;

               for (SkyIslandColumnMaterialPlan.MaterialRange range : plan.materialRanges()) {
                  for (int y = range.bottomY(); y <= range.topY(); y++) {
                     yIterations++;
                     BlockState plannedState = plan.plannedStateAt(y, stone, deepslate, water, bedrock);
                     if (plannedState != null) {
                        int sectionIndex = chunk.getSectionIndex(y);
                        if (sectionIndex != activeSectionIndex) {
                           activeSectionIndex = sectionIndex;
                           activeSection = chunk.getSection(sectionIndex);
                        }

                        activeSection.setBlockState(localX, y & 15, localZ, plannedState, false);
                        blockWrites++;
                        oceanFloor.update(localX, y, localZ, plannedState);
                        worldSurface.update(localX, y, localZ, plannedState);
                     }
                  }
               }
            } else {
               skippedColumns++;
            }
         }
      }

      WorldgenPerformanceMetrics.recordTerrainFill(System.nanoTime() - passStartNanos, snapshotNanos, activeColumns, skippedColumns, yIterations, blockWrites);
   }

   public static void enforceOceanLayer(ChunkAccess chunk, SkyIslandSettings settings, IslandField islandField, BlockState stone, BlockState water) {
      long passStartNanos = System.nanoTime();
      ChunkPos chunkPos = chunk.getPos();
      int minY = chunk.getMinBuildHeight();
      int maxY = chunk.getMaxBuildHeight();
      int chunkMinX = chunkPos.getMinBlockX();
      int chunkMinZ = chunkPos.getMinBlockZ();
      SkyIslandChunkTerrainSnapshot snapshot = islandField.sampleChunkTerrainSnapshot(chunkPos, settings);
      Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Types.OCEAN_FLOOR_WG);
      Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Types.WORLD_SURFACE_WG);
      BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
      BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
      MutableBlockPos mutablePos = new MutableBlockPos();
      long yIterations = 0L;
      long blockWrites = 0L;

      for (int localX = 0; localX < 16; localX++) {
         int worldX = chunkMinX + localX;

         for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkMinZ + localZ;
            List<TerrainColumn> segments = snapshot.segmentsAt(worldX, worldZ);
            SkyIslandColumnMaterialPlan plan = SkyIslandColumnMaterialPlan.create(segments, minY, maxY, settings, worldX, worldZ, islandField.layoutSeed());
            if (!segments.isEmpty() || settings.terrain().ocean().oceanEnabled() && plan.oceanTopY() >= minY) {
               int activeSectionIndex = Integer.MIN_VALUE;
               LevelChunkSection activeSection = null;

               for (int y = minY; y < maxY; y++) {
                  yIterations++;
                  BlockState plannedState = plan.plannedStateAt(y, stone, deepslate, water, bedrock);
                  if (plannedState != null) {
                     mutablePos.set(worldX, y, worldZ);
                     BlockState currentState = chunk.getBlockState(mutablePos);
                     if (currentState != plannedState
                        && (
                           plannedState != stone
                              || y <= plan.oceanFloorTopY()
                                 && (currentState.isAir() || currentState.getFluidState().is(settings.advanced().oceanBlockType().fluid()))
                        )
                        && (plannedState != water || currentState.isAir() || currentState.getFluidState().is(settings.advanced().oceanBlockType().fluid()))) {
                        int sectionIndex = chunk.getSectionIndex(y);
                        if (sectionIndex != activeSectionIndex) {
                           activeSectionIndex = sectionIndex;
                           activeSection = chunk.getSection(sectionIndex);
                        }

                        activeSection.setBlockState(localX, y & 15, localZ, plannedState, false);
                        blockWrites++;
                        oceanFloor.update(localX, y, localZ, plannedState);
                        worldSurface.update(localX, y, localZ, plannedState);
                     }
                  }
               }
            }
         }
      }

      WorldgenPerformanceMetrics.recordOceanLayer(System.nanoTime() - passStartNanos, yIterations, blockWrites);
   }

   public static int baseHeightAt(IslandField islandField, int x, int z, int minY, int maxY, SkyIslandSettings settings) {
      SkyIslandColumnMaterialPlan plan = SkyIslandColumnMaterialPlan.create(
         islandField.sampleSolidSegments(x, z, settings), minY, maxY, settings, x, z, islandField.layoutSeed()
      );
      return plan.firstFreeY();
   }

   public static NoiseColumn baseColumnAt(
      IslandField islandField, int x, int z, int minY, int maxY, SkyIslandSettings settings, BlockState stone, BlockState water
   ) {
      BlockState[] states = new BlockState[maxY - minY];
      BlockState air = Blocks.AIR.defaultBlockState();
      BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
      BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
      SkyIslandColumnMaterialPlan plan = SkyIslandColumnMaterialPlan.create(
         islandField.sampleSolidSegments(x, z, settings), minY, maxY, settings, x, z, islandField.layoutSeed()
      );

      for (int index = 0; index < states.length; index++) {
         states[index] = air;
      }

      for (int y = minY; y < maxY; y++) {
         BlockState plannedState = plan.plannedStateAt(y, stone, deepslate, water, bedrock);
         if (plannedState != null) {
            states[y - minY] = plannedState;
         }
      }

      return new NoiseColumn(minY, states);
   }
}
