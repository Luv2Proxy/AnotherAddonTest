package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.policy;

import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class StructurePlacementPolicies {
   public static final StructurePlacementPolicy SMALL_SKY = new StructurePlacementPolicy(160, 24, 1, 0.85, 3, 5, 8, 3, 1);
   public static final StructurePlacementPolicy SURFACE_SKY = new StructurePlacementPolicy(192, 36, 1, 0.85, 3, 8, 15, 1, 1);
   public static final StructurePlacementPolicy HAMLET_SKY = new StructurePlacementPolicy(256, 52, 1, 0.85, 3, 12, 24, 1, 1);
   public static final StructurePlacementPolicy GROUND_VILLAGE = new StructurePlacementPolicy(320, 72, 1, 0.8, 4, 18, 32, 1, 1);

   private StructurePlacementPolicies() {
   }

   public static StructurePlacementPolicy forCategory(StructurePlacementCategory category) {
      return switch (category) {
         case SMALL_SKY -> SMALL_SKY;
         case SURFACE_SKY -> SURFACE_SKY;
         case HAMLET_SKY -> HAMLET_SKY;
         case GROUND_VILLAGE -> GROUND_VILLAGE;
         case DEFAULT, SKY, STRONGHOLD, UNDERGROUND, WATER -> null;
      };
   }
}
