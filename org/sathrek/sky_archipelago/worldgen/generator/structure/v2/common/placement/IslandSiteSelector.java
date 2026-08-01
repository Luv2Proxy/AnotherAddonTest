package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostQuery;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.IslandPlacementSite;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class IslandSiteSelector {
   private static final int OFFSET_STEP = 8;
   private static final int GRID_STEP = 8;
   private final StructureOverlapGuard overlapGuard;

   public IslandSiteSelector(StructureOverlapGuard overlapGuard) {
      this.overlapGuard = overlapGuard;
   }

   public IslandSiteSelector.Selection selectSite(
      HostQuery query,
      HostIsland host,
      StructurePlacementPolicy policy,
      StructurePlacementCategory category,
      StructureFootprint groundingFootprint,
      StructureFootprint effectiveFootprint,
      IslandField islandField,
      SkyIslandSettings settings,
      long levelSeed
   ) {
      EnumMap<IslandSiteSelector.RejectionReason, Integer> rejections = new EnumMap<>(IslandSiteSelector.RejectionReason.class);
      int attempts = 0;
      long seed = offsetSeed(query, host, levelSeed);

      for (IslandSiteSelector.Offset offset : offsets(host.usableRadius(), seed)) {
         attempts++;
         int candidateX = host.preview().x() + offset.x();
         int candidateZ = host.preview().z() + offset.z();
         int dx = candidateX - groundingFootprint.centerX();
         int dz = candidateZ - groundingFootprint.centerZ();
         StructureFootprint testedFootprint = effectiveFootprint.translate(dx, dz);
         IslandSiteSelector.SiteEvaluation evaluation = this.evaluate(host, policy, testedFootprint, candidateX, candidateZ, islandField, settings);
         if (!evaluation.accepted()) {
            increment(rejections, evaluation.rejectionReason());
         } else {
            if (!this.overlapGuard.wouldOverlap(testedFootprint, category, levelSeed)) {
               int topY = evaluation.topY();
               return new IslandSiteSelector.Selection(
                  new IslandPlacementSite(host, candidateX, topY + policy.topOffset(), candidateZ, topY, offset.x(), offset.z(), evaluation.groundedRatio()),
                  Map.copyOf(rejections),
                  attempts
               );
            }

            increment(rejections, IslandSiteSelector.RejectionReason.RESERVATION_OVERLAP);
         }
      }

      return new IslandSiteSelector.Selection(null, Map.copyOf(rejections), attempts);
   }

   public static List<IslandSiteSelector.Offset> offsets(int usableRadius, long seed) {
      ArrayList<IslandSiteSelector.Offset> offsets = new ArrayList<>();
      int maxJitter = Math.min(24, hostSafeThird(usableRadius));
      addUnique(offsets, new IslandSiteSelector.Offset(randomSignedStep(seed, maxJitter, 8), randomSignedStep(seed ^ -7046029254386353131L, maxJitter, 8)));
      addUnique(offsets, new IslandSiteSelector.Offset(0, 0));

      for (int radius = 8; radius <= usableRadius; radius += 8) {
         for (IslandSiteSelector.Offset offset : rotatedRing(radius, seed)) {
            addUnique(offsets, offset);
         }
      }

      return List.copyOf(offsets);
   }

   private IslandSiteSelector.SiteEvaluation evaluate(
      HostIsland host,
      StructurePlacementPolicy policy,
      StructureFootprint footprint,
      int candidateX,
      int candidateZ,
      IslandField islandField,
      SkyIslandSettings settings
   ) {
      if (!insideUsableRadius(host, footprint)) {
         return IslandSiteSelector.SiteEvaluation.rejected(IslandSiteSelector.RejectionReason.OUTSIDE_USABLE_RADIUS);
      }

      TerrainColumn centerColumn = islandField.sampleColumn(candidateX, candidateZ, settings);
      if (!centerColumn.exists()) {
         return IslandSiteSelector.SiteEvaluation.rejected(IslandSiteSelector.RejectionReason.MISSING_COLUMNS);
      }

      List<IslandSiteSelector.Point> samples = samplePoints(footprint);
      int existingSamples = 0;
      int groundedSamples = 0;
      int minTopY = Integer.MAX_VALUE;
      int maxTopY = Integer.MIN_VALUE;

      for (IslandSiteSelector.Point sample : samples) {
         TerrainColumn column = islandField.sampleColumn(sample.x(), sample.z(), settings);
         if (column.exists()) {
            existingSamples++;
            minTopY = Math.min(minTopY, column.topY());
            maxTopY = Math.max(maxTopY, column.topY());
            if (Math.abs(column.topY() - centerColumn.topY()) <= policy.maxTopVariation()) {
               groundedSamples++;
            }
         }
      }

      int minimumRequiredSamples = Math.max(1, (int)Math.ceil(samples.size() * policy.groundedRatio()));
      if (existingSamples < minimumRequiredSamples) {
         return IslandSiteSelector.SiteEvaluation.rejected(IslandSiteSelector.RejectionReason.MISSING_COLUMNS);
      } else {
         double groundedRatio = (double)groundedSamples / samples.size();
         if (groundedRatio < policy.groundedRatio()) {
            return IslandSiteSelector.SiteEvaluation.rejected(IslandSiteSelector.RejectionReason.POOR_GROUNDING);
         } else {
            return maxTopY - minTopY > policy.maxTopVariation()
               ? IslandSiteSelector.SiteEvaluation.rejected(IslandSiteSelector.RejectionReason.HIGH_TOP_VARIATION)
               : IslandSiteSelector.SiteEvaluation.accepted(groundedRatio, centerColumn.topY());
         }
      }
   }

   private static boolean insideUsableRadius(HostIsland host, StructureFootprint footprint) {
      int centerX = host.preview().x();
      int centerZ = host.preview().z();
      long usableSq = (long)host.usableRadius() * host.usableRadius();
      int[][] corners = new int[][]{
         {footprint.minX(), footprint.minZ()}, {footprint.minX(), footprint.maxZ()}, {footprint.maxX(), footprint.minZ()}, {footprint.maxX(), footprint.maxZ()}
      };

      for (int[] corner : corners) {
         long dx = (long)corner[0] - centerX;
         long dz = (long)corner[1] - centerZ;
         if (dx * dx + dz * dz > usableSq) {
            return false;
         }
      }

      return true;
   }

   private static List<IslandSiteSelector.Point> samplePoints(StructureFootprint footprint) {
      Set<IslandSiteSelector.Point> points = new HashSet<>();
      add(points, footprint.minX(), footprint.minZ());
      add(points, footprint.minX(), footprint.maxZ());
      add(points, footprint.maxX(), footprint.minZ());
      add(points, footprint.maxX(), footprint.maxZ());
      add(points, footprint.centerX(), footprint.centerZ());
      add(points, footprint.centerX(), footprint.minZ());
      add(points, footprint.centerX(), footprint.maxZ());
      add(points, footprint.minX(), footprint.centerZ());
      add(points, footprint.maxX(), footprint.centerZ());

      for (int x = footprint.minX(); x <= footprint.maxX(); x += 8) {
         for (int z = footprint.minZ(); z <= footprint.maxZ(); z += 8) {
            add(points, x, z);
         }
      }

      add(points, footprint.maxX(), footprint.maxZ());
      return List.copyOf(points);
   }

   private static void add(Set<IslandSiteSelector.Point> points, int x, int z) {
      points.add(new IslandSiteSelector.Point(x, z));
   }

   private static List<IslandSiteSelector.Offset> rotatedRing(int radius, long seed) {
      IslandSiteSelector.Offset[] ring = new IslandSiteSelector.Offset[]{
         new IslandSiteSelector.Offset(radius, 0),
         new IslandSiteSelector.Offset(-radius, 0),
         new IslandSiteSelector.Offset(0, radius),
         new IslandSiteSelector.Offset(0, -radius),
         new IslandSiteSelector.Offset(radius, radius),
         new IslandSiteSelector.Offset(radius, -radius),
         new IslandSiteSelector.Offset(-radius, radius),
         new IslandSiteSelector.Offset(-radius, -radius)
      };
      long mixed = mix(seed, radius, -4417276706812531889L);
      int start = Math.floorMod((int)mixed, ring.length);
      boolean reverse = (mixed & 256L) != 0L;
      ArrayList<IslandSiteSelector.Offset> rotated = new ArrayList<>(ring.length);

      for (int i = 0; i < ring.length; i++) {
         int index = reverse ? Math.floorMod(start - i, ring.length) : Math.floorMod(start + i, ring.length);
         rotated.add(ring[index]);
      }

      return rotated;
   }

   private static int randomSignedStep(long seed, int maxJitter, int step) {
      int maxSteps = Math.max(0, maxJitter / Math.max(1, step));
      if (maxSteps == 0) {
         return 0;
      }

      int buckets = maxSteps * 2 + 1;
      int selected = Math.floorMod((int)mix(seed, maxJitter, step), buckets) - maxSteps;
      return selected * step;
   }

   private static long offsetSeed(HostQuery query, HostIsland host, long worldSeed) {
      return mix(
         worldSeed,
         query.structureId() == null ? 0L : query.structureId().hashCode(),
         query.vanillaOrigin().x,
         query.vanillaOrigin().z,
         host.preview().x(),
         host.preview().z()
      );
   }

   private static long mix(long seed, long... values) {
      long mixed = seed ^ -7046029254386353131L;

      for (long value : values) {
         mixed ^= value + -7046029254386353131L + (mixed << 6) + (mixed >>> 2);
      }

      mixed ^= mixed >>> 33;
      mixed *= -49064778989728563L;
      mixed ^= mixed >>> 33;
      mixed *= -4265267296055464877L;
      return mixed ^ mixed >>> 33;
   }

   private static int hostSafeThird(int usableRadius) {
      return Math.max(0, usableRadius / 3);
   }

   private static void addUnique(List<IslandSiteSelector.Offset> offsets, IslandSiteSelector.Offset offset) {
      if (!offsets.contains(offset)) {
         offsets.add(offset);
      }
   }

   private static void increment(EnumMap<IslandSiteSelector.RejectionReason, Integer> rejections, IslandSiteSelector.RejectionReason reason) {
      rejections.merge(reason, 1, Integer::sum);
   }

   public record Offset(int x, int z) {
   }

   private record Point(int x, int z) {
   }

   public enum RejectionReason {
      OUTSIDE_USABLE_RADIUS,
      MISSING_COLUMNS,
      POOR_GROUNDING,
      HIGH_TOP_VARIATION,
      RESERVATION_OVERLAP;
   }

   public record Selection(IslandPlacementSite site, Map<IslandSiteSelector.RejectionReason, Integer> rejections, int attempts) {
      public boolean successful() {
         return this.site != null;
      }
   }

   private record SiteEvaluation(boolean accepted, IslandSiteSelector.RejectionReason rejectionReason, double groundedRatio, int topY) {
      static IslandSiteSelector.SiteEvaluation accepted(double groundedRatio, int topY) {
         return new IslandSiteSelector.SiteEvaluation(true, null, groundedRatio, topY);
      }

      static IslandSiteSelector.SiteEvaluation rejected(IslandSiteSelector.RejectionReason reason) {
         return new IslandSiteSelector.SiteEvaluation(false, reason, 0.0, 0);
      }
   }
}
