package org.sathrek.sky_archipelago.worldgen.structure.mineshafts;

import java.util.Iterator;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class MineshaftAnchorResolver {
   public MineshaftAnchorResolver.Anchor resolve(StructureStart structureStart) {
      if (structureStart != null && structureStart.isValid()) {
         Iterator var2 = structureStart.getPieces().iterator();
         if (var2.hasNext()) {
            StructurePiece piece = (StructurePiece)var2.next();
            return fromBounds(piece.getBoundingBox(), structureStart.getBoundingBox());
         } else {
            return fromBounds(null, structureStart.getBoundingBox());
         }
      } else {
         return new MineshaftAnchorResolver.Anchor(0, 0, 0, "invalid_start_fallback");
      }
   }

   static MineshaftAnchorResolver.Anchor fromBounds(BoundingBox firstPieceBounds, BoundingBox fallbackBounds) {
      if (firstPieceBounds != null) {
         int centerX = Mth.floor((firstPieceBounds.minX() + firstPieceBounds.maxX()) * 0.5);
         int centerZ = Mth.floor((firstPieceBounds.minZ() + firstPieceBounds.maxZ()) * 0.5);
         return new MineshaftAnchorResolver.Anchor(centerX, firstPieceBounds.minY(), centerZ, "first_piece_center");
      } else {
         int centerX = Mth.floor((fallbackBounds.minX() + fallbackBounds.maxX()) * 0.5);
         int centerZ = Mth.floor((fallbackBounds.minZ() + fallbackBounds.maxZ()) * 0.5);
         return new MineshaftAnchorResolver.Anchor(centerX, fallbackBounds.minY(), centerZ, "bounds_center_fallback");
      }
   }

   public record Anchor(int x, int baseY, int z, String source) {
   }
}
