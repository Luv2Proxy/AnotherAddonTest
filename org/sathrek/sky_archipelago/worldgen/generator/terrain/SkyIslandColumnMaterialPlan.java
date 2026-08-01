package org.sathrek.sky_archipelago.worldgen.generator.terrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;

public record SkyIslandColumnMaterialPlan(
   List<TerrainColumn> segments,
   int minY,
   int maxY,
   int deepslateStartY,
   int worldX,
   int worldZ,
   long layoutSeed,
   boolean oceanEnabled,
   int oceanTopY,
   int oceanFloorTopY,
   int[] solidBottoms,
   int[] solidTops,
   int highestSolidY
) {
   private static final int DEEPSLATE_BLEND_BAND = 8;

   public static SkyIslandColumnMaterialPlan create(List<TerrainColumn> segments, int minY, int maxY, SkyIslandSettings settings) {
      return create(segments, minY, maxY, settings, 0, 0, 0L);
   }

   public static SkyIslandColumnMaterialPlan create(
      List<TerrainColumn> segments, int minY, int maxY, SkyIslandSettings settings, int worldX, int worldZ, long layoutSeed
   ) {
      int resolvedOceanTopY = settings.terrain().ocean().oceanEnabled() ? Math.min(maxY - 1, settings.terrain().ocean().oceanLevelY()) : minY - 1;
      int resolvedOceanFloorTopY;
      if (!settings.terrain().ocean().oceanEnabled()) {
         resolvedOceanFloorTopY = minY;
      } else if (!settings.terrain().ocean().oceanFloorNoiseEnabled()) {
         resolvedOceanFloorTopY = minY;
      } else {
         resolvedOceanFloorTopY = OceanFloorNoise.oceanFloorTopY(worldX, worldZ, minY, maxY, settings, layoutSeed);
         resolvedOceanFloorTopY = WaterVolumeReservationRegistry.adjustedOceanFloorTopY(worldX, worldZ, resolvedOceanFloorTopY, layoutSeed);
         resolvedOceanFloorTopY = OceanFloorReservationRegistry.adjustedOceanFloorTopY(worldX, worldZ, resolvedOceanFloorTopY, layoutSeed);
      }

      int segmentCount = segments.size();
      int[] solidBottoms = new int[segmentCount];
      int[] solidTops = new int[segmentCount];
      int highestSolidY = minY - 1;
      int writeIndex = 0;

      for (TerrainColumn segment : segments) {
         int clampedMinY = Math.max(minY, segment.bottomY());
         int clampedMaxY = Math.min(maxY - 1, segment.topY());
         if (clampedMinY <= clampedMaxY) {
            solidBottoms[writeIndex] = clampedMinY;
            solidTops[writeIndex] = clampedMaxY;
            highestSolidY = Math.max(highestSolidY, clampedMaxY);
            writeIndex++;
         }
      }

      if (writeIndex != segmentCount) {
         solidBottoms = Arrays.copyOf(solidBottoms, writeIndex);
         solidTops = Arrays.copyOf(solidTops, writeIndex);
      }

      return new SkyIslandColumnMaterialPlan(
         segments,
         minY,
         maxY,
         settings.advanced().deepslateStartY(),
         worldX,
         worldZ,
         layoutSeed,
         settings.terrain().ocean().oceanEnabled(),
         resolvedOceanTopY,
         resolvedOceanFloorTopY,
         solidBottoms,
         solidTops,
         highestSolidY
      );
   }

   public int firstFreeY() {
      int highestSolidY = this.highestSolidY;
      if (this.oceanEnabled && this.oceanTopY >= this.minY) {
         highestSolidY = Math.max(highestSolidY, this.oceanTopY);
      }

      return highestSolidY + 1;
   }

   public List<SkyIslandColumnMaterialPlan.MaterialRange> materialRanges() {
      List<SkyIslandColumnMaterialPlan.MaterialRange> ranges = new ArrayList<>();
      this.forEachMaterialRange((bottomY, topY) -> ranges.add(new SkyIslandColumnMaterialPlan.MaterialRange(bottomY, topY)));
      ranges.sort(Comparator.comparingInt(SkyIslandColumnMaterialPlan.MaterialRange::bottomY).thenComparingInt(SkyIslandColumnMaterialPlan.MaterialRange::topY));
      return List.copyOf(ranges);
   }

   public void forEachMaterialRange(SkyIslandColumnMaterialPlan.MaterialRangeConsumer consumer) {
      List<SkyIslandColumnMaterialPlan.MaterialRange> solidRanges = this.mergedSolidRanges();

      for (SkyIslandColumnMaterialPlan.MaterialRange range : solidRanges) {
         consumer.accept(range.bottomY(), range.topY());
      }

      if (this.oceanEnabled) {
         if (!this.isCoveredByRanges(this.minY, solidRanges)) {
            consumer.accept(this.minY, this.minY);
         }

         int floorBottom = this.minY + 1;
         int floorTop = Math.min(this.maxY - 1, this.oceanFloorTopY);
         this.emitRangeMinusSolids(floorBottom, floorTop, solidRanges, consumer);
         int oceanBottom = Math.max(this.minY + 1, this.oceanFloorTopY + 1);
         int oceanTop = Math.min(this.maxY - 1, this.oceanTopY);
         this.emitRangeMinusSolids(oceanBottom, oceanTop, solidRanges, consumer);
      }
   }

   public BlockState plannedStateAt(int y, BlockState stone, BlockState deepslate, BlockState water, BlockState bedrock) {
      return switch (this.materialSlotAt(y)) {
         case NONE -> null;
         case STONE -> this.hasSolidTerrainAt(y) && this.usesDeepslateAtY(y) ? deepslate : stone;
         case BEDROCK -> bedrock;
         case OCEAN -> water;
      };
   }

   boolean usesDeepslateAtY(int y) {
      if (y <= this.deepslateStartY) {
         return true;
      }

      int bandTopY = this.deepslateStartY + 8;
      if (y > bandTopY) {
         return false;
      }

      int step = y - this.deepslateStartY;
      double progress = (step - 1) / 7.0;
      double deepslateChance = 1.0 - progress;
      return hash01(this.worldX, y, this.worldZ, this.layoutSeed) < deepslateChance;
   }

   static double hash01(int x, int y, int z, long seed) {
      long mixed = seed ^ x * -7046029254386353131L ^ y * -4417276706812531889L ^ z * 1609587929392839161L;
      mixed ^= mixed >>> 33;
      mixed *= -49064778989728563L;
      mixed ^= mixed >>> 33;
      mixed *= -4265267296055464877L;
      mixed ^= mixed >>> 33;
      return (mixed >>> 11) * 1.110223E-16F;
   }

   public SkyIslandColumnMaterialPlan.MaterialSlot materialSlotAt(int y) {
      if (y < this.minY || y >= this.maxY) {
         return SkyIslandColumnMaterialPlan.MaterialSlot.NONE;
      } else if (this.hasSolidTerrainAt(y)) {
         return SkyIslandColumnMaterialPlan.MaterialSlot.STONE;
      } else if (this.oceanEnabled && y == this.minY) {
         return SkyIslandColumnMaterialPlan.MaterialSlot.BEDROCK;
      } else if (this.oceanEnabled && y > this.minY && y <= this.oceanFloorTopY) {
         return SkyIslandColumnMaterialPlan.MaterialSlot.STONE;
      } else {
         return this.oceanEnabled && y > this.minY && y <= this.oceanTopY
            ? SkyIslandColumnMaterialPlan.MaterialSlot.OCEAN
            : SkyIslandColumnMaterialPlan.MaterialSlot.NONE;
      }
   }

   private boolean hasSolidTerrainAt(int y) {
      for (int index = 0; index < this.solidBottoms.length; index++) {
         if (y >= this.solidBottoms[index] && y <= this.solidTops[index]) {
            return true;
         }
      }

      return false;
   }

   private List<SkyIslandColumnMaterialPlan.MaterialRange> mergedSolidRanges() {
      if (this.solidBottoms.length == 0) {
         return List.of();
      }

      List<SkyIslandColumnMaterialPlan.MaterialRange> ranges = new ArrayList<>(this.solidBottoms.length);

      for (int index = 0; index < this.solidBottoms.length; index++) {
         ranges.add(new SkyIslandColumnMaterialPlan.MaterialRange(this.solidBottoms[index], this.solidTops[index]));
      }

      ranges.sort(Comparator.comparingInt(SkyIslandColumnMaterialPlan.MaterialRange::bottomY).thenComparingInt(SkyIslandColumnMaterialPlan.MaterialRange::topY));
      List<SkyIslandColumnMaterialPlan.MaterialRange> merged = new ArrayList<>(ranges.size());
      int currentBottom = ranges.get(0).bottomY();
      int currentTop = ranges.get(0).topY();

      for (int index = 1; index < ranges.size(); index++) {
         SkyIslandColumnMaterialPlan.MaterialRange range = ranges.get(index);
         if (range.bottomY() <= currentTop + 1) {
            currentTop = Math.max(currentTop, range.topY());
         } else {
            merged.add(new SkyIslandColumnMaterialPlan.MaterialRange(currentBottom, currentTop));
            currentBottom = range.bottomY();
            currentTop = range.topY();
         }
      }

      merged.add(new SkyIslandColumnMaterialPlan.MaterialRange(currentBottom, currentTop));
      return merged;
   }

   private void emitRangeMinusSolids(
      int bottomY, int topY, List<SkyIslandColumnMaterialPlan.MaterialRange> solidRanges, SkyIslandColumnMaterialPlan.MaterialRangeConsumer consumer
   ) {
      if (bottomY <= topY) {
         int cursor = bottomY;

         for (SkyIslandColumnMaterialPlan.MaterialRange solid : solidRanges) {
            if (solid.topY() >= cursor) {
               if (solid.bottomY() > topY) {
                  break;
               }

               if (cursor < solid.bottomY()) {
                  consumer.accept(cursor, Math.min(topY, solid.bottomY() - 1));
               }

               cursor = Math.max(cursor, solid.topY() + 1);
               if (cursor > topY) {
                  return;
               }
            }
         }

         consumer.accept(cursor, topY);
      }
   }

   private boolean isCoveredByRanges(int y, List<SkyIslandColumnMaterialPlan.MaterialRange> ranges) {
      for (SkyIslandColumnMaterialPlan.MaterialRange range : ranges) {
         if (y >= range.bottomY() && y <= range.topY()) {
            return true;
         }
      }

      return false;
   }

   public record MaterialRange(int bottomY, int topY) {
   }

   @FunctionalInterface
   public interface MaterialRangeConsumer {
      void accept(int var1, int var2);
   }

   public enum MaterialSlot {
      NONE,
      STONE,
      BEDROCK,
      OCEAN;
   }
}
