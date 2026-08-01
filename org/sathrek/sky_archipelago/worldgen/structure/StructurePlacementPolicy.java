package org.sathrek.sky_archipelago.worldgen.structure;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandSizeTier;
import org.sathrek.sky_archipelago.worldgen.structure.underground.UndergroundPlacementBehavior;

public final class StructurePlacementPolicy {
   private static final List<String> DEFAULT_WATER_CATEGORY_TOKENS = List.of("ocean", "water");
   private static final List<String> DEFAULT_SKY_CATEGORY_TOKENS = List.of("sky", "sky_village", "airship", "floating", "cloud", "aerial");
   private static final List<String> DEFAULT_GROUND_CATEGORY_TOKENS = List.of();
   private static final List<String> DEFAULT_UNDERGROUND_CATEGORY_TOKENS = List.of("mineshaft", "trial_chambers", "ancient_city", "underground", "cave");
   private static final List<String> DEFAULT_VILLAGE_CATEGORY_TOKENS = List.of("village");
   private static final List<String> DEFAULT_STRONGHOLD_CATEGORY_TOKENS = List.of("stronghold");
   private static final Set<ResourceLocation> DEFAULT_UNDERGROUND_STRUCTURES = Set.of(
      ResourceLocation.parse("minecraft:mineshaft"),
      ResourceLocation.parse("minecraft:trial_chambers"),
      ResourceLocation.parse("minecraft:ancient_city"),
      ResourceLocation.parse("nova_structures:deepslate_camp"),
      ResourceLocation.parse("create_structures_arise:createmonsterroom")
   );
   private static final Map<ResourceLocation, WaterPlacementMode> DEFAULT_WATER_MODE_OVERRIDES = Map.of(
      ResourceLocation.parse("minecraft:shipwreck"),
      WaterPlacementMode.SURFACE,
      ResourceLocation.parse("minecraft:shipwreck_beached"),
      WaterPlacementMode.SURFACE,
      ResourceLocation.parse("minecraft:ocean_ruin_cold"),
      WaterPlacementMode.OCEAN_FLOOR,
      ResourceLocation.parse("minecraft:ocean_ruin_warm"),
      WaterPlacementMode.OCEAN_FLOOR,
      ResourceLocation.parse("minecraft:monument"),
      WaterPlacementMode.OCEAN_FLOOR,
      ResourceLocation.parse("minecraft:ruined_portal_ocean"),
      WaterPlacementMode.OCEAN_FLOOR
   );
   private static final Set<ResourceLocation> DEFAULT_WATER_STRUCTURES = Set.of(ResourceLocation.parse("minecraft:monument"));
   private static final Map<ResourceLocation, Integer> DEFAULT_WATER_SURFACE_OFFSETS = Map.of(
      ResourceLocation.parse("minecraft:shipwreck"), 0, ResourceLocation.parse("minecraft:shipwreck_beached"), 1
   );
   private static final double HAMLET_SKY_SUPPORT_THRESHOLD = 0.62;
   private static final double HAMLET_SKY_FOOTPRINT_INSET_RATIO = 0.12;
   private static final int HAMLET_SKY_SEARCH_RADIUS_CHUNKS = 8;
   private static final int HAMLET_SKY_MIN_STABLE_TOP_CELLS = 18;
   private static final int HAMLET_SKY_TOP_OFFSET = 1;
   private static final int HAMLET_SKY_LOCAL_SEARCH_STEP_BLOCKS = 6;
   private static final int HAMLET_SKY_FINE_SEARCH_STEP_BLOCKS = 2;
   private static final int HAMLET_SKY_FINE_TOP_K = 2;
   private static final int HAMLET_SKY_LOCAL_SEARCH_RADIUS_BLOCKS = 36;
   private static final double HAMLET_SKY_GROUNDED_SAMPLE_THRESHOLD = 0.84;
   private static final int HAMLET_SKY_MAX_GROUND_GAP_BLOCKS = 2;
   private static final int HAMLET_SKY_MIN_HOST_ISLAND_RADIUS = 48;
   private static final int HAMLET_SKY_MIN_HOST_STABLE_TOP_CELLS = 18;
   private static final double GROUND_VILLAGE_SUPPORT_THRESHOLD = 0.58;
   private static final double GROUND_VILLAGE_FOOTPRINT_INSET_RATIO = 0.08;
   private static final int GROUND_VILLAGE_SEARCH_RADIUS_CHUNKS = 10;
   private static final int GROUND_VILLAGE_MIN_STABLE_TOP_CELLS = 24;
   private static final int GROUND_VILLAGE_TOP_OFFSET = 1;
   private static final int GROUND_VILLAGE_LOCAL_SEARCH_STEP_BLOCKS = 8;
   private static final int GROUND_VILLAGE_LOCAL_SEARCH_RADIUS_BLOCKS = 48;
   private static final double GROUND_VILLAGE_GROUNDED_SAMPLE_THRESHOLD = 0.8;
   private static final int GROUND_VILLAGE_MAX_GROUND_GAP_BLOCKS = 3;
   private static final int GROUND_VILLAGE_MIN_HOST_ISLAND_RADIUS = 64;
   private static final int GROUND_VILLAGE_MIN_HOST_STABLE_TOP_CELLS = 24;
   private static final int SMALL_ISLAND_RADIUS_MAX = 32;
   private static final int MEDIUM_ISLAND_RADIUS_MAX = 56;
   private final Set<ResourceLocation> denylistedStructures;
   private final Map<ResourceLocation, StructurePlacementCategory> categoryOverrides;
   private final List<String> waterCategoryTokens;
   private final List<String> skyCategoryTokens;
   private final List<String> groundCategoryTokens;
   private final List<String> undergroundCategoryTokens;
   private final List<String> villageCategoryTokens;
   private final List<String> strongholdCategoryTokens;
   private final double surfaceSkySupportThreshold;
   private final double smallSkySupportThreshold;
   private final double surfaceSkyFootprintInsetRatio;
   private final double smallSkyFootprintInsetRatio;
   private final int surfaceSkySearchRadiusChunks;
   private final int smallSkySearchRadiusChunks;
   private final int surfaceSkyMinStableTopCells;
   private final int smallSkyMinStableTopCells;
   private final int surfaceSkyTopOffset;
   private final int smallSkyTopOffset;
   private final int surfaceSkyLocalSearchStepBlocks;
   private final int smallSkyLocalSearchStepBlocks;
   private final int surfaceSkyLocalSearchRadiusBlocks;
   private final int smallSkyLocalSearchRadiusBlocks;
   private final double surfaceSkyGroundedSampleThreshold;
   private final double smallSkyGroundedSampleThreshold;
   private final int surfaceSkyMaxGroundGapBlocks;
   private final int smallSkyMaxGroundGapBlocks;
   private final int surfaceSkyMinHostIslandRadius;
   private final int smallSkyMinHostIslandRadius;
   private final int surfaceSkyMinHostStableTopCells;
   private final int smallSkyMinHostStableTopCells;
   private final boolean defaultNearMissFallbackEnabled;

   private StructurePlacementPolicy(
      Set<ResourceLocation> denylistedStructures,
      Map<ResourceLocation, StructurePlacementCategory> categoryOverrides,
      List<String> waterCategoryTokens,
      List<String> skyCategoryTokens,
      List<String> groundCategoryTokens,
      List<String> undergroundCategoryTokens,
      List<String> villageCategoryTokens,
      List<String> strongholdCategoryTokens,
      double surfaceSkySupportThreshold,
      double smallSkySupportThreshold,
      double surfaceSkyFootprintInsetRatio,
      double smallSkyFootprintInsetRatio,
      int surfaceSkySearchRadiusChunks,
      int smallSkySearchRadiusChunks,
      int surfaceSkyMinStableTopCells,
      int smallSkyMinStableTopCells,
      int surfaceSkyTopOffset,
      int smallSkyTopOffset,
      int surfaceSkyLocalSearchStepBlocks,
      int smallSkyLocalSearchStepBlocks,
      int surfaceSkyLocalSearchRadiusBlocks,
      int smallSkyLocalSearchRadiusBlocks,
      double surfaceSkyGroundedSampleThreshold,
      double smallSkyGroundedSampleThreshold,
      int surfaceSkyMaxGroundGapBlocks,
      int smallSkyMaxGroundGapBlocks,
      int surfaceSkyMinHostIslandRadius,
      int smallSkyMinHostIslandRadius,
      int surfaceSkyMinHostStableTopCells,
      int smallSkyMinHostStableTopCells,
      boolean defaultNearMissFallbackEnabled
   ) {
      this.denylistedStructures = Set.copyOf(denylistedStructures);
      this.categoryOverrides = Map.copyOf(categoryOverrides);
      this.waterCategoryTokens = List.copyOf(waterCategoryTokens);
      this.skyCategoryTokens = List.copyOf(skyCategoryTokens);
      this.groundCategoryTokens = List.copyOf(groundCategoryTokens);
      this.undergroundCategoryTokens = List.copyOf(undergroundCategoryTokens);
      this.villageCategoryTokens = List.copyOf(villageCategoryTokens);
      this.strongholdCategoryTokens = List.copyOf(strongholdCategoryTokens);
      this.surfaceSkySupportThreshold = surfaceSkySupportThreshold;
      this.smallSkySupportThreshold = smallSkySupportThreshold;
      this.surfaceSkyFootprintInsetRatio = surfaceSkyFootprintInsetRatio;
      this.smallSkyFootprintInsetRatio = smallSkyFootprintInsetRatio;
      this.surfaceSkySearchRadiusChunks = surfaceSkySearchRadiusChunks;
      this.smallSkySearchRadiusChunks = smallSkySearchRadiusChunks;
      this.surfaceSkyMinStableTopCells = surfaceSkyMinStableTopCells;
      this.smallSkyMinStableTopCells = smallSkyMinStableTopCells;
      this.surfaceSkyTopOffset = surfaceSkyTopOffset;
      this.smallSkyTopOffset = smallSkyTopOffset;
      this.surfaceSkyLocalSearchStepBlocks = surfaceSkyLocalSearchStepBlocks;
      this.smallSkyLocalSearchStepBlocks = smallSkyLocalSearchStepBlocks;
      this.surfaceSkyLocalSearchRadiusBlocks = surfaceSkyLocalSearchRadiusBlocks;
      this.smallSkyLocalSearchRadiusBlocks = smallSkyLocalSearchRadiusBlocks;
      this.surfaceSkyGroundedSampleThreshold = surfaceSkyGroundedSampleThreshold;
      this.smallSkyGroundedSampleThreshold = smallSkyGroundedSampleThreshold;
      this.surfaceSkyMaxGroundGapBlocks = surfaceSkyMaxGroundGapBlocks;
      this.smallSkyMaxGroundGapBlocks = smallSkyMaxGroundGapBlocks;
      this.surfaceSkyMinHostIslandRadius = surfaceSkyMinHostIslandRadius;
      this.smallSkyMinHostIslandRadius = smallSkyMinHostIslandRadius;
      this.surfaceSkyMinHostStableTopCells = surfaceSkyMinHostStableTopCells;
      this.smallSkyMinHostStableTopCells = smallSkyMinHostStableTopCells;
      this.defaultNearMissFallbackEnabled = defaultNearMissFallbackEnabled;
   }

   public static StructurePlacementPolicy fromConfig(
      Collection<? extends String> denylistEntries,
      Collection<? extends String> categoryEntries,
      double surfaceSkySupportThreshold,
      double smallSkySupportThreshold,
      double surfaceSkyFootprintInsetRatio,
      double smallSkyFootprintInsetRatio,
      int surfaceSkySearchRadiusChunks,
      int smallSkySearchRadiusChunks,
      int surfaceSkyMinStableTopCells,
      int smallSkyMinStableTopCells,
      int surfaceSkyTopOffset,
      int smallSkyTopOffset,
      int surfaceSkyLocalSearchStepBlocks,
      int smallSkyLocalSearchStepBlocks,
      int surfaceSkyLocalSearchRadiusBlocks,
      int smallSkyLocalSearchRadiusBlocks,
      double surfaceSkyGroundedSampleThreshold,
      double smallSkyGroundedSampleThreshold,
      int surfaceSkyMaxGroundGapBlocks,
      int smallSkyMaxGroundGapBlocks,
      int surfaceSkyMinHostIslandRadius,
      int smallSkyMinHostIslandRadius,
      int surfaceSkyMinHostStableTopCells,
      int smallSkyMinHostStableTopCells,
      boolean defaultNearMissFallbackEnabled
   ) {
      return fromConfig(
         denylistEntries,
         categoryEntries,
         DEFAULT_WATER_CATEGORY_TOKENS,
         DEFAULT_SKY_CATEGORY_TOKENS,
         DEFAULT_GROUND_CATEGORY_TOKENS,
         DEFAULT_UNDERGROUND_CATEGORY_TOKENS,
         DEFAULT_VILLAGE_CATEGORY_TOKENS,
         DEFAULT_STRONGHOLD_CATEGORY_TOKENS,
         surfaceSkySupportThreshold,
         smallSkySupportThreshold,
         surfaceSkyFootprintInsetRatio,
         smallSkyFootprintInsetRatio,
         surfaceSkySearchRadiusChunks,
         smallSkySearchRadiusChunks,
         surfaceSkyMinStableTopCells,
         smallSkyMinStableTopCells,
         surfaceSkyTopOffset,
         smallSkyTopOffset,
         surfaceSkyLocalSearchStepBlocks,
         smallSkyLocalSearchStepBlocks,
         surfaceSkyLocalSearchRadiusBlocks,
         smallSkyLocalSearchRadiusBlocks,
         surfaceSkyGroundedSampleThreshold,
         smallSkyGroundedSampleThreshold,
         surfaceSkyMaxGroundGapBlocks,
         smallSkyMaxGroundGapBlocks,
         surfaceSkyMinHostIslandRadius,
         smallSkyMinHostIslandRadius,
         surfaceSkyMinHostStableTopCells,
         smallSkyMinHostStableTopCells,
         defaultNearMissFallbackEnabled
      );
   }

   public static StructurePlacementPolicy fromConfig(
      Collection<? extends String> denylistEntries,
      Collection<? extends String> categoryEntries,
      Collection<? extends String> skyCategoryTokens,
      double surfaceSkySupportThreshold,
      double smallSkySupportThreshold,
      double surfaceSkyFootprintInsetRatio,
      double smallSkyFootprintInsetRatio,
      int surfaceSkySearchRadiusChunks,
      int smallSkySearchRadiusChunks,
      int surfaceSkyMinStableTopCells,
      int smallSkyMinStableTopCells,
      int surfaceSkyTopOffset,
      int smallSkyTopOffset,
      int surfaceSkyLocalSearchStepBlocks,
      int smallSkyLocalSearchStepBlocks,
      int surfaceSkyLocalSearchRadiusBlocks,
      int smallSkyLocalSearchRadiusBlocks,
      double surfaceSkyGroundedSampleThreshold,
      double smallSkyGroundedSampleThreshold,
      int surfaceSkyMaxGroundGapBlocks,
      int smallSkyMaxGroundGapBlocks,
      int surfaceSkyMinHostIslandRadius,
      int smallSkyMinHostIslandRadius,
      int surfaceSkyMinHostStableTopCells,
      int smallSkyMinHostStableTopCells,
      boolean defaultNearMissFallbackEnabled
   ) {
      return fromConfig(
         denylistEntries,
         categoryEntries,
         DEFAULT_WATER_CATEGORY_TOKENS,
         skyCategoryTokens,
         DEFAULT_GROUND_CATEGORY_TOKENS,
         DEFAULT_UNDERGROUND_CATEGORY_TOKENS,
         DEFAULT_VILLAGE_CATEGORY_TOKENS,
         DEFAULT_STRONGHOLD_CATEGORY_TOKENS,
         surfaceSkySupportThreshold,
         smallSkySupportThreshold,
         surfaceSkyFootprintInsetRatio,
         smallSkyFootprintInsetRatio,
         surfaceSkySearchRadiusChunks,
         smallSkySearchRadiusChunks,
         surfaceSkyMinStableTopCells,
         smallSkyMinStableTopCells,
         surfaceSkyTopOffset,
         smallSkyTopOffset,
         surfaceSkyLocalSearchStepBlocks,
         smallSkyLocalSearchStepBlocks,
         surfaceSkyLocalSearchRadiusBlocks,
         smallSkyLocalSearchRadiusBlocks,
         surfaceSkyGroundedSampleThreshold,
         smallSkyGroundedSampleThreshold,
         surfaceSkyMaxGroundGapBlocks,
         smallSkyMaxGroundGapBlocks,
         surfaceSkyMinHostIslandRadius,
         smallSkyMinHostIslandRadius,
         surfaceSkyMinHostStableTopCells,
         smallSkyMinHostStableTopCells,
         defaultNearMissFallbackEnabled
      );
   }

   public static StructurePlacementPolicy fromConfig(
      Collection<? extends String> denylistEntries,
      Collection<? extends String> categoryEntries,
      Collection<? extends String> waterCategoryTokens,
      Collection<? extends String> skyCategoryTokens,
      Collection<? extends String> groundCategoryTokens,
      Collection<? extends String> undergroundCategoryTokens,
      Collection<? extends String> villageCategoryTokens,
      Collection<? extends String> strongholdCategoryTokens,
      double surfaceSkySupportThreshold,
      double smallSkySupportThreshold,
      double surfaceSkyFootprintInsetRatio,
      double smallSkyFootprintInsetRatio,
      int surfaceSkySearchRadiusChunks,
      int smallSkySearchRadiusChunks,
      int surfaceSkyMinStableTopCells,
      int smallSkyMinStableTopCells,
      int surfaceSkyTopOffset,
      int smallSkyTopOffset,
      int surfaceSkyLocalSearchStepBlocks,
      int smallSkyLocalSearchStepBlocks,
      int surfaceSkyLocalSearchRadiusBlocks,
      int smallSkyLocalSearchRadiusBlocks,
      double surfaceSkyGroundedSampleThreshold,
      double smallSkyGroundedSampleThreshold,
      int surfaceSkyMaxGroundGapBlocks,
      int smallSkyMaxGroundGapBlocks,
      int surfaceSkyMinHostIslandRadius,
      int smallSkyMinHostIslandRadius,
      int surfaceSkyMinHostStableTopCells,
      int smallSkyMinHostStableTopCells,
      boolean defaultNearMissFallbackEnabled
   ) {
      Set<ResourceLocation> denylistedStructures = denylistEntries.stream()
         .<ResourceLocation>map(ResourceLocation::tryParse)
         .filter(Objects::nonNull)
         .collect(Collectors.toSet());
      Map<ResourceLocation, StructurePlacementCategory> categoryOverrides = categoryEntries.stream()
         .map(StructurePlacementPolicy::parseCategoryEntry)
         .filter(Objects::nonNull)
         .collect(
            Collectors.toMap(StructurePlacementPolicy.CategoryEntry::structureId, StructurePlacementPolicy.CategoryEntry::category, (left, right) -> right)
         );
      return new StructurePlacementPolicy(
         denylistedStructures,
         categoryOverrides,
         normalizeTokens(waterCategoryTokens),
         normalizeTokens(skyCategoryTokens),
         normalizeTokens(groundCategoryTokens),
         normalizeTokens(undergroundCategoryTokens),
         normalizeTokens(villageCategoryTokens),
         normalizeTokens(strongholdCategoryTokens),
         surfaceSkySupportThreshold,
         smallSkySupportThreshold,
         surfaceSkyFootprintInsetRatio,
         smallSkyFootprintInsetRatio,
         surfaceSkySearchRadiusChunks,
         smallSkySearchRadiusChunks,
         surfaceSkyMinStableTopCells,
         smallSkyMinStableTopCells,
         surfaceSkyTopOffset,
         smallSkyTopOffset,
         surfaceSkyLocalSearchStepBlocks,
         smallSkyLocalSearchStepBlocks,
         surfaceSkyLocalSearchRadiusBlocks,
         smallSkyLocalSearchRadiusBlocks,
         surfaceSkyGroundedSampleThreshold,
         smallSkyGroundedSampleThreshold,
         surfaceSkyMaxGroundGapBlocks,
         smallSkyMaxGroundGapBlocks,
         surfaceSkyMinHostIslandRadius,
         smallSkyMinHostIslandRadius,
         surfaceSkyMinHostStableTopCells,
         smallSkyMinHostStableTopCells,
         defaultNearMissFallbackEnabled
      );
   }

   public static boolean isValidCategoryEntry(String entry) {
      return parseCategoryEntry(entry) != null;
   }

   public boolean isDenied(ResourceLocation structureId) {
      return structureId != null && this.denylistedStructures.contains(structureId);
   }

   public Set<ResourceLocation> denylistedStructures() {
      return this.denylistedStructures;
   }

   public Map<ResourceLocation, StructurePlacementCategory> categoryOverrides() {
      return this.categoryOverrides;
   }

   public List<String> waterCategoryTokens() {
      return this.waterCategoryTokens;
   }

   public List<String> skyCategoryTokens() {
      return this.skyCategoryTokens;
   }

   public List<String> groundCategoryTokens() {
      return this.groundCategoryTokens;
   }

   public List<String> undergroundCategoryTokens() {
      return this.undergroundCategoryTokens;
   }

   public List<String> villageCategoryTokens() {
      return this.villageCategoryTokens;
   }

   public List<String> strongholdCategoryTokens() {
      return this.strongholdCategoryTokens;
   }

   public StructurePlacementCategory categoryFor(ResourceLocation structureId) {
      if (structureId == null) {
         return StructurePlacementCategory.DEFAULT;
      } else {
         StructurePlacementCategory explicit = this.categoryOverrides.get(structureId);
         if (explicit != null) {
            return explicit;
         } else if (hasCategoryToken(structureId, this.skyCategoryTokens)) {
            return StructurePlacementCategory.SKY;
         } else if (hasCategoryToken(structureId, this.villageCategoryTokens)) {
            return StructurePlacementCategory.GROUND_VILLAGE;
         } else if (hasCategoryToken(structureId, this.strongholdCategoryTokens)) {
            return StructurePlacementCategory.STRONGHOLD;
         } else if (hasCategoryToken(structureId, this.undergroundCategoryTokens)) {
            return StructurePlacementCategory.UNDERGROUND;
         } else if (hasCategoryToken(structureId, this.waterCategoryTokens) || DEFAULT_WATER_STRUCTURES.contains(structureId)) {
            return StructurePlacementCategory.WATER;
         } else {
            return hasCategoryToken(structureId, this.groundCategoryTokens)
               ? StructurePlacementCategory.SURFACE_SKY
               : this.inferredFallbackCategory(structureId);
         }
      }
   }

   public StructurePlacementCategory effectiveCategoryFor(ResourceLocation structureId, ResolvedStructureSupportPlane supportPlane) {
      StructurePlacementCategory baseCategory = this.categoryFor(structureId);
      return baseCategory == StructurePlacementCategory.DEFAULT && supportPlane != null && supportPlane.effectiveFootprint() != null
         ? promoteDefaultByFootprintArea(supportPlane.effectiveFootprint().area())
         : baseCategory;
   }

   public StructurePlacementCategory effectiveCategoryFor(ResourceLocation structureId, int effectiveFootprintArea) {
      StructurePlacementCategory baseCategory = this.categoryFor(structureId);
      return baseCategory != StructurePlacementCategory.DEFAULT ? baseCategory : promoteDefaultByFootprintArea(effectiveFootprintArea);
   }

   public UndergroundPlacementBehavior undergroundBehaviorFor(ResourceLocation structureId) {
      return this.undergroundBehaviorFor(structureId, null);
   }

   public UndergroundPlacementBehavior undergroundBehaviorFor(ResourceLocation structureId, Structure structure) {
      if (this.categoryFor(structureId) != StructurePlacementCategory.UNDERGROUND) {
         return UndergroundPlacementBehavior.STATIC_FOOTPRINT;
      } else if (structureId != null && structureId.getPath().contains("mineshaft")) {
         return UndergroundPlacementBehavior.DYNAMIC_ANCHOR_FIRST;
      } else {
         return this.isUndergroundDynamicJigsaw(structure) ? UndergroundPlacementBehavior.DYNAMIC_ANCHOR_FIRST : UndergroundPlacementBehavior.STATIC_FOOTPRINT;
      }
   }

   public boolean isJigsawStructure(Structure structure) {
      return structure instanceof JigsawStructure;
   }

   private boolean isUndergroundDynamicJigsaw(Structure structure) {
      return this.isJigsawStructure(structure);
   }

   public double thresholdFor(ResourceLocation structureId, double defaultThreshold) {
      return this.thresholdForCategory(this.categoryFor(structureId), defaultThreshold);
   }

   public double thresholdForCategory(StructurePlacementCategory category, double defaultThreshold) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkySupportThreshold;
         case SMALL_SKY -> this.smallSkySupportThreshold;
         case HAMLET_SKY -> 0.62;
         case GROUND_VILLAGE -> 0.58;
         case DEFAULT, SKY, STRONGHOLD, UNDERGROUND, WATER -> defaultThreshold;
      };
   }

   public double footprintInsetRatioFor(ResourceLocation structureId) {
      return this.footprintInsetRatioForCategory(this.categoryFor(structureId));
   }

   public double footprintInsetRatioForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkyFootprintInsetRatio;
         case SMALL_SKY -> this.smallSkyFootprintInsetRatio;
         case HAMLET_SKY -> 0.12;
         case GROUND_VILLAGE -> 0.08;
         case DEFAULT, SKY, STRONGHOLD, UNDERGROUND, WATER -> 0.0;
      };
   }

   public boolean usesIslandAwarePlacement(ResourceLocation structureId) {
      return this.categoryFor(structureId).usesIslandAwarePlacement();
   }

   public int searchRadiusChunksFor(ResourceLocation structureId) {
      return this.searchRadiusChunksForCategory(this.categoryFor(structureId));
   }

   public int searchRadiusChunksForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkySearchRadiusChunks;
         case SMALL_SKY -> this.smallSkySearchRadiusChunks;
         case HAMLET_SKY -> 8;
         case GROUND_VILLAGE -> 10;
         case DEFAULT, SKY -> 0;
         case STRONGHOLD -> Math.max(this.surfaceSkySearchRadiusChunks, this.smallSkySearchRadiusChunks);
         case UNDERGROUND -> Math.max(this.surfaceSkySearchRadiusChunks, this.smallSkySearchRadiusChunks);
         case WATER -> Math.max(this.surfaceSkySearchRadiusChunks, this.smallSkySearchRadiusChunks);
      };
   }

   public int minStableTopCellsFor(ResourceLocation structureId) {
      return this.minStableTopCellsForCategory(this.categoryFor(structureId));
   }

   public int minStableTopCellsForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkyMinStableTopCells;
         case SMALL_SKY -> this.smallSkyMinStableTopCells;
         case HAMLET_SKY -> 18;
         case GROUND_VILLAGE -> 24;
         case DEFAULT, SKY -> 0;
         case STRONGHOLD -> Math.max(this.surfaceSkyMinStableTopCells, this.smallSkyMinStableTopCells);
         case UNDERGROUND -> Math.max(this.surfaceSkyMinStableTopCells, this.smallSkyMinStableTopCells);
         case WATER -> Math.max(this.surfaceSkyMinStableTopCells, this.smallSkyMinStableTopCells);
      };
   }

   public int topOffsetFor(ResourceLocation structureId) {
      return this.topOffsetForCategory(this.categoryFor(structureId));
   }

   public int topOffsetForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkyTopOffset;
         case SMALL_SKY -> this.smallSkyTopOffset;
         case HAMLET_SKY -> 1;
         case GROUND_VILLAGE -> 1;
         case DEFAULT, SKY -> 0;
         case STRONGHOLD, UNDERGROUND, WATER -> 0;
      };
   }

   public int localSearchStepBlocksFor(ResourceLocation structureId) {
      return this.localSearchStepBlocksForCategory(this.categoryFor(structureId));
   }

   public int localSearchStepBlocksForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkyLocalSearchStepBlocks;
         case SMALL_SKY -> this.smallSkyLocalSearchStepBlocks;
         case HAMLET_SKY -> 6;
         case GROUND_VILLAGE -> 8;
         case DEFAULT, SKY -> 0;
         case STRONGHOLD -> Math.max(this.surfaceSkyLocalSearchStepBlocks, this.smallSkyLocalSearchStepBlocks);
         case UNDERGROUND -> Math.max(this.surfaceSkyLocalSearchStepBlocks, this.smallSkyLocalSearchStepBlocks);
         case WATER -> Math.max(this.surfaceSkyLocalSearchStepBlocks, this.smallSkyLocalSearchStepBlocks);
      };
   }

   public int localSearchRadiusBlocksFor(ResourceLocation structureId) {
      return this.localSearchRadiusBlocksForCategory(this.categoryFor(structureId));
   }

   public int localSearchRadiusBlocksForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkyLocalSearchRadiusBlocks;
         case SMALL_SKY -> this.smallSkyLocalSearchRadiusBlocks;
         case HAMLET_SKY -> 36;
         case GROUND_VILLAGE -> 48;
         case DEFAULT, SKY -> 0;
         case STRONGHOLD -> Math.max(this.surfaceSkyLocalSearchRadiusBlocks, this.smallSkyLocalSearchRadiusBlocks);
         case UNDERGROUND -> Math.max(this.surfaceSkyLocalSearchRadiusBlocks, this.smallSkyLocalSearchRadiusBlocks);
         case WATER -> Math.max(this.surfaceSkyLocalSearchRadiusBlocks, this.smallSkyLocalSearchRadiusBlocks);
      };
   }

   public double groundedSampleThresholdFor(ResourceLocation structureId) {
      return this.groundedSampleThresholdForCategory(this.categoryFor(structureId));
   }

   public double groundedSampleThresholdForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkyGroundedSampleThreshold;
         case SMALL_SKY -> this.smallSkyGroundedSampleThreshold;
         case HAMLET_SKY -> 0.84;
         case GROUND_VILLAGE -> 0.8;
         case DEFAULT, SKY -> 0.0;
         case STRONGHOLD -> Math.max(this.surfaceSkyGroundedSampleThreshold, this.smallSkyGroundedSampleThreshold);
         case UNDERGROUND -> Math.max(this.surfaceSkyGroundedSampleThreshold, this.smallSkyGroundedSampleThreshold);
         case WATER -> Math.max(this.surfaceSkyGroundedSampleThreshold, this.smallSkyGroundedSampleThreshold);
      };
   }

   public int maxGroundGapBlocksFor(ResourceLocation structureId) {
      return this.maxGroundGapBlocksForCategory(this.categoryFor(structureId));
   }

   public int maxGroundGapBlocksForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkyMaxGroundGapBlocks;
         case SMALL_SKY -> this.smallSkyMaxGroundGapBlocks;
         case HAMLET_SKY -> 2;
         case GROUND_VILLAGE -> 3;
         case DEFAULT, SKY -> 0;
         case STRONGHOLD -> Math.max(this.surfaceSkyMaxGroundGapBlocks, this.smallSkyMaxGroundGapBlocks);
         case UNDERGROUND -> Math.max(this.surfaceSkyMaxGroundGapBlocks, this.smallSkyMaxGroundGapBlocks);
         case WATER -> Math.max(this.surfaceSkyMaxGroundGapBlocks, this.smallSkyMaxGroundGapBlocks);
      };
   }

   public int minHostIslandRadiusFor(ResourceLocation structureId) {
      return this.minHostIslandRadiusForCategory(this.categoryFor(structureId));
   }

   public int minHostIslandRadiusForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkyMinHostIslandRadius;
         case SMALL_SKY -> this.smallSkyMinHostIslandRadius;
         case HAMLET_SKY -> 48;
         case GROUND_VILLAGE -> 64;
         case DEFAULT, SKY -> 0;
         case STRONGHOLD -> Math.max(this.surfaceSkyMinHostIslandRadius, this.smallSkyMinHostIslandRadius);
         case UNDERGROUND -> Math.max(this.surfaceSkyMinHostIslandRadius, this.smallSkyMinHostIslandRadius);
         case WATER -> Math.max(this.surfaceSkyMinHostIslandRadius, this.smallSkyMinHostIslandRadius);
      };
   }

   public int minHostStableTopCellsFor(ResourceLocation structureId) {
      return this.minHostStableTopCellsForCategory(this.categoryFor(structureId));
   }

   public int minHostStableTopCellsForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> this.surfaceSkyMinHostStableTopCells;
         case SMALL_SKY -> this.smallSkyMinHostStableTopCells;
         case HAMLET_SKY -> 18;
         case GROUND_VILLAGE -> 24;
         case DEFAULT, SKY -> 0;
         case STRONGHOLD -> Math.max(this.surfaceSkyMinHostStableTopCells, this.smallSkyMinHostStableTopCells);
         case UNDERGROUND -> Math.max(this.surfaceSkyMinHostStableTopCells, this.smallSkyMinHostStableTopCells);
         case WATER -> Math.max(this.surfaceSkyMinHostStableTopCells, this.smallSkyMinHostStableTopCells);
      };
   }

   public boolean defaultNearMissFallbackEnabled() {
      return this.defaultNearMissFallbackEnabled;
   }

   public int fineSearchStepBlocksForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> 2;
         case SMALL_SKY -> 2;
         case HAMLET_SKY -> 2;
         case GROUND_VILLAGE -> 2;
         case DEFAULT, SKY, STRONGHOLD, UNDERGROUND, WATER -> 0;
      };
   }

   public int fineTopCandidatesForCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SURFACE_SKY -> 3;
         case SMALL_SKY -> 4;
         case HAMLET_SKY -> 2;
         case GROUND_VILLAGE -> 2;
         case DEFAULT, SKY, STRONGHOLD, UNDERGROUND, WATER -> 0;
      };
   }

   public int maxHostAttemptsForCategory(StructurePlacementCategory category, int islandRadius) {
      return switch (islandSizeTierForRadius(islandRadius)) {
         case SMALL -> {
            switch (category) {
               case SURFACE_SKY:
                  yield 4;
               case SMALL_SKY:
                  yield 5;
               case HAMLET_SKY:
                  yield 2;
               case GROUND_VILLAGE:
                  yield 2;
               case DEFAULT:
               case SKY:
               case STRONGHOLD:
               case UNDERGROUND:
               case WATER:
                  yield 2;
               default:
                  throw new MatchException(null, null);
            }
         }
         case MEDIUM -> {
            switch (category) {
               case SURFACE_SKY:
                  yield 6;
               case SMALL_SKY:
                  yield 8;
               case HAMLET_SKY:
                  yield 3;
               case GROUND_VILLAGE:
                  yield 3;
               case DEFAULT:
               case SKY:
               case STRONGHOLD:
               case UNDERGROUND:
               case WATER:
                  yield 3;
               default:
                  throw new MatchException(null, null);
            }
         }
         case LARGE -> {
            switch (category) {
               case SURFACE_SKY:
                  yield 8;
               case SMALL_SKY:
                  yield 10;
               case HAMLET_SKY:
                  yield 4;
               case GROUND_VILLAGE:
                  yield 4;
               case DEFAULT:
               case SKY:
               case STRONGHOLD:
               case UNDERGROUND:
               case WATER:
                  yield 4;
               default:
                  throw new MatchException(null, null);
            }
         }
      };
   }

   public int maxOffsetsPerIslandForCategory(StructurePlacementCategory category, int islandRadius) {
      return switch (islandSizeTierForRadius(islandRadius)) {
         case SMALL -> {
            switch (category) {
               case SURFACE_SKY:
                  yield 80;
               case SMALL_SKY:
                  yield 100;
               case HAMLET_SKY:
                  yield 70;
               case GROUND_VILLAGE:
                  yield 72;
               case DEFAULT:
               case SKY:
               case STRONGHOLD:
               case UNDERGROUND:
               case WATER:
                  yield 64;
               default:
                  throw new MatchException(null, null);
            }
         }
         case MEDIUM -> {
            switch (category) {
               case SURFACE_SKY:
                  yield 120;
               case SMALL_SKY:
                  yield 140;
               case HAMLET_SKY:
                  yield 90;
               case GROUND_VILLAGE:
                  yield 96;
               case DEFAULT:
               case SKY:
               case STRONGHOLD:
               case UNDERGROUND:
               case WATER:
                  yield 96;
               default:
                  throw new MatchException(null, null);
            }
         }
         case LARGE -> {
            switch (category) {
               case SURFACE_SKY:
                  yield 160;
               case SMALL_SKY:
                  yield 180;
               case HAMLET_SKY:
                  yield 120;
               case GROUND_VILLAGE:
                  yield 128;
               case DEFAULT:
               case SKY:
               case STRONGHOLD:
               case UNDERGROUND:
               case WATER:
                  yield 128;
               default:
                  throw new MatchException(null, null);
            }
         }
      };
   }

   public int adaptiveOffsetCapBonusForCategory(StructurePlacementCategory category, int islandRadius) {
      return switch (islandSizeTierForRadius(islandRadius)) {
         case SMALL -> {
            switch (category) {
               case SURFACE_SKY:
                  yield 24;
               case SMALL_SKY:
                  yield 28;
               case HAMLET_SKY:
                  yield 16;
               case GROUND_VILLAGE:
                  yield 16;
               case DEFAULT:
               case SKY:
               case STRONGHOLD:
               case UNDERGROUND:
               case WATER:
                  yield 12;
               default:
                  throw new MatchException(null, null);
            }
         }
         case MEDIUM -> {
            switch (category) {
               case SURFACE_SKY:
                  yield 32;
               case SMALL_SKY:
                  yield 36;
               case HAMLET_SKY:
                  yield 20;
               case GROUND_VILLAGE:
                  yield 20;
               case DEFAULT:
               case SKY:
               case STRONGHOLD:
               case UNDERGROUND:
               case WATER:
                  yield 16;
               default:
                  throw new MatchException(null, null);
            }
         }
         case LARGE -> {
            switch (category) {
               case SURFACE_SKY:
                  yield 40;
               case SMALL_SKY:
                  yield 44;
               case HAMLET_SKY:
                  yield 24;
               case GROUND_VILLAGE:
                  yield 24;
               case DEFAULT:
               case SKY:
               case STRONGHOLD:
               case UNDERGROUND:
               case WATER:
                  yield 20;
               default:
                  throw new MatchException(null, null);
            }
         }
      };
   }

   public int denylistSize() {
      return this.denylistedStructures.size();
   }

   public int categoryOverrideCount() {
      return this.categoryOverrides.size();
   }

   public WaterPlacementMode waterModeFor(ResourceLocation structureId) {
      if (this.categoryFor(structureId) != StructurePlacementCategory.WATER) {
         return WaterPlacementMode.OCEAN_FLOOR;
      }

      if (structureId == null) {
         return WaterPlacementMode.OCEAN_FLOOR;
      }

      WaterPlacementMode explicit = DEFAULT_WATER_MODE_OVERRIDES.get(structureId);
      if (explicit != null) {
         return explicit;
      }

      String lowered = structureId.toString().toLowerCase(Locale.ROOT);
      return !lowered.contains("shipwreck") && !lowered.contains("boat") && !lowered.contains("vessel")
         ? WaterPlacementMode.OCEAN_FLOOR
         : WaterPlacementMode.SURFACE;
   }

   public int waterSurfaceOffsetFor(ResourceLocation structureId) {
      return structureId == null ? 0 : DEFAULT_WATER_SURFACE_OFFSETS.getOrDefault(structureId, 0);
   }

   private static StructurePlacementPolicy.CategoryEntry parseCategoryEntry(String entry) {
      if (entry == null) {
         return null;
      } else {
         int separator = entry.indexOf(61);
         if (separator > 0 && separator < entry.length() - 1) {
            ResourceLocation structureId = ResourceLocation.tryParse(entry.substring(0, separator).trim());
            StructurePlacementCategory category = StructurePlacementCategory.tryParse(entry.substring(separator + 1));
            return structureId != null && category != null ? new StructurePlacementPolicy.CategoryEntry(structureId, category) : null;
         } else {
            return null;
         }
      }
   }

   private StructurePlacementCategory inferredFallbackCategory(ResourceLocation structureId) {
      if (DEFAULT_UNDERGROUND_STRUCTURES.contains(structureId)) {
         return StructurePlacementCategory.UNDERGROUND;
      }

      String path = structureId.getPath();
      if (!path.contains("mineshaft")
         && !path.contains("trial_chambers")
         && !path.contains("ancient_city")
         && !path.contains("underground")
         && !path.contains("cave")) {
         String loweredPath = path.toLowerCase(Locale.ROOT);
         String loweredFullId = structureId.toString().toLowerCase(Locale.ROOT);
         if (loweredPath.contains("ocean") || loweredPath.contains("water") || loweredFullId.contains("ocean") || loweredFullId.contains("water")) {
            return StructurePlacementCategory.WATER;
         } else {
            return hasCategoryToken(structureId, this.skyCategoryTokens) ? StructurePlacementCategory.SKY : StructurePlacementCategory.DEFAULT;
         }
      } else {
         return StructurePlacementCategory.UNDERGROUND;
      }
   }

   private static List<String> normalizeTokens(Collection<? extends String> tokens) {
      return tokens.stream().filter(Objects::nonNull).map(token -> token.trim().toLowerCase(Locale.ROOT)).filter(token -> !token.isEmpty()).distinct().toList();
   }

   private static boolean hasCategoryToken(ResourceLocation structureId, Collection<String> tokens) {
      if (structureId == null) {
         return false;
      }

      String loweredPath = structureId.getPath().toLowerCase(Locale.ROOT);
      String loweredFullId = structureId.toString().toLowerCase(Locale.ROOT);

      for (String token : tokens) {
         if (!token.isBlank() && (loweredPath.contains(token) || loweredFullId.contains(token))) {
            return true;
         }
      }

      return false;
   }

   private static StructurePlacementCategory promoteDefaultByFootprintArea(int effectiveFootprintArea) {
      LandSizeTier tier = LandSizeTier.forArea(Math.max(0, effectiveFootprintArea));

      return switch (tier) {
         case SMALL -> StructurePlacementCategory.SMALL_SKY;
         case MEDIUM -> StructurePlacementCategory.SURFACE_SKY;
         case LARGE -> StructurePlacementCategory.HAMLET_SKY;
      };
   }

   private static LandSizeTier islandSizeTierForRadius(int radius) {
      if (radius <= 32) {
         return LandSizeTier.SMALL;
      } else {
         return radius <= 56 ? LandSizeTier.MEDIUM : LandSizeTier.LARGE;
      }
   }

   public static boolean isStrongholdStructure(ResourceLocation structureId) {
      return structureId != null && structureId.toString().toLowerCase(Locale.ROOT).contains("stronghold");
   }

   private record CategoryEntry(ResourceLocation structureId, StructurePlacementCategory category) {
   }
}
