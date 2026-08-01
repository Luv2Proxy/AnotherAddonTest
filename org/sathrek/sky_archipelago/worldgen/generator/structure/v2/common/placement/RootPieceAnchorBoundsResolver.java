package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class RootPieceAnchorBoundsResolver implements AnchorBoundsResolver {
   private final AnchorBoundsResolver fallbackResolver;

   public RootPieceAnchorBoundsResolver(AnchorBoundsResolver fallbackResolver) {
      this.fallbackResolver = fallbackResolver;
   }

   @Override
   public BoundingBox resolve(StructureStart structureStart) {
      if (structureStart != null && structureStart.isValid() && !structureStart.getPieces().isEmpty()) {
         StructurePiece piece = (StructurePiece)structureStart.getPieces().get(0);
         return fromBounds(piece == null ? null : piece.getBoundingBox(), this.fallbackResolver.resolve(structureStart));
      } else {
         return this.fallbackResolver.resolve(structureStart);
      }
   }

   static BoundingBox fromBounds(BoundingBox rootPieceBounds, BoundingBox fallbackBounds) {
      return rootPieceBounds != null ? rootPieceBounds : fallbackBounds;
   }
}
