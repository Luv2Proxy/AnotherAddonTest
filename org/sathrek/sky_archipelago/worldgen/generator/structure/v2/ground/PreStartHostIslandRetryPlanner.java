package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.ground;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class PreStartHostIslandRetryPlanner {
   public static final int MAX_RETRY_HOSTS = 3;

   public boolean isEligible(ResourceLocation structureId, StructurePlacementCategory category) {
      if (structureId != null && !isVanillaVillage(structureId)) {
         return switch (category) {
            case SURFACE_SKY, SMALL_SKY, HAMLET_SKY -> true;
            case GROUND_VILLAGE, DEFAULT, SKY, STRONGHOLD, UNDERGROUND, WATER -> false;
         };
      } else {
         return false;
      }
   }

   public List<PreStartHostIslandRetryPlanner.RetryAnchor> selectRetryAnchors(
      ResourceLocation structureId, StructurePlacementCategory category, SkyIslandSettings settings, IslandField islandField, ChunkPos sourceChunkPos
   ) {
      if (!this.isEligible(structureId, category)) {
         return List.of();
      }

      int searchRadiusBlocks = settings.advanced().structurePlacementPolicy().searchRadiusChunksForCategory(category) * 16;
      if (searchRadiusBlocks <= 0) {
         return List.of();
      }

      int sourceCenterX = sourceChunkPos.getMiddleBlockX();
      int sourceCenterZ = sourceChunkPos.getMiddleBlockZ();
      int minHostIslandRadius = settings.advanced().structurePlacementPolicy().minHostIslandRadiusForCategory(category);
      int minHostStableTopCells = settings.advanced().structurePlacementPolicy().minHostStableTopCellsForCategory(category);

      int maxTopDelta = switch (category) {
         case SURFACE_SKY -> 3;
         case SMALL_SKY -> 4;
         case HAMLET_SKY -> 2;
         case GROUND_VILLAGE, DEFAULT, SKY, STRONGHOLD, UNDERGROUND, WATER -> 3;
      };

      int minThickness = switch (category) {
         case SURFACE_SKY -> 6;
         case SMALL_SKY -> 4;
         case HAMLET_SKY -> 8;
         case GROUND_VILLAGE, DEFAULT, SKY, STRONGHOLD, UNDERGROUND, WATER -> 6;
      };
      List<PreStartHostIslandRetryPlanner.RetryAnchor> candidates = new ArrayList<>();

      for (IslandField.IslandPreview preview : islandField.collectIslandPreviewsInRadius(sourceCenterX, sourceCenterZ, searchRadiusBlocks, settings)) {
         PreStartHostIslandRetryPlanner.RetryAnchor anchor = evaluatePreview(
            preview, sourceChunkPos, category, settings, islandField, minHostIslandRadius, minHostStableTopCells, maxTopDelta, minThickness
         );
         if (anchor != null) {
            candidates.add(anchor);
         }
      }

      candidates.sort(PreStartHostIslandRetryPlanner.RetryAnchor.ORDERING);
      Map<Long, PreStartHostIslandRetryPlanner.RetryAnchor> uniqueChunks = new LinkedHashMap<>();

      for (PreStartHostIslandRetryPlanner.RetryAnchor candidate : candidates) {
         uniqueChunks.putIfAbsent(candidate.chunkPos().toLong(), candidate);
         if (uniqueChunks.size() >= 3) {
            break;
         }
      }

      return List.copyOf(uniqueChunks.values());
   }

   public <T> PreStartHostIslandRetryPlanner.RetryExecution<T> executeRetries(
      List<PreStartHostIslandRetryPlanner.RetryAnchor> anchors, Function<PreStartHostIslandRetryPlanner.RetryAnchor, T> attempt, Predicate<T> successPredicate
   ) {
      int attempts = 0;

      for (PreStartHostIslandRetryPlanner.RetryAnchor anchor : anchors) {
         attempts++;
         T result = attempt.apply(anchor);
         if (successPredicate.test(result)) {
            return new PreStartHostIslandRetryPlanner.RetryExecution<>(true, anchor, result, attempts);
         }
      }

      return new PreStartHostIslandRetryPlanner.RetryExecution<>(false, null, null, attempts);
   }

   public StructureStart rebindStructureStartChunk(StructureStart structureStart, ChunkPos chunkPos) {
      if (structureStart == null || !structureStart.isValid()) {
         return structureStart;
      } else {
         return chunkPos.equals(structureStart.getChunkPos())
            ? structureStart
            : new StructureStart(structureStart.getStructure(), chunkPos, structureStart.getReferences(), new PiecesContainer(structureStart.getPieces()));
      }
   }

   private static PreStartHostIslandRetryPlanner.RetryAnchor evaluatePreview(
      IslandField.IslandPreview preview,
      ChunkPos sourceChunkPos,
      StructurePlacementCategory category,
      SkyIslandSettings settings,
      IslandField islandField,
      int minHostIslandRadius,
      int minHostStableTopCells,
      int maxTopDelta,
      int minThickness
   ) {
      if (preview.family() != IslandField.IslandFamily.ANCHOR_PLATEAU) {
         return null;
      }

      if (preview.radius() < minHostIslandRadius) {
         return null;
      }

      ChunkPos retryChunk = new ChunkPos(preview.x() >> 4, preview.z() >> 4);
      if (retryChunk.equals(sourceChunkPos)) {
         return null;
      }

      TerrainColumn centerColumn = islandField.sampleColumn(preview.x(), preview.z(), settings);
      if (!centerColumn.exists()) {
         return null;
      }

      int centerTopY = centerColumn.topY();
      int stableTopCells = countStableTopCells(preview, centerTopY, maxTopDelta, minThickness, settings, islandField);
      if (stableTopCells < minHostStableTopCells) {
         return null;
      }

      long dx = (long)preview.x() - sourceChunkPos.getMiddleBlockX();
      long dz = (long)preview.z() - sourceChunkPos.getMiddleBlockZ();
      long distanceSquared = dx * dx + dz * dz;
      return new PreStartHostIslandRetryPlanner.RetryAnchor(preview, retryChunk, stableTopCells, distanceSquared, category);
   }

   private static int countStableTopCells(
      IslandField.IslandPreview preview, int centerTopY, int maxTopDelta, int minThickness, SkyIslandSettings settings, IslandField islandField
   ) {
      int hostSampleHalfSpan = Math.min(Math.max(8, preview.radius() / 2), 16);
      int[] stableTopCells = new int[]{0};
      new StructureFootprint(
            preview.x() - hostSampleHalfSpan, preview.x() + hostSampleHalfSpan, preview.z() - hostSampleHalfSpan, preview.z() + hostSampleHalfSpan
         )
         .forEachGridPoint(settings.structureSupport().supportSampleGridSize(), (x, z) -> {
            TerrainColumn column = islandField.sampleColumn(x, z, settings);
            if (column.exists()) {
               if (Math.abs(column.topY() - centerTopY) <= maxTopDelta && column.thickness() >= minThickness) {
                  stableTopCells[0]++;
               }
            }
         });
      return stableTopCells[0];
   }

   private static boolean isVanillaVillage(ResourceLocation structureId) {
      return structureId != null && structureId.toString().toLowerCase(Locale.ROOT).contains("village");
   }

   public record RetryAnchor(
      IslandField.IslandPreview preview, ChunkPos chunkPos, int stableTopCells, long distanceSquared, StructurePlacementCategory category
   ) {
      private static final Comparator<PreStartHostIslandRetryPlanner.RetryAnchor> ORDERING = Comparator.<PreStartHostIslandRetryPlanner.RetryAnchor>comparingInt(
            anchor -> anchor.preview().radius()
         )
         .reversed()
         .thenComparingInt(PreStartHostIslandRetryPlanner.RetryAnchor::stableTopCells)
         .reversed()
         .thenComparingLong(PreStartHostIslandRetryPlanner.RetryAnchor::distanceSquared)
         .thenComparingInt(anchor -> anchor.preview().x())
         .thenComparingInt(anchor -> anchor.preview().z())
         .thenComparingInt(anchor -> anchor.preview().y());
   }

   public record RetryExecution<T>(boolean succeeded, PreStartHostIslandRetryPlanner.RetryAnchor winningAnchor, T result, int attempts) {
   }
}
