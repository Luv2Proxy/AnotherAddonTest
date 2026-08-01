package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public final class VillageIslandBoundsEvaluator {
   public boolean fits(HostIsland host, StructureFootprint footprint) {
      return this.fits(host, footprint.minX(), footprint.maxX(), footprint.minZ(), footprint.maxZ());
   }

   public boolean fits(HostIsland host, BoundingBox bounds) {
      return this.fits(host, bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
   }

   public boolean centerFits(HostIsland host, BoundingBox bounds) {
      return this.centerDistanceSq(host, bounds) <= this.usableRadiusSq(host);
   }

   public boolean pieceFits(HostIsland host, BoundingBox bounds, VillagePieceClassifier.PieceKind kind) {
      return switch (kind) {
         case CENTER, BUILDING -> this.fits(host, bounds);
         case ROAD, FARM, OTHER -> this.centerFits(host, bounds);
      };
   }

   public long centerDistanceSq(HostIsland host, BoundingBox bounds) {
      long centerX = ((long)bounds.minX() + bounds.maxX()) / 2L;
      long centerZ = ((long)bounds.minZ() + bounds.maxZ()) / 2L;
      long dx = centerX - host.preview().x();
      long dz = centerZ - host.preview().z();
      return dx * dx + dz * dz;
   }

   public long usableRadiusSq(HostIsland host) {
      long usableRadius = host.usableRadius();
      return usableRadius * usableRadius;
   }

   private boolean fits(HostIsland host, int minX, int maxX, int minZ, int maxZ) {
      int centerX = host.preview().x();
      int centerZ = host.preview().z();
      long usableSq = this.usableRadiusSq(host);
      return isInsideRadius(minX, minZ, centerX, centerZ, usableSq)
         && isInsideRadius(minX, maxZ, centerX, centerZ, usableSq)
         && isInsideRadius(maxX, minZ, centerX, centerZ, usableSq)
         && isInsideRadius(maxX, maxZ, centerX, centerZ, usableSq);
   }

   private static boolean isInsideRadius(int x, int z, int centerX, int centerZ, long usableSq) {
      long dx = (long)x - centerX;
      long dz = (long)z - centerZ;
      return dx * dx + dz * dz <= usableSq;
   }
}
