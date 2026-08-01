package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.stronghold;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandConfig;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.CategoryPlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementDecision;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementCommitCoordinator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.ReservationContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureOverlapGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureStartRelocator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureLocateIndex;
import org.sathrek.sky_archipelago.worldgen.structure.PieceAwareSupportPlaneResolver;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportValidator;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public final class StrongholdPlacementEngine implements CategoryPlacementEngine {
   private static final int STRONGHOLD_VERTICAL_SWEEP_MIN = -96;
   private static final int STRONGHOLD_VERTICAL_SWEEP_MAX = 96;
   private static final int STRONGHOLD_VERTICAL_SWEEP_STEP = 4;
   private final PieceAwareSupportPlaneResolver supportPlaneResolver;
   private final StructureSupportValidator supportValidator;
   private final StructureStartRelocator relocator;
   private final PlacementCommitCoordinator commitCoordinator;

   public StrongholdPlacementEngine(PieceAwareSupportPlaneResolver supportPlaneResolver, StructureSupportValidator supportValidator) {
      this.supportPlaneResolver = supportPlaneResolver;
      this.supportValidator = supportValidator;
      this.relocator = new StructureStartRelocator();
      this.commitCoordinator = new PlacementCommitCoordinator(new StructureOverlapGuard());
   }

   @Override
   public PlacementDecision place(PlacementRequest request) {
      if (!StructureRegistryGuard.canCommit(request, "stronghold_v2")) {
         return PlacementDecision.rejected("unregistered_structure", "unregistered_structure");
      }

      if (!SkyIslandConfig.strongholdHostIslandEnabled()) {
         logStructureVerbose(
            "Rejected stronghold-category {} in chunk [{}, {}]: rejectionStage=stronghold_primary_host_island_disabled",
            request.structureId(),
            request.chunkPos().x,
            request.chunkPos().z
         );
         return PlacementDecision.rejected("stronghold_primary_host_island_disabled", "stronghold_host_island_disabled");
      }

      PlacementRequest.StructureGenerationContext generation = request.generationContext();
      if (generation == null) {
         return PlacementDecision.rejected("stronghold_missing_generation_context", "missing_generation_context");
      }

      StructureSupportContext supportContext = new StructureSupportContext(request.structureId(), request.settings(), request.islandField());
      StructureSupportValidator.SupportReport initialSupportReport = this.supportValidator.evaluatePlacement(supportContext, request.structureStart());
      long retrySeedTag = request.levelSeed() ^ (long)request.chunkPos().x << 32 ^ request.chunkPos().z ^ 1403312583L;
      BoundingBox structureBounds = request.structureStart().getBoundingBox();
      int targetX = Mth.floor((structureBounds.minX() + structureBounds.maxX()) * 0.5);
      int targetZ = Mth.floor((structureBounds.minZ() + structureBounds.maxZ()) * 0.5);
      int configuredMinRadius = SkyIslandConfig.strongholdHostIslandRadiusMin();
      int configuredMaxRadius = SkyIslandConfig.strongholdHostIslandRadiusMax();
      int computedMinRadius = Math.max(configuredMinRadius, estimateStrongholdHostRadius(structureBounds) + 48);
      int computedMaxRadius = Math.max(configuredMaxRadius, computedMinRadius + 56);
      int targetY = Mth.clamp(
         estimateStrongholdHostCenterY(structureBounds, initialSupportReport, computedMinRadius),
         request.settings().terrain().minIslandY(),
         request.settings().terrain().maxIslandY()
      );
      boolean injected = request.islandField()
         .injectForcedHostIsland(targetX, targetY, targetZ, computedMinRadius, computedMaxRadius, retrySeedTag, request.settings());
      logStructureVerbose(
         "Stronghold-category primary host-island gate passed for {} in chunk [{}, {}]: injectedHostIsland={}, hostCenter=({}, {}, {}), hostRadiusRange=[{}, {}]",
         request.structureId(),
         request.chunkPos().x,
         request.chunkPos().z,
         injected,
         targetX,
         targetY,
         targetZ,
         computedMinRadius,
         computedMaxRadius
      );
      if (!injected) {
         logStructureVerbose(
            "Stronghold {} in chunk [{}, {}] host-island injection skipped (already injected or duplicate center); continuing primary sweep",
            request.structureId(),
            request.chunkPos().x,
            request.chunkPos().z
         );
      }

      StructureStart bestRetryStart = null;
      StructureSupportValidator.SupportReport bestRetryReport = null;
      int bestYOffset = 0;

      for (int yOffset = -96; yOffset <= 96; yOffset += 4) {
         StructureStart retryStart = request.structure()
            .generate(
               generation.registryAccess(),
               generation.generator(),
               generation.biomeSource(),
               generation.randomState(),
               generation.templateManager(),
               request.levelSeed(),
               request.chunkPos(),
               generation.references(),
               request.chunk(),
               request.structure().biomes()::contains
            );
         if (retryStart.isValid()) {
            ResolvedStructureSupportPlane retrySupportPlane = this.supportPlaneResolver
               .resolve(
                  request.structureId(), retryStart, request.settings().advanced().structurePlacementPolicy().footprintInsetRatioFor(request.structureId())
               )
               .orElse(null);
            if (retrySupportPlane != null) {
               SkyStructurePlacementTarget hostCenterTarget = new SkyStructurePlacementTarget(
                  targetX,
                  targetY,
                  targetZ,
                  targetY,
                  0,
                  0,
                  0,
                  0,
                  0,
                  0.0,
                  request.settings().advanced().structurePlacementPolicy().searchRadiusChunksFor(request.structureId()) * 16,
                  IslandField.IslandFamily.ANCHOR_PLATEAU,
                  IslandField.ClusterHeightBand.MID_HIGH
               );
               retryStart = this.relocator.relocate(retryStart, request.chunkPos(), retrySupportPlane.rawFootprint(), retrySupportPlane, hostCenterTarget);
            }

            if (yOffset != 0) {
               retryStart = this.relocator.shiftY(retryStart, request.chunkPos(), yOffset);
            }

            StructureSupportValidator.SupportReport retrySupportReport = this.supportValidator.evaluatePlacement(supportContext, retryStart);
            if (bestRetryReport == null || retrySupportReport.supportRatio() > bestRetryReport.supportRatio()) {
               bestRetryStart = retryStart;
               bestRetryReport = retrySupportReport;
               bestYOffset = yOffset;
            }

            if (retrySupportReport.accepted()) {
               break;
            }
         }
      }

      if (bestRetryStart == null || bestRetryReport == null) {
         return PlacementDecision.rejected("stronghold_primary_host_island_rejected", "no_valid_retry_start");
      }

      if (bestRetryReport.accepted()) {
         logStructureVerbose(
            "Accepted stronghold {} in chunk [{}, {}] via primary host-island flow: support={}/{}, ratio={}, yOffset={}",
            request.structureId(),
            request.chunkPos().x,
            request.chunkPos().z,
            bestRetryReport.supportedPoints(),
            bestRetryReport.totalSamples(),
            formatRatio(bestRetryReport.supportRatio()),
            bestYOffset
         );
         PlacementCommitCoordinator.Decision reservationDecision = this.commitCoordinator
            .reserveOrConflict(
               bestRetryStart,
               bestRetryStart.getBoundingBox(),
               StructurePlacementCategory.STRONGHOLD,
               new ReservationContext(request.levelSeed(), request.structureId(), request.chunkPos())
            );
         if (!reservationDecision.accepted()) {
            return PlacementDecision.rejected("fcfs_3d_overlap", reservationDecision.details());
         }

         broadcastAcceptedStructureDebug(request.structureId(), bestRetryStart, bestRetryReport.resolvedBaseY());
         request.structureManager().setStartForStructure(request.sectionPos(), request.structure(), bestRetryStart, request.chunk());
         RelocatedStructureLocateIndex.recordCommittedRelocation(
            request.structureId(),
            request.dimension(),
            request.chunkPos(),
            centerOf(request.structureStart().getBoundingBox()),
            centerOf(bestRetryStart.getBoundingBox()),
            request.chunkPos()
         );
         return PlacementDecision.accepted(
            "stronghold_primary_host_island_accepted",
            "support="
               + bestRetryReport.supportedPoints()
               + "/"
               + bestRetryReport.totalSamples()
               + ",ratio="
               + formatRatio(bestRetryReport.supportRatio())
               + ",yOffset="
               + bestYOffset,
            bestRetryStart
         );
      } else {
         if (SkyIslandServerConfig.structureDebugEnabled()) {
            SkyArchipelago.LOGGER
               .info(
                  "Stronghold primary host-island rejected chunk=[{}, {}] bestSupport={}/{} bestRatio={} required={} bestYOffset={}",
                  new Object[]{
                     request.chunkPos().x,
                     request.chunkPos().z,
                     bestRetryReport.supportedPoints(),
                     bestRetryReport.totalSamples(),
                     formatRatio(bestRetryReport.supportRatio()),
                     formatRatio(bestRetryReport.requiredRatio()),
                     bestYOffset
                  }
               );
         }

         logStructureVerbose(
            "Stronghold {} in chunk [{}, {}] remained rejected after primary host-island flow: bestSupport={}/{}, bestRatio={}, required={}, bestYOffset={}",
            request.structureId(),
            request.chunkPos().x,
            request.chunkPos().z,
            bestRetryReport.supportedPoints(),
            bestRetryReport.totalSamples(),
            formatRatio(bestRetryReport.supportRatio()),
            formatRatio(bestRetryReport.requiredRatio()),
            bestYOffset
         );
         return PlacementDecision.rejected(
            "stronghold_primary_host_island_rejected",
            "bestSupport="
               + bestRetryReport.supportedPoints()
               + "/"
               + bestRetryReport.totalSamples()
               + ",bestRatio="
               + formatRatio(bestRetryReport.supportRatio())
               + ",required="
               + formatRatio(bestRetryReport.requiredRatio())
               + ",bestYOffset="
               + bestYOffset
         );
      }
   }

   private static int estimateStrongholdHostRadius(BoundingBox bounds) {
      int spanX = Math.max(0, bounds.maxX() - bounds.minX());
      int spanZ = Math.max(0, bounds.maxZ() - bounds.minZ());
      int halfSpan = Math.max(spanX, spanZ) / 2;
      return Mth.clamp(Math.max(120, halfSpan + 16), 64, 256);
   }

   private static int estimateStrongholdHostCenterY(BoundingBox bounds, StructureSupportValidator.SupportReport initialSupportReport, int hostRadius) {
      int structureMidY = Mth.floor((bounds.minY() + bounds.maxY()) * 0.5);
      int resolvedBaseY = initialSupportReport.resolvedBaseY();
      int plateauHeight = Math.max(22, (int)Math.round(hostRadius * 0.36));
      int cliffDepth = Math.max(10, (int)Math.round(hostRadius * 0.24));
      int topToCenterOffset = Math.max(12, plateauHeight + cliffDepth / 2);
      int baseAnchoredCenterY = resolvedBaseY - topToCenterOffset;
      return Mth.floor((structureMidY + baseAnchoredCenterY) * 0.5);
   }

   private static void broadcastAcceptedStructureDebug(ResourceLocation structureId, StructureStart structureStart, int resolvedBaseY) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null && SkyIslandServerConfig.structureDebugEnabled()) {
         BoundingBox bounds = structureStart.getBoundingBox();
         int x = Mth.floor((bounds.minX() + bounds.maxX()) * 0.5);
         int y = resolvedBaseY;
         int z = Mth.floor((bounds.minZ() + bounds.maxZ()) * 0.5);
         server.execute(
            () -> server.getPlayerList()
               .broadcastSystemMessage(Component.literal("[Sky Archipelago] POI spawned: " + structureId + " @ " + x + ", " + y + ", " + z), false)
         );
      }
   }

   private static void logStructureVerbose(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }

   private static String formatRatio(double ratio) {
      return String.format(Locale.ROOT, "%.2f", ratio);
   }

   private static BlockPos centerOf(BoundingBox bounds) {
      return RelocatedStructureLocateIndex.centerOf(bounds);
   }
}
