package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class PlacementCommitCoordinator {
   private final StructureOverlapGuard overlapGuard;
   private final OccupiedVolumeEnvelopeBuilder envelopeBuilder;

   public PlacementCommitCoordinator(StructureOverlapGuard overlapGuard) {
      this.overlapGuard = overlapGuard;
      this.envelopeBuilder = new PieceAwareOccupiedVolumeEnvelopeBuilder();
   }

   public PlacementCommitCoordinator.Decision reserveOrConflict(
      StructureStart structureStart, BoundingBox candidateBounds, StructurePlacementCategory category, ReservationContext context
   ) {
      BoundingBox envelope = this.envelopeBuilder.envelope(structureStart, candidateBounds);
      BoundingBox authoritative = candidateBounds;
      boolean fallbackToAuthoritative = envelope == null || !looksUsableEnvelope(envelope, authoritative);
      if (fallbackToAuthoritative && SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER
            .warn(
               "FCFS_ENVELOPE_FALLBACK id={} sourceChunk=[{}, {}] envelope={} authoritative={}",
               new Object[]{context.structureId(), context.sourceChunkPos().x, context.sourceChunkPos().z, format(envelope), format(authoritative)}
            );
         envelope = null;
      }

      FcfsArbiter.Decision reservation = this.overlapGuard.tryReserveTiered(envelope, authoritative, category, context);
      if (reservation.accepted()) {
         return PlacementCommitCoordinator.Decision.allowed(
            envelope,
            authoritative,
            reservation.prefilterCandidates(),
            reservation.authoritativeChecks(),
            fallbackToAuthoritative || reservation.fallbackToAuthoritative()
         );
      }

      ReservedPlacement conflict = reservation.conflictWith();
      String details = conflict == null
         ? "fcfs_3d_overlap"
         : "fcfs_3d_overlap envelope="
            + format(envelope)
            + " authoritative="
            + format(authoritative)
            + " claimedBy="
            + conflict.structureId()
            + " claimedFromChunk=["
            + conflict.sourceChunkX()
            + ","
            + conflict.sourceChunkZ()
            + "] claimedBounds="
            + format(conflict.occupiedBounds());
      return PlacementCommitCoordinator.Decision.rejected(
         "fcfs_3d_overlap",
         details,
         envelope,
         authoritative,
         reservation.prefilterCandidates(),
         reservation.authoritativeChecks(),
         fallbackToAuthoritative || reservation.fallbackToAuthoritative()
      );
   }

   private static String format(BoundingBox bounds) {
      return bounds == null
         ? "null"
         : "[" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + " -> " + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ() + "]";
   }

   private static boolean looksUsableEnvelope(BoundingBox envelope, BoundingBox authoritative) {
      return envelope != null && authoritative != null ? intersects(envelope, authoritative) : false;
   }

   private static boolean intersects(BoundingBox a, BoundingBox b) {
      return a.minX() <= b.maxX() && a.maxX() >= b.minX() && a.minY() <= b.maxY() && a.maxY() >= b.minY() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
   }

   public record Decision(
      boolean accepted,
      String stage,
      String details,
      BoundingBox envelopeBounds,
      BoundingBox authoritativeBounds,
      int prefilterCandidates,
      int authoritativeChecks,
      boolean fallbackToAuthoritative
   ) {
      public static PlacementCommitCoordinator.Decision allowed(
         BoundingBox envelopeBounds, BoundingBox authoritativeBounds, int prefilterCandidates, int authoritativeChecks, boolean fallbackToAuthoritative
      ) {
         String details = "accepted envelope="
            + PlacementCommitCoordinator.format(envelopeBounds)
            + " authoritative="
            + PlacementCommitCoordinator.format(authoritativeBounds)
            + " prefilterCandidates="
            + prefilterCandidates
            + " authoritativeChecks="
            + authoritativeChecks
            + " fallbackToAuthoritative="
            + fallbackToAuthoritative;
         return new PlacementCommitCoordinator.Decision(
            true, "accepted", details, envelopeBounds, authoritativeBounds, prefilterCandidates, authoritativeChecks, fallbackToAuthoritative
         );
      }

      public static PlacementCommitCoordinator.Decision rejected(
         String stage,
         String details,
         BoundingBox envelopeBounds,
         BoundingBox authoritativeBounds,
         int prefilterCandidates,
         int authoritativeChecks,
         boolean fallbackToAuthoritative
      ) {
         return new PlacementCommitCoordinator.Decision(
            false, stage, details, envelopeBounds, authoritativeBounds, prefilterCandidates, authoritativeChecks, fallbackToAuthoritative
         );
      }
   }
}
