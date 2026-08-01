package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.model.WaterAnchorProfile;
import org.sathrek.sky_archipelago.worldgen.structure.WaterPlacementMode;

public final class OceanMonumentWaterAdapter implements DynamicWaterStructureAdapter {
   public static final ResourceLocation STRUCTURE_ID = ResourceLocation.parse("minecraft:monument");
   public static final ResourceLocation ADAPTER_ID = ResourceLocation.parse("sky_archipelago:ocean_monument");
   public static final int BODY_ANCHOR_LOCAL_OFFSET_Y = 8;
   public static final int TOP_ONLY_CUTOFF_BELOW_TOP_BLOCKS = 23;
   public static final int CLEANUP_FOOTPRINT_MARGIN_BLOCKS = 4;
   public static final int PAD_SMOOTHING_MARGIN_BLOCKS = 32;
   private static final int VANILLA_MONUMENT_WATER_TOP_Y = 64;

   @Override
   public ResourceLocation adapterId() {
      return ADAPTER_ID;
   }

   @Override
   public boolean supports(ResourceLocation structureId, WaterPlacementMode mode, BoundingBox bounds) {
      return mode == WaterPlacementMode.OCEAN_FLOOR && STRUCTURE_ID.equals(structureId);
   }

   @Override
   public WaterAnchorProfile anchorProfile(ResourceLocation structureId, WaterPlacementMode mode) {
      return new WaterAnchorProfile(8);
   }

   @Override
   public int cleanupFootprintMarginBlocks() {
      return 4;
   }

   @Override
   public int padSmoothingMarginBlocks() {
      return 32;
   }

   @Override
   public int waterTopY(SkyIslandSettings settings) {
      return settings.terrain().ocean().oceanLevelY();
   }

   @Override
   public int cleanupBottomY(BoundingBox finalBounds, int bodyFloorY, int minBuildY) {
      return Math.max(minBuildY, Math.min(finalBounds.minY(), bodyFloorY) - 64);
   }

   @Override
   public int cleanupTopY(BoundingBox finalBounds, int waterTopY) {
      return Math.max(Math.max(finalBounds.maxY(), waterTopY), 64);
   }

   @Override
   public int topOnlyCutoffY(BoundingBox finalBounds, int bodyFloorY) {
      return finalBounds.maxY() - 23;
   }
}
