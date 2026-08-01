package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class OverlapPolicy3D implements OverlapPolicy {
   @Override
   public boolean conflicts(
      BoundingBox candidateBounds, StructurePlacementCategory candidateCategory, BoundingBox existingBounds, StructurePlacementCategory existingCategory
   ) {
      if (candidateBounds != null && existingBounds != null) {
         int candidatePad = padForCategory(candidateCategory);
         int existingPad = padForCategory(existingCategory);
         return intersects3dPadded(candidateBounds, existingBounds, candidatePad, existingPad);
      } else {
         return false;
      }
   }

   static boolean intersects3dPadded(BoundingBox a, BoundingBox b, int padA, int padB) {
      int expandA = Math.max(0, padA);
      int expandB = Math.max(0, padB);
      return a.minX() - expandA <= b.maxX() + expandB
         && a.maxX() + expandA >= b.minX() - expandB
         && a.minY() - expandA <= b.maxY() + expandB
         && a.maxY() + expandA >= b.minY() - expandB
         && a.minZ() - expandA <= b.maxZ() + expandB
         && a.maxZ() + expandA >= b.minZ() - expandB;
   }

   private static int padForCategory(StructurePlacementCategory category) {
      if (category == null) {
         return 0;
      }

      return switch (category) {
         case SMALL_SKY -> 1;
         case SURFACE_SKY -> 2;
         case HAMLET_SKY -> 4;
         case GROUND_VILLAGE -> 6;
         case DEFAULT, SKY, STRONGHOLD, UNDERGROUND, WATER -> 0;
      };
   }
}
