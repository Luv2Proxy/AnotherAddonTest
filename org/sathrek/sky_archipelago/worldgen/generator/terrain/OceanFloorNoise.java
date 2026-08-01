package org.sathrek.sky_archipelago.worldgen.generator.terrain;

import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;

final class OceanFloorNoise {
   private static final long FLOOR_NOISE_SALT = 7948691001448844613L;
   private static final int SMOOTH_RADIUS = 2;
   private static final int MAX_SMOOTH_DELTA = 8;

   private OceanFloorNoise() {
   }

   static int oceanFloorTopY(int worldX, int worldZ, int minY, int maxY, SkyIslandSettings settings, long layoutSeed) {
      int oceanTopY = Math.min(maxY - 1, settings.terrain().ocean().oceanLevelY());
      int depth = resolveFloorDepth(worldX, worldZ, settings, layoutSeed);
      int candidateY = oceanTopY - depth;
      return Mth.clamp(candidateY, minY + 1, oceanTopY);
   }

   private static int resolveFloorDepth(int worldX, int worldZ, SkyIslandSettings settings, long layoutSeed) {
      int centerDepth = sampleRawDepth(worldX, worldZ, settings, layoutSeed);
      int smoothedDepth = smoothDepth(worldX, worldZ, settings, layoutSeed);
      int clampedToLocalDelta = Mth.clamp(smoothedDepth, centerDepth - 8, centerDepth + 8);
      return Mth.clamp(clampedToLocalDelta, settings.terrain().ocean().oceanFloorMinDepth(), settings.terrain().ocean().oceanFloorMaxDepth());
   }

   private static int smoothDepth(int worldX, int worldZ, SkyIslandSettings settings, long layoutSeed) {
      int weightedSum = 0;
      int weightTotal = 0;

      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            int manhattan = Math.abs(dx) + Math.abs(dz);

            int weight = switch (manhattan) {
               case 0 -> 8;
               case 1 -> 4;
               case 2 -> 2;
               default -> 1;
            };
            int sample = sampleRawDepth(worldX + dx, worldZ + dz, settings, layoutSeed);
            weightedSum += sample * weight;
            weightTotal += weight;
         }
      }

      return weightTotal <= 0 ? sampleRawDepth(worldX, worldZ, settings, layoutSeed) : (int)Math.round((double)weightedSum / weightTotal);
   }

   private static int sampleRawDepth(int worldX, int worldZ, SkyIslandSettings settings, long layoutSeed) {
      int baseDepth = settings.terrain().ocean().oceanFloorBaseOffset();
      int amplitude = settings.terrain().ocean().oceanFloorNoiseAmplitude();
      if (amplitude <= 0) {
         return Mth.clamp(baseDepth, settings.terrain().ocean().oceanFloorMinDepth(), settings.terrain().ocean().oceanFloorMaxDepth());
      }

      double noise = fbm2D(
         layoutSeed ^ 7948691001448844613L,
         worldX * settings.terrain().ocean().oceanFloorNoiseScale(),
         worldZ * settings.terrain().ocean().oceanFloorNoiseScale(),
         4,
         0.5
      );
      int depth = baseDepth + (int)Math.round(noise * amplitude);
      return Mth.clamp(depth, settings.terrain().ocean().oceanFloorMinDepth(), settings.terrain().ocean().oceanFloorMaxDepth());
   }

   private static double fbm2D(long seed, double x, double z, int octaves, double gain) {
      double value = 0.0;
      double amplitude = 1.0;
      double frequency = 1.0;
      double maxAmplitude = 0.0;

      for (int octave = 0; octave < octaves; octave++) {
         value += valueNoise2D(seed + octave * 101L, x * frequency, z * frequency) * amplitude;
         maxAmplitude += amplitude;
         amplitude *= gain;
         frequency *= 2.0;
      }

      return maxAmplitude <= 0.0 ? 0.0 : value / maxAmplitude;
   }

   private static double valueNoise2D(long seed, double x, double z) {
      int x0 = Mth.floor(x);
      int z0 = Mth.floor(z);
      int x1 = x0 + 1;
      int z1 = z0 + 1;
      double tx = smoothstep(x - x0);
      double tz = smoothstep(z - z0);
      double v00 = lattice2D(seed, x0, z0);
      double v10 = lattice2D(seed, x1, z0);
      double v01 = lattice2D(seed, x0, z1);
      double v11 = lattice2D(seed, x1, z1);
      double a = Mth.lerp(tx, v00, v10);
      double b = Mth.lerp(tx, v01, v11);
      return Mth.lerp(tz, a, b);
   }

   private static double lattice2D(long seed, int x, int z) {
      long hash = mix(seed, x, z);
      return (hash >>> 11) * 1.110223E-16F * 2.0 - 1.0;
   }

   private static long mix(long seed, int x, int z) {
      long hash = seed;
      hash ^= x * -7046029254386353131L;
      hash = Long.rotateLeft(hash, 17);
      hash ^= z * -4417276706812531889L;
      hash ^= hash >>> 29;
      hash *= 1609587929392839161L;
      hash ^= hash >>> 32;
      hash *= -7046029254386353131L;
      return hash ^ hash >>> 28;
   }

   private static double smoothstep(double t) {
      return t * t * (3.0 - 2.0 * t);
   }
}
