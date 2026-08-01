package org.sathrek.sky_archipelago.worldgen.structure;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public interface StructureSupportPlaneResolver {
   Optional<ResolvedStructureSupportPlane> resolve(ResourceLocation var1, StructureStart var2, double var3);
}
