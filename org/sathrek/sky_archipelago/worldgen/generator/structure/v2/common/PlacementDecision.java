package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common;

import net.minecraft.world.level.levelgen.structure.StructureStart;

public record PlacementDecision(boolean accepted, String stage, String details, StructureStart structureStart) {
   public static PlacementDecision accepted(String stage, String details, StructureStart structureStart) {
      return new PlacementDecision(true, stage, details, structureStart);
   }

   public static PlacementDecision rejected(String stage, String details) {
      return new PlacementDecision(false, stage, details, null);
   }
}
