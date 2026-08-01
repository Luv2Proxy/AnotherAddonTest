package org.sathrek.sky_archipelago.worldgen.generator.field.forced;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;

public final class ForcedIslandRegistry {
   private final Map<Long, List<IslandField.IslandDescriptor>> forcedDescriptorsByCell = new ConcurrentHashMap<>();
   private final Set<Long> forcedHostCenters = ConcurrentHashMap.newKeySet();
   private final List<ForcedIslandRegistry.ForcedHostZone> forcedHostZones = new CopyOnWriteArrayList<>();
   private final AtomicLong forcedDescriptorRevision = new AtomicLong(0L);

   public long revision() {
      return this.forcedDescriptorRevision.get();
   }

   public List<IslandField.IslandDescriptor> forcedDescriptorsForCell(int cellX, int cellZ) {
      List<IslandField.IslandDescriptor> forced = this.forcedDescriptorsByCell.get(packPair(cellX, cellZ));
      return forced == null ? List.of() : forced;
   }

   public List<IslandField.IslandDescriptor> filterAnchorsInsideProtectionZones(List<IslandField.IslandDescriptor> descriptors) {
      if (!this.forcedHostZones.isEmpty() && !descriptors.isEmpty()) {
         ArrayList<IslandField.IslandDescriptor> filtered = new ArrayList<>(descriptors.size());

         for (IslandField.IslandDescriptor descriptor : descriptors) {
            if (descriptor.family() != IslandField.IslandFamily.ANCHOR_PLATEAU
                  && descriptor.family() != IslandField.IslandFamily.SATELLITE
                  && descriptor.family() != IslandField.IslandFamily.SPIRE
               || !this.isInsideForcedHostProtectionZone(descriptor.centerX(), descriptor.centerZ())) {
               filtered.add(descriptor);
            }
         }

         return filtered;
      } else {
         return descriptors;
      }
   }

   public boolean injectForcedHostIsland(
      long layoutSeed, int spacing, int centerX, int centerY, int centerZ, int minRadius, int maxRadius, long seedTag, SkyIslandSettings settings
   ) {
      long centerKey = packPair(centerX, centerZ);
      if (!this.forcedHostCenters.add(centerKey)) {
         return false;
      }

      int clampedMin = Math.max(8, minRadius);
      int clampedMax = Math.max(clampedMin, maxRadius);
      int range = clampedMax - clampedMin;
      int radius = clampedMin;
      if (range > 0) {
         long mixed = seedTag ^ layoutSeed ^ (long)centerX << 32 ^ centerZ;
         int offset = (int)Math.floorMod(mixed, range + 1L);
         radius = clampedMin + offset;
      }

      int plateauHeight = Math.max(22, (int)Math.round(radius * 0.36));
      int cliffDepth = Math.max(10, (int)Math.round(radius * 0.24));
      int hangDepth = Math.max(28, (int)Math.round(radius * 0.95));
      IslandField.IslandDescriptor descriptor = new IslandField.IslandDescriptor(
         IslandField.IslandFamily.ANCHOR_PLATEAU,
         centerX,
         centerY,
         centerZ,
         radius,
         radius,
         radius,
         0.0,
         IslandShapeArchetype.CLASSIC,
         plateauHeight,
         cliffDepth,
         hangDepth,
         0,
         0,
         Math.max(8, radius - 8),
         Math.max(8, radius - 8),
         0.04,
         0,
         0,
         0,
         seedTag
      );
      int cellX = Mth.floorDiv(centerX, spacing);
      int cellZ = Mth.floorDiv(centerZ, spacing);
      this.forcedDescriptorsByCell.compute(packPair(cellX, cellZ), (ignored, existing) -> {
         if (existing != null && !existing.isEmpty()) {
            ArrayList<IslandField.IslandDescriptor> merged = new ArrayList<>(existing.size() + 1);
            merged.addAll((Collection<? extends IslandField.IslandDescriptor>)existing);
            merged.add(descriptor);
            return List.copyOf(merged);
         } else {
            return List.of(descriptor);
         }
      });
      int protectionRadius = Math.max(radius + 48, (int)Math.round(radius * 1.35));
      this.forcedHostZones.add(new ForcedIslandRegistry.ForcedHostZone(centerX, centerZ, (long)protectionRadius * protectionRadius));
      this.forcedDescriptorRevision.incrementAndGet();
      return true;
   }

   private boolean isInsideForcedHostProtectionZone(int x, int z) {
      for (ForcedIslandRegistry.ForcedHostZone zone : this.forcedHostZones) {
         long dx = (long)x - zone.centerX();
         long dz = (long)z - zone.centerZ();
         if (dx * dx + dz * dz <= zone.radiusSq()) {
            return true;
         }
      }

      return false;
   }

   private static long packPair(int x, int z) {
      return (long)x << 32 ^ z & 4294967295L;
   }

   private record ForcedHostZone(int centerX, int centerZ, long radiusSq) {
   }
}
