package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.model;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.structure.WaterPlacementMode;

public record WaterAnchorProfile(int anchorLocalOffsetY) {
   private static final ResourceLocation MONUMENT_ID = ResourceLocation.parse("minecraft:monument");
   static final int MONUMENT_ANCHOR_LOCAL_OFFSET_Y = 8;

   public static WaterAnchorProfile forStructure(ResourceLocation structureId, WaterPlacementMode mode) {
      return mode == WaterPlacementMode.OCEAN_FLOOR && MONUMENT_ID.equals(structureId) ? new WaterAnchorProfile(8) : defaultProfile();
   }

   public static WaterAnchorProfile defaultProfile() {
      return new WaterAnchorProfile(0);
   }

   public int clampedAnchorLocalOffsetY(BoundingBox bounds) {
      int height = Math.max(0, bounds.maxY() - bounds.minY());
      return Math.max(0, Math.min(height, this.anchorLocalOffsetY));
   }

   public int anchorWorldY(BoundingBox bounds) {
      return bounds.minY() + this.clampedAnchorLocalOffsetY(bounds);
   }

   public int verticalOffsetTo(BoundingBox bounds, int targetBodyY) {
      return targetBodyY - this.anchorWorldY(bounds);
   }

   public int spanAboveAnchor(BoundingBox bounds) {
      return bounds.maxY() - this.anchorWorldY(bounds);
   }
}
