package org.sathrek.sky_archipelago.worldgen.generator.field;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

public final class BiomeTerrainProfileResolver {
   private final boolean blendingEnabled;
   private final int radiusBlocks;
   private final int quantizationSteps;
   private final boolean boundaryOnly;
   private final Function<BiomeTerrainProfileResolver.SamplePos, Holder<Biome>> biomeSampler;

   public BiomeTerrainProfileResolver(
      boolean blendingEnabled,
      int radiusBlocks,
      int quantizationSteps,
      boolean boundaryOnly,
      Function<BiomeTerrainProfileResolver.SamplePos, Holder<Biome>> biomeSampler
   ) {
      this.blendingEnabled = blendingEnabled;
      this.radiusBlocks = Math.max(1, radiusBlocks);
      this.quantizationSteps = Math.max(2, quantizationSteps);
      this.boundaryOnly = boundaryOnly;
      this.biomeSampler = biomeSampler;
   }

   public BiomeTerrainProfileResolver.Resolution resolve(int x, int y, int z) {
      Holder<Biome> centerBiome = this.biomeSampler.apply(new BiomeTerrainProfileResolver.SamplePos(x, y, z));
      BiomeTerrainShaper.TerrainProfile centerProfile = BiomeTerrainShaper.profileFor(centerBiome);
      BiomeTerrainProfileResolver.SpecialClass centerClass = classify(centerBiome);
      if (this.blendingEnabled && centerClass != BiomeTerrainProfileResolver.SpecialClass.NONE) {
         List<Holder<Biome>> neighbors = this.sampleNeighborhood(x, y, z);
         if (this.boundaryOnly && !isSpecialBoundaryColumn(centerClass, neighbors)) {
            return new BiomeTerrainProfileResolver.Resolution(centerProfile, centerClass, 1.0, false);
         }

         int sameClass = 0;
         int transitionClass = 0;
         BiomeTerrainShaper.TerrainProfile nonSpecialAccumulator = null;
         int nonSpecialCount = 0;

         for (Holder<Biome> neighbor : neighbors) {
            BiomeTerrainProfileResolver.SpecialClass neighborClass = classify(neighbor);
            if (neighborClass == centerClass) {
               sameClass++;
            } else if (neighborClass == BiomeTerrainProfileResolver.SpecialClass.NONE) {
               transitionClass++;
               BiomeTerrainShaper.TerrainProfile profile = BiomeTerrainShaper.profileFor(neighbor);
               nonSpecialAccumulator = nonSpecialAccumulator == null ? profile : this.average(nonSpecialAccumulator, profile, nonSpecialCount);
               nonSpecialCount++;
            }
         }

         if (transitionClass != 0 && nonSpecialAccumulator != null) {
            double localDominance = (double)sameClass / (sameClass + transitionClass);
            double quantized = this.quantize(localDominance);
            if (quantized >= 1.0) {
               return new BiomeTerrainProfileResolver.Resolution(centerProfile, centerClass, quantized, false);
            }

            BiomeTerrainShaper.TerrainProfile blended = BiomeTerrainShaper.lerp(nonSpecialAccumulator, centerProfile, quantized);
            return new BiomeTerrainProfileResolver.Resolution(blended, centerClass, quantized, true);
         } else {
            return new BiomeTerrainProfileResolver.Resolution(centerProfile, centerClass, 1.0, false);
         }
      } else {
         return new BiomeTerrainProfileResolver.Resolution(centerProfile, centerClass, 1.0, false);
      }
   }

   static boolean isSpecialBoundaryColumn(BiomeTerrainProfileResolver.SpecialClass centerClass, List<Holder<Biome>> neighbors) {
      if (centerClass == BiomeTerrainProfileResolver.SpecialClass.NONE) {
         return false;
      }

      for (Holder<Biome> neighbor : neighbors) {
         if (classify(neighbor) != centerClass) {
            return true;
         }
      }

      return false;
   }

   static BiomeTerrainProfileResolver.SpecialClass classify(Holder<Biome> biome) {
      if (BiomeTerrainShaper.isRiverLike(biome)) {
         return BiomeTerrainProfileResolver.SpecialClass.RIVER;
      } else {
         return BiomeTerrainShaper.isBeachLike(biome) ? BiomeTerrainProfileResolver.SpecialClass.BEACH : BiomeTerrainProfileResolver.SpecialClass.NONE;
      }
   }

   private List<Holder<Biome>> sampleNeighborhood(int x, int y, int z) {
      int r = this.radiusBlocks;
      int[][] offsets = new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {r, -r}, {-r, r}, {-r, -r}};
      List<Holder<Biome>> neighbors = new ArrayList<>(offsets.length);

      for (int[] offset : offsets) {
         neighbors.add(this.biomeSampler.apply(new BiomeTerrainProfileResolver.SamplePos(x + offset[0], y, z + offset[1])));
      }

      return neighbors;
   }

   private BiomeTerrainShaper.TerrainProfile average(BiomeTerrainShaper.TerrainProfile currentAverage, BiomeTerrainShaper.TerrainProfile next, int currentCount) {
      if (currentCount <= 0) {
         return next;
      }

      double alpha = 1.0 / (currentCount + 1.0);
      return BiomeTerrainShaper.lerp(currentAverage, next, alpha);
   }

   private double quantize(double value) {
      double clamped = Mth.clamp(value, 0.0, 1.0);
      int steps = this.quantizationSteps;
      double scaled = Math.round(clamped * steps);
      return scaled / steps;
   }

   public record Resolution(
      BiomeTerrainShaper.TerrainProfile terrainProfile, BiomeTerrainProfileResolver.SpecialClass specialClass, double blendFactor, boolean blended
   ) {
   }

   public record SamplePos(int x, int y, int z) {
   }

   public enum SpecialClass {
      NONE,
      BEACH,
      RIVER;
   }
}
