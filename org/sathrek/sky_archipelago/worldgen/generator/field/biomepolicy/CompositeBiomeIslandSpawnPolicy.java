package org.sathrek.sky_archipelago.worldgen.generator.field.biomepolicy;

import java.util.List;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

public final class CompositeBiomeIslandSpawnPolicy implements BiomeIslandSpawnPolicy {
   private final List<BiomeExclusionRule> exclusionRules;

   public CompositeBiomeIslandSpawnPolicy(List<BiomeExclusionRule> exclusionRules) {
      this.exclusionRules = List.copyOf(exclusionRules);
   }

   @Override
   public boolean allowIslandSpawn(IslandField.BiomeSample biomeSample, SkyIslandSettings settings) {
      for (BiomeExclusionRule exclusionRule : this.exclusionRules) {
         if (exclusionRule.excludes(biomeSample, settings)) {
            return false;
         }
      }

      return true;
   }
}
