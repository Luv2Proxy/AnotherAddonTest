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
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;

public final class VillageLayoutTrimmer {
   private final VillageIslandBoundsEvaluator boundsEvaluator;
   private final VillagePieceClassifier classifier;

   public VillageLayoutTrimmer() {
      this(new VillageIslandBoundsEvaluator(), new VillagePieceClassifier());
   }

   public VillageLayoutTrimmer(VillageIslandBoundsEvaluator boundsEvaluator, VillagePieceClassifier classifier) {
      this.boundsEvaluator = boundsEvaluator;
      this.classifier = classifier;
   }

   public VillageLayoutTrimmer.TrimResult trim(StructureStart relocatedStart, ChunkPos sourceChunkPos, HostIsland host, int topY) {
      List<VillageLayoutTrimmer.ClassifiedPiece> candidates = relocatedStart.getPieces()
         .stream()
         .map(piece -> new VillageLayoutTrimmer.ClassifiedPiece(piece, this.classifier.classify(piece)))
         .sorted(pieceOrdering(host))
         .toList();
      if (candidates.isEmpty()) {
         return VillageLayoutTrimmer.TrimResult.rejected(
            "ground_village_v2_rejected_empty_after_trim",
            0,
            relocatedStart.getPieces().size(),
            0,
            relocatedStart.getPieces().size(),
            0,
            relocatedStart.getPieces().size(),
            0,
            Map.of()
         );
      }

      int candidatePieces = candidates.size();
      VillageLayoutTrimmer.ShrinkResult shrinkResult = this.shrinkToFit(candidates, host);
      List<VillageLayoutTrimmer.ClassifiedPiece> retained = shrinkResult.retained();
      boolean hasCore = retained.stream().anyMatch(VillageLayoutTrimmer.ClassifiedPiece::isCore);
      List<StructurePiece> retainedPieces = retained.stream().map(VillageLayoutTrimmer.ClassifiedPiece::piece).toList();
      BoundingBox retainedBounds = boundsFor(retainedPieces);
      if (retainedBounds != null && this.allPiecesFit(host, retained) && this.boundsEvaluator.fits(host, retainedBounds)) {
         long buildings = retained.stream().filter(piece -> piece.kind() == VillagePieceClassifier.PieceKind.BUILDING).count();
         VillageLayoutTrimmer.VillageTier tier = tierFor(relocatedStart.getPieces().size(), retained.size(), buildings, hasCore);
         if (tier == VillageLayoutTrimmer.VillageTier.REJECT) {
            return VillageLayoutTrimmer.TrimResult.rejected(
               "ground_village_v2_rejected_empty_after_trim",
               retained.size(),
               relocatedStart.getPieces().size(),
               candidatePieces,
               shrinkResult.pieceFitDrops() + shrinkResult.finalBoundsDrops(),
               shrinkResult.finalBoundsDrops(),
               shrinkResult.pieceFitDrops(),
               shrinkResult.finalBoundsDrops(),
               dropCounts(candidates, retained)
            );
         }

         StructureStart trimmedStart = new StructureStart(
            relocatedStart.getStructure(), sourceChunkPos, relocatedStart.getReferences(), new PiecesContainer(retainedPieces)
         );
         boolean trimmed = retained.size() < relocatedStart.getPieces().size();
         int boundsDropCount = Math.max(0, candidatePieces - retained.size());
         String stage = trimmed ? stageFor(tier) : "ground_village_v2_accepted";
         return new VillageLayoutTrimmer.TrimResult(
            true,
            stage,
            tier,
            trimmedStart,
            retainedBounds,
            retained.size(),
            relocatedStart.getPieces().size() - retained.size(),
            relocatedStart.getPieces().size(),
            candidatePieces,
            shrinkResult.pieceFitDrops() + shrinkResult.finalBoundsDrops(),
            Math.max(boundsDropCount, shrinkResult.finalBoundsDrops()),
            shrinkResult.pieceFitDrops(),
            shrinkResult.finalBoundsDrops(),
            dropCounts(candidates, retained)
         );
      } else {
         return VillageLayoutTrimmer.TrimResult.rejected(
            "ground_village_v2_rejected_bounds",
            retainedPieces.size(),
            relocatedStart.getPieces().size(),
            candidatePieces,
            shrinkResult.pieceFitDrops() + shrinkResult.finalBoundsDrops(),
            shrinkResult.finalBoundsDrops(),
            shrinkResult.pieceFitDrops(),
            shrinkResult.finalBoundsDrops(),
            dropCounts(candidates, retained)
         );
      }
   }

   VillageLayoutTrimmer.TrimSummary trimForTesting(List<VillageLayoutTrimmer.PieceSpec> pieces, HostIsland host, int originalPieces) {
      List<VillageLayoutTrimmer.ClassifiedPiece> candidates = pieces.stream()
         .map(piece -> new VillageLayoutTrimmer.ClassifiedPiece(null, piece.kind(), piece.bounds()))
         .sorted(pieceOrdering(host))
         .toList();
      if (candidates.isEmpty()) {
         return new VillageLayoutTrimmer.TrimSummary(
            false, "ground_village_v2_rejected_empty_after_trim", VillageLayoutTrimmer.VillageTier.REJECT, null, 0, 0, 0, Map.of()
         );
      }

      VillageLayoutTrimmer.ShrinkResult shrinkResult = this.shrinkToFit(candidates, host);
      List<VillageLayoutTrimmer.ClassifiedPiece> retained = shrinkResult.retained();
      BoundingBox bounds = boundsForClassified(retained);
      boolean hasCore = retained.stream().anyMatch(VillageLayoutTrimmer.ClassifiedPiece::isCore);
      long buildings = retained.stream().filter(piece -> piece.kind() == VillagePieceClassifier.PieceKind.BUILDING).count();
      VillageLayoutTrimmer.VillageTier tier = tierFor(originalPieces, retained.size(), buildings, hasCore);
      boolean accepted = bounds != null && this.boundsEvaluator.fits(host, bounds) && tier != VillageLayoutTrimmer.VillageTier.REJECT;
      String stage = accepted ? stageFor(tier) : "ground_village_v2_rejected_empty_after_trim";
      return new VillageLayoutTrimmer.TrimSummary(
         accepted, stage, tier, bounds, retained.size(), shrinkResult.finalBoundsDrops(), shrinkResult.pieceFitDrops(), dropCounts(candidates, retained)
      );
   }

   private static Comparator<VillageLayoutTrimmer.ClassifiedPiece> pieceOrdering(HostIsland host) {
      return Comparator.<VillageLayoutTrimmer.ClassifiedPiece>comparingInt(piece -> piece.kind().ordinal())
         .thenComparingLong(piece -> distanceSq(piece.bounds(), host))
         .thenComparingInt(piece -> piece.bounds().minX())
         .thenComparingInt(piece -> piece.bounds().minZ());
   }

   private VillageLayoutTrimmer.ShrinkResult shrinkToFit(List<VillageLayoutTrimmer.ClassifiedPiece> candidates, HostIsland host) {
      ArrayList<VillageLayoutTrimmer.ClassifiedPiece> retained = new ArrayList<>(candidates);
      int pieceFitDrops = 0;
      int finalBoundsDrops = 0;

      while (retained.size() > 1) {
         BoundingBox retainedBounds = boundsForClassified(retained);
         boolean piecesFit = this.allPiecesFit(host, retained);
         boolean boundsFit = retainedBounds != null && this.boundsEvaluator.fits(host, retainedBounds);
         if (piecesFit && boundsFit) {
            break;
         }

         VillageLayoutTrimmer.DropCause cause = !piecesFit ? VillageLayoutTrimmer.DropCause.PIECE_FIT : VillageLayoutTrimmer.DropCause.FINAL_BOUNDS;
         int dropIndex = lowestPriorityDropIndex(retained, host, cause);
         if (dropIndex < 0) {
            break;
         }

         retained.remove(dropIndex);
         if (cause == VillageLayoutTrimmer.DropCause.PIECE_FIT) {
            pieceFitDrops++;
         } else {
            finalBoundsDrops++;
         }
      }

      return new VillageLayoutTrimmer.ShrinkResult(retained, pieceFitDrops, finalBoundsDrops);
   }

   private static int lowestPriorityDropIndex(List<VillageLayoutTrimmer.ClassifiedPiece> retained, HostIsland host, VillageLayoutTrimmer.DropCause cause) {
      boolean hasBuilding = retained.stream().anyMatch(piecex -> piecex.kind() == VillagePieceClassifier.PieceKind.BUILDING);
      int bestIndex = -1;
      VillageLayoutTrimmer.DropCandidate best = null;

      for (int index = 0; index < retained.size(); index++) {
         VillageLayoutTrimmer.ClassifiedPiece piece = retained.get(index);
         if (canDropForTrim(piece, retained, hasBuilding, cause)) {
            VillageLayoutTrimmer.DropCandidate candidate = new VillageLayoutTrimmer.DropCandidate(
               dropPriority(piece.kind()), distanceSq(piece.bounds(), host), index
            );
            if (best == null || candidate.compareTo(best) > 0) {
               best = candidate;
               bestIndex = index;
            }
         }
      }

      return bestIndex;
   }

   private static boolean canDropForTrim(
      VillageLayoutTrimmer.ClassifiedPiece piece,
      List<VillageLayoutTrimmer.ClassifiedPiece> retained,
      boolean hasBuilding,
      VillageLayoutTrimmer.DropCause cause
   ) {
      return piece.kind() == VillagePieceClassifier.PieceKind.CENTER && coreCount(retained) <= 1L
         ? false
         : cause != VillageLayoutTrimmer.DropCause.PIECE_FIT
            || piece.kind() != VillagePieceClassifier.PieceKind.BUILDING
            || retained.stream().anyMatch(VillageLayoutTrimmer.ClassifiedPiece::isCore)
            || buildingCount(retained) > 1L
            || !hasBuilding;
   }

   private static int dropPriority(VillagePieceClassifier.PieceKind kind) {
      return switch (kind) {
         case FARM -> 5;
         case OTHER -> 4;
         case BUILDING -> 3;
         case ROAD -> 2;
         case CENTER -> 1;
      };
   }

   private static long coreCount(List<VillageLayoutTrimmer.ClassifiedPiece> pieces) {
      return pieces.stream().filter(VillageLayoutTrimmer.ClassifiedPiece::isCore).count();
   }

   private static long buildingCount(List<VillageLayoutTrimmer.ClassifiedPiece> pieces) {
      return pieces.stream().filter(piece -> piece.kind() == VillagePieceClassifier.PieceKind.BUILDING).count();
   }

   private static String stageFor(VillageLayoutTrimmer.VillageTier tier) {
      return switch (tier) {
         case FULL -> "ground_village_v2_trimmed";
         case HAMLET -> "ground_village_v2_downgraded_hamlet";
         case REJECT -> "ground_village_v2_rejected_empty_after_trim";
      };
   }

   private static long distanceSq(BoundingBox bounds, HostIsland host) {
      long dx = (long)((bounds.minX() + bounds.maxX()) / 2) - host.preview().x();
      long dz = (long)((bounds.minZ() + bounds.maxZ()) / 2) - host.preview().z();
      return dx * dx + dz * dz;
   }

   private static VillageLayoutTrimmer.VillageTier tierFor(int originalPieces, int retainedPieces, long buildings, boolean hasCore) {
      if ((hasCore || buildings > 0L) && retainedPieces > 0) {
         if (retainedPieces == originalPieces && retainedPieces >= 6 && buildings >= 3L && hasCore) {
            return VillageLayoutTrimmer.VillageTier.FULL;
         } else {
            return hasCore && retainedPieces >= 3 && buildings >= 1L ? VillageLayoutTrimmer.VillageTier.HAMLET : VillageLayoutTrimmer.VillageTier.REJECT;
         }
      } else {
         return VillageLayoutTrimmer.VillageTier.REJECT;
      }
   }

   private static BoundingBox boundsFor(List<StructurePiece> pieces) {
      if (pieces.isEmpty()) {
         return null;
      }

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

   private static BoundingBox boundsForClassified(List<VillageLayoutTrimmer.ClassifiedPiece> pieces) {
      if (pieces.isEmpty()) {
         return null;
      }

      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (VillageLayoutTrimmer.ClassifiedPiece piece : pieces) {
         BoundingBox bounds = piece.bounds();
         minX = Math.min(minX, bounds.minX());
         minY = Math.min(minY, bounds.minY());
         minZ = Math.min(minZ, bounds.minZ());
         maxX = Math.max(maxX, bounds.maxX());
         maxY = Math.max(maxY, bounds.maxY());
         maxZ = Math.max(maxZ, bounds.maxZ());
      }

      return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
   }

   private static Map<VillagePieceClassifier.PieceKind, Integer> dropCounts(
      List<VillageLayoutTrimmer.ClassifiedPiece> candidates, List<VillageLayoutTrimmer.ClassifiedPiece> retained
   ) {
      EnumMap<VillagePieceClassifier.PieceKind, Integer> counts = new EnumMap<>(VillagePieceClassifier.PieceKind.class);
      ArrayList<VillageLayoutTrimmer.ClassifiedPiece> remaining = new ArrayList<>(retained);

      for (VillageLayoutTrimmer.ClassifiedPiece candidate : candidates) {
         int retainedIndex = remaining.indexOf(candidate);
         if (retainedIndex >= 0) {
            remaining.remove(retainedIndex);
         } else {
            counts.merge(candidate.kind(), 1, Integer::sum);
         }
      }

      return Map.copyOf(counts);
   }

   private boolean allPiecesFit(HostIsland host, List<VillageLayoutTrimmer.ClassifiedPiece> pieces) {
      return pieces.stream().allMatch(piece -> this.boundsEvaluator.pieceFits(host, piece.bounds(), piece.kind()));
   }

   private record ClassifiedPiece(StructurePiece piece, VillagePieceClassifier.PieceKind kind, BoundingBox bounds) {
      ClassifiedPiece(StructurePiece piece, VillagePieceClassifier.PieceKind kind) {
         this(piece, kind, piece.getBoundingBox());
      }

      boolean isCore() {
         return this.kind == VillagePieceClassifier.PieceKind.CENTER;
      }

      VillageLayoutTrimmer.ClassifiedPiece asKind(VillagePieceClassifier.PieceKind nextKind) {
         return new VillageLayoutTrimmer.ClassifiedPiece(this.piece, nextKind, this.bounds);
      }
   }

   private record DropCandidate(int priority, long distanceSq, int index) implements Comparable<VillageLayoutTrimmer.DropCandidate> {
      public int compareTo(VillageLayoutTrimmer.DropCandidate other) {
         int priorityCompare = Integer.compare(this.priority, other.priority);
         if (priorityCompare != 0) {
            return priorityCompare;
         }

         int distanceCompare = Long.compare(this.distanceSq, other.distanceSq);
         return distanceCompare != 0 ? distanceCompare : Integer.compare(this.index, other.index);
      }
   }

   private enum DropCause {
      PIECE_FIT,
      FINAL_BOUNDS;
   }

   record PieceSpec(BoundingBox bounds, VillagePieceClassifier.PieceKind kind) {
   }

   private record ShrinkResult(List<VillageLayoutTrimmer.ClassifiedPiece> retained, int pieceFitDrops, int finalBoundsDrops) {
   }

   public record TrimResult(
      boolean accepted,
      String stage,
      VillageLayoutTrimmer.VillageTier tier,
      StructureStart structureStart,
      BoundingBox finalBounds,
      int retainedPieces,
      int removedPieces,
      int originalPieces,
      int candidatePieces,
      int envelopeDropCount,
      int boundsDropCount,
      int pieceFitDropCount,
      int finalBoundsDropCount,
      Map<VillagePieceClassifier.PieceKind, Integer> boundsDropCounts
   ) {
      static VillageLayoutTrimmer.TrimResult rejected(
         String stage,
         int retainedPieces,
         int originalPieces,
         int candidatePieces,
         int envelopeDropCount,
         int boundsDropCount,
         int pieceFitDropCount,
         int finalBoundsDropCount,
         Map<VillagePieceClassifier.PieceKind, Integer> boundsDropCounts
      ) {
         return new VillageLayoutTrimmer.TrimResult(
            false,
            stage,
            VillageLayoutTrimmer.VillageTier.REJECT,
            null,
            null,
            retainedPieces,
            Math.max(0, originalPieces - retainedPieces),
            originalPieces,
            candidatePieces,
            envelopeDropCount,
            boundsDropCount,
            pieceFitDropCount,
            finalBoundsDropCount,
            Map.copyOf(boundsDropCounts)
         );
      }
   }

   record TrimSummary(
      boolean accepted,
      String stage,
      VillageLayoutTrimmer.VillageTier tier,
      BoundingBox finalBounds,
      int retainedPieces,
      int boundsDropCount,
      int envelopeDropCount,
      Map<VillagePieceClassifier.PieceKind, Integer> boundsDropCounts
   ) {
   }

   public enum VillageTier {
      FULL,
      HAMLET,
      REJECT;
   }
}
