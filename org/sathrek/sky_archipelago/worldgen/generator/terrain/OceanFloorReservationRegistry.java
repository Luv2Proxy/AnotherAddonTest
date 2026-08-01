package org.sathrek.sky_archipelago.worldgen.generator.terrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public final class OceanFloorReservationRegistry {
   private static final long TTL_NANOS = TimeUnit.MINUTES.toNanos(10L);
   private static final int MAX_ENTRIES = 20000;
   private static final Map<Long, List<OceanFloorReservationRegistry.Reservation>> BUCKETS = new ConcurrentHashMap<>();
   private static final ReentrantLock LOCK = new ReentrantLock();
   private static long nextReservationId = 1L;

   private OceanFloorReservationRegistry() {
   }

   public static boolean tryReserve(ResourceLocation structureId, StructureFootprint footprint, int targetFloorY, int smoothingMargin, long levelSeed) {
      LOCK.lock();

      try {
         evictExpiredLocked(System.nanoTime());
         StructureFootprint expanded = expand(footprint, smoothingMargin);

         for (OceanFloorReservationRegistry.Reservation reservation : candidatesForFootprintLocked(expanded)) {
            if (reservation.levelSeed() == levelSeed && intersects2d(expanded, reservation.expandedFootprint())) {
               return false;
            }
         }

         OceanFloorReservationRegistry.Reservation reservation = new OceanFloorReservationRegistry.Reservation(
            nextReservationId++, structureId, footprint, expanded, targetFloorY, Math.max(0, smoothingMargin), levelSeed, System.nanoTime()
         );

         for (long bucket : bucketKeys(expanded)) {
            BUCKETS.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(reservation);
         }

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
         OceanFloorReservationRegistry.Reservation best = null;
         int bestDistance = Integer.MAX_VALUE;
         long bucket = ChunkPos.asLong(SectionPos.blockToSectionCoord(worldX), SectionPos.blockToSectionCoord(worldZ));
         List<OceanFloorReservationRegistry.Reservation> reservations = BUCKETS.get(bucket);
         if (reservations != null && !reservations.isEmpty()) {
            for (OceanFloorReservationRegistry.Reservation reservation : reservations) {
               if (reservation.levelSeed() == levelSeed && contains(reservation.expandedFootprint(), worldX, worldZ)) {
                  int distance = distanceOutsideInner(reservation.innerFootprint(), worldX, worldZ);
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = reservation;
                  }
               }
            }

            if (best == null) {
               return naturalFloorY;
            } else if (!contains(best.innerFootprint(), worldX, worldZ) && best.smoothingMargin() > 0) {
               double t = Mth.clamp((double)bestDistance / best.smoothingMargin(), 0.0, 1.0);
               double smooth = t * t * (3.0 - 2.0 * t);
               return (int)Math.round(Mth.lerp(smooth, best.targetFloorY(), naturalFloorY));
            } else {
               return best.targetFloorY();
            }
         } else {
            return naturalFloorY;
         }
      } finally {
         LOCK.unlock();
      }
   }

   static void clearForTests() {
      LOCK.lock();

      try {
         BUCKETS.clear();
         nextReservationId = 1L;
      } finally {
         LOCK.unlock();
      }
   }

   private static List<OceanFloorReservationRegistry.Reservation> candidatesForFootprintLocked(StructureFootprint footprint) {
      HashSet<OceanFloorReservationRegistry.Reservation> dedup = new HashSet<>();

      for (long bucket : bucketKeys(footprint)) {
         List<OceanFloorReservationRegistry.Reservation> entries = BUCKETS.get(bucket);
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
      Iterator<Entry<Long, List<OceanFloorReservationRegistry.Reservation>>> it = BUCKETS.entrySet().iterator();

      while (it.hasNext()) {
         Entry<Long, List<OceanFloorReservationRegistry.Reservation>> entry = it.next();
         List<OceanFloorReservationRegistry.Reservation> list = entry.getValue();
         list.removeIf(value -> now - value.reservedAtNanos() > TTL_NANOS);
         if (list.isEmpty()) {
            it.remove();
         }
      }
   }

   private static void evictBySizeLocked() {
      int total = BUCKETS.values().stream().mapToInt(List::size).sum();
      if (total > 20000) {
         Iterator<Entry<Long, List<OceanFloorReservationRegistry.Reservation>>> it = BUCKETS.entrySet().iterator();

         while (it.hasNext() && total > 20000) {
            Entry<Long, List<OceanFloorReservationRegistry.Reservation>> entry = it.next();
            List<OceanFloorReservationRegistry.Reservation> list = entry.getValue();
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

   private record Reservation(
      long id,
      ResourceLocation structureId,
      StructureFootprint innerFootprint,
      StructureFootprint expandedFootprint,
      int targetFloorY,
      int smoothingMargin,
      long levelSeed,
      long reservedAtNanos
   ) {
   }
}
