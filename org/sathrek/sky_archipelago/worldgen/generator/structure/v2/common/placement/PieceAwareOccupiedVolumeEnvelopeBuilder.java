package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class PieceAwareOccupiedVolumeEnvelopeBuilder implements OccupiedVolumeEnvelopeBuilder {
   private static final int MAX_ENVELOPE_SIZE = 192;
   private static final int MAX_CENTER_DRIFT = 96;

   @Override
   public BoundingBox envelope(StructureStart structureStart, BoundingBox fallbackBounds) {
      if (fallbackBounds == null && structureStart == null) {
         return null;
      }

      BoundingBox base = fallbackBounds != null ? fallbackBounds : structureStart.getBoundingBox();
      BoundingBox pieceUnion = unionPieces(structureStart);
      BoundingBox candidate = pieceUnion != null ? pieceUnion : base;
      if (isInflated(candidate) || centerDriftTooLarge(candidate, base)) {
         candidate = base;
      }

      return clampSpan(candidate, 192);
   }

   private static BoundingBox unionPieces(StructureStart structureStart) {
      if (structureStart != null && !structureStart.getPieces().isEmpty()) {
         BoundingBox union = null;

         for (StructurePiece piece : structureStart.getPieces()) {
            BoundingBox b = piece.getBoundingBox();
            if (b != null) {
               if (union == null) {
                  union = new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
               } else {
                  union = new BoundingBox(
                     Math.min(union.minX(), b.minX()),
                     Math.min(union.minY(), b.minY()),
                     Math.min(union.minZ(), b.minZ()),
                     Math.max(union.maxX(), b.maxX()),
                     Math.max(union.maxY(), b.maxY()),
                     Math.max(union.maxZ(), b.maxZ())
                  );
               }
            }
         }

         return union;
      } else {
         return null;
      }
   }

   private static boolean isInflated(BoundingBox bounds) {
      return span(bounds.minX(), bounds.maxX()) > 192 || span(bounds.minY(), bounds.maxY()) > 192 || span(bounds.minZ(), bounds.maxZ()) > 192;
   }

   private static boolean centerDriftTooLarge(BoundingBox a, BoundingBox b) {
      int ax = Mth.floor((a.minX() + a.maxX()) * 0.5);
      int ay = Mth.floor((a.minY() + a.maxY()) * 0.5);
      int az = Mth.floor((a.minZ() + a.maxZ()) * 0.5);
      int bx = Mth.floor((b.minX() + b.maxX()) * 0.5);
      int by = Mth.floor((b.minY() + b.maxY()) * 0.5);
      int bz = Mth.floor((b.minZ() + b.maxZ()) * 0.5);
      return Math.abs(ax - bx) > 96 || Math.abs(ay - by) > 96 || Math.abs(az - bz) > 96;
   }

   private static BoundingBox clampSpan(BoundingBox bounds, int maxSpan) {
      int cx = Mth.floor((bounds.minX() + bounds.maxX()) * 0.5);
      int cy = Mth.floor((bounds.minY() + bounds.maxY()) * 0.5);
      int cz = Mth.floor((bounds.minZ() + bounds.maxZ()) * 0.5);
      int halfX = Math.min(span(bounds.minX(), bounds.maxX()) / 2, maxSpan / 2);
      int halfY = Math.min(span(bounds.minY(), bounds.maxY()) / 2, maxSpan / 2);
      int halfZ = Math.min(span(bounds.minZ(), bounds.maxZ()) / 2, maxSpan / 2);
      return new BoundingBox(cx - halfX, cy - halfY, cz - halfZ, cx + halfX, cy + halfY, cz + halfZ);
   }

   private static int span(int min, int max) {
      return Math.max(0, max - min);
   }
}
