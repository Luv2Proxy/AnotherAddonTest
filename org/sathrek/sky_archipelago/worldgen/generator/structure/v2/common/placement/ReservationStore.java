package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class ReservationStore implements ReservationReader, ReservationWriter {
   private static final long TTL_NANOS = TimeUnit.MINUTES.toNanos(10L);
   private static final int MAX_ENTRIES = 20000;
   private final Map<Long, List<ReservedPlacement>> buckets = new ConcurrentHashMap<>();
   private final Map<Long, ReservedPlacement> activeById = new ConcurrentHashMap<>();
   private final ReentrantLock lock = new ReentrantLock();
   private final SpatialIndex spatialIndex = new SpatialIndex();
   private long nextReservationId = 1L;

   @Override
   public List<ReservedPlacement> candidatesForBounds(BoundingBox bounds) {
      this.lock.lock();

      try {
         this.evictExpiredLocked(System.nanoTime());
         HashSet<ReservedPlacement> dedup = new HashSet<>();

         for (long bucket : this.spatialIndex.bucketKeys(bounds)) {
            List<ReservedPlacement> entries = this.buckets.get(bucket);
            if (entries != null) {
               dedup.addAll(entries);
            }
         }

         return new ArrayList<>(dedup);
      } finally {
         this.lock.unlock();
      }
   }

   @Override
   public boolean reserve(ReservedPlacement reservedPlacement) {
      this.lock.lock();

      try {
         long now = System.nanoTime();
         this.evictExpiredLocked(now);
         ReservedPlacement toStore = reservedPlacement.withReservedAtNanos(now);
         long reservationId = this.nextReservationId++;
         this.activeById.put(reservationId, toStore);

         for (long bucket : this.spatialIndex.bucketKeys(toStore.occupiedBounds())) {
            this.buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(toStore);
         }

         this.evictBySizeLocked();
         return true;
      } finally {
         this.lock.unlock();
      }
   }

   public ReservedPlacement reserveIfNoConflict(ReservedPlacement candidate, OverlapPolicy overlapPolicy) {
      this.lock.lock();

      try {
         long now = System.nanoTime();
         this.evictExpiredLocked(now);
         HashSet<ReservedPlacement> dedup = new HashSet<>();

         for (long bucket : this.spatialIndex.bucketKeys(candidate.occupiedBounds())) {
            List<ReservedPlacement> entries = this.buckets.get(bucket);
            if (entries != null) {
               dedup.addAll(entries);
            }
         }

         for (ReservedPlacement existing : dedup) {
            if (existing.seedTag() == candidate.seedTag()
               && overlapPolicy.conflicts(candidate.occupiedBounds(), candidate.category(), existing.occupiedBounds(), existing.category())) {
               return existing;
            }
         }

         ReservedPlacement toStore = candidate.withReservedAtNanos(now);
         long reservationId = this.nextReservationId++;
         this.activeById.put(reservationId, toStore);

         for (long bucket : this.spatialIndex.bucketKeys(toStore.occupiedBounds())) {
            this.buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(toStore);
         }

         this.evictBySizeLocked();
         return null;
      } finally {
         this.lock.unlock();
      }
   }

   public ReservationStore.TieredReservationResult reserveAuthoritativeIfNoConflict(
      ReservedPlacement candidate, BoundingBox prefilterBounds, OverlapPolicy overlapPolicy
   ) {
      this.lock.lock();

      try {
         long now = System.nanoTime();
         this.evictExpiredLocked(now);
         BoundingBox lookupBounds = prefilterBounds != null ? prefilterBounds : candidate.occupiedBounds();
         HashSet<ReservedPlacement> dedup = new HashSet<>();

         for (long bucket : this.spatialIndex.bucketKeys(lookupBounds)) {
            List<ReservedPlacement> entries = this.buckets.get(bucket);
            if (entries != null) {
               dedup.addAll(entries);
            }
         }

         boolean fallbackToAuthoritative = prefilterBounds == null;
         if (!fallbackToAuthoritative && dedup.isEmpty()) {
            for (long bucket : this.spatialIndex.bucketKeys(candidate.occupiedBounds())) {
               List<ReservedPlacement> entries = this.buckets.get(bucket);
               if (entries != null) {
                  dedup.addAll(entries);
               }
            }

            fallbackToAuthoritative = true;
         }

         int authoritativeChecks = 0;

         for (ReservedPlacement existing : dedup) {
            if (existing.seedTag() == candidate.seedTag()) {
               authoritativeChecks++;
               if (overlapPolicy.conflicts(candidate.occupiedBounds(), candidate.category(), existing.occupiedBounds(), existing.category())) {
                  return new ReservationStore.TieredReservationResult(existing, dedup.size(), authoritativeChecks, fallbackToAuthoritative);
               }
            }
         }

         ReservedPlacement toStore = candidate.withReservedAtNanos(now);
         long reservationId = this.nextReservationId++;
         this.activeById.put(reservationId, toStore);

         for (long bucket : this.spatialIndex.bucketKeys(toStore.occupiedBounds())) {
            this.buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(toStore);
         }

         this.evictBySizeLocked();
         return new ReservationStore.TieredReservationResult(null, dedup.size(), authoritativeChecks, fallbackToAuthoritative);
      } finally {
         this.lock.unlock();
      }
   }

   public void clearForTests() {
      this.lock.lock();

      try {
         this.buckets.clear();
         this.activeById.clear();
         this.nextReservationId = 1L;
      } finally {
         this.lock.unlock();
      }
   }

   private void evictExpiredLocked(long now) {
      HashSet<ReservedPlacement> expired = new HashSet<>();
      Iterator<Entry<Long, List<ReservedPlacement>>> it = this.buckets.entrySet().iterator();

      while (it.hasNext()) {
         Entry<Long, List<ReservedPlacement>> entry = it.next();
         List<ReservedPlacement> list = entry.getValue();
         list.removeIf(value -> {
            boolean old = now - value.reservedAtNanos() > TTL_NANOS;
            if (old) {
               expired.add(value);
            }

            return old;
         });
         if (list.isEmpty()) {
            it.remove();
         }
      }

      if (!expired.isEmpty()) {
         this.activeById.entrySet().removeIf(entryx -> expired.contains(entryx.getValue()));
      }
   }

   private void evictBySizeLocked() {
      if (this.activeById.size() > 20000) {
         int overshoot = this.activeById.size() - 20000;
         if (overshoot > 0) {
            List<Long> ids = new ArrayList<>(this.activeById.keySet());
            ids.sort(Long::compareTo);
            HashSet<ReservedPlacement> evicted = new HashSet<>();

            for (int i = 0; i < overshoot && i < ids.size(); i++) {
               Long id = ids.get(i);
               ReservedPlacement removed = this.activeById.remove(id);
               if (removed != null) {
                  evicted.add(removed);
               }
            }

            if (!evicted.isEmpty()) {
               Iterator<Entry<Long, List<ReservedPlacement>>> it = this.buckets.entrySet().iterator();

               while (it.hasNext()) {
                  Entry<Long, List<ReservedPlacement>> entry = it.next();
                  List<ReservedPlacement> list = entry.getValue();
                  list.removeIf(evicted::contains);
                  if (list.isEmpty()) {
                     it.remove();
                  }
               }
            }
         }
      }
   }

   public record TieredReservationResult(ReservedPlacement conflict, int prefilterCandidates, int authoritativeChecks, boolean fallbackToAuthoritative) {
   }
}
