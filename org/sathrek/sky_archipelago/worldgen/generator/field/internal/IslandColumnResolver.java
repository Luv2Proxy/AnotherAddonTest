package org.sathrek.sky_archipelago.worldgen.generator.field.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.WorldgenPerformanceMetrics;
import org.sathrek.sky_archipelago.worldgen.generator.field.BiomeTerrainShaper;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;

public final class IslandColumnResolver {
   private static final int SEARCH_RADIUS_CELLS = 3;
   private final IslandShapeSampler shapeSampler;
   private final IslandDensityEvaluator densityEvaluator;
   private final IslandOverlapResolver overlapResolver = new IslandOverlapResolver();

   public IslandColumnResolver(IslandShapeSampler shapeSampler, IslandDensityEvaluator densityEvaluator) {
      this.shapeSampler = shapeSampler;
      this.densityEvaluator = densityEvaluator;
   }

   public IslandField.ColumnData resolveColumnData(
      int x,
      int z,
      SkyIslandSettings settings,
      int spacing,
      Function<IslandColumnResolver.IslandLookupKey, List<IslandField.IslandDescriptor>> islandsForCell,
      BiFunction<Integer, Integer, BiomeTerrainShaper.TerrainProfile> terrainSampler,
      int top,
      int bottom
   ) {
      long resolveStartNanos = System.nanoTime();
      IslandColumnResolver.ResolvedContributors resolvedContributors = this.resolveContributorsWithMetrics(
         x, z, settings, spacing, islandsForCell, terrainSampler, top, bottom
      );
      return this.resolveColumnDataFromContributors(
         x, z, settings, terrainSampler, top, bottom, resolveStartNanos, resolvedContributors.contributors(), resolvedContributors.candidateChecks(), false
      );
   }

   public IslandField.ColumnData resolveColumnDataFromCandidates(
      int x,
      int z,
      SkyIslandSettings settings,
      int spacing,
      List<IslandColumnResolver.SnapshotCandidate> candidates,
      BiFunction<Integer, Integer, BiomeTerrainShaper.TerrainProfile> terrainSampler,
      int top,
      int bottom
   ) {
      long resolveStartNanos = System.nanoTime();
      List<IslandField.ResolvedContributor> contributors = new ArrayList<>();
      int candidateChecks = 0;
      int centerCellX = Mth.floorDiv(x, spacing);
      int centerCellZ = Mth.floorDiv(z, spacing);

      for (IslandColumnResolver.SnapshotCandidate candidate : candidates) {
         if (Math.abs(candidate.cellX() - centerCellX) <= 3 && Math.abs(candidate.cellZ() - centerCellZ) <= 3) {
            candidateChecks++;
            IslandField.IslandDescriptor descriptor = candidate.descriptor();
            if (mightReachColumn(descriptor, x, z)) {
               IslandField.HorizontalSample horizontal = this.shapeSampler.sampleHorizontal(descriptor, x, z, settings);
               if (horizontal.inInfluence()) {
                  BiomeTerrainShaper.TerrainProfile terrainProfile = terrainSampler.apply(descriptor.centerY(), 0);
                  int minY = Math.max(bottom, descriptor.centerY() - descriptor.maxVerticalReach() - 24);
                  int maxY = Math.min(top, descriptor.centerY() + descriptor.maxVerticalReach() + 24);
                  contributors.add(new IslandField.ResolvedContributor(descriptor, horizontal, terrainProfile, minY, maxY));
               }
            }
         }
      }

      return this.resolveColumnDataFromContributors(x, z, settings, terrainSampler, top, bottom, resolveStartNanos, contributors, candidateChecks, true);
   }

   private IslandField.ColumnData resolveColumnDataFromContributors(
      int x,
      int z,
      SkyIslandSettings settings,
      BiFunction<Integer, Integer, BiomeTerrainShaper.TerrainProfile> terrainSampler,
      int top,
      int bottom,
      long resolveStartNanos,
      List<IslandField.ResolvedContributor> contributors,
      int candidateChecks,
      boolean snapshotCandidates
   ) {
      if (contributors.isEmpty()) {
         WorldgenPerformanceMetrics.recordColumnResolve(System.nanoTime() - resolveStartNanos, 0, 0, 0L, candidateChecks, true, snapshotCandidates);
         return IslandField.ColumnData.EMPTY;
      }

      List<TerrainColumn> rawSegments = new ArrayList<>();
      int segmentTop = Integer.MIN_VALUE;
      int yScans = 0;
      long densityEvaluations = 0L;
      int scanTop = contributors.stream().mapToInt(IslandField.ResolvedContributor::maxY).max().orElse(top);
      int scanBottom = contributors.stream().mapToInt(IslandField.ResolvedContributor::minY).min().orElse(bottom);

      for (int y = scanTop; y >= scanBottom; y--) {
         yScans++;
         double density = Double.NEGATIVE_INFINITY;

         for (IslandField.ResolvedContributor contributor : contributors) {
            if (y >= contributor.minY() && y <= contributor.maxY()) {
               densityEvaluations++;
               density = Math.max(
                  density,
                  this.densityEvaluator
                     .sampleDescriptorDensity(contributor.descriptor(), contributor.horizontal(), x, y, z, settings, contributor.terrainProfile())
               );
            }
         }

         boolean solid = density > 0.0;
         if (solid) {
            if (segmentTop == Integer.MIN_VALUE) {
               segmentTop = y;
            }
         } else if (segmentTop != Integer.MIN_VALUE) {
            rawSegments.add(new TerrainColumn(y + 1, segmentTop));
            segmentTop = Integer.MIN_VALUE;
         }
      }

      if (segmentTop != Integer.MIN_VALUE) {
         rawSegments.add(new TerrainColumn(scanBottom, segmentTop));
      }

      if (rawSegments.isEmpty()) {
         WorldgenPerformanceMetrics.recordColumnResolve(
            System.nanoTime() - resolveStartNanos, contributors.size(), yScans, densityEvaluations, candidateChecks, true, snapshotCandidates
         );
         return IslandField.ColumnData.EMPTY;
      }

      List<IslandOverlapResolver.SegmentCandidate> rawCandidates = new ArrayList<>(rawSegments.size());

      for (TerrainColumn segment : rawSegments) {
         IslandField.DominantContributor segmentContributor = this.findDominantContributor(x, segment.topY(), z, settings, contributors);
         rawCandidates.add(new IslandOverlapResolver.SegmentCandidate(segment, segmentContributor.descriptor(), segmentContributor.horizontal()));
      }

      List<TerrainColumn> segments = this.overlapResolver.resolve(settings.advanced().terrainOverlapMode(), rawCandidates);
      if (segments.isEmpty()) {
         WorldgenPerformanceMetrics.recordColumnResolve(
            System.nanoTime() - resolveStartNanos, contributors.size(), yScans, densityEvaluations, candidateChecks, true, snapshotCandidates
         );
         return IslandField.ColumnData.EMPTY;
      } else {
         TerrainColumn topSegment = segments.get(0);
         IslandField.DominantContributor contributor = this.findDominantContributor(x, topSegment.topY(), z, settings, contributors);
         IslandField.HorizontalSample horizontal = contributor.horizontal();
         IslandField.IslandFamily family = contributor.descriptor() != null ? contributor.descriptor().family() : IslandField.IslandFamily.SATELLITE;
         double plateauCore = contributor.descriptor() != null ? contributor.descriptor().plateauCore() : 0.3;
         boolean topCap = horizontal.coverage() >= plateauCore;
         boolean cliffBand = !topCap && horizontal.coverage() > 0.05;
         boolean undersideFringe = segments.size() > 1 || horizontal.branchCoverage() > 0.16;
         boolean vegetationHangZone = cliffBand || horizontal.branchCoverage() > 0.22;
         IslandField.ColumnData columnData = new IslandField.ColumnData(
            List.copyOf(segments),
            new IslandField.ColumnProfile(topSegment.bottomY(), topSegment.topY(), topCap, cliffBand, undersideFringe, vegetationHangZone, family),
            contributor.descriptor()
         );
         WorldgenPerformanceMetrics.recordColumnResolve(
            System.nanoTime() - resolveStartNanos, contributors.size(), yScans, densityEvaluations, candidateChecks, false, snapshotCandidates
         );
         return columnData;
      }
   }

   public List<IslandField.ResolvedContributor> resolveContributors(
      int x,
      int z,
      SkyIslandSettings settings,
      int spacing,
      Function<IslandColumnResolver.IslandLookupKey, List<IslandField.IslandDescriptor>> islandsForCell,
      BiFunction<Integer, Integer, BiomeTerrainShaper.TerrainProfile> terrainSampler,
      int top,
      int bottom
   ) {
      return this.resolveContributorsWithMetrics(x, z, settings, spacing, islandsForCell, terrainSampler, top, bottom).contributors();
   }

   private IslandColumnResolver.ResolvedContributors resolveContributorsWithMetrics(
      int x,
      int z,
      SkyIslandSettings settings,
      int spacing,
      Function<IslandColumnResolver.IslandLookupKey, List<IslandField.IslandDescriptor>> islandsForCell,
      BiFunction<Integer, Integer, BiomeTerrainShaper.TerrainProfile> terrainSampler,
      int top,
      int bottom
   ) {
      int centerCellX = Mth.floorDiv(x, spacing);
      int centerCellZ = Mth.floorDiv(z, spacing);
      List<IslandField.ResolvedContributor> contributors = new ArrayList<>();
      int candidateChecks = 0;

      for (int cellX = centerCellX - 3; cellX <= centerCellX + 3; cellX++) {
         for (int cellZ = centerCellZ - 3; cellZ <= centerCellZ + 3; cellZ++) {
            for (IslandField.IslandDescriptor descriptor : islandsForCell.apply(new IslandColumnResolver.IslandLookupKey(cellX, cellZ, settings))) {
               candidateChecks++;
               if (mightReachColumn(descriptor, x, z)) {
                  IslandField.HorizontalSample horizontal = this.shapeSampler.sampleHorizontal(descriptor, x, z, settings);
                  if (horizontal.inInfluence()) {
                     BiomeTerrainShaper.TerrainProfile terrainProfile = terrainSampler.apply(descriptor.centerY(), 0);
                     int minY = Math.max(bottom, descriptor.centerY() - descriptor.maxVerticalReach() - 24);
                     int maxY = Math.min(top, descriptor.centerY() + descriptor.maxVerticalReach() + 24);
                     contributors.add(new IslandField.ResolvedContributor(descriptor, horizontal, terrainProfile, minY, maxY));
                  }
               }
            }
         }
      }

      return new IslandColumnResolver.ResolvedContributors(contributors, candidateChecks);
   }

   public static boolean mightReachColumn(IslandField.IslandDescriptor descriptor, int x, int z) {
      long dx = (long)x - descriptor.centerX();
      long dz = (long)z - descriptor.centerZ();
      int reach = Math.max(
         descriptor.maxRadius() * 3 + 96,
         Math.max(Math.abs(descriptor.hangOffsetX()) + descriptor.tailRadiusX() * 2, Math.abs(descriptor.hangOffsetZ()) + descriptor.tailRadiusZ() * 2) + 96
      );
      return dx * dx + dz * dz <= (long)reach * reach;
   }

   public static boolean mightReachArea(IslandField.IslandDescriptor descriptor, int minX, int minZ, int maxX, int maxZ) {
      long nearestX = Math.max(minX, Math.min(maxX, descriptor.centerX()));
      long nearestZ = Math.max(minZ, Math.min(maxZ, descriptor.centerZ()));
      long dx = nearestX - descriptor.centerX();
      long dz = nearestZ - descriptor.centerZ();
      int reach = Math.max(
         descriptor.maxRadius() * 3 + 96,
         Math.max(Math.abs(descriptor.hangOffsetX()) + descriptor.tailRadiusX() * 2, Math.abs(descriptor.hangOffsetZ()) + descriptor.tailRadiusZ() * 2) + 96
      );
      return dx * dx + dz * dz <= (long)reach * reach;
   }

   public IslandField.DominantContributor findDominantContributor(
      int x, int y, int z, SkyIslandSettings settings, List<IslandField.ResolvedContributor> contributors
   ) {
      IslandField.IslandDescriptor bestDescriptor = null;
      IslandField.HorizontalSample bestHorizontal = IslandField.HorizontalSample.EMPTY;
      double bestDensity = Double.NEGATIVE_INFINITY;

      for (IslandField.ResolvedContributor contributor : contributors) {
         if (y >= contributor.minY() && y <= contributor.maxY()) {
            double density = this.densityEvaluator
               .sampleDescriptorDensity(contributor.descriptor(), contributor.horizontal(), x, y, z, settings, contributor.terrainProfile());
            if (density > bestDensity) {
               bestDensity = density;
               bestDescriptor = contributor.descriptor();
               bestHorizontal = contributor.horizontal();
            }
         }
      }

      return new IslandField.DominantContributor(bestDescriptor, bestHorizontal);
   }

   public record IslandLookupKey(int cellX, int cellZ, SkyIslandSettings settings) {
   }

   private record ResolvedContributors(List<IslandField.ResolvedContributor> contributors, int candidateChecks) {
   }

   public record SnapshotCandidate(int cellX, int cellZ, IslandField.IslandDescriptor descriptor) {
   }
}
