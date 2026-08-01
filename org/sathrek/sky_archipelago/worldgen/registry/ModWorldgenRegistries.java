package org.sathrek.sky_archipelago.worldgen.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument.BufferedOceanMonumentPiece;

public final class ModWorldgenRegistries {
   private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(
      BuiltInRegistries.CHUNK_GENERATOR, "sky_archipelago"
   );
   private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(BuiltInRegistries.STRUCTURE_PIECE, "sky_archipelago");
   public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<SkyIslandChunkGenerator>> SKY_ISLAND = CHUNK_GENERATORS.register(
      "sky_island", () -> SkyIslandChunkGenerator.CODEC
   );
   public static final DeferredHolder<StructurePieceType, StructurePieceType> BUFFERED_OCEAN_MONUMENT_PIECE = STRUCTURE_PIECES.register(
      "buffered_ocean_monument", () -> BufferedOceanMonumentPiece::new
   );

   private ModWorldgenRegistries() {
   }

   public static void register(IEventBus modEventBus) {
      CHUNK_GENERATORS.register(modEventBus);
      STRUCTURE_PIECES.register(modEventBus);
   }
}
