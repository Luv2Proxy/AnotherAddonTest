package org.sathrek.sky_archipelago.config.settings;

public record OceanSettings(
   boolean oceanEnabled,
   int oceanLevelY,
   boolean oceanFloorNoiseEnabled,
   int oceanFloorBaseOffset,
   int oceanFloorNoiseAmplitude,
   double oceanFloorNoiseScale,
   int oceanFloorMinDepth,
   int oceanFloorMaxDepth
) {
   public OceanSettings {
      if (oceanLevelY < -64 || oceanLevelY > 2000) {
         throw new IllegalArgumentException("oceanLevelY must be between -64 and 2000");
      }

      if (oceanFloorBaseOffset < 0 || oceanFloorBaseOffset > 2000) {
         throw new IllegalArgumentException("oceanFloorBaseOffset must be between 0 and 2000");
      }

      if (oceanFloorNoiseAmplitude < 0 || oceanFloorNoiseAmplitude > 2000) {
         throw new IllegalArgumentException("oceanFloorNoiseAmplitude must be between 0 and 2000");
      }

      if (oceanFloorNoiseScale < 0.001 || oceanFloorNoiseScale > 1.0) {
         throw new IllegalArgumentException("oceanFloorNoiseScale must be between 0.001 and 1.0");
      }

      if (oceanFloorMinDepth < 1 || oceanFloorMinDepth > 2000) {
         throw new IllegalArgumentException("oceanFloorMinDepth must be between 1 and 2000");
      }

      if (oceanFloorMaxDepth < 1 || oceanFloorMaxDepth > 2000) {
         throw new IllegalArgumentException("oceanFloorMaxDepth must be between 1 and 2000");
      }

      if (oceanFloorMaxDepth < oceanFloorMinDepth) {
         throw new IllegalArgumentException("oceanFloorMaxDepth must be >= oceanFloorMinDepth");
      }
   }

   public static OceanSettings defaults() {
      return new OceanSettings(false, 0, true, 18, 10, 0.05, 8, 40);
   }
}
