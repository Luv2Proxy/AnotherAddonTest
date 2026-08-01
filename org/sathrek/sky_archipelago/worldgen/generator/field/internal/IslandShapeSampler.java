package org.sathrek.sky_archipelago.worldgen.generator.field.internal;

import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;

public final class IslandShapeSampler {
   private final IslandNoise noise;
   private final int topographySalt;
   private final int erosionSalt;
   private final CrescentFootprintSampler crescentFootprintSampler = new CrescentFootprintSampler();

   public IslandShapeSampler(IslandNoise noise, int topographySalt, int erosionSalt) {
      this.noise = noise;
      this.topographySalt = topographySalt;
      this.erosionSalt = erosionSalt;
   }

   public IslandField.HorizontalSample sampleHorizontal(IslandField.IslandDescriptor descriptor, int x, int z, SkyIslandSettings settings) {
      double sin = Math.sin(descriptor.rotation());
      double cos = Math.cos(descriptor.rotation());
      double dx = x - descriptor.centerX();
      double dz = z - descriptor.centerZ();
      double warpX = this.noise.fbm2D(descriptor.seed() + this.topographySalt * 13L, x * 0.026, z * 0.026, 3, 0.52) * descriptor.radiusX() * 0.14;
      double warpZ = this.noise.fbm2D(descriptor.seed() + this.topographySalt * 17L, x * 0.026, z * 0.026, 3, 0.52) * descriptor.radiusZ() * 0.14;
      double localX = (dx + warpX) * cos + (dz + warpZ) * sin;
      double localZ = (dz + warpZ) * cos - (dx + warpX) * sin;
      double directionalBias = Math.cos(descriptor.rotation() + this.noise.sampleRange(descriptor.seed(), 79, -0.85, 0.85)) * localX
         + Math.sin(descriptor.rotation() + this.noise.sampleRange(descriptor.seed(), 83, -0.85, 0.85)) * localZ;
      double asymmetry = directionalBias / Math.max(10.0, descriptor.maxRadius());
      double asymmetryRadiusX = descriptor.radiusX() * (1.0 + Mth.clamp(asymmetry * 0.24, -0.18, 0.34));
      double asymmetryRadiusZ = descriptor.radiusZ() * (1.0 - Mth.clamp(asymmetry * 0.18, -0.2, 0.18));
      double primary = IslandNoise.ellipseDensity(localX, localZ, asymmetryRadiusX, asymmetryRadiusZ);
      double combined = primary;
      boolean dedicatedCrescentAnchor = descriptor.family() == IslandField.IslandFamily.ANCHOR_PLATEAU
         && descriptor.archetype() == IslandShapeArchetype.CRESCENT;
      CrescentFootprintSampler.CrescentFootprint crescentFootprint = null;
      if (dedicatedCrescentAnchor) {
         crescentFootprint = this.crescentFootprintSampler.sample(descriptor, localX, localZ);
         combined = crescentFootprint.coverage();
      } else if (descriptor.family() == IslandField.IslandFamily.ANCHOR_PLATEAU) {
         combined = switch (descriptor.archetype()) {
            case CLASSIC -> combined;
            case BOWL_CRATER -> this.applyBowlCraterHorizontal(descriptor, localX, localZ, primary);
            case CRESCENT -> combined;
            case TERRACE -> this.applyTerraceHorizontal(descriptor, localX, localZ, primary);
         };
      }

      boolean allowAnchorFragmentation = !dedicatedCrescentAnchor
         && (descriptor.family() != IslandField.IslandFamily.ANCHOR_PLATEAU || settings.advanced().anchorFragmentationEnabled());

      for (int lobeIndex = 0; lobeIndex < descriptor.lobeCount() && allowAnchorFragmentation; lobeIndex++) {
         long lobeSeed = IslandNoise.mix(descriptor.seed(), lobeIndex, 1409);
         double lobeAngle = descriptor.rotation() + this.noise.sampleRange(lobeSeed, 5, 0.0, Math.PI * 2);
         double lobeDistance = this.noise.sampleRange(lobeSeed, 7, 0.18, 0.86) * Math.min(descriptor.radiusX(), descriptor.radiusZ());
         double lobeOffsetX = Math.cos(lobeAngle) * lobeDistance;
         double lobeOffsetZ = Math.sin(lobeAngle) * lobeDistance;
         double lobeRadiusX = descriptor.radiusX()
            * this.noise.sampleRange(lobeSeed, 11, 0.3, descriptor.family() == IslandField.IslandFamily.ANCHOR_PLATEAU ? 0.74 : 0.54);
         double lobeRadiusZ = descriptor.radiusZ()
            * this.noise.sampleRange(lobeSeed, 13, 0.3, descriptor.family() == IslandField.IslandFamily.ANCHOR_PLATEAU ? 0.74 : 0.54);
         double lobeWeight = this.noise.sampleRange(lobeSeed, 17, 0.28, descriptor.family() == IslandField.IslandFamily.ANCHOR_PLATEAU ? 0.74 : 0.5);
         double lobeDensity = IslandNoise.ellipseDensity(localX - lobeOffsetX, localZ - lobeOffsetZ, lobeRadiusX, lobeRadiusZ);
         combined = Math.max(combined, primary * 0.58 + lobeDensity * lobeWeight);
      }

      for (int peninsulaIndex = 0; peninsulaIndex < descriptor.peninsulaCount() && allowAnchorFragmentation; peninsulaIndex++) {
         long peninsulaSeed = IslandNoise.mix(descriptor.seed(), peninsulaIndex, 1709);
         double peninsulaAngle = descriptor.rotation() + this.noise.sampleRange(peninsulaSeed, 5, 0.0, Math.PI * 2);
         double peninsulaDistance = this.noise.sampleRange(peninsulaSeed, 7, 0.74, 1.18) * Math.max(descriptor.radiusX(), descriptor.radiusZ());
         double peninsulaOffsetX = Math.cos(peninsulaAngle) * peninsulaDistance;
         double peninsulaOffsetZ = Math.sin(peninsulaAngle) * peninsulaDistance;
         double peninsulaRadiusX = descriptor.radiusX() * this.noise.sampleRange(peninsulaSeed, 11, 0.14, 0.28);
         double peninsulaRadiusZ = descriptor.radiusZ() * this.noise.sampleRange(peninsulaSeed, 13, 0.14, 0.28);
         double peninsulaDensity = IslandNoise.ellipseDensity(localX - peninsulaOffsetX, localZ - peninsulaOffsetZ, peninsulaRadiusX, peninsulaRadiusZ);
         combined = Math.max(combined, primary * 0.34 + peninsulaDensity * this.noise.sampleRange(peninsulaSeed, 17, 0.44, 0.72));
      }

      if (dedicatedCrescentAnchor) {
         combined = Math.max(combined, crescentFootprint.shelfCoverage());
      } else {
         double shelfDensity = IslandNoise.ellipseDensity(
            localX - descriptor.hangOffsetX() * 0.55,
            localZ - descriptor.hangOffsetZ() * 0.55,
            descriptor.tailRadiusX() * 1.35,
            descriptor.tailRadiusZ() * 1.35
         );
         combined = Math.max(combined, primary * (allowAnchorFragmentation ? 0.72 : 0.86) + shelfDensity * (allowAnchorFragmentation ? 0.26 : 0.1));
      }

      for (int biteIndex = 0; biteIndex < descriptor.biteCount() && allowAnchorFragmentation; biteIndex++) {
         long biteSeed = IslandNoise.mix(descriptor.seed(), biteIndex, 1901);
         double biteAngle = descriptor.rotation() + this.noise.sampleRange(biteSeed, 5, 0.0, Math.PI * 2);
         double biteDistance = this.noise.sampleRange(biteSeed, 7, 0.56, 1.06) * Math.min(descriptor.radiusX(), descriptor.radiusZ());
         double biteOffsetX = Math.cos(biteAngle) * biteDistance;
         double biteOffsetZ = Math.sin(biteAngle) * biteDistance;
         double biteRadiusX = descriptor.radiusX() * this.noise.sampleRange(biteSeed, 11, 0.16, 0.28);
         double biteRadiusZ = descriptor.radiusZ() * this.noise.sampleRange(biteSeed, 13, 0.16, 0.28);
         double biteDensity = IslandNoise.ellipseDensity(localX - biteOffsetX, localZ - biteOffsetZ, biteRadiusX, biteRadiusZ);
         if (biteDensity > 0.0) {
            combined -= biteDensity * this.noise.sampleRange(biteSeed, 17, 0.24, 0.54);
         }
      }

      double macroNoise = this.noise.fbm2D(descriptor.seed() + this.topographySalt, x * 0.01, z * 0.01, 4, 0.52);
      double ridgeNoise = this.noise.ridgedFbm2D(descriptor.seed() + this.topographySalt * 2L, x * 0.03, z * 0.03, 3, 0.56);
      double coastNoise = this.noise.ridgedFbm2D(descriptor.seed() + this.erosionSalt, x * 0.046, z * 0.046, 3, 0.55);
      double splitNoise = this.noise.fbm2D(descriptor.seed() + this.erosionSalt * 11L, x * 0.032, z * 0.032, 3, 0.53);
      double branchCoverage = -1.0;
      double edgeDistance = 1.0 - Mth.clamp(combined, 0.0, 1.0);
      double erosion = Math.max(0.0, coastNoise - descriptor.erosionStrength()) * (0.65 + edgeDistance * 0.9)
         + Math.max(0.0, splitNoise - 0.36) * edgeDistance * 0.32;
      double coverage = combined + macroNoise * 0.16 + ridgeNoise * 0.1 - erosion;
      if (dedicatedCrescentAnchor) {
         coverage -= crescentFootprint.openingStrength() * 0.92;
      }

      return new IslandField.HorizontalSample(localX, localZ, coverage, branchCoverage, edgeDistance, coverage > -0.28 || branchCoverage > -0.22);
   }

   private double applyBowlCraterHorizontal(IslandField.IslandDescriptor descriptor, double localX, double localZ, double primary) {
      double rimOuter = IslandNoise.ellipseDensity(localX, localZ, descriptor.radiusX() * 1.02, descriptor.radiusZ() * 1.02);
      double crater = Math.max(0.0, IslandNoise.ellipseDensity(localX, localZ, descriptor.radiusX() * 0.46, descriptor.radiusZ() * 0.46));
      return Math.max(primary, rimOuter * 0.92) - crater * 0.66;
   }

   private double applyTerraceHorizontal(IslandField.IslandDescriptor descriptor, double localX, double localZ, double primary) {
      double upperShelf = IslandNoise.ellipseDensity(localX, localZ, descriptor.radiusX() * 0.82, descriptor.radiusZ() * 0.82);
      double middleShelf = IslandNoise.ellipseDensity(localX, localZ, descriptor.radiusX() * 1.02, descriptor.radiusZ() * 1.02);
      return Math.max(primary * 0.95, Math.max(upperShelf * 0.86, middleShelf));
   }

   public double sampleBowlCraterTopAdjustment(IslandField.IslandDescriptor descriptor, IslandField.HorizontalSample horizontal) {
      double crater = Math.max(
         0.0, IslandNoise.ellipseDensity(horizontal.localX(), horizontal.localZ(), descriptor.radiusX() * 0.42, descriptor.radiusZ() * 0.42)
      );
      return -crater * 10.0;
   }

   public double sampleCrescentTopAdjustment(IslandField.IslandDescriptor descriptor, IslandField.HorizontalSample horizontal) {
      double hornBias = horizontal.localX() / Math.max(10.0, descriptor.radiusX());
      if (hornBias < -0.35) {
         return 2.0;
      } else {
         return hornBias > 0.35 ? 0.8 : 0.0;
      }
   }

   public double sampleTerraceTopAdjustment(IslandField.IslandDescriptor descriptor, IslandField.HorizontalSample horizontal) {
      double radial = 1.0
         - Mth.clamp(
            Math.max(Math.abs(horizontal.localX()) / Math.max(1.0, descriptor.radiusX()), Math.abs(horizontal.localZ()) / Math.max(1.0, descriptor.radiusZ())),
            0.0,
            1.0
         );
      double shelves = Math.floor(radial * 3.0) / 3.0;
      return shelves * 16.0 - radial * 9.0;
   }
}
