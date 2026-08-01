package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class StructureOverlapGuard {
   public boolean wouldOverlap(StructureFootprint candidateFootprint, StructurePlacementCategory category, long levelSeed) {
      return StructurePlacementReservationRegistry.wouldOverlap(candidateFootprint, category, levelSeed);
   }

   public boolean tryReserve(StructureFootprint candidateFootprint, StructurePlacementCategory category, ReservationContext context) {
      return StructurePlacementReservationRegistry.tryReserve(candidateFootprint, category, context);
   }

   public boolean wouldOverlap(BoundingBox candidateBounds, StructurePlacementCategory category, long levelSeed) {
      return StructurePlacementReservationRegistry.wouldOverlap(candidateBounds, category, levelSeed);
   }

   public FcfsArbiter.Decision tryReserve(BoundingBox candidateBounds, StructurePlacementCategory category, ReservationContext context) {
      return StructurePlacementReservationRegistry.tryReserve(candidateBounds, category, context);
   }

   public FcfsArbiter.Decision tryReserveTiered(
      BoundingBox envelopeBounds, BoundingBox authoritativeBounds, StructurePlacementCategory category, ReservationContext context
   ) {
      return StructurePlacementReservationRegistry.tryReserveTiered(envelopeBounds, authoritativeBounds, category, context);
   }
}
