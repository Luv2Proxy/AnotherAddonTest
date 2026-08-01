package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public interface ReservationReader {
   List<ReservedPlacement> candidatesForBounds(BoundingBox var1);
}
