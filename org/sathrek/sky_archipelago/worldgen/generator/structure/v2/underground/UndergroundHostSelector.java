package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.structure.underground.UndergroundHostSelection;

public final class UndergroundHostSelector {
   public List<IslandField.IslandPreview> select(
      ResourceLocation structureId, SkyIslandSettings settings, IslandField islandField, int preferredX, int preferredZ
   ) {
      return UndergroundHostSelection.selectHosts(structureId, settings, islandField, preferredX, preferredZ);
   }
}
