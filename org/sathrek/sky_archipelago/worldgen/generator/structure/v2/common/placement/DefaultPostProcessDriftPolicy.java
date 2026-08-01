package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

public final class DefaultPostProcessDriftPolicy implements PostProcessDriftPolicy {
   @Override
   public PostProcessDriftDecision evaluate(RelocatedPlacementContext context, StructurePiece piece, BoundingBox before, BoundingBox after) {
      return PostProcessDriftDecision.allow("unknown", null, "pass_through");
   }
}
