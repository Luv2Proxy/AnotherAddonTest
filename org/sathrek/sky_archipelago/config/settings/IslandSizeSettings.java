package org.sathrek.sky_archipelago.config.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import org.sathrek.sky_archipelago.config.IslandSizeBand;
import org.sathrek.sky_archipelago.config.IslandSizeMode;

public record IslandSizeSettings(
   IslandSizeMode islandSizeMode, IslandSizeBand smallIslandSizeBand, IslandSizeBand mediumIslandSizeBand, IslandSizeBand largeIslandSizeBand
) {
   public static final Codec<IslandSizeSettings> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            IslandSizeMode.CODEC.optionalFieldOf("mode", IslandSizeMode.RANDOM).forGetter(IslandSizeSettings::islandSizeMode),
            IslandSizeBand.CODEC
               .optionalFieldOf("small")
               .xmap(optional -> (IslandSizeBand)optional.orElse(null), Optional::ofNullable)
               .forGetter(IslandSizeSettings::smallIslandSizeBand),
            IslandSizeBand.CODEC
               .optionalFieldOf("medium")
               .xmap(optional -> (IslandSizeBand)optional.orElse(null), Optional::ofNullable)
               .forGetter(IslandSizeSettings::mediumIslandSizeBand),
            IslandSizeBand.CODEC
               .optionalFieldOf("large")
               .xmap(optional -> (IslandSizeBand)optional.orElse(null), Optional::ofNullable)
               .forGetter(IslandSizeSettings::largeIslandSizeBand)
         )
         .apply(instance, IslandSizeSettings::new)
   );

   public IslandSizeSettings {
      if (islandSizeMode == null) {
         islandSizeMode = IslandSizeMode.RANDOM;
      }
   }

   public IslandSizeSettings resolve(int minRadius, int maxRadius) {
      IslandSizeBand resolvedSmall = this.smallIslandSizeBand != null ? this.smallIslandSizeBand : deriveSmallBand(minRadius, maxRadius);
      IslandSizeBand resolvedMedium = this.mediumIslandSizeBand != null ? this.mediumIslandSizeBand : deriveMediumBand(minRadius, maxRadius);
      IslandSizeBand resolvedLarge = this.largeIslandSizeBand != null ? this.largeIslandSizeBand : deriveLargeBand(minRadius, maxRadius);
      return new IslandSizeSettings(this.islandSizeMode, resolvedSmall, resolvedMedium, resolvedLarge);
   }

   public static IslandSizeSettings defaults() {
      return new IslandSizeSettings(IslandSizeMode.RANDOM, null, null, null);
   }

   public static IslandSizeBand deriveSmallBand(int minRadius, int maxRadius) {
      int span = Math.max(0, maxRadius - minRadius);
      int firstMax = minRadius + Math.max(1, span / 3);
      return new IslandSizeBand(minRadius, Math.min(maxRadius, firstMax), 0.34);
   }

   public static IslandSizeBand deriveMediumBand(int minRadius, int maxRadius) {
      int span = Math.max(0, maxRadius - minRadius);
      int firstMax = minRadius + Math.max(1, span / 3);
      int secondMax = minRadius + Math.max(2, span * 2 / 3);
      int mediumMin = Math.min(maxRadius, firstMax + 1);
      int mediumMax = Math.max(mediumMin, Math.min(maxRadius, secondMax));
      return new IslandSizeBand(mediumMin, mediumMax, 0.33);
   }

   public static IslandSizeBand deriveLargeBand(int minRadius, int maxRadius) {
      int span = Math.max(0, maxRadius - minRadius);
      int secondMax = minRadius + Math.max(2, span * 2 / 3);
      int largeMin = Math.min(maxRadius, secondMax + 1);
      int largeMax = Math.max(largeMin, maxRadius);
      return new IslandSizeBand(largeMin, largeMax, 0.33);
   }
}
