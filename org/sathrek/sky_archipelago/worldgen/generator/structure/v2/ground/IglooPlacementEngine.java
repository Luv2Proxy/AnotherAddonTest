package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.ground;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementDecision;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostQuery;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.AnchoredStartFinalizer;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.IslandHostIndex;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.IslandSiteSelector;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureHostSelector;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureOverlapGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.policy.StructurePlacementPolicies;
import org.sathrek.sky_archipelago.worldgen.structure.JigsawStartHeightOffsetResolver;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class IglooPlacementEngine {
   public static final ResourceLocation STRUCTURE_ID = ResourceLocation.withDefaultNamespace("igloo");
   private final IslandHostIndex hostIndex = new IslandHostIndex();
   private final StructureHostSelector hostSelector = new StructureHostSelector();
   private final IslandSiteSelector siteSelector = new IslandSiteSelector(new StructureOverlapGuard());
   private final JigsawStartHeightOffsetResolver startHeightOffsetResolver = new JigsawStartHeightOffsetResolver();
   private final AnchoredStartFinalizer finalizer = new AnchoredStartFinalizer();

   public PlacementDecision place(PlacementRequest request, StructurePlacementCategory category) {
      PlacementRequest.StructureGenerationContext generation = request.generationContext();
      return generation == null
         ? PlacementDecision.rejected("igloo_anchor_first_rejected", "missing_generation_context")
         : this.place(request, category, generation.references(), generation);
   }

   PlacementDecision place(
      PlacementRequest request, StructurePlacementCategory category, int references, PlacementRequest.StructureGenerationContext generationContext
   ) {
      if (!supports(request.structureId())) {
         return PlacementDecision.rejected("igloo_anchor_first_rejected", "unsupported_structure");
      }

      if (!StructureRegistryGuard.canCommit(request.structureId(), request.structure(), "igloo_anchor_first")) {
         return PlacementDecision.rejected("unregistered_structure", "unregistered_structure");
      }

      if (!request.structureStart().isValid()) {
         return PlacementDecision.rejected("igloo_anchor_first_rejected", "invalid_start");
      }

      if (request.supportPlane() != null && request.supportPlane().rawFootprint() != null && request.supportPlane().effectiveFootprint() != null) {
         StructurePlacementCategory routingCategory = category == StructurePlacementCategory.DEFAULT ? StructurePlacementCategory.SURFACE_SKY : category;
         StructurePlacementPolicy policy = StructurePlacementPolicies.forCategory(routingCategory);
         if (policy == null) {
            return PlacementDecision.rejected("igloo_anchor_first_rejected", "unsupported_category:" + category);
         }

         HostQuery query = new HostQuery(
            request.structureId(),
            routingCategory,
            request.chunkPos(),
            request.chunkPos().getMiddleBlockX(),
            request.chunkPos().getMiddleBlockZ(),
            policy.searchRadius(),
            policy
         );
         List<IslandField.IslandPreview> previews = this.hostIndex.previewsFor(query, request.islandField(), request.settings());
         StructureHostSelector.Selection hostSelection = this.hostSelector
            .selectHosts(query, previews, request.supportPlane().effectiveFootprint(), request.islandField(), request.settings());
         if (hostSelection.hosts().isEmpty()) {
            return PlacementDecision.rejected(
               "igloo_anchor_first_rejected_no_host",
               "previews="
                  + hostSelection.previewCount()
                  + ",requiredRadius="
                  + hostSelection.requiredRadius()
                  + ",hostRejections="
                  + hostSelection.rejections()
            );
         }

         int attempts = 0;

         for (HostIsland host : hostSelection.hosts()) {
            IslandSiteSelector.Selection siteSelection = this.siteSelector
               .selectSite(
                  query,
                  host,
                  policy,
                  routingCategory,
                  request.supportPlane().rawFootprint(),
                  request.supportPlane().effectiveFootprint(),
                  request.islandField(),
                  request.settings(),
                  request.levelSeed()
               );
            attempts += siteSelection.attempts();
            if (siteSelection.successful()) {
               ChunkPos anchorChunk = new ChunkPos(siteSelection.site().x() >> 4, siteSelection.site().z() >> 4);
               StructureStart anchoredStart = request.structure()
                  .generate(
                     generationContext.registryAccess(),
                     generationContext.generator(),
                     generationContext.biomeSource(),
                     generationContext.randomState(),
                     generationContext.templateManager(),
                     request.levelSeed(),
                     anchorChunk,
                     references,
                     request.chunk(),
                     request.structure().biomes()::contains
                  );
               if (anchoredStart.isValid()) {
                  BoundingBox bounds = anchoredStart.getBoundingBox();
                  if (bounds != null && !anchoredStart.getPieces().isEmpty()) {
                     int expectedTopY = siteSelection.site().topY() + policy.topOffset() + this.startHeightOffsetResolver.resolve(request.structure());
                     debug(
                        "IGLOO anchor selected {} sourceChunk=[{}, {}] anchorChunk=[{}, {}] host=({}, {}, {}; usable={}) attemptsUsed={} expectedTopY={} actualMinY={}",
                        request.structureId(),
                        request.chunkPos().x,
                        request.chunkPos().z,
                        anchorChunk.x,
                        anchorChunk.z,
                        host.preview().x(),
                        host.preview().y(),
                        host.preview().z(),
                        host.usableRadius(),
                        attempts,
                        expectedTopY,
                        bounds.minY()
                     );
                     AnchoredStartFinalizer.FinalizationResult finalization = this.finalizer
                        .finalizeAnchoredStart(
                           request.structureId(),
                           request.structure(),
                           request.structureManager(),
                           request.sectionPos(),
                           request.chunk(),
                           anchoredStart,
                           bounds,
                           request.chunkPos(),
                           anchorChunk,
                           request.dimension(),
                           generationContext.registryAccess(),
                           centerOf(request.structureStart().getBoundingBox()),
                           "igloo_anchor_first"
                        );
                     if (!finalization.accepted()) {
                        return PlacementDecision.rejected("igloo_anchor_first_rejected", "rejected_igloo_finalize_failed:" + finalization.stage());
                     }

                     return PlacementDecision.accepted(
                        "igloo_anchor_first_accepted",
                        "sourceChunk=["
                           + request.chunkPos().x
                           + ","
                           + request.chunkPos().z
                           + "],winningAnchorChunk=["
                           + anchorChunk.x
                           + ","
                           + anchorChunk.z
                           + "],host=("
                           + host.preview().x()
                           + ","
                           + host.preview().y()
                           + ","
                           + host.preview().z()
                           + "),attemptsUsed="
                           + attempts
                           + ",expectedTopY="
                           + expectedTopY
                           + ",actualMinY="
                           + bounds.minY()
                           + ",pieceCount="
                           + anchoredStart.getPieces().size(),
                        anchoredStart
                     );
                  }
               }
            }
         }

         return PlacementDecision.rejected(
            "igloo_anchor_first_rejected_no_site_or_start",
            "previews=" + previews.size() + ",hosts=" + hostSelection.hosts().size() + ",siteAttempts=" + attempts
         );
      } else {
         return PlacementDecision.rejected("igloo_anchor_first_rejected", "missing_support_plane");
      }
   }

   public static boolean supports(ResourceLocation structureId) {
      return STRUCTURE_ID.equals(structureId);
   }

   private static BlockPos centerOf(BoundingBox bounds) {
      return bounds == null
         ? BlockPos.ZERO
         : new BlockPos(
            (int)Math.floor((bounds.minX() + bounds.maxX()) * 0.5),
            (int)Math.floor((bounds.minY() + bounds.maxY()) * 0.5),
            (int)Math.floor((bounds.minZ() + bounds.maxZ()) * 0.5)
         );
   }

   private static void debug(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }
}
