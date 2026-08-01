package org.sathrek.sky_archipelago.config.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;

public record ArchetypeSettings(
   boolean classicArchetypeEnabled,
   double classicArchetypeWeight,
   boolean bowlCraterArchetypeEnabled,
   double bowlCraterArchetypeWeight,
   boolean crescentArchetypeEnabled,
   double crescentArchetypeWeight,
   boolean terraceArchetypeEnabled,
   double terraceArchetypeWeight
) {
   public static final Codec<ArchetypeSettings> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.BOOL.optionalFieldOf("classic_enabled", true).forGetter(ArchetypeSettings::classicArchetypeEnabled),
            Codec.doubleRange(0.0, 10.0).optionalFieldOf("classic_weight", 1.0).forGetter(ArchetypeSettings::classicArchetypeWeight),
            Codec.BOOL.optionalFieldOf("bowl_crater_enabled", true).forGetter(ArchetypeSettings::bowlCraterArchetypeEnabled),
            Codec.doubleRange(0.0, 10.0).optionalFieldOf("bowl_crater_weight", 1.0).forGetter(ArchetypeSettings::bowlCraterArchetypeWeight),
            Codec.BOOL.optionalFieldOf("crescent_enabled", true).forGetter(ArchetypeSettings::crescentArchetypeEnabled),
            Codec.doubleRange(0.0, 10.0).optionalFieldOf("crescent_weight", 1.0).forGetter(ArchetypeSettings::crescentArchetypeWeight),
            Codec.BOOL.optionalFieldOf("terrace_enabled", true).forGetter(ArchetypeSettings::terraceArchetypeEnabled),
            Codec.doubleRange(0.0, 10.0).optionalFieldOf("terrace_weight", 1.0).forGetter(ArchetypeSettings::terraceArchetypeWeight)
         )
         .apply(instance, ArchetypeSettings::new)
   );

   public ArchetypeSettings(
      boolean classicArchetypeEnabled,
      double classicArchetypeWeight,
      boolean bowlCraterArchetypeEnabled,
      double bowlCraterArchetypeWeight,
      boolean crescentArchetypeEnabled,
      double crescentArchetypeWeight,
      boolean terraceArchetypeEnabled,
      double terraceArchetypeWeight
   ) {
      if (!(classicArchetypeWeight < 0.0) && !(bowlCraterArchetypeWeight < 0.0) && !(crescentArchetypeWeight < 0.0) && !(terraceArchetypeWeight < 0.0)) {
         this.classicArchetypeEnabled = classicArchetypeEnabled;
         this.classicArchetypeWeight = classicArchetypeWeight;
         this.bowlCraterArchetypeEnabled = bowlCraterArchetypeEnabled;
         this.bowlCraterArchetypeWeight = bowlCraterArchetypeWeight;
         this.crescentArchetypeEnabled = crescentArchetypeEnabled;
         this.crescentArchetypeWeight = crescentArchetypeWeight;
         this.terraceArchetypeEnabled = terraceArchetypeEnabled;
         this.terraceArchetypeWeight = terraceArchetypeWeight;
      } else {
         throw new IllegalArgumentException("archetype weights must be >= 0.0");
      }
   }

   public static ArchetypeSettings defaults() {
      return new ArchetypeSettings(true, 1.0, true, 1.0, true, 1.0, true, 1.0);
   }

   public boolean isEnabled(IslandShapeArchetype archetype) {
      return switch (archetype) {
         case CLASSIC -> this.classicArchetypeEnabled;
         case BOWL_CRATER -> this.bowlCraterArchetypeEnabled;
         case CRESCENT -> this.crescentArchetypeEnabled;
         case TERRACE -> this.terraceArchetypeEnabled;
      };
   }

   public double weight(IslandShapeArchetype archetype) {
      double configuredWeight = switch (archetype) {
         case CLASSIC -> this.classicArchetypeWeight;
         case BOWL_CRATER -> this.bowlCraterArchetypeWeight;
         case CRESCENT -> this.crescentArchetypeWeight;
         case TERRACE -> this.terraceArchetypeWeight;
      };
      return this.isEnabled(archetype) ? configuredWeight : 0.0;
   }
}
