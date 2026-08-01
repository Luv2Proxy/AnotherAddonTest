package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

public final class RelocatedPlacementContext {
   private final ResourceLocation structureId;
   private final ChunkPos startChunk;
   private final ResourceKey<Level> dimension;
   private final int authorityAnchorY;
   private final boolean yLockEnabled;
   private final PostProcessDriftTelemetry telemetry;
   private final PostProcessDriftPolicy policy;
   private final StructurePlacementAdapter adapter;

   RelocatedPlacementContext(
      ResourceLocation structureId,
      ChunkPos startChunk,
      ResourceKey<Level> dimension,
      int authorityAnchorY,
      boolean yLockEnabled,
      PostProcessDriftPolicy policy,
      StructurePlacementAdapter adapter
   ) {
      this.structureId = structureId;
      this.startChunk = startChunk;
      this.dimension = dimension;
      this.authorityAnchorY = authorityAnchorY;
      this.yLockEnabled = yLockEnabled;
      this.policy = policy;
      this.adapter = adapter;
      this.telemetry = new PostProcessDriftTelemetry();
   }

   public ResourceLocation structureId() {
      return this.structureId;
   }

   public ChunkPos startChunk() {
      return this.startChunk;
   }

   public ResourceKey<Level> dimension() {
      return this.dimension;
   }

   public int authorityAnchorY() {
      return this.authorityAnchorY;
   }

   public boolean yLockEnabled() {
      return this.yLockEnabled;
   }

   public boolean suppressIglooSurfaceAdjustment(StructurePiece piece) {
      return this.adapter.suppressIglooSurfaceAdjustment(this, piece);
   }

   public PostProcessDriftDecision evaluateDrift(StructurePiece piece, BoundingBox before, BoundingBox after) {
      return this.policy.evaluate(this, piece, before, after);
   }

   public void recordDrift(StructurePiece piece, BoundingBox before, BoundingBox after, PostProcessDriftDecision decision) {
      this.telemetry.record(piece, before, after, decision, this);
   }

   public String pieceRole(StructurePiece piece) {
      return this.adapter.pieceRole(piece);
   }
}
