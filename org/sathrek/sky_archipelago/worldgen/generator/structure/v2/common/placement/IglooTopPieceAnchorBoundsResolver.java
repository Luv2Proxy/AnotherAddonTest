package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class IglooTopPieceAnchorBoundsResolver implements AnchorBoundsResolver {
   private static final String TEMPLATE_PIECE_CLASS = "net.minecraft.world.level.levelgen.structure.TemplateStructurePiece";
   private static final String IGLOO_TOP_TEMPLATE = "minecraft:igloo/top";
   private final AnchorBoundsResolver fallbackResolver;

   public IglooTopPieceAnchorBoundsResolver(AnchorBoundsResolver fallbackResolver) {
      this.fallbackResolver = fallbackResolver;
   }

   @Override
   public BoundingBox resolve(StructureStart structureStart) {
      if (structureStart != null && structureStart.isValid()) {
         List<StructurePiece> pieces = structureStart.getPieces();
         if (pieces.isEmpty()) {
            return this.fallbackResolver.resolve(structureStart);
         }

         BoundingBox topTemplate = null;
         BoundingBox lastPiece = null;
         BoundingBox highestMinY = null;
         int highestMinYValue = Integer.MIN_VALUE;

         for (int i = 0; i < pieces.size(); i++) {
            StructurePiece piece = pieces.get(i);
            BoundingBox bounds = piece == null ? null : piece.getBoundingBox();
            if (bounds != null) {
               if (i == pieces.size() - 1) {
                  lastPiece = bounds;
               }

               if (bounds.minY() > highestMinYValue) {
                  highestMinYValue = bounds.minY();
                  highestMinY = bounds;
               }

               if (isIglooTopTemplate(piece)) {
                  topTemplate = bounds;
                  break;
               }
            }
         }

         BoundingBox fallback = this.fallbackResolver.resolve(structureStart);
         if (topTemplate != null) {
            return topTemplate;
         } else if (lastPiece != null) {
            return lastPiece;
         } else {
            return highestMinY != null ? highestMinY : fallback;
         }
      } else {
         return this.fallbackResolver.resolve(structureStart);
      }
   }

   private static boolean isIglooTopTemplate(StructurePiece piece) {
      if (piece == null) {
         return false;
      }

      try {
         Class<?> type = piece.getClass();
         Class<?> templateType = Class.forName("net.minecraft.world.level.levelgen.structure.TemplateStructurePiece");
         if (!templateType.isAssignableFrom(type)) {
            return false;
         } else {
            Class<?> current = type;
            if (current != null) {
               Field templateName = current.getDeclaredField("templateName");
               templateName.setAccessible(true);
               Object value = templateName.get(piece);
               String name = value == null ? "" : value.toString();
               return "minecraft:igloo/top".equals(name) || name.endsWith("/igloo/top");
            } else {
               return false;
            }
         }
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         return false;
      }
   }
}
