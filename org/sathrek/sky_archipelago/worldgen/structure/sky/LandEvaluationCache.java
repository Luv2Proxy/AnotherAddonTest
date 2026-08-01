package org.sathrek.sky_archipelago.worldgen.structure.sky;

import java.util.HashMap;
import java.util.Map;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;

public final class LandEvaluationCache {
   private final Map<Long, TerrainColumn> columnByXZ = new HashMap<>();
   private final Map<Long, Boolean> supportByXZAndBaseY = new HashMap<>();

   public TerrainColumn sampleColumn(StructureSupportContext context, int x, int z) {
      long key = packPair(x, z);
      return this.columnByXZ.computeIfAbsent(key, ignored -> context.islandField().sampleColumn(x, z, context.settings()));
   }

   public boolean hasSupportBelow(StructureSupportContext context, int x, int z, int baseY) {
      long key = mixSupportKey(x, z, baseY);
      return this.supportByXZAndBaseY
         .computeIfAbsent(
            key, ignored -> context.islandField().hasSupportBelow(x, z, baseY, context.settings().structureSupport().supportCheckDepth(), context.settings())
         );
   }

   private static long packPair(int x, int z) {
      return (long)x << 32 ^ z & 4294967295L;
   }

   private static long mixSupportKey(int x, int z, int baseY) {
      long hash = packPair(x, z);
      hash ^= baseY * -7046029254386353131L;
      hash ^= hash >>> 33;
      hash *= -49064778989728563L;
      return hash ^ hash >>> 33;
   }
}
