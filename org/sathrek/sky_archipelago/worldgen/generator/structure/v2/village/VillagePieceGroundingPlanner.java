package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;

public final class VillagePieceGroundingPlanner {
   private static final int MAX_FARM_TOP_VARIATION = 2;
   private static final int MAX_ROAD_TOP_VARIATION = 6;
   private static final int MAX_BUILDING_TOP_VARIATION = 8;
   private static final int SAMPLE_STEP = 4;
   private final VillagePieceClassifier classifier;

   public VillagePieceGroundingPlanner() {
      this(new VillagePieceClassifier());
   }

   public VillagePieceGroundingPlanner(VillagePieceClassifier classifier) {
      this.classifier = classifier;
   }

   public VillagePieceGroundingPlanner.GroundingResult normalize(
      StructureStart structureStart, ChunkPos sourceChunkPos, IslandField islandField, SkyIslandSettings settings
   ) {
      return this.normalize(
         structureStart,
         sourceChunkPos,
         structureStart.getPieces()
            .stream()
            .map(piece -> new VillagePieceGroundingPlanner.PieceCandidate(piece, this.classifier.classify(piece), piece.getBoundingBox()))
            .toList(),
         islandField,
         settings
      );
   }

   VillagePieceGroundingPlanner.GroundingResult normalize(
      StructureStart structureStart,
      ChunkPos sourceChunkPos,
      List<VillagePieceGroundingPlanner.PieceCandidate> candidates,
      IslandField islandField,
      SkyIslandSettings settings
   ) {
      ArrayList<StructurePiece> kept = new ArrayList<>();
      EnumMap<VillagePieceGroundingPlanner.DropReason, Integer> dropReasons = new EnumMap<>(VillagePieceGroundingPlanner.DropReason.class);
      int snappedPieces = 0;
      int maxUpShift = 0;
      int maxDownShift = 0;
      int farmDrops = 0;
      int failedCorePieces = 0;
      String firstCoreFailure = null;

      for (VillagePieceGroundingPlanner.PieceCandidate candidate : candidates) {
         VillagePieceGroundingPlanner.GroundingDecision decision = this.evaluateBounds(
            candidate.kind(), candidate.bounds(), candidate.isCore(), islandField, settings
         );
         if (decision.outcome() == VillagePieceGroundingPlanner.DecisionOutcome.REJECT) {
            failedCorePieces++;
            if (firstCoreFailure == null) {
               firstCoreFailure = decision.reason();
            }

            increment(dropReasons, VillagePieceGroundingPlanner.DropReason.fromCoreFailure(decision.reason()));
         } else if (decision.outcome() == VillagePieceGroundingPlanner.DecisionOutcome.DROP) {
            if (candidate.kind() == VillagePieceClassifier.PieceKind.FARM) {
               farmDrops++;
            }

            increment(dropReasons, decision.dropReason());
         } else {
            int shiftY = decision.shiftY();
            if (shiftY != 0) {
               candidate.piece().move(0, shiftY, 0);
               snappedPieces++;
               if (shiftY > 0) {
                  maxUpShift = Math.max(maxUpShift, shiftY);
               } else {
                  maxDownShift = Math.max(maxDownShift, -shiftY);
               }
            }

            kept.add(candidate.piece());
         }
      }

      if (kept.isEmpty()) {
         String reason = firstCoreFailure == null ? "empty_after_grounding" : firstCoreFailure;
         return VillagePieceGroundingPlanner.GroundingResult.rejected(reason, 0, candidates.size(), dropReasons);
      } else if (!this.hasNucleus(kept)) {
         String reason = firstCoreFailure == null ? "missing_village_nucleus" : firstCoreFailure;
         return VillagePieceGroundingPlanner.GroundingResult.rejected(reason, kept.size(), candidates.size(), dropReasons);
      } else {
         BoundingBox finalBounds = boundsFor(kept);
         StructureStart normalizedStart = new StructureStart(
            structureStart.getStructure(), sourceChunkPos, structureStart.getReferences(), new PiecesContainer(kept)
         );
         return new VillagePieceGroundingPlanner.GroundingResult(
            true,
            "ground_village_v2_grounded",
            normalizedStart,
            finalBounds,
            kept.size(),
            candidates.size() - kept.size(),
            snappedPieces,
            maxUpShift,
            maxDownShift,
            farmDrops,
            failedCorePieces,
            Map.copyOf(dropReasons)
         );
      }
   }

   VillagePieceGroundingPlanner.GroundingSummary evaluateCandidatesForTesting(
      List<VillagePieceGroundingPlanner.BoundsCandidate> candidates, IslandField islandField, SkyIslandSettings settings
   ) {
      EnumMap<VillagePieceGroundingPlanner.DropReason, Integer> dropReasons = new EnumMap<>(VillagePieceGroundingPlanner.DropReason.class);
      int retainedPieces = 0;
      int failedCorePieces = 0;
      String firstCoreFailure = null;
      boolean hasCenter = false;
      boolean hasBuilding = false;

      for (VillagePieceGroundingPlanner.BoundsCandidate candidate : candidates) {
         VillagePieceGroundingPlanner.GroundingDecision decision = this.evaluateBounds(
            candidate.kind(), candidate.bounds(), candidate.core(), islandField, settings
         );
         if (decision.outcome() == VillagePieceGroundingPlanner.DecisionOutcome.REJECT) {
            failedCorePieces++;
            if (firstCoreFailure == null) {
               firstCoreFailure = decision.reason();
            }

            increment(dropReasons, VillagePieceGroundingPlanner.DropReason.fromCoreFailure(decision.reason()));
         } else if (decision.outcome() == VillagePieceGroundingPlanner.DecisionOutcome.DROP) {
            increment(dropReasons, decision.dropReason());
         } else {
            retainedPieces++;
            if (candidate.kind() == VillagePieceClassifier.PieceKind.CENTER) {
               hasCenter = true;
            }

            if (candidate.kind() == VillagePieceClassifier.PieceKind.BUILDING) {
               hasBuilding = true;
            }
         }
      }

      boolean accepted = retainedPieces > 0 && hasCenter && hasBuilding;
      String stage = accepted
         ? "ground_village_v2_grounded"
         : "ground_village_v2_rejected_" + (firstCoreFailure == null ? "missing_village_nucleus" : firstCoreFailure);
      return new VillagePieceGroundingPlanner.GroundingSummary(
         accepted, stage, retainedPieces, Math.max(0, candidates.size() - retainedPieces), failedCorePieces, Map.copyOf(dropReasons)
      );
   }

   VillagePieceGroundingPlanner.GroundingDecision evaluateBounds(
      VillagePieceClassifier.PieceKind kind, BoundingBox bounds, boolean core, IslandField islandField, SkyIslandSettings settings
   ) {
      VillagePieceGroundingPlanner.PieceGroundSample sample = this.sampleTerrain(supportBoundsFor(bounds, kind), islandField, settings);
      if (!sample.hasTerrain()) {
         return core
            ? VillagePieceGroundingPlanner.GroundingDecision.rejected("core_missing_terrain")
            : VillagePieceGroundingPlanner.GroundingDecision.dropped(VillagePieceGroundingPlanner.DropReason.missing_terrain);
      }

      if (kind == VillagePieceClassifier.PieceKind.FARM && sample.topVariation() > 2) {
         return VillagePieceGroundingPlanner.GroundingDecision.dropped(VillagePieceGroundingPlanner.DropReason.farm_too_uneven);
      }

      if (kind == VillagePieceClassifier.PieceKind.ROAD && sample.topVariation() > 6) {
         return VillagePieceGroundingPlanner.GroundingDecision.dropped(VillagePieceGroundingPlanner.DropReason.road_too_uneven);
      }

      if (kind == VillagePieceClassifier.PieceKind.BUILDING && sample.topVariation() > 8) {
         return VillagePieceGroundingPlanner.GroundingDecision.dropped(VillagePieceGroundingPlanner.DropReason.building_too_uneven);
      }

      int targetBaseY = sample.medianTopY() + targetBaseOffset(kind);
      int shiftY = targetBaseY - bounds.minY();
      return VillagePieceGroundingPlanner.GroundingDecision.accepted(shiftY, sample.medianTopY(), sample.topVariation());
   }

   private VillagePieceGroundingPlanner.PieceGroundSample sampleTerrain(BoundingBox supportBounds, IslandField islandField, SkyIslandSettings settings) {
      ArrayList<Integer> topYs = new ArrayList<>();

      for (int x = supportBounds.minX(); x <= supportBounds.maxX(); x += 4) {
         for (int z = supportBounds.minZ(); z <= supportBounds.maxZ(); z += 4) {
            TerrainColumn column = islandField.sampleColumn(x, z, settings);
            if (column.exists()) {
               topYs.add(column.topY());
            }
         }
      }

      sampleCorner(topYs, supportBounds.minX(), supportBounds.minZ(), islandField, settings);
      sampleCorner(topYs, supportBounds.minX(), supportBounds.maxZ(), islandField, settings);
      sampleCorner(topYs, supportBounds.maxX(), supportBounds.minZ(), islandField, settings);
      sampleCorner(topYs, supportBounds.maxX(), supportBounds.maxZ(), islandField, settings);
      if (topYs.isEmpty()) {
         return VillagePieceGroundingPlanner.PieceGroundSample.missing();
      }

      topYs.sort(Comparator.naturalOrder());
      int median = topYs.get(topYs.size() / 2);
      int variation = topYs.get(topYs.size() - 1) - topYs.get(0);
      return new VillagePieceGroundingPlanner.PieceGroundSample(true, median, variation);
   }

   private static void sampleCorner(List<Integer> topYs, int x, int z, IslandField islandField, SkyIslandSettings settings) {
      TerrainColumn column = islandField.sampleColumn(x, z, settings);
      if (column.exists()) {
         topYs.add(column.topY());
      }
   }

   private static int targetBaseOffset(VillagePieceClassifier.PieceKind kind) {
      return switch (kind) {
         case CENTER, ROAD, FARM -> 0;
         case BUILDING, OTHER -> 1;
      };
   }

   private static BoundingBox supportBoundsFor(BoundingBox bounds, VillagePieceClassifier.PieceKind kind) {
      int minX = bounds.minX();
      int maxX = bounds.maxX();
      int minZ = bounds.minZ();
      int maxZ = bounds.maxZ();
      if (kind == VillagePieceClassifier.PieceKind.BUILDING || kind == VillagePieceClassifier.PieceKind.OTHER) {
         int insetX = Math.max(0, (maxX - minX) / 6);
         int insetZ = Math.max(0, (maxZ - minZ) / 6);
         minX += insetX;
         maxX -= insetX;
         minZ += insetZ;
         maxZ -= insetZ;
      } else if (kind == VillagePieceClassifier.PieceKind.ROAD) {
         int insetX = Math.max(0, (maxX - minX) / 4);
         int insetZ = Math.max(0, (maxZ - minZ) / 4);
         minX += insetX;
         maxX -= insetX;
         minZ += insetZ;
         maxZ -= insetZ;
      } else if (kind == VillagePieceClassifier.PieceKind.FARM) {
         minX -= 2;
         maxX += 2;
         minZ -= 2;
         maxZ += 2;
      }

      if (minX > maxX) {
         int center = (bounds.minX() + bounds.maxX()) / 2;
         minX = center;
         maxX = center;
      }

      if (minZ > maxZ) {
         int center = (bounds.minZ() + bounds.maxZ()) / 2;
         minZ = center;
         maxZ = center;
      }

      return new BoundingBox(minX, bounds.minY(), minZ, maxX, bounds.maxY(), maxZ);
   }

   private static BoundingBox boundsFor(List<StructurePiece> pieces) {
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (StructurePiece piece : pieces) {
         BoundingBox bounds = piece.getBoundingBox();
         minX = Math.min(minX, bounds.minX());
         minY = Math.min(minY, bounds.minY());
         minZ = Math.min(minZ, bounds.minZ());
         maxX = Math.max(maxX, bounds.maxX());
         maxY = Math.max(maxY, bounds.maxY());
         maxZ = Math.max(maxZ, bounds.maxZ());
      }

      return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
   }

   private static void increment(EnumMap<VillagePieceGroundingPlanner.DropReason, Integer> reasons, VillagePieceGroundingPlanner.DropReason reason) {
      reasons.merge(reason, 1, Integer::sum);
   }

   private boolean hasNucleus(List<StructurePiece> pieces) {
      boolean hasCenter = false;
      boolean hasBuilding = false;

      for (StructurePiece piece : pieces) {
         VillagePieceClassifier.PieceKind kind = this.classifier.classify(piece);
         if (kind == VillagePieceClassifier.PieceKind.CENTER) {
            hasCenter = true;
         }

         if (kind == VillagePieceClassifier.PieceKind.BUILDING) {
            hasBuilding = true;
         }
      }

      return hasCenter && hasBuilding;
   }

   record BoundsCandidate(BoundingBox bounds, VillagePieceClassifier.PieceKind kind, boolean core) {
   }

   enum DecisionOutcome {
      ACCEPT,
      DROP,
      REJECT;
   }

   enum DropReason {
      missing_terrain,
      piece_shift_exceeds_cap,
      farm_too_uneven,
      road_too_uneven,
      building_too_uneven,
      core_missing_terrain,
      core_grade_unstable;

      static VillagePieceGroundingPlanner.DropReason fromCoreFailure(String reason) {
         if ("core_missing_terrain".equals(reason)) {
            return core_missing_terrain;
         } else {
            return "core_grade_unstable".equals(reason) ? core_grade_unstable : piece_shift_exceeds_cap;
         }
      }
   }

   record GroundingDecision(
      VillagePieceGroundingPlanner.DecisionOutcome outcome,
      int shiftY,
      int medianTopY,
      int topVariation,
      VillagePieceGroundingPlanner.DropReason dropReason,
      String reason
   ) {
      static VillagePieceGroundingPlanner.GroundingDecision accepted(int shiftY, int medianTopY, int topVariation) {
         return new VillagePieceGroundingPlanner.GroundingDecision(
            VillagePieceGroundingPlanner.DecisionOutcome.ACCEPT, shiftY, medianTopY, topVariation, null, "accepted"
         );
      }

      static VillagePieceGroundingPlanner.GroundingDecision dropped(VillagePieceGroundingPlanner.DropReason reason) {
         return new VillagePieceGroundingPlanner.GroundingDecision(VillagePieceGroundingPlanner.DecisionOutcome.DROP, 0, 0, 0, reason, reason.name());
      }

      static VillagePieceGroundingPlanner.GroundingDecision rejected(String reason) {
         return new VillagePieceGroundingPlanner.GroundingDecision(VillagePieceGroundingPlanner.DecisionOutcome.REJECT, 0, 0, 0, null, reason);
      }
   }

   record GroundingResult(
      boolean accepted,
      String stage,
      StructureStart structureStart,
      BoundingBox finalBounds,
      int retainedPieces,
      int droppedPieces,
      int snappedPieces,
      int maxUpShift,
      int maxDownShift,
      int farmDrops,
      int failedCorePieces,
      Map<VillagePieceGroundingPlanner.DropReason, Integer> dropReasons
   ) {
      static VillagePieceGroundingPlanner.GroundingResult rejected(
         String stage, int retainedPieces, int originalPieces, Map<VillagePieceGroundingPlanner.DropReason, Integer> dropReasons
      ) {
         return new VillagePieceGroundingPlanner.GroundingResult(
            false,
            "ground_village_v2_rejected_" + stage,
            null,
            null,
            retainedPieces,
            Math.max(0, originalPieces - retainedPieces),
            0,
            0,
            0,
            dropReasons.getOrDefault(VillagePieceGroundingPlanner.DropReason.farm_too_uneven, 0),
            dropReasons.getOrDefault(VillagePieceGroundingPlanner.DropReason.core_missing_terrain, 0)
               + dropReasons.getOrDefault(VillagePieceGroundingPlanner.DropReason.core_grade_unstable, 0),
            Map.copyOf(dropReasons)
         );
      }
   }

   record GroundingSummary(
      boolean accepted,
      String stage,
      int retainedPieces,
      int droppedPieces,
      int failedCorePieces,
      Map<VillagePieceGroundingPlanner.DropReason, Integer> dropReasons
   ) {
   }

   record PieceCandidate(StructurePiece piece, VillagePieceClassifier.PieceKind kind, BoundingBox bounds) {
      PieceCandidate {
         bounds = bounds == null ? piece.getBoundingBox() : bounds;
      }

      BoundingBox supportBounds() {
         return VillagePieceGroundingPlanner.supportBoundsFor(this.bounds, this.kind);
      }

      boolean isCore() {
         return this.kind == VillagePieceClassifier.PieceKind.CENTER;
      }
   }

   private record PieceGroundSample(boolean hasTerrain, int medianTopY, int topVariation) {
      static VillagePieceGroundingPlanner.PieceGroundSample missing() {
         return new VillagePieceGroundingPlanner.PieceGroundSample(false, 0, 0);
      }
   }
}
