package org.sathrek.sky_archipelago.mixin.worldgen;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureSaveDataSanitizer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapSaveSanitizerMixin {
   @Shadow
   @Final
   private ServerLevel level;

   @Inject(method = "save", at = @At("HEAD"))
   private void sky_archipelago$sanitizeStructureSaveDataBeforeChunkSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
      StructureSaveDataSanitizer.sanitize(this.level == null ? null : this.level.registryAccess(), chunk, "chunk_map_save_head");
   }
}
