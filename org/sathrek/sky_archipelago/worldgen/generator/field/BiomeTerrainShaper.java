package org.sathrek.sky_archipelago.worldgen.generator.field;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public final class BiomeTerrainShaper {
   private static final BiomeTerrainShaper.TerrainProfile NEUTRAL = new BiomeTerrainShaper.TerrainProfile(0.55, 0.35, 0.2, 0.1, 1.0, 14);
   private static final BiomeTerrainShaper.TerrainProfile ALPINE = new BiomeTerrainShaper.TerrainProfile(1.45, 1.2, 0.34, 0.1, 0.88, 18);
   private static final BiomeTerrainShaper.TerrainProfile ROLLING = new BiomeTerrainShaper.TerrainProfile(0.82, 0.52, 0.26, 0.18, 1.0, 14);
   private static final BiomeTerrainShaper.TerrainProfile FLAT = new BiomeTerrainShaper.TerrainProfile(0.34, 0.18, 0.18, 0.4, 1.1, 13);
   private static final BiomeTerrainShaper.TerrainProfile MESA = new BiomeTerrainShaper.TerrainProfile(0.9, 0.74, 0.16, 0.28, 0.94, 15);
   private static final BiomeTerrainShaper.TerrainProfile CHANNELLED = new BiomeTerrainShaper.TerrainProfile(0.6, 0.3, 0.34, 0.56, 1.08, 14);

   private BiomeTerrainShaper() {
   }

   public static BiomeTerrainShaper.TerrainProfile neutral() {
      return NEUTRAL;
   }

   public static BiomeTerrainShaper.TerrainProfile flat() {
      return FLAT;
   }

   public static BiomeTerrainShaper.TerrainProfile channelled() {
      return CHANNELLED;
   }

   public static BiomeTerrainShaper.TerrainProfile profileFor(Holder<Biome> biome) {
      if (biome.is(BiomeTags.IS_BADLANDS)) {
         return MESA;
      } else if (isBeachLike(biome) || biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(Biomes.MEADOW)) {
         return FLAT;
      } else if (isRiverLike(biome) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
         return CHANNELLED;
      } else if (biome.is(Biomes.JAGGED_PEAKS)
         || biome.is(Biomes.FROZEN_PEAKS)
         || biome.is(Biomes.STONY_PEAKS)
         || biome.is(Biomes.SNOWY_SLOPES)
         || biome.is(Biomes.WINDSWEPT_HILLS)
         || biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)
         || biome.is(Biomes.WINDSWEPT_FOREST)
         || biome.is(Biomes.WINDSWEPT_SAVANNA)) {
         return ALPINE;
      } else {
         return !biome.is(Biomes.SAVANNA)
               && !biome.is(Biomes.SAVANNA_PLATEAU)
               && !biome.is(Biomes.FOREST)
               && !biome.is(Biomes.FLOWER_FOREST)
               && !biome.is(Biomes.BIRCH_FOREST)
               && !biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)
               && !biome.is(Biomes.DARK_FOREST)
               && !biome.is(Biomes.TAIGA)
               && !biome.is(Biomes.OLD_GROWTH_PINE_TAIGA)
               && !biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)
               && !biome.is(Biomes.JUNGLE)
               && !biome.is(Biomes.BAMBOO_JUNGLE)
               && !biome.is(Biomes.SPARSE_JUNGLE)
               && !biome.is(Biomes.CHERRY_GROVE)
               && !biome.is(Biomes.GROVE)
            ? NEUTRAL
            : ROLLING;
      }
   }

   public static boolean isBeachLike(Holder<Biome> biome) {
      return biome.is(BiomeTags.IS_BEACH) || biome.is(Biomes.DESERT) || biome.is(Biomes.SNOWY_BEACH);
   }

   public static boolean isRiverLike(Holder<Biome> biome) {
      return biome.is(Biomes.RIVER) || biome.is(Biomes.FROZEN_RIVER);
   }

   public static BiomeTerrainShaper.TerrainProfile lerp(BiomeTerrainShaper.TerrainProfile from, BiomeTerrainShaper.TerrainProfile to, double alpha) {
      double clamped = Mth.clamp(alpha, 0.0, 1.0);
      return new BiomeTerrainShaper.TerrainProfile(
         Mth.lerp(clamped, from.macroReliefScale(), to.macroReliefScale()),
         Mth.lerp(clamped, from.ridgeReliefScale(), to.ridgeReliefScale()),
         Mth.lerp(clamped, from.basinCarveScale(), to.basinCarveScale()),
         Mth.lerp(clamped, from.channelCarveScale(), to.channelCarveScale()),
         Mth.lerp(clamped, from.capProgressExponent(), to.capProgressExponent()),
         Math.max(1, (int)Math.round(Mth.lerp(clamped, from.minimumThickness(), to.minimumThickness())))
      );
   }

   public record TerrainProfile(
      double macroReliefScale, double ridgeReliefScale, double basinCarveScale, double channelCarveScale, double capProgressExponent, int minimumThickness
   ) {
   }
}
