package org.sathrek.sky_archipelago.worldgen.generator.field.biomepolicy;

import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

public final class OceanBiomeExclusionRule implements BiomeExclusionRule {
   @Override
   public boolean excludes(IslandField.BiomeSample biomeSample, SkyIslandSettings settings) {
      return settings.advanced().disableIslandsOverOceanBiomes() && biomeSample.isOceanBiome();
   }
}
