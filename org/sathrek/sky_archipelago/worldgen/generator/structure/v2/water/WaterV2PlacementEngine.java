package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water;

import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.CategoryPlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementDecision;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.model.WaterAnchorProfile;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.OceanFloorReservationRegistry;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.WaterVolumeReservationRegistry;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.WaterPlacementMode;

public final class WaterV2PlacementEngine implements CategoryPlacementEngine {
   private static final int PAD_ELIGIBLE_AREA = 1024;
   private static final int PAD_SMOOTHING_MARGIN_BLOCKS = 32;
   private final WaterModeSelector modeSelector = new WaterModeSelector();
   private final WaterAnchorPlanner anchorPlanner = new WaterAnchorPlanner();
   private final WaterDepthProbe depthProbe = new WaterDepthProbe();
   private final WaterConstraintEvaluator constraintEvaluator = new WaterConstraintEvaluator();
   private final WaterPlacementCommitter committer = new WaterPlacementCommitter();
   private final WaterTelemetry telemetry = new WaterTelemetry();

   @Override
   public PlacementDecision place(PlacementRequest request) {
      BoundingBox bounds = request.structureStart().getBoundingBox();
      WaterConstraintEvaluator.ValidationResult common = this.constraintEvaluator.validateCommon(request.settings().terrain().ocean().oceanEnabled(), bounds);
      WaterPlacementMode mode = this.modeSelector.select(request.settings().advanced().structurePlacementPolicy(), request.structureId());
      if (!common.accepted()) {
         this.telemetry.rejected(request.structureId(), request.chunkPos(), mode, common.reason(), request.settings().terrain().ocean().oceanLevelY(), null);
         return PlacementDecision.rejected("water_v2_rejected", common.reason());
      }

      int oceanLevelY = request.settings().terrain().ocean().oceanLevelY();
      int surfaceOffset = request.settings().advanced().structurePlacementPolicy().waterSurfaceOffsetFor(request.structureId());
      int requestedBaseY = mode == WaterPlacementMode.SURFACE ? oceanLevelY + surfaceOffset : oceanLevelY;
      Optional<DynamicWaterStructureAdapter> adapter = DynamicWaterStructureAdapters.find(request.structureId(), mode, bounds);
      WaterAnchorProfile anchorProfile = adapter.<WaterAnchorProfile>map(value -> value.anchorProfile(request.structureId(), mode))
         .orElseGet(() -> WaterAnchorProfile.forStructure(request.structureId(), mode));
      boolean adapterBacked = adapter.isPresent();
      boolean padAllowed = adapterBacked || isPadEligible(mode, bounds);
      int searchRadiusBlocks = request.settings().advanced().structurePlacementPolicy().localSearchRadiusBlocksForCategory(StructurePlacementCategory.WATER);
      int searchStepBlocks = request.settings().advanced().structurePlacementPolicy().localSearchStepBlocksForCategory(StructurePlacementCategory.WATER);
      WaterDepthProbe.ProbeResult probeResult = this.depthProbe
         .findPlacement(
            bounds,
            mode,
            requestedBaseY,
            oceanLevelY,
            searchRadiusBlocks,
            searchStepBlocks,
            request.islandField(),
            request.settings(),
            request.chunk().getMinBuildHeight(),
            request.chunk().getMaxBuildHeight(),
            padAllowed,
            anchorProfile
         );
      WaterConstraintEvaluator.ValidationResult probeValidation = this.constraintEvaluator.validateModeProbe(probeResult);
      if (!probeValidation.accepted()) {
         this.telemetry.rejected(request.structureId(), request.chunkPos(), mode, probeValidation.reason(), oceanLevelY, probeResult);
         return PlacementDecision.rejected("water_v2_rejected", probeValidation.reason());
      }

      WaterAnchorPlanner.AnchorPlan anchor = this.anchorPlanner
         .plan(bounds, probeResult.targetX(), probeResult.targetZ(), probeResult.targetBodyY(), anchorProfile);
      int targetBodyY = anchor.targetBodyY();
      int dx = anchor.offsetX();
      int dz = anchor.offsetZ();
      int dy = anchor.offsetY();
      boolean padReserved = false;
      Integer volumeWaterTopY = null;
      Integer volumeCleanupBottomY = null;
      Integer volumeCleanupTopY = null;
      Integer volumeTopOnlyCutoffY = null;
      if (adapterBacked) {
         DynamicWaterStructureAdapter value = adapter.orElseThrow();
         StructureFootprint footprint = footprintFor(bounds).translate(dx, dz);
         BoundingBox finalBounds = translate(bounds, dx, dy, dz);
         volumeWaterTopY = value.waterTopY(request.settings());
         volumeCleanupBottomY = value.cleanupBottomY(finalBounds, targetBodyY, request.chunk().getMinBuildHeight());
         volumeCleanupTopY = value.cleanupTopY(finalBounds, volumeWaterTopY);
         volumeTopOnlyCutoffY = value.topOnlyCutoffY(finalBounds, targetBodyY);
         padReserved = WaterVolumeReservationRegistry.tryReserve(
            request.structureId(),
            value.adapterId(),
            footprint,
            targetBodyY,
            volumeTopOnlyCutoffY,
            volumeWaterTopY,
            finalBounds.maxY(),
            volumeCleanupBottomY,
            volumeCleanupTopY,
            value.cleanupFootprintMarginBlocks(),
            value.padSmoothingMarginBlocks(),
            request.levelSeed()
         );
         if (!padReserved) {
            this.telemetry.rejected(request.structureId(), request.chunkPos(), mode, "rejected_water_v2_volume_reservation_overlap", oceanLevelY, probeResult);
            return PlacementDecision.rejected("water_v2_rejected", "rejected_water_v2_volume_reservation_overlap");
         }
      } else if (isPadEligible(mode, bounds)) {
         StructureFootprint footprint = footprintFor(bounds).translate(dx, dz);
         padReserved = OceanFloorReservationRegistry.tryReserve(request.structureId(), footprint, targetBodyY, 32, request.levelSeed());
         if (!padReserved) {
            this.telemetry.rejected(request.structureId(), request.chunkPos(), mode, "rejected_water_v2_reservation_overlap", oceanLevelY, probeResult);
            return PlacementDecision.rejected("water_v2_rejected", "rejected_water_v2_reservation_overlap");
         }
      }

      if (!StructureRegistryGuard.canCommit(request, "water_v2")) {
         return PlacementDecision.rejected("unregistered_structure", "unregistered_structure");
      }

      StructureStart committed = this.committer.commit(request, request.structureStart(), dx, dy, dz);
      if (committed == null) {
         return PlacementDecision.rejected("unregistered_structure", "unregistered_structure");
      }

      this.telemetry
         .accepted(
            request.structureId(),
            request.chunkPos(),
            mode,
            oceanLevelY,
            targetBodyY,
            anchor.targetX(),
            anchor.targetZ(),
            dx,
            dy,
            dz,
            probeResult,
            bounds,
            anchorProfile,
            padReserved,
            adapter.map(DynamicWaterStructureAdapter::adapterId).orElse(null),
            volumeTopOnlyCutoffY,
            volumeWaterTopY,
            volumeCleanupBottomY,
            volumeCleanupTopY
         );
      return PlacementDecision.accepted("water_v2_accepted", "mode=" + mode + ",targetBodyY=" + targetBodyY + ",padReserved=" + padReserved, committed);
   }

   private static boolean isPadEligible(WaterPlacementMode mode, BoundingBox bounds) {
      return mode == WaterPlacementMode.OCEAN_FLOOR && WaterDepthProbe.footprintArea(bounds) >= 1024;
   }

   private static StructureFootprint footprintFor(BoundingBox bounds) {
      return new StructureFootprint(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
   }

   private static BoundingBox translate(BoundingBox bounds, int dx, int dy, int dz) {
      return new BoundingBox(bounds.minX() + dx, bounds.minY() + dy, bounds.minZ() + dz, bounds.maxX() + dx, bounds.maxY() + dy, bounds.maxZ() + dz);
   }
}
