package org.sathrek.sky_archipelago.worldgen.generator.terrain;

import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;

public record SkyIslandChunkTerrainSnapshot(int halo, SkyIslandChunkTerrainSnapshot.SnapshotColumnData[][] columns, int worldMinX, int worldMinZ) {
   public static final int DEFAULT_HALO = 2;
   private static final int[][] STEEP_NEIGHBOR_OFFSETS = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};

   public static SkyIslandChunkTerrainSnapshot create(ChunkPos chunkPos, IslandField islandField, SkyIslandSettings settings) {
      return createFromColumnData(
         chunkPos,
         (x, z) -> new IslandField.ColumnData(
            islandField.sampleSolidSegments(x, z, settings),
            islandField.sampleColumnProfile(x, z, settings),
            islandField.sampleDominantIslandDescriptor(x, z, settings)
         )
      );
   }

   public static SkyIslandChunkTerrainSnapshot createFromColumnData(ChunkPos chunkPos, BiFunction<Integer, Integer, IslandField.ColumnData> columnDataResolver) {
      int halo = 2;
      int size = 16 + halo * 2;
      int worldMinX = chunkPos.getMinBlockX() - halo;
      int worldMinZ = chunkPos.getMinBlockZ() - halo;
      SkyIslandChunkTerrainSnapshot.SnapshotColumnData[][] columns = new SkyIslandChunkTerrainSnapshot.SnapshotColumnData[size][size];

      for (int dx = 0; dx < size; dx++) {
         for (int dz = 0; dz < size; dz++) {
            int x = worldMinX + dx;
            int z = worldMinZ + dz;
            IslandField.ColumnData columnData = columnDataResolver.apply(x, z);
            columns[dx][dz] = new SkyIslandChunkTerrainSnapshot.SnapshotColumnData(columnData.segments(), columnData.profile());
         }
      }

      return new SkyIslandChunkTerrainSnapshot(halo, columns, worldMinX, worldMinZ);
   }

   public static SkyIslandChunkTerrainSnapshot createEmpty(ChunkPos chunkPos) {
      int halo = 2;
      int size = 16 + halo * 2;
      int worldMinX = chunkPos.getMinBlockX() - halo;
      int worldMinZ = chunkPos.getMinBlockZ() - halo;
      SkyIslandChunkTerrainSnapshot.SnapshotColumnData[][] columns = new SkyIslandChunkTerrainSnapshot.SnapshotColumnData[size][size];

      for (int dx = 0; dx < size; dx++) {
         for (int dz = 0; dz < size; dz++) {
            columns[dx][dz] = SkyIslandChunkTerrainSnapshot.SnapshotColumnData.EMPTY;
         }
      }

      return new SkyIslandChunkTerrainSnapshot(halo, columns, worldMinX, worldMinZ);
   }

   public List<TerrainColumn> segmentsAt(int worldX, int worldZ) {
      return this.columnAt(worldX, worldZ).segments();
   }

   public TerrainColumn terrainColumnAt(int worldX, int worldZ) {
      return this.tryColumnAt(worldX, worldZ).terrainColumn();
   }

   public IslandField.ColumnProfile profileAt(int worldX, int worldZ) {
      return this.columnAt(worldX, worldZ).profile();
   }

   public boolean isSteepAt(int worldX, int worldZ, int surfaceY) {
      int steepNeighbors = 0;

      for (int[] offset : STEEP_NEIGHBOR_OFFSETS) {
         TerrainColumn neighbor = this.columnAt(worldX + offset[0], worldZ + offset[1]).terrainColumn();
         if (!neighbor.exists()) {
            steepNeighbors++;
         } else if (Math.abs(neighbor.topY() - surfaceY) >= 5) {
            steepNeighbors++;
         }
      }

      return steepNeighbors >= 3;
   }

   private SkyIslandChunkTerrainSnapshot.SnapshotColumnData columnAt(int worldX, int worldZ) {
      return this.tryColumnAt(worldX, worldZ);
   }

   private SkyIslandChunkTerrainSnapshot.SnapshotColumnData tryColumnAt(int worldX, int worldZ) {
      int localX = worldX - this.worldMinX;
      int localZ = worldZ - this.worldMinZ;
      return localX >= 0 && localZ >= 0 && localX < this.columns.length && localZ < this.columns[0].length
         ? this.columns[localX][localZ]
         : SkyIslandChunkTerrainSnapshot.SnapshotColumnData.EMPTY;
   }

   private record SnapshotColumnData(List<TerrainColumn> segments, IslandField.ColumnProfile profile) {
      private static final SkyIslandChunkTerrainSnapshot.SnapshotColumnData EMPTY = new SkyIslandChunkTerrainSnapshot.SnapshotColumnData(
         List.of(), new IslandField.ColumnProfile(1, 0, false, false, false, false, IslandField.IslandFamily.SATELLITE)
      );

      TerrainColumn terrainColumn() {
         return this.profile.terrainColumn();
      }
   }
}
