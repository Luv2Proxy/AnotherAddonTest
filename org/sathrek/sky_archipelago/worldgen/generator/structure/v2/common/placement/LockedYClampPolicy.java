package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

public final class LockedYClampPolicy implements PostProcessDriftPolicy {
   @Override
   public PostProcessDriftDecision evaluate(RelocatedPlacementContext context, StructurePiece piece, BoundingBox before, BoundingBox after) {
      if (context != null && context.yLockEnabled() && before != null && after != null) {
         int dy = after.minY() - before.minY();
         String role = context.pieceRole(piece);
         return dy == 0 ? PostProcessDriftDecision.allow(role, 0, "no_drift") : PostProcessDriftDecision.clamp(-dy, role, 0, "generic_locked_y");
      } else {
         return PostProcessDriftDecision.allow("unknown", null, "lock_disabled_or_missing_bounds");
      }
   }
}
