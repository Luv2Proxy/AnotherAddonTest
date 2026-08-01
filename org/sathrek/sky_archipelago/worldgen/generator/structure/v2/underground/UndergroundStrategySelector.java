package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground;

import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.worldgen.structure.underground.UndergroundPlacementBehavior;

public final class UndergroundStrategySelector {
   public UndergroundStrategySelector.Strategy select(UndergroundPlacementBehavior behavior, ResourceLocation structureId) {
      if (behavior != UndergroundPlacementBehavior.DYNAMIC_ANCHOR_FIRST) {
         return UndergroundStrategySelector.Strategy.STATIC_FOOTPRINT;
      } else {
         return structureId != null && structureId.getPath().contains("mineshaft")
            ? UndergroundStrategySelector.Strategy.DYNAMIC_MINESHAFT
            : UndergroundStrategySelector.Strategy.DYNAMIC_JIGSAW;
      }
   }

   public enum Strategy {
      DYNAMIC_MINESHAFT,
      DYNAMIC_JIGSAW,
      STATIC_FOOTPRINT;
   }
}
