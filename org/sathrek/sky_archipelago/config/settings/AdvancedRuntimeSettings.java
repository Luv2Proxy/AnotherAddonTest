package org.sathrek.sky_archipelago.config.settings;

import org.sathrek.sky_archipelago.config.OceanBlockType;
import org.sathrek.sky_archipelago.config.TerrainOverlapMode;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.StructureWhitelist;

public record AdvancedRuntimeSettings(
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
   StructureWhitelist structureWhitelist,
   StructurePlacementPolicy structurePlacementPolicy
) {
   public AdvancedRuntimeSettings {
      if (deepslateStartY < -64 || deepslateStartY > 2000) {
         throw new IllegalArgumentException("deepslateStartY must be between -64 and 2000");
      }

      if (biomeProfileBlendingRadiusBlocks < 1 || biomeProfileBlendingRadiusBlocks > 16) {
         throw new IllegalArgumentException("biomeProfileBlendingRadiusBlocks must be between 1 and 16");
      }

      if (biomeProfileBlendingQuantizationSteps < 2 || biomeProfileBlendingQuantizationSteps > 64) {
         throw new IllegalArgumentException("biomeProfileBlendingQuantizationSteps must be between 2 and 64");
      }

      if (terrainOverlapMode == null) {
         throw new IllegalArgumentException("terrainOverlapMode cannot be null");
      }

      if (oceanBlockType == null) {
         throw new IllegalArgumentException("oceanBlockType cannot be null");
      }

      if (structureWhitelist == null) {
         throw new IllegalArgumentException("structureWhitelist cannot be null");
      }

      if (structurePlacementPolicy == null) {
         throw new IllegalArgumentException("structurePlacementPolicy cannot be null");
      }
   }
}
