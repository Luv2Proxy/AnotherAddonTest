package org.sathrek.sky_archipelago.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.StreamSupport;
import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.config.settings.AdvancedGeneratorSettings;
import org.sathrek.sky_archipelago.config.settings.AdvancedRuntimeSettings;
import org.sathrek.sky_archipelago.config.settings.StructureCategorySettings;
import org.sathrek.sky_archipelago.config.settings.StructureSupportSettings;
import org.sathrek.sky_archipelago.config.settings.TerrainSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.StructureWhitelist;

public record SkyIslandGeneratorSettings(
   TerrainSettings terrain,
   StructureSupportSettings structureSupport,
   StructureCategorySettings surfaceSky,
   StructureCategorySettings smallSky,
   AdvancedGeneratorSettings advanced
) {
   public static final Codec<SkyIslandGeneratorSettings> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            TerrainSettings.CODEC.fieldOf("terrain").forGetter(SkyIslandGeneratorSettings::terrain),
            StructureSupportSettings.CODEC.fieldOf("structure_support").forGetter(SkyIslandGeneratorSettings::structureSupport),
            StructureCategorySettings.CODEC.fieldOf("surface_sky").forGetter(SkyIslandGeneratorSettings::surfaceSky),
            StructureCategorySettings.CODEC.fieldOf("small_sky").forGetter(SkyIslandGeneratorSettings::smallSky),
            AdvancedGeneratorSettings.CODEC.fieldOf("advanced").forGetter(SkyIslandGeneratorSettings::advanced)
         )
         .apply(instance, SkyIslandGeneratorSettings::new)
   );

   public SkyIslandGeneratorSettings {
      if (terrain == null) {
         throw new IllegalArgumentException("terrain cannot be null");
      }

      if (structureSupport == null) {
         throw new IllegalArgumentException("structureSupport cannot be null");
      }

      if (surfaceSky == null) {
         throw new IllegalArgumentException("surfaceSky cannot be null");
      }

      if (smallSky == null) {
         throw new IllegalArgumentException("smallSky cannot be null");
      }

      if (advanced == null) {
         throw new IllegalArgumentException("advanced cannot be null");
      }
   }

   public SkyIslandSettings toRuntimeSettings() {
      return new SkyIslandSettings(
         this.terrain,
         this.structureSupport,
         this.surfaceSky,
         this.smallSky,
         new AdvancedRuntimeSettings(
            this.advanced.defaultNearMissFallbackEnabled(),
            this.advanced.customStructureRulesEnabled(),
            this.advanced.clusterCompanionIslandsEnabled(),
            this.advanced.anchorFragmentationEnabled(),
            this.advanced.disableIslandsOverOceanBiomes(),
            this.advanced.deepslateStartY(),
            this.advanced.biomeProfileBlendingEnabled(),
            this.advanced.biomeProfileBlendingRadiusBlocks(),
            this.advanced.biomeProfileBlendingQuantizationSteps(),
            this.advanced.biomeProfileBlendingBoundaryOnly(),
            this.advanced.terrainOverlapMode(),
            this.advanced.oceanBlockType(),
            StructureWhitelist.fromStrings(this.advanced.structureWhitelistEntries()),
            StructurePlacementPolicy.fromConfig(
               this.advanced.structureDenylistEntries(),
               this.advanced.structureCategoryOverrideEntries(),
               this.advanced.waterStructureCategoryTokens(),
               this.advanced.skyStructureCategoryTokens(),
               this.advanced.groundStructureCategoryTokens(),
               this.advanced.undergroundStructureCategoryTokens(),
               this.advanced.villageStructureCategoryTokens(),
               this.advanced.strongholdStructureCategoryTokens(),
               this.surfaceSky.supportThreshold(),
               this.smallSky.supportThreshold(),
               this.surfaceSky.footprintInsetRatio(),
               this.smallSky.footprintInsetRatio(),
               this.surfaceSky.searchRadiusChunks(),
               this.smallSky.searchRadiusChunks(),
               this.surfaceSky.minStableTopCells(),
               this.smallSky.minStableTopCells(),
               this.surfaceSky.topOffset(),
               this.smallSky.topOffset(),
               this.surfaceSky.localSearchStepBlocks(),
               this.smallSky.localSearchStepBlocks(),
               this.surfaceSky.localSearchRadiusBlocks(),
               this.smallSky.localSearchRadiusBlocks(),
               this.surfaceSky.groundedSampleThreshold(),
               this.smallSky.groundedSampleThreshold(),
               this.surfaceSky.maxGroundGapBlocks(),
               this.smallSky.maxGroundGapBlocks(),
               this.surfaceSky.minHostIslandRadius(),
               this.smallSky.minHostIslandRadius(),
               this.surfaceSky.minHostStableTopCells(),
               this.smallSky.minHostStableTopCells(),
               this.advanced.defaultNearMissFallbackEnabled()
            )
         )
      );
   }

   public static SkyIslandGeneratorSettings fromRuntimeSettings(SkyIslandSettings settings) {
      return new SkyIslandGeneratorSettings(
         settings.terrain(),
         settings.structureSupport(),
         settings.surfaceSky(),
         settings.smallSky(),
         new AdvancedGeneratorSettings(
            settings.advanced().defaultNearMissFallbackEnabled(),
            settings.advanced().customStructureRulesEnabled(),
            settings.advanced().clusterCompanionIslandsEnabled(),
            settings.advanced().anchorFragmentationEnabled(),
            settings.advanced().disableIslandsOverOceanBiomes(),
            settings.advanced().deepslateStartY(),
            settings.advanced().biomeProfileBlendingEnabled(),
            settings.advanced().biomeProfileBlendingRadiusBlocks(),
            settings.advanced().biomeProfileBlendingQuantizationSteps(),
            settings.advanced().biomeProfileBlendingBoundaryOnly(),
            settings.advanced().terrainOverlapMode(),
            settings.advanced().oceanBlockType(),
            sortedResourceStrings(settings.advanced().structureWhitelist().entries()),
            sortedResourceStrings(settings.advanced().structurePlacementPolicy().denylistedStructures()),
            settings.advanced()
               .structurePlacementPolicy()
               .categoryOverrides()
               .entrySet()
               .stream()
               .map(entry -> entry.getKey() + "=" + entry.getValue().externalName())
               .sorted()
               .toList(),
            settings.advanced().structurePlacementPolicy().waterCategoryTokens(),
            settings.advanced().structurePlacementPolicy().skyCategoryTokens(),
            settings.advanced().structurePlacementPolicy().groundCategoryTokens(),
            settings.advanced().structurePlacementPolicy().undergroundCategoryTokens(),
            settings.advanced().structurePlacementPolicy().villageCategoryTokens(),
            settings.advanced().structurePlacementPolicy().strongholdCategoryTokens()
         )
      );
   }

   public boolean isArchetypeEnabled(IslandShapeArchetype archetype) {
      return this.terrain.archetypes().isEnabled(archetype);
   }

   public double archetypeWeight(IslandShapeArchetype archetype) {
      return this.terrain.archetypes().weight(archetype);
   }

   private static List<String> sortedResourceStrings(Iterable<ResourceLocation> resourceLocations) {
      return StreamSupport.stream(resourceLocations.spliterator(), false).<String>map(ResourceLocation::toString).sorted().toList();
   }
}
