package org.sathrek.sky_archipelago.config;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.config.ModConfigEvent.Unloading;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

@EventBusSubscriber(modid = "sky_archipelago", bus = Bus.MOD)
public final class SkyIslandServerConfig {
   private static final Builder BUILDER = new Builder();
   private static final BooleanValue STRUCTURE_DEBUG_ENABLED = BUILDER.comment(
         "Master toggle for Sky Archipelago structure debug output (verbose placement logs + debug broadcasts)."
      )
      .define("structureDebugEnabled", false);
   private static final BooleanValue STRUCTURE_DEBUG_BROADCAST_ENABLED = BUILDER.comment("Broadcast accepted POI coordinates in server chat for debugging.")
      .define("structureDebugBroadcastEnabled", false);
   private static final BooleanValue STRUCTURE_VERBOSE_LOGS_ENABLED = BUILDER.comment("Emit detailed accepted/rejected structure placement logs.")
      .define("structureVerboseLogsEnabled", false);
   private static final BooleanValue SUPPRESS_SMALL_LAND_VERBOSE_LOGS = BUILDER.comment(
         "Suppress per-structure verbose logs for SMALL LAND placements; aggregate summary still records them."
      )
      .define("suppressSmallLandVerboseLogs", false);
   private static final BooleanValue UNDERGROUND_VERBOSE_ONLY = BUILDER.comment(
         "When enabled, only emit verbose logs related to UNDERGROUND structure placement."
      )
      .define("undergroundVerboseOnly", false);
   private static final BooleanValue CACHE_METRICS_LOGS_ENABLED = BUILDER.comment(
         "Emit periodic worldgen cache metrics (hit/miss/eviction age) for tuning cache unload behavior."
      )
      .define("cacheMetricsLogsEnabled", false);
   private static final BooleanValue ADAPTIVE_COLUMN_CACHE_ENABLED = BUILDER.comment("Enable adaptive TTL tuning for the worldgen column cache.")
      .define("adaptiveColumnCacheEnabled", true);
   private static final IntValue ADAPTIVE_COLUMN_CACHE_MIN_TTL_SECONDS = BUILDER.comment("Minimum adaptive TTL for column cache entries, in seconds.")
      .defineInRange("adaptiveColumnCacheMinTtlSeconds", 15, 1, 3600);
   private static final IntValue ADAPTIVE_COLUMN_CACHE_MAX_TTL_SECONDS = BUILDER.comment("Maximum adaptive TTL for column cache entries, in seconds.")
      .defineInRange("adaptiveColumnCacheMaxTtlSeconds", 180, 1, 3600);
   private static final IntValue ADAPTIVE_COLUMN_CACHE_RETUNE_INTERVAL_SECONDS = BUILDER.comment("Adaptive TTL retune interval for column cache, in seconds.")
      .defineInRange("adaptiveColumnCacheRetuneIntervalSeconds", 15, 1, 300);
   private static final IntValue ADAPTIVE_COLUMN_CACHE_ADJUSTMENT_PERCENT = BUILDER.comment(
         "Percent step used when increasing or decreasing adaptive column cache TTL."
      )
      .defineInRange("adaptiveColumnCacheAdjustmentPercent", 15, 1, 100);
   private static final DoubleValue ADAPTIVE_COLUMN_CACHE_HIGH_PRESSURE_MISS_RATE_THRESHOLD = BUILDER.comment(
         "Misses/second threshold that indicates high pressure for adaptive column cache TTL tuning."
      )
      .defineInRange("adaptiveColumnCacheHighPressureMissRateThreshold", 4000.0, 1.0, 1000000.0);
   private static final DoubleValue ADAPTIVE_COLUMN_CACHE_TARGET_HIT_RATE = BUILDER.comment("Target hit rate for adaptive column cache TTL tuning.")
      .defineInRange("adaptiveColumnCacheTargetHitRate", 0.85, 0.0, 1.0);
   private static final BooleanValue STRUCTURE_PLACEMENT_CRASH_SHIELD_ENABLED = BUILDER.comment(
         "Guard per-structure feature placement failures so one bad structure cannot crash chunk generation."
      )
      .define("structurePlacementCrashShieldEnabled", true);
   private static final BooleanValue STRUCTURE_PLACEMENT_CRASH_SHIELD_STRICT = BUILDER.comment(
         "Strict mode for structure placement crash shield. When true, guarded placement failures are rethrown."
      )
      .define("structurePlacementCrashShieldStrict", false);
   private static final IntValue STRUCTURE_PLACEMENT_CRASH_SHIELD_RATE_LIMIT_SECONDS = BUILDER.comment(
         "Rate limit window in seconds for repeating crash-shield warning logs per structure id."
      )
      .defineInRange("structurePlacementCrashShieldRateLimitSeconds", 30, 1, 3600);
   public static final ModConfigSpec SPEC = BUILDER.build();
   private static final AtomicBoolean CURRENT_STRUCTURE_DEBUG_ENABLED = new AtomicBoolean(false);
   private static final AtomicBoolean CURRENT_CACHE_METRICS_LOGS_ENABLED = new AtomicBoolean(false);
   private static final AtomicBoolean CURRENT_ADAPTIVE_COLUMN_CACHE_ENABLED = new AtomicBoolean(true);
   private static final AtomicInteger CURRENT_ADAPTIVE_COLUMN_CACHE_MIN_TTL_SECONDS = new AtomicInteger(15);
   private static final AtomicInteger CURRENT_ADAPTIVE_COLUMN_CACHE_MAX_TTL_SECONDS = new AtomicInteger(180);
   private static final AtomicInteger CURRENT_ADAPTIVE_COLUMN_CACHE_RETUNE_INTERVAL_SECONDS = new AtomicInteger(15);
   private static final AtomicInteger CURRENT_ADAPTIVE_COLUMN_CACHE_ADJUSTMENT_PERCENT = new AtomicInteger(15);
   private static final AtomicReference<Double> CURRENT_ADAPTIVE_COLUMN_CACHE_HIGH_PRESSURE_MISS_RATE_THRESHOLD = new AtomicReference<>(4000.0);
   private static final AtomicReference<Double> CURRENT_ADAPTIVE_COLUMN_CACHE_TARGET_HIT_RATE = new AtomicReference<>(0.85);
   private static final AtomicBoolean CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_ENABLED = new AtomicBoolean(true);
   private static final AtomicBoolean CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_STRICT = new AtomicBoolean(false);
   private static final AtomicInteger CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_RATE_LIMIT_SECONDS = new AtomicInteger(30);

   private SkyIslandServerConfig() {
   }

   public static boolean structureDebugEnabled() {
      return CURRENT_STRUCTURE_DEBUG_ENABLED.get();
   }

   public static boolean cacheMetricsLogsEnabled() {
      return CURRENT_CACHE_METRICS_LOGS_ENABLED.get();
   }

   public static boolean adaptiveColumnCacheEnabled() {
      return CURRENT_ADAPTIVE_COLUMN_CACHE_ENABLED.get();
   }

   public static int adaptiveColumnCacheMinTtlSeconds() {
      return CURRENT_ADAPTIVE_COLUMN_CACHE_MIN_TTL_SECONDS.get();
   }

   public static int adaptiveColumnCacheMaxTtlSeconds() {
      return CURRENT_ADAPTIVE_COLUMN_CACHE_MAX_TTL_SECONDS.get();
   }

   public static int adaptiveColumnCacheRetuneIntervalSeconds() {
      return CURRENT_ADAPTIVE_COLUMN_CACHE_RETUNE_INTERVAL_SECONDS.get();
   }

   public static int adaptiveColumnCacheAdjustmentPercent() {
      return CURRENT_ADAPTIVE_COLUMN_CACHE_ADJUSTMENT_PERCENT.get();
   }

   public static double adaptiveColumnCacheHighPressureMissRateThreshold() {
      return CURRENT_ADAPTIVE_COLUMN_CACHE_HIGH_PRESSURE_MISS_RATE_THRESHOLD.get();
   }

   public static double adaptiveColumnCacheTargetHitRate() {
      return CURRENT_ADAPTIVE_COLUMN_CACHE_TARGET_HIT_RATE.get();
   }

   public static boolean structurePlacementCrashShieldEnabled() {
      return CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_ENABLED.get();
   }

   public static boolean structurePlacementCrashShieldStrict() {
      return CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_STRICT.get();
   }

   public static int structurePlacementCrashShieldRateLimitSeconds() {
      return CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_RATE_LIMIT_SECONDS.get();
   }

   @SubscribeEvent
   static void onConfigLoad(ModConfigEvent event) {
      if (event.getConfig().getSpec() == SPEC) {
         if (event instanceof Unloading) {
            CURRENT_STRUCTURE_DEBUG_ENABLED.set(false);
            CURRENT_CACHE_METRICS_LOGS_ENABLED.set(false);
            CURRENT_ADAPTIVE_COLUMN_CACHE_ENABLED.set(false);
            CURRENT_ADAPTIVE_COLUMN_CACHE_MIN_TTL_SECONDS.set(15);
            CURRENT_ADAPTIVE_COLUMN_CACHE_MAX_TTL_SECONDS.set(180);
            CURRENT_ADAPTIVE_COLUMN_CACHE_RETUNE_INTERVAL_SECONDS.set(15);
            CURRENT_ADAPTIVE_COLUMN_CACHE_ADJUSTMENT_PERCENT.set(15);
            CURRENT_ADAPTIVE_COLUMN_CACHE_HIGH_PRESSURE_MISS_RATE_THRESHOLD.set(4000.0);
            CURRENT_ADAPTIVE_COLUMN_CACHE_TARGET_HIT_RATE.set(0.85);
            CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_ENABLED.set(true);
            CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_STRICT.set(false);
            CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_RATE_LIMIT_SECONDS.set(30);
         } else {
            CURRENT_STRUCTURE_DEBUG_ENABLED.set((Boolean)STRUCTURE_DEBUG_ENABLED.get());
            STRUCTURE_DEBUG_BROADCAST_ENABLED.get();
            STRUCTURE_VERBOSE_LOGS_ENABLED.get();
            SUPPRESS_SMALL_LAND_VERBOSE_LOGS.get();
            UNDERGROUND_VERBOSE_ONLY.get();
            CURRENT_CACHE_METRICS_LOGS_ENABLED.set((Boolean)CACHE_METRICS_LOGS_ENABLED.get());
            CURRENT_ADAPTIVE_COLUMN_CACHE_ENABLED.set((Boolean)ADAPTIVE_COLUMN_CACHE_ENABLED.get());
            int minTtl = (Integer)ADAPTIVE_COLUMN_CACHE_MIN_TTL_SECONDS.get();
            int maxTtl = (Integer)ADAPTIVE_COLUMN_CACHE_MAX_TTL_SECONDS.get();
            CURRENT_ADAPTIVE_COLUMN_CACHE_MIN_TTL_SECONDS.set(Math.min(minTtl, maxTtl));
            CURRENT_ADAPTIVE_COLUMN_CACHE_MAX_TTL_SECONDS.set(Math.max(minTtl, maxTtl));
            CURRENT_ADAPTIVE_COLUMN_CACHE_RETUNE_INTERVAL_SECONDS.set((Integer)ADAPTIVE_COLUMN_CACHE_RETUNE_INTERVAL_SECONDS.get());
            CURRENT_ADAPTIVE_COLUMN_CACHE_ADJUSTMENT_PERCENT.set((Integer)ADAPTIVE_COLUMN_CACHE_ADJUSTMENT_PERCENT.get());
            CURRENT_ADAPTIVE_COLUMN_CACHE_HIGH_PRESSURE_MISS_RATE_THRESHOLD.set((Double)ADAPTIVE_COLUMN_CACHE_HIGH_PRESSURE_MISS_RATE_THRESHOLD.get());
            CURRENT_ADAPTIVE_COLUMN_CACHE_TARGET_HIT_RATE.set((Double)ADAPTIVE_COLUMN_CACHE_TARGET_HIT_RATE.get());
            CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_ENABLED.set((Boolean)STRUCTURE_PLACEMENT_CRASH_SHIELD_ENABLED.get());
            CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_STRICT.set((Boolean)STRUCTURE_PLACEMENT_CRASH_SHIELD_STRICT.get());
            CURRENT_STRUCTURE_PLACEMENT_CRASH_SHIELD_RATE_LIMIT_SECONDS.set((Integer)STRUCTURE_PLACEMENT_CRASH_SHIELD_RATE_LIMIT_SECONDS.get());
         }
      }
   }
}
