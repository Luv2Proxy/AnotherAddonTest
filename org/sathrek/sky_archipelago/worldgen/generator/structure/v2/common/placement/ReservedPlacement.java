package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public record ReservedPlacement(
   ResourceLocation structureId,
   int sourceChunkX,
   int sourceChunkZ,
   BoundingBox occupiedBounds,
   StructurePlacementCategory category,
   long reservedAtNanos,
   long seedTag
) {
   ReservedPlacement withReservedAtNanos(long reservedAtNanos) {
      return new ReservedPlacement(this.structureId, this.sourceChunkX, this.sourceChunkZ, this.occupiedBounds, this.category, reservedAtNanos, this.seedTag);
   }
}
