package org.sathrek.sky_archipelago.worldgen.structure.sky;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementCandidate;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementFailureDiagnostics;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementResult;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.RejectedHostCandidate;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.RejectedSkyCandidate;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.SkyCandidate;

public final class SkyIslandPlacementService {
   private final SkyCandidateEvaluator skyCandidateEvaluator;
   private final SkyCandidateOrdering ordering;

   public SkyIslandPlacementService(SkyCandidateEvaluator skyCandidateEvaluator, SkyCandidateOrdering ordering) {
      this.skyCandidateEvaluator = skyCandidateEvaluator;
      this.ordering = ordering;
   }

   public PlacementResult resolvePlacementForFootprint(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      SkyIslandSettings settings,
      IslandField islandField,
      ChunkPos sourceChunkPos,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint
   ) {
      if (!category.usesIslandAwarePlacement()) {
         return PlacementResult.notAttempted();
      } else {
         int searchRadiusBlocks = settings.advanced().structurePlacementPolicy().searchRadiusChunksForCategory(category) * 16;
         if (searchRadiusBlocks <= 0) {
            return PlacementResult.failed("search_radius_disabled");
         } else {
            SkyIslandPlacementService.PlacementComputation base = this.computeCandidates(
               structureId, category, settings, islandField, sourceChunkPos, rawFootprint, effectiveFootprint, groundingFootprint, false
            );
            if (!base.candidates().isEmpty()) {
               PlacementCandidate best = base.candidates().get(0);
               return new PlacementResult(
                  true,
                  true,
                  best.target(),
                  null,
                  best.rawFootprint(),
                  best.effectiveFootprint(),
                  null,
                  false,
                  base.qualifiedHosts(),
                  base.attemptedHosts(),
                  base.coarseEvaluated(),
                  base.fineEvaluated(),
                  base.hostCapHit(),
                  base.offsetCapHit()
               );
            } else {
               SkyIslandPlacementService.PlacementComputation fallback = this.computeCandidates(
                  structureId, category, settings, islandField, sourceChunkPos, rawFootprint, effectiveFootprint, groundingFootprint, true
               );
               if (!fallback.candidates().isEmpty()) {
                  PlacementCandidate best = fallback.candidates().get(0);
                  return new PlacementResult(
                     true,
                     true,
                     best.target(),
                     null,
                     best.rawFootprint(),
                     best.effectiveFootprint(),
                     null,
                     true,
                     fallback.qualifiedHosts(),
                     fallback.attemptedHosts(),
                     fallback.coarseEvaluated(),
                     fallback.fineEvaluated(),
                     fallback.hostCapHit(),
                     fallback.offsetCapHit()
                  );
               } else {
                  String reason = fallback.qualifiedHosts() == 0 ? "no_qualified_host_island" : "no_viable_island_candidate";
                  return new PlacementResult(
                     true,
                     false,
                     null,
                     reason,
                     null,
                     null,
                     null,
                     true,
                     fallback.qualifiedHosts(),
                     fallback.attemptedHosts(),
                     fallback.coarseEvaluated(),
                     fallback.fineEvaluated(),
                     fallback.hostCapHit(),
                     fallback.offsetCapHit()
                  );
               }
            }
         }
      }
   }

   public List<PlacementCandidate> resolveOrderedPlacementCandidates(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      SkyIslandSettings settings,
      IslandField islandField,
      ChunkPos sourceChunkPos,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint
   ) {
      SkyIslandPlacementService.PlacementComputation base = this.computeCandidates(
         structureId, category, settings, islandField, sourceChunkPos, rawFootprint, effectiveFootprint, groundingFootprint, false
      );
      return !base.candidates().isEmpty()
         ? base.candidates()
         : this.computeCandidates(structureId, category, settings, islandField, sourceChunkPos, rawFootprint, effectiveFootprint, groundingFootprint, true)
            .candidates();
   }

   private SkyIslandPlacementService.PlacementComputation computeCandidates(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      SkyIslandSettings settings,
      IslandField islandField,
      ChunkPos sourceChunkPos,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint,
      boolean islandCenteredFallback
   ) {
      if (!category.usesIslandAwarePlacement()) {
         return SkyIslandPlacementService.PlacementComputation.empty();
      }

      int searchRadiusBlocks = settings.advanced().structurePlacementPolicy().searchRadiusChunksForCategory(category) * 16;
      if (searchRadiusBlocks <= 0) {
         return SkyIslandPlacementService.PlacementComputation.empty();
      }

      int centerX = sourceChunkPos.getMiddleBlockX();
      int centerZ = sourceChunkPos.getMiddleBlockZ();
      int minStableTopCells = settings.advanced().structurePlacementPolicy().minStableTopCellsForCategory(category);
      int topOffset = settings.advanced().structurePlacementPolicy().topOffsetForCategory(category);
      int minHostIslandRadius = settings.advanced().structurePlacementPolicy().minHostIslandRadiusForCategory(category);
      int minHostStableTopCells = settings.advanced().structurePlacementPolicy().minHostStableTopCellsForCategory(category);

      int maxHostTopDelta = switch (category) {
         case SMALL_SKY -> 4;
         case SURFACE_SKY -> 3;
         case HAMLET_SKY -> 2;
         case GROUND_VILLAGE -> 2;
         case STRONGHOLD, UNDERGROUND, WATER, DEFAULT, SKY -> 3;
      };

      int minHostThickness;
      int var38 = minHostThickness = switch (category) {
         case SMALL_SKY -> 4;
         case SURFACE_SKY -> 6;
         case HAMLET_SKY -> 8;
         case GROUND_VILLAGE -> 10;
         case STRONGHOLD, UNDERGROUND, WATER, DEFAULT, SKY -> 6;
      };
      List<IslandField.IslandPreview> previews = islandField.collectIslandPreviewsInRadius(centerX, centerZ, searchRadiusBlocks, settings);
      List<IslandField.IslandPreview> qualified = previews.stream()
         .filter(
            preview -> this.skyCandidateEvaluator
               .isQualifiedHostCandidate(
                  structureId, category, preview, islandField, settings, minHostIslandRadius, minHostStableTopCells, maxHostTopDelta, minHostThickness
               )
         )
         .sorted(
            Comparator.<IslandField.IslandPreview>comparingInt(p -> distanceSq(p.x(), p.z(), centerX, centerZ))
               .thenComparing(p -> p.radius(), Comparator.reverseOrder())
         )
         .toList();
      if (qualified.isEmpty()) {
         return new SkyIslandPlacementService.PlacementComputation(List.of(), 0, 0, 0, 0, false, false);
      }

      int hostBudget = settings.advanced().structurePlacementPolicy().maxHostAttemptsForCategory(category, qualified.get(0).radius());
      int attemptedHosts = 0;
      int coarseEvaluated = 0;
      int fineEvaluated = 0;
      boolean offsetCapHit = false;
      boolean hostCapHit = false;
      ArrayList<SkyCandidate> all = new ArrayList<>();

      for (IslandField.IslandPreview host : qualified) {
         if (attemptedHosts >= hostBudget) {
            hostCapHit = true;
            break;
         }

         attemptedHosts++;
         int distanceAnchorX = islandCenteredFallback ? host.x() : rawFootprint.centerX();
         int distanceAnchorZ = islandCenteredFallback ? host.z() : rawFootprint.centerZ();
         int perIslandCap = settings.advanced().structurePlacementPolicy().maxOffsetsPerIslandForCategory(category, host.radius());
         SkyCandidateEvaluator.CandidateSweepResult sweep = this.skyCandidateEvaluator
            .evaluateCandidatesWithSweep(
               structureId,
               category,
               host,
               rawFootprint,
               effectiveFootprint,
               groundingFootprint,
               searchRadiusBlocks,
               minStableTopCells,
               topOffset,
               islandField,
               settings,
               distanceAnchorX,
               distanceAnchorZ,
               perIslandCap
            );
         coarseEvaluated += sweep.coarseOffsetsEvaluated();
         fineEvaluated += sweep.fineOffsetsEvaluated();
         if (sweep.offsetCapHit()) {
            offsetCapHit = true;
         }

         all.addAll(sweep.candidates());
      }

      all.sort(this.ordering.orderingFor(structureId));
      ArrayList<PlacementCandidate> ordered = new ArrayList<>(all.size());

      for (int i = 0; i < all.size(); i++) {
         SkyCandidate candidate = all.get(i);
         int targetY = candidate.topY() + topOffset;
         ordered.add(
            new PlacementCandidate(
               new SkyStructurePlacementTarget(
                  candidate.targetCenterX(),
                  targetY,
                  candidate.targetCenterZ(),
                  candidate.topY(),
                  topOffset,
                  candidate.localOffsetX(),
                  candidate.localOffsetZ(),
                  candidate.stableTopCells(),
                  candidate.groundedSamples(),
                  candidate.groundedRatio(),
                  searchRadiusBlocks,
                  candidate.preview().family(),
                  candidate.preview().heightBand()
               ),
               candidate.rawFootprint(),
               candidate.effectiveFootprint(),
               i
            )
         );
      }

      return new SkyIslandPlacementService.PlacementComputation(
         ordered, qualified.size(), attemptedHosts, coarseEvaluated, fineEvaluated, hostCapHit, offsetCapHit
      );
   }

   public PlacementFailureDiagnostics diagnosePlacementFailure(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      SkyIslandSettings settings,
      IslandField islandField,
      ChunkPos sourceChunkPos,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint
   ) {
      if (!category.usesIslandAwarePlacement()) {
         return PlacementFailureDiagnostics.empty();
      }

      int searchRadiusBlocks = settings.advanced().structurePlacementPolicy().searchRadiusChunksForCategory(category) * 16;
      if (searchRadiusBlocks <= 0) {
         return PlacementFailureDiagnostics.empty();
      }

      int centerX = sourceChunkPos.getMiddleBlockX();
      int centerZ = sourceChunkPos.getMiddleBlockZ();
      int minStableTopCells = settings.advanced().structurePlacementPolicy().minStableTopCellsForCategory(category);
      int topOffset = settings.advanced().structurePlacementPolicy().topOffsetForCategory(category);
      int minHostIslandRadius = settings.advanced().structurePlacementPolicy().minHostIslandRadiusForCategory(category);
      int minHostStableTopCells = settings.advanced().structurePlacementPolicy().minHostStableTopCellsForCategory(category);

      int maxHostTopDelta = switch (category) {
         case SMALL_SKY -> 4;
         case SURFACE_SKY -> 3;
         case HAMLET_SKY -> 2;
         case GROUND_VILLAGE -> 2;
         case STRONGHOLD, UNDERGROUND, WATER, DEFAULT, SKY -> 3;
      };

      int minHostThickness;
      int var22 = minHostThickness = switch (category) {
         case SMALL_SKY -> 4;
         case SURFACE_SKY -> 6;
         case HAMLET_SKY -> 8;
         case GROUND_VILLAGE -> 10;
         case STRONGHOLD, UNDERGROUND, WATER, DEFAULT, SKY -> 6;
      };
      List<IslandField.IslandPreview> previews = islandField.collectIslandPreviewsInRadius(centerX, centerZ, searchRadiusBlocks, settings);
      List<IslandField.IslandPreview> qualifiedPreviews = previews.stream()
         .filter(
            preview -> this.skyCandidateEvaluator
               .isQualifiedHostCandidate(
                  structureId, category, preview, islandField, settings, minHostIslandRadius, minHostStableTopCells, maxHostTopDelta, minHostThickness
               )
         )
         .toList();
      List<RejectedHostCandidate> rejectedHosts = previews.stream()
         .map(
            preview -> this.skyCandidateEvaluator
               .evaluateRejectedHostCandidate(
                  structureId, category, preview, islandField, settings, minHostIslandRadius, minHostStableTopCells, maxHostTopDelta, minHostThickness
               )
         )
         .filter(Objects::nonNull)
         .sorted(
            Comparator.comparingInt(RejectedHostCandidate::stableTopCells)
               .reversed()
               .thenComparingInt(candidate -> candidate.preview().radius())
               .reversed()
               .thenComparing(RejectedHostCandidate::rejectionReason)
         )
         .limit(5L)
         .toList();
      List<RejectedSkyCandidate> rejectedCandidates = qualifiedPreviews.stream()
         .flatMap(
            preview -> this.skyCandidateEvaluator
               .evaluateRejectedCandidates(
                  structureId,
                  category,
                  preview,
                  rawFootprint,
                  effectiveFootprint,
                  groundingFootprint,
                  searchRadiusBlocks,
                  minStableTopCells,
                  topOffset,
                  islandField,
                  settings
               )
               .stream()
         )
         .sorted(
            Comparator.comparingInt(RejectedSkyCandidate::stableTopCells)
               .reversed()
               .thenComparingDouble(RejectedSkyCandidate::groundedRatio)
               .reversed()
               .thenComparingInt(RejectedSkyCandidate::distanceSquared)
         )
         .limit(8L)
         .toList();
      return new PlacementFailureDiagnostics(previews.size(), qualifiedPreviews.size(), rejectedHosts, rejectedCandidates);
   }

   private static int distanceSq(int x0, int z0, int x1, int z1) {
      int dx = x0 - x1;
      int dz = z0 - z1;
      return dx * dx + dz * dz;
   }

   private record PlacementComputation(
      List<PlacementCandidate> candidates,
      int qualifiedHosts,
      int attemptedHosts,
      int coarseEvaluated,
      int fineEvaluated,
      boolean hostCapHit,
      boolean offsetCapHit
   ) {
      static SkyIslandPlacementService.PlacementComputation empty() {
         return new SkyIslandPlacementService.PlacementComputation(List.of(), 0, 0, 0, 0, false, false);
      }
   }
}
