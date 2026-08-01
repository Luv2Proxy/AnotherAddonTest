package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class RelocatedPlacementContextManager {
   private static final Map<StructureStart, RelocatedPlacementContextManager.PlacementDescriptor> DESCRIPTORS = Collections.synchronizedMap(new WeakHashMap<>());
   private static final ThreadLocal<RelocatedPlacementContext> ACTIVE = new ThreadLocal<>();
   private static final PostProcessDriftPolicy LOCKED_POLICY = new LockedYClampPolicy();
   private static final PostProcessDriftPolicy DEFAULT_POLICY = new DefaultPostProcessDriftPolicy();

   private RelocatedPlacementContextManager() {
   }

   public static void registerRelocatedStart(
      StructureStart relocatedStart,
      ResourceLocation structureId,
      ChunkPos startChunk,
      ResourceKey<Level> dimension,
      int authorityAnchorY,
      boolean yLockEnabled
   ) {
      if (relocatedStart != null && structureId != null && startChunk != null && dimension != null) {
         DESCRIPTORS.put(
            relocatedStart, new RelocatedPlacementContextManager.PlacementDescriptor(structureId, startChunk, dimension, authorityAnchorY, yLockEnabled)
         );
      }
   }

   public static RelocatedPlacementContextManager.Scope activateFor(StructureStart structureStart) {
      RelocatedPlacementContextManager.PlacementDescriptor descriptor = DESCRIPTORS.get(structureStart);
      if (descriptor == null) {
         return RelocatedPlacementContextManager.Scope.noop();
      }

      PostProcessDriftPolicy policy = descriptor.yLockEnabled() ? LOCKED_POLICY : DEFAULT_POLICY;
      RelocatedPlacementContext context = new RelocatedPlacementContext(
         descriptor.structureId(),
         descriptor.startChunk(),
         descriptor.dimension(),
         descriptor.authorityAnchorY(),
         descriptor.yLockEnabled(),
         policy,
         StructurePlacementAdapterRegistry.adapterFor(descriptor.structureId())
      );
      ACTIVE.set(context);
      return RelocatedPlacementContextManager.Scope.active();
   }

   public static RelocatedPlacementContext active() {
      return ACTIVE.get();
   }

   public static void clearForTests() {
      DESCRIPTORS.clear();
      ACTIVE.remove();
   }

   private record PlacementDescriptor(
      ResourceLocation structureId, ChunkPos startChunk, ResourceKey<Level> dimension, int authorityAnchorY, boolean yLockEnabled
   ) {
   }

   public static final class Scope implements AutoCloseable {
      private final boolean active;

      private Scope(boolean active) {
         this.active = active;
      }

      private static RelocatedPlacementContextManager.Scope active() {
         return new RelocatedPlacementContextManager.Scope(true);
      }

      private static RelocatedPlacementContextManager.Scope noop() {
         return new RelocatedPlacementContextManager.Scope(false);
      }

      @Override
      public void close() {
         if (this.active) {
            RelocatedPlacementContextManager.ACTIVE.remove();
         }
      }
   }
}
