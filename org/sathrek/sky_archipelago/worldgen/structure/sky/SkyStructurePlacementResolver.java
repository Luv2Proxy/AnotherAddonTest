package org.sathrek.sky_archipelago.worldgen.structure.sky;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportPlaneResolver;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandRefinementResult;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementCandidate;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementFailureDiagnostics;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementResult;

public final class SkyStructurePlacementResolver {
   private final StructureSupportPlaneResolver supportPlaneResolver;
   private final SkyIslandPlacementService skyIslandPlacementService;
   private final LandPlacementRefinementService landPlacementRefinementService;
   private final StructureGroundingFootprintResolver groundingFootprintResolver;

   public SkyStructurePlacementResolver(StructureSupportPlaneResolver supportPlaneResolver) {
      this(
         supportPlaneResolver,
         new SkyIslandPlacementService(new SkyCandidateEvaluator(new LocalOffsetSampler(), new SkyCandidateOrdering()), new SkyCandidateOrdering()),
         new LandPlacementRefinementService(new LandSupportMaskFactory(), new LandCandidateEvaluator(), new LocalOffsetSampler(), new LandRejectionClassifier()),
         new StructureGroundingFootprintResolver()
      );
   }

   public SkyStructurePlacementResolver(
      StructureSupportPlaneResolver supportPlaneResolver,
      SkyIslandPlacementService skyIslandPlacementService,
      LandPlacementRefinementService landPlacementRefinementService,
      StructureGroundingFootprintResolver groundingFootprintResolver
   ) {
      this.supportPlaneResolver = supportPlaneResolver;
      this.skyIslandPlacementService = skyIslandPlacementService;
      this.landPlacementRefinementService = landPlacementRefinementService;
      this.groundingFootprintResolver = groundingFootprintResolver;
   }

   public PlacementResult resolvePlacement(StructureSupportContext context, StructureStart structureStart, ChunkPos sourceChunkPos) {
      Optional<ResolvedStructureSupportPlane> supportPlane = this.supportPlaneResolver
         .resolve(context.structureId(), structureStart, context.settings().advanced().structurePlacementPolicy().footprintInsetRatioFor(context.structureId()));
      if (supportPlane.isEmpty()) {
         return PlacementResult.failed("missing_support_plane");
      }

      ResolvedStructureSupportPlane plane = supportPlane.get();
      StructurePlacementCategory category = context.settings().advanced().structurePlacementPolicy().effectiveCategoryFor(context.structureId(), plane);
      if (!category.usesIslandAwarePlacement()) {
         return PlacementResult.notAttempted();
      }

      PlacementResult searchResult = this.skyIslandPlacementService
         .resolvePlacementForFootprint(
            context.structureId(),
            category,
            context.settings(),
            context.islandField(),
            sourceChunkPos,
            plane.rawFootprint(),
            plane.effectiveFootprint(),
            this.groundingFootprintResolver.resolve(context.structureId(), plane.rawFootprint(), structureStart)
         );
      return searchResult.attempted() && searchResult.successful()
         ? new PlacementResult(
            true,
            true,
            searchResult.target(),
            null,
            plane.rawFootprint(),
            plane.effectiveFootprint(),
            plane,
            searchResult.islandCenteredFallbackUsed(),
            searchResult.qualifiedHosts(),
            searchResult.attemptedHosts(),
            searchResult.coarseOffsetsEvaluated(),
            searchResult.fineOffsetsEvaluated(),
            searchResult.hostAttemptCapHit(),
            searchResult.offsetCapHit()
         )
         : new PlacementResult(
            searchResult.attempted(),
            searchResult.successful(),
            searchResult.target(),
            searchResult.failureReason(),
            searchResult.rawFootprint(),
            searchResult.effectiveFootprint(),
            plane,
            searchResult.islandCenteredFallbackUsed(),
            searchResult.qualifiedHosts(),
            searchResult.attemptedHosts(),
            searchResult.coarseOffsetsEvaluated(),
            searchResult.fineOffsetsEvaluated(),
            searchResult.hostAttemptCapHit(),
            searchResult.offsetCapHit()
         );
   }

   public LandRefinementResult refineLandPlacement(StructureSupportContext context, StructureStart structureStart) {
      Optional<ResolvedStructureSupportPlane> supportPlane = this.supportPlaneResolver
         .resolve(context.structureId(), structureStart, context.settings().advanced().structurePlacementPolicy().footprintInsetRatioFor(context.structureId()));
      return supportPlane.isEmpty()
         ? LandRefinementResult.rejected("rejected_missing_support_plane")
         : this.landPlacementRefinementService.refineLandPlacement(context, structureStart, supportPlane.get());
   }

   public List<PlacementCandidate> resolveOrderedPlacementCandidates(StructureSupportContext context, StructureStart structureStart, ChunkPos sourceChunkPos) {
      Optional<ResolvedStructureSupportPlane> supportPlane = this.supportPlaneResolver
         .resolve(context.structureId(), structureStart, context.settings().advanced().structurePlacementPolicy().footprintInsetRatioFor(context.structureId()));
      if (supportPlane.isEmpty()) {
         return List.of();
      }

      ResolvedStructureSupportPlane plane = supportPlane.get();
      StructurePlacementCategory category = context.settings().advanced().structurePlacementPolicy().effectiveCategoryFor(context.structureId(), plane);
      return !category.usesIslandAwarePlacement()
         ? List.of()
         : this.skyIslandPlacementService
            .resolveOrderedPlacementCandidates(
               context.structureId(),
               category,
               context.settings(),
               context.islandField(),
               sourceChunkPos,
               plane.rawFootprint(),
               plane.effectiveFootprint(),
               this.groundingFootprintResolver.resolve(context.structureId(), plane.rawFootprint(), structureStart)
            );
   }

   public PlacementFailureDiagnostics diagnoseRejectedPlacement(StructureSupportContext context, StructureStart structureStart, ChunkPos sourceChunkPos) {
      Optional<ResolvedStructureSupportPlane> supportPlane = this.supportPlaneResolver
         .resolve(context.structureId(), structureStart, context.settings().advanced().structurePlacementPolicy().footprintInsetRatioFor(context.structureId()));
      if (supportPlane.isEmpty()) {
         return PlacementFailureDiagnostics.empty();
      }

      ResolvedStructureSupportPlane plane = supportPlane.get();
      StructurePlacementCategory category = context.settings().advanced().structurePlacementPolicy().effectiveCategoryFor(context.structureId(), plane);
      return !category.usesIslandAwarePlacement()
         ? PlacementFailureDiagnostics.empty()
         : this.skyIslandPlacementService
            .diagnosePlacementFailure(
               context.structureId(),
               category,
               context.settings(),
               context.islandField(),
               sourceChunkPos,
               plane.rawFootprint(),
               plane.effectiveFootprint(),
               this.groundingFootprintResolver.resolve(context.structureId(), plane.rawFootprint(), structureStart)
            );
   }

   public PlacementResult resolvePlacementForFootprint(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      SkyIslandSettings settings,
      IslandField islandField,
      ChunkPos sourceChunkPos,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint
   ) {
      return this.skyIslandPlacementService
         .resolvePlacementForFootprint(structureId, category, settings, islandField, sourceChunkPos, rawFootprint, effectiveFootprint, rawFootprint);
   }
}
