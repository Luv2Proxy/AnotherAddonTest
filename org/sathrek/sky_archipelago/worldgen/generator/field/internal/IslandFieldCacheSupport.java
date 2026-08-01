package org.sathrek.sky_archipelago.worldgen.generator.field.internal;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

public final class IslandFieldCacheSupport {
   private IslandFieldCacheSupport() {
   }

   public record AdaptivePolicy(
      BooleanSupplier enabled,
      IntSupplier minTtlSeconds,
      IntSupplier maxTtlSeconds,
      IntSupplier retuneIntervalSeconds,
      IntSupplier adjustmentPercent,
      DoubleSupplier highPressureMissRateThreshold,
      DoubleSupplier targetHitRate
   ) {
   }

   private static final class CacheEntry<V> {
      private final V value;
      private volatile long lastAccessNanos;

      private CacheEntry(V value, long lastAccessNanos) {
         this.value = value;
         this.lastAccessNanos = lastAccessNanos;
      }

      private V value() {
         return this.value;
      }

      private long lastAccessNanos() {
         return this.lastAccessNanos;
      }

      private void touch(long now) {
         this.lastAccessNanos = now;
      }
   }

   public static final class TimedCache<K, V> {
      private static final int SWEEP_INTERVAL_MASK = 4095;
      private static final int MAX_SCAN_MULTIPLIER = 4;
      private final ConcurrentHashMap<K, IslandFieldCacheSupport.CacheEntry<V>> entries = new ConcurrentHashMap<>();
      private final String name;
      private final long ttlNanos;
      private final int sweepBudget;
      private final int maxEntries;
      private final int trimTargetEntries;
      private final int trimBatch;
      private final AtomicInteger operations = new AtomicInteger();
      private final AtomicLong lastEvictionLogNanos = new AtomicLong(0L);
      private final AtomicLong lastMetricsLogNanos = new AtomicLong(0L);
      private final AtomicLong lastRetuneNanos = new AtomicLong(0L);
      private final AtomicLong totalEvicted = new AtomicLong(0L);
      private final BooleanSupplier cacheDebugLogsEnabled;
      private final IslandFieldCacheSupport.AdaptivePolicy adaptivePolicy;
      private final long cacheLogIntervalNanos;
      private final Consumer<String> logSink;
      private volatile long effectiveTtlNanos;
      private volatile String lastRetuneReason = "steady";
      private volatile String pressureLevel = "low";
      private volatile long previousHitSnapshot = 0L;
      private volatile long previousMissSnapshot = 0L;
      private volatile long previousExpiredSnapshot = 0L;
      private final LongAdder hitCount = new LongAdder();
      private final LongAdder missCount = new LongAdder();
      private final LongAdder expiredOnAccessCount = new LongAdder();
      private final LongAdder putCount = new LongAdder();
      private final LongAdder computeLoadCount = new LongAdder();
      private final LongAdder evictionAgeNanosSum = new LongAdder();
      private final LongAdder evictionAgeSamples = new LongAdder();
      private final LongAdder evictUnder10s = new LongAdder();
      private final LongAdder evict10To30s = new LongAdder();
      private final LongAdder evict30To60s = new LongAdder();
      private final LongAdder evict60To120s = new LongAdder();
      private final LongAdder evict120To300s = new LongAdder();
      private final LongAdder evictOver300s = new LongAdder();

      public TimedCache(
         String name, long ttlNanos, int sweepBudget, BooleanSupplier cacheDebugLogsEnabled, long cacheLogIntervalNanos, Consumer<String> logSink
      ) {
         this(name, ttlNanos, sweepBudget, -1, -1, sweepBudget, cacheDebugLogsEnabled, cacheLogIntervalNanos, logSink, null);
      }

      public TimedCache(
         String name,
         long ttlNanos,
         int sweepBudget,
         int maxEntries,
         int trimTargetEntries,
         int trimBatch,
         BooleanSupplier cacheDebugLogsEnabled,
         long cacheLogIntervalNanos,
         Consumer<String> logSink
      ) {
         this(name, ttlNanos, sweepBudget, maxEntries, trimTargetEntries, trimBatch, cacheDebugLogsEnabled, cacheLogIntervalNanos, logSink, null);
      }

      public TimedCache(
         String name,
         long ttlNanos,
         int sweepBudget,
         int maxEntries,
         int trimTargetEntries,
         int trimBatch,
         BooleanSupplier cacheDebugLogsEnabled,
         long cacheLogIntervalNanos,
         Consumer<String> logSink,
         IslandFieldCacheSupport.AdaptivePolicy adaptivePolicy
      ) {
         this.name = name;
         this.ttlNanos = ttlNanos;
         this.sweepBudget = sweepBudget;
         this.maxEntries = maxEntries;
         this.trimTargetEntries = trimTargetEntries > 0 ? trimTargetEntries : maxEntries;
         this.trimBatch = Math.max(trimBatch, sweepBudget);
         this.cacheDebugLogsEnabled = cacheDebugLogsEnabled;
         this.adaptivePolicy = adaptivePolicy;
         this.cacheLogIntervalNanos = cacheLogIntervalNanos;
         this.logSink = logSink;
         this.effectiveTtlNanos = ttlNanos;
      }

      public V get(K key) {
         long now = System.nanoTime();
         this.maybeSweep(now);
         IslandFieldCacheSupport.CacheEntry<V> entry = this.entries.get(key);
         if (entry == null) {
            this.missCount.increment();
            return null;
         } else if (this.isExpired(entry, now)) {
            this.entries.remove(key, entry);
            this.missCount.increment();
            this.expiredOnAccessCount.increment();
            return null;
         } else {
            entry.touch(now);
            this.hitCount.increment();
            return entry.value();
         }
      }

      public boolean contains(K key) {
         return this.get(key) != null;
      }

      public void put(K key, V value) {
         long now = System.nanoTime();
         this.maybeSweep(now);
         this.entries.put(key, new IslandFieldCacheSupport.CacheEntry<>(value, now));
         this.putCount.increment();
         this.maybeTrimToMax(now);
      }

      public V computeIfAbsent(K key, Function<K, V> loader) {
         long now = System.nanoTime();
         this.maybeSweep(now);
         IslandFieldCacheSupport.CacheEntry<V> existing = this.entries.get(key);
         if (existing != null && !this.isExpired(existing, now)) {
            existing.touch(now);
            this.hitCount.increment();
            return existing.value();
         }

         this.missCount.increment();
         if (existing != null) {
            this.expiredOnAccessCount.increment();
         }

         IslandFieldCacheSupport.CacheEntry<V> resolved = this.entries.compute(key, (ignored, current) -> {
            long innerNow = System.nanoTime();
            if (current != null && !this.isExpired((IslandFieldCacheSupport.CacheEntry<V>)current, innerNow)) {
               current.touch(innerNow);
               return current;
            } else {
               this.computeLoadCount.increment();
               return new IslandFieldCacheSupport.CacheEntry<>(loader.apply(key), innerNow);
            }
         });
         this.maybeTrimToMax(System.nanoTime());
         return resolved.value();
      }

      public int size() {
         this.maybeSweep(System.nanoTime());
         return this.entries.size();
      }

      private void maybeSweep(long now) {
         this.maybeRetune(now);
         if ((this.operations.incrementAndGet() & 4095) == 0) {
            this.sweepExpired(now);
            this.maybeTrimToMax(now);
            this.maybeLogMetrics(now);
         }
      }

      private void sweepExpired(long now) {
         int removed = 0;
         int scanned = 0;
         int maxScanned = Math.max(this.sweepBudget * 4, this.sweepBudget);

         for (Entry<K, IslandFieldCacheSupport.CacheEntry<V>> mapEntry : this.entries.entrySet()) {
            scanned++;
            IslandFieldCacheSupport.CacheEntry<V> entry = mapEntry.getValue();
            if (this.isExpired(entry, now) && this.entries.remove(mapEntry.getKey(), entry)) {
               removed++;
               this.recordEvictionAge(now - entry.lastAccessNanos());
               if (removed >= this.sweepBudget) {
                  return;
               }
            }

            if (scanned >= maxScanned) {
               break;
            }
         }

         if (removed > 0) {
            this.totalEvicted.addAndGet(removed);
            this.maybeLogEviction(now, removed, scanned);
         }
      }

      private boolean isExpired(IslandFieldCacheSupport.CacheEntry<V> entry, long now) {
         return now - entry.lastAccessNanos() > this.effectiveTtlNanos;
      }

      private void maybeTrimToMax(long now) {
         if (this.maxEntries > 0) {
            int size = this.entries.size();
            if (size > this.maxEntries) {
               int desiredRemovals = Math.max(1, size - this.trimTargetEntries);
               int targetRemovals = Math.min(this.trimBatch, desiredRemovals);
               if (targetRemovals > 0) {
                  int sampleLimit = Math.max(targetRemovals * 8, targetRemovals);
                  Object[] candidateKeys = new Object[targetRemovals];
                  long[] candidateAges = new long[targetRemovals];
                  int candidateCount = 0;
                  long smallestCandidateAge = Long.MAX_VALUE;
                  int smallestCandidateIndex = -1;
                  int sampled = 0;

                  for (Entry<K, IslandFieldCacheSupport.CacheEntry<V>> mapEntry : this.entries.entrySet()) {
                     sampled++;
                     IslandFieldCacheSupport.CacheEntry<V> entry = mapEntry.getValue();
                     long age = now - entry.lastAccessNanos();
                     if (candidateCount < targetRemovals) {
                        candidateKeys[candidateCount] = mapEntry.getKey();
                        candidateAges[candidateCount] = age;
                        if (age < smallestCandidateAge) {
                           smallestCandidateAge = age;
                           smallestCandidateIndex = candidateCount;
                        }

                        candidateCount++;
                     } else if (age > smallestCandidateAge) {
                        candidateKeys[smallestCandidateIndex] = mapEntry.getKey();
                        candidateAges[smallestCandidateIndex] = age;
                        smallestCandidateAge = candidateAges[0];
                        smallestCandidateIndex = 0;

                        for (int index = 1; index < candidateCount; index++) {
                           if (candidateAges[index] < smallestCandidateAge) {
                              smallestCandidateAge = candidateAges[index];
                              smallestCandidateIndex = index;
                           }
                        }
                     }

                     if (sampled >= sampleLimit) {
                        break;
                     }
                  }

                  int removed = 0;

                  for (int index = 0; index < candidateCount; index++) {
                     K key = (K)candidateKeys[index];
                     if (key != null) {
                        IslandFieldCacheSupport.CacheEntry<V> removedEntry = this.entries.remove(key);
                        if (removedEntry != null) {
                           this.recordEvictionAge(now - removedEntry.lastAccessNanos());
                           removed++;
                        }
                     }
                  }

                  if (removed > 0) {
                     this.totalEvicted.addAndGet(removed);
                     this.maybeLogTrim(now, removed);
                  }
               }
            }
         }
      }

      private void maybeLogEviction(long now, int removed, int scanned) {
         if (this.cacheDebugLogsEnabled.getAsBoolean()) {
            long lastLog = this.lastEvictionLogNanos.get();
            if (now - lastLog >= this.cacheLogIntervalNanos) {
               if (this.lastEvictionLogNanos.compareAndSet(lastLog, now)) {
                  this.logSink
                     .accept(
                        "IslandField cache eviction: cache="
                           + this.name
                           + ", removed="
                           + removed
                           + ", scanned="
                           + scanned
                           + ", size="
                           + this.entries.size()
                           + ", totalEvicted="
                           + this.totalEvicted.get()
                           + ", ttlSeconds="
                           + TimeUnit.NANOSECONDS.toSeconds(this.effectiveTtlNanos)
                           + ", sweepBudget="
                           + this.sweepBudget
                     );
               }
            }
         }
      }

      private void maybeLogTrim(long now, int removed) {
         if (this.cacheDebugLogsEnabled.getAsBoolean()) {
            long lastLog = this.lastEvictionLogNanos.get();
            if (now - lastLog >= this.cacheLogIntervalNanos) {
               if (this.lastEvictionLogNanos.compareAndSet(lastLog, now)) {
                  this.logSink
                     .accept(
                        "IslandField cache trim: cache="
                           + this.name
                           + ", removed="
                           + removed
                           + ", size="
                           + this.entries.size()
                           + ", totalEvicted="
                           + this.totalEvicted.get()
                           + ", maxEntries="
                           + this.maxEntries
                           + ", trimBatch="
                           + this.trimBatch
                     );
               }
            }
         }
      }

      private void maybeRetune(long now) {
         if (this.adaptivePolicy != null && this.adaptivePolicy.enabled().getAsBoolean()) {
            int minTtlSeconds = Math.max(1, this.adaptivePolicy.minTtlSeconds().getAsInt());
            int maxTtlSeconds = Math.max(minTtlSeconds, this.adaptivePolicy.maxTtlSeconds().getAsInt());
            long minTtlNanos = TimeUnit.SECONDS.toNanos(minTtlSeconds);
            long maxTtlNanos = TimeUnit.SECONDS.toNanos(maxTtlSeconds);
            this.effectiveTtlNanos = clamp(this.effectiveTtlNanos, minTtlNanos, maxTtlNanos);
            int intervalSeconds = Math.max(1, this.adaptivePolicy.retuneIntervalSeconds().getAsInt());
            long intervalNanos = TimeUnit.SECONDS.toNanos(intervalSeconds);
            long lastRetune = this.lastRetuneNanos.get();
            if (now - lastRetune >= intervalNanos) {
               if (this.lastRetuneNanos.compareAndSet(lastRetune, now)) {
                  long hits = this.hitCount.sum();
                  long misses = this.missCount.sum();
                  long expiredOnAccess = this.expiredOnAccessCount.sum();
                  long deltaHits = Math.max(0L, hits - this.previousHitSnapshot);
                  long deltaMisses = Math.max(0L, misses - this.previousMissSnapshot);
                  long deltaExpiredOnAccess = Math.max(0L, expiredOnAccess - this.previousExpiredSnapshot);
                  this.previousHitSnapshot = hits;
                  this.previousMissSnapshot = misses;
                  this.previousExpiredSnapshot = expiredOnAccess;
                  double elapsedSeconds = Math.max(0.001, (double)intervalNanos / TimeUnit.SECONDS.toNanos(1L));
                  double missRatePerSecond = deltaMisses / elapsedSeconds;
                  double windowHitRate = deltaHits + deltaMisses == 0L ? 1.0 : (double)deltaHits / (deltaHits + deltaMisses);
                  int size = this.entries.size();
                  boolean hasCapacityBound = this.maxEntries > 0;
                  double occupancy = hasCapacityBound ? (double)size / this.maxEntries : 0.0;
                  double highPressureMissRateThreshold = Math.max(1.0, this.adaptivePolicy.highPressureMissRateThreshold().getAsDouble());
                  double targetHitRate = clamp(this.adaptivePolicy.targetHitRate().getAsDouble(), 0.0, 1.0);
                  boolean highPressure = hasCapacityBound && size >= this.maxEntries || missRatePerSecond >= highPressureMissRateThreshold * 2.0;
                  boolean mediumPressure = !highPressure && (hasCapacityBound && occupancy >= 0.85 || missRatePerSecond >= highPressureMissRateThreshold);
                  this.pressureLevel = highPressure ? "high" : (mediumPressure ? "medium" : "low");
                  int adjustmentPercent = Math.max(1, this.adaptivePolicy.adjustmentPercent().getAsInt());
                  double adjustmentFactor = Math.max(0.01, adjustmentPercent / 100.0);
                  long nextTtl = this.effectiveTtlNanos;
                  if (!highPressure && !mediumPressure) {
                     if (deltaExpiredOnAccess <= 0L && (!(windowHitRate < targetHitRate) || !(missRatePerSecond > highPressureMissRateThreshold * 0.5))) {
                        this.lastRetuneReason = "steady";
                     } else {
                        nextTtl = Math.min(maxTtlNanos, Math.round(nextTtl * (1.0 + adjustmentFactor)));
                        this.lastRetuneReason = "miss_recovery_up_ttl";
                     }
                  } else {
                     nextTtl = Math.max(minTtlNanos, Math.round(nextTtl * (1.0 - adjustmentFactor)));
                     this.lastRetuneReason = "pressure_down_ttl";
                  }

                  this.effectiveTtlNanos = clamp(nextTtl, minTtlNanos, maxTtlNanos);
               }
            }
         } else {
            this.effectiveTtlNanos = this.ttlNanos;
            this.lastRetuneReason = "adaptive_disabled";
            this.pressureLevel = "low";
         }
      }

      private static long clamp(long value, long min, long max) {
         return Math.max(min, Math.min(max, value));
      }

      private static double clamp(double value, double min, double max) {
         return Math.max(min, Math.min(max, value));
      }

      private void recordEvictionAge(long ageNanos) {
         this.evictionAgeNanosSum.add(Math.max(0L, ageNanos));
         this.evictionAgeSamples.increment();
         if (ageNanos < TimeUnit.SECONDS.toNanos(10L)) {
            this.evictUnder10s.increment();
         } else if (ageNanos < TimeUnit.SECONDS.toNanos(30L)) {
            this.evict10To30s.increment();
         } else if (ageNanos < TimeUnit.MINUTES.toNanos(1L)) {
            this.evict30To60s.increment();
         } else if (ageNanos < TimeUnit.MINUTES.toNanos(2L)) {
            this.evict60To120s.increment();
         } else if (ageNanos < TimeUnit.MINUTES.toNanos(5L)) {
            this.evict120To300s.increment();
         } else {
            this.evictOver300s.increment();
         }
      }

      private void maybeLogMetrics(long now) {
         if (this.cacheDebugLogsEnabled.getAsBoolean()) {
            long lastLog = this.lastMetricsLogNanos.get();
            if (now - lastLog >= this.cacheLogIntervalNanos) {
               if (this.lastMetricsLogNanos.compareAndSet(lastLog, now)) {
                  long hits = this.hitCount.sum();
                  long misses = this.missCount.sum();
                  long totalLookups = hits + misses;
                  double hitRate = totalLookups == 0L ? 0.0 : (double)hits / totalLookups;
                  long ageSamples = this.evictionAgeSamples.sum();
                  long avgEvictAgeMs = ageSamples == 0L ? 0L : TimeUnit.NANOSECONDS.toMillis(this.evictionAgeNanosSum.sum() / ageSamples);
                  this.logSink
                     .accept(
                        "IslandField cache metrics: cache="
                           + this.name
                           + ", size="
                           + this.entries.size()
                           + ", hits="
                           + hits
                           + ", misses="
                           + misses
                           + ", hitRate="
                           + String.format("%.3f", hitRate)
                           + ", expiredOnAccess="
                           + this.expiredOnAccessCount.sum()
                           + ", puts="
                           + this.putCount.sum()
                           + ", computeLoads="
                           + this.computeLoadCount.sum()
                           + ", totalEvicted="
                           + this.totalEvicted.get()
                           + ", avgEvictAgeMs="
                           + avgEvictAgeMs
                           + ", evictAgeBuckets={lt10s:"
                           + this.evictUnder10s.sum()
                           + ",10to30s:"
                           + this.evict10To30s.sum()
                           + ",30to60s:"
                           + this.evict30To60s.sum()
                           + ",60to120s:"
                           + this.evict60To120s.sum()
                           + ",120to300s:"
                           + this.evict120To300s.sum()
                           + ",gte300s:"
                           + this.evictOver300s.sum()
                           + "}, ttlSeconds="
                           + TimeUnit.NANOSECONDS.toSeconds(this.effectiveTtlNanos)
                           + ", effectiveTtlMs="
                           + TimeUnit.NANOSECONDS.toMillis(this.effectiveTtlNanos)
                           + ", adaptiveEnabled="
                           + (this.adaptivePolicy != null && this.adaptivePolicy.enabled().getAsBoolean())
                           + ", retuneReason="
                           + this.lastRetuneReason
                           + ", pressureLevel="
                           + this.pressureLevel
                     );
               }
            }
         }
      }
   }
}
