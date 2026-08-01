package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.structure.WaterPlacementMode;

public final class DynamicWaterStructureAdapters {
   private static final List<DynamicWaterStructureAdapter> ADAPTERS = List.of();

   private DynamicWaterStructureAdapters() {
   }

   public static Optional<DynamicWaterStructureAdapter> find(ResourceLocation structureId, WaterPlacementMode mode, BoundingBox bounds) {
      return ADAPTERS.stream().filter(adapter -> adapter.supports(structureId, mode, bounds)).findFirst();
   }
}
