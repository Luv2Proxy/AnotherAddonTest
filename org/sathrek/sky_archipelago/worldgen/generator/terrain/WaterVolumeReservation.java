package org.sathrek.sky_archipelago.worldgen.generator.terrain;

import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public record WaterVolumeReservation(
   long id,
   ResourceLocation structureId,
   ResourceLocation adapterId,
   StructureFootprint footprint,
   StructureFootprint cleanupFootprint,
   StructureFootprint smoothingFootprint,
   int bodyFloorY,
   int topOnlyCutoffY,
   int waterTopY,
   int structureTopY,
   int cleanupBottomY,
   int cleanupTopY,
   int smoothingMargin,
   long levelSeed,
   long reservedAtNanos
) {
}
