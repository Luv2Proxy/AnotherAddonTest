package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;

public final class PostProcessDriftTelemetry {
   private static final int DRIFT_WARN_THRESHOLD = 4;

   public void record(StructurePiece piece, BoundingBox before, BoundingBox after, PostProcessDriftDecision decision, RelocatedPlacementContext context) {
      if (SkyIslandServerConfig.structureDebugEnabled() && before != null && after != null && context != null) {
         int dx = after.minX() - before.minX();
         int dy = after.minY() - before.minY();
         int dz = after.minZ() - before.minZ();
         boolean flagged = Math.abs(dy) > 4;
         int correctionDy = decision == null ? 0 : decision.correctionDy();
         String role = decision == null ? "unknown" : decision.pieceRole();
         Integer expectedDy = decision == null ? null : decision.expectedDy();
         String reason = decision == null ? "none" : decision.reason();
         SkyArchipelago.LOGGER
            .info(
               "postprocess_piece_drift id={} startChunk=[{}, {}] piece={} role={} drift=({}, {}, {}) expectedDy={} before={} after={} flagged={} correctionDy={} reason={}",
               new Object[]{
                  context.structureId(),
                  context.startChunk().x,
                  context.startChunk().z,
                  piece == null ? "unknown" : piece.getClass().getName(),
                  role,
                  dx,
                  dy,
                  dz,
                  expectedDy,
                  before,
                  after,
                  flagged,
                  correctionDy,
                  reason
               }
            );
         if (correctionDy != 0) {
            SkyArchipelago.LOGGER
               .warn(
                  "postprocess_y_clamp id={} startChunk=[{}, {}] piece={} role={} observedDy={} expectedDy={} correctionDy={} reason={}",
                  new Object[]{
                     context.structureId(),
                     context.startChunk().x,
                     context.startChunk().z,
                     piece == null ? "unknown" : piece.getClass().getName(),
                     role,
                     dy,
                     expectedDy,
                     correctionDy,
                     reason
                  }
               );
         }
      }
   }
}
