package org.sathrek.sky_archipelago.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record IslandSizeBand(int minRadius, int maxRadius, double weight) {
   public static final Codec<IslandSizeBand> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.intRange(8, 500).fieldOf("min_radius").forGetter(IslandSizeBand::minRadius),
            Codec.intRange(8, 500).fieldOf("max_radius").forGetter(IslandSizeBand::maxRadius),
            Codec.doubleRange(0.0, 1.0).fieldOf("weight").forGetter(IslandSizeBand::weight)
         )
         .apply(instance, IslandSizeBand::new)
   );

   public IslandSizeBand {
      if (maxRadius < minRadius) {
         throw new IllegalArgumentException("maxRadius must be >= minRadius");
      }

      if (weight < 0.0) {
         throw new IllegalArgumentException("weight must be >= 0");
      }
   }
}
