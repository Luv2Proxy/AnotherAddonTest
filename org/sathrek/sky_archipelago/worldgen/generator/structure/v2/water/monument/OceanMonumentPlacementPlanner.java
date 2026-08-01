package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public final class OceanMonumentPlacementPlanner {
   public static final int CENTER_TO_MIN_OFFSET_BLOCKS = 29;
   public static final int FOOTPRINT_RADIUS_BLOCKS = 29;
   public static final int FOOTPRINT_SPAN_BLOCKS = 58;

   public OceanMonumentPlacementPlanner.PlannedMonumentPlacement plan(ChunkPos candidateChunk, int offsetX, int offsetZ, int bodyFloorY) {
      int centerX = candidateChunk.getMinBlockX() + offsetX;
      int centerZ = candidateChunk.getMinBlockZ() + offsetZ;
      int minX = centerX - 29;
      int minZ = centerZ - 29;
      int minY = bodyFloorY - 8;
      return new OceanMonumentPlacementPlanner.PlannedMonumentPlacement(
         centerX, centerZ, bodyFloorY, minX, minY, minZ, new StructureFootprint(centerX - 29, centerX + 29, centerZ - 29, centerZ + 29)
      );
   }

   public record PlannedMonumentPlacement(int centerX, int centerZ, int bodyFloorY, int minX, int minY, int minZ, StructureFootprint footprint) {
      public BoundingBox expectedBounds() {
         return new BoundingBox(this.minX, this.minY, this.minZ, this.minX + 58, this.minY + 22, this.minZ + 58);
      }
   }
}
