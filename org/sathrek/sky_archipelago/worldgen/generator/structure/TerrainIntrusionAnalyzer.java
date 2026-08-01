package org.sathrek.sky_archipelago.worldgen.generator.structure;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

final class TerrainIntrusionAnalyzer {
   private static final int MIN_CLEARANCE_BLOCKS_ABOVE_BASE = 2;
   private static final int MAX_WORST_SAMPLES = 6;

   TerrainIntrusionAnalyzer.TerrainIntrusionReport analyze(
      StructurePlacementCategory category, StructureStart structureStart, IslandField islandField, SkyIslandSettings settings, int resolvedBaseY
   ) {
      if (isTrackedCategory(category) && structureStart != null && structureStart.isValid()) {
         List<BoundingBox> pieceBounds = structureStart.getPieces().stream().<BoundingBox>map(StructurePiece::getBoundingBox).toList();
         return this.analyzeFromPieceBounds(pieceBounds, islandField, settings, resolvedBaseY);
      } else {
         return TerrainIntrusionAnalyzer.TerrainIntrusionReport.notAttempted();
      }
   }

   TerrainIntrusionAnalyzer.TerrainIntrusionReport analyzeFromPieceBounds(
      List<BoundingBox> pieceBounds, IslandField islandField, SkyIslandSettings settings, int resolvedBaseY
   ) {
      if (pieceBounds != null && !pieceBounds.isEmpty()) {
         Map<Long, TerrainIntrusionAnalyzer.IntrusionSample> worstByXZ = new HashMap<>();
         int sampleGridSize = Math.max(2, settings.structureSupport().supportSampleGridSize());
         int totalSamples = 0;

         for (BoundingBox bounds : pieceBounds) {
            int intrusionMinY = Math.max(resolvedBaseY + 2, bounds.minY() + 1);
            if (intrusionMinY <= bounds.maxY()) {
               StructureFootprint pieceFootprint = new StructureFootprint(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
               pieceFootprint.forEachGridPoint(
                  sampleGridSize,
                  (x, z) -> {
                     TerrainColumn column = islandField.sampleColumn(x, z, settings);
                     if (column.exists() && column.intersectsInclusive(intrusionMinY, bounds.maxY())) {
                        int intrusionDepth = Math.min(column.topY(), bounds.maxY()) - Math.max(column.bottomY(), intrusionMinY) + 1;
                        if (intrusionDepth > 0) {
                           long key = pack(x, z);
                           TerrainIntrusionAnalyzer.IntrusionSample current = worstByXZ.get(key);
                           TerrainIntrusionAnalyzer.IntrusionSample candidate = new TerrainIntrusionAnalyzer.IntrusionSample(
                              x, z, column.topY(), intrusionMinY, bounds.maxY(), intrusionDepth
                           );
                           if (current == null || candidate.intrusionDepth() > current.intrusionDepth()) {
                              worstByXZ.put(key, candidate);
                           }
                        }
                     }
                  }
               );
               totalSamples += sampleGridSize * sampleGridSize;
            }
         }

         List<TerrainIntrusionAnalyzer.IntrusionSample> samples = worstByXZ.values()
            .stream()
            .sorted(
               Comparator.comparingInt(TerrainIntrusionAnalyzer.IntrusionSample::intrusionDepth)
                  .reversed()
                  .thenComparingInt(TerrainIntrusionAnalyzer.IntrusionSample::x)
                  .thenComparingInt(TerrainIntrusionAnalyzer.IntrusionSample::z)
            )
            .toList();
         int maxIntrusionDepth = samples.stream().mapToInt(TerrainIntrusionAnalyzer.IntrusionSample::intrusionDepth).max().orElse(0);
         return new TerrainIntrusionAnalyzer.TerrainIntrusionReport(true, totalSamples, samples.size(), maxIntrusionDepth, samples.stream().limit(6L).toList());
      } else {
         return TerrainIntrusionAnalyzer.TerrainIntrusionReport.emptyAttempt();
      }
   }

   private static boolean isTrackedCategory(StructurePlacementCategory category) {
      return category == StructurePlacementCategory.SURFACE_SKY
         || category == StructurePlacementCategory.SMALL_SKY
         || category == StructurePlacementCategory.HAMLET_SKY;
   }

   private static long pack(int x, int z) {
      return (long)x << 32 ^ z & 4294967295L;
   }

   record IntrusionSample(int x, int z, int terrainTopY, int intrusionMinY, int intrusionMaxY, int intrusionDepth) {
   }

   record TerrainIntrusionReport(
      boolean attempted, int totalSamples, int intersectingSamples, int maxIntrusionDepth, List<TerrainIntrusionAnalyzer.IntrusionSample> worstSamples
   ) {
      static TerrainIntrusionAnalyzer.TerrainIntrusionReport notAttempted() {
         return new TerrainIntrusionAnalyzer.TerrainIntrusionReport(false, 0, 0, 0, List.of());
      }

      static TerrainIntrusionAnalyzer.TerrainIntrusionReport emptyAttempt() {
         return new TerrainIntrusionAnalyzer.TerrainIntrusionReport(true, 0, 0, 0, List.of());
      }
   }
}
