package org.sathrek.sky_archipelago.worldgen.generator.structure;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.RelocatedPlacementContextManager;

public final class FeaturePlacementCrashShield {
   private static final AtomicLong SHIELDED_FAILURES_TOTAL = new AtomicLong();
   private static final Map<ResourceLocation, AtomicLong> SHIELDED_FAILURES_BY_STRUCTURE = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, AtomicLong> LAST_WARN_EPOCH_SECONDS = new ConcurrentHashMap<>();
   private static volatile LongSupplier EPOCH_SECONDS_SUPPLIER = () -> System.currentTimeMillis() / 1000L;

   private FeaturePlacementCrashShield() {
   }

   public static void placeStructureStartSafely(
      StructureStart structureStart,
      WorldGenLevel level,
      StructureManager structureManager,
      ChunkGenerator generator,
      RandomSource random,
      BoundingBox box,
      ChunkPos chunkPos
   ) {
      try {
         try (RelocatedPlacementContextManager.Scope ignored = RelocatedPlacementContextManager.activateFor(structureStart)) {
            structureStart.placeInChunk(level, structureManager, generator, random, box, chunkPos);
         }

         WaterVolumePostPlacementNormalizer.normalizeAfterPlacement(level, resolveStructureId(level, structureStart), chunkPos);
      } catch (Throwable throwable) {
         if (!shouldSwallow(throwable)) {
            throw throwable;
         }

         onShieldedFailure(level, structureStart, chunkPos, throwable);
      }
   }

   static boolean shouldSwallow(Throwable throwable) {
      if (!SkyIslandServerConfig.structurePlacementCrashShieldEnabled()) {
         return false;
      } else if (throwable instanceof VirtualMachineError || throwable instanceof ThreadDeath || throwable instanceof LinkageError) {
         return false;
      } else {
         return SkyIslandServerConfig.structurePlacementCrashShieldStrict() ? false : isStructurePlacementFailure(throwable);
      }
   }

   private static boolean isStructurePlacementFailure(Throwable throwable) {
      for (Throwable current = throwable; current != null; current = current.getCause()) {
         String className = current.getClass().getName();
         if (className.contains("levelgen.structure")
            || className.contains("structure.templatesystem")
            || className.contains("PoolElementStructurePiece")
            || className.contains("StructureTemplate")) {
            return true;
         }

         for (StackTraceElement element : current.getStackTrace()) {
            String owner = element.getClassName();
            if (owner.contains("levelgen.structure")
               || owner.contains("structure.templatesystem")
               || owner.contains("PoolElementStructurePiece")
               || owner.contains("StructureTemplate")) {
               return true;
            }
         }
      }

      return false;
   }

   private static void onShieldedFailure(WorldGenLevel level, StructureStart structureStart, ChunkPos chunkPos, Throwable throwable) {
      ResourceLocation structureId = resolveStructureId(level, structureStart);
      long total = SHIELDED_FAILURES_TOTAL.incrementAndGet();
      long byStructure = SHIELDED_FAILURES_BY_STRUCTURE.computeIfAbsent(structureId, ignored -> new AtomicLong()).incrementAndGet();
      boolean shouldWarn = shouldWarnNow(structureId);
      if (shouldWarn) {
         StackTraceElement head = firstFrame(throwable);
         String frame = head == null ? "none" : head.getClassName() + "#" + head.getMethodName() + ":" + head.getLineNumber();
         String message = throwable.getMessage() == null ? "null" : throwable.getMessage();
         SkyArchipelago.LOGGER
            .warn(
               "Shielded structure placement failure stage=feature_placement structureId={} chunk=[{}, {}] exception={} message={} firstFrame={} shielded_failures_total={} shielded_failures_by_structure={} hint=\"consider denylisting {}\"",
               new Object[]{structureId, chunkPos.x, chunkPos.z, throwable.getClass().getName(), message, frame, total, byStructure, structureId}
            );
      }
   }

   private static StackTraceElement firstFrame(Throwable throwable) {
      for (Throwable current = throwable; current != null; current = current.getCause()) {
         StackTraceElement[] trace = current.getStackTrace();
         if (trace.length > 0) {
            return trace[0];
         }
      }

      return null;
   }

   private static ResourceLocation resolveStructureId(WorldGenLevel level, StructureStart structureStart) {
      ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structureStart.getStructure());
      return id != null ? id : ResourceLocation.parse("sky_archipelago:unknown_structure");
   }

   static void setEpochSecondsSupplierForTests(LongSupplier supplier) {
      EPOCH_SECONDS_SUPPLIER = supplier;
   }

   static boolean shouldWarnNowForTests(ResourceLocation structureId) {
      return shouldWarnNow(structureId);
   }

   static void recordFailureForTests(ResourceLocation structureId) {
      SHIELDED_FAILURES_TOTAL.incrementAndGet();
      SHIELDED_FAILURES_BY_STRUCTURE.computeIfAbsent(structureId, ignored -> new AtomicLong()).incrementAndGet();
   }

   static void resetForTests() {
      SHIELDED_FAILURES_TOTAL.set(0L);
      SHIELDED_FAILURES_BY_STRUCTURE.clear();
      LAST_WARN_EPOCH_SECONDS.clear();
      EPOCH_SECONDS_SUPPLIER = () -> System.currentTimeMillis() / 1000L;
   }

   static long shieldedFailuresTotal() {
      return SHIELDED_FAILURES_TOTAL.get();
   }

   static long shieldedFailuresByStructure(ResourceLocation structureId) {
      AtomicLong count = SHIELDED_FAILURES_BY_STRUCTURE.get(structureId);
      return count == null ? 0L : count.get();
   }

   private static boolean shouldWarnNow(ResourceLocation structureId) {
      long now = EPOCH_SECONDS_SUPPLIER.getAsLong();
      long rateLimitSeconds = Math.max(1, SkyIslandServerConfig.structurePlacementCrashShieldRateLimitSeconds());
      AtomicLong lastWarn = LAST_WARN_EPOCH_SECONDS.computeIfAbsent(structureId, ignored -> new AtomicLong(Long.MIN_VALUE));
      long previous = lastWarn.get();
      return previous == Long.MIN_VALUE ? lastWarn.compareAndSet(previous, now) : now - previous >= rateLimitSeconds && lastWarn.compareAndSet(previous, now);
   }
}
