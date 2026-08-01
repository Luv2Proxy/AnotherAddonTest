package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

public interface PostProcessDriftPolicy {
   PostProcessDriftDecision evaluate(RelocatedPlacementContext var1, StructurePiece var2, BoundingBox var3, BoundingBox var4);
}
