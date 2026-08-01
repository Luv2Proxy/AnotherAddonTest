package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public final class StructureStartRelocator {
   public StructureStart relocate(
      StructureStart structureStart,
      ChunkPos sourceChunkPos,
      StructureFootprint rawFootprint,
      ResolvedStructureSupportPlane supportPlane,
      SkyStructurePlacementTarget target
   ) {
      int offsetX = target.x() - rawFootprint.centerX();
      int offsetZ = target.z() - rawFootprint.centerZ();
      int offsetY = target.y() - supportPlane.baseY();
      return this.relocateByOffsets(structureStart, sourceChunkPos, offsetX, offsetY, offsetZ);
   }

   public StructureStart relocateToChunk(
      StructureStart structureStart,
      ChunkPos targetChunkPos,
      StructureFootprint rawFootprint,
      ResolvedStructureSupportPlane supportPlane,
      SkyStructurePlacementTarget target
   ) {
      int offsetX = target.x() - rawFootprint.centerX();
      int offsetZ = target.z() - rawFootprint.centerZ();
      int offsetY = target.y() - supportPlane.baseY();
      return this.relocateByOffsetsToChunk(structureStart, targetChunkPos, offsetX, offsetY, offsetZ);
   }

   public StructureStart shiftY(StructureStart structureStart, ChunkPos sourceChunkPos, int yOffset) {
      return this.shiftYToChunk(structureStart, sourceChunkPos, yOffset);
   }

   public StructureStart shiftYToChunk(StructureStart structureStart, ChunkPos targetChunkPos, int yOffset) {
      if (yOffset != 0) {
         movePieces(structureStart, 0, yOffset, 0);
      }

      return copyWithChunk(structureStart, targetChunkPos);
   }

   public StructureStart relocateByOffsets(StructureStart structureStart, ChunkPos sourceChunkPos, int offsetX, int offsetY, int offsetZ) {
      return this.relocateByOffsetsToChunk(structureStart, sourceChunkPos, offsetX, offsetY, offsetZ);
   }

   public StructureStart relocateByOffsetsToChunk(StructureStart structureStart, ChunkPos targetChunkPos, int offsetX, int offsetY, int offsetZ) {
      if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
         movePieces(structureStart, offsetX, offsetY, offsetZ);
      }

      return copyWithChunk(structureStart, targetChunkPos);
   }

   private static StructureStart copyWithChunk(StructureStart structureStart, ChunkPos targetChunkPos) {
      return new StructureStart(structureStart.getStructure(), targetChunkPos, structureStart.getReferences(), new PiecesContainer(structureStart.getPieces()));
   }

   private static void movePieces(StructureStart structureStart, int offsetX, int offsetY, int offsetZ) {
      Set<StructurePiece> movedPieces = Collections.newSetFromMap(new IdentityHashMap<>());
      Set<Object> visitedContainers = Collections.newSetFromMap(new IdentityHashMap<>());

      for (StructurePiece piece : structureStart.getPieces()) {
         movePieceTree(piece, offsetX, offsetY, offsetZ, movedPieces, visitedContainers);
      }
   }

   private static void movePieceTree(
      StructurePiece piece, int offsetX, int offsetY, int offsetZ, Set<StructurePiece> movedPieces, Set<Object> visitedContainers
   ) {
      if (piece != null && movedPieces.add(piece)) {
         piece.move(offsetX, offsetY, offsetZ);
         moveNestedPieces(piece, offsetX, offsetY, offsetZ, movedPieces, visitedContainers);
      }
   }

   private static void moveNestedPieces(Object owner, int offsetX, int offsetY, int offsetZ, Set<StructurePiece> movedPieces, Set<Object> visitedContainers) {
      if (owner != null && visitedContainers.add(owner)) {
         for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
               if (!Modifier.isStatic(field.getModifiers())) {
                  Object value = fieldValue(field, owner);
                  moveNestedValue(value, offsetX, offsetY, offsetZ, movedPieces, visitedContainers);
               }
            }
         }
      }
   }

   private static Object fieldValue(Field field, Object owner) {
      try {
         if (!field.canAccess(owner)) {
            field.setAccessible(true);
         }

         return field.get(owner);
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         return null;
      }
   }

   private static void moveNestedValue(Object value, int offsetX, int offsetY, int offsetZ, Set<StructurePiece> movedPieces, Set<Object> visitedContainers) {
      if (value instanceof StructurePiece piece) {
         movePieceTree(piece, offsetX, offsetY, offsetZ, movedPieces, visitedContainers);
      } else if (value instanceof Iterable<?> iterable) {
         moveNestedPieces(iterable, offsetX, offsetY, offsetZ, movedPieces, visitedContainers);

         for (Object element : iterable) {
            moveNestedValue(element, offsetX, offsetY, offsetZ, movedPieces, visitedContainers);
         }
      } else {
         if (value != null && value.getClass().isArray()) {
            moveNestedPieces(value, offsetX, offsetY, offsetZ, movedPieces, visitedContainers);
            int length = Array.getLength(value);

            for (int index = 0; index < length; index++) {
               moveNestedValue(Array.get(value, index), offsetX, offsetY, offsetZ, movedPieces, visitedContainers);
            }
         }
      }
   }
}
