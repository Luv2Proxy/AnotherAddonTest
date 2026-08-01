package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class FcfsArbiter {
   private final ReservationReader reservationReader;
   private final ReservationWriter reservationWriter;
   private final OverlapPolicy overlapPolicy;

   public FcfsArbiter(ReservationReader reservationReader, ReservationWriter reservationWriter, OverlapPolicy overlapPolicy) {
      this.reservationReader = reservationReader;
      this.reservationWriter = reservationWriter;
      this.overlapPolicy = overlapPolicy;
   }

   public FcfsArbiter.Decision wouldConflict(BoundingBox candidateBounds, StructurePlacementCategory candidateCategory, long levelSeed) {
      for (ReservedPlacement placement : this.reservationReader.candidatesForBounds(candidateBounds)) {
         if (placement.seedTag() == levelSeed
            && this.overlapPolicy.conflicts(candidateBounds, candidateCategory, placement.occupiedBounds(), placement.category())) {
            return FcfsArbiter.Decision.rejected(placement);
         }
      }

      return FcfsArbiter.Decision.allowed();
   }

   public FcfsArbiter.Decision tryReserve(BoundingBox candidateBounds, StructurePlacementCategory candidateCategory, ReservationContext context) {
      ReservedPlacement candidate = new ReservedPlacement(
         context.structureId(), context.sourceChunkPos().x, context.sourceChunkPos().z, candidateBounds, candidateCategory, 0L, context.levelSeed()
      );
      if (this.reservationWriter instanceof ReservationStore store) {
         ReservedPlacement conflict = store.reserveIfNoConflict(candidate, this.overlapPolicy);
         return conflict == null ? FcfsArbiter.Decision.allowed() : FcfsArbiter.Decision.rejected(conflict);
      } else {
         FcfsArbiter.Decision conflict = this.wouldConflict(candidateBounds, candidateCategory, context.levelSeed());
         if (!conflict.accepted()) {
            return conflict;
         }

         this.reservationWriter.reserve(candidate);
         return FcfsArbiter.Decision.allowed();
      }
   }

   public FcfsArbiter.Decision tryReserveTiered(
      BoundingBox envelopeBounds, BoundingBox authoritativeBounds, StructurePlacementCategory candidateCategory, ReservationContext context
   ) {
      ReservedPlacement candidate = new ReservedPlacement(
         context.structureId(), context.sourceChunkPos().x, context.sourceChunkPos().z, authoritativeBounds, candidateCategory, 0L, context.levelSeed()
      );
      if (this.reservationWriter instanceof ReservationStore store) {
         ReservationStore.TieredReservationResult result = store.reserveAuthoritativeIfNoConflict(candidate, envelopeBounds, this.overlapPolicy);
         return result.conflict() == null
            ? FcfsArbiter.Decision.allowed(result.prefilterCandidates(), result.authoritativeChecks(), result.fallbackToAuthoritative())
            : FcfsArbiter.Decision.rejected(result.conflict(), result.prefilterCandidates(), result.authoritativeChecks(), result.fallbackToAuthoritative());
      } else {
         FcfsArbiter.Decision conflict = this.wouldConflict(authoritativeBounds, candidateCategory, context.levelSeed());
         if (!conflict.accepted()) {
            return FcfsArbiter.Decision.rejected(conflict.conflictWith(), 0, 0, true);
         }

         this.reservationWriter.reserve(candidate);
         return FcfsArbiter.Decision.allowed(0, 0, true);
      }
   }

   public record Decision(boolean accepted, ReservedPlacement conflictWith, int prefilterCandidates, int authoritativeChecks, boolean fallbackToAuthoritative) {
      public static FcfsArbiter.Decision allowed() {
         return new FcfsArbiter.Decision(true, null, 0, 0, false);
      }

      public static FcfsArbiter.Decision allowed(int prefilterCandidates, int authoritativeChecks, boolean fallbackToAuthoritative) {
         return new FcfsArbiter.Decision(true, null, prefilterCandidates, authoritativeChecks, fallbackToAuthoritative);
      }

      public static FcfsArbiter.Decision rejected(ReservedPlacement placement) {
         return new FcfsArbiter.Decision(false, placement, 0, 0, false);
      }

      public static FcfsArbiter.Decision rejected(
         ReservedPlacement placement, int prefilterCandidates, int authoritativeChecks, boolean fallbackToAuthoritative
      ) {
         return new FcfsArbiter.Decision(false, placement, prefilterCandidates, authoritativeChecks, fallbackToAuthoritative);
      }
   }
}
