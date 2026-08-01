package org.sathrek.sky_archipelago.worldgen.structure.sky;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.sathrek.sky_archipelago.worldgen.WorldgenPerformanceMetrics;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LocalOffset;

public final class LocalOffsetSampler {
   private final ConcurrentMap<LocalOffsetSampler.OffsetKey, List<LocalOffset>> cache = new ConcurrentHashMap<>();

   public List<LocalOffset> localOffsets(int radius, int step, int fineStep) {
      List<LocalOffset> offsets = this.cache.computeIfAbsent(new LocalOffsetSampler.OffsetKey(radius, step, fineStep), LocalOffsetSampler::buildOffsets);
      WorldgenPerformanceMetrics.recordLocalOffsets(radius, step, fineStep, offsets.size());
      return offsets;
   }

   private static List<LocalOffset> buildOffsets(LocalOffsetSampler.OffsetKey key) {
      List<LocalOffset> offsets = new ArrayList<>();

      for (int offsetX = -key.radius(); offsetX <= key.radius(); offsetX += key.step()) {
         for (int offsetZ = -key.radius(); offsetZ <= key.radius(); offsetZ += key.step()) {
            if (offsetX * offsetX + offsetZ * offsetZ <= key.radius() * key.radius()) {
               offsets.add(new LocalOffset(offsetX, offsetZ, offsetX * offsetX + offsetZ * offsetZ));
            }
         }
      }

      if (key.fineStep() > 0 && key.fineStep() < key.step()) {
         int fineRadius = Math.min(key.radius(), Math.max(key.fineStep() * 3, key.radius() / 3));

         for (int offsetX = -fineRadius; offsetX <= fineRadius; offsetX += key.fineStep()) {
            for (int offsetZ = -fineRadius; offsetZ <= fineRadius; offsetZ += key.fineStep()) {
               if (offsetX * offsetX + offsetZ * offsetZ <= fineRadius * fineRadius) {
                  offsets.add(new LocalOffset(offsetX, offsetZ, offsetX * offsetX + offsetZ * offsetZ));
               }
            }
         }
      }

      if (offsets.stream().noneMatch(offset -> offset.offsetX() == 0 && offset.offsetZ() == 0)) {
         offsets.add(new LocalOffset(0, 0, 0));
      }

      offsets = new ArrayList<>(offsets.stream().distinct().toList());
      offsets.sort(Comparator.comparingInt(LocalOffset::distanceSquared).thenComparingInt(LocalOffset::offsetX).thenComparingInt(LocalOffset::offsetZ));
      return List.copyOf(offsets);
   }

   private record OffsetKey(int radius, int step, int fineStep) {
   }
}
