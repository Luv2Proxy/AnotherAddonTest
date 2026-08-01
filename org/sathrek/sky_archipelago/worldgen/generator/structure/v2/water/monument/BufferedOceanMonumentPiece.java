package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.sathrek.sky_archipelago.worldgen.registry.ModWorldgenRegistries;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public final class BufferedOceanMonumentPiece extends StructurePiece {
   private static final String LEVEL_SEED_TAG = "LevelSeed";
   private static final String SOURCE_CHUNK_X_TAG = "SourceChunkX";
   private static final String SOURCE_CHUNK_Z_TAG = "SourceChunkZ";
   private static final String MIN_X_TAG = "MinX";
   private static final String MIN_Z_TAG = "MinZ";
   private static final String FINAL_MIN_Y_TAG = "FinalMinY";
   private static final String BODY_FLOOR_Y_TAG = "BodyFloorY";
   private static final String WATER_TOP_Y_TAG = "WaterTopY";
   private static final String FOOTPRINT_MIN_X_TAG = "FootprintMinX";
   private static final String FOOTPRINT_MAX_X_TAG = "FootprintMaxX";
   private static final String FOOTPRINT_MIN_Z_TAG = "FootprintMinZ";
   private static final String FOOTPRINT_MAX_Z_TAG = "FootprintMaxZ";
   private static final String DIRECTION_TAG = "Direction";
   private final long levelSeed;
   private final int sourceChunkX;
   private final int sourceChunkZ;
   private final int minX;
   private final int minZ;
   private final int finalMinY;
   private final int bodyFloorY;
   private final int waterTopY;
   private final Direction direction;
   private final StructureFootprint footprint;
   private final OceanMonumentBufferedPlacer placer;

   public BufferedOceanMonumentPiece(
      long levelSeed, ChunkPos sourceChunk, int minX, int finalMinY, int minZ, int bodyFloorY, int waterTopY, Direction direction, StructureFootprint footprint
   ) {
      this(
         levelSeed,
         sourceChunk.x,
         sourceChunk.z,
         minX,
         minZ,
         finalMinY,
         bodyFloorY,
         waterTopY,
         direction,
         footprint,
         new BoundingBox(minX, finalMinY, minZ, minX + 57, finalMinY + 22, minZ + 57),
         new OceanMonumentBufferedPlacer()
      );
   }

   public BufferedOceanMonumentPiece(CompoundTag tag) {
      this(
         tag.getLong("LevelSeed"),
         tag.getInt("SourceChunkX"),
         tag.getInt("SourceChunkZ"),
         tag.getInt("MinX"),
         tag.getInt("MinZ"),
         tag.getInt("FinalMinY"),
         tag.getInt("BodyFloorY"),
         tag.getInt("WaterTopY"),
         Direction.from2DDataValue(tag.getInt("Direction")),
         new StructureFootprint(tag.getInt("FootprintMinX"), tag.getInt("FootprintMaxX"), tag.getInt("FootprintMinZ"), tag.getInt("FootprintMaxZ")),
         null,
         new OceanMonumentBufferedPlacer()
      );
   }

   private BufferedOceanMonumentPiece(
      long levelSeed,
      int sourceChunkX,
      int sourceChunkZ,
      int minX,
      int minZ,
      int finalMinY,
      int bodyFloorY,
      int waterTopY,
      Direction direction,
      StructureFootprint footprint,
      BoundingBox boundingBox,
      OceanMonumentBufferedPlacer placer
   ) {
      super(
         (StructurePieceType)ModWorldgenRegistries.BUFFERED_OCEAN_MONUMENT_PIECE.get(),
         0,
         boundingBox != null ? boundingBox : new BoundingBox(minX, finalMinY, minZ, minX + 57, finalMinY + 22, minZ + 57)
      );
      this.levelSeed = levelSeed;
      this.sourceChunkX = sourceChunkX;
      this.sourceChunkZ = sourceChunkZ;
      this.minX = minX;
      this.minZ = minZ;
      this.finalMinY = finalMinY;
      this.bodyFloorY = bodyFloorY;
      this.waterTopY = waterTopY;
      this.direction = direction;
      this.footprint = footprint;
      this.placer = placer;
      this.setOrientation(direction);
   }

   protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
      tag.putLong("LevelSeed", this.levelSeed);
      tag.putInt("SourceChunkX", this.sourceChunkX);
      tag.putInt("SourceChunkZ", this.sourceChunkZ);
      tag.putInt("MinX", this.minX);
      tag.putInt("MinZ", this.minZ);
      tag.putInt("FinalMinY", this.finalMinY);
      tag.putInt("BodyFloorY", this.bodyFloorY);
      tag.putInt("WaterTopY", this.waterTopY);
      tag.putInt("FootprintMinX", this.footprint.minX());
      tag.putInt("FootprintMaxX", this.footprint.maxX());
      tag.putInt("FootprintMinZ", this.footprint.minZ());
      tag.putInt("FootprintMaxZ", this.footprint.maxZ());
      tag.putInt("Direction", this.direction.get2DDataValue());
   }

   public void postProcess(
      WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos
   ) {
      this.placer
         .place(
            level,
            structureManager,
            generator,
            box,
            chunkPos,
            pos,
            this.levelSeed,
            this.sourceChunkX,
            this.sourceChunkZ,
            this.minX,
            this.minZ,
            this.finalMinY,
            this.waterTopY,
            this.direction,
            this.footprint
         );
   }

   public int bodyFloorY() {
      return this.bodyFloorY;
   }
}
