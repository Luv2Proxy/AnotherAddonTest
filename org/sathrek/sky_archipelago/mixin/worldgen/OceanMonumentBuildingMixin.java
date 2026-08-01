package org.sathrek.sky_archipelago.mixin.worldgen;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces.MonumentBuilding;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument.OceanMonumentBuildingYOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MonumentBuilding.class)
public abstract class OceanMonumentBuildingMixin {
   @Unique
   private boolean sky_archipelago$anchorFirstMonument;

   @ModifyConstant(method = "<init>(Lnet/minecraft/util/RandomSource;IILnet/minecraft/core/Direction;)V", constant = @Constant(intValue = 39), require = 1)
   private static int sky_archipelago$usePlannedMonumentY(int vanillaY) {
      return OceanMonumentBuildingYOverride.resolve(vanillaY);
   }

   @Inject(method = "<init>(Lnet/minecraft/util/RandomSource;IILnet/minecraft/core/Direction;)V", at = @At("RETURN"))
   private void sky_archipelago$markAnchorFirstMonument(CallbackInfo callbackInfo) {
      this.sky_archipelago$anchorFirstMonument = OceanMonumentBuildingYOverride.active();
   }

   @Redirect(
      method = "postProcess",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces$MonumentBuilding;generateWaterBox(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;IIIIII)V",
         ordinal = 0
      ),
      require = 1
   )
   private void sky_archipelago$skipAnchorFirstSeaLevelBackfill(
      MonumentBuilding instance, WorldGenLevel level, BoundingBox boundingBox, int x1, int y1, int z1, int x2, int y2, int z2
   ) {
      if (!this.sky_archipelago$anchorFirstMonument) {
         ((OceanMonumentPieceAccessor)instance).sky_archipelago$invokeGenerateWaterBox(level, boundingBox, x1, y1, z1, x2, y2, z2);
      } else {
         if (SkyIslandServerConfig.structureDebugEnabled()) {
            SkyArchipelago.LOGGER
               .info(
                  "OCEAN_MONUMENT_DEBUG stage=skip_sea_level_backfill bounds=[{},{},{} -> {},{},{}] localBox=[{},{},{} -> {},{},{}] seaLevel={}",
                  new Object[]{
                     boundingBox.minX(),
                     boundingBox.minY(),
                     boundingBox.minZ(),
                     boundingBox.maxX(),
                     boundingBox.maxY(),
                     boundingBox.maxZ(),
                     x1,
                     y1,
                     z1,
                     x2,
                     y2,
                     z2,
                     level.getSeaLevel()
                  }
               );
         }
      }
   }
}
