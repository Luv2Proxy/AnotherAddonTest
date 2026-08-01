package org.sathrek.sky_archipelago.mixin.worldgen;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces$OceanMonumentPiece")
public interface OceanMonumentPieceAccessor {
   @Invoker("generateWaterBox")
   void sky_archipelago$invokeGenerateWaterBox(WorldGenLevel var1, BoundingBox var2, int var3, int var4, int var5, int var6, int var7, int var8);
}
