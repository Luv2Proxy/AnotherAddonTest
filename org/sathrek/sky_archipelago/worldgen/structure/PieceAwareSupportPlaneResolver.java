package org.sathrek.sky_archipelago.worldgen.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class PieceAwareSupportPlaneResolver implements StructureSupportPlaneResolver {
   private static final int SUPPORT_SLICE_HEIGHT = 2;
   private static final double SUBSTANTIAL_AREA_RATIO = 0.35;
   private static final int MIN_SUBSTANTIAL_AREA = 9;

   @Override
   public Optional<ResolvedStructureSupportPlane> resolve(ResourceLocation structureId, StructureStart structureStart, double footprintInsetRatio) {
      if (!structureStart.isValid()) {
         return Optional.empty();
      }

      List<BoundingBox> pieceBounds = structureStart.getPieces().stream().<BoundingBox>map(StructurePiece::getBoundingBox).toList();
      return Optional.of(this.resolveFromPieceBounds(pieceBounds, structureStart.getBoundingBox(), footprintInsetRatio));
   }

   ResolvedStructureSupportPlane resolveFromPieceBounds(List<BoundingBox> pieceBounds, BoundingBox fallbackBounds, double footprintInsetRatio) {
      if (pieceBounds.isEmpty()) {
         return fallbackFromBounds(fallbackBounds, footprintInsetRatio);
      }

      List<Integer> candidateBaseYs = pieceBounds.stream().<Integer>map(BoundingBox::minY).distinct().sorted().toList();
      if (candidateBaseYs.isEmpty()) {
         return fallbackFromBounds(fallbackBounds, footprintInsetRatio);
      }

      List<PieceAwareSupportPlaneResolver.SupportSlice> slices = candidateBaseYs.stream()
         .map(baseY -> this.buildSupportSlice(baseY, pieceBounds))
         .filter(slice -> !slice.pieceBounds().isEmpty() && slice.area() > 0)
         .toList();
      if (slices.isEmpty()) {
         return fallbackFromBounds(fallbackBounds, footprintInsetRatio);
      }

      int peakArea = slices.stream().mapToInt(PieceAwareSupportPlaneResolver.SupportSlice::area).max().orElse(0);
      int requiredArea = Math.max(9, (int)Math.ceil(peakArea * 0.35));
      PieceAwareSupportPlaneResolver.SupportSlice chosenSlice = slices.stream()
         .filter(slice -> slice.area() >= requiredArea)
         .min(Comparator.comparingInt(PieceAwareSupportPlaneResolver.SupportSlice::baseY))
         .orElse(null);
      if (chosenSlice == null) {
         return fallbackFromBounds(fallbackBounds, footprintInsetRatio);
      }

      StructureFootprint rawFootprint = toFootprint(chosenSlice.pieceBounds());
      return rawFootprint == null
         ? fallbackFromBounds(fallbackBounds, footprintInsetRatio)
         : new ResolvedStructureSupportPlane(
            chosenSlice.baseY(), rawFootprint, rawFootprint.insetByRatio(footprintInsetRatio), false, chosenSlice.pieceBounds().size(), chosenSlice.area()
         );
   }

   private PieceAwareSupportPlaneResolver.SupportSlice buildSupportSlice(int baseY, List<BoundingBox> pieceBounds) {
      List<BoundingBox> supportingBounds = pieceBounds.stream()
         .filter(bounds -> bounds.minY() >= baseY && bounds.minY() <= baseY + 2)
         .collect(Collectors.toCollection(ArrayList::new));
      return new PieceAwareSupportPlaneResolver.SupportSlice(baseY, supportingBounds, unionArea(supportingBounds));
   }

   private static ResolvedStructureSupportPlane fallbackFromBounds(BoundingBox bounds, double footprintInsetRatio) {
      StructureFootprint rawFootprint = new StructureFootprint(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
      return new ResolvedStructureSupportPlane(
         bounds.minY(),
         rawFootprint,
         rawFootprint.insetByRatio(footprintInsetRatio),
         true,
         0,
         Math.max(0, (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1))
      );
   }

   private static StructureFootprint toFootprint(List<BoundingBox> pieceBounds) {
      if (pieceBounds.isEmpty()) {
         return null;
      }

      int minX = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (BoundingBox bounds : pieceBounds) {
         minX = Math.min(minX, bounds.minX());
         maxX = Math.max(maxX, bounds.maxX());
         minZ = Math.min(minZ, bounds.minZ());
         maxZ = Math.max(maxZ, bounds.maxZ());
      }

      return new StructureFootprint(minX, maxX, minZ, maxZ);
   }

   private static int unionArea(List<BoundingBox> bounds) {
      if (bounds.isEmpty()) {
         return 0;
      }

      List<Integer> xBreakpoints = new ArrayList<>();

      for (BoundingBox bound : bounds) {
         xBreakpoints.add(bound.minX());
         xBreakpoints.add(bound.maxX() + 1);
      }

      xBreakpoints = xBreakpoints.stream().distinct().sorted().toList();
      if (xBreakpoints.size() < 2) {
         return 0;
      }

      int totalArea = 0;

      for (int index = 0; index < xBreakpoints.size() - 1; index++) {
         int spanMinX = xBreakpoints.get(index);
         int spanMaxXExclusive = xBreakpoints.get(index + 1);
         int spanWidth = spanMaxXExclusive - spanMinX;
         if (spanWidth > 0) {
            List<int[]> zIntervals = bounds.stream()
               .filter(bound -> bound.minX() < spanMaxXExclusive && bound.maxX() + 1 > spanMinX)
               .map(bound -> new int[]{bound.minZ(), bound.maxZ() + 1})
               .sorted(Comparator.comparingInt(intervalx -> intervalx[0]))
               .toList();
            if (!zIntervals.isEmpty()) {
               int mergedLength = 0;
               int currentStart = zIntervals.get(0)[0];
               int currentEnd = zIntervals.get(0)[1];

               for (int i = 1; i < zIntervals.size(); i++) {
                  int[] interval = zIntervals.get(i);
                  if (interval[0] <= currentEnd) {
                     currentEnd = Math.max(currentEnd, interval[1]);
                  } else {
                     mergedLength += currentEnd - currentStart;
                     currentStart = interval[0];
                     currentEnd = interval[1];
                  }
               }

               mergedLength += currentEnd - currentStart;
               totalArea += spanWidth * mergedLength;
            }
         }
      }

      return totalArea;
   }

   private record SupportSlice(int baseY, List<BoundingBox> pieceBounds, int area) {
   }
}
