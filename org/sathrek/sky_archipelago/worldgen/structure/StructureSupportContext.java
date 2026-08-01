package org.sathrek.sky_archipelago.worldgen.structure;

import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

public record StructureSupportContext(ResourceLocation structureId, SkyIslandSettings settings, IslandField islandField) {
}
