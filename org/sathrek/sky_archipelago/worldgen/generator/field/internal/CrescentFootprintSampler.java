package org.sathrek.sky_archipelago.worldgen.generator.field.internal;

import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

final class CrescentFootprintSampler {
   CrescentFootprintSampler.CrescentFootprint sample(IslandField.IslandDescriptor descriptor, double localX, double localZ) {
      double outer = this.outerBody(descriptor, localX, localZ);
      double inner = this.innerCarve(descriptor, localX, localZ);
      double backArc = this.backArcSupport(descriptor, localX, localZ);
      double horns = this.hornSupport(descriptor, localX, localZ);
      double opening = this.openingMask(descriptor, localX, localZ);
      double body = outer * 1.08 + backArc * 0.58 + horns * 0.86 - inner * 1.08 - opening * 0.36;
      double hardOpen = Mth.clamp(inner * 1.04 + opening * 0.62 - outer * 0.3 - horns * 0.34, 0.0, 1.0);
      body -= hardOpen * 1.18;
      double shelf = this.shelfSupport(descriptor, localX, localZ, body, hardOpen);
      return new CrescentFootprintSampler.CrescentFootprint(Math.max(body, shelf), shelf, hardOpen);
   }

   private double outerBody(IslandField.IslandDescriptor descriptor, double localX, double localZ) {
      return IslandNoise.ellipseDensity(localX, localZ, descriptor.radiusX() * 1.16, descriptor.radiusZ() * 1.1);
   }

   private double innerCarve(IslandField.IslandDescriptor descriptor, double localX, double localZ) {
      return Math.max(0.0, IslandNoise.ellipseDensity(localX - descriptor.radiusX() * 0.38, localZ, descriptor.radiusX() * 0.78, descriptor.radiusZ() * 0.58));
   }

   private double backArcSupport(IslandField.IslandDescriptor descriptor, double localX, double localZ) {
      double backBias = Mth.clamp((-localX / Math.max(1.0, descriptor.radiusX()) + 0.34) / 1.04, 0.0, 1.0);
      double backMass = IslandNoise.ellipseDensity(localX + descriptor.radiusX() * 0.34, localZ, descriptor.radiusX() * 0.76, descriptor.radiusZ() * 0.98);
      return Math.max(0.0, backMass) * backBias;
   }

   private double hornSupport(IslandField.IslandDescriptor descriptor, double localX, double localZ) {
      double hornX = descriptor.radiusX() * 0.56;
      double hornZ = descriptor.radiusZ() * 0.86;
      double hornRadiusX = descriptor.radiusX() * 0.58;
      double hornRadiusZ = descriptor.radiusZ() * 0.3;
      double upperHorn = IslandNoise.ellipseDensity(localX - hornX, localZ - hornZ, hornRadiusX, hornRadiusZ);
      double lowerHorn = IslandNoise.ellipseDensity(localX - hornX, localZ + hornZ, hornRadiusX, hornRadiusZ);
      double shoulder = IslandNoise.ellipseDensity(
         localX - descriptor.radiusX() * 0.24, Math.abs(localZ) - descriptor.radiusZ() * 0.7, descriptor.radiusX() * 0.7, descriptor.radiusZ() * 0.26
      );
      return Math.max(0.0, Math.max(Math.max(upperHorn, lowerHorn), shoulder * 0.66));
   }

   private double openingMask(IslandField.IslandDescriptor descriptor, double localX, double localZ) {
      double openSide = Mth.clamp((localX / Math.max(1.0, descriptor.radiusX()) + 0.08) / 0.82, 0.0, 1.0);
      double centerBand = Mth.clamp(1.0 - Math.abs(localZ) / Math.max(1.0, descriptor.radiusZ() * 0.4), 0.0, 1.0);
      return openSide * centerBand;
   }

   private double shelfSupport(IslandField.IslandDescriptor descriptor, double localX, double localZ, double body, double hardOpen) {
      double shelf = IslandNoise.ellipseDensity(
         localX + descriptor.radiusX() * 0.26 - descriptor.hangOffsetX() * 0.2,
         localZ - descriptor.hangOffsetZ() * 0.2,
         descriptor.radiusX() * 0.92,
         descriptor.radiusZ() * 0.92
      );
      double hornShoulder = Mth.clamp((Math.abs(localZ) / Math.max(1.0, descriptor.radiusZ()) - 0.5) / 0.34, 0.0, 1.0);
      double backOnly = Mth.clamp((-localX / Math.max(1.0, descriptor.radiusX()) + 0.5) / 1.14, 0.0, 1.0);
      double shoulderSupport = Math.max(backOnly, hornShoulder * 0.72);
      return Math.max(0.0, Math.max(body, shelf * 0.54 * shoulderSupport - hardOpen * 0.78));
   }

   record CrescentFootprint(double coverage, double shelfCoverage, double openingStrength) {
   }
}
