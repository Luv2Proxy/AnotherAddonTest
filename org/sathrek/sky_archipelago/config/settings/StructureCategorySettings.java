package org.sathrek.sky_archipelago.config.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record StructureCategorySettings(
   double supportThreshold,
   double footprintInsetRatio,
   int searchRadiusChunks,
   int minStableTopCells,
   int topOffset,
   int localSearchStepBlocks,
   int localSearchRadiusBlocks,
   double groundedSampleThreshold,
   int maxGroundGapBlocks,
   int minHostIslandRadius,
   int minHostStableTopCells
) {
   public static final Codec<StructureCategorySettings> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.doubleRange(0.0, 1.0).fieldOf("support_threshold").forGetter(StructureCategorySettings::supportThreshold),
            Codec.doubleRange(0.0, 0.49).fieldOf("footprint_inset_ratio").forGetter(StructureCategorySettings::footprintInsetRatio),
            Codec.intRange(0, 12).fieldOf("search_radius_chunks").forGetter(StructureCategorySettings::searchRadiusChunks),
            Codec.intRange(1, 64).fieldOf("min_stable_top_cells").forGetter(StructureCategorySettings::minStableTopCells),
            Codec.intRange(-16, 32).fieldOf("top_offset").forGetter(StructureCategorySettings::topOffset),
            Codec.intRange(1, 32).fieldOf("local_search_step_blocks").forGetter(StructureCategorySettings::localSearchStepBlocks),
            Codec.intRange(0, 96).fieldOf("local_search_radius_blocks").forGetter(StructureCategorySettings::localSearchRadiusBlocks),
            Codec.doubleRange(0.0, 1.0).fieldOf("grounded_sample_threshold").forGetter(StructureCategorySettings::groundedSampleThreshold),
            Codec.intRange(0, 8).fieldOf("max_ground_gap_blocks").forGetter(StructureCategorySettings::maxGroundGapBlocks),
            Codec.intRange(0, 128).fieldOf("min_host_island_radius").forGetter(StructureCategorySettings::minHostIslandRadius),
            Codec.intRange(1, 64).fieldOf("min_host_stable_top_cells").forGetter(StructureCategorySettings::minHostStableTopCells)
         )
         .apply(instance, StructureCategorySettings::new)
   );
}
