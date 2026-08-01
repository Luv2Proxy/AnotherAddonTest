package org.sathrek.sky_archipelago.worldgen.generator.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.worldgen.generator.field.BiomeTerrainShaper;

public final class SurfaceBlockSelector {
   private static final long UNDERWATER_BLEND_SALT = 6026299900829495313L;
   private static final TagKey<Biome> SURFACE_MUSHROOM = createTag("surface_mushroom");
   private static final TagKey<Biome> SURFACE_BADLANDS = createTag("surface_badlands");
   private static final TagKey<Biome> SURFACE_SAND = createTag("surface_sand");
   private static final TagKey<Biome> SURFACE_GRAVEL = createTag("surface_gravel");
   private static final TagKey<Biome> SURFACE_COARSE_DIRT = createTag("surface_coarse_dirt");
   private static final TagKey<Biome> SURFACE_SNOW = createTag("surface_snow");

   private SurfaceBlockSelector() {
   }

   public static SurfaceBlockSelector.SurfaceLayer forBiome(Holder<Biome> biome, BlockPos topPos) {
      return resolveProfile(biome, topPos).toSurfaceLayer();
   }

   public static SurfaceBlockSelector.SurfaceLayer forUnderwaterFloor(Holder<Biome> biome, BlockPos topPos) {
      return underwaterProfileForBiome(biome, topPos).toSurfaceLayer();
   }

   public static SurfaceBlockSelector.SurfaceProfile surfaceProfileForBiomeKey(ResourceKey<Biome> biomeKey, boolean coldEnoughToSnow) {
      if (biomeKey == Biomes.MUSHROOM_FIELDS) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.MYCELIUM, SurfaceBlockSelector.SurfaceMaterial.DIRT, SurfaceBlockSelector.SurfaceMaterial.DIRT, 5
         );
      } else if (biomeKey == Biomes.DESERT || biomeKey == Biomes.SNOWY_BEACH) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SANDSTONE, 4
         );
      } else if (biomeKey == Biomes.BADLANDS || biomeKey == Biomes.ERODED_BADLANDS || biomeKey == Biomes.WOODED_BADLANDS) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.RED_SAND,
            SurfaceBlockSelector.SurfaceMaterial.ORANGE_TERRACOTTA,
            SurfaceBlockSelector.SurfaceMaterial.TERRACOTTA,
            4
         );
      } else if (biomeKey == Biomes.STONY_SHORE
         || biomeKey == Biomes.STONY_PEAKS
         || biomeKey == Biomes.JAGGED_PEAKS
         || biomeKey == Biomes.WINDSWEPT_GRAVELLY_HILLS) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.GRAVEL, SurfaceBlockSelector.SurfaceMaterial.GRAVEL, SurfaceBlockSelector.SurfaceMaterial.STONE, 3
         );
      } else {
         return biomeKey != Biomes.SNOWY_SLOPES && biomeKey != Biomes.FROZEN_PEAKS && biomeKey != Biomes.GROVE && !coldEnoughToSnow
            ? new SurfaceBlockSelector.SurfaceProfile(
               SurfaceBlockSelector.SurfaceMaterial.GRASS_BLOCK, SurfaceBlockSelector.SurfaceMaterial.DIRT, SurfaceBlockSelector.SurfaceMaterial.STONE, 5
            )
            : new SurfaceBlockSelector.SurfaceProfile(
               SurfaceBlockSelector.SurfaceMaterial.SNOW_BLOCK, SurfaceBlockSelector.SurfaceMaterial.DIRT, SurfaceBlockSelector.SurfaceMaterial.STONE, 4
            );
      }
   }

   static SurfaceBlockSelector.SurfaceProfile underwaterProfileForBiomeKey(ResourceKey<Biome> biomeKey, boolean coldEnoughToSnow) {
      return underwaterProfileForWarmth(estimateWarmthForBiomeKey(biomeKey, coldEnoughToSnow), false);
   }

   private static SurfaceBlockSelector.SurfaceProfile resolveProfile(Holder<Biome> biome, BlockPos topPos) {
      boolean coldEnoughToSnow = ((Biome)biome.value()).coldEnoughToSnow(topPos.above());
      SurfaceBlockSelector.SurfaceProfile overrideProfile = profileFromOverrideTags(biome);
      if (overrideProfile != null) {
         return overrideProfile;
      }

      if (!BiomeTerrainShaper.isRiverLike(biome) && !BiomeTerrainShaper.isBeachLike(biome)) {
         SurfaceBlockSelector.SurfaceProfile taggedProfile = profileFromVanillaTags(biome);
         if (taggedProfile != null) {
            return taggedProfile;
         }

         SurfaceBlockSelector.SurfaceProfile heuristicProfile = profileFromHeuristics(biome, coldEnoughToSnow);
         return heuristicProfile != null
            ? heuristicProfile
            : new SurfaceBlockSelector.SurfaceProfile(
               SurfaceBlockSelector.SurfaceMaterial.GRASS_BLOCK, SurfaceBlockSelector.SurfaceMaterial.DIRT, SurfaceBlockSelector.SurfaceMaterial.STONE, 5
            );
      } else {
         return shorelineProfileForBiome(biome, topPos, coldEnoughToSnow);
      }
   }

   private static SurfaceBlockSelector.SurfaceProfile shorelineProfileForBiome(Holder<Biome> biome, BlockPos topPos, boolean coldEnoughToSnow) {
      boolean riverLike = BiomeTerrainShaper.isRiverLike(biome);
      boolean beachLike = BiomeTerrainShaper.isBeachLike(biome);
      boolean badlandsLike = biome.is(SURFACE_BADLANDS) || biome.is(BiomeTags.IS_BADLANDS);
      float baseWarmth = ((Biome)biome.value()).getBaseTemperature();
      double edgeNoise = blendNoiseAt(topPos.getX(), topPos.getZ());
      float warmed = Mth.clamp((float)(baseWarmth + edgeNoise * 0.2), -1.0F, 2.0F);
      float sandWeight = smoothstep(0.3F, 1.05F, warmed);
      if (beachLike) {
         sandWeight = Math.max(0.78F, sandWeight);
      }

      if (riverLike) {
         sandWeight *= 0.52F;
      }

      if (coldEnoughToSnow) {
         sandWeight *= 0.35F;
      }

      float bankNoise = (float)((valueNoise2D(6026299900829495408L, topPos.getX() * 0.055, topPos.getZ() * 0.055) + 1.0) * 0.5);
      if (riverLike) {
         sandWeight *= Mth.lerp(bankNoise, 0.62F, 1.05F);
      }

      sandWeight = Mth.clamp(sandWeight, 0.0F, 1.0F);
      float gravelWeight = 1.0F - sandWeight;
      float selectorTop = (float)((valueNoise2D(6026299900829495376L, topPos.getX() * 0.08, topPos.getZ() * 0.08) + 1.0) * 0.5);
      float selectorUnder = (float)((valueNoise2D(6026299900829495360L, topPos.getX() * 0.075, topPos.getZ() * 0.075) + 1.0) * 0.5);
      float selectorGrass = (float)((valueNoise2D(6026299900829495392L, topPos.getX() * 0.065, topPos.getZ() * 0.065) + 1.0) * 0.5);
      SurfaceBlockSelector.SurfaceMaterial top;
      if (badlandsLike && warmed > 1.2F && selectorTop > 0.72F) {
         top = SurfaceBlockSelector.SurfaceMaterial.RED_SAND;
      } else if (selectorTop < gravelWeight * (riverLike ? 0.75F : 0.6F)) {
         top = SurfaceBlockSelector.SurfaceMaterial.GRAVEL;
      } else if (selectorTop < gravelWeight + sandWeight * (riverLike ? 0.64F : 0.92F)) {
         top = SurfaceBlockSelector.SurfaceMaterial.SAND;
      } else {
         top = riverLike && selectorGrass > 0.33F ? SurfaceBlockSelector.SurfaceMaterial.GRASS_BLOCK : SurfaceBlockSelector.SurfaceMaterial.SAND;
      }

      SurfaceBlockSelector.SurfaceMaterial under;
      if (top == SurfaceBlockSelector.SurfaceMaterial.RED_SAND) {
         under = SurfaceBlockSelector.SurfaceMaterial.ORANGE_TERRACOTTA;
      } else if (top == SurfaceBlockSelector.SurfaceMaterial.GRASS_BLOCK) {
         under = selectorUnder < 0.35F ? SurfaceBlockSelector.SurfaceMaterial.DIRT : SurfaceBlockSelector.SurfaceMaterial.SAND;
      } else if (selectorUnder < Math.max(0.25F, gravelWeight)) {
         under = SurfaceBlockSelector.SurfaceMaterial.GRAVEL;
      } else {
         under = SurfaceBlockSelector.SurfaceMaterial.SAND;
      }
      SurfaceBlockSelector.SurfaceMaterial deep = switch (top) {
         case RED_SAND -> SurfaceBlockSelector.SurfaceMaterial.TERRACOTTA;
         case SAND -> SurfaceBlockSelector.SurfaceMaterial.SANDSTONE;
         case GRASS_BLOCK -> SurfaceBlockSelector.SurfaceMaterial.STONE;
         default -> SurfaceBlockSelector.SurfaceMaterial.STONE;
      };
      return new SurfaceBlockSelector.SurfaceProfile(top, under, deep, riverLike ? 4 : 4);
   }

   private static SurfaceBlockSelector.SurfaceProfile profileFromOverrideTags(Holder<Biome> biome) {
      if (biome.is(SURFACE_MUSHROOM)) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.MYCELIUM, SurfaceBlockSelector.SurfaceMaterial.DIRT, SurfaceBlockSelector.SurfaceMaterial.DIRT, 5
         );
      } else if (biome.is(SURFACE_BADLANDS)) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.RED_SAND,
            SurfaceBlockSelector.SurfaceMaterial.ORANGE_TERRACOTTA,
            SurfaceBlockSelector.SurfaceMaterial.TERRACOTTA,
            4
         );
      } else if (biome.is(SURFACE_SAND)) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SANDSTONE, 4
         );
      } else if (biome.is(SURFACE_GRAVEL)) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.GRAVEL, SurfaceBlockSelector.SurfaceMaterial.GRAVEL, SurfaceBlockSelector.SurfaceMaterial.STONE, 3
         );
      } else if (biome.is(SURFACE_COARSE_DIRT)) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.COARSE_DIRT, SurfaceBlockSelector.SurfaceMaterial.DIRT, SurfaceBlockSelector.SurfaceMaterial.STONE, 4
         );
      } else {
         return biome.is(SURFACE_SNOW)
            ? new SurfaceBlockSelector.SurfaceProfile(
               SurfaceBlockSelector.SurfaceMaterial.SNOW_BLOCK, SurfaceBlockSelector.SurfaceMaterial.DIRT, SurfaceBlockSelector.SurfaceMaterial.STONE, 4
            )
            : null;
      }
   }

   private static SurfaceBlockSelector.SurfaceProfile profileFromVanillaTags(Holder<Biome> biome) {
      if (biome.is(BiomeTags.IS_BADLANDS)) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.RED_SAND,
            SurfaceBlockSelector.SurfaceMaterial.ORANGE_TERRACOTTA,
            SurfaceBlockSelector.SurfaceMaterial.TERRACOTTA,
            4
         );
      } else if (biome.is(BiomeTags.IS_BEACH)) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SANDSTONE, 4
         );
      } else {
         return biome.is(BiomeTags.IS_SAVANNA)
            ? new SurfaceBlockSelector.SurfaceProfile(
               SurfaceBlockSelector.SurfaceMaterial.GRASS_BLOCK, SurfaceBlockSelector.SurfaceMaterial.DIRT, SurfaceBlockSelector.SurfaceMaterial.STONE, 5
            )
            : null;
      }
   }

   private static SurfaceBlockSelector.SurfaceProfile profileFromHeuristics(Holder<Biome> biome, boolean coldEnoughToSnow) {
      if (biome.is(Biomes.MUSHROOM_FIELDS)) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.MYCELIUM, SurfaceBlockSelector.SurfaceMaterial.DIRT, SurfaceBlockSelector.SurfaceMaterial.DIRT, 5
         );
      } else if (biome.is(Biomes.DESERT) || biome.is(Biomes.SNOWY_BEACH)) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SANDSTONE, 4
         );
      } else if (biome.is(Biomes.STONY_SHORE) || biome.is(Biomes.STONY_PEAKS) || biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.GRAVEL, SurfaceBlockSelector.SurfaceMaterial.GRAVEL, SurfaceBlockSelector.SurfaceMaterial.STONE, 3
         );
      } else {
         return !biome.is(Biomes.SNOWY_SLOPES) && !biome.is(Biomes.FROZEN_PEAKS) && !biome.is(Biomes.GROVE) && !coldEnoughToSnow
            ? null
            : new SurfaceBlockSelector.SurfaceProfile(
               SurfaceBlockSelector.SurfaceMaterial.SNOW_BLOCK, SurfaceBlockSelector.SurfaceMaterial.DIRT, SurfaceBlockSelector.SurfaceMaterial.STONE, 4
            );
      }
   }

   private static TagKey<Biome> createTag(String path) {
      return TagKey.create(Registries.BIOME, SkyArchipelago.id(path));
   }

   private static SurfaceBlockSelector.SurfaceProfile underwaterProfileForBiome(Holder<Biome> biome, BlockPos topPos) {
      boolean badlandsLike = biome.is(SURFACE_BADLANDS) || biome.is(BiomeTags.IS_BADLANDS);
      float baseWarmth = ((Biome)biome.value()).getBaseTemperature();
      double blendNoise = blendNoiseAt(topPos.getX(), topPos.getZ());
      float warmed = Mth.clamp((float)(baseWarmth + blendNoise * 0.22), -1.0F, 2.0F);
      return underwaterProfileForWarmthBlended(warmed, badlandsLike, topPos.getX(), topPos.getZ());
   }

   private static SurfaceBlockSelector.SurfaceProfile underwaterProfileForWarmth(float warmth, boolean badlandsLike) {
      if (warmth < 0.15F) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.GRAVEL, SurfaceBlockSelector.SurfaceMaterial.GRAVEL, SurfaceBlockSelector.SurfaceMaterial.STONE, 3
         );
      } else if (warmth < 0.45F) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.GRAVEL, SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.STONE, 3
         );
      } else if (warmth < 0.9F) {
         return new SurfaceBlockSelector.SurfaceProfile(
            SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SANDSTONE, 4
         );
      } else {
         return !badlandsLike && !(warmth > 1.35F)
            ? new SurfaceBlockSelector.SurfaceProfile(
               SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SAND, SurfaceBlockSelector.SurfaceMaterial.SANDSTONE, 4
            )
            : new SurfaceBlockSelector.SurfaceProfile(
               SurfaceBlockSelector.SurfaceMaterial.RED_SAND,
               SurfaceBlockSelector.SurfaceMaterial.ORANGE_TERRACOTTA,
               SurfaceBlockSelector.SurfaceMaterial.TERRACOTTA,
               4
            );
      }
   }

   private static SurfaceBlockSelector.SurfaceProfile underwaterProfileForWarmthBlended(float warmth, boolean badlandsLike, int x, int z) {
      float sandWeight = smoothstep(0.15F, 0.9F, warmth);
      float hotWeight = smoothstep(1.05F, 1.55F, warmth) * (badlandsLike ? 1.0F : 0.4F);
      hotWeight = Mth.clamp(hotWeight, 0.0F, 1.0F);
      float gravelWeight = (1.0F - sandWeight) * (1.0F - hotWeight);
      sandWeight *= 1.0F - hotWeight;
      float total = gravelWeight + sandWeight + hotWeight;
      if (total > 0.0F) {
         gravelWeight /= total;
         sandWeight /= total;
         hotWeight /= total;
      } else {
         gravelWeight = 1.0F;
         sandWeight = 0.0F;
         hotWeight = 0.0F;
      }

      float selectorTop = (float)((valueNoise2D(6026299900829495296L, x * 0.075, z * 0.075) + 1.0) * 0.5);
      float selectorUnder = (float)((valueNoise2D(6026299900829495344L, x * 0.07, z * 0.07) + 1.0) * 0.5);
      SurfaceBlockSelector.SurfaceMaterial top;
      if (selectorTop < gravelWeight) {
         top = SurfaceBlockSelector.SurfaceMaterial.GRAVEL;
      } else if (selectorTop < gravelWeight + sandWeight) {
         top = SurfaceBlockSelector.SurfaceMaterial.SAND;
      } else {
         top = SurfaceBlockSelector.SurfaceMaterial.RED_SAND;
      }

      SurfaceBlockSelector.SurfaceMaterial under;
      if (hotWeight > 0.3F && top == SurfaceBlockSelector.SurfaceMaterial.RED_SAND) {
         under = SurfaceBlockSelector.SurfaceMaterial.ORANGE_TERRACOTTA;
      } else if (selectorUnder < Math.max(0.25F, gravelWeight)) {
         under = SurfaceBlockSelector.SurfaceMaterial.GRAVEL;
      } else {
         under = SurfaceBlockSelector.SurfaceMaterial.SAND;
      }
      SurfaceBlockSelector.SurfaceMaterial deep = switch (top) {
         case RED_SAND -> SurfaceBlockSelector.SurfaceMaterial.TERRACOTTA;
         case SAND -> SurfaceBlockSelector.SurfaceMaterial.SANDSTONE;
         default -> SurfaceBlockSelector.SurfaceMaterial.STONE;
      };
      return new SurfaceBlockSelector.SurfaceProfile(top, under, deep, 4);
   }

   private static float estimateWarmthForBiomeKey(ResourceKey<Biome> biomeKey, boolean coldEnoughToSnow) {
      if (biomeKey == Biomes.BADLANDS || biomeKey == Biomes.ERODED_BADLANDS || biomeKey == Biomes.WOODED_BADLANDS) {
         return 1.6F;
      } else if (biomeKey == Biomes.DESERT) {
         return 2.0F;
      } else if (biomeKey == Biomes.SNOWY_BEACH || biomeKey == Biomes.FROZEN_PEAKS || biomeKey == Biomes.SNOWY_SLOPES || biomeKey == Biomes.GROVE) {
         return -0.4F;
      } else if (biomeKey == Biomes.STONY_SHORE
         || biomeKey == Biomes.STONY_PEAKS
         || biomeKey == Biomes.JAGGED_PEAKS
         || biomeKey == Biomes.WINDSWEPT_GRAVELLY_HILLS) {
         return 0.2F;
      } else {
         return coldEnoughToSnow ? -0.2F : 0.8F;
      }
   }

   private static double blendNoiseAt(int x, int z) {
      return valueNoise2D(6026299900829495313L, x * 0.021, z * 0.021);
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

   private static float smoothstep(float edge0, float edge1, float value) {
      if (value <= edge0) {
         return 0.0F;
      }

      if (value >= edge1) {
         return 1.0F;
      }

      float t = (value - edge0) / (edge1 - edge0);
      return t * t * (3.0F - 2.0F * t);
   }

   public record SurfaceLayer(BlockState top, BlockState under, BlockState deep, int soilDepth) {
      public BlockState exposedStateAtDepth(int depth) {
         if (depth <= 0) {
            return this.top;
         } else {
            return depth <= 2 ? this.under : this.deep;
         }
      }

      public BlockState requiredSurfaceStateAtDepth(int depth) {
         return this.requiredSurfaceMaterialAtDepth(depth).blockState();
      }

      public SurfaceBlockSelector.SurfaceMaterial requiredSurfaceMaterialAtDepth(int depth) {
         if (depth >= 3) {
            return SurfaceBlockSelector.SurfaceMaterial.materialFromBlockState(this.deep);
         }

         SurfaceBlockSelector.SurfaceMaterial preferred = SurfaceBlockSelector.SurfaceMaterial.materialFromBlockState(this.exposedStateAtDepth(depth));
         if (preferred != SurfaceBlockSelector.SurfaceMaterial.STONE) {
            return preferred;
         }

         SurfaceBlockSelector.SurfaceMaterial underMaterial = SurfaceBlockSelector.SurfaceMaterial.materialFromBlockState(this.under);
         if (underMaterial != SurfaceBlockSelector.SurfaceMaterial.STONE) {
            return underMaterial;
         }

         SurfaceBlockSelector.SurfaceMaterial topMaterial = SurfaceBlockSelector.SurfaceMaterial.materialFromBlockState(this.top);
         return topMaterial != SurfaceBlockSelector.SurfaceMaterial.STONE ? topMaterial : SurfaceBlockSelector.SurfaceMaterial.DIRT;
      }
   }

   public enum SurfaceMaterial {
      MYCELIUM,
      DIRT,
      RED_SAND,
      ORANGE_TERRACOTTA,
      TERRACOTTA,
      SAND,
      SANDSTONE,
      GRAVEL,
      COARSE_DIRT,
      SNOW_BLOCK,
      GRASS_BLOCK,
      STONE;

      public BlockState blockState() {
         return switch (this) {
            case MYCELIUM -> Blocks.MYCELIUM.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case RED_SAND -> Blocks.RED_SAND.defaultBlockState();
            case ORANGE_TERRACOTTA -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
            case TERRACOTTA -> Blocks.TERRACOTTA.defaultBlockState();
            case SAND -> Blocks.SAND.defaultBlockState();
            case SANDSTONE -> Blocks.SANDSTONE.defaultBlockState();
            case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case COARSE_DIRT -> Blocks.COARSE_DIRT.defaultBlockState();
            case SNOW_BLOCK -> Blocks.SNOW_BLOCK.defaultBlockState();
            case GRASS_BLOCK -> Blocks.GRASS_BLOCK.defaultBlockState();
            case STONE -> Blocks.STONE.defaultBlockState();
         };
      }

      public static SurfaceBlockSelector.SurfaceMaterial materialFromBlockState(BlockState state) {
         if (state.is(Blocks.MYCELIUM)) {
            return MYCELIUM;
         } else if (state.is(Blocks.DIRT)) {
            return DIRT;
         } else if (state.is(Blocks.RED_SAND)) {
            return RED_SAND;
         } else if (state.is(Blocks.ORANGE_TERRACOTTA)) {
            return ORANGE_TERRACOTTA;
         } else if (state.is(Blocks.TERRACOTTA)) {
            return TERRACOTTA;
         } else if (state.is(Blocks.SAND)) {
            return SAND;
         } else if (state.is(Blocks.SANDSTONE)) {
            return SANDSTONE;
         } else if (state.is(Blocks.GRAVEL)) {
            return GRAVEL;
         } else if (state.is(Blocks.COARSE_DIRT)) {
            return COARSE_DIRT;
         } else if (state.is(Blocks.SNOW_BLOCK)) {
            return SNOW_BLOCK;
         } else {
            return state.is(Blocks.GRASS_BLOCK) ? GRASS_BLOCK : STONE;
         }
      }
   }

   public record SurfaceProfile(
      SurfaceBlockSelector.SurfaceMaterial top, SurfaceBlockSelector.SurfaceMaterial under, SurfaceBlockSelector.SurfaceMaterial deep, int soilDepth
   ) {
      public SurfaceBlockSelector.SurfaceLayer toSurfaceLayer() {
         return new SurfaceBlockSelector.SurfaceLayer(this.top.blockState(), this.under.blockState(), this.deep.blockState(), this.soilDepth);
      }

      public SurfaceBlockSelector.SurfaceMaterial exposedMaterialAtDepth(int depth) {
         if (depth <= 0) {
            return this.top;
         } else {
            return depth <= 2 ? this.under : this.deep;
         }
      }
   }
}
