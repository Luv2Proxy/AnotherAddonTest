package org.sathrek.sky_archipelago.config.settings;

import org.sathrek.sky_archipelago.config.ClusterSpacingMode;

public record ClusterSpacingSettings(ClusterSpacingMode clusterSpacingMode, int clusterSpacing, int minClusterSpacing, int maxClusterSpacing) {
   public ClusterSpacingSettings {
      if (clusterSpacingMode == null) {
         throw new IllegalArgumentException("clusterSpacingMode cannot be null");
      }

      if (clusterSpacing < 32 || clusterSpacing > 2000) {
         throw new IllegalArgumentException("clusterSpacing must be between 32 and 2000");
      }

      if (minClusterSpacing < 32 || minClusterSpacing > 2000) {
         throw new IllegalArgumentException("minClusterSpacing must be between 32 and 2000");
      }

      if (maxClusterSpacing < 32 || maxClusterSpacing > 2000) {
         throw new IllegalArgumentException("maxClusterSpacing must be between 32 and 2000");
      }

      if (maxClusterSpacing < minClusterSpacing) {
         throw new IllegalArgumentException("maxClusterSpacing must be >= minClusterSpacing");
      }
   }

   public static ClusterSpacingSettings defaults() {
      return new ClusterSpacingSettings(ClusterSpacingMode.CONSISTENT, 96, 96, 96);
   }
}
