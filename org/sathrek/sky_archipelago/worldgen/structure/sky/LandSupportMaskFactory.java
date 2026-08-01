package org.sathrek.sky_archipelago.worldgen.structure.sky;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandBudget;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandSupportMask;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.RelativePoint;

public final class LandSupportMaskFactory {
   public LandSupportMask buildLandSupportMask(
      StructureStart structureStart, StructureFootprint rawFootprint, StructureFootprint effectiveFootprint, LandBudget budget, StructureSupportContext context
   ) {
      int centerX = rawFootprint.centerX();
      int centerZ = rawFootprint.centerZ();
      StructureFootprint core = effectiveFootprint.insetByRatio(budget.coreInsetRatio());
      StructureFootprint centralCore = effectiveFootprint.insetByRatio(budget.centralCoreInsetRatio());
      StructureFootprint inner = rawFootprint.insetByRatio(budget.innerMarginInsetRatio());
      Set<RelativePoint> occupied = new HashSet<>();
      if (structureStart != null && structureStart.isValid()) {
         int minPieceY = Integer.MAX_VALUE;

         for (StructurePiece piece : structureStart.getPieces()) {
            minPieceY = Math.min(minPieceY, piece.getBoundingBox().minY());
         }

         int pieceYCutoff = minPieceY == Integer.MAX_VALUE ? Integer.MIN_VALUE : minPieceY + 6;

         for (StructurePiece piece : structureStart.getPieces()) {
            if (piece.getBoundingBox().minY() <= pieceYCutoff) {
               int step = Math.max(2, Math.min(6, Math.min(piece.getBoundingBox().getXSpan(), piece.getBoundingBox().getZSpan()) / 4));

               for (int x = piece.getBoundingBox().minX(); x <= piece.getBoundingBox().maxX(); x += step) {
                  for (int z = piece.getBoundingBox().minZ(); z <= piece.getBoundingBox().maxZ(); z += step) {
                     if (contains(rawFootprint, x, z)) {
                        occupied.add(new RelativePoint(x - centerX, z - centerZ));
                     }
                  }
               }
            }
         }
      }

      if (occupied.isEmpty()) {
         effectiveFootprint.forEachGridPoint(
            Math.max(3, context.settings().structureSupport().supportSampleGridSize() - 1), (x, z) -> occupied.add(new RelativePoint(x - centerX, z - centerZ))
         );
      }

      Set<RelativePoint> coreRequired = new HashSet<>();
      Set<RelativePoint> centralRequired = new HashSet<>();
      Set<RelativePoint> edgeOptional = new HashSet<>();

      for (RelativePoint point : occupied) {
         int worldX = centerX + point.dx();
         int worldZ = centerZ + point.dz();
         if (contains(centralCore, worldX, worldZ)) {
            centralRequired.add(point);
         }

         if (contains(core, worldX, worldZ)) {
            coreRequired.add(point);
         } else if (contains(inner, worldX, worldZ)) {
            edgeOptional.add(point);
         }
      }

      if (coreRequired.isEmpty()) {
         core.forEachGridPoint(
            Math.max(3, context.settings().structureSupport().supportSampleGridSize() - 1),
            (x, z) -> coreRequired.add(new RelativePoint(x - centerX, z - centerZ))
         );
      }

      if (centralRequired.isEmpty()) {
         centralCore.forEachGridPoint(
            Math.max(3, context.settings().structureSupport().supportSampleGridSize() - 1),
            (x, z) -> centralRequired.add(new RelativePoint(x - centerX, z - centerZ))
         );
      }

      if (edgeOptional.isEmpty()) {
         inner.forEachGridPoint(
            Math.max(4, context.settings().structureSupport().supportSampleGridSize()), (x, z) -> edgeOptional.add(new RelativePoint(x - centerX, z - centerZ))
         );
      }

      return new LandSupportMask(List.copyOf(coreRequired), List.copyOf(centralRequired), List.copyOf(edgeOptional));
   }

   private static boolean contains(StructureFootprint footprint, int x, int z) {
      return x >= footprint.minX() && x <= footprint.maxX() && z >= footprint.minZ() && z <= footprint.maxZ();
   }
}
