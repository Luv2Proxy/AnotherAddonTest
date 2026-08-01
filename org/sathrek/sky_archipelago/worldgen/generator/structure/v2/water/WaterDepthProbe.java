package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.model.WaterAnchorProfile;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandColumnMaterialPlan;
import org.sathrek.sky_archipelago.worldgen.structure.WaterPlacementMode;

public final class WaterDepthProbe {
   private static final int SAMPLE_STEP_BLOCKS = 8;
   private static final int FLOOR_MATCH_TOLERANCE_BLOCKS = 3;
   private static final int PAD_ELIGIBLE_AREA = 1024;
   static final int PAD_MAX_SURFACE_PROTRUSION_BLOCKS = 12;
   private static final int PAD_MIN_BUILD_CLEARANCE_BLOCKS = 1;

   public WaterDepthProbe.ProbeResult findPlacement(
      BoundingBox bounds,
      WaterPlacementMode mode,
      int requestedBaseY,
      int oceanLevelY,
      int searchRadiusBlocks,
      int searchStepBlocks,
      IslandField islandField,
      SkyIslandSettings settings,
      int minBuildY,
      int maxBuildY,
      boolean padAllowed
   ) {
      return this.findPlacement(
         bounds,
         mode,
         requestedBaseY,
         oceanLevelY,
         searchRadiusBlocks,
         searchStepBlocks,
         islandField,
         settings,
         minBuildY,
         maxBuildY,
         padAllowed,
         WaterAnchorProfile.defaultProfile()
      );
   }

   public WaterDepthProbe.ProbeResult findPlacement(
      BoundingBox bounds,
      WaterPlacementMode mode,
      int requestedBaseY,
      int oceanLevelY,
      int searchRadiusBlocks,
      int searchStepBlocks,
      IslandField islandField,
      SkyIslandSettings settings,
      int minBuildY,
      int maxBuildY,
      boolean padAllowed,
      WaterAnchorProfile anchorProfile
   ) {
      List<int[]> samples = samplePoints(bounds);
      int centerX = centerX(bounds);
      int centerZ = centerZ(bounds);
      boolean canReservePad = padAllowed && mode == WaterPlacementMode.OCEAN_FLOOR && footprintArea(bounds) >= 1024;
      int step = Math.max(1, searchStepBlocks);
      int radius = Math.max(0, searchRadiusBlocks);
      int candidatesScanned = 0;
      String rejectionReason = mode == WaterPlacementMode.SURFACE ? "rejected_water_v2_no_ocean_footprint" : "rejected_water_v2_no_floor_fit";

      for (int[] offset : candidateOffsets(radius, step)) {
         candidatesScanned++;
         BoundingBox translated = translateHorizontal(bounds, offset[0], offset[1]);
         WaterDepthProbe.CandidateResult candidate = this.evaluateCandidate(
            translated,
            mode,
            requestedBaseY,
            oceanLevelY,
            samples,
            offset[0],
            offset[1],
            islandField,
            settings,
            minBuildY,
            maxBuildY,
            canReservePad,
            anchorProfile
         );
         if (candidate.accepted()) {
            return WaterDepthProbe.ProbeResult.accepted(
               centerX + offset[0],
               centerZ + offset[1],
               candidate.selectedOceanFloorY(),
               candidate.targetBodyY(),
               candidatesScanned,
               samples.size(),
               candidate.supportingSamples(),
               candidate.supportRatio()
            );
         }

         if (candidate.rejectionReason() != null) {
            rejectionReason = candidate.rejectionReason();
         }
      }

      return WaterDepthProbe.ProbeResult.rejected(rejectionReason, candidatesScanned, samples.size(), 0, 0.0);
   }

   private WaterDepthProbe.CandidateResult evaluateCandidate(
      BoundingBox translatedBounds,
      WaterPlacementMode mode,
      int requestedBaseY,
      int oceanLevelY,
      List<int[]> originalSamples,
      int offsetX,
      int offsetZ,
      IslandField islandField,
      SkyIslandSettings settings,
      int minBuildY,
      int maxBuildY,
      boolean canReservePad,
      WaterAnchorProfile anchorProfile
   ) {
      int minFloorY = Integer.MAX_VALUE;
      int maxFloorY = Integer.MIN_VALUE;
      int supporting = 0;

      for (int[] originalSample : originalSamples) {
         int sampleX = originalSample[0] + offsetX;
         int sampleZ = originalSample[1] + offsetZ;
         SkyIslandColumnMaterialPlan plan = SkyIslandColumnMaterialPlan.create(
            islandField.sampleSolidSegments(sampleX, sampleZ, settings), minBuildY, maxBuildY, settings, sampleX, sampleZ, islandField.layoutSeed()
         );
         if (!isOceanWaterColumn(plan, oceanLevelY)) {
            return WaterDepthProbe.CandidateResult.rejected();
         }

         supporting++;
         minFloorY = Math.min(minFloorY, plan.oceanFloorTopY());
         maxFloorY = Math.max(maxFloorY, plan.oceanFloorTopY());
      }

      if (supporting != originalSamples.size()) {
         return WaterDepthProbe.CandidateResult.rejected();
      }

      if (mode == WaterPlacementMode.SURFACE) {
         return WaterDepthProbe.CandidateResult.accepted(maxFloorY, requestedBaseY, supporting, ratio(supporting, originalSamples.size()));
      }

      if (!canReservePad && maxFloorY - minFloorY > 3) {
         return WaterDepthProbe.CandidateResult.rejected();
      }

      int targetBodyY = canReservePad ? padTargetBodyY(translatedBounds, maxFloorY, oceanLevelY, minBuildY, anchorProfile) : maxFloorY;
      int dy = anchorProfile.verticalOffsetTo(translatedBounds, targetBodyY);
      return !canReservePad && translatedBounds.maxY() + dy >= oceanLevelY
         ? WaterDepthProbe.CandidateResult.rejected("rejected_water_v2_not_fully_submerged")
         : WaterDepthProbe.CandidateResult.accepted(maxFloorY, targetBodyY, supporting, ratio(supporting, originalSamples.size()));
   }

   static int padTargetBaseY(BoundingBox bounds, int naturalFloorY, int oceanLevelY, int minBuildY) {
      return padTargetBodyY(bounds, naturalFloorY, oceanLevelY, minBuildY, WaterAnchorProfile.defaultProfile());
   }

   static int padTargetBodyY(BoundingBox bounds, int naturalFloorY, int oceanLevelY, int minBuildY, WaterAnchorProfile anchorProfile) {
      int verticalSpan = anchorProfile.spanAboveAnchor(bounds);
      int shallowestBaseY = oceanLevelY + 12 - verticalSpan;
      int targetBaseY = Math.min(naturalFloorY, shallowestBaseY);
      return Math.max(minBuildY + 1, targetBaseY);
   }

   private static boolean isOceanWaterColumn(SkyIslandColumnMaterialPlan plan, int oceanLevelY) {
      if (!plan.oceanEnabled()) {
         return false;
      }

      int oceanTopY = Math.min(plan.oceanTopY(), oceanLevelY);
      if (plan.oceanFloorTopY() >= oceanTopY) {
         return false;
      }

      for (int y = plan.oceanFloorTopY() + 1; y <= oceanTopY; y++) {
         if (plan.materialSlotAt(y) != SkyIslandColumnMaterialPlan.MaterialSlot.OCEAN) {
            return false;
         }
      }

      return true;
   }

   private static List<int[]> candidateOffsets(int radius, int step) {
      ArrayList<int[]> offsets = new ArrayList<>();
      offsets.add(new int[]{0, 0});
      if (radius <= 0) {
         return offsets;
      }

      int dx = -radius;

      while (dx <= radius) {
         for (int dz = -radius; dz <= radius; dz += step) {
            if (dx != 0 || dz != 0) {
               offsets.add(new int[]{dx, dz});
            }
         }

         dx += step;
      }

      offsets.sort(
         Comparator.<int[]>comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1])
            .thenComparingInt(offset -> Math.abs(offset[0]))
            .thenComparingInt(offset -> Math.abs(offset[1]))
            .thenComparingInt(offset -> offset[0])
            .thenComparingInt(offset -> offset[1])
      );
      return offsets;
   }

   static List<int[]> samplePoints(BoundingBox bounds) {
      ArrayList<int[]> points = new ArrayList<>();
      points.add(new int[]{bounds.minX(), bounds.minZ()});
      points.add(new int[]{bounds.minX(), bounds.maxZ()});
      points.add(new int[]{bounds.maxX(), bounds.minZ()});
      points.add(new int[]{bounds.maxX(), bounds.maxZ()});
      points.add(new int[]{centerX(bounds), centerZ(bounds)});

      for (int x = bounds.minX(); x <= bounds.maxX(); x += 8) {
         for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 8) {
            points.add(new int[]{x, z});
         }
      }

      return points;
   }

   private static BoundingBox translateHorizontal(BoundingBox bounds, int dx, int dz) {
      return new BoundingBox(bounds.minX() + dx, bounds.minY(), bounds.minZ() + dz, bounds.maxX() + dx, bounds.maxY(), bounds.maxZ() + dz);
   }

   private static int centerX(BoundingBox bounds) {
      return Mth.floor((bounds.minX() + bounds.maxX()) * 0.5);
   }

   private static int centerZ(BoundingBox bounds) {
      return Mth.floor((bounds.minZ() + bounds.maxZ()) * 0.5);
   }

   static int footprintArea(BoundingBox bounds) {
      return Math.max(1, (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1));
   }

   private static double ratio(int supporting, int total) {
      return total == 0 ? 0.0 : (double)supporting / total;
   }

   private record CandidateResult(
      boolean accepted, String rejectionReason, int selectedOceanFloorY, int targetBodyY, int supportingSamples, double supportRatio
   ) {
      static WaterDepthProbe.CandidateResult accepted(int selectedOceanFloorY, int targetBodyY, int supportingSamples, double supportRatio) {
         return new WaterDepthProbe.CandidateResult(true, null, selectedOceanFloorY, targetBodyY, supportingSamples, supportRatio);
      }

      static WaterDepthProbe.CandidateResult rejected() {
         return rejected(null);
      }

      static WaterDepthProbe.CandidateResult rejected(String reason) {
         return new WaterDepthProbe.CandidateResult(false, reason, Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0.0);
      }
   }

   public record ProbeResult(
      boolean accepted,
      String rejectionReason,
      int targetX,
      int targetZ,
      int selectedOceanFloorY,
      int targetBodyY,
      int scannedCandidates,
      int totalSamples,
      int supportingSamples,
      double supportRatio
   ) {
      public int targetBaseY() {
         return this.targetBodyY;
      }

      static WaterDepthProbe.ProbeResult accepted(
         int targetX,
         int targetZ,
         int selectedOceanFloorY,
         int targetBodyY,
         int scannedCandidates,
         int totalSamples,
         int supportingSamples,
         double supportRatio
      ) {
         return new WaterDepthProbe.ProbeResult(
            true, null, targetX, targetZ, selectedOceanFloorY, targetBodyY, scannedCandidates, totalSamples, supportingSamples, supportRatio
         );
      }

      static WaterDepthProbe.ProbeResult rejected(String reason, int scannedCandidates, int totalSamples, int supportingSamples, double supportRatio) {
         return new WaterDepthProbe.ProbeResult(
            false,
            reason,
            Integer.MIN_VALUE,
            Integer.MIN_VALUE,
            Integer.MIN_VALUE,
            Integer.MIN_VALUE,
            scannedCandidates,
            totalSamples,
            supportingSamples,
            supportRatio
         );
      }
   }
}
