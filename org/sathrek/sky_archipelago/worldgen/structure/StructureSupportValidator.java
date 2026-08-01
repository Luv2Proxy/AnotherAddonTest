package org.sathrek.sky_archipelago.worldgen.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;

public final class StructureSupportValidator {
   private static final int HAMLET_MIN_SUBSTANTIAL_PIECE_AREA = 16;
   private static final int HAMLET_MAJOR_PIECE_AREA = 48;
   private static final double HAMLET_MIN_GROUNDED_PIECE_RATIO = 0.75;
   private static final double HAMLET_MIN_PER_PIECE_SUPPORT_RATIO = 0.7;
   private static final int SKY_MIN_CLEARANCE_BLOCKS = 8;
   private static final int SKY_NEARBY_CONTEXT_RADIUS_BLOCKS = 96;
   private static final int SKY_NEARBY_CONTEXT_STEP_BLOCKS = 16;
   private static final int SKY_MIN_NEARBY_ISLAND_COLUMNS = 1;
   private final StructureSupportPlaneResolver supportPlaneResolver;

   public StructureSupportValidator(StructureSupportPlaneResolver supportPlaneResolver) {
      this.supportPlaneResolver = supportPlaneResolver;
   }

   public boolean isValidPlacement(StructureSupportContext context, StructureStart structureStart) {
      return this.evaluatePlacement(context, structureStart).accepted();
   }

   public StructureSupportValidator.SupportReport evaluatePlacement(StructureSupportContext context, StructureStart structureStart) {
      return this.evaluatePlacement(context, structureStart, null);
   }

   public StructureSupportValidator.SupportReport evaluatePlacement(
      StructureSupportContext context, StructureStart structureStart, StructurePlacementCategory overrideCategory
   ) {
      StructurePlacementCategory category = context.settings().advanced().structurePlacementPolicy().categoryFor(context.structureId());
      if (overrideCategory != null) {
         category = overrideCategory;
      }

      StructureSupportValidator.EvaluationCache cache = new StructureSupportValidator.EvaluationCache();
      if (!structureStart.isValid()) {
         return StructureSupportValidator.SupportReport.invalidReport(category);
      } else if (context.settings().advanced().structureWhitelist().isWhitelisted(context.structureId())) {
         return StructureSupportValidator.SupportReport.whitelistedReport(category, structureStart.getBoundingBox());
      } else if (category == StructurePlacementCategory.SKY) {
         return this.evaluateSkyAirPlacement(context, structureStart.getBoundingBox(), cache);
      } else {
         Optional<ResolvedStructureSupportPlane> supportPlane = this.supportPlaneResolver
            .resolve(
               context.structureId(), structureStart, context.settings().advanced().structurePlacementPolicy().footprintInsetRatioFor(context.structureId())
            );
         if (supportPlane.isEmpty()) {
            return StructureSupportValidator.SupportReport.missingFootprintReport(category, structureStart.getBoundingBox());
         } else {
            StructureSupportValidator.SupportReport footprintReport = this.evaluateFootprintSupport(
               context,
               category,
               supportPlane.get().rawFootprint(),
               supportPlane.get().effectiveFootprint(),
               structureStart.getBoundingBox(),
               supportPlane.get().scanStartY(),
               supportPlane.get().baseY(),
               supportPlane.get().usedFallback(),
               supportPlane.get().supportSliceCount(),
               supportPlane.get().supportSliceArea(),
               cache
            );
            if (footprintReport.accepted() && category == StructurePlacementCategory.HAMLET_SKY) {
               List<BoundingBox> pieceBounds = structureStart.getPieces().stream().<BoundingBox>map(StructurePiece::getBoundingBox).toList();
               return this.applyHamletPieceSupportCheck(context, pieceBounds, structureStart.getBoundingBox(), footprintReport);
            } else {
               return footprintReport;
            }
         }
      }
   }

   public boolean isFootprintSupported(StructureSupportContext context, StructureFootprint footprint, int scanStartY) {
      StructurePlacementCategory category = context.settings().advanced().structurePlacementPolicy().categoryFor(context.structureId());
      StructureFootprint effectiveFootprint = footprint.insetByRatio(
         context.settings().advanced().structurePlacementPolicy().footprintInsetRatioFor(context.structureId())
      );
      return this.evaluateFootprintSupport(context, category, footprint, effectiveFootprint, null, scanStartY, scanStartY + 1, false, 0, 0).accepted();
   }

   public StructureSupportValidator.SupportReport evaluateFootprintSupport(
      StructureSupportContext context,
      StructurePlacementCategory category,
      StructureFootprint footprint,
      StructureFootprint effectiveFootprint,
      BoundingBox structureBounds,
      int scanStartY,
      int resolvedBaseY,
      boolean usedSupportPlaneFallback,
      int supportSliceCount,
      int supportSliceArea
   ) {
      return this.evaluateFootprintSupport(
         context,
         category,
         footprint,
         effectiveFootprint,
         structureBounds,
         scanStartY,
         resolvedBaseY,
         usedSupportPlaneFallback,
         supportSliceCount,
         supportSliceArea,
         new StructureSupportValidator.EvaluationCache()
      );
   }

   private StructureSupportValidator.SupportReport evaluateFootprintSupport(
      StructureSupportContext context,
      StructurePlacementCategory category,
      StructureFootprint footprint,
      StructureFootprint effectiveFootprint,
      BoundingBox structureBounds,
      int scanStartY,
      int resolvedBaseY,
      boolean usedSupportPlaneFallback,
      int supportSliceCount,
      int supportSliceArea,
      StructureSupportValidator.EvaluationCache cache
   ) {
      int[] supportedPoints = new int[]{0};
      int[] totalSamples = new int[]{0};
      List<StructureSupportValidator.SupportSample> failingSamples = new ArrayList<>();
      effectiveFootprint.forEachGridPoint(context.settings().structureSupport().supportSampleGridSize(), (x, z) -> {
         totalSamples[0]++;
         boolean supported = cache.hasSupportBelow(context, x, z, scanStartY, context.settings().structureSupport().supportCheckDepth());
         if (supported) {
            supportedPoints[0]++;
         } else if (failingSamples.size() < 6) {
            failingSamples.add(new StructureSupportValidator.SupportSample(x, z));
         }
      });
      double ratio = totalSamples[0] <= 0 ? 0.0 : (double)supportedPoints[0] / totalSamples[0];
      double requiredRatio = context.settings()
         .advanced()
         .structurePlacementPolicy()
         .thresholdFor(context.structureId(), context.settings().structureSupport().supportThreshold());
      boolean accepted = passesThreshold(supportedPoints[0], totalSamples[0], requiredRatio);
      return new StructureSupportValidator.SupportReport(
         accepted,
         false,
         false,
         false,
         false,
         category,
         structureBounds,
         footprint,
         effectiveFootprint,
         resolvedBaseY,
         usedSupportPlaneFallback,
         supportSliceCount,
         supportSliceArea,
         scanStartY,
         context.settings().structureSupport().supportCheckDepth(),
         supportedPoints[0],
         totalSamples[0],
         ratio,
         requiredRatio,
         0,
         0,
         1.0,
         0.0,
         false,
         0,
         0,
         Integer.MIN_VALUE,
         List.copyOf(failingSamples)
      );
   }

   public static boolean passesThreshold(int supportedSamples, int totalSamples, double threshold) {
      return totalSamples <= 0 ? false : (double)supportedSamples / totalSamples >= threshold;
   }

   StructureSupportValidator.SupportReport evaluateSkyAirPlacement(StructureSupportContext context, BoundingBox bounds) {
      return this.evaluateSkyAirPlacement(context, bounds, new StructureSupportValidator.EvaluationCache());
   }

   private StructureSupportValidator.SupportReport evaluateSkyAirPlacement(
      StructureSupportContext context, BoundingBox bounds, StructureSupportValidator.EvaluationCache cache
   ) {
      StructureFootprint footprint = new StructureFootprint(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
      int collisionSamples = 0;
      int[] sampleCount = new int[]{0};
      int[] minObservedClearance = new int[]{Integer.MAX_VALUE};
      int[] collisionSamplesRef = new int[]{0};
      footprint.forEachGridPoint(context.settings().structureSupport().supportSampleGridSize(), (x, z) -> {
         sampleCount[0]++;
         TerrainColumn column = cache.sampleColumn(context, x, z);
         if (column.exists()) {
            int clearance = bounds.minY() - column.topY();
            minObservedClearance[0] = Math.min(minObservedClearance[0], clearance);
            if (clearance <= 8) {
               collisionSamplesRef[0]++;
            }
         }
      });
      collisionSamples = collisionSamplesRef[0];
      int centerX = Mth.floor((bounds.minX() + bounds.maxX()) * 0.5);
      int centerZ = Mth.floor((bounds.minZ() + bounds.maxZ()) * 0.5);
      int nearbyIslands = 0;

      for (int x = centerX - 96; x <= centerX + 96; x += 16) {
         for (int z = centerZ - 96; z <= centerZ + 96; z += 16) {
            if (cache.sampleColumn(context, x, z).exists()) {
               nearbyIslands++;
            }
         }
      }

      boolean passesVoidGuard = nearbyIslands >= 1;
      boolean passesClearance = collisionSamples == 0;
      boolean accepted = passesVoidGuard && passesClearance;
      int resolvedClearance = minObservedClearance[0] == Integer.MAX_VALUE ? Integer.MAX_VALUE : minObservedClearance[0];
      double supportRatio = sampleCount[0] == 0 ? 1.0 : (double)(sampleCount[0] - collisionSamples) / sampleCount[0];
      return new StructureSupportValidator.SupportReport(
         accepted,
         false,
         false,
         false,
         false,
         StructurePlacementCategory.SKY,
         bounds,
         footprint,
         footprint,
         bounds.minY(),
         false,
         0,
         0,
         bounds.minY(),
         0,
         sampleCount[0] - collisionSamples,
         sampleCount[0],
         supportRatio,
         1.0,
         0,
         0,
         1.0,
         0.0,
         true,
         nearbyIslands,
         collisionSamples,
         resolvedClearance,
         List.of()
      );
   }

   StructureSupportValidator.SupportReport applyHamletPieceSupportCheck(
      StructureSupportContext context, List<BoundingBox> pieceBounds, BoundingBox structureBounds, StructureSupportValidator.SupportReport baseline
   ) {
      StructureSupportValidator.EvaluationCache cache = new StructureSupportValidator.EvaluationCache();
      List<BoundingBox> substantialBounds = pieceBounds.stream().filter(boundsx -> footprintArea(boundsx) >= 16).toList();
      if (substantialBounds.isEmpty()) {
         return baseline.withHamletPieceSupport(false, 0, 0, 1.0, 0.75, baseline.failingSamples());
      }

      int groundedPieces = 0;
      boolean majorPieceRejected = false;
      StructureSupportValidator.SupportReport weakestPieceReport = null;
      int maxGroundGapBlocks = context.settings().advanced().structurePlacementPolicy().maxGroundGapBlocksFor(context.structureId());

      for (BoundingBox bounds : substantialBounds) {
         StructureSupportValidator.SupportReport pieceReport = this.evaluateHamletPieceGrounding(context, bounds, structureBounds, maxGroundGapBlocks, cache);
         boolean grounded = pieceReport.supportRatio() >= 0.7;
         if (grounded) {
            groundedPieces++;
         } else if (footprintArea(bounds) >= 48) {
            majorPieceRejected = true;
         }

         if (weakestPieceReport == null || pieceReport.supportRatio() < weakestPieceReport.supportRatio()) {
            weakestPieceReport = pieceReport;
         }
      }

      double groundedRatio = (double)groundedPieces / substantialBounds.size();
      boolean accepted = groundedRatio >= 0.75 && !majorPieceRejected;
      List<StructureSupportValidator.SupportSample> failingSamples = weakestPieceReport != null
         ? weakestPieceReport.failingSamples()
         : baseline.failingSamples();
      return accepted
         ? baseline.withHamletPieceSupport(false, groundedPieces, substantialBounds.size(), groundedRatio, 0.75, failingSamples)
         : baseline.withAccepted(false).withHamletPieceSupport(true, groundedPieces, substantialBounds.size(), groundedRatio, 0.75, failingSamples);
   }

   private StructureSupportValidator.SupportReport evaluateHamletPieceGrounding(
      StructureSupportContext context, BoundingBox bounds, BoundingBox structureBounds, int maxGroundGapBlocks, StructureSupportValidator.EvaluationCache cache
   ) {
      StructureFootprint footprint = new StructureFootprint(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
      int[] groundedPoints = new int[]{0};
      int[] totalSamples = new int[]{0};
      List<StructureSupportValidator.SupportSample> failingSamples = new ArrayList<>();
      int pieceBaseY = bounds.minY();
      footprint.forEachGridPoint(context.settings().structureSupport().supportSampleGridSize(), (x, z) -> {
         totalSamples[0]++;
         TerrainColumn column = cache.sampleColumn(context, x, z);
         boolean grounded = column.exists() && pieceBaseY - column.topY() >= 0 && pieceBaseY - column.topY() <= maxGroundGapBlocks;
         if (grounded) {
            groundedPoints[0]++;
         } else if (failingSamples.size() < 6) {
            failingSamples.add(new StructureSupportValidator.SupportSample(x, z));
         }
      });
      double ratio = totalSamples[0] <= 0 ? 0.0 : (double)groundedPoints[0] / totalSamples[0];
      return new StructureSupportValidator.SupportReport(
         ratio >= 0.7,
         false,
         false,
         false,
         false,
         StructurePlacementCategory.HAMLET_SKY,
         structureBounds,
         footprint,
         footprint,
         pieceBaseY,
         false,
         1,
         footprintArea(bounds),
         pieceBaseY - 1,
         maxGroundGapBlocks + 1,
         groundedPoints[0],
         totalSamples[0],
         ratio,
         0.7,
         0,
         0,
         1.0,
         0.0,
         false,
         0,
         0,
         Integer.MIN_VALUE,
         List.copyOf(failingSamples)
      );
   }

   private static int footprintArea(BoundingBox bounds) {
      return Math.max(0, (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1));
   }

   private static final class EvaluationCache {
      private final Map<Long, TerrainColumn> columnByXZ = new HashMap<>();
      private final Map<Long, Boolean> supportByXZAndY = new HashMap<>();

      TerrainColumn sampleColumn(StructureSupportContext context, int x, int z) {
         long key = packPair(x, z);
         return this.columnByXZ.computeIfAbsent(key, ignored -> context.islandField().sampleColumn(x, z, context.settings()));
      }

      boolean hasSupportBelow(StructureSupportContext context, int x, int z, int fromYInclusive, int depth) {
         long key = mixSupportKey(x, z, fromYInclusive, depth);
         return this.supportByXZAndY.computeIfAbsent(key, ignored -> context.islandField().hasSupportBelow(x, z, fromYInclusive, depth, context.settings()));
      }

      private static long packPair(int x, int z) {
         return (long)x << 32 ^ z & 4294967295L;
      }

      private static long mixSupportKey(int x, int z, int y, int depth) {
         long hash = packPair(x, z);
         hash ^= y * -7046029254386353131L;
         hash ^= depth * -4417276706812531889L;
         hash ^= hash >>> 33;
         hash *= -49064778989728563L;
         return hash ^ hash >>> 33;
      }
   }

   public record SupportReport(
      boolean accepted,
      boolean whitelisted,
      boolean missingFootprint,
      boolean invalidStart,
      boolean hamletPieceSupportRejected,
      StructurePlacementCategory category,
      BoundingBox structureBounds,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      int resolvedBaseY,
      boolean usedSupportPlaneFallback,
      int supportSliceCount,
      int supportSliceArea,
      int scanStartY,
      int scanDepth,
      int supportedPoints,
      int totalSamples,
      double supportRatio,
      double requiredRatio,
      int hamletGroundedPieces,
      int hamletTotalPieces,
      double hamletGroundedRatio,
      double hamletRequiredRatio,
      boolean skyAirValidated,
      int skyNearbyIslandColumns,
      int skyCollisionSamples,
      int skyMinClearanceBlocks,
      List<StructureSupportValidator.SupportSample> failingSamples
   ) {
      public static StructureSupportValidator.SupportReport invalidReport(StructurePlacementCategory category) {
         return new StructureSupportValidator.SupportReport(
            false,
            false,
            false,
            true,
            false,
            category,
            null,
            null,
            null,
            0,
            false,
            0,
            0,
            0,
            0,
            0,
            0,
            0.0,
            0.0,
            0,
            0,
            0.0,
            0.0,
            false,
            0,
            0,
            Integer.MIN_VALUE,
            List.of()
         );
      }

      public static StructureSupportValidator.SupportReport whitelistedReport(StructurePlacementCategory category, BoundingBox structureBounds) {
         return new StructureSupportValidator.SupportReport(
            true,
            true,
            false,
            false,
            false,
            category,
            structureBounds,
            null,
            null,
            0,
            false,
            0,
            0,
            0,
            0,
            0,
            0,
            1.0,
            0.0,
            0,
            0,
            1.0,
            0.0,
            false,
            0,
            0,
            Integer.MIN_VALUE,
            List.of()
         );
      }

      public static StructureSupportValidator.SupportReport missingFootprintReport(StructurePlacementCategory category, BoundingBox structureBounds) {
         return new StructureSupportValidator.SupportReport(
            false,
            false,
            true,
            false,
            false,
            category,
            structureBounds,
            null,
            null,
            0,
            false,
            0,
            0,
            0,
            0,
            0,
            0,
            0.0,
            0.0,
            0,
            0,
            0.0,
            0.0,
            false,
            0,
            0,
            Integer.MIN_VALUE,
            List.of()
         );
      }

      public StructureSupportValidator.SupportReport withAccepted(boolean nextAccepted) {
         return new StructureSupportValidator.SupportReport(
            nextAccepted,
            this.whitelisted,
            this.missingFootprint,
            this.invalidStart,
            this.hamletPieceSupportRejected,
            this.category,
            this.structureBounds,
            this.rawFootprint,
            this.effectiveFootprint,
            this.resolvedBaseY,
            this.usedSupportPlaneFallback,
            this.supportSliceCount,
            this.supportSliceArea,
            this.scanStartY,
            this.scanDepth,
            this.supportedPoints,
            this.totalSamples,
            this.supportRatio,
            this.requiredRatio,
            this.hamletGroundedPieces,
            this.hamletTotalPieces,
            this.hamletGroundedRatio,
            this.hamletRequiredRatio,
            this.skyAirValidated,
            this.skyNearbyIslandColumns,
            this.skyCollisionSamples,
            this.skyMinClearanceBlocks,
            this.failingSamples
         );
      }

      public StructureSupportValidator.SupportReport withHamletPieceSupport(
         boolean rejected,
         int groundedPieces,
         int totalPieces,
         double groundedRatio,
         double requiredRatio,
         List<StructureSupportValidator.SupportSample> nextFailingSamples
      ) {
         return new StructureSupportValidator.SupportReport(
            this.accepted,
            this.whitelisted,
            this.missingFootprint,
            this.invalidStart,
            rejected,
            this.category,
            this.structureBounds,
            this.rawFootprint,
            this.effectiveFootprint,
            this.resolvedBaseY,
            this.usedSupportPlaneFallback,
            this.supportSliceCount,
            this.supportSliceArea,
            this.scanStartY,
            this.scanDepth,
            this.supportedPoints,
            this.totalSamples,
            this.supportRatio,
            this.requiredRatio,
            groundedPieces,
            totalPieces,
            groundedRatio,
            requiredRatio,
            this.skyAirValidated,
            this.skyNearbyIslandColumns,
            this.skyCollisionSamples,
            this.skyMinClearanceBlocks,
            List.copyOf(nextFailingSamples)
         );
      }
   }

   public record SupportSample(int x, int z) {
   }
}
