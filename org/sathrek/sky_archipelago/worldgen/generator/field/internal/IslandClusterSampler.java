package org.sathrek.sky_archipelago.worldgen.generator.field.internal;

import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.config.IslandSizeMode;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;

public final class IslandClusterSampler {
   private static final int CLUSTER_TIER_SALT = 131;
   private static final int CLUSTER_JITTER_X_SALT = 163;
   private static final int CLUSTER_JITTER_Z_SALT = 197;
   private static final int CLUSTER_HEIGHT_SALT = 223;
   private static final int CLUSTER_ROTATION_SALT = 251;
   private static final int SATELLITE_COUNT_SALT = 281;
   private static final int SPIRE_COUNT_SALT = 307;
   private static final int ARCHETYPE_SALT = 463;
   private final IslandNoise noise;

   public IslandClusterSampler(IslandNoise noise) {
      this.noise = noise;
   }

   public IslandField.ClusterDescriptor sampleClusterDescriptor(
      int cellX, int cellZ, long layoutSeed, int spacing, SkyIslandSettings settings, int descriptorSeedSalt
   ) {
      int halfSpacing = spacing / 2;
      int jitterRange = Math.max(10, spacing / 3);
      int centerX = cellX * spacing + halfSpacing + this.noise.sampleInt(cellX, cellZ, 163, -jitterRange, jitterRange);
      int centerZ = cellZ * spacing + halfSpacing + this.noise.sampleInt(cellX, cellZ, 197, -jitterRange, jitterRange);
      IslandField.ClusterHeightBand heightBand = this.sampleHeightBand(cellX, cellZ, settings);
      IslandField.ClusterSizeBand sizeBand = this.sampleSizeBand(cellX, cellZ, settings);
      IslandField.HeightBandProfile heightBandProfile = this.heightBandProfile(settings, heightBand);
      int centerY = this.noise.sampleInt(cellX, cellZ, 223, heightBandProfile.minY(), heightBandProfile.maxY());
      double tierRoll = this.noise.sample01(cellX, cellZ, 131);
      IslandField.ClusterTier tier = tierRoll < 0.18
         ? IslandField.ClusterTier.GRAND
         : (tierRoll < 0.72 ? IslandField.ClusterTier.STANDARD : IslandField.ClusterTier.SCATTERED);

      int satelliteCount = switch (tier) {
         case GRAND -> this.noise.sampleInt(cellX, cellZ, 281, 5, 8);
         case STANDARD -> this.noise.sampleInt(cellX, cellZ, 281, 3, 6);
         case SCATTERED -> this.noise.sampleInt(cellX, cellZ, 281, 2, 4);
      };

      satelliteCount += switch (heightBand) {
         case LOW -> -1;
         case MID_HIGH -> 0;
         case VERY_HIGH -> tier == IslandField.ClusterTier.GRAND ? 1 : 0;
      };
      satelliteCount = Math.max(heightBand == IslandField.ClusterHeightBand.LOW ? 1 : 2, satelliteCount);

      int spireCount = switch (tier) {
         case GRAND -> this.noise.sampleInt(cellX, cellZ, 307, 1, 2);
         case STANDARD -> this.noise.sampleInt(cellX, cellZ, 307, 0, 1);
         case SCATTERED -> this.noise.sampleInt(cellX, cellZ, 307, 0, 1);
      };

      spireCount += switch (heightBand) {
         case LOW -> -1;
         case MID_HIGH -> 0;
         case VERY_HIGH -> 1;
      };
      spireCount = Math.max(0, Math.min(3, spireCount));
      return new IslandField.ClusterDescriptor(
         cellX,
         cellZ,
         centerX,
         centerY,
         centerZ,
         heightBand,
         sizeBand,
         tier,
         this.noise.sample01(cellX, cellZ, 251) * (Math.PI * 2),
         this.selectAnchorArchetype(cellX, cellZ, settings),
         satelliteCount,
         spireCount,
         IslandNoise.mix(layoutSeed + descriptorSeedSalt, cellX, cellZ)
      );
   }

   public IslandShapeArchetype selectAnchorArchetype(int cellX, int cellZ, SkyIslandSettings settings) {
      double totalWeight = 0.0;

      for (IslandShapeArchetype archetype : IslandShapeArchetype.values()) {
         totalWeight += settings.archetypeWeight(archetype);
      }

      if (totalWeight <= 0.0) {
         return IslandShapeArchetype.CLASSIC;
      }

      double roll = this.noise.sample01(cellX, cellZ, 463) * totalWeight;

      for (IslandShapeArchetype archetype : IslandShapeArchetype.values()) {
         double weight = settings.archetypeWeight(archetype);
         if (!(weight <= 0.0)) {
            if (roll < weight) {
               return archetype;
            }

            roll -= weight;
         }
      }

      return IslandShapeArchetype.CLASSIC;
   }

   public IslandField.ClusterHeightBand sampleHeightBand(int cellX, int cellZ, SkyIslandSettings settings) {
      double totalWeight = settings.terrain().lowBandWeight() + settings.terrain().midHighBandWeight() + settings.terrain().veryHighBandWeight();
      double roll = this.noise.sample01(cellX, cellZ, 240) * totalWeight;
      if (roll < settings.terrain().lowBandWeight()) {
         return IslandField.ClusterHeightBand.LOW;
      }

      roll -= settings.terrain().lowBandWeight();
      return roll < settings.terrain().midHighBandWeight() ? IslandField.ClusterHeightBand.MID_HIGH : IslandField.ClusterHeightBand.VERY_HIGH;
   }

   public IslandField.ClusterSizeBand sampleSizeBand(int cellX, int cellZ, SkyIslandSettings settings) {
      if (settings.terrain().islandSize().islandSizeMode() == IslandSizeMode.RANDOM) {
         return IslandField.ClusterSizeBand.RANDOM;
      }

      double totalWeight = settings.terrain().islandSize().smallIslandSizeBand().weight()
         + settings.terrain().islandSize().mediumIslandSizeBand().weight()
         + settings.terrain().islandSize().largeIslandSizeBand().weight();
      double roll = this.noise.sample01(cellX, cellZ, 276) * totalWeight;
      if (roll < settings.terrain().islandSize().smallIslandSizeBand().weight()) {
         return IslandField.ClusterSizeBand.SMALL;
      }

      roll -= settings.terrain().islandSize().smallIslandSizeBand().weight();
      return roll < settings.terrain().islandSize().mediumIslandSizeBand().weight() ? IslandField.ClusterSizeBand.MEDIUM : IslandField.ClusterSizeBand.LARGE;
   }

   public IslandField.SizeBandProfile sizeBandProfile(SkyIslandSettings settings, IslandField.ClusterSizeBand sizeBand) {
      return switch (sizeBand) {
         case RANDOM -> new IslandField.SizeBandProfile(settings.terrain().minIslandRadius(), settings.terrain().maxIslandRadius());
         case SMALL -> new IslandField.SizeBandProfile(
            settings.terrain().islandSize().smallIslandSizeBand().minRadius(), settings.terrain().islandSize().smallIslandSizeBand().maxRadius()
         );
         case MEDIUM -> new IslandField.SizeBandProfile(
            settings.terrain().islandSize().mediumIslandSizeBand().minRadius(), settings.terrain().islandSize().mediumIslandSizeBand().maxRadius()
         );
         case LARGE -> new IslandField.SizeBandProfile(
            settings.terrain().islandSize().largeIslandSizeBand().minRadius(), settings.terrain().islandSize().largeIslandSizeBand().maxRadius()
         );
      };
   }

   public IslandField.HeightBandProfile heightBandProfile(SkyIslandSettings settings, IslandField.ClusterHeightBand band) {
      int minY = settings.terrain().minIslandY();
      int maxY = settings.terrain().maxIslandY();
      int span = Math.max(24, maxY - minY);
      int lowWidth = Math.max(12, span / 5);
      int highWidth = Math.max(12, span / 6);
      int midMin = minY + Math.max(12, span / 5);
      int midMax = maxY - Math.max(12, span / 6);
      int lowCenter = Mth.clamp(
         minY + Math.max(10, span / 6) + settings.terrain().lowBandCenterOffset(), minY + lowWidth / 2, Math.max(minY + lowWidth / 2, midMin - 8)
      );
      int highCenter = Mth.clamp(
         maxY - Math.max(8, span / 9) + settings.terrain().veryHighBandCenterOffset(), Math.min(midMax + 8, maxY - highWidth / 2), maxY - highWidth / 2
      );

      return switch (band) {
         case LOW -> {
            int bandMin = minY;
            int bandMax = Math.min(maxY, Math.max(minY + 8, lowCenter + lowWidth / 2));
            yield new IslandField.HeightBandProfile(bandMin, bandMax);
         }
         case MID_HIGH -> {
            int bandMin = Math.max(minY, Math.min(midMin, lowCenter + lowWidth / 2 + 4));
            int bandMax = Math.min(maxY, Math.max(bandMin + 12, Math.max(midMax, highCenter - highWidth / 2 - 4)));
            yield new IslandField.HeightBandProfile(bandMin, bandMax);
         }
         case VERY_HIGH -> {
            int bandMin = Math.max(minY, Math.min(maxY - 8, highCenter - highWidth / 2));
            int bandMax = maxY;
            yield new IslandField.HeightBandProfile(bandMin, bandMax);
         }
      };
   }

   public int clampToBand(int value, IslandField.HeightBandProfile bandProfile, int inset) {
      int min = Math.min(bandProfile.maxY(), bandProfile.minY() + inset);
      int max = Math.max(min, bandProfile.maxY() - inset);
      return Mth.clamp(value, min, max);
   }
}
