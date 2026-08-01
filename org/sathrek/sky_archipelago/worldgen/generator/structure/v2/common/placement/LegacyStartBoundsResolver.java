package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class LegacyStartBoundsResolver implements AnchorBoundsResolver {
   @Override
   public BoundingBox resolve(StructureStart structureStart) {
      return structureStart == null ? null : structureStart.getBoundingBox();
   }
}
