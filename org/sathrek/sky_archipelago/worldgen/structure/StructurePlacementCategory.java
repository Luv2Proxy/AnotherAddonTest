package org.sathrek.sky_archipelago.worldgen.structure;

import java.util.Locale;

public enum StructurePlacementCategory {
   DEFAULT,
   SKY,
   SURFACE_SKY,
   SMALL_SKY,
   HAMLET_SKY,
   GROUND_VILLAGE,
   STRONGHOLD,
   UNDERGROUND,
   WATER;

   public boolean usesIslandAwarePlacement() {
      return this == SURFACE_SKY
         || this == SMALL_SKY
         || this == HAMLET_SKY
         || this == GROUND_VILLAGE
         || this == STRONGHOLD
         || this == UNDERGROUND
         || this == WATER;
   }

   public String externalName() {
      return switch (this) {
         case DEFAULT, SKY, STRONGHOLD, UNDERGROUND, WATER -> this.name();
         case SURFACE_SKY -> "MEDIUM_GROUND";
         case SMALL_SKY -> "SMALL_GROUND";
         case HAMLET_SKY -> "LARGE_GROUND";
         case GROUND_VILLAGE -> "GROUND_VILLAGE";
      };
   }

   public static StructurePlacementCategory tryParse(String value) {
      if (value == null) {
         return null;
      }

      String normalized = value.trim().toUpperCase(Locale.ROOT);

      normalized = switch (normalized) {
         case "SMALL_GROUND" -> "SMALL_SKY";
         case "MEDIUM_GROUND" -> "SURFACE_SKY";
         case "LARGE_GROUND" -> "HAMLET_SKY";
         case "VILLAGE_GROUND" -> "GROUND_VILLAGE";
         case "STRONGHOLD_GROUND" -> "STRONGHOLD";
         default -> normalized;
      };

      try {
         return valueOf(normalized);
      } catch (IllegalArgumentException ignored) {
         return null;
      }
   }
}
