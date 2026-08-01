package org.sathrek.sky_archipelago.worldgen.structure.underground;

import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

public final class UndergroundHostSelection {
   private UndergroundHostSelection() {
   }

   public static List<IslandField.IslandPreview> selectHosts(
      ResourceLocation structureId, SkyIslandSettings settings, IslandField islandField, int centerX, int centerZ
   ) {
      int searchRadiusBlocks = settings.advanced().structurePlacementPolicy().searchRadiusChunksFor(structureId) * 16;
      if (searchRadiusBlocks <= 0) {
         return List.of();
      }

      int minHostRadius = settings.advanced().structurePlacementPolicy().minHostIslandRadiusFor(structureId);
      return islandField.collectIslandPreviewsInRadius(centerX, centerZ, searchRadiusBlocks, settings)
         .stream()
         .filter(preview -> preview.family() == IslandField.IslandFamily.ANCHOR_PLATEAU)
         .filter(preview -> preview.radius() >= minHostRadius)
         .sorted(
            Comparator.comparingInt(IslandField.IslandPreview::x)
               .thenComparingInt(IslandField.IslandPreview::z)
               .thenComparingInt(IslandField.IslandPreview::radius)
               .thenComparingInt(IslandField.IslandPreview::y)
         )
         .toList();
   }
}
