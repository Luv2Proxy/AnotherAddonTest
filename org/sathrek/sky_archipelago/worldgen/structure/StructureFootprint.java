package org.sathrek.sky_archipelago.worldgen.structure;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.worldgen.WorldgenPerformanceMetrics;

public record StructureFootprint(int minX, int maxX, int minZ, int maxZ) {
   public int centerX() {
      return Mth.floor((this.minX + this.maxX) * 0.5);
   }

   public int centerZ() {
      return Mth.floor((this.minZ + this.maxZ) * 0.5);
   }

   public int spanX() {
      return this.maxX - this.minX;
   }

   public int spanZ() {
      return this.maxZ - this.minZ;
   }

   public int area() {
      return Math.max(1, (this.maxX - this.minX + 1) * (this.maxZ - this.minZ + 1));
   }

   public boolean contains(int x, int z) {
      return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
   }

   public StructureFootprint translate(int offsetX, int offsetZ) {
      return new StructureFootprint(this.minX + offsetX, this.maxX + offsetX, this.minZ + offsetZ, this.maxZ + offsetZ);
   }

   public StructureFootprint insetByRatio(double insetRatio) {
      double clampedRatio = Mth.clamp((float)insetRatio, 0.0F, 0.45F);
      if (clampedRatio <= 0.0) {
         return this;
      }

      int widthX = this.maxX - this.minX;
      int widthZ = this.maxZ - this.minZ;
      int insetX = Math.min(Math.max(0, Mth.floor(widthX * clampedRatio)), Math.max(0, widthX / 2));
      int insetZ = Math.min(Math.max(0, Mth.floor(widthZ * clampedRatio)), Math.max(0, widthZ / 2));
      int nextMinX = this.minX + insetX;
      int nextMaxX = this.maxX - insetX;
      if (nextMinX > nextMaxX) {
         int centerX = Mth.floor((this.minX + this.maxX) * 0.5);
         nextMinX = centerX;
         nextMaxX = centerX;
      }

      int nextMinZ = this.minZ + insetZ;
      int nextMaxZ = this.maxZ - insetZ;
      if (nextMinZ > nextMaxZ) {
         int centerZ = Mth.floor((this.minZ + this.maxZ) * 0.5);
         nextMinZ = centerZ;
         nextMaxZ = centerZ;
      }

      return new StructureFootprint(nextMinX, nextMaxX, nextMinZ, nextMaxZ);
   }

   public List<StructureFootprint.GridPoint> sampleGrid(int gridSize) {
      int clampedGrid = Math.max(1, gridSize);
      List<StructureFootprint.GridPoint> samples = new ArrayList<>(clampedGrid * clampedGrid);
      this.forEachGridPoint(clampedGrid, (sampleX, sampleZ) -> samples.add(new StructureFootprint.GridPoint(sampleX, sampleZ)));
      return samples;
   }

   public void forEachGridPoint(int gridSize, StructureFootprint.GridPointConsumer consumer) {
      int clampedGrid = Math.max(1, gridSize);
      WorldgenPerformanceMetrics.recordGridSample(clampedGrid, clampedGrid * clampedGrid);

      for (int gridX = 0; gridX < clampedGrid; gridX++) {
         float xT = clampedGrid == 1 ? 0.5F : (float)gridX / (clampedGrid - 1);
         int sampleX = Mth.floor(Mth.lerp(xT, this.minX, this.maxX));

         for (int gridZ = 0; gridZ < clampedGrid; gridZ++) {
            float zT = clampedGrid == 1 ? 0.5F : (float)gridZ / (clampedGrid - 1);
            int sampleZ = Mth.floor(Mth.lerp(zT, this.minZ, this.maxZ));
            consumer.accept(sampleX, sampleZ);
         }
      }
   }

   public record GridPoint(int x, int z) {
   }

   @FunctionalInterface
   public interface GridPointConsumer {
      void accept(int var1, int var2);
   }
}
