package org.sathrek.sky_archipelago.worldgen.structure;

import net.minecraft.world.level.levelgen.structure.StructureStart;

public interface AnchorResolverStrategy {
   AnchorResolverStrategy.DynamicAnchor resolve(StructureStart var1);

   record DynamicAnchor(int x, int baseY, int z, String source) {
   }
}
