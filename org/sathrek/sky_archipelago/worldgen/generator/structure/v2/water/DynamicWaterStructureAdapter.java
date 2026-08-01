package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.model.WaterAnchorProfile;
import org.sathrek.sky_archipelago.worldgen.structure.WaterPlacementMode;

public interface DynamicWaterStructureAdapter {
   ResourceLocation adapterId();

   boolean supports(ResourceLocation var1, WaterPlacementMode var2, BoundingBox var3);

   WaterAnchorProfile anchorProfile(ResourceLocation var1, WaterPlacementMode var2);

   int cleanupFootprintMarginBlocks();

   int padSmoothingMarginBlocks();

   int waterTopY(SkyIslandSettings var1);

   int cleanupBottomY(BoundingBox var1, int var2, int var3);

   int cleanupTopY(BoundingBox var1, int var2);

   int topOnlyCutoffY(BoundingBox var1, int var2);
}
