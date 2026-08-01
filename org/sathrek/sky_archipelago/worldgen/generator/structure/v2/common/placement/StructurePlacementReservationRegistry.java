package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class StructurePlacementReservationRegistry {
   private static final ReservationStore STORE = new ReservationStore();
   private static final FcfsArbiter ARBITER = new FcfsArbiter(STORE, STORE, new OverlapPolicy3D());

   private StructurePlacementReservationRegistry() {
   }

   public static boolean wouldOverlap(StructureFootprint candidateFootprint, StructurePlacementCategory candidateCategory, long levelSeed) {
      BoundingBox candidateBounds = footprintBounds(candidateFootprint);
      return !ARBITER.wouldConflict(candidateBounds, candidateCategory, levelSeed).accepted();
   }

   public static boolean wouldOverlap(BoundingBox candidateBounds, StructurePlacementCategory candidateCategory, long levelSeed) {
      return !ARBITER.wouldConflict(candidateBounds, candidateCategory, levelSeed).accepted();
   }

   public static boolean tryReserve(StructureFootprint candidateFootprint, StructurePlacementCategory candidateCategory, ReservationContext context) {
      return ARBITER.tryReserve(footprintBounds(candidateFootprint), candidateCategory, context).accepted();
   }

   public static FcfsArbiter.Decision tryReserve(BoundingBox candidateBounds, StructurePlacementCategory candidateCategory, ReservationContext context) {
      return ARBITER.tryReserve(candidateBounds, candidateCategory, context);
   }

   public static FcfsArbiter.Decision tryReserveTiered(
      BoundingBox envelopeBounds, BoundingBox authoritativeBounds, StructurePlacementCategory candidateCategory, ReservationContext context
   ) {
      return ARBITER.tryReserveTiered(envelopeBounds, authoritativeBounds, candidateCategory, context);
   }

   static boolean intersects2dPadded(StructureFootprint a, StructureFootprint b, int padA, int padB) {
      int expandA = Math.max(0, padA);
      int expandB = Math.max(0, padB);
      return a.minX() - expandA <= b.maxX() + expandB
         && a.maxX() + expandA >= b.minX() - expandB
         && a.minZ() - expandA <= b.maxZ() + expandB
         && a.maxZ() + expandA >= b.minZ() - expandB;
   }

   public static void clearForTests() {
      STORE.clearForTests();
   }

   public static List<ReservedPlacement> debugCandidatesForBounds(BoundingBox bounds) {
      return STORE.candidatesForBounds(bounds);
   }

   private static BoundingBox footprintBounds(StructureFootprint footprint) {
      return new BoundingBox(footprint.minX(), -536870912, footprint.minZ(), footprint.maxX(), 536870911, footprint.maxZ());
   }
}
