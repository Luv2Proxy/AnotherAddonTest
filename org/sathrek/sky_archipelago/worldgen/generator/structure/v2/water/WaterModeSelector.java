package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water;

import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.WaterPlacementMode;

public final class WaterModeSelector {
   public WaterPlacementMode select(StructurePlacementPolicy policy, ResourceLocation structureId) {
      return policy.waterModeFor(structureId);
   }
}
