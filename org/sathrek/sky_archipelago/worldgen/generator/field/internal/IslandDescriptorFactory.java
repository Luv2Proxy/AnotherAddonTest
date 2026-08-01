package org.sathrek.sky_archipelago.worldgen.generator.field.internal;

import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;

public final class IslandDescriptorFactory {
   private static final double EIGHTH_TURN = Math.PI / 4;
   private final IslandNoise noise;
   private final IslandClusterSampler clusterSampler;

   public IslandDescriptorFactory(IslandNoise noise, IslandClusterSampler clusterSampler) {
      this.noise = noise;
      this.clusterSampler = clusterSampler;
   }

   public IslandField.IslandDescriptor createAnchorDescriptor(IslandField.ClusterDescriptor cluster, SkyIslandSettings settings) {
      IslandField.SizeBandProfile sizeProfile = this.clusterSampler.sizeBandProfile(settings, cluster.sizeBand());

      double bandScale = switch (cluster.heightBand()) {
         case LOW -> 0.88;
         case MID_HIGH -> 1.0;
         case VERY_HIGH -> 1.1;
      };
      double familyScale = (cluster.tier() == IslandField.ClusterTier.GRAND ? 1.55 : (cluster.tier() == IslandField.ClusterTier.STANDARD ? 1.18 : 0.95))
         * bandScale;
      int radiusX = (int)Math.round(
         this.noise.sampleRange(cluster.seed(), 11, sizeProfile.minRadius() * familyScale, sizeProfile.maxRadius() * familyScale * 1.18)
      );
      int radiusZ = (int)Math.round(
         this.noise.sampleRange(cluster.seed(), 13, sizeProfile.minRadius() * familyScale, sizeProfile.maxRadius() * familyScale * 1.18)
      );
      if (cluster.archetype() == IslandShapeArchetype.CRESCENT) {
         int longestRadius = Math.max(radiusX, radiusZ);
         int shortestRadius = Math.min(radiusX, radiusZ);
         radiusX = Math.max(16, (int)Math.round(longestRadius * 1.05));
         radiusZ = Math.max(16, (int)Math.round(Math.max(shortestRadius * 1.02, longestRadius * 0.82)));
      }
      double verticalBias = switch (cluster.heightBand()) {
         case LOW -> 0.9;
         case MID_HIGH -> 1.0;
         case VERY_HIGH -> 1.08;
      };
      int plateauHeight = (int)Math.round(Math.max(12.0, ((radiusX + radiusZ) * 0.16 + this.noise.sampleRange(cluster.seed(), 17, 6.0, 14.0)) * verticalBias));
      int cliffDepth = (int)Math.round(Math.max(18.0, ((radiusX + radiusZ) * 0.22 + this.noise.sampleRange(cluster.seed(), 19, 12.0, 22.0)) * verticalBias));
      int hangDepth = (int)Math.round(
         Math.max(
            24.0,
            (Math.max(radiusX, radiusZ) * 0.85 + this.noise.sampleRange(cluster.seed(), 23, 20.0, 44.0))
               * (
                  cluster.heightBand() == IslandField.ClusterHeightBand.LOW
                     ? 0.84
                     : (cluster.heightBand() == IslandField.ClusterHeightBand.VERY_HIGH ? 1.04 : 1.0)
               )
         )
      );
      if (cluster.archetype() == IslandShapeArchetype.CRESCENT) {
         plateauHeight = Math.max(8, (int)Math.round(plateauHeight * 0.15));
         cliffDepth = Math.max(12, (int)Math.round(cliffDepth * 0.4));
         hangDepth = Math.max(16, (int)Math.round(hangDepth * 0.45));
      }

      int[] cappedHeights = capThickness(plateauHeight, cliffDepth, hangDepth, settings.terrain().maxIslandThicknessBlocks(), 8, 12, 16);
      plateauHeight = cappedHeights[0];
      cliffDepth = cappedHeights[1];
      hangDepth = cappedHeights[2];
      int hangOffsetX = this.noise.sampleInt(cluster.seed(), 31, -radiusX / 4, radiusX / 4);
      int hangOffsetZ = this.noise.sampleInt(cluster.seed(), 37, -radiusZ / 4, radiusZ / 4);
      int tailRadiusX = (int)Math.round(radiusX * this.noise.sampleRange(cluster.seed(), 41, 0.34, 0.58));
      int tailRadiusZ = (int)Math.round(radiusZ * this.noise.sampleRange(cluster.seed(), 43, 0.34, 0.58));
      double erosionStrength = this.noise.sampleRange(cluster.seed(), 47, 0.32, 0.54);
      int lobeCount = 6 + this.noise.sampleInt(cluster.seed(), 53, 0, 3);
      int peninsulaCount = 4 + this.noise.sampleInt(cluster.seed(), 57, 0, 2);
      int biteCount = 4 + this.noise.sampleInt(cluster.seed(), 61, 0, 2);
      if (cluster.archetype() == IslandShapeArchetype.CRESCENT) {
         hangOffsetX = this.noise.sampleInt(cluster.seed(), 31, -radiusX / 8, radiusX / 8);
         hangOffsetZ = this.noise.sampleInt(cluster.seed(), 37, -radiusZ / 8, radiusZ / 8);
         tailRadiusX = (int)Math.round(radiusX * this.noise.sampleRange(cluster.seed(), 41, 0.26, 0.4));
         tailRadiusZ = (int)Math.round(radiusZ * this.noise.sampleRange(cluster.seed(), 43, 0.22, 0.36));
         erosionStrength = this.noise.sampleRange(cluster.seed(), 47, 0.4, 0.58);
         lobeCount = 0;
         peninsulaCount = 0;
         biteCount = 0;
      }

      double rotation = this.anchorRotation(cluster);
      return new IslandField.IslandDescriptor(
         IslandField.IslandFamily.ANCHOR_PLATEAU,
         cluster.centerX(),
         cluster.centerY(),
         cluster.centerZ(),
         radiusX,
         radiusZ,
         Math.max(radiusX, radiusZ),
         rotation,
         cluster.archetype(),
         plateauHeight,
         cliffDepth,
         hangDepth,
         hangOffsetX,
         hangOffsetZ,
         tailRadiusX,
         tailRadiusZ,
         erosionStrength,
         lobeCount,
         peninsulaCount,
         biteCount,
         cluster.seed() ^ 25214903917L
      );
   }

   private double anchorRotation(IslandField.ClusterDescriptor cluster) {
      if (cluster.archetype() != IslandShapeArchetype.CRESCENT) {
         return cluster.baseRotation() + this.noise.sampleRange(cluster.seed(), 29, -0.45, 0.45);
      }

      int facingBucket = this.noise.sampleInt(cluster.seed(), 89, 0, 7);
      double jitter = this.noise.sampleRange(cluster.seed(), 97, -0.16, 0.16);
      return facingBucket * (Math.PI / 4) + jitter;
   }

   public IslandField.IslandDescriptor createSatelliteDescriptor(IslandField.ClusterDescriptor cluster, SkyIslandSettings settings, int index) {
      long seed = IslandNoise.mix(cluster.seed(), index, 701);
      IslandField.SizeBandProfile sizeProfile = this.clusterSampler.sizeBandProfile(settings, cluster.sizeBand());
      IslandField.HeightBandProfile bandProfile = this.clusterSampler.heightBandProfile(settings, cluster.heightBand());

      double angle = cluster.baseRotation() + this.noise.sampleRange(seed, 5, 0.15, 5.811946409141117) + index * switch (cluster.archetype()) {
         case CLASSIC, BOWL_CRATER -> 0.55;
         case CRESCENT -> 0.7;
         case TERRACE -> 0.48;
      };
      double orbit = this.noise.sampleRange(seed, 7, sizeProfile.minRadius() * 1.6, sizeProfile.maxRadius() * 2.8)
         + index * this.noise.sampleRange(seed, 11, 4.0, 10.0);

      orbit *= switch (cluster.archetype()) {
         case CLASSIC -> 1.0;
         case BOWL_CRATER -> 1.1;
         case CRESCENT -> 1.06;
         case TERRACE -> 0.92;
      };
      int centerX = cluster.centerX() + Mth.floor(Math.cos(angle) * orbit);
      int centerZ = cluster.centerZ() + Mth.floor(Math.sin(angle) * orbit);

      int centerY = this.clusterSampler.clampToBand(cluster.centerY() + this.noise.sampleInt(seed, 13, -24, 18) + switch (cluster.archetype()) {
         case CLASSIC -> 0;
         case BOWL_CRATER -> 4;
         case CRESCENT -> 2;
         case TERRACE -> 6;
      }, bandProfile, 8);

      double familyScale = this.noise.sampleRange(seed, 17, 0.46, 0.8) * switch (cluster.heightBand()) {
         case LOW -> 0.9;
         case MID_HIGH -> 1.0;
         case VERY_HIGH -> 1.06;
      };
      int radiusX = (int)Math.round(
         this.noise.sampleRange(seed, 19, sizeProfile.minRadius() * 0.58 * familyScale, sizeProfile.maxRadius() * 0.62 * familyScale)
      );
      int radiusZ = (int)Math.round(this.noise.sampleRange(seed, 23, sizeProfile.minRadius() * 0.52 * familyScale, sizeProfile.maxRadius() * 0.7 * familyScale));
      radiusX = Math.max(10, radiusX);
      radiusZ = Math.max(10, radiusZ);
      double verticalBias = cluster.heightBand() == IslandField.ClusterHeightBand.LOW
         ? 0.9
         : (cluster.heightBand() == IslandField.ClusterHeightBand.VERY_HIGH ? 1.05 : 1.0);
      int plateauHeight = (int)Math.round(this.noise.sampleRange(seed, 29, 6.0, 12.0) * verticalBias);
      int cliffDepth = (int)Math.round(this.noise.sampleRange(seed, 31, 14.0, 24.0) * verticalBias);
      int hangDepth = (int)Math.round(
         Math.max(
            18.0,
            Math.max(radiusX, radiusZ)
               * this.noise.sampleRange(seed, 37, 0.7, 1.1)
               * (
                  cluster.heightBand() == IslandField.ClusterHeightBand.LOW
                     ? 0.82
                     : (cluster.heightBand() == IslandField.ClusterHeightBand.VERY_HIGH ? 1.04 : 1.0)
               )
         )
      );
      int[] cappedHeights = capThickness(plateauHeight, cliffDepth, hangDepth, settings.terrain().maxIslandThicknessBlocks(), 4, 10, 14);
      plateauHeight = cappedHeights[0];
      cliffDepth = cappedHeights[1];
      hangDepth = cappedHeights[2];
      return new IslandField.IslandDescriptor(
         IslandField.IslandFamily.SATELLITE,
         centerX,
         centerY,
         centerZ,
         radiusX,
         radiusZ,
         Math.max(radiusX, radiusZ),
         this.noise.sampleRange(seed, 41, 0.0, Math.PI * 2),
         cluster.archetype(),
         plateauHeight,
         cliffDepth,
         hangDepth,
         this.noise.sampleInt(seed, 43, -radiusX / 3, radiusX / 3),
         this.noise.sampleInt(seed, 47, -radiusZ / 3, radiusZ / 3),
         (int)Math.round(radiusX * this.noise.sampleRange(seed, 53, 0.24, 0.42)),
         (int)Math.round(radiusZ * this.noise.sampleRange(seed, 59, 0.24, 0.42)),
         this.noise.sampleRange(seed, 61, 0.24, 0.46),
         4 + this.noise.sampleInt(seed, 67, 0, 1),
         2 + this.noise.sampleInt(seed, 71, 0, 1),
         2 + this.noise.sampleInt(seed, 73, 0, 2),
         seed
      );
   }

   public IslandField.IslandDescriptor createSpireDescriptor(IslandField.ClusterDescriptor cluster, SkyIslandSettings settings, int index) {
      long seed = IslandNoise.mix(cluster.seed(), index, 911);
      IslandField.SizeBandProfile sizeProfile = this.clusterSampler.sizeBandProfile(settings, cluster.sizeBand());
      IslandField.HeightBandProfile bandProfile = this.clusterSampler.heightBandProfile(settings, cluster.heightBand());

      double angle = cluster.baseRotation() + this.noise.sampleRange(seed, 3, 0.0, Math.PI * 2) + switch (cluster.archetype()) {
         case CLASSIC, BOWL_CRATER, TERRACE -> 0.0;
         case CRESCENT -> index == 0 ? -0.55 : 0.55;
      };

      double orbit = this.noise.sampleRange(seed, 5, sizeProfile.minRadius() * 1.4, sizeProfile.maxRadius() * 2.4) * switch (cluster.archetype()) {
         case CLASSIC -> 1.0;
         case BOWL_CRATER -> 1.14;
         case CRESCENT -> 1.12;
         case TERRACE -> 0.88;
      };
      int centerX = cluster.centerX() + Mth.floor(Math.cos(angle) * orbit);
      int centerZ = cluster.centerZ() + Mth.floor(Math.sin(angle) * orbit);
      int centerY = this.clusterSampler.clampToBand(cluster.centerY() + this.noise.sampleInt(seed, 7, -12, 34), bandProfile, 6);
      int radiusX = this.noise.sampleInt(seed, 11, 8, Math.max(12, sizeProfile.minRadius() / 2));
      int radiusZ = this.noise.sampleInt(seed, 13, 8, Math.max(12, sizeProfile.minRadius() / 2 + 4));
      int plateauHeight = this.noise.sampleInt(seed, 17, 3, 7);
      int cliffDepth = this.noise.sampleInt(seed, 19, 10, 18);
      radiusX = Math.max(
         8,
         (int)Math.round(
            radiusX
               * (
                  cluster.heightBand() == IslandField.ClusterHeightBand.LOW
                     ? 0.92
                     : (cluster.heightBand() == IslandField.ClusterHeightBand.VERY_HIGH ? 1.08 : 1.0)
               )
         )
      );
      radiusZ = Math.max(
         8,
         (int)Math.round(
            radiusZ
               * (
                  cluster.heightBand() == IslandField.ClusterHeightBand.LOW
                     ? 0.92
                     : (cluster.heightBand() == IslandField.ClusterHeightBand.VERY_HIGH ? 1.08 : 1.0)
               )
         )
      );
      int hangDepth = (int)Math.round(
         this.noise.sampleInt(seed, 23, Math.max(24, sizeProfile.minRadius()), Math.max(36, sizeProfile.maxRadius() + 18))
            * (
               cluster.heightBand() == IslandField.ClusterHeightBand.LOW
                  ? 0.86
                  : (cluster.heightBand() == IslandField.ClusterHeightBand.VERY_HIGH ? 1.06 : 1.0)
            )
      );
      int[] cappedHeights = capThickness(plateauHeight, cliffDepth, hangDepth, settings.terrain().maxIslandThicknessBlocks(), 3, 8, 12);
      plateauHeight = cappedHeights[0];
      cliffDepth = cappedHeights[1];
      hangDepth = cappedHeights[2];
      return new IslandField.IslandDescriptor(
         IslandField.IslandFamily.SPIRE,
         centerX,
         centerY,
         centerZ,
         radiusX,
         radiusZ,
         Math.max(radiusX, radiusZ),
         this.noise.sampleRange(seed, 29, 0.0, Math.PI * 2),
         cluster.archetype(),
         plateauHeight,
         cliffDepth,
         hangDepth,
         this.noise.sampleInt(seed, 31, -radiusX / 4, radiusX / 4),
         this.noise.sampleInt(seed, 37, -radiusZ / 4, radiusZ / 4),
         Math.max(6, radiusX / 2),
         Math.max(6, radiusZ / 2),
         this.noise.sampleRange(seed, 41, 0.18, 0.3),
         2,
         0,
         1,
         seed
      );
   }

   static int[] capThickness(int plateauHeight, int cliffDepth, int hangDepth, int cap, int minPlateau, int minCliff, int minHang) {
      int rawThickness = plateauHeight + cliffDepth + hangDepth;
      if (rawThickness <= cap) {
         return new int[]{plateauHeight, cliffDepth, hangDepth};
      }

      int minimumTotal = minPlateau + minCliff + minHang;
      int effectiveCap = Math.max(cap, minimumTotal);
      double scale = (double)effectiveCap / rawThickness;
      int plateau = Math.max(minPlateau, (int)Math.floor(plateauHeight * scale));
      int cliff = Math.max(minCliff, (int)Math.floor(cliffDepth * scale));
      int hang = Math.max(minHang, (int)Math.floor(hangDepth * scale));
      int[] values = new int[]{plateau, cliff, hang};
      int total = values[0] + values[1] + values[2];
      if (total < effectiveCap) {
         distributeRemainderUp(effectiveCap - total, plateauHeight, cliffDepth, hangDepth, values);
      } else if (total > effectiveCap) {
         distributeRemainderDown(total - effectiveCap, minPlateau, minCliff, minHang, values);
      }

      return values;
   }

   private static void distributeRemainderUp(int remainder, int rawPlateau, int rawCliff, int rawHang, int[] values) {
      while (remainder > 0) {
         double ratioPlateau = values[0] / Math.max(1.0, rawPlateau);
         double ratioCliff = values[1] / Math.max(1.0, rawCliff);
         double ratioHang = values[2] / Math.max(1.0, rawHang);
         if (ratioPlateau <= ratioCliff && ratioPlateau <= ratioHang) {
            values[0]++;
         } else if (ratioCliff <= ratioHang) {
            values[1]++;
         } else {
            values[2]++;
         }

         remainder--;
      }
   }

   private static void distributeRemainderDown(int overflow, int minPlateau, int minCliff, int minHang, int[] values) {
      while (true) {
         label34: {
            if (overflow > 0) {
               if (values[2] > minHang && values[2] >= values[1] && values[2] >= values[0]) {
                  values[2]--;
                  break label34;
               }

               if (values[1] > minCliff && values[1] >= values[0]) {
                  values[1]--;
                  break label34;
               }

               if (values[0] > minPlateau) {
                  values[0]--;
                  break label34;
               }

               if (values[1] > minCliff) {
                  values[1]--;
                  break label34;
               }

               if (values[2] > minHang) {
                  values[2]--;
                  break label34;
               }
            }

            return;
         }

         overflow--;
      }
   }
}
