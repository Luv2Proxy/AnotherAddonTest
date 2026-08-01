package org.sathrek.sky_archipelago.worldgen.generator.terrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.OceanMonumentWaterAdapter;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public final class WaterVolumeReservationRegistry {
   private static final long TTL_NANOS = TimeUnit.MINUTES.toNanos(10L);
   private static final int MAX_ENTRIES = 20000;
   private static final Map<Long, List<WaterVolumeReservation>> BUCKETS = new ConcurrentHashMap<>();
   private static final ReentrantLock LOCK = new ReentrantLock();
   private static final AtomicInteger SEED_MISMATCH_LOG_COUNT = new AtomicInteger();
   private static final int SEED_MISMATCH_LOG_LIMIT = 40;
   private static long nextReservationId = 1L;

   private WaterVolumeReservationRegistry() {
   }

   public static boolean tryReserve(
      ResourceLocation structureId,
      ResourceLocation adapterId,
      StructureFootprint footprint,
      int bodyFloorY,
      int topOnlyCutoffY,
      int waterTopY,
      int structureTopY,
      int cleanupBottomY,
      int cleanupTopY,
      int cleanupFootprintMargin,
      int smoothingMargin,
      long levelSeed
   ) {
      LOCK.lock();

      try {
         evictExpiredLocked(System.nanoTime());
         StructureFootprint cleanupFootprint = expand(footprint, cleanupFootprintMargin);
         StructureFootprint smoothingFootprint = expand(footprint, smoothingMargin);

         for (WaterVolumeReservation reservation : candidatesForFootprintLocked(cleanupFootprint)) {
            if (reservation.levelSeed() == levelSeed && intersects2d(cleanupFootprint, reservation.cleanupFootprint())) {
               debugMonument(
                  structureId,
                  "OCEAN_MONUMENT_DEBUG stage=reservation_overlap structureId={} adapterId={} levelSeed={} newFootprint={} newCleanupFootprint={} existingReservationId={} existingSeed={} existingCleanupFootprint={}",
                  structureId,
                  adapterId,
                  levelSeed,
                  formatFootprint(footprint),
                  formatFootprint(cleanupFootprint),
                  reservation.id(),
                  reservation.levelSeed(),
                  formatFootprint(reservation.cleanupFootprint())
               );
               return false;
            }
         }

         WaterVolumeReservation reservation = new WaterVolumeReservation(
            nextReservationId++,
            structureId,
            adapterId,
            footprint,
            cleanupFootprint,
            smoothingFootprint,
            bodyFloorY,
            topOnlyCutoffY,
            waterTopY,
            structureTopY,
            cleanupBottomY,
            cleanupTopY,
            Math.max(0, smoothingMargin),
            levelSeed,
            System.nanoTime()
         );

         for (long bucket : bucketKeys(smoothingFootprint)) {
            BUCKETS.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(reservation);
         }

         debugMonument(
            structureId,
            "OCEAN_MONUMENT_DEBUG stage=reservation_stored reservationId={} structureId={} adapterId={} levelSeed={} footprint={} cleanupFootprint={} smoothingFootprint={} bodyFloorY={} topOnlyCutoffY={} waterTopY={} structureTopY={} cleanupY=[{}, {}] cleanupMargin={} smoothingMargin={} bucketCount={}",
            reservation.id(),
            structureId,
            adapterId,
            levelSeed,
            formatFootprint(footprint),
            formatFootprint(cleanupFootprint),
            formatFootprint(smoothingFootprint),
            bodyFloorY,
            topOnlyCutoffY,
            waterTopY,
            structureTopY,
            cleanupBottomY,
            cleanupTopY,
            cleanupFootprintMargin,
            smoothingMargin,
            bucketKeys(smoothingFootprint).size()
         );
         evictBySizeLocked();
         return true;
      } finally {
         LOCK.unlock();
      }
   }

   public static int adjustedOceanFloorTopY(int worldX, int worldZ, int naturalFloorY, long levelSeed) {
      LOCK.lock();

      try {
         evictExpiredLocked(System.nanoTime());
         WaterVolumeReservation best = null;
         int bestDistance = Integer.MAX_VALUE;
         long bucket = ChunkPos.asLong(SectionPos.blockToSectionCoord(worldX), SectionPos.blockToSectionCoord(worldZ));
         List<WaterVolumeReservation> reservations = BUCKETS.get(bucket);
         if (reservations != null && !reservations.isEmpty()) {
            WaterVolumeReservation seedMismatchCandidate = null;

            for (WaterVolumeReservation reservation : reservations) {
               if (reservation.levelSeed() != levelSeed
                  && OceanMonumentWaterAdapter.ADAPTER_ID.equals(reservation.adapterId())
                  && contains(reservation.smoothingFootprint(), worldX, worldZ)) {
                  seedMismatchCandidate = reservation;
               }

               if (reservation.levelSeed() == levelSeed && contains(reservation.smoothingFootprint(), worldX, worldZ)) {
                  int distance = distanceOutsideInner(reservation.footprint(), worldX, worldZ);
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = reservation;
                  }
               }
            }

            if (best == null && seedMismatchCandidate != null) {
               logSeedMismatch(
                  "OCEAN_MONUMENT_DEBUG stage=floor_adjust_seed_mismatch world=({}, {}) requestedSeed={} reservationId={} reservationSeed={} naturalFloorY={} bodyFloorY={} footprint={} smoothingFootprint={}",
                  worldX,
                  worldZ,
                  levelSeed,
                  seedMismatchCandidate.id(),
                  seedMismatchCandidate.levelSeed(),
                  naturalFloorY,
                  seedMismatchCandidate.bodyFloorY(),
                  formatFootprint(seedMismatchCandidate.footprint()),
                  formatFootprint(seedMismatchCandidate.smoothingFootprint())
               );
               best = seedMismatchCandidate;
               bestDistance = distanceOutsideInner(best.footprint(), worldX, worldZ);
            }

            if (best == null) {
               return naturalFloorY;
            } else if (!contains(best.footprint(), worldX, worldZ) && best.smoothingMargin() > 0) {
               double t = Mth.clamp((double)bestDistance / best.smoothingMargin(), 0.0, 1.0);
               double smooth = t * t * (3.0 - 2.0 * t);
               return (int)Math.round(Mth.lerp(smooth, best.bodyFloorY(), naturalFloorY));
            } else {
               return best.bodyFloorY();
            }
         } else {
            return naturalFloorY;
         }
      } finally {
         LOCK.unlock();
      }
   }

   public static List<WaterVolumeReservation> reservationsForChunk(ChunkPos chunkPos, ResourceLocation structureId, long levelSeed) {
      LOCK.lock();

      try {
         evictExpiredLocked(System.nanoTime());
         long bucket = chunkPos.toLong();
         List<WaterVolumeReservation> reservations = BUCKETS.get(bucket);
         if (reservations != null && !reservations.isEmpty()) {
            ArrayList<WaterVolumeReservation> matches = new ArrayList<>();
            int seedMismatchMatches = 0;
            StructureFootprint chunkFootprint = new StructureFootprint(
               chunkPos.getMinBlockX(), chunkPos.getMaxBlockX(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockZ()
            );

            for (WaterVolumeReservation reservation : reservations) {
               if (reservation.structureId().equals(structureId)
                  && reservation.levelSeed() != levelSeed
                  && intersects2d(chunkFootprint, reservation.cleanupFootprint())) {
                  seedMismatchMatches++;
               }

               if (reservation.levelSeed() == levelSeed
                  && reservation.structureId().equals(structureId)
                  && intersects2d(chunkFootprint, reservation.cleanupFootprint())) {
                  matches.add(reservation);
               }
            }

            if (matches.isEmpty() && seedMismatchMatches > 0) {
               debugMonument(
                  structureId,
                  "OCEAN_MONUMENT_DEBUG stage=reservation_lookup_seed_mismatch chunk=[{}, {}] structureId={} requestedSeed={} mismatchedReservations={} bucketReservations={} chunkFootprint={}",
                  chunkPos.x,
                  chunkPos.z,
                  structureId,
                  levelSeed,
                  seedMismatchMatches,
                  reservations.size(),
                  formatFootprint(chunkFootprint)
               );
            } else {
               debugMonument(
                  structureId,
                  "OCEAN_MONUMENT_DEBUG stage=reservation_lookup chunk=[{}, {}] structureId={} levelSeed={} matches={} bucketReservations={} chunkFootprint={}",
                  chunkPos.x,
                  chunkPos.z,
                  structureId,
                  levelSeed,
                  matches.size(),
                  reservations.size(),
                  formatFootprint(chunkFootprint)
               );
            }

            return List.copyOf(matches);
         } else {
            debugMonument(
               structureId,
               "OCEAN_MONUMENT_DEBUG stage=reservation_lookup_empty_bucket chunk=[{}, {}] structureId={} levelSeed={}",
               chunkPos.x,
               chunkPos.z,
               structureId,
               levelSeed
            );
            return List.of();
         }
      } finally {
         LOCK.unlock();
      }
   }

   public static void clearForTests() {
      LOCK.lock();

      try {
         BUCKETS.clear();
         nextReservationId = 1L;
      } finally {
         LOCK.unlock();
      }
   }

   private static List<WaterVolumeReservation> candidatesForFootprintLocked(StructureFootprint footprint) {
      HashSet<WaterVolumeReservation> dedup = new HashSet<>();

      for (long bucket : bucketKeys(footprint)) {
         List<WaterVolumeReservation> entries = BUCKETS.get(bucket);
         if (entries != null) {
            dedup.addAll(entries);
         }
      }

      return new ArrayList<>(dedup);
   }

   private static List<Long> bucketKeys(StructureFootprint footprint) {
      int minChunkX = SectionPos.blockToSectionCoord(footprint.minX());
      int maxChunkX = SectionPos.blockToSectionCoord(footprint.maxX());
      int minChunkZ = SectionPos.blockToSectionCoord(footprint.minZ());
      int maxChunkZ = SectionPos.blockToSectionCoord(footprint.maxZ());
      ArrayList<Long> keys = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));

      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
         for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            keys.add(ChunkPos.asLong(chunkX, chunkZ));
         }
      }

      return keys;
   }

   private static void evictExpiredLocked(long now) {
      Iterator<Entry<Long, List<WaterVolumeReservation>>> it = BUCKETS.entrySet().iterator();

      while (it.hasNext()) {
         Entry<Long, List<WaterVolumeReservation>> entry = it.next();
         List<WaterVolumeReservation> list = entry.getValue();
         list.removeIf(value -> now - value.reservedAtNanos() > TTL_NANOS);
         if (list.isEmpty()) {
            it.remove();
         }
      }
   }

   private static void evictBySizeLocked() {
      int total = BUCKETS.values().stream().mapToInt(List::size).sum();
      if (total > 20000) {
         Iterator<Entry<Long, List<WaterVolumeReservation>>> it = BUCKETS.entrySet().iterator();

         while (it.hasNext() && total > 20000) {
            Entry<Long, List<WaterVolumeReservation>> entry = it.next();
            List<WaterVolumeReservation> list = entry.getValue();
            if (!list.isEmpty()) {
               list.remove(0);
               total--;
            }

            if (list.isEmpty()) {
               it.remove();
            }
         }
      }
   }

   private static boolean intersects2d(StructureFootprint a, StructureFootprint b) {
      return a.minX() <= b.maxX() && a.maxX() >= b.minX() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
   }

   private static boolean contains(StructureFootprint footprint, int x, int z) {
      return x >= footprint.minX() && x <= footprint.maxX() && z >= footprint.minZ() && z <= footprint.maxZ();
   }

   private static int distanceOutsideInner(StructureFootprint footprint, int x, int z) {
      int dx = Math.max(Math.max(footprint.minX() - x, 0), x - footprint.maxX());
      int dz = Math.max(Math.max(footprint.minZ() - z, 0), z - footprint.maxZ());
      return Math.max(dx, dz);
   }

   private static StructureFootprint expand(StructureFootprint footprint, int blocks) {
      int margin = Math.max(0, blocks);
      return new StructureFootprint(footprint.minX() - margin, footprint.maxX() + margin, footprint.minZ() - margin, footprint.maxZ() + margin);
   }

   private static void debugMonument(ResourceLocation structureId, String message, Object... args) {
      if (OceanMonumentWaterAdapter.STRUCTURE_ID.equals(structureId) && SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }

   private static void logSeedMismatch(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled() && SEED_MISMATCH_LOG_COUNT.getAndIncrement() < 40) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }

   private static String formatFootprint(StructureFootprint footprint) {
      return "[" + footprint.minX() + "," + footprint.minZ() + " -> " + footprint.maxX() + "," + footprint.maxZ() + "]";
   }
}
