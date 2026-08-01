package org.sathrek.sky_archipelago.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.config.ModConfigEvent.Unloading;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
import org.sathrek.sky_archipelago.config.settings.IslandSizeSettings;
import org.sathrek.sky_archipelago.config.settings.SkyIslandSettingLimits;
import org.sathrek.sky_archipelago.config.settings.SkyIslandSettingsFactory;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.StructureWhitelist;

@EventBusSubscriber(modid = "sky_archipelago", bus = Bus.MOD)
public final class SkyIslandConfig {
   private static final String SKY_ISLANDS_WORLD_PRESET_RESOURCE = "data/sky_archipelago/worldgen/world_preset/sky_islands.json";
   private static final double DEFAULT_ISLAND_DENSITY = 0.4;
   private static final int DEFAULT_MIN_ISLAND_RADIUS = 24;
   private static final int DEFAULT_MAX_ISLAND_RADIUS = 75;
   private static final IslandSizeMode DEFAULT_ISLAND_SIZE_MODE = IslandSizeMode.RANDOM;
   private static final IslandSizeBand DEFAULT_SMALL_ISLAND_SIZE_BAND = IslandSizeSettings.deriveSmallBand(24, 75);
   private static final IslandSizeBand DEFAULT_MEDIUM_ISLAND_SIZE_BAND = IslandSizeSettings.deriveMediumBand(24, 75);
   private static final IslandSizeBand DEFAULT_LARGE_ISLAND_SIZE_BAND = IslandSizeSettings.deriveLargeBand(24, 75);
   private static final int DEFAULT_MIN_ISLAND_Y = 20;
   private static final int DEFAULT_MAX_ISLAND_Y = 170;
   private static final int DEFAULT_MAX_ISLAND_THICKNESS_BLOCKS = 140;
   private static final double DEFAULT_LOW_BAND_WEIGHT = 0.15;
   private static final double DEFAULT_MID_HIGH_BAND_WEIGHT = 0.75;
   private static final double DEFAULT_VERY_HIGH_BAND_WEIGHT = 0.1;
   private static final int DEFAULT_LOW_BAND_CENTER_OFFSET = -18;
   private static final int DEFAULT_VERY_HIGH_BAND_CENTER_OFFSET = 12;
   private static final ClusterSpacingMode DEFAULT_CLUSTER_SPACING_MODE = ClusterSpacingMode.CONSISTENT;
   private static final int DEFAULT_CLUSTER_SPACING = 96;
   private static final int DEFAULT_MIN_CLUSTER_SPACING = 96;
   private static final int DEFAULT_MAX_CLUSTER_SPACING = 96;
   private static final double DEFAULT_TERRAIN_RELIEF_SCALE = 1.0;
   private static final boolean DEFAULT_CLASSIC_ARCHETYPE_ENABLED = true;
   private static final double DEFAULT_CLASSIC_ARCHETYPE_WEIGHT = 1.0;
   private static final boolean DEFAULT_BOWL_CRATER_ARCHETYPE_ENABLED = true;
   private static final double DEFAULT_BOWL_CRATER_ARCHETYPE_WEIGHT = 1.0;
   private static final boolean DEFAULT_CRESCENT_ARCHETYPE_ENABLED = true;
   private static final double DEFAULT_CRESCENT_ARCHETYPE_WEIGHT = 1.0;
   private static final boolean DEFAULT_TERRACE_ARCHETYPE_ENABLED = true;
   private static final double DEFAULT_TERRACE_ARCHETYPE_WEIGHT = 1.0;
   private static final boolean DEFAULT_OCEAN_ENABLED = false;
   private static final int DEFAULT_OCEAN_LEVEL_Y = 0;
   private static final boolean DEFAULT_OCEAN_FLOOR_NOISE_ENABLED = true;
   private static final int DEFAULT_OCEAN_FLOOR_BASE_OFFSET = 18;
   private static final int DEFAULT_OCEAN_FLOOR_NOISE_AMPLITUDE = 10;
   private static final double DEFAULT_OCEAN_FLOOR_NOISE_SCALE = 0.05;
   private static final int DEFAULT_OCEAN_FLOOR_MIN_DEPTH = 8;
   private static final int DEFAULT_OCEAN_FLOOR_MAX_DEPTH = 40;
   private static final OceanBlockType DEFAULT_OCEAN_BLOCK_TYPE = OceanBlockType.WATER;
   private static final int DEFAULT_SUPPORT_CHECK_DEPTH = 48;
   private static final int DEFAULT_SUPPORT_SAMPLE_GRID_SIZE = 5;
   private static final double DEFAULT_SUPPORT_THRESHOLD = 0.6;
   private static final double DEFAULT_SURFACE_SKY_SUPPORT_THRESHOLD = 0.42;
   private static final double DEFAULT_SMALL_SKY_SUPPORT_THRESHOLD = 0.32;
   private static final double DEFAULT_SURFACE_SKY_FOOTPRINT_INSET_RATIO = 0.18;
   private static final double DEFAULT_SMALL_SKY_FOOTPRINT_INSET_RATIO = 0.28;
   private static final int DEFAULT_SURFACE_SKY_SEARCH_RADIUS_CHUNKS = 3;
   private static final int DEFAULT_SMALL_SKY_SEARCH_RADIUS_CHUNKS = 8;
   private static final int DEFAULT_SURFACE_SKY_MIN_STABLE_TOP_CELLS = 12;
   private static final int DEFAULT_SMALL_SKY_MIN_STABLE_TOP_CELLS = 5;
   private static final int DEFAULT_SURFACE_SKY_TOP_OFFSET = 1;
   private static final int DEFAULT_SMALL_SKY_TOP_OFFSET = 1;
   private static final int DEFAULT_SURFACE_SKY_LOCAL_SEARCH_STEP_BLOCKS = 8;
   private static final int DEFAULT_SMALL_SKY_LOCAL_SEARCH_STEP_BLOCKS = 4;
   private static final int DEFAULT_SURFACE_SKY_LOCAL_SEARCH_RADIUS_BLOCKS = 24;
   private static final int DEFAULT_SMALL_SKY_LOCAL_SEARCH_RADIUS_BLOCKS = 14;
   private static final double DEFAULT_SURFACE_SKY_GROUNDED_SAMPLE_THRESHOLD = 0.7;
   private static final double DEFAULT_SMALL_SKY_GROUNDED_SAMPLE_THRESHOLD = 0.75;
   private static final int DEFAULT_SURFACE_SKY_MAX_GROUND_GAP_BLOCKS = 1;
   private static final int DEFAULT_SMALL_SKY_MAX_GROUND_GAP_BLOCKS = 1;
   private static final int DEFAULT_SURFACE_SKY_MIN_HOST_ISLAND_RADIUS = 26;
   private static final int DEFAULT_SMALL_SKY_MIN_HOST_ISLAND_RADIUS = 22;
   private static final int DEFAULT_SURFACE_SKY_MIN_HOST_STABLE_TOP_CELLS = 10;
   private static final int DEFAULT_SMALL_SKY_MIN_HOST_STABLE_TOP_CELLS = 7;
   private static final boolean DEFAULT_DEFAULT_NEAR_MISS_FALLBACK = false;
   private static final boolean DEFAULT_CUSTOM_STRUCTURE_RULES_ENABLED = true;
   private static final boolean DEFAULT_CLUSTER_COMPANION_ISLANDS_ENABLED = true;
   private static final boolean DEFAULT_ANCHOR_FRAGMENTATION_ENABLED = true;
   private static final boolean DEFAULT_DISABLE_ISLANDS_OVER_OCEAN_BIOMES = false;
   private static final int DEFAULT_DEEPSLATE_START_Y = 16;
   private static final boolean DEFAULT_BIOME_PROFILE_BLENDING_ENABLED = true;
   private static final int DEFAULT_BIOME_PROFILE_BLENDING_RADIUS_BLOCKS = 4;
   private static final int DEFAULT_BIOME_PROFILE_BLENDING_QUANTIZATION_STEPS = 8;
   private static final boolean DEFAULT_BIOME_PROFILE_BLENDING_BOUNDARY_ONLY = true;
   private static final TerrainOverlapMode DEFAULT_TERRAIN_OVERLAP_MODE = TerrainOverlapMode.VOID;
   private static final boolean DEFAULT_STRONGHOLD_HOST_ISLAND_ENABLED = true;
   private static final int DEFAULT_STRONGHOLD_HOST_ISLAND_CLOSEST_N = 8;
   private static final int DEFAULT_STRONGHOLD_HOST_ISLAND_RADIUS_MIN = 192;
   private static final int DEFAULT_STRONGHOLD_HOST_ISLAND_RADIUS_MAX = 256;
   private static final List<String> DEFAULT_STRUCTURE_WHITELIST = List.of(
      "shellbound_for_airship:airship_ship1",
      "shellbound_for_airship:airship_ship2",
      "shellbound_for_airship:airship_ship3",
      "shellbound_for_airship:airship_ship4",
      "shellbound_for_airship:airship_ship5",
      "shellbound_for_airship:airship_ship6",
      "shellbound_for_airship:airship_ship7",
      "shellbound_for_airship:airship_ship8",
      "shellbound_for_airship:airship_ship9",
      "shellbound_for_airship:airship_ship10",
      "sky_whale_ship:whale",
      "sky_whale_ship:whaleship",
      "sky_whale_ship:whalelight",
      "sky_whale_ship:whalearena",
      "mostructures:the_castle_in_the_sky",
      "create_structures_arise:pillagersteampunkairship"
   );
   private static final Builder BUILDER = new Builder();
   private static final List<String> DEFAULT_STRUCTURE_CATEGORY_OVERRIDES = List.of(
      "minecraft:pillager_outpost=MEDIUM_GROUND",
      "minecraft:ruined_portal_desert=SMALL_GROUND",
      "valhelsia_structures:desert_house=SMALL_GROUND",
      "repurposed_structures:outpost_oak=MEDIUM_GROUND",
      "valhelsia_structures:player_house=SMALL_GROUND",
      "valhelsia_structures:forge=SMALL_GROUND",
      "valhelsia_structures:tower_ruin=SMALL_GROUND",
      "valhelsia_structures:big_tree=SMALL_GROUND",
      "mostructures:villager_tower=MEDIUM_GROUND",
      "mostructures:villager_market=MEDIUM_GROUND",
      "mvs:large_warped_tower=LARGE_GROUND"
   );
   private static final List<String> DEFAULT_SKY_STRUCTURE_CATEGORY_TOKENS = List.of("sky", "airship", "floating", "cloud", "aerial");
   private static final List<String> DEFAULT_WATER_STRUCTURE_CATEGORY_TOKENS = SkyIslandSettingLimits.DEFAULT_WATER_STRUCTURE_CATEGORY_TOKENS;
   private static final List<String> DEFAULT_GROUND_STRUCTURE_CATEGORY_TOKENS = SkyIslandSettingLimits.DEFAULT_GROUND_STRUCTURE_CATEGORY_TOKENS;
   private static final List<String> DEFAULT_UNDERGROUND_STRUCTURE_CATEGORY_TOKENS = SkyIslandSettingLimits.DEFAULT_UNDERGROUND_STRUCTURE_CATEGORY_TOKENS;
   private static final List<String> DEFAULT_VILLAGE_STRUCTURE_CATEGORY_TOKENS = SkyIslandSettingLimits.DEFAULT_VILLAGE_STRUCTURE_CATEGORY_TOKENS;
   private static final List<String> DEFAULT_STRONGHOLD_STRUCTURE_CATEGORY_TOKENS = SkyIslandSettingLimits.DEFAULT_STRONGHOLD_STRUCTURE_CATEGORY_TOKENS;
   private static final DoubleValue ISLAND_DENSITY = BUILDER.comment(
         "Chance that a cluster cell becomes an island anchor. Lower values create larger sky gaps."
      )
      .defineInRange("islandDensity", 0.4, 0.01, 1.0);
   private static final IntValue MIN_ISLAND_RADIUS = BUILDER.comment("Minimum horizontal island radius in blocks.")
      .defineInRange("minIslandRadius", 24, 8, 128);
   private static final IntValue MAX_ISLAND_RADIUS = BUILDER.comment("Maximum horizontal island radius in blocks.")
      .defineInRange("maxIslandRadius", 75, 8, 500);
   private static final EnumValue<IslandSizeMode> ISLAND_SIZE_MODE = BUILDER.comment(
         "How island radius sizes are selected. RANDOM preserves global min/max behavior, SPECIFIC uses small/medium/large bands."
      )
      .defineEnum("islandSizeMode", DEFAULT_ISLAND_SIZE_MODE);
   private static final IntValue SMALL_ISLAND_MIN_RADIUS = BUILDER.comment("Specific mode: minimum radius for small islands.")
      .defineInRange("smallIslandMinRadius", DEFAULT_SMALL_ISLAND_SIZE_BAND.minRadius(), 8, 500);
   private static final IntValue SMALL_ISLAND_MAX_RADIUS = BUILDER.comment("Specific mode: maximum radius for small islands.")
      .defineInRange("smallIslandMaxRadius", DEFAULT_SMALL_ISLAND_SIZE_BAND.maxRadius(), 8, 500);
   private static final DoubleValue SMALL_ISLAND_WEIGHT = BUILDER.comment("Specific mode: spawn weight for small islands.")
      .defineInRange("smallIslandWeight", DEFAULT_SMALL_ISLAND_SIZE_BAND.weight(), 0.0, 1.0);
   private static final IntValue MEDIUM_ISLAND_MIN_RADIUS = BUILDER.comment("Specific mode: minimum radius for medium islands.")
      .defineInRange("mediumIslandMinRadius", DEFAULT_MEDIUM_ISLAND_SIZE_BAND.minRadius(), 8, 500);
   private static final IntValue MEDIUM_ISLAND_MAX_RADIUS = BUILDER.comment("Specific mode: maximum radius for medium islands.")
      .defineInRange("mediumIslandMaxRadius", DEFAULT_MEDIUM_ISLAND_SIZE_BAND.maxRadius(), 8, 500);
   private static final DoubleValue MEDIUM_ISLAND_WEIGHT = BUILDER.comment("Specific mode: spawn weight for medium islands.")
      .defineInRange("mediumIslandWeight", DEFAULT_MEDIUM_ISLAND_SIZE_BAND.weight(), 0.0, 1.0);
   private static final IntValue LARGE_ISLAND_MIN_RADIUS = BUILDER.comment("Specific mode: minimum radius for large islands.")
      .defineInRange("largeIslandMinRadius", DEFAULT_LARGE_ISLAND_SIZE_BAND.minRadius(), 8, 500);
   private static final IntValue LARGE_ISLAND_MAX_RADIUS = BUILDER.comment("Specific mode: maximum radius for large islands.")
      .defineInRange("largeIslandMaxRadius", DEFAULT_LARGE_ISLAND_SIZE_BAND.maxRadius(), 8, 500);
   private static final DoubleValue LARGE_ISLAND_WEIGHT = BUILDER.comment("Specific mode: spawn weight for large islands.")
      .defineInRange("largeIslandWeight", DEFAULT_LARGE_ISLAND_SIZE_BAND.weight(), 0.0, 1.0);
   private static final IntValue MIN_ISLAND_Y = BUILDER.comment("Minimum Y for island centers. Values above 320 require world-height expansion mods/settings.")
      .defineInRange("minIslandY", 20, -32, 2000);
   private static final IntValue MAX_ISLAND_Y = BUILDER.comment("Maximum Y for island centers. Values above 320 require world-height expansion mods/settings.")
      .defineInRange("maxIslandY", 170, -16, 2000);
   private static final IntValue MAX_ISLAND_THICKNESS_BLOCKS = BUILDER.comment(
         "Maximum vertical island thickness in blocks. Prevents giant islands from becoming too tall/deep."
      )
      .defineInRange("maxIslandThicknessBlocks", 140, 24, 1024);
   private static final DoubleValue LOW_BAND_WEIGHT = BUILDER.comment("Relative weight of the uncommon low-island height band.")
      .defineInRange("lowBandWeight", 0.15, 0.0, 10.0);
   private static final DoubleValue MID_HIGH_BAND_WEIGHT = BUILDER.comment("Relative weight of the dominant mid/high island height band.")
      .defineInRange("midHighBandWeight", 0.75, 0.0, 10.0);
   private static final DoubleValue VERY_HIGH_BAND_WEIGHT = BUILDER.comment("Relative weight of the rare very-high island height band.")
      .defineInRange("veryHighBandWeight", 0.1, 0.0, 10.0);
   private static final IntValue LOW_BAND_CENTER_OFFSET = BUILDER.comment(
         "Center offset applied to the low-island band within the configured vertical envelope."
      )
      .defineInRange("lowBandCenterOffset", -18, -96, 96);
   private static final IntValue VERY_HIGH_BAND_CENTER_OFFSET = BUILDER.comment(
         "Center offset applied to the very-high island band within the configured vertical envelope."
      )
      .defineInRange("veryHighBandCenterOffset", 12, -96, 96);
   private static final IntValue CLUSTER_SPACING = BUILDER.comment("Average spacing between island clusters in blocks.")
      .defineInRange("clusterSpacing", 96, 32, 2000);
   private static final EnumValue<ClusterSpacingMode> CLUSTER_SPACING_MODE = BUILDER.comment(
         "Cluster spacing mode. CONSISTENT uses fixed spacing. DYNAMIC picks deterministic per-world spacing from min/max."
      )
      .defineEnum("clusterSpacingMode", DEFAULT_CLUSTER_SPACING_MODE);
   private static final IntValue MIN_CLUSTER_SPACING = BUILDER.comment("Dynamic spacing mode: minimum cluster spacing in blocks.")
      .defineInRange("minClusterSpacing", 96, 32, 2000);
   private static final IntValue MAX_CLUSTER_SPACING = BUILDER.comment("Dynamic spacing mode: maximum cluster spacing in blocks.")
      .defineInRange("maxClusterSpacing", 96, 32, 2000);
   private static final DoubleValue TERRAIN_RELIEF_SCALE = BUILDER.comment(
         "Multiplier for island top-surface height variation. Higher values create rougher, less flat tops."
      )
      .defineInRange("terrainReliefScale", 1.0, 0.0, 40.0);
   private static final BooleanValue CLASSIC_ARCHETYPE_ENABLED = BUILDER.comment("Whether the classic island archetype is allowed to spawn.")
      .define("classicArchetypeEnabled", true);
   private static final DoubleValue CLASSIC_ARCHETYPE_WEIGHT = BUILDER.comment("Relative spawn weight for the classic island archetype.")
      .defineInRange("classicArchetypeWeight", 1.0, 0.0, 10.0);
   private static final BooleanValue BOWL_CRATER_ARCHETYPE_ENABLED = BUILDER.comment("Whether the bowl/crater island archetype is allowed to spawn.")
      .define("bowlCraterArchetypeEnabled", true);
   private static final DoubleValue BOWL_CRATER_ARCHETYPE_WEIGHT = BUILDER.comment("Relative spawn weight for the bowl/crater island archetype.")
      .defineInRange("bowlCraterArchetypeWeight", 1.0, 0.0, 10.0);
   private static final BooleanValue CRESCENT_ARCHETYPE_ENABLED = BUILDER.comment("Whether the crescent island archetype is allowed to spawn.")
      .define("crescentArchetypeEnabled", true);
   private static final DoubleValue CRESCENT_ARCHETYPE_WEIGHT = BUILDER.comment("Relative spawn weight for the crescent island archetype.")
      .defineInRange("crescentArchetypeWeight", 1.0, 0.0, 10.0);
   private static final BooleanValue TERRACE_ARCHETYPE_ENABLED = BUILDER.comment("Whether the terrace island archetype is allowed to spawn.")
      .define("terraceArchetypeEnabled", true);
   private static final DoubleValue TERRACE_ARCHETYPE_WEIGHT = BUILDER.comment("Relative spawn weight for the terrace island archetype.")
      .defineInRange("terraceArchetypeWeight", 1.0, 0.0, 10.0);
   private static final BooleanValue OCEAN_ENABLED = BUILDER.comment("Whether to fill an optional ocean layer below the sky islands.")
      .define("oceanEnabled", false);
   private static final IntValue OCEAN_LEVEL_Y = BUILDER.comment(
         "Maximum Y level filled by the optional ocean layer when ocean generation is enabled. Values above 320 require world-height expansion mods/settings."
      )
      .defineInRange("oceanLevelY", 0, -64, 2000);
   private static final BooleanValue OCEAN_FLOOR_NOISE_ENABLED = BUILDER.comment(
         "Whether to shape an ocean floor beneath the ocean surface using deterministic noise."
      )
      .define("oceanFloorNoiseEnabled", true);
   private static final IntValue OCEAN_FLOOR_BASE_OFFSET = BUILDER.comment("Base depth below ocean level for the ocean floor shape.")
      .defineInRange("oceanFloorBaseOffset", 18, 0, 2000);
   private static final IntValue OCEAN_FLOOR_NOISE_AMPLITUDE = BUILDER.comment("Vertical amplitude of ocean floor noise in blocks.")
      .defineInRange("oceanFloorNoiseAmplitude", 10, 0, 2000);
   private static final DoubleValue OCEAN_FLOOR_NOISE_SCALE = BUILDER.comment("Horizontal scale of ocean floor noise. Lower values make broader features.")
      .defineInRange("oceanFloorNoiseScale", 0.05, 0.001, 1.0);
   private static final IntValue OCEAN_FLOOR_MIN_DEPTH = BUILDER.comment("Minimum depth below ocean level for ocean floor clamping.")
      .defineInRange("oceanFloorMinDepth", 8, 1, 2000);
   private static final IntValue OCEAN_FLOOR_MAX_DEPTH = BUILDER.comment("Maximum depth below ocean level for ocean floor clamping.")
      .defineInRange("oceanFloorMaxDepth", 40, 1, 2000);
   private static final ConfigValue<String> OCEAN_BLOCK_TYPE = BUILDER.comment("Fluid block used by the optional ocean layer. Accepted values: water, lava.")
      .define("oceanBlockType", DEFAULT_OCEAN_BLOCK_TYPE.serializedName(), value -> value instanceof String);
   private static final IntValue SUPPORT_CHECK_DEPTH = BUILDER.comment("How far below a structure footprint to scan for support.")
      .defineInRange("supportCheckDepth", 48, 1, 256);
   private static final IntValue SUPPORT_SAMPLE_GRID_SIZE = BUILDER.comment("Grid size used to sample support across the structure footprint.")
      .defineInRange("supportSampleGridSize", 5, 1, 15);
   private static final DoubleValue SUPPORT_THRESHOLD = BUILDER.comment("Required fraction of supported samples for a structure to be accepted.")
      .defineInRange("supportThreshold", 0.6, 0.0, 1.0);
   private static final DoubleValue SURFACE_SKY_SUPPORT_THRESHOLD = BUILDER.comment("Required fraction of supported samples for surface-sky structures.")
      .defineInRange("surfaceSkySupportThreshold", 0.42, 0.0, 1.0);
   private static final DoubleValue SMALL_SKY_SUPPORT_THRESHOLD = BUILDER.comment("Required fraction of supported samples for small sky structures.")
      .defineInRange("smallSkySupportThreshold", 0.32, 0.0, 1.0);
   private static final DoubleValue SURFACE_SKY_FOOTPRINT_INSET_RATIO = BUILDER.comment(
         "Inset ratio used to shrink support footprints for larger sky-surface structures."
      )
      .defineInRange("surfaceSkyFootprintInsetRatio", 0.18, 0.0, 0.49);
   private static final DoubleValue SMALL_SKY_FOOTPRINT_INSET_RATIO = BUILDER.comment(
         "Inset ratio used to shrink support footprints for smaller sky structures."
      )
      .defineInRange("smallSkyFootprintInsetRatio", 0.28, 0.0, 0.49);
   private static final IntValue SURFACE_SKY_SEARCH_RADIUS_CHUNKS = BUILDER.comment(
         "How many chunks outward to search for an island-top candidate for larger sky-surface structures."
      )
      .defineInRange("surfaceSkySearchRadiusChunks", 3, 0, 12);
   private static final IntValue SMALL_SKY_SEARCH_RADIUS_CHUNKS = BUILDER.comment(
         "How many chunks outward to search for an island-top candidate for smaller sky structures."
      )
      .defineInRange("smallSkySearchRadiusChunks", 8, 0, 12);
   private static final IntValue SURFACE_SKY_MIN_STABLE_TOP_CELLS = BUILDER.comment(
         "Minimum number of stable top-surface samples required for a larger sky-surface structure candidate."
      )
      .defineInRange("surfaceSkyMinStableTopCells", 12, 1, 64);
   private static final IntValue SMALL_SKY_MIN_STABLE_TOP_CELLS = BUILDER.comment(
         "Minimum number of stable top-surface samples required for a smaller sky structure candidate."
      )
      .defineInRange("smallSkyMinStableTopCells", 5, 1, 64);
   private static final IntValue SURFACE_SKY_TOP_OFFSET = BUILDER.comment(
         "Vertical offset applied when aligning larger sky-surface structures to an island top."
      )
      .defineInRange("surfaceSkyTopOffset", 1, -16, 32);
   private static final IntValue SMALL_SKY_TOP_OFFSET = BUILDER.comment("Vertical offset applied when aligning smaller sky structures to an island top.")
      .defineInRange("smallSkyTopOffset", 1, -16, 32);
   private static final IntValue SURFACE_SKY_LOCAL_SEARCH_STEP_BLOCKS = BUILDER.comment(
         "Block step size used when sweeping local placement sites for larger sky-surface structures."
      )
      .defineInRange("surfaceSkyLocalSearchStepBlocks", 8, 1, 32);
   private static final IntValue SMALL_SKY_LOCAL_SEARCH_STEP_BLOCKS = BUILDER.comment(
         "Block step size used when sweeping local placement sites for smaller sky structures."
      )
      .defineInRange("smallSkyLocalSearchStepBlocks", 4, 1, 32);
   private static final IntValue SURFACE_SKY_LOCAL_SEARCH_RADIUS_BLOCKS = BUILDER.comment(
         "Maximum local offset radius in blocks tested across an island top for larger sky-surface structures."
      )
      .defineInRange("surfaceSkyLocalSearchRadiusBlocks", 24, 0, 96);
   private static final IntValue SMALL_SKY_LOCAL_SEARCH_RADIUS_BLOCKS = BUILDER.comment(
         "Maximum local offset radius in blocks tested across an island top for smaller sky structures."
      )
      .defineInRange("smallSkyLocalSearchRadiusBlocks", 14, 0, 96);
   private static final DoubleValue SURFACE_SKY_GROUNDED_SAMPLE_THRESHOLD = BUILDER.comment(
         "Required grounded-base sample ratio for larger sky-surface structures after relocation."
      )
      .defineInRange("surfaceSkyGroundedSampleThreshold", 0.7, 0.0, 1.0);
   private static final DoubleValue SMALL_SKY_GROUNDED_SAMPLE_THRESHOLD = BUILDER.comment(
         "Required grounded-base sample ratio for smaller sky structures after relocation."
      )
      .defineInRange("smallSkyGroundedSampleThreshold", 0.75, 0.0, 1.0);
   private static final IntValue SURFACE_SKY_MAX_GROUND_GAP_BLOCKS = BUILDER.comment(
         "Maximum gap in blocks allowed between the relocated structure base and solid terrain for larger sky-surface structures."
      )
      .defineInRange("surfaceSkyMaxGroundGapBlocks", 1, 0, 8);
   private static final IntValue SMALL_SKY_MAX_GROUND_GAP_BLOCKS = BUILDER.comment(
         "Maximum gap in blocks allowed between the relocated structure base and solid terrain for smaller sky structures."
      )
      .defineInRange("smallSkyMaxGroundGapBlocks", 1, 0, 8);
   private static final IntValue SURFACE_SKY_MIN_HOST_ISLAND_RADIUS = BUILDER.comment(
         "Minimum island preview radius required before a larger sky-surface structure can consider an island as a grounded host."
      )
      .defineInRange("surfaceSkyMinHostIslandRadius", 26, 0, 128);
   private static final IntValue SMALL_SKY_MIN_HOST_ISLAND_RADIUS = BUILDER.comment(
         "Minimum island preview radius required before a smaller sky structure can consider an island as a grounded host."
      )
      .defineInRange("smallSkyMinHostIslandRadius", 22, 0, 128);
   private static final IntValue SURFACE_SKY_MIN_HOST_STABLE_TOP_CELLS = BUILDER.comment(
         "Minimum stable top-cell count required before a larger sky-surface structure considers an island a qualified grounded host."
      )
      .defineInRange("surfaceSkyMinHostStableTopCells", 10, 1, 64);
   private static final IntValue SMALL_SKY_MIN_HOST_STABLE_TOP_CELLS = BUILDER.comment(
         "Minimum stable top-cell count required before a smaller sky structure considers an island a qualified grounded host."
      )
      .defineInRange("smallSkyMinHostStableTopCells", 7, 1, 64);
   private static final BooleanValue DEFAULT_NEAR_MISS_FALLBACK = BUILDER.comment(
         "Reserved toggle for future near-miss promotion of uncategorized surface structures. Disabled by default."
      )
      .define("defaultNearMissFallbackEnabled", false);
   private static final BooleanValue CUSTOM_STRUCTURE_RULES_ENABLED = BUILDER.comment(
         "Whether Sky Archipelago custom structure placement/support rules are enabled."
      )
      .define("customStructureRulesEnabled", true);
   private static final BooleanValue CLUSTER_COMPANION_ISLANDS_ENABLED = BUILDER.comment(
         "Whether satellite/spire companion islands generate around anchor islands."
      )
      .define("clusterCompanionIslandsEnabled", true);
   private static final BooleanValue ANCHOR_FRAGMENTATION_ENABLED = BUILDER.comment(
         "Whether anchor islands use extra fragmentation features that create small outcroppings/debris."
      )
      .define("anchorFragmentationEnabled", true);
   private static final BooleanValue DISABLE_ISLANDS_OVER_OCEAN_BIOMES = BUILDER.comment(
         "Whether island clusters should be skipped when sampled biome is ocean."
      )
      .define("disableIslandsOverOceanBiomes", false);
   private static final IntValue DEEPSLATE_START_Y = BUILDER.comment(
         "Island interior below or at this Y uses deepslate substrate. Values above 320 require world-height expansion mods/settings."
      )
      .defineInRange("deepslateStartY", 16, -64, 2000);
   private static final BooleanValue BIOME_PROFILE_BLENDING_ENABLED = BUILDER.comment(
         "Whether river/beach terrain profiles blend into neighboring non-river/beach profiles."
      )
      .define("biomeProfileBlendingEnabled", true);
   private static final IntValue BIOME_PROFILE_BLENDING_RADIUS_BLOCKS = BUILDER.comment("Neighborhood sample radius in blocks used by biome profile blending.")
      .defineInRange("biomeProfileBlendingRadiusBlocks", 4, 1, 16);
   private static final IntValue BIOME_PROFILE_BLENDING_QUANTIZATION_STEPS = BUILDER.comment(
         "Blend quantization steps. Higher values are smoother but slightly more variant."
      )
      .defineInRange("biomeProfileBlendingQuantizationSteps", 8, 2, 64);
   private static final BooleanValue BIOME_PROFILE_BLENDING_BOUNDARY_ONLY = BUILDER.comment(
         "If true, extra blending samples run only near river/beach boundaries."
      )
      .define("biomeProfileBlendingBoundaryOnly", true);
   private static final EnumValue<TerrainOverlapMode> TERRAIN_OVERLAP_MODE = BUILDER.comment("How overlapping island terrain columns are resolved.")
      .defineEnum("terrainOverlapMode", DEFAULT_TERRAIN_OVERLAP_MODE);
   private static final BooleanValue STRONGHOLD_HOST_ISLAND_ENABLED = BUILDER.comment(
         "Whether strongholds can retry placement by injecting a temporary host island after initial support failure."
      )
      .define("strongholdHostIslandEnabled", true);
   private static final IntValue STRONGHOLD_HOST_ISLAND_CLOSEST_N = BUILDER.comment(
         "How many nearest strongholds to origin are eligible for host-island retry."
      )
      .defineInRange("strongholdHostIslandClosestN", 8, 1, 128);
   private static final IntValue STRONGHOLD_HOST_ISLAND_RADIUS_MIN = BUILDER.comment("Minimum radius for injected stronghold host islands.")
      .defineInRange("strongholdHostIslandRadiusMin", 192, 8, 256);
   private static final IntValue STRONGHOLD_HOST_ISLAND_RADIUS_MAX = BUILDER.comment("Maximum radius for injected stronghold host islands.")
      .defineInRange("strongholdHostIslandRadiusMax", 256, 8, 256);
   private static final ConfigValue<List<? extends String>> SKY_STRUCTURE_WHITELIST = BUILDER.comment(
         new String[]{
            "Structures that bypass normal support/grounding validation and are committed with minimal checks.",
            "Whitelisted structures still require nearby-island presence via the whitelist void guard.",
            "Examples: modid:sky_temple, modid:airship_wreck, modid:floating_platform"
         }
      )
      .defineListAllowEmpty(
         "skyStructureWhitelist", DEFAULT_STRUCTURE_WHITELIST, value -> value instanceof String stringValue && StructureWhitelist.isValidEntry(stringValue)
      );
   private static final ConfigValue<List<? extends String>> SKY_STRUCTURE_DENYLIST = BUILDER.comment(
         new String[]{
            "Structures to skip entirely before placement validation.", "Leave empty by default so future special handling can still be added later."
         }
      )
      .defineListAllowEmpty("skyStructureDenylist", List.of(), value -> value instanceof String stringValue && StructureWhitelist.isValidEntry(stringValue));
   private static final ConfigValue<List<? extends String>> SKY_STRUCTURE_CATEGORY_OVERRIDES = BUILDER.comment(
         new String[]{
            "Optional per-structure placement categories in the form modid:structure=CATEGORY.",
            "Preferred categories: DEFAULT, SKY, SMALL_GROUND, MEDIUM_GROUND, LARGE_GROUND, GROUND_VILLAGE, STRONGHOLD, UNDERGROUND, WATER.",
            "Legacy aliases still supported: SMALL_SKY, SURFACE_SKY, HAMLET_SKY."
         }
      )
      .defineListAllowEmpty(
         "skyStructureCategoryOverrides",
         DEFAULT_STRUCTURE_CATEGORY_OVERRIDES,
         value -> value instanceof String stringValue && StructurePlacementPolicy.isValidCategoryEntry(stringValue)
      );
   private static final ConfigValue<List<? extends String>> WATER_STRUCTURE_CATEGORY_TOKENS = BUILDER.comment(
         new String[]{
            "Category inference order when no explicit override is present: SKY, GROUND_VILLAGE, STRONGHOLD, UNDERGROUND, WATER, then MEDIUM_GROUND.",
            "Keep broad MEDIUM_GROUND tokens empty unless you need them; normal uncategorized structures still use fallback/default placement.",
            "Case-insensitive path tokens used to infer WATER category when no explicit override is present."
         }
      )
      .defineListAllowEmpty("waterStructureCategoryTokens", DEFAULT_WATER_STRUCTURE_CATEGORY_TOKENS, SkyIslandConfig::isValidCategoryToken);
   private static final ConfigValue<List<? extends String>> SKY_STRUCTURE_CATEGORY_TOKENS = BUILDER.comment(
         "Case-insensitive path tokens used to infer SKY category for floating structures when no explicit override is present."
      )
      .defineListAllowEmpty("skyStructureCategoryTokens", DEFAULT_SKY_STRUCTURE_CATEGORY_TOKENS, SkyIslandConfig::isValidCategoryToken);
   private static final ConfigValue<List<? extends String>> GROUND_STRUCTURE_CATEGORY_TOKENS = BUILDER.comment(
         "Case-insensitive path tokens used to infer MEDIUM_GROUND category when no explicit override is present."
      )
      .defineListAllowEmpty("groundStructureCategoryTokens", DEFAULT_GROUND_STRUCTURE_CATEGORY_TOKENS, SkyIslandConfig::isValidCategoryToken);
   private static final ConfigValue<List<? extends String>> UNDERGROUND_STRUCTURE_CATEGORY_TOKENS = BUILDER.comment(
         "Case-insensitive path tokens used to infer UNDERGROUND category when no explicit override is present."
      )
      .defineListAllowEmpty("undergroundStructureCategoryTokens", DEFAULT_UNDERGROUND_STRUCTURE_CATEGORY_TOKENS, SkyIslandConfig::isValidCategoryToken);
   private static final ConfigValue<List<? extends String>> VILLAGE_STRUCTURE_CATEGORY_TOKENS = BUILDER.comment(
         "Case-insensitive path tokens used to infer GROUND_VILLAGE category when no explicit override is present."
      )
      .defineListAllowEmpty("villageStructureCategoryTokens", DEFAULT_VILLAGE_STRUCTURE_CATEGORY_TOKENS, SkyIslandConfig::isValidCategoryToken);
   private static final ConfigValue<List<? extends String>> STRONGHOLD_STRUCTURE_CATEGORY_TOKENS = BUILDER.comment(
         "Case-insensitive path tokens used to infer STRONGHOLD category when no explicit override is present."
      )
      .defineListAllowEmpty("strongholdStructureCategoryTokens", DEFAULT_STRONGHOLD_STRUCTURE_CATEGORY_TOKENS, SkyIslandConfig::isValidCategoryToken);
   public static final ModConfigSpec SPEC = BUILDER.build();
   private static final AtomicReference<SkyIslandGeneratorSettings> CURRENT_GENERATOR = new AtomicReference<>(defaultGeneratorSettings());
   private static final AtomicReference<SkyIslandSettings> CURRENT = new AtomicReference<>(defaultSettings());
   private static final Optional<SkyIslandGeneratorSettings> STOCK_EMBEDDED_WORLD_PRESET_SETTINGS = loadStockEmbeddedWorldPresetSettings();

   private SkyIslandConfig() {
   }

   public static SkyIslandSettings current() {
      return CURRENT.get();
   }

   public static SkyIslandGeneratorSettings currentGeneratorSettings() {
      return CURRENT_GENERATOR.get();
   }

   public static SkyIslandSettings resolveForGenerator(Optional<SkyIslandGeneratorSettings> embeddedSettings) {
      return resolveForGenerator(embeddedSettings, current(), isDedicatedServerRuntime(), SkyIslandConfig::resolveFileBackedDefaultGeneratorSettings);
   }

   static SkyIslandSettings resolveForGenerator(
      Optional<SkyIslandGeneratorSettings> embeddedSettings, SkyIslandSettings commonSettings, boolean dedicatedServer
   ) {
      return resolveForGenerator(embeddedSettings, commonSettings, dedicatedServer, SkyIslandConfig::resolveFileBackedDefaultGeneratorSettings);
   }

   static SkyIslandSettings resolveForGenerator(
      Optional<SkyIslandGeneratorSettings> embeddedSettings,
      SkyIslandSettings commonSettings,
      boolean dedicatedServer,
      Supplier<Optional<SkyIslandGeneratorSettings>> defaultPresetSupplier
   ) {
      SkyIslandSettings defaultPresetRuntime = defaultPresetSupplier.get().orElseGet(SkyIslandConfig::defaultGeneratorSettings).toRuntimeSettings();
      if (dedicatedServer) {
         refreshCommonRuntimeFromSpecIfNeeded();
         commonSettings = current();
         if (commonSettings != null && !isBaselineRuntimeSettings(commonSettings)) {
            return commonSettings;
         } else {
            return embeddedSettings.isPresent() && !isStockEmbeddedSettings(embeddedSettings.get())
               ? embeddedSettings.get().toRuntimeSettings()
               : defaultPresetRuntime;
         }
      } else {
         return embeddedSettings.isPresent() && !isStockEmbeddedSettings(embeddedSettings.get())
            ? embeddedSettings.get().toRuntimeSettings()
            : defaultPresetRuntime;
      }
   }

   private static boolean isDedicatedServerRuntime() {
      return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
   }

   private static boolean isValidCategoryToken(Object value) {
      return value instanceof String stringValue && !stringValue.trim().isEmpty();
   }

   private static Optional<SkyIslandGeneratorSettings> resolveFileBackedDefaultGeneratorSettings() {
      return SkyIslandDefaultPresetStore.atDefaultLocation().resolveDefaultSettings(SkyIslandSavedPresetRepository.atDefaultLocation());
   }

   private static boolean isBaselineSettings(SkyIslandGeneratorSettings settings) {
      return defaultGeneratorSettings().equals(settings);
   }

   public static boolean isStockEmbeddedSettings(SkyIslandGeneratorSettings settings) {
      if (settings == null) {
         return false;
      } else {
         return isBaselineSettings(settings) ? true : STOCK_EMBEDDED_WORLD_PRESET_SETTINGS.map(settings::equals).orElse(false);
      }
   }

   private static boolean isBaselineRuntimeSettings(SkyIslandSettings settings) {
      return settings != null && defaultSettings().equals(settings);
   }

   private static Optional<SkyIslandGeneratorSettings> loadStockEmbeddedWorldPresetSettings() {
      ClassLoader classLoader = SkyIslandConfig.class.getClassLoader();

      try (InputStream inputStream = classLoader.getResourceAsStream("data/sky_archipelago/worldgen/world_preset/sky_islands.json")) {
         if (inputStream == null) {
            return Optional.empty();
         }

         String rawPreset = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
         JsonElement parsed = JsonParser.parseString(rawPreset);
         if (!parsed.isJsonObject()) {
            return Optional.empty();
         }

         JsonObject root = parsed.getAsJsonObject();
         if (!root.has("dimensions")) {
            return Optional.empty();
         }

         JsonObject dimensions = root.getAsJsonObject("dimensions");
         if (dimensions != null && dimensions.has("minecraft:overworld")) {
            JsonObject overworld = dimensions.getAsJsonObject("minecraft:overworld");
            if (overworld != null && overworld.has("generator")) {
               JsonObject generator = overworld.getAsJsonObject("generator");
               if (generator != null && generator.has("sky_island_settings")) {
                  JsonElement settingsElement = generator.get("sky_island_settings");
                  SkyIslandPresetJson.DecodeResult decoded = SkyIslandPresetJson.decodeSettings(settingsElement.toString());
                  return !decoded.success() ? Optional.empty() : Optional.of(decoded.settings());
               } else {
                  return Optional.empty();
               }
            } else {
               return Optional.empty();
            }
         } else {
            return Optional.empty();
         }
      } catch (Exception exception) {
         return Optional.empty();
      }
   }

   public static SkyIslandGeneratorSettings defaultGeneratorSettings() {
      return SkyIslandSettingsFactory.generator(
         0.4,
         24,
         75,
         DEFAULT_ISLAND_SIZE_MODE,
         DEFAULT_SMALL_ISLAND_SIZE_BAND,
         DEFAULT_MEDIUM_ISLAND_SIZE_BAND,
         DEFAULT_LARGE_ISLAND_SIZE_BAND,
         20,
         170,
         140,
         0.15,
         0.75,
         0.1,
         -18,
         12,
         DEFAULT_CLUSTER_SPACING_MODE,
         96,
         96,
         96,
         1.0,
         true,
         1.0,
         true,
         1.0,
         true,
         1.0,
         true,
         1.0,
         false,
         0,
         true,
         18,
         10,
         0.05,
         8,
         40,
         DEFAULT_OCEAN_BLOCK_TYPE,
         48,
         5,
         0.6,
         0.42,
         0.32,
         0.18,
         0.28,
         3,
         8,
         12,
         5,
         1,
         1,
         8,
         4,
         24,
         14,
         0.7,
         0.75,
         1,
         1,
         26,
         22,
         10,
         7,
         false,
         true,
         true,
         true,
         false,
         16,
         true,
         4,
         8,
         true,
         TerrainOverlapMode.VOID,
         DEFAULT_STRUCTURE_WHITELIST,
         List.of(),
         DEFAULT_STRUCTURE_CATEGORY_OVERRIDES,
         DEFAULT_WATER_STRUCTURE_CATEGORY_TOKENS,
         DEFAULT_SKY_STRUCTURE_CATEGORY_TOKENS,
         DEFAULT_GROUND_STRUCTURE_CATEGORY_TOKENS,
         DEFAULT_UNDERGROUND_STRUCTURE_CATEGORY_TOKENS,
         DEFAULT_VILLAGE_STRUCTURE_CATEGORY_TOKENS,
         DEFAULT_STRONGHOLD_STRUCTURE_CATEGORY_TOKENS
      );
   }

   @SubscribeEvent
   static void onConfigLoad(ModConfigEvent event) {
      if (event.getConfig().getSpec() == SPEC) {
         if (!(event instanceof Unloading)) {
            SkyIslandGeneratorSettings generatorSettings = loadGeneratorSettingsFromConfig();
            CURRENT_GENERATOR.set(generatorSettings);
            CURRENT.set(generatorSettings.toRuntimeSettings());
         }
      }
   }

   private static void refreshCommonRuntimeFromSpecIfNeeded() {
      SkyIslandSettings existing = CURRENT.get();
      if (existing == null || isBaselineRuntimeSettings(existing)) {
         SkyIslandGeneratorSettings generatorSettings = loadGeneratorSettingsFromConfig();
         CURRENT_GENERATOR.set(generatorSettings);
         CURRENT.set(generatorSettings.toRuntimeSettings());
      }
   }

   private static SkyIslandSettings defaultSettings() {
      return defaultGeneratorSettings().toRuntimeSettings();
   }

   private static SkyIslandGeneratorSettings loadGeneratorSettingsFromConfig() {
      int minRadius = (Integer)MIN_ISLAND_RADIUS.get();
      int maxRadius = Math.max(minRadius, (Integer)MAX_ISLAND_RADIUS.get());
      IslandSizeBand defaultSmall = IslandSizeSettings.deriveSmallBand(minRadius, maxRadius);
      IslandSizeBand defaultMedium = IslandSizeSettings.deriveMediumBand(minRadius, maxRadius);
      IslandSizeBand defaultLarge = IslandSizeSettings.deriveLargeBand(minRadius, maxRadius);
      IslandSizeBand smallBand = sanitizeBand(
         (Integer)SMALL_ISLAND_MIN_RADIUS.get(), (Integer)SMALL_ISLAND_MAX_RADIUS.get(), (Double)SMALL_ISLAND_WEIGHT.get(), minRadius, maxRadius, defaultSmall
      );
      IslandSizeBand mediumBand = sanitizeBand(
         (Integer)MEDIUM_ISLAND_MIN_RADIUS.get(),
         (Integer)MEDIUM_ISLAND_MAX_RADIUS.get(),
         (Double)MEDIUM_ISLAND_WEIGHT.get(),
         minRadius,
         maxRadius,
         defaultMedium
      );
      IslandSizeBand largeBand = sanitizeBand(
         (Integer)LARGE_ISLAND_MIN_RADIUS.get(), (Integer)LARGE_ISLAND_MAX_RADIUS.get(), (Double)LARGE_ISLAND_WEIGHT.get(), minRadius, maxRadius, defaultLarge
      );
      int minY = (Integer)MIN_ISLAND_Y.get();
      int maxY = Math.max(minY, (Integer)MAX_ISLAND_Y.get());
      int maxThickness = (Integer)MAX_ISLAND_THICKNESS_BLOCKS.get();
      return SkyIslandSettingsFactory.generator(
         (Double)ISLAND_DENSITY.get(),
         minRadius,
         maxRadius,
         (IslandSizeMode)ISLAND_SIZE_MODE.get(),
         smallBand,
         mediumBand,
         largeBand,
         minY,
         maxY,
         maxThickness,
         (Double)LOW_BAND_WEIGHT.get(),
         (Double)MID_HIGH_BAND_WEIGHT.get(),
         (Double)VERY_HIGH_BAND_WEIGHT.get(),
         (Integer)LOW_BAND_CENTER_OFFSET.get(),
         (Integer)VERY_HIGH_BAND_CENTER_OFFSET.get(),
         (ClusterSpacingMode)CLUSTER_SPACING_MODE.get(),
         (Integer)CLUSTER_SPACING.get(),
         Math.min((Integer)MIN_CLUSTER_SPACING.get(), (Integer)MAX_CLUSTER_SPACING.get()),
         Math.max((Integer)MIN_CLUSTER_SPACING.get(), (Integer)MAX_CLUSTER_SPACING.get()),
         (Double)TERRAIN_RELIEF_SCALE.get(),
         (Boolean)CLASSIC_ARCHETYPE_ENABLED.get(),
         (Double)CLASSIC_ARCHETYPE_WEIGHT.get(),
         (Boolean)BOWL_CRATER_ARCHETYPE_ENABLED.get(),
         (Double)BOWL_CRATER_ARCHETYPE_WEIGHT.get(),
         (Boolean)CRESCENT_ARCHETYPE_ENABLED.get(),
         (Double)CRESCENT_ARCHETYPE_WEIGHT.get(),
         (Boolean)TERRACE_ARCHETYPE_ENABLED.get(),
         (Double)TERRACE_ARCHETYPE_WEIGHT.get(),
         (Boolean)OCEAN_ENABLED.get(),
         (Integer)OCEAN_LEVEL_Y.get(),
         (Boolean)OCEAN_FLOOR_NOISE_ENABLED.get(),
         (Integer)OCEAN_FLOOR_BASE_OFFSET.get(),
         (Integer)OCEAN_FLOOR_NOISE_AMPLITUDE.get(),
         (Double)OCEAN_FLOOR_NOISE_SCALE.get(),
         (Integer)OCEAN_FLOOR_MIN_DEPTH.get(),
         (Integer)OCEAN_FLOOR_MAX_DEPTH.get(),
         OceanBlockType.fromSerializedName((String)OCEAN_BLOCK_TYPE.get()),
         (Integer)SUPPORT_CHECK_DEPTH.get(),
         (Integer)SUPPORT_SAMPLE_GRID_SIZE.get(),
         (Double)SUPPORT_THRESHOLD.get(),
         (Double)SURFACE_SKY_SUPPORT_THRESHOLD.get(),
         (Double)SMALL_SKY_SUPPORT_THRESHOLD.get(),
         (Double)SURFACE_SKY_FOOTPRINT_INSET_RATIO.get(),
         (Double)SMALL_SKY_FOOTPRINT_INSET_RATIO.get(),
         (Integer)SURFACE_SKY_SEARCH_RADIUS_CHUNKS.get(),
         (Integer)SMALL_SKY_SEARCH_RADIUS_CHUNKS.get(),
         (Integer)SURFACE_SKY_MIN_STABLE_TOP_CELLS.get(),
         (Integer)SMALL_SKY_MIN_STABLE_TOP_CELLS.get(),
         (Integer)SURFACE_SKY_TOP_OFFSET.get(),
         (Integer)SMALL_SKY_TOP_OFFSET.get(),
         (Integer)SURFACE_SKY_LOCAL_SEARCH_STEP_BLOCKS.get(),
         (Integer)SMALL_SKY_LOCAL_SEARCH_STEP_BLOCKS.get(),
         (Integer)SURFACE_SKY_LOCAL_SEARCH_RADIUS_BLOCKS.get(),
         (Integer)SMALL_SKY_LOCAL_SEARCH_RADIUS_BLOCKS.get(),
         (Double)SURFACE_SKY_GROUNDED_SAMPLE_THRESHOLD.get(),
         (Double)SMALL_SKY_GROUNDED_SAMPLE_THRESHOLD.get(),
         (Integer)SURFACE_SKY_MAX_GROUND_GAP_BLOCKS.get(),
         (Integer)SMALL_SKY_MAX_GROUND_GAP_BLOCKS.get(),
         (Integer)SURFACE_SKY_MIN_HOST_ISLAND_RADIUS.get(),
         (Integer)SMALL_SKY_MIN_HOST_ISLAND_RADIUS.get(),
         (Integer)SURFACE_SKY_MIN_HOST_STABLE_TOP_CELLS.get(),
         (Integer)SMALL_SKY_MIN_HOST_STABLE_TOP_CELLS.get(),
         (Boolean)DEFAULT_NEAR_MISS_FALLBACK.get(),
         (Boolean)CUSTOM_STRUCTURE_RULES_ENABLED.get(),
         (Boolean)CLUSTER_COMPANION_ISLANDS_ENABLED.get(),
         (Boolean)ANCHOR_FRAGMENTATION_ENABLED.get(),
         (Boolean)DISABLE_ISLANDS_OVER_OCEAN_BIOMES.get(),
         (Integer)DEEPSLATE_START_Y.get(),
         (Boolean)BIOME_PROFILE_BLENDING_ENABLED.get(),
         (Integer)BIOME_PROFILE_BLENDING_RADIUS_BLOCKS.get(),
         (Integer)BIOME_PROFILE_BLENDING_QUANTIZATION_STEPS.get(),
         (Boolean)BIOME_PROFILE_BLENDING_BOUNDARY_ONLY.get(),
         (TerrainOverlapMode)TERRAIN_OVERLAP_MODE.get(),
         ((List)SKY_STRUCTURE_WHITELIST.get()).stream().map(String::valueOf).toList(),
         ((List)SKY_STRUCTURE_DENYLIST.get()).stream().map(String::valueOf).toList(),
         ((List)SKY_STRUCTURE_CATEGORY_OVERRIDES.get()).stream().map(String::valueOf).toList(),
         ((List)WATER_STRUCTURE_CATEGORY_TOKENS.get()).stream().map(String::valueOf).toList(),
         ((List)SKY_STRUCTURE_CATEGORY_TOKENS.get()).stream().map(String::valueOf).toList(),
         ((List)GROUND_STRUCTURE_CATEGORY_TOKENS.get()).stream().map(String::valueOf).toList(),
         ((List)UNDERGROUND_STRUCTURE_CATEGORY_TOKENS.get()).stream().map(String::valueOf).toList(),
         ((List)VILLAGE_STRUCTURE_CATEGORY_TOKENS.get()).stream().map(String::valueOf).toList(),
         ((List)STRONGHOLD_STRUCTURE_CATEGORY_TOKENS.get()).stream().map(String::valueOf).toList()
      );
   }

   private static IslandSizeBand sanitizeBand(int requestedMin, int requestedMax, double weight, int globalMin, int globalMax, IslandSizeBand fallback) {
      int min = Math.max(globalMin, Math.min(globalMax, requestedMin));
      int max = Math.max(min, Math.max(globalMin, Math.min(globalMax, requestedMax)));
      return min > max ? fallback : new IslandSizeBand(min, max, weight);
   }

   public static boolean strongholdHostIslandEnabled() {
      return (Boolean)STRONGHOLD_HOST_ISLAND_ENABLED.get();
   }

   public static int strongholdHostIslandClosestN() {
      return (Integer)STRONGHOLD_HOST_ISLAND_CLOSEST_N.get();
   }

   public static int strongholdHostIslandRadiusMin() {
      return (Integer)STRONGHOLD_HOST_ISLAND_RADIUS_MIN.get();
   }

   public static int strongholdHostIslandRadiusMax() {
      return Math.max((Integer)STRONGHOLD_HOST_ISLAND_RADIUS_MIN.get(), (Integer)STRONGHOLD_HOST_ISLAND_RADIUS_MAX.get());
   }
}
