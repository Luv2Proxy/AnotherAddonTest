package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class SpatialIndex {
   public List<Long> bucketKeys(BoundingBox bounds) {
      int minChunkX = SectionPos.blockToSectionCoord(bounds.minX());
      int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX());
      int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ());
      int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ());
      ArrayList<Long> keys = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));

      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
         for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            keys.add(ChunkPos.asLong(chunkX, chunkZ));
         }
      }

      return keys;
   }
}
