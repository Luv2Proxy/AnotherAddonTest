package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.AcceptedStructurePlacement;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIslandKey;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class AcceptedStructurePlacementRegistry {
   private static final long TTL_NANOS = TimeUnit.MINUTES.toNanos(10L);
   private static final int MAX_ENTRIES = 20000;
   private static final Map<Long, AcceptedStructurePlacementRegistry.TrackedPlacement> ACTIVE_BY_ID = new ConcurrentHashMap<>();
   private static final ReentrantLock LOCK = new ReentrantLock();
   private static long nextPlacementId = 1L;

   private AcceptedStructurePlacementRegistry() {
   }

   public static void record(AcceptedStructurePlacement placement) {
      LOCK.lock();

      try {
         long now = System.nanoTime();
         evictExpiredLocked(now);
         ACTIVE_BY_ID.put(nextPlacementId++, new AcceptedStructurePlacementRegistry.TrackedPlacement(placement, now));
         evictBySizeLocked();
      } finally {
         LOCK.unlock();
      }
   }

   public static boolean hasSameStructureWithinSpacing(ResourceLocation structureId, ChunkPos originChunk, int spacingChunks) {
      LOCK.lock();

      try {
         evictExpiredLocked(System.nanoTime());

         for (AcceptedStructurePlacementRegistry.TrackedPlacement tracked : ACTIVE_BY_ID.values()) {
            AcceptedStructurePlacement placement = tracked.placement();
            if (placement.structureId().equals(structureId) && chunkDistance(placement.originChunk(), originChunk) <= spacingChunks) {
               return true;
            }
         }

         return false;
      } finally {
         LOCK.unlock();
      }
   }

   public static int countForHost(HostIslandKey hostIslandKey, StructurePlacementCategory category) {
      LOCK.lock();

      try {
         evictExpiredLocked(System.nanoTime());
         int count = 0;

         for (AcceptedStructurePlacementRegistry.TrackedPlacement tracked : ACTIVE_BY_ID.values()) {
            AcceptedStructurePlacement placement = tracked.placement();
            if (placement.hostIslandKey().equals(hostIslandKey) && placement.category() == category) {
               count++;
            }
         }

         return count;
      } finally {
         LOCK.unlock();
      }
   }

   public static int countSameForHost(ResourceLocation structureId, HostIslandKey hostIslandKey) {
      LOCK.lock();

      try {
         evictExpiredLocked(System.nanoTime());
         int count = 0;

         for (AcceptedStructurePlacementRegistry.TrackedPlacement tracked : ACTIVE_BY_ID.values()) {
            AcceptedStructurePlacement placement = tracked.placement();
            if (placement.structureId().equals(structureId) && placement.hostIslandKey().equals(hostIslandKey)) {
               count++;
            }
         }

         return count;
      } finally {
         LOCK.unlock();
      }
   }

   public static void clearForTests() {
      LOCK.lock();

      try {
         ACTIVE_BY_ID.clear();
         nextPlacementId = 1L;
      } finally {
         LOCK.unlock();
      }
   }

   private static int chunkDistance(ChunkPos a, ChunkPos b) {
      return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
   }

   private static void evictExpiredLocked(long now) {
      ACTIVE_BY_ID.entrySet().removeIf(entry -> now - entry.getValue().recordedAtNanos() > TTL_NANOS);
   }

   private static void evictBySizeLocked() {
      if (ACTIVE_BY_ID.size() > 20000) {
         int overshoot = ACTIVE_BY_ID.size() - 20000;
         List<Long> ids = new ArrayList<>(ACTIVE_BY_ID.keySet());
         ids.sort(Long::compareTo);

         for (int i = 0; i < overshoot && i < ids.size(); i++) {
            ACTIVE_BY_ID.remove(ids.get(i));
         }
      }
   }

   private record TrackedPlacement(AcceptedStructurePlacement placement, long recordedAtNanos) {
   }
}
