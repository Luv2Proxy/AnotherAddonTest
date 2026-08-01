package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground;

import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.CategoryPlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementDecision;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.structure.JigsawAnchorResolver;
import org.sathrek.sky_archipelago.worldgen.structure.PieceAwareSupportPlaneResolver;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.mineshafts.MineshaftAnchorResolver;
import org.sathrek.sky_archipelago.worldgen.structure.mineshafts.MineshaftAnchorStrategy;
import org.sathrek.sky_archipelago.worldgen.structure.mineshafts.MineshaftPlacementCoordinator;
import org.sathrek.sky_archipelago.worldgen.structure.mineshafts.MineshaftPlacementDecision;
import org.sathrek.sky_archipelago.worldgen.structure.underground.DynamicUndergroundPlacementCoordinator;
import org.sathrek.sky_archipelago.worldgen.structure.underground.UndergroundPlacementBehavior;
import org.sathrek.sky_archipelago.worldgen.structure.underground.UndergroundPlacementCoordinator;
import org.sathrek.sky_archipelago.worldgen.structure.underground.UndergroundPlacementDecision;

public final class UndergroundV2PlacementEngine implements CategoryPlacementEngine {
   private final UndergroundStrategySelector strategySelector = new UndergroundStrategySelector();
   private final UndergroundCandidatePlanner candidatePlanner = new UndergroundCandidatePlanner();
   private final UndergroundConstraintEvaluator constraintEvaluator = new UndergroundConstraintEvaluator();
   private final UndergroundCandidateRanker candidateRanker = new UndergroundCandidateRanker();
   private final UndergroundPlacementCommitter committer = new UndergroundPlacementCommitter();
   private final UndergroundTelemetry telemetry = new UndergroundTelemetry();
   private final UndergroundPlacementCoordinator staticCoordinator = new UndergroundPlacementCoordinator(new PieceAwareSupportPlaneResolver());
   private final DynamicUndergroundPlacementCoordinator dynamicCoordinator = new DynamicUndergroundPlacementCoordinator(
      new MineshaftAnchorStrategy(new MineshaftAnchorResolver()), new JigsawAnchorResolver()
   );
   private final MineshaftPlacementCoordinator mineshaftCoordinator = new MineshaftPlacementCoordinator(new MineshaftAnchorResolver());

   @Override
   public PlacementDecision place(PlacementRequest request) {
      if (!StructureRegistryGuard.canCommit(request, "underground_v2")) {
         return PlacementDecision.rejected("unregistered_structure", "unregistered_structure");
      }

      StructureSupportContext context = new StructureSupportContext(request.structureId(), request.settings(), request.islandField());
      UndergroundPlacementBehavior behavior = request.settings()
         .advanced()
         .structurePlacementPolicy()
         .undergroundBehaviorFor(request.structureId(), request.structure());
      UndergroundStrategySelector.Strategy strategy = this.strategySelector.select(behavior, request.structureId());
      UndergroundCandidatePlanner.AnchorHint anchorHint = this.candidatePlanner
         .anchorHint(request.islandAwareTarget(), request.chunkPos().getMiddleBlockX(), request.chunkPos().getMiddleBlockZ());

      return switch (strategy) {
         case STATIC_FOOTPRINT -> this.runStatic(request, context, strategy);
         case DYNAMIC_MINESHAFT -> this.runDynamic(request, context, strategy, false, anchorHint);
         case DYNAMIC_JIGSAW -> this.runDynamic(request, context, strategy, true, anchorHint);
      };
   }

   private PlacementDecision runStatic(PlacementRequest request, StructureSupportContext context, UndergroundStrategySelector.Strategy strategy) {
      UndergroundPlacementDecision decision = this.staticCoordinator
         .decide(context, request.structureStart(), request.islandAwareTarget(), request.chunk().getMinBuildHeight(), request.settings().terrain().maxIslandY());
      UndergroundConstraintEvaluator.ConstraintSummary constraints = this.constraintEvaluator.summarize(decision);
      UndergroundCandidateRanker.RankSummary rank = this.candidateRanker.summarize(decision);
      if (!decision.accepted()) {
         this.telemetry
            .rejected(
               request.structureId(),
               request.chunkPos(),
               strategy,
               "underground_v2_static_rejected",
               decision.rejectionReason(),
               request.islandAwareTarget(),
               decision.candidatesGenerated(),
               decision.candidatesPruned(),
               decision.candidatesEvaluated(),
               decision.samplesEvaluated(),
               constraints,
               rank
            );
         return PlacementDecision.rejected("underground_v2_static_rejected", decision.rejectionReason());
      }

      StructureStart committed = this.committer.commitStatic(request, request.structureStart(), decision.target(), decision.verticalDelta());
      if (committed == null) {
         return PlacementDecision.rejected("unregistered_structure", "unregistered_structure");
      }

      this.telemetry
         .accepted(
            request.structureId(),
            request.chunkPos(),
            strategy,
            "underground_v2_static_accepted",
            request.islandAwareTarget(),
            decision.target(),
            decision.verticalDelta(),
            decision.candidatesGenerated(),
            decision.candidatesPruned(),
            decision.candidatesEvaluated(),
            decision.samplesEvaluated(),
            constraints,
            rank
         );
      return PlacementDecision.accepted("underground_v2_static_accepted", "strategy=STATIC_FOOTPRINT", committed);
   }

   private PlacementDecision runDynamic(
      PlacementRequest request,
      StructureSupportContext context,
      UndergroundStrategySelector.Strategy strategy,
      boolean useJigsawAnchor,
      UndergroundCandidatePlanner.AnchorHint anchorHint
   ) {
      MineshaftPlacementDecision decision = useJigsawAnchor
         ? this.dynamicCoordinator
            .decide(
               context,
               request.structureStart(),
               request.islandAwareTarget(),
               request.chunk().getMinBuildHeight(),
               request.settings().terrain().maxIslandY(),
               true
            )
         : this.mineshaftCoordinator
            .decide(
               context, request.structureStart(), request.islandAwareTarget(), request.chunk().getMinBuildHeight(), request.settings().terrain().maxIslandY()
            );
      UndergroundConstraintEvaluator.ConstraintSummary constraints = this.constraintEvaluator.summarize(decision);
      UndergroundCandidateRanker.RankSummary rank = this.candidateRanker.summarize(decision);
      if (!decision.accepted()) {
         this.telemetry
            .rejected(
               request.structureId(),
               request.chunkPos(),
               strategy,
               "underground_v2_dynamic_rejected",
               decision.rejectionReason(),
               request.islandAwareTarget(),
               decision.candidatesGenerated(),
               decision.candidatesPruned(),
               decision.candidatesEvaluated(),
               decision.samplesEvaluated(),
               constraints,
               rank
            );
         return PlacementDecision.rejected(
            "underground_v2_dynamic_rejected",
            "reason=" + decision.rejectionReason() + ",anchor=(" + anchorHint.preferredX() + "," + anchorHint.preferredZ() + ")"
         );
      }

      StructureStart committed = this.committer.commitDynamic(request, request.structureStart(), decision.target(), decision.verticalDelta());
      if (committed == null) {
         return PlacementDecision.rejected("unregistered_structure", "unregistered_structure");
      }

      this.telemetry
         .accepted(
            request.structureId(),
            request.chunkPos(),
            strategy,
            "underground_v2_dynamic_accepted",
            request.islandAwareTarget(),
            decision.target(),
            decision.verticalDelta(),
            decision.candidatesGenerated(),
            decision.candidatesPruned(),
            decision.candidatesEvaluated(),
            decision.samplesEvaluated(),
            constraints,
            rank
         );
      return PlacementDecision.accepted(
         "underground_v2_dynamic_accepted", "strategy=" + strategy + ",anchor=(" + anchorHint.preferredX() + "," + anchorHint.preferredZ() + ")", committed
      );
   }
}
