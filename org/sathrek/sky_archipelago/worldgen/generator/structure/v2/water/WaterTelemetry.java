package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.model.WaterAnchorProfile;
import org.sathrek.sky_archipelago.worldgen.structure.WaterPlacementMode;

public final class WaterTelemetry {
   public void accepted(
      ResourceLocation structureId,
      ChunkPos chunkPos,
      WaterPlacementMode mode,
      int oceanLevelY,
      int targetBodyY,
      int targetX,
      int targetZ,
      int dx,
      int dy,
      int dz,
      WaterDepthProbe.ProbeResult probeResult,
      BoundingBox bounds,
      WaterAnchorProfile anchorProfile,
      boolean padReserved
   ) {
      this.accepted(
         structureId,
         chunkPos,
         mode,
         oceanLevelY,
         targetBodyY,
         targetX,
         targetZ,
         dx,
         dy,
         dz,
         probeResult,
         bounds,
         anchorProfile,
         padReserved,
         null,
         null,
         null,
         null,
         null
      );
   }

   public void accepted(
      ResourceLocation structureId,
      ChunkPos chunkPos,
      WaterPlacementMode mode,
      int oceanLevelY,
      int targetBodyY,
      int targetX,
      int targetZ,
      int dx,
      int dy,
      int dz,
      WaterDepthProbe.ProbeResult probeResult,
      BoundingBox bounds,
      WaterAnchorProfile anchorProfile,
      boolean padReserved,
      ResourceLocation adapterId,
      Integer topOnlyCutoffY,
      Integer waterTopY,
      Integer cleanupBottomY,
      Integer cleanupTopY
   ) {
      int finalMinY = bounds != null ? bounds.minY() + dy : Integer.MIN_VALUE;
      int finalMaxY = bounds != null ? bounds.maxY() + dy : Integer.MIN_VALUE;
      debug(
         "WATER_V2_ACCEPT id={} chunk=[{}, {}] mode={} oceanLevelY={} selectedOceanFloorY={} targetBodyY={} target=({}, {}, {}) delta=({}, {}, {}) boundsY=[{}, {}] anchorLocalOffsetY={} finalBoundsY=[{}, {}] probeAccepted={} probeSteps={} probeSupport={}/{} probeRatio={} padReserved={} adapterId={} topOnlyCutoffY={} waterTopY={} cleanupY=[{}, {}]",
         structureId,
         chunkPos.x,
         chunkPos.z,
         mode,
         oceanLevelY,
         probeResult != null ? probeResult.selectedOceanFloorY() : Integer.MIN_VALUE,
         targetBodyY,
         targetX,
         targetBodyY,
         targetZ,
         dx,
         dy,
         dz,
         bounds != null ? bounds.minY() : Integer.MIN_VALUE,
         bounds != null ? bounds.maxY() : Integer.MIN_VALUE,
         anchorProfile != null ? anchorProfile.anchorLocalOffsetY() : 0,
         finalMinY,
         finalMaxY,
         probeResult != null && probeResult.accepted(),
         probeResult != null ? probeResult.scannedCandidates() : 0,
         probeResult != null ? probeResult.supportingSamples() : 0,
         probeResult != null ? probeResult.totalSamples() : 0,
         probeResult != null ? formatRatio(probeResult.supportRatio()) : "0.00",
         padReserved,
         adapterId != null ? adapterId : "none",
         topOnlyCutoffY != null ? topOnlyCutoffY : Integer.MIN_VALUE,
         waterTopY != null ? waterTopY : Integer.MIN_VALUE,
         cleanupBottomY != null ? cleanupBottomY : Integer.MIN_VALUE,
         cleanupTopY != null ? cleanupTopY : Integer.MIN_VALUE
      );
      broadcastAcceptedPlacement(structureId, mode, targetX, targetBodyY, targetZ);
   }

   public void rejected(
      ResourceLocation structureId, ChunkPos chunkPos, WaterPlacementMode mode, String reason, int oceanLevelY, WaterDepthProbe.ProbeResult probeResult
   ) {
      debug(
         "WATER_V2_REJECT id={} chunk=[{}, {}] mode={} reason={} oceanLevelY={} probeAccepted={} probeSteps={} probeSupport={}/{} probeRatio={}",
         structureId,
         chunkPos.x,
         chunkPos.z,
         mode,
         reason,
         oceanLevelY,
         probeResult != null && probeResult.accepted(),
         probeResult != null ? probeResult.scannedCandidates() : 0,
         probeResult != null ? probeResult.supportingSamples() : 0,
         probeResult != null ? probeResult.totalSamples() : 0,
         probeResult != null ? formatRatio(probeResult.supportRatio()) : "0.00"
      );
   }

   private static String formatRatio(double ratio) {
      return String.format(Locale.ROOT, "%.2f", ratio);
   }

   private static void debug(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }

   private static void broadcastAcceptedPlacement(ResourceLocation structureId, WaterPlacementMode mode, int x, int y, int z) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null && SkyIslandServerConfig.structureDebugEnabled()) {
         String tpHint = "/tp @s " + x + " " + y + " " + z;
         server.execute(
            () -> server.getPlayerList()
               .broadcastSystemMessage(
                  Component.literal(
                     "[Sky Archipelago] WATER_V2 spawned: " + structureId + " mode=" + mode + " @ " + x + ", " + y + ", " + z + " (" + tpHint + ")"
                  ),
                  false
               )
         );
      }
   }
}
