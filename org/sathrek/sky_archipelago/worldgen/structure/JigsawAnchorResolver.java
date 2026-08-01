package org.sathrek.sky_archipelago.worldgen.structure;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class JigsawAnchorResolver implements AnchorResolverStrategy {
   @Override
   public AnchorResolverStrategy.DynamicAnchor resolve(StructureStart structureStart) {
      if (structureStart != null && structureStart.isValid()) {
         StructurePiece entryLikePiece = null;

         for (StructurePiece piece : structureStart.getPieces()) {
            if (entryLikePiece == null || piece.getBoundingBox().minY() < entryLikePiece.getBoundingBox().minY()) {
               entryLikePiece = piece;
            }
         }

         if (entryLikePiece != null) {
            return fromBounds(entryLikePiece.getBoundingBox(), "entry_piece_center");
         }

         BoundingBox bounds = structureStart.getBoundingBox();
         if (bounds != null) {
            return fromBounds(bounds, "bounds_center_fallback");
         }
      }

      return new AnchorResolverStrategy.DynamicAnchor(0, 0, 0, "invalid_start_fallback");
   }

   private static AnchorResolverStrategy.DynamicAnchor fromBounds(BoundingBox bounds, String source) {
      int centerX = Mth.floor((bounds.minX() + bounds.maxX()) * 0.5);
      int centerZ = Mth.floor((bounds.minZ() + bounds.maxZ()) * 0.5);
      return new AnchorResolverStrategy.DynamicAnchor(centerX, bounds.minY(), centerZ, source);
   }
}
