package org.sathrek.sky_archipelago.worldgen.generator.field.biomepolicy;

import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

@FunctionalInterface
public interface BiomeExclusionRule {
   boolean excludes(IslandField.BiomeSample var1, SkyIslandSettings var2);
}
