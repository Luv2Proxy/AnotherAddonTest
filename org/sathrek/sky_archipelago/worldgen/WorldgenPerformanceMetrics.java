package org.sathrek.sky_archipelago.worldgen;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.sathrek.sky_archipelago.SkyArchipelago;

public final class WorldgenPerformanceMetrics {
   private static final boolean ENABLED = false;
   private static final long LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(15L);
   private static final long TERRAIN_CHUNK_LOG_STEP = 64L;
   private static final LongAdder terrainChunks = new LongAdder();
   private static final LongAdder terrainNanos = new LongAdder();
   private static final LongAdder terrainSnapshotNanos = new LongAdder();
   private static final LongAdder terrainActiveColumns = new LongAdder();
   private static final LongAdder terrainSkippedColumns = new LongAdder();
   private static final LongAdder terrainYIterations = new LongAdder();
   private static final LongAdder terrainBlockWrites = new LongAdder();
   private static final LongAdder oceanLayerChunks = new LongAdder();
   private static final LongAdder oceanLayerNanos = new LongAdder();
   private static final LongAdder oceanLayerYIterations = new LongAdder();
   private static final LongAdder oceanLayerBlockWrites = new LongAdder();
   private static final LongAdder surfaceChunks = new LongAdder();
   private static final LongAdder surfaceNanos = new LongAdder();
   private static final LongAdder surfaceSnapshotNanos = new LongAdder();
   private static final LongAdder surfaceProfileColumns = new LongAdder();
   private static final LongAdder surfaceSkinWrites = new LongAdder();
   private static final LongAdder structureChunks = new LongAdder();
   private static final LongAdder structureNanos = new LongAdder();
   private static final LongAdder columnSamples = new LongAdder();
   private static final LongAdder columnResolves = new LongAdder();
   private static final LongAdder columnResolveNanos = new LongAdder();
   private static final LongAdder columnResolveContributors = new LongAdder();
   private static final LongAdder columnResolveYScans = new LongAdder();
   private static final LongAdder columnResolveDensityEvals = new LongAdder();
   private static final LongAdder columnResolveCandidateChecks = new LongAdder();
   private static final LongAdder emptyBeforeYScanColumnResolves = new LongAdder();
   private static final LongAdder emptyAfterYScanColumnResolves = new LongAdder();
   private static final LongAdder emptyColumnResolves = new LongAdder();
   private static final LongAdder terrainSnapshotCacheHits = new LongAdder();
   private static final LongAdder terrainSnapshotCacheMisses = new LongAdder();
   private static final LongAdder terrainSnapshotCandidateSets = new LongAdder();
   private static final LongAdder terrainSnapshotCandidates = new LongAdder();
   private static final LongAdder terrainSnapshotOpenFastPaths = new LongAdder();
   private static final LongAdder columnResolvesFromCellSearch = new LongAdder();
   private static final LongAdder columnResolvesFromSnapshotCandidates = new LongAdder();
   private static final LongAdder gridSampleCalls = new LongAdder();
   private static final LongAdder gridSamplePoints = new LongAdder();
   private static final LongAdder localOffsetCalls = new LongAdder();
   private static final LongAdder localOffsetPoints = new LongAdder();
   private static final AtomicLong lastLogNanos = new AtomicLong(System.nanoTime());
   private static final AtomicLong lastTerrainLogStep = new AtomicLong();

   private WorldgenPerformanceMetrics() {
   }

   public static boolean enabled() {
      return false;
   }

   public static void recordTerrainFill(long totalNanos, long snapshotNanos, int activeColumns, int skippedColumns, long yIterations, long blockWrites) {
   }

   public static void recordOceanLayer(long totalNanos, long yIterations, long blockWrites) {
   }

   public static void recordSurfacePass(long totalNanos, long snapshotNanos, int profileColumns, long skinWrites) {
   }

   public static void recordStructurePass(long totalNanos) {
   }

   public static void recordColumnSample() {
   }

   public static void recordColumnResolve(long totalNanos, int contributors, int yScans, long densityEvaluations, int candidateChecks, boolean empty) {
      recordColumnResolve(totalNanos, contributors, yScans, densityEvaluations, candidateChecks, empty, false);
   }

   public static void recordColumnResolve(
      long totalNanos, int contributors, int yScans, long densityEvaluations, int candidateChecks, boolean empty, boolean snapshotCandidates
   ) {
   }

   public static void recordTerrainSnapshotCacheHit() {
   }

   public static void recordTerrainSnapshotCacheMiss() {
   }

   public static void recordTerrainSnapshotCandidateSet(int candidates, boolean openFastPath) {
   }

   public static void recordGridSample(int gridSize, int pointCount) {
   }

   public static void recordLocalOffsets(int radius, int step, int fineStep, int offsetCount) {
   }

   private static void maybeLog() {
      long terrainCount = terrainChunks.sum();
      long step = terrainCount / 64L;
      if (step > lastTerrainLogStep.get() && lastTerrainLogStep.compareAndSet(lastTerrainLogStep.get(), step)) {
         logSnapshot("chunk_step");
      } else {
         long now = System.nanoTime();
         long last = lastLogNanos.get();
         if (now - last >= LOG_INTERVAL_NANOS && lastLogNanos.compareAndSet(last, now)) {
            logSnapshot("interval");
         }
      }
   }

   private static void logSnapshot(String reason) {
      long terrainCount = terrainChunks.sum();
      long surfaceCount = surfaceChunks.sum();
      long structureCount = structureChunks.sum();
      long columnResolveCount = columnResolves.sum();
      long gridCalls = gridSampleCalls.sum();
      long offsetCalls = localOffsetCalls.sum();
      SkyArchipelago.LOGGER
         .info(
            "TEMP worldgen perf metrics reason={} terrainChunks={} terrainAvgMs={} terrainSnapshotAvgMs={} terrainActiveColsAvg={} terrainSkippedColsAvg={} terrainYIterAvg={} terrainWritesAvg={} oceanChunks={} oceanAvgMs={} oceanYIterAvg={} oceanWritesAvg={} surfaceChunks={} surfaceAvgMs={} surfaceSnapshotAvgMs={} surfaceProfileColsAvg={} surfaceSkinWritesAvg={} structureChunks={} structureAvgMs={} columnSamples={} columnResolves={} columnResolveAvgMs={} columnApproxHitRate={} columnContribAvg={} columnCandidateChecksAvg={} columnYScansAvg={} columnDensityEvalAvg={} emptyColumnResolveRatio={} emptyBeforeYScanRatio={} emptyAfterYScanRatio={} columnCellSearchRatio={} columnSnapshotCandidateRatio={} snapshotCacheHits={} snapshotCacheMisses={} snapshotCacheHitRate={} snapshotCandidateSets={} snapshotCandidatesAvg={} snapshotOpenFastPathRatio={} gridSampleCalls={} gridPointsAvg={} localOffsetCalls={} localOffsetsAvg={}",
            new Object[]{
               reason,
               terrainCount,
               avgMillis(terrainNanos.sum(), terrainCount),
               avgMillis(terrainSnapshotNanos.sum(), terrainCount),
               avgLong(terrainActiveColumns.sum(), terrainCount),
               avgLong(terrainSkippedColumns.sum(), terrainCount),
               avgLong(terrainYIterations.sum(), terrainCount),
               avgLong(terrainBlockWrites.sum(), terrainCount),
               oceanLayerChunks.sum(),
               avgMillis(oceanLayerNanos.sum(), oceanLayerChunks.sum()),
               avgLong(oceanLayerYIterations.sum(), oceanLayerChunks.sum()),
               avgLong(oceanLayerBlockWrites.sum(), oceanLayerChunks.sum()),
               surfaceCount,
               avgMillis(surfaceNanos.sum(), surfaceCount),
               avgMillis(surfaceSnapshotNanos.sum(), surfaceCount),
               avgLong(surfaceProfileColumns.sum(), surfaceCount),
               avgLong(surfaceSkinWrites.sum(), surfaceCount),
               structureCount,
               avgMillis(structureNanos.sum(), structureCount),
               columnSamples.sum(),
               columnResolveCount,
               avgMillis(columnResolveNanos.sum(), columnResolveCount),
               ratio(columnSamples.sum() - columnResolveCount, columnSamples.sum()),
               avgDecimal(columnResolveContributors.sum(), columnResolveCount),
               avgDecimal(columnResolveCandidateChecks.sum(), columnResolveCount),
               avgLong(columnResolveYScans.sum(), columnResolveCount),
               avgLong(columnResolveDensityEvals.sum(), columnResolveCount),
               ratio(emptyColumnResolves.sum(), columnResolveCount),
               ratio(emptyBeforeYScanColumnResolves.sum(), columnResolveCount),
               ratio(emptyAfterYScanColumnResolves.sum(), columnResolveCount),
               ratio(columnResolvesFromCellSearch.sum(), columnResolveCount),
               ratio(columnResolvesFromSnapshotCandidates.sum(), columnResolveCount),
               terrainSnapshotCacheHits.sum(),
               terrainSnapshotCacheMisses.sum(),
               ratio(terrainSnapshotCacheHits.sum(), terrainSnapshotCacheHits.sum() + terrainSnapshotCacheMisses.sum()),
               terrainSnapshotCandidateSets.sum(),
               avgDecimal(terrainSnapshotCandidates.sum(), terrainSnapshotCandidateSets.sum()),
               ratio(terrainSnapshotOpenFastPaths.sum(), terrainSnapshotCandidateSets.sum()),
               gridCalls,
               avgLong(gridSamplePoints.sum(), gridCalls),
               offsetCalls,
               avgLong(localOffsetPoints.sum(), offsetCalls)
            }
         );
   }

   private static long avgLong(long total, long count) {
      return count <= 0L ? 0L : total / count;
   }

   private static String avgMillis(long totalNanos, long count) {
      return count <= 0L ? "0.000" : String.format("%.3f", (double)totalNanos / count / 1000000.0);
   }

   private static String avgDecimal(long total, long count) {
      return count <= 0L ? "0.000" : String.format("%.3f", (double)total / count);
   }

   private static String ratio(long numerator, long denominator) {
      return denominator <= 0L ? "0.000" : String.format("%.3f", Math.max(0.0, (double)numerator / denominator));
   }
}
