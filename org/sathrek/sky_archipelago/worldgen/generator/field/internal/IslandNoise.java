package org.sathrek.sky_archipelago.worldgen.generator.field.internal;

import net.minecraft.util.Mth;

public final class IslandNoise {
   private final long layoutSeed;

   public IslandNoise(long layoutSeed) {
      this.layoutSeed = layoutSeed;
   }

   public int sampleInt(long seed, int salt, int minInclusive, int maxInclusive) {
      if (minInclusive == maxInclusive) {
         return minInclusive;
      }

      double sample = this.sample01(seed, salt);
      return minInclusive + (int)Math.floor(sample * (maxInclusive - minInclusive + 1));
   }

   public int sampleInt(int cellX, int cellZ, int salt, int minInclusive, int maxInclusive) {
      return this.sampleInt(mix(this.layoutSeed + salt, cellX, cellZ), salt, minInclusive, maxInclusive);
   }

   public double sampleRange(long seed, int salt, double minInclusive, double maxInclusive) {
      return Mth.lerp(this.sample01(seed, salt), minInclusive, maxInclusive);
   }

   public double sample01(int cellX, int cellZ, int salt) {
      return this.sample01(mix(this.layoutSeed + salt, cellX, cellZ), salt);
   }

   public double sample01(long seed, int salt) {
      long hash = mix(seed + salt * 31L, salt, 17);
      long masked = hash >>> 11;
      return masked * 1.110223E-16F;
   }

   public double fbm2D(long seed, double x, double z, int octaves, double gain) {
      double value = 0.0;
      double amplitude = 1.0;
      double frequency = 1.0;
      double maxAmplitude = 0.0;

      for (int octave = 0; octave < octaves; octave++) {
         value += this.valueNoise2D(seed + octave * 97L, x * frequency, z * frequency) * amplitude;
         maxAmplitude += amplitude;
         amplitude *= gain;
         frequency *= 2.0;
      }

      return maxAmplitude <= 0.0 ? 0.0 : value / maxAmplitude;
   }

   public double ridgedFbm2D(long seed, double x, double z, int octaves, double gain) {
      return this.toRidged(this.fbm2D(seed, x, z, octaves, gain));
   }

   public double fbm3D(long seed, double x, double y, double z, int octaves, double gain) {
      double value = 0.0;
      double amplitude = 1.0;
      double frequency = 1.0;
      double maxAmplitude = 0.0;

      for (int octave = 0; octave < octaves; octave++) {
         value += this.valueNoise3D(seed + octave * 131L, x * frequency, y * frequency, z * frequency) * amplitude;
         maxAmplitude += amplitude;
         amplitude *= gain;
         frequency *= 2.0;
      }

      return maxAmplitude <= 0.0 ? 0.0 : value / maxAmplitude;
   }

   public double ridgedFbm3D(long seed, double x, double y, double z, int octaves, double gain) {
      return this.toRidged(this.fbm3D(seed, x, y, z, octaves, gain));
   }

   private double valueNoise2D(long seed, double x, double z) {
      int x0 = Mth.floor(x);
      int z0 = Mth.floor(z);
      int x1 = x0 + 1;
      int z1 = z0 + 1;
      double tx = smoothstep(x - x0);
      double tz = smoothstep(z - z0);
      double v00 = this.lattice2D(seed, x0, z0);
      double v10 = this.lattice2D(seed, x1, z0);
      double v01 = this.lattice2D(seed, x0, z1);
      double v11 = this.lattice2D(seed, x1, z1);
      double a = Mth.lerp(tx, v00, v10);
      double b = Mth.lerp(tx, v01, v11);
      return Mth.lerp(tz, a, b);
   }

   private double valueNoise3D(long seed, double x, double y, double z) {
      int x0 = Mth.floor(x);
      int y0 = Mth.floor(y);
      int z0 = Mth.floor(z);
      int x1 = x0 + 1;
      int y1 = y0 + 1;
      int z1 = z0 + 1;
      double tx = smoothstep(x - x0);
      double ty = smoothstep(y - y0);
      double tz = smoothstep(z - z0);
      double c000 = this.lattice3D(seed, x0, y0, z0);
      double c100 = this.lattice3D(seed, x1, y0, z0);
      double c010 = this.lattice3D(seed, x0, y1, z0);
      double c110 = this.lattice3D(seed, x1, y1, z0);
      double c001 = this.lattice3D(seed, x0, y0, z1);
      double c101 = this.lattice3D(seed, x1, y0, z1);
      double c011 = this.lattice3D(seed, x0, y1, z1);
      double c111 = this.lattice3D(seed, x1, y1, z1);
      double x00 = Mth.lerp(tx, c000, c100);
      double x10 = Mth.lerp(tx, c010, c110);
      double x01 = Mth.lerp(tx, c001, c101);
      double x11 = Mth.lerp(tx, c011, c111);
      double y0Mix = Mth.lerp(ty, x00, x10);
      double y1Mix = Mth.lerp(ty, x01, x11);
      return Mth.lerp(tz, y0Mix, y1Mix);
   }

   private double lattice2D(long seed, int x, int z) {
      long hash = mix(seed, x, z);
      return (hash >>> 11) * 1.110223E-16F * 2.0 - 1.0;
   }

   private double lattice3D(long seed, int x, int y, int z) {
      long hash = mix(seed ^ y * -7046029254386353131L, x, z);
      hash ^= Long.rotateLeft(y * -4417276706812531889L, 21);
      hash ^= hash >>> 29;
      hash *= 1609587929392839161L;
      hash ^= hash >>> 28;
      return (hash >>> 11) * 1.110223E-16F * 2.0 - 1.0;
   }

   private double toRidged(double value) {
      return 1.0 - Math.abs(value);
   }

   public static double ellipseDensity(double localX, double localZ, double radiusX, double radiusZ) {
      double xTerm = localX * localX / (radiusX * radiusX);
      double zTerm = localZ * localZ / (radiusZ * radiusZ);
      return 1.0 - (xTerm + zTerm);
   }

   private static double smoothstep(double t) {
      return t * t * (3.0 - 2.0 * t);
   }

   public static long mix(long seed, int x, int z) {
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
}
