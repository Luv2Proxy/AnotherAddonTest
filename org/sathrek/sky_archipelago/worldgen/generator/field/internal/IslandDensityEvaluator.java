package org.sathrek.sky_archipelago.worldgen.generator.field.internal;

import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.BiomeTerrainShaper;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

public final class IslandDensityEvaluator {
   private final IslandNoise noise;
   private final IslandShapeSampler shapeSampler;
   private final int topographySalt;
   private final int erosionSalt;
   private final int detailSalt;

   public IslandDensityEvaluator(IslandNoise noise, IslandShapeSampler shapeSampler, int topographySalt, int erosionSalt, int detailSalt) {
      this.noise = noise;
      this.shapeSampler = shapeSampler;
      this.topographySalt = topographySalt;
      this.erosionSalt = erosionSalt;
      this.detailSalt = detailSalt;
   }

   public double sampleDescriptorDensity(
      IslandField.IslandDescriptor descriptor,
      IslandField.HorizontalSample horizontal,
      int x,
      int y,
      int z,
      SkyIslandSettings settings,
      BiomeTerrainShaper.TerrainProfile terrainProfile
   ) {
      double coverage = Mth.clamp(horizontal.coverage(), -0.4, 1.2);
      double macroRelief = this.noise.fbm2D(descriptor.seed() + this.topographySalt * 23L, x * 0.006, z * 0.006, 4, 0.5);
      double ridgeRelief = this.noise.ridgedFbm2D(descriptor.seed() + this.topographySalt * 29L, x * 0.016, z * 0.016, 3, 0.55);
      double shelfNoise = this.noise.fbm2D(descriptor.seed() + this.topographySalt * 31L, x * 0.024, z * 0.024, 3, 0.56);
      double biomeMacro = this.noise.fbm2D(descriptor.seed() + this.topographySalt * 41L, x * 0.0075, z * 0.0075, 3, 0.5);
      double biomeRidge = this.noise.ridgedFbm2D(descriptor.seed() + this.topographySalt * 43L, x * 0.014, z * 0.014, 3, 0.55);
      double topRelief = macroRelief * 7.5 + ridgeRelief * 4.5 + shelfNoise * 2.2;
      topRelief += (biomeMacro * terrainProfile.macroReliefScale() * 7.0 + biomeRidge * terrainProfile.ridgeReliefScale() * 4.8)
         * settings.terrain().terrainReliefScale()
         * 0.92;
      if (descriptor.family() == IslandField.IslandFamily.ANCHOR_PLATEAU) {
         topRelief += switch (descriptor.archetype()) {
            case CLASSIC -> 0.0;
            case BOWL_CRATER -> this.shapeSampler.sampleBowlCraterTopAdjustment(descriptor, horizontal);
            case CRESCENT -> this.shapeSampler.sampleCrescentTopAdjustment(descriptor, horizontal);
            case TERRACE -> this.shapeSampler.sampleTerraceTopAdjustment(descriptor, horizontal);
         };
      }

      int top = Mth.floor(descriptor.centerY() + descriptor.plateauHeight() + topRelief);
      double mainBottomInset = descriptor.family() == IslandField.IslandFamily.ANCHOR_PLATEAU
         ? 0.34
         : (descriptor.family() == IslandField.IslandFamily.SATELLITE ? 0.28 : 0.22);

      int effectiveHangDepth = switch (descriptor.family()) {
         case ANCHOR_PLATEAU -> Math.min(descriptor.hangDepth(), 16);
         case SATELLITE -> Math.min(descriptor.hangDepth(), 20);
         case SPIRE -> Math.min(descriptor.hangDepth(), 24);
      };
      int shoulder = Mth.floor(descriptor.centerY() - descriptor.cliffDepth() * (0.55 + Mth.clamp(coverage, 0.0, 1.0) * 0.42) - Math.max(0.0, shelfNoise) * 6.0);
      if (descriptor.family() == IslandField.IslandFamily.ANCHOR_PLATEAU) {
         shoulder += switch (descriptor.archetype()) {
            case CLASSIC -> 0;
            case BOWL_CRATER -> 2;
            case CRESCENT -> -1;
            case TERRACE -> 4;
         };
      }

      int underside = shoulder - effectiveHangDepth;
      double heightSpan = Math.max(16.0, top - shoulder);
      double verticalProgress = Mth.clamp((top - y) / heightSpan, 0.0, 1.35);
      double bodyInset = mainBottomInset
         + Math.pow(verticalProgress, descriptor.family() == IslandField.IslandFamily.ANCHOR_PLATEAU ? 1.08 : 0.92)
            * (descriptor.family() == IslandField.IslandFamily.SPIRE ? 0.42 : 0.58);
      double topWindow = (top - y) / Math.max(4.0, descriptor.plateauHeight() + 10.0);
      double bottomWindow = (y - shoulder) / Math.max(8.0, descriptor.cliffDepth() + effectiveHangDepth * 0.18);
      double mainBody = Math.min(topWindow, bottomWindow) + coverage - bodyInset;
      double edgeVoid = Math.max(0.0, this.noise.fbm3D(descriptor.seed() + this.erosionSalt * 5L, x * 0.02, y * 0.036, z * 0.02, 3, 0.56) - 0.18)
         * (0.38 + horizontal.edgeDistance() * 0.8);
      double channelVoid = Math.max(0.0, this.noise.ridgedFbm3D(descriptor.seed() + this.erosionSalt * 7L, x * 0.03, y * 0.03, z * 0.03, 3, 0.55) - 0.26)
         * (0.3 + horizontal.edgeDistance() * 0.9)
         * terrainProfile.channelCarveScale();
      double cavernVoid = Math.max(0.0, this.noise.ridgedFbm3D(descriptor.seed() + this.detailSalt * 3L, x * 0.04, y * 0.04, z * 0.04, 2, 0.52) - 0.34)
         * 0.22
         * terrainProfile.basinCarveScale();
      double density = mainBody - edgeVoid - channelVoid - cavernVoid;
      if (y < underside - 4) {
         density -= 0.55 + (underside - 4 - y) * 0.055;
      }

      if (y > top + 3) {
         density -= 0.4 + (y - top) * 0.05;
      }

      return density;
   }
}
