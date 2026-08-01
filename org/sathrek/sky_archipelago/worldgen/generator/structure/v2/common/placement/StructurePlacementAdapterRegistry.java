package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.resources.ResourceLocation;

public final class StructurePlacementAdapterRegistry {
   private static final StructurePlacementAdapter DEFAULT_ADAPTER = new DefaultStructurePlacementAdapter();

   private StructurePlacementAdapterRegistry() {
   }

   public static StructurePlacementAdapter adapterFor(ResourceLocation structureId) {
      return structureId == null ? DEFAULT_ADAPTER : DEFAULT_ADAPTER;
   }
}
