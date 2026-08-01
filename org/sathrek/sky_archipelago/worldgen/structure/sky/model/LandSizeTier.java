package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

public enum LandSizeTier {
   SMALL,
   MEDIUM,
   LARGE;

   public static LandSizeTier forArea(int area) {
      if (area <= 256) {
         return SMALL;
      } else {
         return area <= 1024 ? MEDIUM : LARGE;
      }
   }

   public LandBudget budget() {
      return switch (this) {
         case SMALL -> new LandBudget(24, 4, 4, 96, 64, 4, 1, 3, 8, 0.32, 0.08, 0.42, 0.25, 0, 0);
         case MEDIUM -> new LandBudget(32, 4, 4, 180, 120, 8, 2, 5, 12, 0.26, 0.08, 0.38, 0.32, 0, 0);
         case LARGE -> new LandBudget(76, 8, 4, 520, 320, 16, 5, 12, 22, 0.2, 0.06, 0.36, 0.4, 6, 8);
      };
   }
}
