package org.sathrek.sky_archipelago.config.settings;

import java.util.List;

public final class SkyIslandSettingLimits {
   public static final List<String> DEFAULT_WATER_STRUCTURE_CATEGORY_TOKENS = List.of("ocean", "water");
   public static final List<String> DEFAULT_SKY_STRUCTURE_CATEGORY_TOKENS = List.of("sky", "airship", "floating", "cloud", "aerial");
   public static final List<String> DEFAULT_GROUND_STRUCTURE_CATEGORY_TOKENS = List.of();
   public static final List<String> DEFAULT_UNDERGROUND_STRUCTURE_CATEGORY_TOKENS = List.of(
      "mineshaft", "trial_chambers", "ancient_city", "underground", "cave"
   );
   public static final List<String> DEFAULT_VILLAGE_STRUCTURE_CATEGORY_TOKENS = List.of("village");
   public static final List<String> DEFAULT_STRONGHOLD_STRUCTURE_CATEGORY_TOKENS = List.of("stronghold");
   public static final double MIN_ISLAND_DENSITY = 0.01;
   public static final double MAX_ISLAND_DENSITY = 1.0;
   public static final int MIN_ISLAND_RADIUS_LIMIT = 8;
   public static final int MAX_MIN_ISLAND_RADIUS_LIMIT = 500;
   public static final int MAX_ISLAND_RADIUS_LIMIT = 500;
   public static final int MIN_ISLAND_Y_LIMIT = -32;
   public static final int MAX_ISLAND_Y_LIMIT = 2000;
   public static final int MIN_ISLAND_THICKNESS_BLOCKS = 24;
   public static final int MAX_ISLAND_THICKNESS_BLOCKS = 1024;
   public static final int MIN_OCEAN_LEVEL_Y_LIMIT = -64;
   public static final int MAX_OCEAN_LEVEL_Y_LIMIT = 2000;
   public static final int MIN_OCEAN_FLOOR_BASE_OFFSET = 0;
   public static final int MAX_OCEAN_FLOOR_BASE_OFFSET = 2000;
   public static final int MIN_OCEAN_FLOOR_NOISE_AMPLITUDE = 0;
   public static final int MAX_OCEAN_FLOOR_NOISE_AMPLITUDE = 2000;
   public static final double MIN_OCEAN_FLOOR_NOISE_SCALE = 0.001;
   public static final double MAX_OCEAN_FLOOR_NOISE_SCALE = 1.0;
   public static final int MIN_OCEAN_FLOOR_DEPTH = 1;
   public static final int MAX_OCEAN_FLOOR_DEPTH = 2000;
   public static final double MIN_ARCHETYPE_WEIGHT = 0.0;
   public static final double MAX_ARCHETYPE_WEIGHT = 10.0;
   public static final double MIN_BAND_WEIGHT = 0.0;
   public static final double MAX_BAND_WEIGHT = 10.0;
   public static final int MIN_CLUSTER_SPACING = 32;
   public static final int MAX_CLUSTER_SPACING = 2000;
   public static final int MIN_DEEPSLATE_START_Y = -64;
   public static final int MAX_DEEPSLATE_START_Y = 2000;
   public static final int MIN_BIOME_PROFILE_BLEND_RADIUS_BLOCKS = 1;
   public static final int MAX_BIOME_PROFILE_BLEND_RADIUS_BLOCKS = 16;
   public static final int MIN_BIOME_PROFILE_BLEND_QUANTIZATION_STEPS = 2;
   public static final int MAX_BIOME_PROFILE_BLEND_QUANTIZATION_STEPS = 64;

   private SkyIslandSettingLimits() {
   }
}
