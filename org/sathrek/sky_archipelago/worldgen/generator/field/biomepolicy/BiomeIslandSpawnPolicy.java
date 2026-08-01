package org.sathrek.sky_archipelago.worldgen.generator.field.biomepolicy;

import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

@FunctionalInterface
public interface BiomeIslandSpawnPolicy {
   boolean allowIslandSpawn(IslandField.BiomeSample var1, SkyIslandSettings var2);
}
