package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.model.WaterAnchorProfile;

public final class WaterAnchorPlanner {
   public WaterAnchorPlanner.AnchorPlan plan(BoundingBox bounds, int targetX, int targetZ, int targetBodyY, WaterAnchorProfile anchorProfile) {
      int centerX = Mth.floor((bounds.minX() + bounds.maxX()) * 0.5);
      int centerZ = Mth.floor((bounds.minZ() + bounds.maxZ()) * 0.5);
      return new WaterAnchorPlanner.AnchorPlan(
         targetX, targetZ, targetBodyY, targetX - centerX, anchorProfile.verticalOffsetTo(bounds, targetBodyY), targetZ - centerZ
      );
   }

   public WaterAnchorPlanner.AnchorPlan plan(BoundingBox bounds, int targetX, int targetZ, int targetBodyY) {
      return this.plan(bounds, targetX, targetZ, targetBodyY, WaterAnchorProfile.defaultProfile());
   }

   public record AnchorPlan(int targetX, int targetZ, int targetBodyY, int offsetX, int offsetY, int offsetZ) {
      public int targetBaseY() {
         return this.targetBodyY;
      }
   }
}
