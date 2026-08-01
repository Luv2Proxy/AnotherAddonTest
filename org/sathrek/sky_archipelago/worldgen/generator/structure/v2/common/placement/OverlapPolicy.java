package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public interface OverlapPolicy {
   boolean conflicts(BoundingBox var1, StructurePlacementCategory var2, BoundingBox var3, StructurePlacementCategory var4);
}
