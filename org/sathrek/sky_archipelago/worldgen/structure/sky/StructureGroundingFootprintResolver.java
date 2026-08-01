package org.sathrek.sky_archipelago.worldgen.structure.sky;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public final class StructureGroundingFootprintResolver {
   private static final ResourceLocation PILLAGER_OUTPOST = ResourceLocation.parse("minecraft:pillager_outpost");

   public StructureFootprint resolve(ResourceLocation structureId, StructureFootprint fallback, StructureStart structureStart) {
      if (!PILLAGER_OUTPOST.equals(structureId)) {
         return fallback;
      }

      BoundingBox bounds = structureStart.getBoundingBox();
      return new StructureFootprint(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
   }
}
