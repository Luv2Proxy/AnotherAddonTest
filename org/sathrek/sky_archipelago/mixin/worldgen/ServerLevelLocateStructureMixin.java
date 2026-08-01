package org.sathrek.sky_archipelago.mixin.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureLocateIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelLocateStructureMixin {
   @Inject(method = "findNearestMapStructure", at = @At("RETURN"), cancellable = true)
   private void sky_archipelago$correctLocateNearestStructure(
      TagKey<Structure> tag, BlockPos origin, int radius, boolean skipKnownStructures, CallbackInfoReturnable<BlockPos> cir
   ) {
      if (cir.getReturnValue() != null) {
         ServerLevel level = (ServerLevel)this;
         RelocatedStructureLocateIndex.findNearestForTag(level, tag, origin, radius).ifPresent(cir::setReturnValue);
      }
   }
}
