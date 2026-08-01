package org.sathrek.sky_archipelago.worldgen.structure.sky;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LocalOffset;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.RejectedHostCandidate;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.RejectedSkyCandidate;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.SkyCandidate;

public final class SkyCandidateEvaluator {
   private final LocalOffsetSampler localOffsetSampler;
   private final SkyCandidateOrdering ordering;

   public SkyCandidateEvaluator(LocalOffsetSampler localOffsetSampler, SkyCandidateOrdering ordering) {
      this.localOffsetSampler = localOffsetSampler;
      this.ordering = ordering;
   }

   public boolean isQualifiedHostCandidate(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      IslandField.IslandPreview preview,
      IslandField islandField,
      SkyIslandSettings settings,
      int minHostIslandRadius,
      int minHostStableTopCells,
      int maxTopDelta,
      int minThickness
   ) {
      return this.evaluateHostCandidate(
            structureId, category, preview, islandField, settings, minHostIslandRadius, minHostStableTopCells, maxTopDelta, minThickness
         )
         .qualified();
   }

   public RejectedHostCandidate evaluateRejectedHostCandidate(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      IslandField.IslandPreview preview,
      IslandField islandField,
      SkyIslandSettings settings,
      int minHostIslandRadius,
      int minHostStableTopCells,
      int maxTopDelta,
      int minThickness
   ) {
      SkyCandidateEvaluator.HostCandidateEvaluation evaluation = this.evaluateHostCandidate(
         structureId, category, preview, islandField, settings, minHostIslandRadius, minHostStableTopCells, maxTopDelta, minThickness
      );
      return evaluation.qualified()
         ? null
         : new RejectedHostCandidate(preview, evaluation.rejectionReason(), evaluation.stableTopCells(), minHostStableTopCells, minHostIslandRadius);
   }

   private SkyCandidateEvaluator.HostCandidateEvaluation evaluateHostCandidate(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      IslandField.IslandPreview preview,
      IslandField islandField,
      SkyIslandSettings settings,
      int minHostIslandRadius,
      int minHostStableTopCells,
      int maxTopDelta,
      int minThickness
   ) {
      if (!this.isCandidateFamilyAllowed(category, preview.family())) {
         return new SkyCandidateEvaluator.HostCandidateEvaluation(false, "family_not_allowed", 0);
      }

      if (preview.radius() < minHostIslandRadius) {
         return new SkyCandidateEvaluator.HostCandidateEvaluation(false, "host_radius_too_small", 0);
      }

      TerrainColumn centerColumn = islandField.sampleColumn(preview.x(), preview.z(), settings);
      if (!centerColumn.exists()) {
         return new SkyCandidateEvaluator.HostCandidateEvaluation(false, "host_center_missing_column", 0);
      }

      int centerTopY = centerColumn.topY();
      int[] hostStableTopCells = new int[]{0};
      int hostSampleHalfSpan = Math.min(Math.max(8, preview.radius() / 2), 16);
      new StructureFootprint(
            preview.x() - hostSampleHalfSpan, preview.x() + hostSampleHalfSpan, preview.z() - hostSampleHalfSpan, preview.z() + hostSampleHalfSpan
         )
         .forEachGridPoint(settings.structureSupport().supportSampleGridSize(), (x, z) -> {
            TerrainColumn column = islandField.sampleColumn(x, z, settings);
            if (column.exists()) {
               if (Math.abs(column.topY() - centerTopY) <= maxTopDelta && column.thickness() >= minThickness) {
                  hostStableTopCells[0]++;
               }
            }
         });
      return hostStableTopCells[0] < minHostStableTopCells
         ? new SkyCandidateEvaluator.HostCandidateEvaluation(false, "host_stability_too_low", hostStableTopCells[0])
         : new SkyCandidateEvaluator.HostCandidateEvaluation(true, "qualified", hostStableTopCells[0]);
   }

   public Optional<SkyCandidate> evaluateCandidate(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      IslandField.IslandPreview preview,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint,
      int searchRadiusBlocks,
      int minStableTopCells,
      int topOffset,
      IslandField islandField,
      SkyIslandSettings settings
   ) {
      List<SkyCandidate> candidates = this.evaluateCandidates(
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
      );
      return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
   }

   public List<SkyCandidate> evaluateCandidates(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      IslandField.IslandPreview preview,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint,
      int searchRadiusBlocks,
      int minStableTopCells,
      int topOffset,
      IslandField islandField,
      SkyIslandSettings settings
   ) {
      return this.evaluateCandidatesWithSweep(
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
            settings,
            rawFootprint.centerX(),
            rawFootprint.centerZ(),
            settings.advanced().structurePlacementPolicy().maxOffsetsPerIslandForCategory(category, preview.radius())
         )
         .candidates();
   }

   public SkyCandidateEvaluator.CandidateSweepResult evaluateCandidatesWithSweep(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      IslandField.IslandPreview preview,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint,
      int searchRadiusBlocks,
      int minStableTopCells,
      int topOffset,
      IslandField islandField,
      SkyIslandSettings settings,
      int distanceAnchorX,
      int distanceAnchorZ,
      int maxOffsetsPerIsland
   ) {
      TerrainColumn centerColumn = islandField.sampleColumn(preview.x(), preview.z(), settings);
      if (!centerColumn.exists()) {
         return new SkyCandidateEvaluator.CandidateSweepResult(List.of(), 0, 0, false);
      }

      int maxTopDelta = switch (category) {
         case SMALL_SKY -> 4;
         case SURFACE_SKY -> 3;
         case HAMLET_SKY -> 2;
         case GROUND_VILLAGE -> 2;
         case STRONGHOLD, UNDERGROUND, WATER, DEFAULT, SKY -> 3;
      };

      int minThickness = switch (category) {
         case SMALL_SKY -> 4;
         case SURFACE_SKY -> 6;
         case HAMLET_SKY -> 8;
         case GROUND_VILLAGE -> 10;
         case STRONGHOLD, UNDERGROUND, WATER, DEFAULT, SKY -> 6;
      };
      int localSearchRadius = Math.min(
         settings.advanced().structurePlacementPolicy().localSearchRadiusBlocksForCategory(category),
         Math.max(0, preview.radius() - this.requiredMargin(category, preview.family()))
      );
      int coarseStep = settings.advanced().structurePlacementPolicy().localSearchStepBlocksForCategory(category);
      int fineStep = settings.advanced().structurePlacementPolicy().fineSearchStepBlocksForCategory(category);
      int fineTopK = settings.advanced().structurePlacementPolicy().fineTopCandidatesForCategory(category);
      int requiredStableTopCells = minStableTopCells + this.additionalStableCellRequirement(category, preview.family());
      double groundedThreshold = settings.advanced().structurePlacementPolicy().groundedSampleThresholdForCategory(category);
      int maxGroundGapBlocks = settings.advanced().structurePlacementPolicy().maxGroundGapBlocksForCategory(category);
      int baseCap = Math.max(1, maxOffsetsPerIsland);
      int hardCap = baseCap;
      List<LocalOffset> coarseOffsets = this.localOffsetSampler.localOffsets(localSearchRadius, coarseStep, coarseStep);
      ArrayList<SkyCandidate> coarseAccepted = new ArrayList<>();
      int coarseEvaluated = 0;
      int totalEvaluated = 0;
      boolean capHit = false;
      boolean strongNearMiss = false;

      for (LocalOffset offset : coarseOffsets) {
         if (totalEvaluated >= hardCap) {
            capHit = true;
            break;
         }

         coarseEvaluated++;
         totalEvaluated++;
         SkyCandidateEvaluator.SiteEvaluation evaluation = this.evaluateSiteOutcome(
            preview,
            rawFootprint,
            effectiveFootprint,
            groundingFootprint,
            offset.offsetX(),
            offset.offsetZ(),
            category,
            requiredStableTopCells,
            groundedThreshold,
            maxGroundGapBlocks,
            searchRadiusBlocks,
            topOffset,
            maxTopDelta,
            minThickness,
            islandField,
            settings,
            distanceAnchorX,
            distanceAnchorZ
         );
         evaluation.candidate().ifPresent(coarseAccepted::add);
         if (!strongNearMiss && isStrongNearMiss(evaluation, requiredStableTopCells, groundedThreshold)) {
            strongNearMiss = true;
         }
      }

      if (strongNearMiss) {
         int adaptiveBonus = settings.advanced().structurePlacementPolicy().adaptiveOffsetCapBonusForCategory(category, preview.radius());
         hardCap = Math.max(hardCap, baseCap + Math.max(0, adaptiveBonus));
         capHit = totalEvaluated >= hardCap;
      }

      ArrayList<SkyCandidate> merged = new ArrayList<>(coarseAccepted);
      int fineEvaluated = 0;
      if (!capHit && fineStep > 0 && fineStep < coarseStep && fineTopK > 0 && !coarseAccepted.isEmpty()) {
         List<SkyCandidate> topCoarse = coarseAccepted.stream().sorted(this.ordering.orderingFor(structureId)).limit(fineTopK).toList();
         ArrayList<LocalOffset> fineOffsets = new ArrayList<>();

         for (SkyCandidate coarseAnchor : topCoarse) {
            int fineRadius = Math.max(fineStep, Math.min(coarseStep * 2, localSearchRadius));
            fineOffsets.addAll(
               this.localOffsetSampler
                  .localOffsets(fineRadius, fineStep, fineStep)
                  .stream()
                  .map(offset -> new LocalOffset(coarseAnchor.localOffsetX() + offset.offsetX(), coarseAnchor.localOffsetZ() + offset.offsetZ(), 0))
                  .toList()
            );
         }

         for (LocalOffset fineOffset : fineOffsets.stream()
            .distinct()
            .sorted(Comparator.comparingInt(LocalOffset::offsetX).thenComparingInt(LocalOffset::offsetZ))
            .toList()) {
            if (totalEvaluated >= hardCap) {
               capHit = true;
               break;
            }

            fineEvaluated++;
            totalEvaluated++;
            this.evaluateSite(
                  preview,
                  rawFootprint,
                  effectiveFootprint,
                  groundingFootprint,
                  fineOffset.offsetX(),
                  fineOffset.offsetZ(),
                  category,
                  requiredStableTopCells,
                  groundedThreshold,
                  maxGroundGapBlocks,
                  searchRadiusBlocks,
                  topOffset,
                  maxTopDelta,
                  minThickness,
                  islandField,
                  settings,
                  distanceAnchorX,
                  distanceAnchorZ
               )
               .ifPresent(merged::add);
         }
      }

      List<SkyCandidate> ordered = merged.stream().distinct().sorted(this.ordering.orderingFor(structureId)).toList();
      return new SkyCandidateEvaluator.CandidateSweepResult(ordered, coarseEvaluated, fineEvaluated, capHit);
   }

   public List<RejectedSkyCandidate> evaluateRejectedCandidates(
      ResourceLocation structureId,
      StructurePlacementCategory category,
      IslandField.IslandPreview preview,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint,
      int searchRadiusBlocks,
      int minStableTopCells,
      int topOffset,
      IslandField islandField,
      SkyIslandSettings settings
   ) {
      TerrainColumn centerColumn = islandField.sampleColumn(preview.x(), preview.z(), settings);
      if (!centerColumn.exists()) {
         return List.of();
      }

      int maxTopDelta = switch (category) {
         case SMALL_SKY -> 4;
         case SURFACE_SKY -> 3;
         case HAMLET_SKY -> 2;
         case GROUND_VILLAGE -> 2;
         case STRONGHOLD, UNDERGROUND, WATER, DEFAULT, SKY -> 3;
      };

      int minThickness;
      int var21 = minThickness = switch (category) {
         case SMALL_SKY -> 4;
         case SURFACE_SKY -> 6;
         case HAMLET_SKY -> 8;
         case GROUND_VILLAGE -> 10;
         case STRONGHOLD, UNDERGROUND, WATER, DEFAULT, SKY -> 6;
      };
      int localSearchRadius = Math.min(
         settings.advanced().structurePlacementPolicy().localSearchRadiusBlocksForCategory(category),
         Math.max(0, preview.radius() - this.requiredMargin(category, preview.family()))
      );
      int localSearchStep = settings.advanced().structurePlacementPolicy().localSearchStepBlocksForCategory(category);
      int requiredStableTopCells = minStableTopCells + this.additionalStableCellRequirement(category, preview.family());
      double groundedThreshold = settings.advanced().structurePlacementPolicy().groundedSampleThresholdForCategory(category);
      int maxGroundGapBlocks = settings.advanced().structurePlacementPolicy().maxGroundGapBlocksForCategory(category);
      return this.localOffsetSampler
         .localOffsets(localSearchRadius, localSearchStep, localSearchStep)
         .stream()
         .map(
            offset -> this.evaluateRejectedSite(
               preview,
               rawFootprint,
               effectiveFootprint,
               groundingFootprint,
               offset.offsetX(),
               offset.offsetZ(),
               category,
               requiredStableTopCells,
               groundedThreshold,
               maxGroundGapBlocks,
               searchRadiusBlocks,
               topOffset,
               maxTopDelta,
               minThickness,
               islandField,
               settings
            )
         )
         .flatMap(Optional::stream)
         .sorted(
            Comparator.comparingInt(RejectedSkyCandidate::stableTopCells)
               .reversed()
               .thenComparingDouble(RejectedSkyCandidate::groundedRatio)
               .reversed()
               .thenComparingInt(RejectedSkyCandidate::distanceSquared)
         )
         .toList();
   }

   private boolean isCandidateFamilyAllowed(StructurePlacementCategory category, IslandField.IslandFamily family) {
      return switch (category) {
         case SMALL_SKY, SURFACE_SKY, WATER -> family == IslandField.IslandFamily.ANCHOR_PLATEAU || family == IslandField.IslandFamily.SATELLITE;
         case HAMLET_SKY, GROUND_VILLAGE, STRONGHOLD, UNDERGROUND -> family == IslandField.IslandFamily.ANCHOR_PLATEAU;
         case DEFAULT, SKY -> false;
      };
   }

   private static boolean isStrongNearMiss(SkyCandidateEvaluator.SiteEvaluation evaluation, int requiredStableTopCells, double groundedThreshold) {
      if (evaluation.candidate().isPresent()) {
         return false;
      }

      return switch (evaluation.rejectionReason()) {
         case "grounded_ratio_too_low" -> evaluation.groundedRatio() >= Math.max(0.0, groundedThreshold - 0.15);
         case "stable_cells_too_low" -> evaluation.stableTopCells() >= Math.max(1, requiredStableTopCells - 2);
         case "insufficient_island_margin" -> evaluation.stableTopCells() >= Math.max(1, requiredStableTopCells - 1);
         default -> false;
      };
   }

   private boolean hasEnoughIslandMargin(StructurePlacementCategory category, IslandField.IslandPreview preview, StructureFootprint targetFootprint) {
      int requiredMargin = this.requiredMargin(category, preview.family());
      int allowedRadius = preview.radius() - requiredMargin;
      if (allowedRadius <= 0) {
         return false;
      }

      int footprintHalfSpan = Math.max(targetFootprint.spanX(), targetFootprint.spanZ()) / 2;
      if (footprintHalfSpan >= allowedRadius) {
         return false;
      }

      int[][] corners = new int[][]{
         {targetFootprint.minX(), targetFootprint.minZ()},
         {targetFootprint.minX(), targetFootprint.maxZ()},
         {targetFootprint.maxX(), targetFootprint.minZ()},
         {targetFootprint.maxX(), targetFootprint.maxZ()}
      };

      for (int[] corner : corners) {
         int dx = corner[0] - preview.x();
         int dz = corner[1] - preview.z();
         if (dx * dx + dz * dz > allowedRadius * allowedRadius) {
            return false;
         }
      }

      return true;
   }

   private int requiredMargin(StructurePlacementCategory category, IslandField.IslandFamily family) {
      return switch (category) {
         case SMALL_SKY -> 6;
         case SURFACE_SKY -> 8;
         case HAMLET_SKY -> 14;
         case GROUND_VILLAGE -> 18;
         case STRONGHOLD, UNDERGROUND, WATER -> 8;
         case DEFAULT, SKY -> 0;
      };
   }

   private int additionalStableCellRequirement(StructurePlacementCategory category, IslandField.IslandFamily family) {
      return switch (category) {
         case SMALL_SKY -> 2;
         case SURFACE_SKY, STRONGHOLD, UNDERGROUND, WATER, DEFAULT, SKY -> 0;
         case HAMLET_SKY -> 4;
         case GROUND_VILLAGE -> 5;
      };
   }

   private Optional<SkyCandidate> evaluateSite(
      IslandField.IslandPreview preview,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint,
      int localOffsetX,
      int localOffsetZ,
      StructurePlacementCategory category,
      int requiredStableTopCells,
      double groundedThreshold,
      int maxGroundGapBlocks,
      int searchRadiusBlocks,
      int topOffset,
      int maxTopDelta,
      int minThickness,
      IslandField islandField,
      SkyIslandSettings settings,
      int distanceAnchorX,
      int distanceAnchorZ
   ) {
      SkyCandidateEvaluator.SiteEvaluation evaluation = this.evaluateSiteOutcome(
         preview,
         rawFootprint,
         effectiveFootprint,
         groundingFootprint,
         localOffsetX,
         localOffsetZ,
         category,
         requiredStableTopCells,
         groundedThreshold,
         maxGroundGapBlocks,
         searchRadiusBlocks,
         topOffset,
         maxTopDelta,
         minThickness,
         islandField,
         settings,
         distanceAnchorX,
         distanceAnchorZ
      );
      return evaluation.candidate();
   }

   private Optional<RejectedSkyCandidate> evaluateRejectedSite(
      IslandField.IslandPreview preview,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint,
      int localOffsetX,
      int localOffsetZ,
      StructurePlacementCategory category,
      int requiredStableTopCells,
      double groundedThreshold,
      int maxGroundGapBlocks,
      int searchRadiusBlocks,
      int topOffset,
      int maxTopDelta,
      int minThickness,
      IslandField islandField,
      SkyIslandSettings settings
   ) {
      SkyCandidateEvaluator.SiteEvaluation evaluation = this.evaluateSiteOutcome(
         preview,
         rawFootprint,
         effectiveFootprint,
         groundingFootprint,
         localOffsetX,
         localOffsetZ,
         category,
         requiredStableTopCells,
         groundedThreshold,
         maxGroundGapBlocks,
         searchRadiusBlocks,
         topOffset,
         maxTopDelta,
         minThickness,
         islandField,
         settings,
         rawFootprint.centerX(),
         rawFootprint.centerZ()
      );
      return evaluation.candidate().isPresent()
         ? Optional.empty()
         : Optional.of(
            new RejectedSkyCandidate(
               preview,
               evaluation.candidateCenterX(),
               evaluation.candidateCenterZ(),
               localOffsetX,
               localOffsetZ,
               evaluation.rejectionReason(),
               evaluation.stableTopCells(),
               requiredStableTopCells,
               evaluation.groundedSamples(),
               evaluation.groundingSampleCount(),
               evaluation.groundedRatio(),
               groundedThreshold,
               evaluation.distanceSquared(),
               searchRadiusBlocks
            )
         );
   }

   private SkyCandidateEvaluator.SiteEvaluation evaluateSiteOutcome(
      IslandField.IslandPreview preview,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint groundingFootprint,
      int localOffsetX,
      int localOffsetZ,
      StructurePlacementCategory category,
      int requiredStableTopCells,
      double groundedThreshold,
      int maxGroundGapBlocks,
      int searchRadiusBlocks,
      int topOffset,
      int maxTopDelta,
      int minThickness,
      IslandField islandField,
      SkyIslandSettings settings,
      int distanceAnchorX,
      int distanceAnchorZ
   ) {
      int candidateCenterX = preview.x() + localOffsetX;
      int candidateCenterZ = preview.z() + localOffsetZ;
      TerrainColumn centerColumn = islandField.sampleColumn(candidateCenterX, candidateCenterZ, settings);
      if (!centerColumn.exists()) {
         return new SkyCandidateEvaluator.SiteEvaluation(
            Optional.empty(), "candidate_center_missing_column", 0, 0, 0, 0.0, Integer.MAX_VALUE, candidateCenterX, candidateCenterZ
         );
      }

      int candidateTopY = centerColumn.topY();
      int rawOffsetX = candidateCenterX - rawFootprint.centerX();
      int rawOffsetZ = candidateCenterZ - rawFootprint.centerZ();
      int effectiveOffsetX = candidateCenterX - effectiveFootprint.centerX();
      int effectiveOffsetZ = candidateCenterZ - effectiveFootprint.centerZ();
      int groundingOffsetX = candidateCenterX - groundingFootprint.centerX();
      int groundingOffsetZ = candidateCenterZ - groundingFootprint.centerZ();
      StructureFootprint targetRawFootprint = rawFootprint.translate(rawOffsetX, rawOffsetZ);
      StructureFootprint targetEffectiveFootprint = effectiveFootprint.translate(effectiveOffsetX, effectiveOffsetZ);
      StructureFootprint targetGroundingFootprint = groundingFootprint.translate(groundingOffsetX, groundingOffsetZ);
      if (!this.hasEnoughIslandMargin(category, preview, targetGroundingFootprint)) {
         return new SkyCandidateEvaluator.SiteEvaluation(
            Optional.empty(), "insufficient_island_margin", 0, 0, 0, 0.0, Integer.MAX_VALUE, candidateCenterX, candidateCenterZ
         );
      }

      int[] stableTopCells = new int[]{0};
      targetEffectiveFootprint.forEachGridPoint(settings.structureSupport().supportSampleGridSize(), (x, z) -> {
         TerrainColumn column = islandField.sampleColumn(x, z, settings);
         if (column.exists()) {
            if (Math.abs(column.topY() - candidateTopY) <= maxTopDelta && column.thickness() >= minThickness) {
               stableTopCells[0]++;
            }
         }
      });
      if (stableTopCells[0] < requiredStableTopCells) {
         return new SkyCandidateEvaluator.SiteEvaluation(
            Optional.empty(), "stable_cells_too_low", stableTopCells[0], 0, 0, 0.0, Integer.MAX_VALUE, candidateCenterX, candidateCenterZ
         );
      }

      int targetBaseY = candidateTopY + topOffset;
      int[] groundedSamples = new int[]{0};
      int[] groundingSampleCount = new int[]{0};
      targetGroundingFootprint.forEachGridPoint(Math.max(3, settings.structureSupport().supportSampleGridSize()), (x, z) -> {
         groundingSampleCount[0]++;
         TerrainColumn column = islandField.sampleColumn(x, z, settings);
         if (column.exists()) {
            int gap = targetBaseY - column.topY();
            if (gap >= 0 && gap <= maxGroundGapBlocks) {
               groundedSamples[0]++;
            }
         }
      });
      double groundedRatio = groundingSampleCount[0] == 0 ? 0.0 : (double)groundedSamples[0] / groundingSampleCount[0];
      if (groundedRatio < groundedThreshold) {
         return new SkyCandidateEvaluator.SiteEvaluation(
            Optional.empty(),
            "grounded_ratio_too_low",
            stableTopCells[0],
            groundedSamples[0],
            groundingSampleCount[0],
            groundedRatio,
            Integer.MAX_VALUE,
            candidateCenterX,
            candidateCenterZ
         );
      }

      int dx = candidateCenterX - distanceAnchorX;
      int dz = candidateCenterZ - distanceAnchorZ;
      int distanceSquared = dx * dx + dz * dz;
      return distanceSquared > searchRadiusBlocks * searchRadiusBlocks
         ? new SkyCandidateEvaluator.SiteEvaluation(
            Optional.empty(),
            "outside_search_radius",
            stableTopCells[0],
            groundedSamples[0],
            groundingSampleCount[0],
            groundedRatio,
            distanceSquared,
            candidateCenterX,
            candidateCenterZ
         )
         : new SkyCandidateEvaluator.SiteEvaluation(
            Optional.of(
               new SkyCandidate(
                  preview,
                  targetRawFootprint,
                  targetEffectiveFootprint,
                  candidateCenterX,
                  candidateCenterZ,
                  localOffsetX,
                  localOffsetZ,
                  stableTopCells[0],
                  groundedSamples[0],
                  groundedRatio,
                  distanceSquared,
                  candidateTopY
               )
            ),
            "accepted",
            stableTopCells[0],
            groundedSamples[0],
            groundingSampleCount[0],
            groundedRatio,
            distanceSquared,
            candidateCenterX,
            candidateCenterZ
         );
   }

   public record CandidateSweepResult(List<SkyCandidate> candidates, int coarseOffsetsEvaluated, int fineOffsetsEvaluated, boolean offsetCapHit) {
   }

   private record HostCandidateEvaluation(boolean qualified, String rejectionReason, int stableTopCells) {
   }

   private record SiteEvaluation(
      Optional<SkyCandidate> candidate,
      String rejectionReason,
      int stableTopCells,
      int groundedSamples,
      int groundingSampleCount,
      double groundedRatio,
      int distanceSquared,
      int candidateCenterX,
      int candidateCenterZ
   ) {
   }
}
