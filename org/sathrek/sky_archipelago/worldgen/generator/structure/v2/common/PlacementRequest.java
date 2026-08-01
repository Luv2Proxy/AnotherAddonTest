package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.PreAnchorPlacementContext;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public record PlacementRequest(
   Structure structure,
   ResourceLocation structureId,
   StructureStart structureStart,
   ResolvedStructureSupportPlane supportPlane,
   StructureManager structureManager,
   SectionPos sectionPos,
   ChunkAccess chunk,
   ChunkPos chunkPos,
   SkyIslandSettings settings,
   IslandField islandField,
   long levelSeed,
   PreAnchorPlacementContext preAnchorPlacementContext,
   SkyStructurePlacementTarget islandAwareTarget,
   ResourceKey<Level> dimension,
   PlacementRequest.StructureGenerationContext generationContext
) {
   public PlacementRequest(
      Structure structure,
      ResourceLocation structureId,
      StructureStart structureStart,
      ResolvedStructureSupportPlane supportPlane,
      StructureManager structureManager,
      SectionPos sectionPos,
      ChunkAccess chunk,
      ChunkPos chunkPos,
      SkyIslandSettings settings,
      IslandField islandField,
      long levelSeed,
      PreAnchorPlacementContext preAnchorPlacementContext,
      SkyStructurePlacementTarget islandAwareTarget
   ) {
      this(
         structure,
         structureId,
         structureStart,
         supportPlane,
         structureManager,
         sectionPos,
         chunk,
         chunkPos,
         settings,
         islandField,
         levelSeed,
         preAnchorPlacementContext,
         islandAwareTarget,
         Level.OVERWORLD,
         null
      );
   }

   public record StructureGenerationContext(
      SkyIslandChunkGenerator generator,
      BiomeSource biomeSource,
      RegistryAccess registryAccess,
      RandomState randomState,
      StructureTemplateManager templateManager,
      int references
   ) {
   }
}
