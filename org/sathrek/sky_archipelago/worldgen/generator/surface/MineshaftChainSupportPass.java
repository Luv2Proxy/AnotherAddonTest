package org.sathrek.sky_archipelago.worldgen.generator.surface;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

final class MineshaftChainSupportPass {
   private static final int COLUMN_SPACING_MOD = 4;
   private static final int MAX_CHAIN_LENGTH = 96;

   private MineshaftChainSupportPass() {
   }

   static void apply(WorldGenRegion level, StructureManager structureManager, ChunkAccess chunk) {
      Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      List<MineshaftChainSupportPass.StructureCandidate> candidates = new ArrayList<>();

      for (Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
         ResourceLocation structureId = structureRegistry.getKey(entry.getKey());
         if (isMineshaftStructure(structureId)) {
            candidates.add(new MineshaftChainSupportPass.StructureCandidate(structureId, entry.getValue()));
         }
      }

      structureRegistry.forEach(structure -> {
         ResourceLocation structureId = structureRegistry.getKey(structure);
         if (isMineshaftStructure(structureId)) {
            for (StructureStart referencedStart : safeStartsForStructure(structureManager, chunk.getPos(), candidatex -> candidatex == structure)) {
               candidates.add(new MineshaftChainSupportPass.StructureCandidate(structureId, referencedStart));
            }
         }
      });
      int chunkMinX = chunk.getPos().getMinBlockX();
      int chunkMinZ = chunk.getPos().getMinBlockZ();
      int chunkMaxX = chunkMinX + 15;
      int chunkMaxZ = chunkMinZ + 15;
      int minY = chunk.getMinBuildHeight();
      MutableBlockPos pos = new MutableBlockPos();
      Set<MineshaftChainSupportPass.StartFingerprint> seen = new HashSet<>();

      for (MineshaftChainSupportPass.StructureCandidate candidate : candidates) {
         StructureStart start = candidate.structureStart();
         if (start != null && start.isValid()) {
            MineshaftChainSupportPass.StartFingerprint fingerprint = MineshaftChainSupportPass.StartFingerprint.from(candidate.structureId(), start);
            if (seen.add(fingerprint)) {
               for (StructurePiece piece : start.getPieces()) {
                  BoundingBox box = piece.getBoundingBox();
                  int minX = Math.max(chunkMinX, box.minX());
                  int maxX = Math.min(chunkMaxX, box.maxX());
                  int minZ = Math.max(chunkMinZ, box.minZ());
                  int maxZ = Math.min(chunkMaxZ, box.maxZ());
                  if (minX <= maxX && minZ <= maxZ) {
                     int scanY = box.minY();
                     if (scanY > minY) {
                        for (int worldX = minX; worldX <= maxX; worldX++) {
                           for (int worldZ = minZ; worldZ <= maxZ; worldZ++) {
                              if (shouldPlaceSupportAt(worldX, worldZ)) {
                                 pos.set(worldX, scanY, worldZ);
                                 if (chunk.getBlockState(pos).is(BlockTags.PLANKS)) {
                                    placeVerticalChainIfNeeded(chunk, pos, minY);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   static boolean shouldPlaceSupportAt(int worldX, int worldZ) {
      long hash = 1469598103934665603L;
      hash ^= worldX;
      hash *= 1099511628211L;
      hash ^= worldZ;
      hash *= 1099511628211L;
      return Math.floorMod(hash, 4) == 0;
   }

   private static void placeVerticalChainIfNeeded(ChunkAccess chunk, MutableBlockPos plankPos, int minY) {
      int x = plankPos.getX();
      int z = plankPos.getZ();
      int startY = plankPos.getY() - 1;
      if (startY > minY) {
         MutableBlockPos below = new MutableBlockPos(x, startY, z);
         if (chunk.getBlockState(below).isAir()) {
            int placed = 0;

            for (int y = startY; y > minY && placed < 96; y--) {
               below.setY(y);
               if (!chunk.getBlockState(below).isAir()) {
                  break;
               }

               chunk.setBlockState(below, Blocks.CHAIN.defaultBlockState(), false);
               placed++;
            }
         }
      }
   }

   private static Iterable<StructureStart> safeStartsForStructure(StructureManager structureManager, ChunkPos chunkPos, Predicate<Structure> predicate) {
      try {
         return structureManager.startsForStructure(chunkPos, predicate);
      } catch (IllegalStateException ignored) {
         return List.of();
      }
   }

   private static boolean isMineshaftStructure(ResourceLocation structureId) {
      return structureId != null && structureId.getPath().contains("mineshaft");
   }

   static MineshaftChainSupportPass.StartFingerprint fingerprintForTest(
      ResourceLocation structureId, ChunkPos sourceChunkPos, BoundingBox bounds, int pieceCount
   ) {
      return new MineshaftChainSupportPass.StartFingerprint(
         structureId == null ? "unknown" : structureId.toString(),
         sourceChunkPos.x,
         sourceChunkPos.z,
         bounds.minX(),
         bounds.maxX(),
         bounds.minZ(),
         bounds.maxZ(),
         bounds.minY(),
         bounds.maxY(),
         pieceCount
      );
   }

   record StartFingerprint(String structureId, int sourceChunkX, int sourceChunkZ, int minX, int maxX, int minZ, int maxZ, int minY, int maxY, int pieceCount) {
      static MineshaftChainSupportPass.StartFingerprint from(ResourceLocation structureId, StructureStart start) {
         return MineshaftChainSupportPass.fingerprintForTest(structureId, start.getChunkPos(), start.getBoundingBox(), start.getPieces().size());
      }
   }

   private record StructureCandidate(ResourceLocation structureId, StructureStart structureStart) {
   }
}
