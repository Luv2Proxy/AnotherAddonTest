package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model;

import java.util.List;
import net.minecraft.world.level.ChunkPos;

public record PreAnchorPlacementContext(
   HostIsland host, ChunkPos sourceChunk, ChunkPos winningAnchorChunk, int attemptsUsed, List<ChunkPos> attemptedAnchorChunks
) {
}
