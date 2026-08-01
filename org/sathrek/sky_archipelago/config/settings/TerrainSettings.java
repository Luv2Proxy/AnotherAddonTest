package org.sathrek.sky_archipelago.config.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import org.sathrek.sky_archipelago.config.ClusterSpacingMode;
import org.sathrek.sky_archipelago.config.IslandSizeBand;
import org.sathrek.sky_archipelago.config.IslandSizeMode;

public record TerrainSettings(
   double islandDensity,
   int minIslandRadius,
   int maxIslandRadius,
   IslandSizeSettings islandSize,
   int minIslandY,
   int maxIslandY,
   int maxIslandThicknessBlocks,
   double lowBandWeight,
   double midHighBandWeight,
   double veryHighBandWeight,
   int lowBandCenterOffset,
   int veryHighBandCenterOffset,
   ClusterSpacingSettings spacing,
   double terrainReliefScale,
   ArchetypeSettings archetypes,
   OceanSettings ocean
) {
   private static final MapCodec<IslandSizeSettings> SIZE_BANDS_CODEC = new MapCodec<IslandSizeSettings>() {
      public <T> Stream<T> keys(DynamicOps<T> ops) {
         return Stream.of((T[])(new Object[]{ops.createString("island_size_bands"), ops.createString("size_bands")}));
      }

      public <T> DataResult<IslandSizeSettings> decode(DynamicOps<T> ops, MapLike<T> input) {
         T explicit = (T)input.get("island_size_bands");
         if (explicit != null) {
            return IslandSizeSettings.CODEC.parse(ops, explicit);
         }

         T legacy = (T)input.get("size_bands");
         return legacy != null ? IslandSizeSettings.CODEC.parse(ops, legacy) : DataResult.success(IslandSizeSettings.defaults());
      }

      public <T> RecordBuilder<T> encode(IslandSizeSettings input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
         return prefix.add("island_size_bands", IslandSizeSettings.CODEC.encodeStart(ops, input).getOrThrow());
      }
   };
   private static final MapCodec<OceanSettings> OCEAN_CODEC = new MapCodec<OceanSettings>() {
      public <T> Stream<T> keys(DynamicOps<T> ops) {
         return Stream.of(
            (T[])(new Object[]{
               ops.createString("ocean_enabled"),
               ops.createString("ocean_level_y"),
               ops.createString("ocean_floor_noise_enabled"),
               ops.createString("ocean_floor_base_offset"),
               ops.createString("ocean_floor_noise_amplitude"),
               ops.createString("ocean_floor_noise_scale"),
               ops.createString("ocean_floor_min_depth"),
               ops.createString("ocean_floor_max_depth")
            })
         );
      }

      public <T> DataResult<OceanSettings> decode(DynamicOps<T> ops, MapLike<T> input) {
         OceanSettings defaults = OceanSettings.defaults();
         boolean oceanEnabled = TerrainSettings.parseOrDefault(Codec.BOOL, ops, input.get("ocean_enabled"), defaults.oceanEnabled());
         int oceanLevelY = TerrainSettings.parseOrDefault(Codec.intRange(-64, 2000), ops, input.get("ocean_level_y"), defaults.oceanLevelY());
         boolean oceanFloorNoiseEnabled = TerrainSettings.parseOrDefault(
            Codec.BOOL, ops, input.get("ocean_floor_noise_enabled"), defaults.oceanFloorNoiseEnabled()
         );
         int oceanFloorBaseOffset = TerrainSettings.parseOrDefault(
            Codec.intRange(0, 2000), ops, input.get("ocean_floor_base_offset"), defaults.oceanFloorBaseOffset()
         );
         int oceanFloorNoiseAmplitude = TerrainSettings.parseOrDefault(
            Codec.intRange(0, 2000), ops, input.get("ocean_floor_noise_amplitude"), defaults.oceanFloorNoiseAmplitude()
         );
         double oceanFloorNoiseScale = TerrainSettings.parseOrDefault(
            Codec.doubleRange(0.001, 1.0), ops, input.get("ocean_floor_noise_scale"), defaults.oceanFloorNoiseScale()
         );
         int oceanFloorMinDepth = TerrainSettings.parseOrDefault(
            Codec.intRange(1, 2000), ops, input.get("ocean_floor_min_depth"), defaults.oceanFloorMinDepth()
         );
         int oceanFloorMaxDepth = TerrainSettings.parseOrDefault(
            Codec.intRange(1, 2000), ops, input.get("ocean_floor_max_depth"), defaults.oceanFloorMaxDepth()
         );
         return DataResult.success(
            new OceanSettings(
               oceanEnabled,
               oceanLevelY,
               oceanFloorNoiseEnabled,
               oceanFloorBaseOffset,
               oceanFloorNoiseAmplitude,
               oceanFloorNoiseScale,
               oceanFloorMinDepth,
               oceanFloorMaxDepth
            )
         );
      }

      public <T> RecordBuilder<T> encode(OceanSettings input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
         return prefix.add("ocean_enabled", Codec.BOOL.encodeStart(ops, input.oceanEnabled()).getOrThrow())
            .add("ocean_level_y", Codec.INT.encodeStart(ops, input.oceanLevelY()).getOrThrow())
            .add("ocean_floor_noise_enabled", Codec.BOOL.encodeStart(ops, input.oceanFloorNoiseEnabled()).getOrThrow())
            .add("ocean_floor_base_offset", Codec.INT.encodeStart(ops, input.oceanFloorBaseOffset()).getOrThrow())
            .add("ocean_floor_noise_amplitude", Codec.INT.encodeStart(ops, input.oceanFloorNoiseAmplitude()).getOrThrow())
            .add("ocean_floor_noise_scale", Codec.DOUBLE.encodeStart(ops, input.oceanFloorNoiseScale()).getOrThrow())
            .add("ocean_floor_min_depth", Codec.INT.encodeStart(ops, input.oceanFloorMinDepth()).getOrThrow())
            .add("ocean_floor_max_depth", Codec.INT.encodeStart(ops, input.oceanFloorMaxDepth()).getOrThrow());
      }
   };
   private static final MapCodec<ClusterSpacingSettings> CLUSTER_SPACING_CODEC = new MapCodec<ClusterSpacingSettings>() {
      public <T> Stream<T> keys(DynamicOps<T> ops) {
         return Stream.of(
            (T[])(new Object[]{
               ops.createString("cluster_spacing_mode"),
               ops.createString("cluster_spacing"),
               ops.createString("min_cluster_spacing"),
               ops.createString("max_cluster_spacing")
            })
         );
      }

      public <T> DataResult<ClusterSpacingSettings> decode(DynamicOps<T> ops, MapLike<T> input) {
         ClusterSpacingSettings defaults = ClusterSpacingSettings.defaults();
         ClusterSpacingMode mode = TerrainSettings.parseOrDefault(
            ClusterSpacingMode.CODEC, ops, input.get("cluster_spacing_mode"), defaults.clusterSpacingMode()
         );
         int spacing = TerrainSettings.parseOrDefault(Codec.intRange(32, 2000), ops, input.get("cluster_spacing"), defaults.clusterSpacing());
         int minSpacing = TerrainSettings.parseOrDefault(Codec.intRange(32, 2000), ops, input.get("min_cluster_spacing"), spacing);
         int maxSpacing = TerrainSettings.parseOrDefault(Codec.intRange(32, 2000), ops, input.get("max_cluster_spacing"), spacing);
         return DataResult.success(new ClusterSpacingSettings(mode, spacing, Math.min(minSpacing, maxSpacing), Math.max(minSpacing, maxSpacing)));
      }

      public <T> RecordBuilder<T> encode(ClusterSpacingSettings input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
         return prefix.add("cluster_spacing_mode", ClusterSpacingMode.CODEC.encodeStart(ops, input.clusterSpacingMode()).getOrThrow())
            .add("cluster_spacing", Codec.INT.encodeStart(ops, input.clusterSpacing()).getOrThrow())
            .add("min_cluster_spacing", Codec.INT.encodeStart(ops, input.minClusterSpacing()).getOrThrow())
            .add("max_cluster_spacing", Codec.INT.encodeStart(ops, input.maxClusterSpacing()).getOrThrow());
      }
   };
   public static final Codec<TerrainSettings> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.doubleRange(0.01, 1.0).fieldOf("island_density").forGetter(TerrainSettings::islandDensity),
            Codec.intRange(8, 500).fieldOf("min_island_radius").forGetter(TerrainSettings::minIslandRadius),
            Codec.intRange(8, 500).fieldOf("max_island_radius").forGetter(TerrainSettings::maxIslandRadius),
            SIZE_BANDS_CODEC.forGetter(TerrainSettings::islandSize),
            Codec.intRange(-32, 2000).fieldOf("min_island_y").forGetter(TerrainSettings::minIslandY),
            Codec.intRange(-32, 2000).fieldOf("max_island_y").forGetter(TerrainSettings::maxIslandY),
            Codec.intRange(24, 1024).optionalFieldOf("max_island_thickness_blocks", 140).forGetter(TerrainSettings::maxIslandThicknessBlocks),
            Codec.doubleRange(0.0, 10.0).fieldOf("low_band_weight").forGetter(TerrainSettings::lowBandWeight),
            Codec.doubleRange(0.0, 10.0).fieldOf("mid_high_band_weight").forGetter(TerrainSettings::midHighBandWeight),
            Codec.doubleRange(0.0, 10.0).fieldOf("very_high_band_weight").forGetter(TerrainSettings::veryHighBandWeight),
            Codec.intRange(-96, 96).fieldOf("low_band_center_offset").forGetter(TerrainSettings::lowBandCenterOffset),
            Codec.intRange(-96, 96).fieldOf("very_high_band_center_offset").forGetter(TerrainSettings::veryHighBandCenterOffset),
            CLUSTER_SPACING_CODEC.forGetter(TerrainSettings::spacing),
            Codec.doubleRange(0.0, 40.0).fieldOf("terrain_relief_scale").forGetter(TerrainSettings::terrainReliefScale),
            ArchetypeSettings.CODEC.optionalFieldOf("archetypes", ArchetypeSettings.defaults()).forGetter(TerrainSettings::archetypes),
            OCEAN_CODEC.forGetter(TerrainSettings::ocean)
         )
         .apply(instance, TerrainSettings::new)
   );

   public TerrainSettings {
      if (maxIslandRadius < minIslandRadius) {
         throw new IllegalArgumentException("maxIslandRadius must be >= minIslandRadius");
      }

      islandSize = islandSize.resolve(minIslandRadius, maxIslandRadius);
      validateBandRange(islandSize.smallIslandSizeBand(), "small");
      validateBandRange(islandSize.mediumIslandSizeBand(), "medium");
      validateBandRange(islandSize.largeIslandSizeBand(), "large");
      if (islandSize.islandSizeMode() == IslandSizeMode.SPECIFIC) {
         double totalWeight = islandSize.smallIslandSizeBand().weight()
            + islandSize.mediumIslandSizeBand().weight()
            + islandSize.largeIslandSizeBand().weight();
         if (Math.abs(totalWeight - 1.0) > 0.001) {
            throw new IllegalArgumentException("specific size band weights must total 1.0");
         }
      }

      if (maxIslandY < minIslandY) {
         throw new IllegalArgumentException("maxIslandY must be >= minIslandY");
      }

      if (maxIslandThicknessBlocks >= 24 && maxIslandThicknessBlocks <= 1024) {
         if (lowBandWeight + midHighBandWeight + veryHighBandWeight <= 0.0) {
            throw new IllegalArgumentException("at least one height band weight must be > 0");
         }

         if (spacing == null) {
            throw new IllegalArgumentException("spacing cannot be null");
         }

         if (archetypes == null) {
            throw new IllegalArgumentException("archetypes cannot be null");
         }

         if (ocean == null) {
            throw new IllegalArgumentException("ocean cannot be null");
         }
      } else {
         throw new IllegalArgumentException("maxIslandThicknessBlocks must be between 24 and 1024");
      }
   }

   public int maxIslandThickness() {
      return this.maxIslandThicknessBlocks;
   }

   public int spawnSearchTopY() {
      return Math.min(2000, this.maxIslandY + this.maxIslandThickness() + 16);
   }

   private static <T, A> A parseOrDefault(Codec<A> codec, DynamicOps<T> ops, T value, A defaultValue) {
      return value == null ? defaultValue : codec.parse(ops, value).result().orElse(defaultValue);
   }

   private static void validateBandRange(IslandSizeBand band, String bandName) {
      if (band.minRadius() < 8 || band.maxRadius() > 500) {
         throw new IllegalArgumentException(bandName + " size band radius is outside allowed limits");
      }
   }
}
