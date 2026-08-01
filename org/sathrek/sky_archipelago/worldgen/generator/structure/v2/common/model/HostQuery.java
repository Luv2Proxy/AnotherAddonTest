package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public record HostQuery(
   ResourceLocation structureId,
   StructurePlacementCategory category,
   ChunkPos vanillaOrigin,
   int originX,
   int originZ,
   int searchRadius,
   StructurePlacementPolicy policy
) {
}
