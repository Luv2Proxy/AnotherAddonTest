package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;

public final class FcfsChunkWindowTelemetry {
   private static final int WINDOW_SHIFT = 5;
   private static final int LOG_EVERY_ATTEMPTS = 64;
   private static final Map<Long, FcfsChunkWindowTelemetry.WindowStats> WINDOWS = new ConcurrentHashMap<>();

   private FcfsChunkWindowTelemetry() {
   }

   public static void record(
      int chunkX,
      int chunkZ,
      boolean accepted,
      boolean fcfsOverlapRejected,
      boolean hostSwitched,
      long arbitrationNanos,
      int prefilterCandidates,
      int authoritativeChecks,
      boolean fallbackToAuthoritative
   ) {
      int wx = chunkX >> 5;
      int wz = chunkZ >> 5;
      long key = (long)wx << 32 ^ wz & 4294967295L;
      FcfsChunkWindowTelemetry.WindowStats stats = WINDOWS.computeIfAbsent(key, ignored -> new FcfsChunkWindowTelemetry.WindowStats());
      long attempts = stats.attempts.incrementAndGet();
      if (accepted) {
         stats.accepted.incrementAndGet();
      }

      if (fcfsOverlapRejected) {
         stats.overlapRejects.incrementAndGet();
      }

      if (hostSwitched) {
         stats.hostSwitches.incrementAndGet();
      }

      stats.prefilterCandidates.addAndGet(Math.max(0L, prefilterCandidates));
      stats.authoritativeChecks.addAndGet(Math.max(0L, authoritativeChecks));
      if (fallbackToAuthoritative) {
         stats.fallbackToAuthoritative.incrementAndGet();
      }

      stats.arbitrationNanos.addAndGet(Math.max(0L, arbitrationNanos));
      if (SkyIslandServerConfig.structureDebugEnabled() && attempts % 64L == 0L) {
         long acceptedCount = stats.accepted.get();
         long overlapRejectCount = stats.overlapRejects.get();
         long hostSwitchCount = stats.hostSwitches.get();
         long prefilterCount = stats.prefilterCandidates.get();
         long authoritativeCheckCount = stats.authoritativeChecks.get();
         long fallbackCount = stats.fallbackToAuthoritative.get();
         double avgArbMicros = attempts == 0L ? 0.0 : (double)stats.arbitrationNanos.get() / attempts / 1000.0;
         SkyArchipelago.LOGGER
            .info(
               "FCFS_WINDOW window=[{}, {}] attempts={} accepted={} overlapRejects={} hostSwitches={} prefilterCandidates={} authoritativeChecks={} fallbackToAuthoritative={} avgArbitrationMicros={}",
               new Object[]{
                  wx,
                  wz,
                  attempts,
                  acceptedCount,
                  overlapRejectCount,
                  hostSwitchCount,
                  prefilterCount,
                  authoritativeCheckCount,
                  fallbackCount,
                  String.format(Locale.ROOT, "%.2f", avgArbMicros)
               }
            );
      }
   }

   private static final class WindowStats {
      private final AtomicLong attempts = new AtomicLong();
      private final AtomicLong accepted = new AtomicLong();
      private final AtomicLong overlapRejects = new AtomicLong();
      private final AtomicLong hostSwitches = new AtomicLong();
      private final AtomicLong prefilterCandidates = new AtomicLong();
      private final AtomicLong authoritativeChecks = new AtomicLong();
      private final AtomicLong fallbackToAuthoritative = new AtomicLong();
      private final AtomicLong arbitrationNanos = new AtomicLong();
   }
}
