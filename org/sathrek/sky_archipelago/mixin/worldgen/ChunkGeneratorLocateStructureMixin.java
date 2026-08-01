package org.sathrek.sky_archipelago.mixin.worldgen;

import com.mojang.datafixers.util.Pair;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureLocateIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLocateStructureMixin {
   @Inject(method = "findNearestMapStructure", at = @At("RETURN"), cancellable = true)
   private void sky_archipelago$correctLocateNearestStructureForCommand(
      ServerLevel level,
      HolderSet<Structure> structures,
      BlockPos origin,
      int radius,
      boolean skipKnownStructures,
      CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir
   ) {
      Pair<BlockPos, Holder<Structure>> vanilla = (Pair<BlockPos, Holder<Structure>>)cir.getReturnValue();
      if (vanilla != null && structures != null && structures.size() != 0) {
         Set<ResourceLocation> structureIds = new HashSet<>();

         for (Holder<Structure> holder : structures) {
            holder.unwrapKey().ifPresent(key -> structureIds.add(key.location()));
         }

         if (!structureIds.isEmpty()) {
            RelocatedStructureLocateIndex.findNearestForStructureIds(level, structureIds, origin, radius)
               .ifPresent(relocatedPos -> cir.setReturnValue(Pair.of(relocatedPos, (Holder)vanilla.getSecond())));
         }
      }
   }
}
