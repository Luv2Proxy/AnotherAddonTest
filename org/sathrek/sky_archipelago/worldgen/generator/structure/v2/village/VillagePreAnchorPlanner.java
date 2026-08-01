package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostQuery;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.IslandHostIndex;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureHostSelector;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class VillagePreAnchorPlanner {
   private static final int MAX_ANCHORS = 6;
   private static final int MIN_REQUIRED_RADIUS = 35;
   private static final int RELAX_STEP = 8;
   private final IslandHostIndex hostIndex;
   private final StructureHostSelector hostSelector;

   public VillagePreAnchorPlanner() {
      this(new IslandHostIndex(), new StructureHostSelector());
   }

   VillagePreAnchorPlanner(IslandHostIndex hostIndex, StructureHostSelector hostSelector) {
      this.hostIndex = hostIndex;
      this.hostSelector = hostSelector;
   }

   public VillagePreAnchorPlanner.Plan plan(
      ResourceLocation structureId,
      ChunkPos sourceChunk,
      StructurePlacementPolicy policy,
      StructureFootprint footprint,
      IslandField islandField,
      SkyIslandSettings settings
   ) {
      HostQuery query = new HostQuery(
         structureId,
         StructurePlacementCategory.GROUND_VILLAGE,
         sourceChunk,
         sourceChunk.getMiddleBlockX(),
         sourceChunk.getMiddleBlockZ(),
         policy.searchRadius(),
         policy
      );
      StructureFootprint hostFootprint = footprint != null ? footprint : fallbackFootprint(sourceChunk, policy.minHostRadius());
      List<IslandField.IslandPreview> previews = this.hostIndex.previewsFor(query, islandField, settings);
      int initialRequiredRadius = StructureHostSelector.requiredRadius(hostFootprint, policy);
      int requiredRadius = initialRequiredRadius;
      int relaxSteps = 0;

      while (true) {
         StructureHostSelector.Selection selection = this.hostSelector.selectHosts(query, previews, hostFootprint, islandField, settings, requiredRadius);
         if (!selection.hosts().isEmpty() || requiredRadius <= 35) {
            ArrayList<VillagePreAnchorPlanner.Anchor> anchors = new ArrayList<>();

            for (HostIsland host : selection.hosts()) {
               anchors.add(new VillagePreAnchorPlanner.Anchor(host, new ChunkPos(host.preview().x() >> 4, host.preview().z() >> 4), 0));
               anchors.add(new VillagePreAnchorPlanner.Anchor(host, offsetChunk(host, 8, 0), 1));
               anchors.add(new VillagePreAnchorPlanner.Anchor(host, offsetChunk(host, -8, 0), 2));
               anchors.add(new VillagePreAnchorPlanner.Anchor(host, offsetChunk(host, 0, 8), 3));
               anchors.add(new VillagePreAnchorPlanner.Anchor(host, offsetChunk(host, 0, -8), 4));
               if (anchors.size() >= 6) {
                  break;
               }
            }

            anchors.sort(
               Comparator.comparingInt(VillagePreAnchorPlanner.Anchor::variantRank)
                  .thenComparing(anchor -> anchor.host().usableRadius(), Comparator.reverseOrder())
                  .thenComparingInt(anchor -> anchor.host().preview().x())
                  .thenComparingInt(anchor -> anchor.host().preview().z())
            );
            if (anchors.size() > 6) {
               anchors = new ArrayList<>(anchors.subList(0, 6));
            }

            return new VillagePreAnchorPlanner.Plan(
               List.copyOf(anchors), selection.previewCount(), initialRequiredRadius, selection.requiredRadius(), relaxSteps, selection.rejections()
            );
         }

         requiredRadius = Math.max(35, requiredRadius - 8);
         relaxSteps++;
      }
   }

   private static StructureFootprint fallbackFootprint(ChunkPos sourceChunk, int radius) {
      int centerX = sourceChunk.getMiddleBlockX();
      int centerZ = sourceChunk.getMiddleBlockZ();
      return new StructureFootprint(centerX - radius, centerX + radius, centerZ - radius, centerZ + radius);
   }

   private static ChunkPos offsetChunk(HostIsland host, int dx, int dz) {
      int x = host.preview().x() + dx;
      int z = host.preview().z() + dz;
      return new ChunkPos(x >> 4, z >> 4);
   }

   public record Anchor(HostIsland host, ChunkPos chunkPos, int variantRank) {
   }

   public record Plan(
      List<VillagePreAnchorPlanner.Anchor> anchors,
      int previewCount,
      int initialRequiredRadius,
      int requiredRadius,
      int relaxationSteps,
      Map<StructureHostSelector.RejectionReason, Integer> rejections
   ) {
   }
}
