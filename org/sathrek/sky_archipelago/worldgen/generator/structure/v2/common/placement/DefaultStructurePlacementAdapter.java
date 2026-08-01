package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.StructurePiece;

public final class DefaultStructurePlacementAdapter implements StructurePlacementAdapter {
   @Override
   public boolean suppressIglooSurfaceAdjustment(RelocatedPlacementContext context, StructurePiece piece) {
      return false;
   }

   @Override
   public String pieceRole(StructurePiece piece) {
      return "unknown";
   }
}
