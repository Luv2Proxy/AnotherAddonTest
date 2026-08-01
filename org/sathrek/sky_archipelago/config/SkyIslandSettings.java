package org.sathrek.sky_archipelago.config;

import org.sathrek.sky_archipelago.config.settings.AdvancedRuntimeSettings;
import org.sathrek.sky_archipelago.config.settings.StructureCategorySettings;
import org.sathrek.sky_archipelago.config.settings.StructureSupportSettings;
import org.sathrek.sky_archipelago.config.settings.TerrainSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;

public record SkyIslandSettings(
   TerrainSettings terrain,
   StructureSupportSettings structureSupport,
   StructureCategorySettings surfaceSky,
   StructureCategorySettings smallSky,
   AdvancedRuntimeSettings advanced
) {
   public SkyIslandSettings {
      if (terrain == null) {
         throw new IllegalArgumentException("terrain cannot be null");
      }

      if (structureSupport == null) {
         throw new IllegalArgumentException("structureSupport cannot be null");
      }

      if (surfaceSky == null) {
         throw new IllegalArgumentException("surfaceSky cannot be null");
      }

      if (smallSky == null) {
         throw new IllegalArgumentException("smallSky cannot be null");
      }

      if (advanced == null) {
         throw new IllegalArgumentException("advanced cannot be null");
      }
   }

   public int maxIslandThickness() {
      return this.terrain.maxIslandThickness();
   }

   public int spawnSearchTopY() {
      return this.terrain.spawnSearchTopY();
   }

   public boolean isArchetypeEnabled(IslandShapeArchetype archetype) {
      return this.terrain.archetypes().isEnabled(archetype);
   }

   public double archetypeWeight(IslandShapeArchetype archetype) {
      return this.terrain.archetypes().weight(archetype);
   }
}
