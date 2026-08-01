package org.sathrek.sky_archipelago.worldgen.generator.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.config.settings.SkyIslandSettingsFactory;

public final class WorldHeightClampResolver {
   private static final boolean LOGGING_ENABLED = false;
   private static final int DEFAULT_HEIGHT = 320;
   private static final int MIN_EFFECTIVE_WORLD_MAX_Y = -64;
   private static final Set<String> WARNED_KEYS = ConcurrentHashMap.newKeySet();

   private WorldHeightClampResolver() {
   }

   public static int effectiveWorldMaxY(int maxBuildHeightExclusive) {
      return Math.max(-64, maxBuildHeightExclusive - 1);
   }

   public static SkyIslandSettings clampSettingsToWorld(SkyIslandSettings settings, int maxBuildHeightExclusive) {
      int worldMaxY = effectiveWorldMaxY(maxBuildHeightExclusive);
      int clampedMaxIslandY = Math.min(settings.terrain().maxIslandY(), worldMaxY);
      int clampedMinIslandY = Math.min(settings.terrain().minIslandY(), clampedMaxIslandY);
      int clampedOceanLevelY = Math.min(settings.terrain().ocean().oceanLevelY(), worldMaxY);
      int clampedDeepslateStartY = Math.min(settings.advanced().deepslateStartY(), worldMaxY);
      return SkyIslandSettingsFactory.runtime(
         settings.terrain().islandDensity(),
         settings.terrain().minIslandRadius(),
         settings.terrain().maxIslandRadius(),
         settings.terrain().islandSize().islandSizeMode(),
         settings.terrain().islandSize().smallIslandSizeBand(),
         settings.terrain().islandSize().mediumIslandSizeBand(),
         settings.terrain().islandSize().largeIslandSizeBand(),
         clampedMinIslandY,
         clampedMaxIslandY,
         settings.terrain().maxIslandThicknessBlocks(),
         settings.terrain().lowBandWeight(),
         settings.terrain().midHighBandWeight(),
         settings.terrain().veryHighBandWeight(),
         settings.terrain().lowBandCenterOffset(),
         settings.terrain().veryHighBandCenterOffset(),
         settings.terrain().spacing().clusterSpacingMode(),
         settings.terrain().spacing().clusterSpacing(),
         settings.terrain().spacing().minClusterSpacing(),
         settings.terrain().spacing().maxClusterSpacing(),
         settings.terrain().terrainReliefScale(),
         settings.terrain().archetypes().classicArchetypeEnabled(),
         settings.terrain().archetypes().classicArchetypeWeight(),
         settings.terrain().archetypes().bowlCraterArchetypeEnabled(),
         settings.terrain().archetypes().bowlCraterArchetypeWeight(),
         settings.terrain().archetypes().crescentArchetypeEnabled(),
         settings.terrain().archetypes().crescentArchetypeWeight(),
         settings.terrain().archetypes().terraceArchetypeEnabled(),
         settings.terrain().archetypes().terraceArchetypeWeight(),
         settings.terrain().ocean().oceanEnabled(),
         clampedOceanLevelY,
         settings.terrain().ocean().oceanFloorNoiseEnabled(),
         settings.terrain().ocean().oceanFloorBaseOffset(),
         settings.terrain().ocean().oceanFloorNoiseAmplitude(),
         settings.terrain().ocean().oceanFloorNoiseScale(),
         settings.terrain().ocean().oceanFloorMinDepth(),
         settings.terrain().ocean().oceanFloorMaxDepth(),
         settings.advanced().oceanBlockType(),
         settings.structureSupport().supportCheckDepth(),
         settings.structureSupport().supportSampleGridSize(),
         settings.structureSupport().supportThreshold(),
         settings.surfaceSky().supportThreshold(),
         settings.smallSky().supportThreshold(),
         settings.surfaceSky().footprintInsetRatio(),
         settings.smallSky().footprintInsetRatio(),
         settings.surfaceSky().searchRadiusChunks(),
         settings.smallSky().searchRadiusChunks(),
         settings.surfaceSky().minStableTopCells(),
         settings.smallSky().minStableTopCells(),
         settings.surfaceSky().topOffset(),
         settings.smallSky().topOffset(),
         settings.surfaceSky().localSearchStepBlocks(),
         settings.smallSky().localSearchStepBlocks(),
         settings.surfaceSky().localSearchRadiusBlocks(),
         settings.smallSky().localSearchRadiusBlocks(),
         settings.surfaceSky().groundedSampleThreshold(),
         settings.smallSky().groundedSampleThreshold(),
         settings.surfaceSky().maxGroundGapBlocks(),
         settings.smallSky().maxGroundGapBlocks(),
         settings.surfaceSky().minHostIslandRadius(),
         settings.smallSky().minHostIslandRadius(),
         settings.surfaceSky().minHostStableTopCells(),
         settings.smallSky().minHostStableTopCells(),
         settings.advanced().defaultNearMissFallbackEnabled(),
         settings.advanced().customStructureRulesEnabled(),
         settings.advanced().clusterCompanionIslandsEnabled(),
         settings.advanced().anchorFragmentationEnabled(),
         settings.advanced().disableIslandsOverOceanBiomes(),
         clampedDeepslateStartY,
         settings.advanced().biomeProfileBlendingEnabled(),
         settings.advanced().biomeProfileBlendingRadiusBlocks(),
         settings.advanced().biomeProfileBlendingQuantizationSteps(),
         settings.advanced().biomeProfileBlendingBoundaryOnly(),
         settings.advanced().terrainOverlapMode(),
         settings.advanced().structureWhitelist(),
         settings.advanced().structurePlacementPolicy()
      );
   }

   public static void logLegacyHeightNoticeIfNeeded(SkyIslandSettings settings) {
   }

   public static void logWorldClampIfNeeded(SkyIslandSettings configured, SkyIslandSettings clamped, int maxBuildHeightExclusive) {
   }
}
