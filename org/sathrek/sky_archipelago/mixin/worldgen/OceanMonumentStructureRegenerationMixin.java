package org.sathrek.sky_archipelago.mixin.worldgen;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument.OceanMonumentBuildingYOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure")
public abstract class OceanMonumentStructureRegenerationMixin {
   @Unique
   private static final ThreadLocal<OceanMonumentBuildingYOverride.Scope> SKY_ARCHIPELAGO$REGEN_SCOPE = new ThreadLocal<>();

   @Inject(method = "regeneratePiecesAfterLoad", at = @At("HEAD"))
   private static void sky_archipelago$applyRegenerationYOverride(
      ChunkPos chunkPos, long seed, PiecesContainer piecesContainer, CallbackInfoReturnable<PiecesContainer> cir
   ) {
      if (piecesContainer != null && !piecesContainer.isEmpty()) {
         StructurePiece first = (StructurePiece)piecesContainer.pieces().get(0);
         int regenMinY = first.getBoundingBox().minY();
         SKY_ARCHIPELAGO$REGEN_SCOPE.set(OceanMonumentBuildingYOverride.push(regenMinY));
         if (SkyIslandServerConfig.structureDebugEnabled()) {
            SkyArchipelago.LOGGER
               .info(
                  "OCEAN_MONUMENT_DEBUG stage=regen_policy_applied chunk=[{}, {}] regenPolicyApplied=preserve_loaded_min_y appliedOverrideMinY={}",
                  new Object[]{chunkPos.x, chunkPos.z, regenMinY}
               );
         }
      }
   }

   @Inject(method = "regeneratePiecesAfterLoad", at = @At("RETURN"))
   private static void sky_archipelago$clearRegenerationYOverride(
      ChunkPos chunkPos, long seed, PiecesContainer piecesContainer, CallbackInfoReturnable<PiecesContainer> cir
   ) {
      OceanMonumentBuildingYOverride.Scope scope = SKY_ARCHIPELAGO$REGEN_SCOPE.get();
      SKY_ARCHIPELAGO$REGEN_SCOPE.remove();
      if (scope != null) {
         scope.close();
      }
   }
}
