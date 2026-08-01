package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostQuery;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class StructureHostSelector {
   public StructureHostSelector.Selection selectHosts(
      HostQuery query, List<IslandField.IslandPreview> previews, StructureFootprint footprint, IslandField islandField, SkyIslandSettings settings
   ) {
      return this.selectHosts(query, previews, footprint, islandField, settings, null);
   }

   public StructureHostSelector.Selection selectHosts(
      HostQuery query,
      List<IslandField.IslandPreview> previews,
      StructureFootprint footprint,
      IslandField islandField,
      SkyIslandSettings settings,
      Integer requiredRadiusOverride
   ) {
      int requiredRadius = requiredRadiusOverride != null ? requiredRadiusOverride : requiredRadius(footprint, query.policy());
      int requiredStableTopCells = requiredStableTopCells(query.category());
      ArrayList<HostIsland> hosts = new ArrayList<>();
      EnumMap<StructureHostSelector.RejectionReason, Integer> rejections = new EnumMap<>(StructureHostSelector.RejectionReason.class);

      for (IslandField.IslandPreview preview : previews) {
         HostIsland host = this.evaluate(preview, requiredRadius, requiredStableTopCells, query.policy(), islandField, settings);
         if (host == null) {
            increment(rejections, this.rejectionReason(preview, requiredRadius, requiredStableTopCells, query.policy(), islandField, settings));
         } else {
            hosts.add(host);
         }
      }

      hosts.sort(ordering(query));
      return new StructureHostSelector.Selection(List.copyOf(hosts), Map.copyOf(rejections), previews.size(), requiredRadius);
   }

   public static int requiredRadius(StructureFootprint footprint, StructurePlacementPolicy policy) {
      double halfX = (footprint.spanX() + 1) * 0.5;
      double halfZ = (footprint.spanZ() + 1) * 0.5;
      int footprintRadius = (int)Math.ceil(Math.sqrt(halfX * halfX + halfZ * halfZ));
      return Math.max(policy.minHostRadius(), footprintRadius);
   }

   private HostIsland evaluate(
      IslandField.IslandPreview preview,
      int requiredRadius,
      int requiredStableTopCells,
      StructurePlacementPolicy policy,
      IslandField islandField,
      SkyIslandSettings settings
   ) {
      if (!isAllowedFamily(preview.family())) {
         return null;
      }

      int usableRadius = preview.radius() - policy.margin();
      if (usableRadius <= 0) {
         return null;
      }

      if (usableRadius < requiredRadius) {
         return null;
      }

      TerrainColumn center = islandField.sampleColumn(preview.x(), preview.z(), settings);
      if (!center.exists()) {
         return null;
      }

      int stableTopCells = this.stableTopCells(preview, usableRadius, center.topY(), policy, islandField, settings);
      return stableTopCells < requiredStableTopCells ? null : new HostIsland(preview, center.topY(), stableTopCells, usableRadius, requiredRadius);
   }

   private StructureHostSelector.RejectionReason rejectionReason(
      IslandField.IslandPreview preview,
      int requiredRadius,
      int requiredStableTopCells,
      StructurePlacementPolicy policy,
      IslandField islandField,
      SkyIslandSettings settings
   ) {
      if (!isAllowedFamily(preview.family())) {
         return StructureHostSelector.RejectionReason.UNSUPPORTED_FAMILY;
      }

      int usableRadius = preview.radius() - policy.margin();
      if (usableRadius <= 0) {
         return StructureHostSelector.RejectionReason.NO_USABLE_RADIUS;
      }

      if (usableRadius < requiredRadius) {
         return StructureHostSelector.RejectionReason.RADIUS_TOO_SMALL;
      }

      TerrainColumn center = islandField.sampleColumn(preview.x(), preview.z(), settings);
      if (!center.exists()) {
         return StructureHostSelector.RejectionReason.MISSING_CENTER_COLUMN;
      }

      int stableTopCells = this.stableTopCells(preview, usableRadius, center.topY(), policy, islandField, settings);
      return stableTopCells < requiredStableTopCells
         ? StructureHostSelector.RejectionReason.INSUFFICIENT_STABLE_TOP
         : StructureHostSelector.RejectionReason.INSUFFICIENT_STABLE_TOP;
   }

   private int stableTopCells(
      IslandField.IslandPreview preview, int usableRadius, int centerTopY, StructurePlacementPolicy policy, IslandField islandField, SkyIslandSettings settings
   ) {
      int sampleDistance = Math.max(1, Math.min(usableRadius, Math.max(4, preview.radius() / 3)));
      int[][] samples = new int[][]{
         {0, 0},
         {sampleDistance, 0},
         {-sampleDistance, 0},
         {0, sampleDistance},
         {0, -sampleDistance},
         {sampleDistance, sampleDistance},
         {sampleDistance, -sampleDistance},
         {-sampleDistance, sampleDistance},
         {-sampleDistance, -sampleDistance}
      };
      int stable = 0;

      for (int[] sample : samples) {
         TerrainColumn column = islandField.sampleColumn(preview.x() + sample[0], preview.z() + sample[1], settings);
         if (column.exists() && Math.abs(column.topY() - centerTopY) <= policy.maxTopVariation()) {
            stable++;
         }
      }

      return stable;
   }

   private static int requiredStableTopCells(StructurePlacementCategory category) {
      return switch (category) {
         case GROUND_VILLAGE -> 0;
         case HAMLET_SKY -> 7;
         case STRONGHOLD -> 8;
         case SURFACE_SKY -> 7;
         case SMALL_SKY -> 5;
         default -> 3;
      };
   }

   private static boolean isAllowedFamily(IslandField.IslandFamily family) {
      return family == IslandField.IslandFamily.ANCHOR_PLATEAU || family == IslandField.IslandFamily.SATELLITE;
   }

   private static Comparator<HostIsland> ordering(HostQuery query) {
      boolean largeStructure = query.category() == StructurePlacementCategory.GROUND_VILLAGE
         || query.category() == StructurePlacementCategory.HAMLET_SKY
         || query.category() == StructurePlacementCategory.STRONGHOLD;
      return largeStructure
         ? Comparator.<HostIsland>comparingInt(host -> familyRank(query.category(), host.preview().family()))
            .thenComparing(Comparator.comparingInt(HostIsland::stableTopCells).reversed())
            .thenComparing(Comparator.<HostIsland>comparingInt(host -> host.usableRadius() - host.requiredRadius()).reversed())
            .thenComparing(Comparator.comparingInt(HostIsland::usableRadius).reversed())
            .thenComparingLong(host -> distanceSq(host.preview().x(), host.preview().z(), query.originX(), query.originZ()))
            .thenComparingInt(host -> host.preview().x())
            .thenComparingInt(host -> host.preview().z())
            .thenComparingInt(host -> host.preview().y())
         : Comparator.<HostIsland>comparingLong(host -> distanceSq(host.preview().x(), host.preview().z(), query.originX(), query.originZ()))
            .thenComparingInt(host -> familyRank(query.category(), host.preview().family()))
            .thenComparing(Comparator.<HostIsland>comparingInt(host -> host.usableRadius() - host.requiredRadius()).reversed())
            .thenComparing(Comparator.comparingInt(HostIsland::stableTopCells).reversed())
            .thenComparingInt(host -> host.preview().x())
            .thenComparingInt(host -> host.preview().z())
            .thenComparingInt(host -> host.preview().y());
   }

   private static int familyRank(StructurePlacementCategory category, IslandField.IslandFamily family) {
      return (
               category == StructurePlacementCategory.SURFACE_SKY
                  || category == StructurePlacementCategory.HAMLET_SKY
                  || category == StructurePlacementCategory.GROUND_VILLAGE
                  || category == StructurePlacementCategory.STRONGHOLD
            )
            && family == IslandField.IslandFamily.ANCHOR_PLATEAU
         ? 0
         : 1;
   }

   private static long distanceSq(int x0, int z0, int x1, int z1) {
      long dx = (long)x0 - x1;
      long dz = (long)z0 - z1;
      return dx * dx + dz * dz;
   }

   private static void increment(EnumMap<StructureHostSelector.RejectionReason, Integer> rejections, StructureHostSelector.RejectionReason reason) {
      rejections.merge(reason, 1, Integer::sum);
   }

   public enum RejectionReason {
      UNSUPPORTED_FAMILY,
      RADIUS_TOO_SMALL,
      NO_USABLE_RADIUS,
      MISSING_CENTER_COLUMN,
      INSUFFICIENT_STABLE_TOP;
   }

   public record Selection(List<HostIsland> hosts, Map<StructureHostSelector.RejectionReason, Integer> rejections, int previewCount, int requiredRadius) {
   }
}
