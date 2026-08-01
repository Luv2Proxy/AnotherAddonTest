package org.sathrek.sky_archipelago.config.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.config.OceanBlockType;
import org.sathrek.sky_archipelago.config.TerrainOverlapMode;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.StructureWhitelist;

public record AdvancedGeneratorSettings(
   boolean defaultNearMissFallbackEnabled,
   boolean customStructureRulesEnabled,
   boolean clusterCompanionIslandsEnabled,
   boolean anchorFragmentationEnabled,
   boolean disableIslandsOverOceanBiomes,
   int deepslateStartY,
   boolean biomeProfileBlendingEnabled,
   int biomeProfileBlendingRadiusBlocks,
   int biomeProfileBlendingQuantizationSteps,
   boolean biomeProfileBlendingBoundaryOnly,
   TerrainOverlapMode terrainOverlapMode,
   OceanBlockType oceanBlockType,
   List<String> structureWhitelistEntries,
   List<String> structureDenylistEntries,
   List<String> structureCategoryOverrideEntries,
   List<String> waterStructureCategoryTokens,
   List<String> skyStructureCategoryTokens,
   List<String> groundStructureCategoryTokens,
   List<String> undergroundStructureCategoryTokens,
   List<String> villageStructureCategoryTokens,
   List<String> strongholdStructureCategoryTokens
) {
   public static final Codec<AdvancedGeneratorSettings> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.BOOL.fieldOf("default_near_miss_fallback_enabled").forGetter(AdvancedGeneratorSettings::defaultNearMissFallbackEnabled),
            Codec.BOOL.optionalFieldOf("custom_structure_rules_enabled", true).forGetter(AdvancedGeneratorSettings::customStructureRulesEnabled),
            Codec.BOOL.optionalFieldOf("cluster_companion_islands_enabled", true).forGetter(AdvancedGeneratorSettings::clusterCompanionIslandsEnabled),
            Codec.BOOL.optionalFieldOf("anchor_fragmentation_enabled", true).forGetter(AdvancedGeneratorSettings::anchorFragmentationEnabled),
            Codec.BOOL.optionalFieldOf("disable_islands_over_ocean_biomes", false).forGetter(AdvancedGeneratorSettings::disableIslandsOverOceanBiomes),
            Codec.intRange(-64, 2000).optionalFieldOf("deepslate_start_y", 16).forGetter(AdvancedGeneratorSettings::deepslateStartY),
            Codec.BOOL.optionalFieldOf("biome_profile_blending_enabled", true).forGetter(AdvancedGeneratorSettings::biomeProfileBlendingEnabled),
            Codec.intRange(1, 16)
               .optionalFieldOf("biome_profile_blending_radius_blocks", 4)
               .forGetter(AdvancedGeneratorSettings::biomeProfileBlendingRadiusBlocks),
            Codec.intRange(2, 64)
               .optionalFieldOf("biome_profile_blending_quantization_steps", 8)
               .forGetter(AdvancedGeneratorSettings::biomeProfileBlendingQuantizationSteps),
            Codec.BOOL.optionalFieldOf("biome_profile_blending_boundary_only", true).forGetter(AdvancedGeneratorSettings::biomeProfileBlendingBoundaryOnly),
            TerrainOverlapMode.CODEC.optionalFieldOf("terrain_overlap_mode", TerrainOverlapMode.VOID).forGetter(AdvancedGeneratorSettings::terrainOverlapMode),
            OceanBlockType.CODEC.optionalFieldOf("ocean_block_type", OceanBlockType.WATER).forGetter(AdvancedGeneratorSettings::oceanBlockType),
            ResourceLocation.CODEC
               .xmap(ResourceLocation::toString, ResourceLocation::parse)
               .listOf()
               .fieldOf("structure_whitelist")
               .forGetter(AdvancedGeneratorSettings::structureWhitelistEntries),
            ResourceLocation.CODEC
               .xmap(ResourceLocation::toString, ResourceLocation::parse)
               .listOf()
               .fieldOf("structure_denylist")
               .forGetter(AdvancedGeneratorSettings::structureDenylistEntries),
            Codec.STRING.listOf().fieldOf("structure_category_overrides").forGetter(AdvancedGeneratorSettings::structureCategoryOverrideEntries),
            AdvancedGeneratorSettings.CategoryTokens.CODEC.forGetter(AdvancedGeneratorSettings::categoryTokens)
         )
         .apply(instance, AdvancedGeneratorSettings::fromCodec)
   );

   public AdvancedGeneratorSettings {
      structureWhitelistEntries = List.copyOf(structureWhitelistEntries);
      structureDenylistEntries = List.copyOf(structureDenylistEntries);
      structureCategoryOverrideEntries = List.copyOf(structureCategoryOverrideEntries);
      waterStructureCategoryTokens = List.copyOf(waterStructureCategoryTokens);
      skyStructureCategoryTokens = List.copyOf(skyStructureCategoryTokens);
      groundStructureCategoryTokens = List.copyOf(groundStructureCategoryTokens);
      undergroundStructureCategoryTokens = List.copyOf(undergroundStructureCategoryTokens);
      villageStructureCategoryTokens = List.copyOf(villageStructureCategoryTokens);
      strongholdStructureCategoryTokens = List.copyOf(strongholdStructureCategoryTokens);
      if (!structureWhitelistEntries.stream().allMatch(StructureWhitelist::isValidEntry)) {
         throw new IllegalArgumentException("structureWhitelistEntries contains an invalid structure id");
      }

      if (!structureDenylistEntries.stream().allMatch(StructureWhitelist::isValidEntry)) {
         throw new IllegalArgumentException("structureDenylistEntries contains an invalid structure id");
      }

      if (!structureCategoryOverrideEntries.stream().allMatch(StructurePlacementPolicy::isValidCategoryEntry)) {
         throw new IllegalArgumentException("structureCategoryOverrideEntries contains an invalid category entry");
      }

      validateCategoryTokens(waterStructureCategoryTokens, "waterStructureCategoryTokens");
      validateCategoryTokens(skyStructureCategoryTokens, "skyStructureCategoryTokens");
      validateCategoryTokens(groundStructureCategoryTokens, "groundStructureCategoryTokens");
      validateCategoryTokens(undergroundStructureCategoryTokens, "undergroundStructureCategoryTokens");
      validateCategoryTokens(villageStructureCategoryTokens, "villageStructureCategoryTokens");
      validateCategoryTokens(strongholdStructureCategoryTokens, "strongholdStructureCategoryTokens");
      if (terrainOverlapMode == null) {
         throw new IllegalArgumentException("terrainOverlapMode cannot be null");
      }

      if (oceanBlockType == null) {
         throw new IllegalArgumentException("oceanBlockType cannot be null");
      }
   }

   AdvancedGeneratorSettings.CategoryTokens categoryTokens() {
      return new AdvancedGeneratorSettings.CategoryTokens(
         this.waterStructureCategoryTokens,
         this.skyStructureCategoryTokens,
         this.groundStructureCategoryTokens,
         this.undergroundStructureCategoryTokens,
         this.villageStructureCategoryTokens,
         this.strongholdStructureCategoryTokens
      );
   }

   private static AdvancedGeneratorSettings fromCodec(
      boolean defaultNearMissFallbackEnabled,
      boolean customStructureRulesEnabled,
      boolean clusterCompanionIslandsEnabled,
      boolean anchorFragmentationEnabled,
      boolean disableIslandsOverOceanBiomes,
      int deepslateStartY,
      boolean biomeProfileBlendingEnabled,
      int biomeProfileBlendingRadiusBlocks,
      int biomeProfileBlendingQuantizationSteps,
      boolean biomeProfileBlendingBoundaryOnly,
      TerrainOverlapMode terrainOverlapMode,
      OceanBlockType oceanBlockType,
      List<String> structureWhitelistEntries,
      List<String> structureDenylistEntries,
      List<String> structureCategoryOverrideEntries,
      AdvancedGeneratorSettings.CategoryTokens categoryTokens
   ) {
      return new AdvancedGeneratorSettings(
         defaultNearMissFallbackEnabled,
         customStructureRulesEnabled,
         clusterCompanionIslandsEnabled,
         anchorFragmentationEnabled,
         disableIslandsOverOceanBiomes,
         deepslateStartY,
         biomeProfileBlendingEnabled,
         biomeProfileBlendingRadiusBlocks,
         biomeProfileBlendingQuantizationSteps,
         biomeProfileBlendingBoundaryOnly,
         terrainOverlapMode,
         oceanBlockType,
         structureWhitelistEntries,
         structureDenylistEntries,
         structureCategoryOverrideEntries,
         categoryTokens.waterStructureCategoryTokens,
         categoryTokens.skyStructureCategoryTokens,
         categoryTokens.groundStructureCategoryTokens,
         categoryTokens.undergroundStructureCategoryTokens,
         categoryTokens.villageStructureCategoryTokens,
         categoryTokens.strongholdStructureCategoryTokens
      );
   }

   private static void validateCategoryTokens(List<String> tokens, String fieldName) {
      if (tokens.stream().anyMatch(token -> token == null || token.trim().isEmpty())) {
         throw new IllegalArgumentException(fieldName + " contains a blank token");
      }
   }

   private record CategoryTokens(
      List<String> waterStructureCategoryTokens,
      List<String> skyStructureCategoryTokens,
      List<String> groundStructureCategoryTokens,
      List<String> undergroundStructureCategoryTokens,
      List<String> villageStructureCategoryTokens,
      List<String> strongholdStructureCategoryTokens
   ) {
      static final MapCodec<AdvancedGeneratorSettings.CategoryTokens> CODEC = RecordCodecBuilder.mapCodec(
         instance -> instance.group(
               Codec.STRING
                  .listOf()
                  .optionalFieldOf("water_structure_category_tokens", SkyIslandSettingLimits.DEFAULT_WATER_STRUCTURE_CATEGORY_TOKENS)
                  .forGetter(AdvancedGeneratorSettings.CategoryTokens::waterStructureCategoryTokens),
               Codec.STRING
                  .listOf()
                  .optionalFieldOf("sky_structure_category_tokens", SkyIslandSettingLimits.DEFAULT_SKY_STRUCTURE_CATEGORY_TOKENS)
                  .forGetter(AdvancedGeneratorSettings.CategoryTokens::skyStructureCategoryTokens),
               Codec.STRING
                  .listOf()
                  .optionalFieldOf("ground_structure_category_tokens", SkyIslandSettingLimits.DEFAULT_GROUND_STRUCTURE_CATEGORY_TOKENS)
                  .forGetter(AdvancedGeneratorSettings.CategoryTokens::groundStructureCategoryTokens),
               Codec.STRING
                  .listOf()
                  .optionalFieldOf("underground_structure_category_tokens", SkyIslandSettingLimits.DEFAULT_UNDERGROUND_STRUCTURE_CATEGORY_TOKENS)
                  .forGetter(AdvancedGeneratorSettings.CategoryTokens::undergroundStructureCategoryTokens),
               Codec.STRING
                  .listOf()
                  .optionalFieldOf("village_structure_category_tokens", SkyIslandSettingLimits.DEFAULT_VILLAGE_STRUCTURE_CATEGORY_TOKENS)
                  .forGetter(AdvancedGeneratorSettings.CategoryTokens::villageStructureCategoryTokens),
               Codec.STRING
                  .listOf()
                  .optionalFieldOf("stronghold_structure_category_tokens", SkyIslandSettingLimits.DEFAULT_STRONGHOLD_STRUCTURE_CATEGORY_TOKENS)
                  .forGetter(AdvancedGeneratorSettings.CategoryTokens::strongholdStructureCategoryTokens)
            )
            .apply(instance, AdvancedGeneratorSettings.CategoryTokens::new)
      );
   }
}
