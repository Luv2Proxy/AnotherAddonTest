package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

public record ReservationContext(long levelSeed, ResourceLocation structureId, ChunkPos sourceChunkPos) {
}
