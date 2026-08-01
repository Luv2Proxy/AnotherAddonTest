package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class OceanMonumentCaptureContext implements AutoCloseable {
   private static final ThreadLocal<OceanMonumentCaptureContext> ACTIVE = new ThreadLocal<>();
   private final WorldGenLevel level;
   private final int yShift;
   private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
   private boolean closed;

   private OceanMonumentCaptureContext(WorldGenLevel level, int yShift) {
      this.level = level;
      this.yShift = yShift;
   }

   public static OceanMonumentCaptureContext begin(WorldGenLevel level, int yShift) {
      OceanMonumentCaptureContext context = new OceanMonumentCaptureContext(level, yShift);
      ACTIVE.set(context);
      return context;
   }

   public static OceanMonumentCaptureContext active() {
      return ACTIVE.get();
   }

   public static boolean activeFor(WorldGenLevel level) {
      OceanMonumentCaptureContext context = ACTIVE.get();
      return context != null && context.level == level;
   }

   public boolean captureBlock(BlockPos pos, BlockState state, BoundingBox box) {
      if (!box.isInside(pos)) {
         return false;
      }

      this.blocks.put(pos.immutable(), state);
      return true;
   }

   public BlockState blockAt(WorldGenLevel level, BlockPos pos) {
      BlockState captured = this.blocks.get(pos);
      if (captured != null) {
         return captured;
      }

      BlockPos shifted = pos.offset(0, this.yShift, 0);
      return shifted.getY() >= level.getMinBuildHeight() && shifted.getY() < level.getMaxBuildHeight()
         ? level.getBlockState(shifted)
         : Blocks.AIR.defaultBlockState();
   }

   public Map<BlockPos, BlockState> blocks() {
      return Collections.unmodifiableMap(this.blocks);
   }

   @Override
   public void close() {
      if (!this.closed) {
         ACTIVE.remove();
         this.closed = true;
      }
   }
}
