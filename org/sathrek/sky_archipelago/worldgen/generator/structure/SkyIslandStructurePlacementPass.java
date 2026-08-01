package org.sathrek.sky_archipelago.worldgen.generator.structure;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureReferenceRegistry;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.ground.SmallGroundStructurePlacementEngine;

public final class SkyIslandStructurePlacementPass {
   private SkyIslandStructurePlacementPass() {
   }

   public static void createStructures(
      SkyIslandChunkGenerator generator,
      BiomeSource biomeSource,
      RegistryAccess registryAccess,
      ChunkGeneratorStructureState structureState,
      StructureManager structureManager,
      ChunkAccess chunk,
      StructureTemplateManager templateManager,
      SkyIslandSettings settings,
      IslandField islandField
   ) {
      StructureSaveDataSanitizer.sanitize(registryAccess, chunk, "structure_pass_start");
      RelocatedStructureReferenceRegistry.applyPending(structureManager, chunk, registryAccess);
      SmallGroundStructurePlacementEngine.flushPendingSpawnAnnouncements();
      StructurePlacementOrchestrator.createStructures(
         generator, biomeSource, registryAccess, structureState, structureManager, chunk, templateManager, settings, islandField
      );
      SmallGroundStructurePlacementEngine.flushPendingSpawnAnnouncements();
   }
}
