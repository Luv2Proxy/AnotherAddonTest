package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public interface OccupiedVolumeEnvelopeBuilder {
   BoundingBox envelope(StructureStart var1, BoundingBox var2);
}
