package org.sathrek.sky_archipelago.mixin.worldgen;

import java.util.Optional;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(JigsawStructure.class)
public interface JigsawStructureAccessor {
   @Accessor("startHeight")
   HeightProvider sky_archipelago$startHeight();

   @Accessor("projectStartToHeightmap")
   Optional<Types> sky_archipelago$projectStartToHeightmap();
}
