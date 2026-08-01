package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.sky;

import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.CategoryPlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementDecision;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementCommitCoordinator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.ReservationContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureOverlapGuard;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportValidator;

public final class SkyV2PlacementEngine implements CategoryPlacementEngine {
   private final StructureSupportValidator supportValidator;
   private final PlacementCommitCoordinator commitCoordinator;

   public SkyV2PlacementEngine(StructureSupportValidator supportValidator) {
      this.supportValidator = supportValidator;
      this.commitCoordinator = new PlacementCommitCoordinator(new StructureOverlapGuard());
   }

   @Override
   public PlacementDecision place(PlacementRequest request) {
      if (!request.structureStart().isValid()) {
         return PlacementDecision.rejected("sky_v2_invalid_start", "invalid_start");
      }

      if (!StructureRegistryGuard.canCommit(request, "sky_v2_direct")) {
         return PlacementDecision.rejected("unregistered_structure", "unregistered_structure");
      }

      StructureSupportContext supportContext = new StructureSupportContext(request.structureId(), request.settings(), request.islandField());
      StructureSupportValidator.SupportReport report = this.supportValidator
         .evaluatePlacement(supportContext, request.structureStart(), StructurePlacementCategory.SKY);
      if (!report.accepted()) {
         return PlacementDecision.rejected(
            "sky_v2_air_clearance_rejected",
            "skyAirValidated="
               + report.skyAirValidated()
               + ", nearbyIslands="
               + report.skyNearbyIslandColumns()
               + ", collisionSamples="
               + report.skyCollisionSamples()
               + ", minClearance="
               + (report.skyMinClearanceBlocks() == Integer.MAX_VALUE ? "none" : report.skyMinClearanceBlocks())
         );
      }

      PlacementCommitCoordinator.Decision reservationDecision = this.commitCoordinator
         .reserveOrConflict(
            request.structureStart(),
            request.structureStart().getBoundingBox(),
            StructurePlacementCategory.SKY,
            new ReservationContext(request.levelSeed(), request.structureId(), request.chunkPos())
         );
      if (!reservationDecision.accepted()) {
         return PlacementDecision.rejected("fcfs_3d_overlap", reservationDecision.details());
      }

      request.structureManager().setStartForStructure(request.sectionPos(), request.structure(), request.structureStart(), request.chunk());
      return PlacementDecision.accepted(
         "sky_v2_accepted",
         "skyAirValidated="
            + report.skyAirValidated()
            + ", nearbyIslands="
            + report.skyNearbyIslandColumns()
            + ", collisionSamples="
            + report.skyCollisionSamples()
            + ", minClearance="
            + (report.skyMinClearanceBlocks() == Integer.MAX_VALUE ? "none" : report.skyMinClearanceBlocks()),
         request.structureStart()
      );
   }
}
