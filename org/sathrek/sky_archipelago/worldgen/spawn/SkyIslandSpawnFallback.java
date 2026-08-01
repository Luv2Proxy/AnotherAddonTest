package org.sathrek.sky_archipelago.worldgen.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

@EventBusSubscriber(modid = "sky_archipelago")
public final class SkyIslandSpawnFallback {
   private static final boolean LOGGING_ENABLED = false;
   private static final int MAX_RADIUS_BLOCKS = 98304;
   private static final int RING_STEP_BLOCKS = 64;
   private static final int POINTS_PER_RING = 128;
   private static final int MAX_CANDIDATES = 120000;
   private static final int PROGRESS_LOG_RING_INTERVAL = 32;

   private SkyIslandSpawnFallback() {
   }

   @SubscribeEvent
   static void onServerStarted(ServerStartedEvent event) {
      ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
      if (overworld != null) {
         if (overworld.getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator generator) {
            if (overworld.getGameTime() <= 40L) {
               RandomState randomState = overworld.getChunkSource().randomState();
               BlockPos currentSpawn = overworld.getSharedSpawnPos();
               if (generator.resolveAnchorSpawnPos(randomState, currentSpawn.getX(), currentSpawn.getZ(), overworld.getMaxBuildHeight()) == null) {
                  long startNanos = System.nanoTime();
                  int attempts = 0;
                  logInfo(
                     "Spawn fallback start: center={}, maxRadius={}, ringStep={}, pointsPerRing={}, maxCandidates={}", currentSpawn, 98304, 64, 128, 120000
                  );
                  BlockPos centeredCandidate = generator.resolveAnchorSpawnPos(
                     randomState, currentSpawn.getX(), currentSpawn.getZ(), overworld.getMaxBuildHeight()
                  );
                  attempts++;
                  if (centeredCandidate != null) {
                     overworld.setDefaultSpawnPos(centeredCandidate, 0.0F);
                     long elapsedMs = (System.nanoTime() - startNanos) / 1000000L;
                     logInfo("Spawn fallback selected {} after {} attempts (radius=0 blocks, {}ms)", centeredCandidate, attempts, elapsedMs);
                  } else {
                     for (int radius = 64; radius <= 98304; radius += 64) {
                        int ringRadius = radius;
                        int ringIndex = radius / 64;
                        if (ringIndex % 32 == 0) {
                           logInfo("Spawn fallback progress: radius={} attempts={} elapsedMs={}", radius, attempts, (System.nanoTime() - startNanos) / 1000000L);
                        }

                        for (int index = 0; index < 128; index++) {
                           if (attempts++ >= 120000) {
                              logWarn(
                                 "Spawn fallback aborted: attempts={} maxCandidates={} radius={} elapsedMs={}",
                                 attempts,
                                 120000,
                                 radius,
                                 (System.nanoTime() - startNanos) / 1000000L
                              );
                              return;
                           }

                           double angle = (Math.PI * 2) * index / 128.0;
                           int x = currentSpawn.getX() + (int)Math.round(Math.cos(angle) * ringRadius);
                           int z = currentSpawn.getZ() + (int)Math.round(Math.sin(angle) * ringRadius);
                           BlockPos candidate = generator.resolveAnchorSpawnPos(randomState, x, z, overworld.getMaxBuildHeight());
                           if (candidate != null) {
                              overworld.setDefaultSpawnPos(candidate, 0.0F);
                              long elapsedMs = (System.nanoTime() - startNanos) / 1000000L;
                              logInfo("Spawn fallback selected {} after {} attempts (radius={} blocks, {}ms)", candidate, attempts, radius, elapsedMs);
                              return;
                           }
                        }
                     }

                     long elapsedMs = (System.nanoTime() - startNanos) / 1000000L;
                     logWarn("Spawn fallback failed after {} attempts (radius={} blocks, {}ms)", attempts, 98304, elapsedMs);
                  }
               }
            }
         }
      }
   }

   private static void logInfo(String message, Object... args) {
   }

   private static void logWarn(String message, Object... args) {
   }
}
