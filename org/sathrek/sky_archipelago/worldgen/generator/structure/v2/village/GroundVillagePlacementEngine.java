package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.CategoryPlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementDecision;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.AcceptedStructurePlacement;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIslandKey;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostQuery;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.PlannedStructurePlacement;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.PreAnchorPlacementContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.IslandHostIndex;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.IslandSiteSelector;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementCommitCoordinator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.ReservationContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureAnchorPlanner;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureHostSelector;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureOverlapGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureStartRelocator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.policy.StructurePlacementPolicies;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.AcceptedStructurePlacementRegistry;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureLocateIndex;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureReferenceRegistry;
import org.sathrek.sky_archipelago.worldgen.structure.JigsawStartHeightOffsetResolver;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class GroundVillagePlacementEngine implements CategoryPlacementEngine {
   private final IslandHostIndex hostIndex;
   private final StructureHostSelector hostSelector;
   private final IslandSiteSelector siteSelector;
   private final StructureAnchorPlanner anchorPlanner;
   private final StructureStartRelocator relocator;
   private final VillageLayoutTrimmer trimmer;
   private final VillagePieceGroundingPlanner groundingPlanner;
   private final VillagePieceClassifier classifier;
   private final StructureOverlapGuard overlapGuard = new StructureOverlapGuard();
   private final PlacementCommitCoordinator commitCoordinator = new PlacementCommitCoordinator(this.overlapGuard);
   private final JigsawStartHeightOffsetResolver startHeightOffsetResolver;

   public GroundVillagePlacementEngine() {
      this.hostIndex = new IslandHostIndex();
      this.hostSelector = new StructureHostSelector();
      this.siteSelector = new IslandSiteSelector(this.overlapGuard);
      this.anchorPlanner = new StructureAnchorPlanner();
      this.relocator = new StructureStartRelocator();
      this.trimmer = new VillageLayoutTrimmer();
      this.groundingPlanner = new VillagePieceGroundingPlanner();
      this.classifier = new VillagePieceClassifier();
      this.startHeightOffsetResolver = new JigsawStartHeightOffsetResolver();
   }

   @Override
   public PlacementDecision place(PlacementRequest request) {
      if (!StructureRegistryGuard.canCommit(request, "ground_village_v2")) {
         return PlacementDecision.rejected("unregistered_structure", "unregistered_structure");
      }

      if (!request.structureStart().isValid()) {
         return PlacementDecision.rejected("ground_village_v2_rejected_invalid_start", "invalid_start");
      }

      ResolvedStructureSupportPlane supportPlane = request.supportPlane();
      if (supportPlane != null && supportPlane.rawFootprint() != null && supportPlane.effectiveFootprint() != null) {
         PreAnchorPlacementContext preAnchor = request.preAnchorPlacementContext();
         if (preAnchor != null) {
            return this.finalizePreAnchoredVillage(request, preAnchor);
         }

         StructurePlacementPolicy policy = StructurePlacementPolicies.GROUND_VILLAGE;
         StructureFootprint villageFootprint = footprint(request.structureStart().getBoundingBox());
         int originalBuildings = this.countBuildings(request.structureStart());
         int villageAnchorY = this.villageAnchorY(request.structureStart());
         int verticalStartOffset = this.startHeightOffsetResolver.resolve(request.structure());
         HostQuery query = new HostQuery(
            request.structureId(),
            StructurePlacementCategory.GROUND_VILLAGE,
            request.chunkPos(),
            request.chunkPos().getMiddleBlockX(),
            request.chunkPos().getMiddleBlockZ(),
            policy.searchRadius(),
            policy
         );
         List<IslandField.IslandPreview> previews = this.hostIndex.previewsFor(query, request.islandField(), request.settings());
         StructureHostSelector.Selection hostSelection = this.hostSelector
            .selectHosts(query, previews, villageFootprint, request.islandField(), request.settings());
         if (hostSelection.hosts().isEmpty()) {
            return PlacementDecision.rejected(
               "ground_village_v2_rejected_no_host",
               "previews="
                  + hostSelection.previewCount()
                  + ", requiredRadius="
                  + hostSelection.requiredRadius()
                  + ", hostRejections="
                  + hostSelection.rejections()
            );
         }

         int siteAttempts = 0;

         for (HostIsland host : hostSelection.hosts()) {
            IslandSiteSelector.Selection siteSelection = this.siteSelector
               .selectSite(
                  query,
                  host,
                  policy,
                  StructurePlacementCategory.GROUND_VILLAGE,
                  villageFootprint,
                  villageFootprint,
                  request.islandField(),
                  request.settings(),
                  request.levelSeed()
               );
            siteAttempts += siteSelection.attempts();
            if (siteSelection.successful()) {
               PlannedStructurePlacement plan = this.anchorPlanner
                  .plan(siteSelection.site(), policy, villageFootprint, villageFootprint, villageFootprint, villageAnchorY, verticalStartOffset);
               BlockPos vanillaPos = centerOf(request.structureStart().getBoundingBox());
               ChunkPos relocatedAnchorChunk = new ChunkPos(plan.finalRawFootprint().centerX() >> 4, plan.finalRawFootprint().centerZ() >> 4);
               StructureStart relocated = this.relocator
                  .relocateByOffsetsToChunk(request.structureStart(), relocatedAnchorChunk, plan.dx(), plan.dy(), plan.dz());
               VillagePieceGroundingPlanner.GroundingResult preTrimGrounding = this.groundingPlanner
                  .normalize(relocated, relocatedAnchorChunk, request.islandField(), request.settings());
               if (!preTrimGrounding.accepted()) {
                  return PlacementDecision.rejected(
                     preTrimGrounding.stage(),
                     "phase=pre_trim, relocatedAnchorChunk="
                        + relocatedAnchorChunk
                        + ", retainedPieces="
                        + preTrimGrounding.retainedPieces()
                        + ", droppedPieces="
                        + preTrimGrounding.droppedPieces()
                        + ", failedCorePieces="
                        + preTrimGrounding.failedCorePieces()
                        + ", dropReasons="
                        + preTrimGrounding.dropReasons()
                  );
               }

               VillageLayoutTrimmer.TrimResult trim = this.trimmer
                  .trim(preTrimGrounding.structureStart(), relocatedAnchorChunk, host, siteSelection.site().topY());
               if (!trim.accepted()) {
                  debug(
                     "GROUND_VILLAGE trim rejected {} originChunk=[{}, {}] relocatedAnchorChunk=[{}, {}] stage={} host=({}, {}, {}; usable={}) originalPieces={} candidatePieces={} retainedPieces={} removedPieces={} envelopeDropCount={} boundsDropCount={} pieceFitDropCount={} finalBoundsDropCount={} boundsDropCounts={}",
                     request.structureId(),
                     request.chunkPos().x,
                     request.chunkPos().z,
                     relocatedAnchorChunk.x,
                     relocatedAnchorChunk.z,
                     trim.stage(),
                     host.preview().x(),
                     host.preview().y(),
                     host.preview().z(),
                     host.usableRadius(),
                     trim.originalPieces(),
                     trim.candidatePieces(),
                     trim.retainedPieces(),
                     trim.removedPieces(),
                     trim.envelopeDropCount(),
                     trim.boundsDropCount(),
                     trim.pieceFitDropCount(),
                     trim.finalBoundsDropCount(),
                     trim.boundsDropCounts()
                  );
                  return PlacementDecision.rejected(
                     trim.stage(),
                     "relocatedAnchorChunk="
                        + relocatedAnchorChunk
                        + ", originalPieces="
                        + trim.originalPieces()
                        + ", candidatePieces="
                        + trim.candidatePieces()
                        + ", retainedPieces="
                        + trim.retainedPieces()
                        + ", removedPieces="
                        + trim.removedPieces()
                        + ", envelopeDropCount="
                        + trim.envelopeDropCount()
                        + ", boundsDropCount="
                        + trim.boundsDropCount()
                        + ", pieceFitDropCount="
                        + trim.pieceFitDropCount()
                        + ", finalBoundsDropCount="
                        + trim.finalBoundsDropCount()
                        + ", boundsDropCounts="
                        + trim.boundsDropCounts()
                  );
               }

               VillagePieceGroundingPlanner.GroundingResult grounding = this.groundingPlanner
                  .normalize(trim.structureStart(), relocatedAnchorChunk, request.islandField(), request.settings());
               if (!grounding.accepted()) {
                  return PlacementDecision.rejected(
                     grounding.stage(),
                     "phase=post_trim, relocatedAnchorChunk="
                        + relocatedAnchorChunk
                        + ", retainedPieces="
                        + grounding.retainedPieces()
                        + ", droppedPieces="
                        + grounding.droppedPieces()
                        + ", failedCorePieces="
                        + grounding.failedCorePieces()
                        + ", dropReasons="
                        + grounding.dropReasons()
                  );
               }

               if (!new VillageIslandBoundsEvaluator().fits(host, grounding.finalBounds())) {
                  return PlacementDecision.rejected(
                     "ground_village_v2_rejected_bounds_after_grounding",
                     "relocatedAnchorChunk="
                        + relocatedAnchorChunk
                        + ", finalBounds="
                        + grounding.finalBounds()
                        + ", retainedPieces="
                        + grounding.retainedPieces()
                        + ", droppedPieces="
                        + grounding.droppedPieces()
                  );
               }

               ReservationContext reservationContext = new ReservationContext(request.levelSeed(), request.structureId(), relocatedAnchorChunk);
               PlacementCommitCoordinator.Decision reservationDecision = this.commitCoordinator
                  .reserveOrConflict(grounding.structureStart(), grounding.finalBounds(), StructurePlacementCategory.GROUND_VILLAGE, reservationContext);
               if (!reservationDecision.accepted()) {
                  return PlacementDecision.rejected(
                     reservationDecision.stage(),
                     "tier="
                        + this.tierAfterGrounding(grounding.structureStart(), originalBuildings)
                        + ", relocatedAnchorChunk="
                        + relocatedAnchorChunk
                        + ", originalPieces="
                        + trim.originalPieces()
                        + ", retainedPieces="
                        + grounding.retainedPieces()
                        + ", reservationDetails="
                        + reservationDecision.details()
                  );
               }

               BoundingBox bounds = grounding.structureStart().getBoundingBox();
               debug(
                  "GROUND_VILLAGE COMMIT CHECK sourceChunk=[{}, {}] startChunk=[{}, {}] relocatedAnchorChunk=[{}, {}] requestSection={} finalBounds={} chunkSpan=[{}, {}] -> [{}, {}] pieces={} retainedPieces={} droppedPieces={}",
                  request.chunkPos().x,
                  request.chunkPos().z,
                  grounding.structureStart().getChunkPos().x,
                  grounding.structureStart().getChunkPos().z,
                  relocatedAnchorChunk.x,
                  relocatedAnchorChunk.z,
                  request.sectionPos(),
                  bounds,
                  bounds.minX() >> 4,
                  bounds.minZ() >> 4,
                  bounds.maxX() >> 4,
                  bounds.maxZ() >> 4,
                  grounding.structureStart().getPieces().size(),
                  grounding.retainedPieces(),
                  grounding.droppedPieces()
               );
               request.structureManager().setStartForStructure(request.sectionPos(), request.structure(), grounding.structureStart(), request.chunk());
               RelocatedStructureReferenceRegistry.RegistrationResult references = RelocatedStructureReferenceRegistry.registerTouchedChunks(
                  request, grounding.structureStart(), grounding.finalBounds()
               );
               HostIslandKey hostKey = HostIslandKey.from(host);
               BlockPos relocatedPos = centerOf(grounding.finalBounds());
               AcceptedStructurePlacementRegistry.record(
                  new AcceptedStructurePlacement(request.structureId(), StructurePlacementCategory.GROUND_VILLAGE, relocatedAnchorChunk, relocatedPos, hostKey)
               );
               RelocatedStructureLocateIndex.recordCommittedRelocation(
                  request.structureId(), request.dimension(), relocatedAnchorChunk, vanillaPos, relocatedPos, references.anchorChunk()
               );
               VillageLayoutTrimmer.VillageTier finalTier = this.tierAfterGrounding(grounding.structureStart(), originalBuildings);
               if (finalTier == VillageLayoutTrimmer.VillageTier.REJECT) {
                  return PlacementDecision.rejected(
                     "ground_village_v2_rejected_tier_after_grounding",
                     "tier=REJECT, relocatedAnchorChunk="
                        + relocatedAnchorChunk
                        + ", originalBuildings="
                        + originalBuildings
                        + ", retainedPieces="
                        + grounding.retainedPieces()
                        + ", droppedPieces="
                        + grounding.droppedPieces()
                        + ", finalBounds="
                        + grounding.finalBounds()
                  );
               }

               String stage = grounding.snappedPieces() <= 0 && grounding.droppedPieces() <= 0 ? trim.stage() : "ground_village_v2_grounded";
               String details = "tier="
                  + finalTier
                  + ", host=("
                  + host.preview().x()
                  + ","
                  + host.preview().y()
                  + ","
                  + host.preview().z()
                  + "), sourceChunk=["
                  + request.chunkPos().x
                  + ","
                  + request.chunkPos().z
                  + "], relocatedAnchorChunk=["
                  + relocatedAnchorChunk.x
                  + ","
                  + relocatedAnchorChunk.z
                  + "], startChunk=["
                  + grounding.structureStart().getChunkPos().x
                  + ","
                  + grounding.structureStart().getChunkPos().z
                  + "], startHeightOffset="
                  + verticalStartOffset
                  + ", villageAnchorY="
                  + villageAnchorY
                  + ", originalBuildings="
                  + originalBuildings
                  + ", originalPieces="
                  + trim.originalPieces()
                  + ", candidatePieces="
                  + trim.candidatePieces()
                  + ", retainedPieces="
                  + grounding.retainedPieces()
                  + ", removedPieces="
                  + trim.removedPieces()
                  + ", initialPieces="
                  + trim.originalPieces()
                  + ", preTrimGroundingDropCount="
                  + preTrimGrounding.droppedPieces()
                  + ", envelopeDropCount="
                  + trim.envelopeDropCount()
                  + ", boundsDropCount="
                  + trim.boundsDropCount()
                  + ", droppedForPieceFit="
                  + trim.pieceFitDropCount()
                  + ", droppedForFinalBounds="
                  + trim.finalBoundsDropCount()
                  + ", groundingDropCount="
                  + grounding.droppedPieces()
                  + ", droppedAfterGrounding="
                  + grounding.droppedPieces()
                  + ", snappedPieces="
                  + grounding.snappedPieces()
                  + ", maxUpShift="
                  + grounding.maxUpShift()
                  + ", maxDownShift="
                  + grounding.maxDownShift()
                  + ", farmDrops="
                  + grounding.farmDrops()
                  + ", failedCorePieces="
                  + grounding.failedCorePieces()
                  + ", boundsDropCounts="
                  + trim.boundsDropCounts()
                  + ", preTrimDropReasons="
                  + preTrimGrounding.dropReasons()
                  + ", dropReasons="
                  + grounding.dropReasons()
                  + ", finalBounds="
                  + grounding.finalBounds()
                  + ", hostUsableRadius="
                  + host.usableRadius()
                  + ", touchedChunks="
                  + references.touchedChunks().size()
                  + ", touchedSpan="
                  + references.span();
               debug(
                  "GROUND_VILLAGE accepted {} originChunk=[{}, {}] stage={} {}",
                  request.structureId(),
                  request.chunkPos().x,
                  request.chunkPos().z,
                  stage,
                  details
               );
               broadcastAcceptedVillageDebug(request.structureId(), grounding.finalBounds(), finalTier);
               return PlacementDecision.accepted(stage, details, grounding.structureStart());
            }

            debug(
               "GROUND_VILLAGE site rejected {} originChunk=[{}, {}] host=({}, {}, {}; radius={}; usable={}) attempts={} rejections={}",
               request.structureId(),
               request.chunkPos().x,
               request.chunkPos().z,
               host.preview().x(),
               host.preview().y(),
               host.preview().z(),
               host.preview().radius(),
               host.usableRadius(),
               siteSelection.attempts(),
               siteSelection.rejections()
            );
         }

         return PlacementDecision.rejected(
            "ground_village_v2_rejected_no_site_or_commit",
            "previews=" + previews.size() + ", hosts=" + hostSelection.hosts().size() + ", siteAttempts=" + siteAttempts
         );
      } else {
         return PlacementDecision.rejected("ground_village_v2_rejected_missing_support_plane", "missing_support_plane");
      }
   }

   private int countBuildings(StructureStart structureStart) {
      return (int)structureStart.getPieces().stream().filter(piece -> this.classifier.classify(piece) == VillagePieceClassifier.PieceKind.BUILDING).count();
   }

   private int villageAnchorY(StructureStart structureStart) {
      return structureStart.getPieces()
         .stream()
         .filter(piece -> this.classifier.classify(piece) == VillagePieceClassifier.PieceKind.CENTER)
         .map(piece -> piece.getBoundingBox().minY())
         .findFirst()
         .orElse(structureStart.getBoundingBox().minY());
   }

   private static StructureFootprint footprint(BoundingBox bounds) {
      return new StructureFootprint(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
   }

   private static BlockPos centerOf(BoundingBox bounds) {
      return new BlockPos(
         (int)Math.floor((bounds.minX() + bounds.maxX()) * 0.5),
         (int)Math.floor((bounds.minY() + bounds.maxY()) * 0.5),
         (int)Math.floor((bounds.minZ() + bounds.maxZ()) * 0.5)
      );
   }

   private VillageLayoutTrimmer.VillageTier tierAfterGrounding(StructureStart structureStart, int originalBuildings) {
      long retainedBuildings = structureStart.getPieces()
         .stream()
         .filter(piece -> this.classifier.classify(piece) == VillagePieceClassifier.PieceKind.BUILDING)
         .count();
      boolean hasCore = structureStart.getPieces()
         .stream()
         .map(this.classifier::classify)
         .anyMatch(kind -> kind == VillagePieceClassifier.PieceKind.CENTER || kind == VillagePieceClassifier.PieceKind.ROAD);
      int retainedPieces = structureStart.getPieces().size();
      if ((hasCore || retainedBuildings > 0L) && retainedPieces != 0) {
         double buildingRetention = originalBuildings <= 0 ? 0.0 : (double)retainedBuildings / originalBuildings;
         if (hasCore && buildingRetention >= 0.9) {
            return VillageLayoutTrimmer.VillageTier.FULL;
         } else {
            return hasCore && buildingRetention >= 0.35 ? VillageLayoutTrimmer.VillageTier.HAMLET : VillageLayoutTrimmer.VillageTier.REJECT;
         }
      } else {
         return VillageLayoutTrimmer.VillageTier.REJECT;
      }
   }

   private PlacementDecision finalizePreAnchoredVillage(PlacementRequest request, PreAnchorPlacementContext context) {
      if (!request.structureStart().isValid()) {
         return PlacementDecision.rejected("ground_village_v2_rejected_invalid_start", "phase=pre_anchor_finalize, invalid_start");
      }

      int originalBuildings = this.countBuildings(request.structureStart());
      BlockPos vanillaPos = centerOf(request.structureStart().getBoundingBox());
      ChunkPos anchorChunk = context.winningAnchorChunk();
      VillagePieceGroundingPlanner.GroundingResult grounding = this.groundingPlanner
         .normalize(request.structureStart(), anchorChunk, request.islandField(), request.settings());
      if (!grounding.accepted()) {
         return PlacementDecision.rejected(
            grounding.stage(),
            "phase=pre_anchor_finalize, attemptsUsed="
               + context.attemptsUsed()
               + ", anchorChunk="
               + anchorChunk
               + ", retainedPieces="
               + grounding.retainedPieces()
               + ", droppedPieces="
               + grounding.droppedPieces()
               + ", failedCorePieces="
               + grounding.failedCorePieces()
               + ", dropReasons="
               + grounding.dropReasons()
         );
      }

      if (!new VillageIslandBoundsEvaluator().fits(context.host(), grounding.finalBounds())) {
         return PlacementDecision.rejected(
            "ground_village_v2_rejected_bounds_after_grounding",
            "phase=pre_anchor_finalize, attemptsUsed="
               + context.attemptsUsed()
               + ", anchorChunk="
               + anchorChunk
               + ", finalBounds="
               + grounding.finalBounds()
               + ", hostUsableRadius="
               + context.host().usableRadius()
         );
      }

      ReservationContext reservationContext = new ReservationContext(request.levelSeed(), request.structureId(), anchorChunk);
      PlacementCommitCoordinator.Decision reservationDecision = this.commitCoordinator
         .reserveOrConflict(grounding.structureStart(), grounding.finalBounds(), StructurePlacementCategory.GROUND_VILLAGE, reservationContext);
      if (!reservationDecision.accepted()) {
         return PlacementDecision.rejected(
            reservationDecision.stage(),
            "phase=pre_anchor_finalize, tier="
               + this.tierAfterGrounding(grounding.structureStart(), originalBuildings)
               + ", anchorChunk="
               + anchorChunk
               + ", retainedPieces="
               + grounding.retainedPieces()
               + ", reservationDetails="
               + reservationDecision.details()
         );
      }

      request.structureManager().setStartForStructure(request.sectionPos(), request.structure(), grounding.structureStart(), request.chunk());
      RelocatedStructureReferenceRegistry.RegistrationResult references = RelocatedStructureReferenceRegistry.registerTouchedChunks(
         request, grounding.structureStart(), grounding.finalBounds()
      );
      HostIslandKey hostKey = HostIslandKey.from(context.host());
      BlockPos relocatedPos = centerOf(grounding.finalBounds());
      AcceptedStructurePlacementRegistry.record(
         new AcceptedStructurePlacement(request.structureId(), StructurePlacementCategory.GROUND_VILLAGE, anchorChunk, relocatedPos, hostKey)
      );
      RelocatedStructureLocateIndex.recordCommittedRelocation(
         request.structureId(), request.dimension(), anchorChunk, vanillaPos, relocatedPos, references.anchorChunk()
      );
      VillageLayoutTrimmer.VillageTier finalTier = this.tierAfterGrounding(grounding.structureStart(), originalBuildings);
      if (finalTier == VillageLayoutTrimmer.VillageTier.REJECT) {
         return PlacementDecision.rejected(
            "ground_village_v2_rejected_tier_after_grounding",
            "phase=pre_anchor_finalize, tier=REJECT, anchorChunk="
               + anchorChunk
               + ", attemptsUsed="
               + context.attemptsUsed()
               + ", retainedPieces="
               + grounding.retainedPieces()
         );
      }

      String details = "tier="
         + finalTier
         + ", host=("
         + context.host().preview().x()
         + ","
         + context.host().preview().y()
         + ","
         + context.host().preview().z()
         + "), sourceChunk=["
         + context.sourceChunk().x
         + ","
         + context.sourceChunk().z
         + "], winningAnchorChunk=["
         + anchorChunk.x
         + ","
         + anchorChunk.z
         + "], attemptsUsed="
         + context.attemptsUsed()
         + ", attemptedAnchorChunks="
         + context.attemptedAnchorChunks()
         + ", originalPieces="
         + request.structureStart().getPieces().size()
         + ", retainedPieces="
         + grounding.retainedPieces()
         + ", droppedPieces="
         + grounding.droppedPieces()
         + ", snappedPieces="
         + grounding.snappedPieces()
         + ", dropReasons="
         + grounding.dropReasons()
         + ", finalBounds="
         + grounding.finalBounds()
         + ", hostUsableRadius="
         + context.host().usableRadius()
         + ", touchedChunks="
         + references.touchedChunks().size()
         + ", touchedSpan="
         + references.span();
      debug(
         "GROUND_VILLAGE pre-anchor accepted {} originChunk=[{}, {}] stage={} {}",
         request.structureId(),
         context.sourceChunk().x,
         context.sourceChunk().z,
         "ground_village_v2_pre_anchored",
         details
      );
      broadcastAcceptedVillageDebug(request.structureId(), grounding.finalBounds(), finalTier);
      return PlacementDecision.accepted("ground_village_v2_pre_anchored", details, grounding.structureStart());
   }

   private static void debug(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }

   private static void broadcastAcceptedVillageDebug(ResourceLocation structureId, BoundingBox bounds, VillageLayoutTrimmer.VillageTier tier) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null && SkyIslandServerConfig.structureDebugEnabled()) {
         BlockPos center = centerOf(bounds);
         server.execute(
            () -> server.getPlayerList()
               .broadcastSystemMessage(
                  Component.literal(
                     "[Sky Archipelago] GROUND_VILLAGE spawned: "
                        + structureId
                        + " tier="
                        + tier
                        + " @ "
                        + center.getX()
                        + ", "
                        + center.getY()
                        + ", "
                        + center.getZ()
                  ),
                  false
               )
         );
      }
   }
}
