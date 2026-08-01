package org.sathrek.sky_archipelago.worldgen.generator.field;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.config.ClusterSpacingMode;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.WorldgenPerformanceMetrics;
import org.sathrek.sky_archipelago.worldgen.generator.field.biomepolicy.BiomeIslandSpawnPolicy;
import org.sathrek.sky_archipelago.worldgen.generator.field.biomepolicy.CompositeBiomeIslandSpawnPolicy;
import org.sathrek.sky_archipelago.worldgen.generator.field.biomepolicy.OceanBiomeExclusionRule;
import org.sathrek.sky_archipelago.worldgen.generator.field.cache.ClusterCacheKey;
import org.sathrek.sky_archipelago.worldgen.generator.field.cache.IslandFieldCaches;
import org.sathrek.sky_archipelago.worldgen.generator.field.cache.SnapshotCacheKey;
import org.sathrek.sky_archipelago.worldgen.generator.field.forced.ForcedIslandRegistry;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandClusterSampler;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandColumnResolver;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandDensityEvaluator;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandDescriptorFactory;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandNoise;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandShapeSampler;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandChunkTerrainSnapshot;

public final class IslandField {
   private static final int SEARCH_RADIUS_CELLS = 3;
   private static final int ACTIVE_SALT = 101;
   private static final int DESCRIPTOR_SEED_SALT = 337;
   private static final int TOPOGRAPHY_SALT = 367;
   private static final int EROSION_SALT = 397;
   private static final int DETAIL_SALT = 431;
   private final long layoutSeed;
   private final IslandField.BiomeTerrainSampler biomeTerrainSampler;
   private final BiomeIslandSpawnPolicy biomeIslandSpawnPolicy;
   private final IslandNoise noise;
   private final IslandClusterSampler clusterSampler;
   private final IslandDescriptorFactory descriptorFactory;
   private final IslandShapeSampler shapeSampler;
   private final IslandDensityEvaluator densityEvaluator;
   private final IslandColumnResolver columnResolver;
   private final ForcedIslandRegistry forcedRegistry = new ForcedIslandRegistry();
   private final IslandFieldCaches caches = new IslandFieldCaches();

   public IslandField(long layoutSeed) {
      this(layoutSeed, (x, y, z) -> new IslandField.BiomeSample(BiomeTerrainShaper.neutral(), false));
   }

   public IslandField(long layoutSeed, IslandField.BiomeTerrainSampler biomeTerrainSampler) {
      this.layoutSeed = layoutSeed;
      this.biomeTerrainSampler = biomeTerrainSampler;
      this.biomeIslandSpawnPolicy = new CompositeBiomeIslandSpawnPolicy(List.of(new OceanBiomeExclusionRule()));
      this.noise = new IslandNoise(layoutSeed);
      this.clusterSampler = new IslandClusterSampler(this.noise);
      this.descriptorFactory = new IslandDescriptorFactory(this.noise, this.clusterSampler);
      this.shapeSampler = new IslandShapeSampler(this.noise, 367, 397);
      this.densityEvaluator = new IslandDensityEvaluator(this.noise, this.shapeSampler, 367, 397, 431);
      this.columnResolver = new IslandColumnResolver(this.shapeSampler, this.densityEvaluator);
   }

   public long layoutSeed() {
      return this.layoutSeed;
   }

   public double sampleDensity(int x, int y, int z, SkyIslandSettings settings) {
      int spacing = this.effectiveClusterSpacing(settings);
      int centerCellX = Mth.floorDiv(x, spacing);
      int centerCellZ = Mth.floorDiv(z, spacing);
      double bestDensity = Double.NEGATIVE_INFINITY;

      for (int cellX = centerCellX - 3; cellX <= centerCellX + 3; cellX++) {
         for (int cellZ = centerCellZ - 3; cellZ <= centerCellZ + 3; cellZ++) {
            IslandField.ClusterDescriptor cluster = this.sampleCluster(cellX, cellZ, settings);
            if (cluster != null) {
               for (IslandField.IslandDescriptor descriptor : this.buildClusterIslands(cluster, settings)) {
                  if (Math.abs(y - descriptor.centerY()) <= descriptor.maxVerticalReach() + 24) {
                     IslandField.HorizontalSample horizontal = this.sampleHorizontal(descriptor, x, z, settings);
                     if (horizontal.inInfluence()) {
                        BiomeTerrainShaper.TerrainProfile terrainProfile = this.biomeTerrainSampler.sample(x, descriptor.centerY(), z).terrainProfile();
                        bestDensity = Math.max(bestDensity, this.sampleDescriptorDensity(descriptor, horizontal, x, y, z, settings, terrainProfile));
                     }
                  }
               }
            }
         }
      }

      return bestDensity;
   }

   public boolean isSolidAt(int x, int y, int z, SkyIslandSettings settings) {
      return this.sampleDensity(x, y, z, settings) > 0.0;
   }

   public TerrainColumn sampleColumn(int x, int z, SkyIslandSettings settings) {
      return this.sampleColumnData(x, z, settings).terrainColumn();
   }

   public List<TerrainColumn> sampleSolidSegments(int x, int z, SkyIslandSettings settings) {
      return this.sampleColumnData(x, z, settings).segments();
   }

   public int countSolidSegments(int x, int z, SkyIslandSettings settings) {
      return this.sampleColumnData(x, z, settings).segments().size();
   }

   public IslandField.ColumnProfile sampleColumnProfile(int x, int z, SkyIslandSettings settings) {
      return this.sampleColumnData(x, z, settings).profile();
   }

   public boolean isOceanBiomeAt(int x, int y, int z) {
      return this.biomeTerrainSampler.sample(x, y, z).isOceanBiome();
   }

   public IslandField.IslandDescriptor sampleDominantIslandDescriptor(int x, int z, SkyIslandSettings settings) {
      return this.sampleColumnData(x, z, settings).dominantDescriptor();
   }

   public SkyIslandChunkTerrainSnapshot sampleChunkTerrainSnapshot(ChunkPos chunkPos, SkyIslandSettings settings) {
      SnapshotCacheKey cacheKey = this.caches.snapshotKey(chunkPos.x, chunkPos.z, 2, settings, this.forcedRegistry.revision());
      SkyIslandChunkTerrainSnapshot cached = this.caches.chunkTerrainSnapshotCache().get(cacheKey);
      if (cached != null) {
         WorldgenPerformanceMetrics.recordTerrainSnapshotCacheHit();
         return cached;
      } else {
         WorldgenPerformanceMetrics.recordTerrainSnapshotCacheMiss();
         SkyIslandChunkTerrainSnapshot snapshot = this.createChunkTerrainSnapshot(chunkPos, settings);
         this.caches.chunkTerrainSnapshotCache().put(cacheKey, snapshot);
         return snapshot;
      }
   }

   public boolean hasSupportBelow(int x, int z, int fromYInclusive, int depth, SkyIslandSettings settings) {
      int minY = Math.max(this.scanBottom(settings), fromYInclusive - depth + 1);
      int maxY = Math.min(this.scanTop(settings), fromYInclusive);

      for (TerrainColumn segment : this.sampleColumnData(x, z, settings).segments()) {
         if (segment.intersectsInclusive(minY, maxY)) {
            return true;
         }
      }

      return false;
   }

   IslandField.ColumnData sampleColumnData(int x, int z, SkyIslandSettings settings) {
      WorldgenPerformanceMetrics.recordColumnSample();
      return this.caches
         .columnCache()
         .computeIfAbsent(this.caches.columnKey(x, z, settings, this.forcedRegistry.revision()), ignored -> this.resolveColumnData(x, z, settings));
   }

   int cachedColumnCount() {
      return this.caches.columnCache().size();
   }

   int cachedChunkTerrainSnapshotCount() {
      return this.caches.chunkTerrainSnapshotCache().size();
   }

   public long forcedDescriptorRevision() {
      return this.forcedRegistry.revision();
   }

   public int countIslandsInRadius(int centerX, int centerZ, int radius, SkyIslandSettings settings) {
      return this.collectIslandPreviewsInRadius(centerX, centerZ, radius, settings).size();
   }

   public List<IslandField.IslandAnchor> collectIslandsInRadius(int centerX, int centerZ, int radius, SkyIslandSettings settings) {
      List<IslandField.IslandAnchor> anchors = new ArrayList<>();

      for (IslandField.IslandPreview preview : this.collectIslandPreviewsInRadius(centerX, centerZ, radius, settings)) {
         anchors.add(new IslandField.IslandAnchor(preview.x(), preview.y(), preview.z(), preview.radius()));
      }

      return anchors;
   }

   public List<IslandField.IslandPreview> collectIslandPreviewsInRadius(int centerX, int centerZ, int radius, SkyIslandSettings settings) {
      int spacing = this.effectiveClusterSpacing(settings);
      int minCellX = Mth.floorDiv(centerX - radius, spacing) - 1;
      int maxCellX = Mth.floorDiv(centerX + radius, spacing) + 1;
      int minCellZ = Mth.floorDiv(centerZ - radius, spacing) - 1;
      int maxCellZ = Mth.floorDiv(centerZ + radius, spacing) + 1;
      List<IslandField.IslandPreview> previews = new ArrayList<>();

      for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
         for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
            IslandField.ClusterDescriptor cluster = this.sampleCluster(cellX, cellZ, settings);
            if (cluster != null) {
               for (IslandField.IslandDescriptor descriptor : this.buildClusterIslands(cluster, settings)) {
                  double dx = descriptor.centerX() - centerX;
                  double dz = descriptor.centerZ() - centerZ;
                  if (dx * dx + dz * dz <= (double)radius * radius) {
                     previews.add(
                        new IslandField.IslandPreview(
                           descriptor.archetype(),
                           descriptor.family(),
                           cluster.heightBand(),
                           descriptor.centerX(),
                           descriptor.centerY(),
                           descriptor.centerZ(),
                           descriptor.maxRadius(),
                           descriptor.plateauHeight(),
                           descriptor.hangDepth()
                        )
                     );
                  }
               }
            }
         }
      }

      previews.sort(
         Comparator.comparingInt(IslandField.IslandPreview::x)
            .thenComparingInt(IslandField.IslandPreview::z)
            .thenComparingInt(IslandField.IslandPreview::radius)
            .thenComparingInt(IslandField.IslandPreview::y)
      );
      return previews;
   }

   public int scanTop(SkyIslandSettings settings) {
      return Math.min(2000, settings.terrain().maxIslandY() + settings.maxIslandThickness() + Math.max(48, settings.terrain().maxIslandRadius() + 24));
   }

   public int scanBottom(SkyIslandSettings settings) {
      return Math.max(-64, settings.terrain().minIslandY() - Math.max(96, settings.terrain().maxIslandRadius() * 3));
   }

   private IslandField.ColumnData resolveColumnData(int x, int z, SkyIslandSettings settings) {
      int spacing = this.effectiveClusterSpacing(settings);
      return this.columnResolver
         .resolveColumnData(
            x,
            z,
            settings,
            spacing,
            key -> this.descriptorsForCell(key.cellX(), key.cellZ(), key.settings()),
            (centerY, ignored) -> this.biomeTerrainSampler.sample(x, centerY, z).terrainProfile(),
            this.scanTop(settings),
            this.scanBottom(settings)
         );
   }

   private SkyIslandChunkTerrainSnapshot createChunkTerrainSnapshot(ChunkPos chunkPos, SkyIslandSettings settings) {
      int halo = 2;
      int size = 16 + halo * 2;
      int minX = chunkPos.getMinBlockX() - halo;
      int minZ = chunkPos.getMinBlockZ() - halo;
      int maxX = minX + size - 1;
      int maxZ = minZ + size - 1;
      int spacing = this.effectiveClusterSpacing(settings);
      List<IslandColumnResolver.SnapshotCandidate> candidates = this.collectSnapshotCandidates(minX, minZ, maxX, maxZ, spacing, settings);
      WorldgenPerformanceMetrics.recordTerrainSnapshotCandidateSet(candidates.size(), candidates.isEmpty());
      if (candidates.isEmpty()) {
         return SkyIslandChunkTerrainSnapshot.createEmpty(chunkPos);
      }

      int top = this.scanTop(settings);
      int bottom = this.scanBottom(settings);
      return SkyIslandChunkTerrainSnapshot.createFromColumnData(
         chunkPos,
         (x, z) -> {
            WorldgenPerformanceMetrics.recordColumnSample();
            return this.caches
               .columnCache()
               .computeIfAbsent(
                  this.caches.columnKey(x, z, settings, this.forcedRegistry.revision()),
                  ignored -> this.columnResolver
                     .resolveColumnDataFromCandidates(
                        x, z, settings, spacing, candidates, (centerY, unused) -> this.biomeTerrainSampler.sample(x, centerY, z).terrainProfile(), top, bottom
                     )
               );
         }
      );
   }

   private List<IslandColumnResolver.SnapshotCandidate> collectSnapshotCandidates(
      int minX, int minZ, int maxX, int maxZ, int spacing, SkyIslandSettings settings
   ) {
      int minCellX = Mth.floorDiv(minX, spacing) - 3;
      int maxCellX = Mth.floorDiv(maxX, spacing) + 3;
      int minCellZ = Mth.floorDiv(minZ, spacing) - 3;
      int maxCellZ = Mth.floorDiv(maxZ, spacing) + 3;
      ArrayList<IslandColumnResolver.SnapshotCandidate> candidates = new ArrayList<>();

      for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
         for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
            for (IslandField.IslandDescriptor descriptor : this.descriptorsForCell(cellX, cellZ, settings)) {
               if (IslandColumnResolver.mightReachArea(descriptor, minX, minZ, maxX, maxZ)) {
                  candidates.add(new IslandColumnResolver.SnapshotCandidate(cellX, cellZ, descriptor));
               }
            }
         }
      }

      return List.copyOf(candidates);
   }

   private List<IslandField.IslandDescriptor> descriptorsForCell(int cellX, int cellZ, SkyIslandSettings settings) {
      IslandField.ClusterDescriptor cluster = this.sampleCluster(cellX, cellZ, settings);
      List<IslandField.IslandDescriptor> forced = this.forcedRegistry.forcedDescriptorsForCell(cellX, cellZ);
      if (cluster == null) {
         return forced;
      }

      List<IslandField.IslandDescriptor> natural = this.buildClusterIslands(cluster, settings);
      if (forced.isEmpty()) {
         return natural;
      }

      ArrayList<IslandField.IslandDescriptor> merged = new ArrayList<>(natural.size() + forced.size());
      merged.addAll(natural);
      merged.addAll(forced);
      return List.copyOf(merged);
   }

   private IslandField.ClusterDescriptor sampleCluster(int cellX, int cellZ, SkyIslandSettings settings) {
      ClusterCacheKey cacheKey = this.caches.clusterKey(cellX, cellZ, settings, this.forcedRegistry.revision());
      IslandField.ClusterDescriptor cached = this.caches.clusterCache().get(cacheKey);
      if (cached != null) {
         return cached;
      } else if (this.caches.inactiveClusterCache().contains(cacheKey)) {
         return null;
      } else {
         double activation = this.sample01(cellX, cellZ, 101);
         if (activation >= settings.terrain().islandDensity()) {
            this.caches.inactiveClusterCache().put(cacheKey, Boolean.TRUE);
            return null;
         } else if (!this.isClusterBiomeAllowed(cellX, cellZ, settings)) {
            this.caches.inactiveClusterCache().put(cacheKey, Boolean.TRUE);
            return null;
         } else {
            int spacing = this.effectiveClusterSpacing(settings);
            IslandField.ClusterDescriptor clusterDescriptor = this.clusterSampler
               .sampleClusterDescriptor(cellX, cellZ, this.layoutSeed, spacing, settings, 337);
            this.caches.clusterCache().put(cacheKey, clusterDescriptor);
            return clusterDescriptor;
         }
      }
   }

   private List<IslandField.IslandDescriptor> buildClusterIslands(IslandField.ClusterDescriptor cluster, SkyIslandSettings settings) {
      ClusterCacheKey cacheKey = this.caches.clusterKey(cluster.cellX(), cluster.cellZ(), settings, this.forcedRegistry.revision());
      List<IslandField.IslandDescriptor> cached = this.caches.islandDescriptorCache().get(cacheKey);
      if (cached != null) {
         return cached;
      }

      List<IslandField.IslandDescriptor> descriptors = new ArrayList<>();
      descriptors.add(this.descriptorFactory.createAnchorDescriptor(cluster, settings));
      if (settings.advanced().clusterCompanionIslandsEnabled()) {
         for (int index = 0; index < cluster.satelliteCount(); index++) {
            descriptors.add(this.descriptorFactory.createSatelliteDescriptor(cluster, settings, index));
         }

         for (int index = 0; index < cluster.spireCount(); index++) {
            descriptors.add(this.descriptorFactory.createSpireDescriptor(cluster, settings, index));
         }
      }

      descriptors = this.forcedRegistry.filterAnchorsInsideProtectionZones(descriptors);
      List<IslandField.IslandDescriptor> frozen = List.copyOf(descriptors);
      this.caches.islandDescriptorCache().put(cacheKey, frozen);
      return frozen;
   }

   public boolean injectForcedHostIsland(int centerX, int centerY, int centerZ, int minRadius, int maxRadius, long seedTag, SkyIslandSettings settings) {
      int spacing = this.effectiveClusterSpacing(settings);
      return this.forcedRegistry.injectForcedHostIsland(this.layoutSeed, spacing, centerX, centerY, centerZ, minRadius, maxRadius, seedTag, settings);
   }

   private IslandField.HorizontalSample sampleHorizontal(IslandField.IslandDescriptor descriptor, int x, int z, SkyIslandSettings settings) {
      return this.shapeSampler.sampleHorizontal(descriptor, x, z, settings);
   }

   private double sampleDescriptorDensity(
      IslandField.IslandDescriptor descriptor,
      IslandField.HorizontalSample horizontal,
      int x,
      int y,
      int z,
      SkyIslandSettings settings,
      BiomeTerrainShaper.TerrainProfile terrainProfile
   ) {
      return this.densityEvaluator.sampleDescriptorDensity(descriptor, horizontal, x, y, z, settings, terrainProfile);
   }

   private double sample01(int cellX, int cellZ, int salt) {
      return this.noise.sample01(cellX, cellZ, salt);
   }

   private boolean isClusterBiomeAllowed(int cellX, int cellZ, SkyIslandSettings settings) {
      int spacing = this.effectiveClusterSpacing(settings);
      int clusterCenterX = cellX * spacing + spacing / 2;
      int clusterCenterZ = cellZ * spacing + spacing / 2;
      int sampleY = (settings.terrain().minIslandY() + settings.terrain().maxIslandY()) / 2;
      IslandField.BiomeSample biomeSample = this.biomeTerrainSampler.sample(clusterCenterX, sampleY, clusterCenterZ);
      return this.biomeIslandSpawnPolicy.allowIslandSpawn(biomeSample, settings);
   }

   private int effectiveClusterSpacing(SkyIslandSettings settings) {
      if (settings.terrain().spacing().clusterSpacingMode() == ClusterSpacingMode.CONSISTENT) {
         return settings.terrain().spacing().clusterSpacing();
      }

      int min = settings.terrain().spacing().minClusterSpacing();
      int max = settings.terrain().spacing().maxClusterSpacing();
      int span = Math.max(0, max - min);
      if (span == 0) {
         return min;
      }

      long mixed = this.layoutSeed ^ -7046029254386353131L;
      int offset = (int)Math.floorMod(mixed, span + 1L);
      return min + offset;
   }

   public record BiomeSample(BiomeTerrainShaper.TerrainProfile terrainProfile, boolean isOceanBiome) {
   }

   @FunctionalInterface
   public interface BiomeTerrainSampler {
      IslandField.BiomeSample sample(int var1, int var2, int var3);
   }

   public record ClusterDescriptor(
      int cellX,
      int cellZ,
      int centerX,
      int centerY,
      int centerZ,
      IslandField.ClusterHeightBand heightBand,
      IslandField.ClusterSizeBand sizeBand,
      IslandField.ClusterTier tier,
      double baseRotation,
      IslandShapeArchetype archetype,
      int satelliteCount,
      int spireCount,
      long seed
   ) {
   }

   public enum ClusterHeightBand {
      LOW,
      MID_HIGH,
      VERY_HIGH;
   }

   public enum ClusterSizeBand {
      RANDOM,
      SMALL,
      MEDIUM,
      LARGE;
   }

   public enum ClusterTier {
      GRAND,
      STANDARD,
      SCATTERED;
   }

   public record ColumnData(List<TerrainColumn> segments, IslandField.ColumnProfile profile, IslandField.IslandDescriptor dominantDescriptor) {
      public static final IslandField.ColumnData EMPTY = new IslandField.ColumnData(List.of(), IslandField.ColumnProfile.EMPTY, null);

      TerrainColumn terrainColumn() {
         return this.profile.terrainColumn();
      }
   }

   public record ColumnProfile(
      int bottomY, int topY, boolean topCap, boolean cliffBand, boolean undersideFringe, boolean vegetationHangZone, IslandField.IslandFamily family
   ) {
      static final IslandField.ColumnProfile EMPTY = new IslandField.ColumnProfile(1, 0, false, false, false, false, IslandField.IslandFamily.SATELLITE);

      public boolean exists() {
         return this.topY >= this.bottomY;
      }

      public TerrainColumn terrainColumn() {
         return this.exists() ? new TerrainColumn(this.bottomY, this.topY) : TerrainColumn.EMPTY;
      }
   }

   public record DominantContributor(IslandField.IslandDescriptor descriptor, IslandField.HorizontalSample horizontal) {
   }

   public record HeightBandProfile(int minY, int maxY) {
   }

   public record HorizontalSample(double localX, double localZ, double coverage, double branchCoverage, double edgeDistance, boolean inInfluence) {
      public static final IslandField.HorizontalSample EMPTY = new IslandField.HorizontalSample(0.0, 0.0, -1.0, -1.0, 1.0, false);
   }

   public record IslandAnchor(int x, int y, int z, int radius) {
   }

   public record IslandDescriptor(
      IslandField.IslandFamily family,
      int centerX,
      int centerY,
      int centerZ,
      int radiusX,
      int radiusZ,
      int maxRadius,
      double rotation,
      IslandShapeArchetype archetype,
      int plateauHeight,
      int cliffDepth,
      int hangDepth,
      int hangOffsetX,
      int hangOffsetZ,
      int tailRadiusX,
      int tailRadiusZ,
      double erosionStrength,
      int lobeCount,
      int peninsulaCount,
      int biteCount,
      long seed
   ) {
      public int maxVerticalReach() {
         return this.plateauHeight + this.cliffDepth + this.hangDepth + 40;
      }

      public double plateauCore() {
         return switch (this.family) {
            case ANCHOR_PLATEAU -> this.archetype == IslandShapeArchetype.CRESCENT ? 0.38 : 0.5;
            case SATELLITE -> 0.36;
            case SPIRE -> 0.26;
         };
      }
   }

   public enum IslandFamily {
      ANCHOR_PLATEAU,
      SATELLITE,
      SPIRE;
   }

   public record IslandPreview(
      IslandShapeArchetype archetype,
      IslandField.IslandFamily family,
      IslandField.ClusterHeightBand heightBand,
      int x,
      int y,
      int z,
      int radius,
      int plateauHeight,
      int hangDepth
   ) {
   }

   public record ResolvedContributor(
      IslandField.IslandDescriptor descriptor, IslandField.HorizontalSample horizontal, BiomeTerrainShaper.TerrainProfile terrainProfile, int minY, int maxY
   ) {
   }

   public record SizeBandProfile(int minRadius, int maxRadius) {
   }
}
