package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.ground;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.AcceptedStructurePlacement;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIslandKey;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostQuery;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.PlannedStructurePlacement;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.AnchorBoundsPolicy;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.AnchorBoundsResolver;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.FcfsChunkWindowTelemetry;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.IslandHostIndex;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.IslandSiteSelector;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.LegacyStartBoundsResolver;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementCommitCoordinator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.ReservationContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.RootPieceAnchorBoundsResolver;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureAnchorPlanner;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureCommitter;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureDiversityGate;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureHostSelector;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureOverlapGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureStartRelocator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.policy.StructurePlacementPolicies;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.AcceptedStructurePlacementRegistry;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureReferenceRegistry;
import org.sathrek.sky_archipelago.worldgen.structure.JigsawStartHeightOffsetResolver;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.sky.StructureGroundingFootprintResolver;

public final class SmallGroundStructurePlacementEngine {
   private static final ResourceKey<Level> DEFAULT_DIMENSION = Level.OVERWORLD;
   private static final Map<RelocatedStructureReferenceRegistry.MaterializationKey, SmallGroundStructurePlacementEngine.PendingSpawnAnnouncement> PENDING_SPAWN_ANNOUNCEMENTS = new ConcurrentHashMap<>();
   private final IslandHostIndex hostIndex;
   private final StructureHostSelector hostSelector;
   private final IslandSiteSelector siteSelector;
   private final StructureAnchorPlanner anchorPlanner;
   private final StructureCommitter committer;
   private final StructureGroundingFootprintResolver groundingFootprintResolver;
   private final StructureDiversityGate diversityGate;
   private final JigsawStartHeightOffsetResolver startHeightOffsetResolver;
   private final AnchorBoundsResolver legacyBoundsResolver;
   private final AnchorBoundsResolver rootPieceBoundsResolver;
   private final AnchorBoundsPolicy anchorBoundsPolicy;
   private static final int FCFS_OVERLAP_RETRY_CAP_PER_ORIGIN = 10;

   public SmallGroundStructurePlacementEngine() {
      StructureOverlapGuard overlapGuard = new StructureOverlapGuard();
      this.hostIndex = new IslandHostIndex();
      this.hostSelector = new StructureHostSelector();
      this.siteSelector = new IslandSiteSelector(overlapGuard);
      this.anchorPlanner = new StructureAnchorPlanner();
      this.committer = new StructureCommitter(overlapGuard, new StructureStartRelocator());
      this.groundingFootprintResolver = new StructureGroundingFootprintResolver();
      this.diversityGate = new StructureDiversityGate();
      this.startHeightOffsetResolver = new JigsawStartHeightOffsetResolver();
      this.legacyBoundsResolver = new LegacyStartBoundsResolver();
      this.rootPieceBoundsResolver = new RootPieceAnchorBoundsResolver(this.legacyBoundsResolver);
      this.anchorBoundsPolicy = AnchorBoundsPolicy.rootPieceTrialPolicy();
   }

   public boolean place(
      Structure structure,
      ResourceLocation structureId,
      StructurePlacementCategory category,
      StructureStart structureStart,
      ResolvedStructureSupportPlane supportPlane,
      StructureManager structureManager,
      SectionPos sectionPos,
      ChunkAccess chunk,
      ChunkPos chunkPos,
      SkyIslandSettings settings,
      IslandField islandField,
      long levelSeed,
      RegistryAccess registryAccess
   ) {
      flushPendingSpawnAnnouncements();
      StructurePlacementPolicy policy = StructurePlacementPolicies.forCategory(category);
      if (policy == null) {
         return false;
      }

      if (!structureStart.isValid()) {
         debug("V2 rejected invalid start for {} category={} originChunk=[{}, {}]", structureId, category, chunkPos.x, chunkPos.z);
         return false;
      }

      if (supportPlane == null) {
         debug("V2 rejected {} category={} originChunk=[{}, {}]: missing_support_plane", structureId, category, chunkPos.x, chunkPos.z);
         return false;
      }

      HostQuery query = new HostQuery(structureId, category, chunkPos, chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(), policy.searchRadius(), policy);
      StructureDiversityGate.OriginResult originDiversity = this.diversityGate.evaluateOrigin(structureId, chunkPos, policy);
      if (!originDiversity.accepted()) {
         debug(
            "V2 rejected {} category={} originChunk=[{}, {}]: {} spacingBlocked={} spacingChunks={} policy={}",
            structureId,
            category,
            chunkPos.x,
            chunkPos.z,
            originDiversity.reason(),
            originDiversity.spacingBlocked(),
            originDiversity.spacingChunks(),
            policy
         );
         return false;
      }

      StructureFootprint groundingFootprint = this.groundingFootprintResolver.resolve(structureId, supportPlane.rawFootprint(), structureStart);
      int verticalStartOffset = this.startHeightOffsetResolver.resolve(structure);
      List<IslandField.IslandPreview> previews = this.hostIndex.previewsFor(query, islandField, settings);
      StructureHostSelector.Selection hostSelection = this.hostSelector.selectHosts(query, previews, supportPlane.effectiveFootprint(), islandField, settings);
      if (hostSelection.hosts().isEmpty()) {
         debug(
            "V2 rejected {} category={} originChunk=[{}, {}]: no_host policy={} previews={} requiredRadius={} hostRejections={}",
            structureId,
            category,
            chunkPos.x,
            chunkPos.z,
            policy,
            hostSelection.previewCount(),
            hostSelection.requiredRadius(),
            hostSelection.rejections()
         );
         return false;
      }

      int siteAttempts = 0;
      int fcfsOverlapRejects = 0;

      for (HostIsland host : hostSelection.hosts()) {
         HostIslandKey hostIslandKey = HostIslandKey.from(host);
         StructureDiversityGate.HostResult hostDiversity = this.diversityGate.evaluateHost(structureId, category, hostIslandKey, policy);
         if (!hostDiversity.accepted()) {
            debug(
               "V2 host skipped {} category={} originChunk=[{}, {}] host=({}, {}, {}; family={}; radius={}): {} totalOnHost={}/{} sameOnHost={}/{}",
               structureId,
               category,
               chunkPos.x,
               chunkPos.z,
               host.preview().x(),
               host.preview().y(),
               host.preview().z(),
               host.preview().family(),
               host.preview().radius(),
               hostDiversity.reason(),
               hostDiversity.totalOnHost(),
               hostDiversity.maxPerHost(),
               hostDiversity.sameOnHost(),
               hostDiversity.maxSamePerHost()
            );
         } else {
            IslandSiteSelector.Selection siteSelection = this.siteSelector
               .selectSite(query, host, policy, category, groundingFootprint, supportPlane.effectiveFootprint(), islandField, settings, levelSeed);
            siteAttempts += siteSelection.attempts();
            if (!siteSelection.successful()) {
               debug(
                  "V2 site rejected {} category={} host=({}, {}, {}; radius={}; usable={}) attempts={} siteRejections={}",
                  structureId,
                  category,
                  host.preview().x(),
                  host.preview().y(),
                  host.preview().z(),
                  host.preview().radius(),
                  host.usableRadius(),
                  siteSelection.attempts(),
                  siteSelection.rejections()
               );
            } else {
               PlannedStructurePlacement plan = this.anchorPlanner
                  .plan(
                     siteSelection.site(),
                     policy,
                     supportPlane.rawFootprint(),
                     supportPlane.effectiveFootprint(),
                     groundingFootprint,
                     this.planningBaseY(category, structure, structureStart, supportPlane.baseY()),
                     verticalStartOffset
                  );
               ReservationContext reservationContext = new ReservationContext(levelSeed, structureId, chunkPos);
               long startCommitNanos = System.nanoTime();
               StructureCommitter.CommitResult commit = this.committer
                  .commit(
                     structureStart,
                     structure,
                     category,
                     plan,
                     structureManager,
                     sectionPos,
                     chunk,
                     chunkPos,
                     reservationContext,
                     Level.OVERWORLD,
                     registryAccess
                  );
               long arbitrationNanos = System.nanoTime() - startCommitNanos;
               BoundingBox finalBounds = commit.accepted() ? commit.structureStart().getBoundingBox() : null;
               RelocatedStructureReferenceRegistry.RegistrationResult referenceResult = commit.references();
               boolean fcfsRejected = "fcfs_3d_overlap".equals(commit.reason());
               if (fcfsRejected) {
                  fcfsOverlapRejects++;
               }

               PlacementCommitCoordinator.Decision reservationDecision = commit.reservationDecision();
               FcfsChunkWindowTelemetry.record(
                  chunkPos.x,
                  chunkPos.z,
                  commit.accepted(),
                  fcfsRejected,
                  fcfsRejected,
                  arbitrationNanos,
                  reservationDecision != null ? reservationDecision.prefilterCandidates() : 0,
                  reservationDecision != null ? reservationDecision.authoritativeChecks() : 0,
                  reservationDecision != null && reservationDecision.fallbackToAuthoritative()
               );
               debug(
                  "V2 placement result {} category={} originChunk=[{}, {}] policy={} startHeightOffset={} previews={} hostRejections={} chosenHost=({}, {}, {}; family={}; radius={}; stable={}; usable={}) site=({}, {}, {}; local={},{}; grounded={}) siteAttempts={} delta=({}, {}, {}) reservation={} reservationDetails={} finalFootprint={} finalBounds={} touchedChunks={} touchedSpan={} anchorChunk={} referencesApplied={} referencesQueued={}",
                  structureId,
                  category,
                  chunkPos.x,
                  chunkPos.z,
                  policy,
                  verticalStartOffset,
                  previews.size(),
                  hostSelection.rejections(),
                  host.preview().x(),
                  host.preview().y(),
                  host.preview().z(),
                  host.preview().family(),
                  host.preview().radius(),
                  host.stableTopCells(),
                  host.usableRadius(),
                  siteSelection.site().x(),
                  siteSelection.site().y(),
                  siteSelection.site().z(),
                  siteSelection.site().localOffsetX(),
                  siteSelection.site().localOffsetZ(),
                  siteSelection.site().groundedRatio(),
                  siteAttempts,
                  plan.dx(),
                  plan.dy(),
                  plan.dz(),
                  commit.reason(),
                  commit.details(),
                  plan.finalEffectiveFootprint(),
                  finalBounds,
                  referenceResult != null ? referenceResult.touchedChunks().size() : 0,
                  referenceResult != null ? referenceResult.span() : "none",
                  referenceResult != null ? referenceResult.anchorChunk() : "none",
                  referenceResult != null ? referenceResult.appliedNow() : 0,
                  referenceResult != null ? referenceResult.queued() : 0
               );
               if (commit.accepted()) {
                  BlockPos finalCenter = centerOf(commit.structureStart().getBoundingBox());
                  AcceptedStructurePlacementRegistry.record(new AcceptedStructurePlacement(structureId, category, chunkPos, finalCenter, hostIslandKey));
                  debug(
                     "V2 accepted placement recorded {} category={} originChunk=[{}, {}] hostKey={} finalCenter={}",
                     structureId,
                     category,
                     chunkPos.x,
                     chunkPos.z,
                     hostIslandKey,
                     finalCenter
                  );
                  RelocatedStructureReferenceRegistry.MaterializationKey key = new RelocatedStructureReferenceRegistry.MaterializationKey(
                     structureId, chunkPos, DEFAULT_DIMENSION
                  );
                  if (RelocatedStructureReferenceRegistry.isMaterialized(key)) {
                     debug(
                        "materialization_ready id={} startChunk=[{}, {}] anchorChunk={} progress={}",
                        structureId,
                        chunkPos.x,
                        chunkPos.z,
                        referenceResult != null ? referenceResult.anchorChunk() : "none",
                        RelocatedStructureReferenceRegistry.materializationProgress(key)
                     );
                     broadcastAcceptedStructureDebug(structureId, finalCenter);
                  } else {
                     PENDING_SPAWN_ANNOUNCEMENTS.put(key, new SmallGroundStructurePlacementEngine.PendingSpawnAnnouncement(structureId, finalCenter));
                     debug(
                        "materialization_queued id={} startChunk=[{}, {}] touched={} span={} appliedNow={} queued={} progress={}",
                        structureId,
                        chunkPos.x,
                        chunkPos.z,
                        referenceResult != null ? referenceResult.touchedChunks().size() : 0,
                        referenceResult != null ? referenceResult.span() : "none",
                        referenceResult != null ? referenceResult.appliedNow() : 0,
                        referenceResult != null ? referenceResult.queued() : 0,
                        RelocatedStructureReferenceRegistry.materializationProgress(key)
                     );
                  }

                  return true;
               }

               if (fcfsOverlapRejects >= 10) {
                  debug(
                     "V2 FCFS retry cap reached {} category={} originChunk=[{}, {}] overlapRejects={} siteAttempts={}",
                     structureId,
                     category,
                     chunkPos.x,
                     chunkPos.z,
                     fcfsOverlapRejects,
                     siteAttempts
                  );
                  break;
               }
            }
         }
      }

      debug(
         "V2 rejected {} category={} originChunk=[{}, {}]: no_site_or_commit policy={} previews={} hosts={} siteAttempts={}",
         structureId,
         category,
         chunkPos.x,
         chunkPos.z,
         policy,
         previews.size(),
         hostSelection.hosts().size(),
         siteAttempts
      );
      return false;
   }

   private static void debug(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }

   private int planningBaseY(StructurePlacementCategory category, Structure structure, StructureStart structureStart, int supportPlaneBaseY) {
      if (structureStart != null && structureStart.isValid()) {
         BoundingBox startBounds = structureStart.getBoundingBox();
         if (startBounds == null) {
            return supportPlaneBaseY;
         }

         AnchorBoundsResolver resolver = this.legacyBoundsResolver;
         if (this.anchorBoundsPolicy.useRootPieceBounds(category, structure)) {
            resolver = this.rootPieceBoundsResolver;
         }

         BoundingBox anchorBounds = resolver.resolve(structureStart);
         if (anchorBounds == null) {
            return supportPlaneBaseY;
         }

         int delta = anchorBounds.minY() - startBounds.minY();
         return supportPlaneBaseY + delta;
      } else {
         return supportPlaneBaseY;
      }
   }

   private static BlockPos centerOf(BoundingBox bounds) {
      return new BlockPos(
         (int)Math.floor((bounds.minX() + bounds.maxX()) * 0.5),
         (int)Math.floor((bounds.minY() + bounds.maxY()) * 0.5),
         (int)Math.floor((bounds.minZ() + bounds.maxZ()) * 0.5)
      );
   }

   public static void flushPendingSpawnAnnouncements() {
      if (!PENDING_SPAWN_ANNOUNCEMENTS.isEmpty()) {
         for (Entry<RelocatedStructureReferenceRegistry.MaterializationKey, SmallGroundStructurePlacementEngine.PendingSpawnAnnouncement> entry : List.copyOf(
            PENDING_SPAWN_ANNOUNCEMENTS.entrySet()
         )) {
            if (RelocatedStructureReferenceRegistry.isMaterialized(entry.getKey())) {
               SmallGroundStructurePlacementEngine.PendingSpawnAnnouncement announcement = entry.getValue();
               debug(
                  "materialization_ready id={} startChunk=[{}, {}] progress={}",
                  announcement.structureId(),
                  entry.getKey().startChunk().x,
                  entry.getKey().startChunk().z,
                  RelocatedStructureReferenceRegistry.materializationProgress(entry.getKey())
               );
               broadcastAcceptedStructureDebug(announcement.structureId(), announcement.spawnedAt());
               PENDING_SPAWN_ANNOUNCEMENTS.remove(entry.getKey());
            }
         }
      }
   }

   private static void broadcastAcceptedStructureDebug(ResourceLocation structureId, BlockPos spawnedAt) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null && SkyIslandServerConfig.structureDebugEnabled()) {
         server.execute(
            () -> server.getPlayerList()
               .broadcastSystemMessage(
                  Component.literal(
                     "[Sky Archipelago] V2 POI spawned: " + structureId + " @ " + spawnedAt.getX() + ", " + spawnedAt.getY() + ", " + spawnedAt.getZ()
                  ),
                  false
               )
         );
      }
   }

   private record PendingSpawnAnnouncement(ResourceLocation structureId, BlockPos spawnedAt) {
   }
}
