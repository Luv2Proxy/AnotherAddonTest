package org.sathrek.sky_archipelago.mixin.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument.OceanMonumentCaptureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructurePiece.class)
public abstract class StructurePieceCaptureMixin {
   @Shadow
   protected abstract MutableBlockPos getWorldPos(int var1, int var2, int var3);

   @Redirect(
      method = "placeBlock",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
      ),
      require = 0
   )
   private boolean sky_archipelago$capturePlacedBlock(
      WorldGenLevel level,
      BlockPos pos,
      BlockState state,
      int flags,
      WorldGenLevel originalLevel,
      BlockState originalState,
      int x,
      int y,
      int z,
      BoundingBox box
   ) {
      OceanMonumentCaptureContext context = OceanMonumentCaptureContext.active();
      return context != null && OceanMonumentCaptureContext.activeFor(level) ? context.captureBlock(pos, state, box) : level.setBlock(pos, state, flags);
   }

   @Redirect(
      method = "fillColumnDown",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
      ),
      require = 0
   )
   private boolean sky_archipelago$captureColumnBlock(
      WorldGenLevel level,
      BlockPos pos,
      BlockState state,
      int flags,
      WorldGenLevel originalLevel,
      BlockState originalState,
      int x,
      int y,
      int z,
      BoundingBox box
   ) {
      OceanMonumentCaptureContext context = OceanMonumentCaptureContext.active();
      return context != null && OceanMonumentCaptureContext.activeFor(level) ? context.captureBlock(pos, state, box) : level.setBlock(pos, state, flags);
   }

   @Inject(method = "getBlock", at = @At("HEAD"), cancellable = true)
   private void sky_archipelago$readCapturedOverlay(BlockGetter level, int x, int y, int z, BoundingBox box, CallbackInfoReturnable<BlockState> callbackInfo) {
      if (level instanceof WorldGenLevel worldGenLevel) {
         OceanMonumentCaptureContext context = OceanMonumentCaptureContext.active();
         if (context != null && OceanMonumentCaptureContext.activeFor(worldGenLevel)) {
            BlockPos pos = this.getWorldPos(x, y, z);
            if (box.isInside(pos)) {
               callbackInfo.setReturnValue(context.blockAt(worldGenLevel, pos));
            }
         }
      }
   }
}
