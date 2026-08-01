package org.sathrek.sky_archipelago.worldgen.generator.field.cache;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandFieldCacheSupport;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandChunkTerrainSnapshot;

public final class IslandFieldCaches {
   private static final long CACHE_TTL_NANOS = TimeUnit.MINUTES.toNanos(2L);
   private static final int CACHE_SWEEP_BUDGET = 128;
   private static final long CACHE_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(15L);
   private static final int COLUMN_CACHE_HIGH_WATERMARK = 1000000;
   private static final int COLUMN_CACHE_LOW_WATERMARK = 800000;
   private static final int COLUMN_CACHE_TRIM_BATCH = 16384;
   private static final long SNAPSHOT_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(30L);
   private static final int SNAPSHOT_CACHE_HIGH_WATERMARK = 4096;
   private static final int SNAPSHOT_CACHE_LOW_WATERMARK = 3072;
   private static final int SNAPSHOT_CACHE_TRIM_BATCH = 256;
   private final IslandFieldCacheSupport.TimedCache<ClusterCacheKey, IslandField.ClusterDescriptor> clusterCache = new IslandFieldCacheSupport.TimedCache<>(
      "cluster", CACHE_TTL_NANOS, 128, SkyIslandServerConfig::cacheMetricsLogsEnabled, CACHE_LOG_INTERVAL_NANOS, SkyArchipelago.LOGGER::info
   );
   private final IslandFieldCacheSupport.TimedCache<ClusterCacheKey, Boolean> inactiveClusterCache = new IslandFieldCacheSupport.TimedCache<>(
      "inactive_cluster", CACHE_TTL_NANOS, 128, SkyIslandServerConfig::cacheMetricsLogsEnabled, CACHE_LOG_INTERVAL_NANOS, SkyArchipelago.LOGGER::info
   );
   private final IslandFieldCacheSupport.TimedCache<ClusterCacheKey, List<IslandField.IslandDescriptor>> islandDescriptorCache = new IslandFieldCacheSupport.TimedCache<>(
      "island_descriptor", CACHE_TTL_NANOS, 128, SkyIslandServerConfig::cacheMetricsLogsEnabled, CACHE_LOG_INTERVAL_NANOS, SkyArchipelago.LOGGER::info
   );
   private final IslandFieldCacheSupport.TimedCache<ColumnCacheKey, IslandField.ColumnData> columnCache = new IslandFieldCacheSupport.TimedCache<>(
      "column",
      CACHE_TTL_NANOS,
      128,
      1000000,
      800000,
      16384,
      SkyIslandServerConfig::cacheMetricsLogsEnabled,
      CACHE_LOG_INTERVAL_NANOS,
      SkyArchipelago.LOGGER::info,
      new IslandFieldCacheSupport.AdaptivePolicy(
         SkyIslandServerConfig::adaptiveColumnCacheEnabled,
         SkyIslandServerConfig::adaptiveColumnCacheMinTtlSeconds,
         SkyIslandServerConfig::adaptiveColumnCacheMaxTtlSeconds,
         SkyIslandServerConfig::adaptiveColumnCacheRetuneIntervalSeconds,
         SkyIslandServerConfig::adaptiveColumnCacheAdjustmentPercent,
         SkyIslandServerConfig::adaptiveColumnCacheHighPressureMissRateThreshold,
         SkyIslandServerConfig::adaptiveColumnCacheTargetHitRate
      )
   );
   private final IslandFieldCacheSupport.TimedCache<SnapshotCacheKey, SkyIslandChunkTerrainSnapshot> chunkTerrainSnapshotCache = new IslandFieldCacheSupport.TimedCache<>(
      "chunk_terrain_snapshot",
      SNAPSHOT_CACHE_TTL_NANOS,
      128,
      4096,
      3072,
      256,
      SkyIslandServerConfig::cacheMetricsLogsEnabled,
      CACHE_LOG_INTERVAL_NANOS,
      SkyArchipelago.LOGGER::info
   );

   public ClusterCacheKey clusterKey(int cellX, int cellZ, SkyIslandSettings settings, long forcedRevision) {
      IslandFieldSettingsKey settingsKey = IslandFieldSettingsKey.from(settings);
      return new ClusterCacheKey(cellX, cellZ, settingsKey, forcedRevision);
   }

   public ColumnCacheKey columnKey(int x, int z, SkyIslandSettings settings, long forcedRevision) {
      IslandFieldSettingsKey settingsKey = IslandFieldSettingsKey.from(settings);
      return new ColumnCacheKey(x, z, settingsKey, forcedRevision);
   }

   public SnapshotCacheKey snapshotKey(int chunkX, int chunkZ, int halo, SkyIslandSettings settings, long forcedRevision) {
      IslandFieldSettingsKey settingsKey = IslandFieldSettingsKey.from(settings);
      return new SnapshotCacheKey(chunkX, chunkZ, halo, settingsKey, forcedRevision);
   }

   public IslandFieldCacheSupport.TimedCache<ClusterCacheKey, IslandField.ClusterDescriptor> clusterCache() {
      return this.clusterCache;
   }

   public IslandFieldCacheSupport.TimedCache<ClusterCacheKey, Boolean> inactiveClusterCache() {
      return this.inactiveClusterCache;
   }

   public IslandFieldCacheSupport.TimedCache<ClusterCacheKey, List<IslandField.IslandDescriptor>> islandDescriptorCache() {
      return this.islandDescriptorCache;
   }

   public IslandFieldCacheSupport.TimedCache<ColumnCacheKey, IslandField.ColumnData> columnCache() {
      return this.columnCache;
   }

   public IslandFieldCacheSupport.TimedCache<SnapshotCacheKey, SkyIslandChunkTerrainSnapshot> chunkTerrainSnapshotCache() {
      return this.chunkTerrainSnapshotCache;
   }
}
