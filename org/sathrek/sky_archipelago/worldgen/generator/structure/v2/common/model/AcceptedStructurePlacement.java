package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public record AcceptedStructurePlacement(
   ResourceLocation structureId, StructurePlacementCategory category, ChunkPos originChunk, BlockPos finalCenter, HostIslandKey hostIslandKey
) {
}
