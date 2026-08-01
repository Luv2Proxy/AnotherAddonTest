package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class WaterConstraintEvaluator {
   public WaterConstraintEvaluator.ValidationResult validateCommon(boolean oceanEnabled, BoundingBox bounds) {
      if (!oceanEnabled) {
         return WaterConstraintEvaluator.ValidationResult.reject("rejected_water_v2_requires_ocean");
      } else {
         return bounds == null
            ? WaterConstraintEvaluator.ValidationResult.reject("rejected_water_v2_missing_bounds")
            : WaterConstraintEvaluator.ValidationResult.ok();
      }
   }

   public WaterConstraintEvaluator.ValidationResult validateModeProbe(WaterDepthProbe.ProbeResult probeResult) {
      return !probeResult.accepted()
         ? WaterConstraintEvaluator.ValidationResult.reject(probeResult.rejectionReason())
         : WaterConstraintEvaluator.ValidationResult.ok();
   }

   public record ValidationResult(boolean accepted, String reason) {
      static WaterConstraintEvaluator.ValidationResult ok() {
         return new WaterConstraintEvaluator.ValidationResult(true, null);
      }

      static WaterConstraintEvaluator.ValidationResult reject(String reason) {
         return new WaterConstraintEvaluator.ValidationResult(false, reason);
      }
   }
}
