package org.sathrek.sky_archipelago.worldgen.generator.structure;

import java.util.List;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.OceanMonumentWaterAdapter;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.WaterVolumeReservation;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.WaterVolumeReservationRegistry;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public final class WaterVolumePostPlacementNormalizer {
   private WaterVolumePostPlacementNormalizer() {
   }

   public static void normalizeAfterPlacement(WorldGenLevel level, ResourceLocation structureId, ChunkPos chunkPos) {
      List<WaterVolumeReservation> reservations = WaterVolumeReservationRegistry.reservationsForChunk(chunkPos, structureId, level.getSeed());
      if (reservations.isEmpty()) {
         if (OceanMonumentWaterAdapter.STRUCTURE_ID.equals(structureId)) {
            debug(
               "OCEAN_MONUMENT_DEBUG stage=normalizer_no_reservation chunk=[{}, {}] structureId={} levelSeed={} hint=vanilla_monument_water_box_will_not_be_cleaned",
               chunkPos.x,
               chunkPos.z,
               structureId,
               level.getSeed()
            );
         }
      } else {
         for (WaterVolumeReservation reservation : reservations) {
            if (OceanMonumentWaterAdapter.ADAPTER_ID.equals(reservation.adapterId())) {
               WaterVolumePostPlacementNormalizer.CleanupCounts counts = normalizeOceanMonument(level, reservation, chunkPos);
               debug(
                  "OCEAN_MONUMENT_DEBUG stage=normalizer_cleanup structureId={} adapterId={} chunk=[{}, {}] levelSeed={} reservationSeed={} footprint={} cleanupFootprint={} bodyFloorY={} topOnlyCutoffY={} waterTopY={} structureTopY={} cleanupY=[{}, {}] scannedY={} waterCleared={} blocksTrimmedBelowCutoff={} blocksTrimmedAboveTop={}",
                  reservation.structureId(),
                  reservation.adapterId(),
                  chunkPos.x,
                  chunkPos.z,
                  level.getSeed(),
                  reservation.levelSeed(),
                  formatFootprint(reservation.footprint()),
                  formatFootprint(reservation.cleanupFootprint()),
                  reservation.bodyFloorY(),
                  reservation.topOnlyCutoffY(),
                  reservation.waterTopY(),
                  reservation.structureTopY(),
                  reservation.cleanupBottomY(),
                  reservation.cleanupTopY(),
                  reservation.cleanupTopY() - reservation.cleanupBottomY() + 1,
                  counts.waterCleared(),
                  counts.trimmedBelowCutoff(),
                  counts.trimmedAboveTop()
               );
            }
         }
      }
   }

   private static WaterVolumePostPlacementNormalizer.CleanupCounts normalizeOceanMonument(
      WorldGenLevel level, WaterVolumeReservation reservation, ChunkPos chunkPos
   ) {
      StructureFootprint cleanup = reservation.cleanupFootprint();
      int minX = Math.max(chunkPos.getMinBlockX(), cleanup.minX());
      int maxX = Math.min(chunkPos.getMaxBlockX(), cleanup.maxX());
      int minZ = Math.max(chunkPos.getMinBlockZ(), cleanup.minZ());
      int maxZ = Math.min(chunkPos.getMaxBlockZ(), cleanup.maxZ());
      int minY = Math.max(level.getMinBuildHeight(), reservation.cleanupBottomY());
      int maxY = Math.min(level.getMaxBuildHeight() - 1, reservation.cleanupTopY());
      int waterCleared = 0;
      int trimmedBelowCutoff = 0;
      int trimmedAboveTop = 0;
      MutableBlockPos pos = new MutableBlockPos();

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
               pos.set(x, y, z);
               BlockState state = level.getBlockState(pos);
               WaterVolumePostPlacementNormalizer.CleanupAction action = cleanupAction(reservation, y, state);
               if (action != WaterVolumePostPlacementNormalizer.CleanupAction.KEEP) {
                  level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                  waterCleared++;
               }
            }
         }
      }

      return new WaterVolumePostPlacementNormalizer.CleanupCounts(waterCleared, trimmedBelowCutoff, trimmedAboveTop);
   }

   static WaterVolumePostPlacementNormalizer.CleanupAction cleanupAction(WaterVolumeReservation reservation, int y, BlockState state) {
      return cleanupAction(y, reservation.waterTopY(), reservation.topOnlyCutoffY(), reservation.structureTopY(), isWaterLike(state));
   }

   static WaterVolumePostPlacementNormalizer.CleanupAction cleanupAction(int y, int waterTopY, int topOnlyCutoffY, int structureTopY, boolean waterLike) {
      return y > waterTopY && waterLike ? WaterVolumePostPlacementNormalizer.CleanupAction.CLEAR_WATER : WaterVolumePostPlacementNormalizer.CleanupAction.KEEP;
   }

   static boolean isWaterLike(BlockState state) {
      return state.is(Blocks.WATER) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
   }

   private static void debug(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }

   private static String formatFootprint(StructureFootprint footprint) {
      return "[" + footprint.minX() + "," + footprint.minZ() + " -> " + footprint.maxX() + "," + footprint.maxZ() + "]";
   }

   enum CleanupAction {
      KEEP,
      CLEAR_WATER,
      TRIM_BELOW_CUTOFF,
      TRIM_ABOVE_STRUCTURE_TOP;
   }

   private record CleanupCounts(int waterCleared, int trimmedBelowCutoff, int trimmedAboveTop) {
   }
}
