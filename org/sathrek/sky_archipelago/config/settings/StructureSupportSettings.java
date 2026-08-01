package org.sathrek.sky_archipelago.config.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record StructureSupportSettings(int supportCheckDepth, int supportSampleGridSize, double supportThreshold) {
   public static final Codec<StructureSupportSettings> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.intRange(1, 256).fieldOf("support_check_depth").forGetter(StructureSupportSettings::supportCheckDepth),
            Codec.intRange(1, 15).fieldOf("support_sample_grid_size").forGetter(StructureSupportSettings::supportSampleGridSize),
            Codec.doubleRange(0.0, 1.0).fieldOf("support_threshold").forGetter(StructureSupportSettings::supportThreshold)
         )
         .apply(instance, StructureSupportSettings::new)
   );
}
