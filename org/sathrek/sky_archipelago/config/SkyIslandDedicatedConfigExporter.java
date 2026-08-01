package org.sathrek.sky_archipelago.config;

import java.util.List;
import java.util.Locale;

public final class SkyIslandDedicatedConfigExporter {
   private SkyIslandDedicatedConfigExporter() {
   }

   public static String toDedicatedConfigSnippet(SkyIslandGeneratorSettings settings) {
      StringBuilder builder = new StringBuilder();
      appendLine(builder, "islandDensity", formatDouble(settings.terrain().islandDensity()));
      appendLine(builder, "minIslandRadius", Integer.toString(settings.terrain().minIslandRadius()));
      appendLine(builder, "maxIslandRadius", Integer.toString(settings.terrain().maxIslandRadius()));
      appendLine(builder, "islandSizeMode", quoted(settings.terrain().islandSize().islandSizeMode().name()));
      appendLine(builder, "smallIslandMinRadius", Integer.toString(settings.terrain().islandSize().smallIslandSizeBand().minRadius()));
      appendLine(builder, "smallIslandMaxRadius", Integer.toString(settings.terrain().islandSize().smallIslandSizeBand().maxRadius()));
      appendLine(builder, "smallIslandWeight", formatDouble(settings.terrain().islandSize().smallIslandSizeBand().weight()));
      appendLine(builder, "mediumIslandMinRadius", Integer.toString(settings.terrain().islandSize().mediumIslandSizeBand().minRadius()));
      appendLine(builder, "mediumIslandMaxRadius", Integer.toString(settings.terrain().islandSize().mediumIslandSizeBand().maxRadius()));
      appendLine(builder, "mediumIslandWeight", formatDouble(settings.terrain().islandSize().mediumIslandSizeBand().weight()));
      appendLine(builder, "largeIslandMinRadius", Integer.toString(settings.terrain().islandSize().largeIslandSizeBand().minRadius()));
      appendLine(builder, "largeIslandMaxRadius", Integer.toString(settings.terrain().islandSize().largeIslandSizeBand().maxRadius()));
      appendLine(builder, "largeIslandWeight", formatDouble(settings.terrain().islandSize().largeIslandSizeBand().weight()));
      appendLine(builder, "minIslandY", Integer.toString(settings.terrain().minIslandY()));
      appendLine(builder, "maxIslandY", Integer.toString(settings.terrain().maxIslandY()));
      appendLine(builder, "lowBandWeight", formatDouble(settings.terrain().lowBandWeight()));
      appendLine(builder, "midHighBandWeight", formatDouble(settings.terrain().midHighBandWeight()));
      appendLine(builder, "veryHighBandWeight", formatDouble(settings.terrain().veryHighBandWeight()));
      appendLine(builder, "lowBandCenterOffset", Integer.toString(settings.terrain().lowBandCenterOffset()));
      appendLine(builder, "veryHighBandCenterOffset", Integer.toString(settings.terrain().veryHighBandCenterOffset()));
      appendLine(builder, "clusterSpacingMode", quoted(settings.terrain().spacing().clusterSpacingMode().name()));
      appendLine(builder, "clusterSpacing", Integer.toString(settings.terrain().spacing().clusterSpacing()));
      appendLine(builder, "minClusterSpacing", Integer.toString(settings.terrain().spacing().minClusterSpacing()));
      appendLine(builder, "maxClusterSpacing", Integer.toString(settings.terrain().spacing().maxClusterSpacing()));
      appendLine(builder, "terrainReliefScale", formatDouble(settings.terrain().terrainReliefScale()));
      appendLine(builder, "classicArchetypeEnabled", Boolean.toString(settings.terrain().archetypes().classicArchetypeEnabled()));
      appendLine(builder, "classicArchetypeWeight", formatDouble(settings.terrain().archetypes().classicArchetypeWeight()));
      appendLine(builder, "bowlCraterArchetypeEnabled", Boolean.toString(settings.terrain().archetypes().bowlCraterArchetypeEnabled()));
      appendLine(builder, "bowlCraterArchetypeWeight", formatDouble(settings.terrain().archetypes().bowlCraterArchetypeWeight()));
      appendLine(builder, "crescentArchetypeEnabled", Boolean.toString(settings.terrain().archetypes().crescentArchetypeEnabled()));
      appendLine(builder, "crescentArchetypeWeight", formatDouble(settings.terrain().archetypes().crescentArchetypeWeight()));
      appendLine(builder, "terraceArchetypeEnabled", Boolean.toString(settings.terrain().archetypes().terraceArchetypeEnabled()));
      appendLine(builder, "terraceArchetypeWeight", formatDouble(settings.terrain().archetypes().terraceArchetypeWeight()));
      appendLine(builder, "oceanEnabled", Boolean.toString(settings.terrain().ocean().oceanEnabled()));
      appendLine(builder, "oceanLevelY", Integer.toString(settings.terrain().ocean().oceanLevelY()));
      appendLine(builder, "oceanFloorNoiseEnabled", Boolean.toString(settings.terrain().ocean().oceanFloorNoiseEnabled()));
      appendLine(builder, "oceanFloorBaseOffset", Integer.toString(settings.terrain().ocean().oceanFloorBaseOffset()));
      appendLine(builder, "oceanFloorNoiseAmplitude", Integer.toString(settings.terrain().ocean().oceanFloorNoiseAmplitude()));
      appendLine(builder, "oceanFloorNoiseScale", formatDouble(settings.terrain().ocean().oceanFloorNoiseScale()));
      appendLine(builder, "oceanFloorMinDepth", Integer.toString(settings.terrain().ocean().oceanFloorMinDepth()));
      appendLine(builder, "oceanFloorMaxDepth", Integer.toString(settings.terrain().ocean().oceanFloorMaxDepth()));
      appendLine(builder, "oceanBlockType", quoted(settings.advanced().oceanBlockType().serializedName()));
      appendLine(builder, "supportCheckDepth", Integer.toString(settings.structureSupport().supportCheckDepth()));
      appendLine(builder, "supportSampleGridSize", Integer.toString(settings.structureSupport().supportSampleGridSize()));
      appendLine(builder, "supportThreshold", formatDouble(settings.structureSupport().supportThreshold()));
      appendLine(builder, "surfaceSkySupportThreshold", formatDouble(settings.surfaceSky().supportThreshold()));
      appendLine(builder, "smallSkySupportThreshold", formatDouble(settings.smallSky().supportThreshold()));
      appendLine(builder, "surfaceSkyFootprintInsetRatio", formatDouble(settings.surfaceSky().footprintInsetRatio()));
      appendLine(builder, "smallSkyFootprintInsetRatio", formatDouble(settings.smallSky().footprintInsetRatio()));
      appendLine(builder, "surfaceSkySearchRadiusChunks", Integer.toString(settings.surfaceSky().searchRadiusChunks()));
      appendLine(builder, "smallSkySearchRadiusChunks", Integer.toString(settings.smallSky().searchRadiusChunks()));
      appendLine(builder, "surfaceSkyMinStableTopCells", Integer.toString(settings.surfaceSky().minStableTopCells()));
      appendLine(builder, "smallSkyMinStableTopCells", Integer.toString(settings.smallSky().minStableTopCells()));
      appendLine(builder, "surfaceSkyTopOffset", Integer.toString(settings.surfaceSky().topOffset()));
      appendLine(builder, "smallSkyTopOffset", Integer.toString(settings.smallSky().topOffset()));
      appendLine(builder, "surfaceSkyLocalSearchStepBlocks", Integer.toString(settings.surfaceSky().localSearchStepBlocks()));
      appendLine(builder, "smallSkyLocalSearchStepBlocks", Integer.toString(settings.smallSky().localSearchStepBlocks()));
      appendLine(builder, "surfaceSkyLocalSearchRadiusBlocks", Integer.toString(settings.surfaceSky().localSearchRadiusBlocks()));
      appendLine(builder, "smallSkyLocalSearchRadiusBlocks", Integer.toString(settings.smallSky().localSearchRadiusBlocks()));
      appendLine(builder, "surfaceSkyGroundedSampleThreshold", formatDouble(settings.surfaceSky().groundedSampleThreshold()));
      appendLine(builder, "smallSkyGroundedSampleThreshold", formatDouble(settings.smallSky().groundedSampleThreshold()));
      appendLine(builder, "surfaceSkyMaxGroundGapBlocks", Integer.toString(settings.surfaceSky().maxGroundGapBlocks()));
      appendLine(builder, "smallSkyMaxGroundGapBlocks", Integer.toString(settings.smallSky().maxGroundGapBlocks()));
      appendLine(builder, "surfaceSkyMinHostIslandRadius", Integer.toString(settings.surfaceSky().minHostIslandRadius()));
      appendLine(builder, "smallSkyMinHostIslandRadius", Integer.toString(settings.smallSky().minHostIslandRadius()));
      appendLine(builder, "surfaceSkyMinHostStableTopCells", Integer.toString(settings.surfaceSky().minHostStableTopCells()));
      appendLine(builder, "smallSkyMinHostStableTopCells", Integer.toString(settings.smallSky().minHostStableTopCells()));
      appendLine(builder, "defaultNearMissFallbackEnabled", Boolean.toString(settings.advanced().defaultNearMissFallbackEnabled()));
      appendLine(builder, "customStructureRulesEnabled", Boolean.toString(settings.advanced().customStructureRulesEnabled()));
      appendLine(builder, "clusterCompanionIslandsEnabled", Boolean.toString(settings.advanced().clusterCompanionIslandsEnabled()));
      appendLine(builder, "anchorFragmentationEnabled", Boolean.toString(settings.advanced().anchorFragmentationEnabled()));
      appendLine(builder, "disableIslandsOverOceanBiomes", Boolean.toString(settings.advanced().disableIslandsOverOceanBiomes()));
      appendLine(builder, "deepslateStartY", Integer.toString(settings.advanced().deepslateStartY()));
      appendLine(builder, "terrainOverlapMode", quoted(settings.advanced().terrainOverlapMode().name()));
      appendLine(builder, "skyStructureWhitelist", formatStringList(settings.advanced().structureWhitelistEntries()));
      appendLine(builder, "skyStructureDenylist", formatStringList(settings.advanced().structureDenylistEntries()));
      appendLine(builder, "skyStructureCategoryOverrides", formatStringList(settings.advanced().structureCategoryOverrideEntries()));
      appendLine(builder, "waterStructureCategoryTokens", formatStringList(settings.advanced().waterStructureCategoryTokens()));
      appendLine(builder, "skyStructureCategoryTokens", formatStringList(settings.advanced().skyStructureCategoryTokens()));
      appendLine(builder, "groundStructureCategoryTokens", formatStringList(settings.advanced().groundStructureCategoryTokens()));
      appendLine(builder, "undergroundStructureCategoryTokens", formatStringList(settings.advanced().undergroundStructureCategoryTokens()));
      appendLine(builder, "villageStructureCategoryTokens", formatStringList(settings.advanced().villageStructureCategoryTokens()));
      appendLine(builder, "strongholdStructureCategoryTokens", formatStringList(settings.advanced().strongholdStructureCategoryTokens()));
      return builder.toString();
   }

   private static void appendLine(StringBuilder builder, String key, String value) {
      builder.append(key).append('=').append(value).append('\n');
   }

   private static String formatDouble(double value) {
      return String.format(Locale.ROOT, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
   }

   private static String formatStringList(List<String> values) {
      if (values.isEmpty()) {
         return "[]";
      }

      StringBuilder builder = new StringBuilder("[");

      for (int i = 0; i < values.size(); i++) {
         if (i > 0) {
            builder.append(", ");
         }

         builder.append(quoted(values.get(i)));
      }

      builder.append(']');
      return builder.toString();
   }

   private static String quoted(String value) {
      return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
   }
}
