package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.StructurePiece;

public interface StructurePlacementAdapter {
   boolean suppressIglooSurfaceAdjustment(RelocatedPlacementContext var1, StructurePiece var2);

   String pieceRole(StructurePiece var1);
}
