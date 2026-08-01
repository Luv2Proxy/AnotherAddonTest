package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village;

import java.util.Locale;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

public final class VillagePieceClassifier {
   public VillagePieceClassifier.PieceKind classify(StructurePiece piece) {
      String text = (piece.getClass().getName() + " " + piece).toLowerCase(Locale.ROOT);
      if (containsAny(text, "road", "street", "path", "junction", "crossing")) {
         return VillagePieceClassifier.PieceKind.ROAD;
      } else if (containsAny(text, "farm", "crop", "field")) {
         return VillagePieceClassifier.PieceKind.FARM;
      } else if (containsAny(text, "town", "meeting", "center", "bell")) {
         return VillagePieceClassifier.PieceKind.CENTER;
      } else {
         BoundingBox bounds = piece.getBoundingBox();
         int spanX = bounds.maxX() - bounds.minX() + 1;
         int spanZ = bounds.maxZ() - bounds.minZ() + 1;
         int height = bounds.maxY() - bounds.minY() + 1;
         int area = spanX * spanZ;
         if ((spanX <= 6 && spanZ >= 16 || spanZ <= 6 && spanX >= 16) && height <= 5) {
            return VillagePieceClassifier.PieceKind.ROAD;
         } else if (area >= 64 && height <= 3) {
            return VillagePieceClassifier.PieceKind.FARM;
         } else {
            return area >= 20 && height >= 4 ? VillagePieceClassifier.PieceKind.BUILDING : VillagePieceClassifier.PieceKind.OTHER;
         }
      }
   }

   private static boolean containsAny(String text, String... tokens) {
      for (String token : tokens) {
         if (text.contains(token)) {
            return true;
         }
      }

      return false;
   }

   public enum PieceKind {
      CENTER,
      ROAD,
      BUILDING,
      FARM,
      OTHER;
   }
}
