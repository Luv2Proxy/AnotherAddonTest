package org.sathrek.sky_archipelago.worldgen.generator.structure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandConfig;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.WorldgenPerformanceMetrics;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementDecision;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.PreAnchorPlacementContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureStartRelocator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.policy.StructurePlacementPolicies;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.RelocatedStructureLocateIndex;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.ground.IglooPlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.ground.PreStartHostIslandRetryPlanner;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.ground.SmallGroundStructurePlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.sky.SkyV2PlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.stronghold.StrongholdPlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground.UndergroundV2PlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.GroundVillagePlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillageIslandBoundsEvaluator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillagePieceClassifier;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillagePreAnchorPlanner;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.WaterV2PlacementEngine;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument.OceanMonumentPlacementEngine;
import org.sathrek.sky_archipelago.worldgen.structure.PieceAwareSupportPlaneResolver;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportValidator;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementResolver;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.LandRefinementResult;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementFailureDiagnostics;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.PlacementResult;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.RejectedHostCandidate;
import org.sathrek.sky_archipelago.worldgen.structure.sky.model.RejectedSkyCandidate;
import org.sathrek.sky_archipelago.worldgen.structure.underground.UndergroundPlacementBehavior;

public final class StructurePlacementOrchestrator {
   private static final int WHITELIST_VOID_GUARD_RADIUS_BLOCKS = 96;
   private static final int WHITELIST_VOID_GUARD_STEP_BLOCKS = 16;
   private static final int WHITELIST_VOID_GUARD_MIN_NEARBY_ISLAND_COLUMNS = 1;
   private static final PieceAwareSupportPlaneResolver SUPPORT_PLANE_RESOLVER = new PieceAwareSupportPlaneResolver();
   private static final StructureSupportValidator STRUCTURE_SUPPORT_VALIDATOR = new StructureSupportValidator(SUPPORT_PLANE_RESOLVER);
   private static final SkyStructurePlacementResolver SKY_STRUCTURE_PLACEMENT_RESOLVER = new SkyStructurePlacementResolver(SUPPORT_PLANE_RESOLVER);
   private static final SmallGroundStructurePlacementEngine SMALL_GROUND_STRUCTURE_PLACEMENT_ENGINE = new SmallGroundStructurePlacementEngine();
   private static final IglooPlacementEngine IGLOO_PLACEMENT_ENGINE = new IglooPlacementEngine();
   private static final GroundVillagePlacementEngine GROUND_VILLAGE_PLACEMENT_ENGINE = new GroundVillagePlacementEngine();
   private static final VillagePreAnchorPlanner VILLAGE_PRE_ANCHOR_PLANNER = new VillagePreAnchorPlanner();
   private static final VillagePieceClassifier VILLAGE_PIECE_CLASSIFIER = new VillagePieceClassifier();
   private static final VillageIslandBoundsEvaluator VILLAGE_BOUNDS_EVALUATOR = new VillageIslandBoundsEvaluator();
   private static final SkyV2PlacementEngine SKY_V2_PLACEMENT_ENGINE = new SkyV2PlacementEngine(STRUCTURE_SUPPORT_VALIDATOR);
   private static final UndergroundV2PlacementEngine UNDERGROUND_V2_PLACEMENT_ENGINE = new UndergroundV2PlacementEngine();
   private static final WaterV2PlacementEngine WATER_V2_PLACEMENT_ENGINE = new WaterV2PlacementEngine();
   private static final OceanMonumentPlacementEngine OCEAN_MONUMENT_PLACEMENT_ENGINE = new OceanMonumentPlacementEngine();
   private static final StrongholdPlacementEngine STRONGHOLD_PLACEMENT_ENGINE = new StrongholdPlacementEngine(
      SUPPORT_PLANE_RESOLVER, STRUCTURE_SUPPORT_VALIDATOR
   );
   private static final PreStartHostIslandRetryPlanner PRE_START_HOST_ISLAND_RETRY_PLANNER = new PreStartHostIslandRetryPlanner();
   private static final StructureStartRelocator STRUCTURE_START_RELOCATOR = new StructureStartRelocator();
   private static final TerrainIntrusionAnalyzer TERRAIN_INTRUSION_ANALYZER = new TerrainIntrusionAnalyzer();
   private static final int LAND_SUMMARY_INTERVAL = 100;
   private static final AtomicInteger LAND_ATTEMPT_COUNT = new AtomicInteger();
   private static final AtomicInteger LAND_SMALL_SUPPRESSED_COUNT = new AtomicInteger();
   private static final Map<String, AtomicInteger> LAND_STAGE_COUNTS = new ConcurrentHashMap<>();
   private static final Map<String, AtomicInteger> LAND_ID_COUNTS = new ConcurrentHashMap<>();

   private StructurePlacementOrchestrator() {
   }

   public static void createStructures(
      SkyIslandChunkGenerator generator,
      BiomeSource biomeSource,
      RegistryAccess registryAccess,
      ChunkGeneratorStructureState structureState,
      StructureManager structureManager,
      ChunkAccess chunk,
      StructureTemplateManager templateManager,
      SkyIslandSettings settings,
      IslandField islandField
   ) {
      long passStartNanos = System.nanoTime();
      ChunkPos chunkPos = chunk.getPos();
      SectionPos sectionPos = SectionPos.bottomOf(chunk);
      RandomState randomState = structureState.randomState();
      structureState.possibleStructureSets()
         .forEach(
            holder -> {
               List<StructureSelectionEntry> entries = ((StructureSet)holder.value()).structures();

               for (StructureSelectionEntry entry : entries) {
                  StructureStart existing = structureManager.getStartForStructure(sectionPos, (Structure)entry.structure().value(), chunk);
                  if (existing != null && existing.isValid()) {
                     return;
                  }
               }

               if (((StructureSet)holder.value()).placement().isStructureChunk(structureState, chunkPos.x, chunkPos.z)) {
                  if (entries.size() == 1) {
                     tryGenerateValidatedStructure(
                        generator,
                        biomeSource,
                        entries.get(0),
                        structureManager,
                        registryAccess,
                        randomState,
                        templateManager,
                        structureState.getLevelSeed(),
                        chunk,
                        chunkPos,
                        sectionPos,
                        islandField,
                        settings
                     );
                  } else {
                     ArrayList<StructureSelectionEntry> weightedEntries = new ArrayList<>(entries);
                     WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
                     random.setLargeFeatureSeed(structureState.getLevelSeed(), chunkPos.x, chunkPos.z);
                     int totalWeight = weightedEntries.stream().mapToInt(StructureSelectionEntry::weight).sum();

                     while (!weightedEntries.isEmpty()) {
                        int selectedWeight = random.nextInt(totalWeight);

                        int pickedIndex;
                        for (pickedIndex = 0; pickedIndex < weightedEntries.size(); pickedIndex++) {
                           selectedWeight -= weightedEntries.get(pickedIndex).weight();
                           if (selectedWeight < 0) {
                              break;
                           }
                        }

                        StructureSelectionEntry picked = weightedEntries.get(pickedIndex);
                        if (tryGenerateValidatedStructure(
                           generator,
                           biomeSource,
                           picked,
                           structureManager,
                           registryAccess,
                           randomState,
                           templateManager,
                           structureState.getLevelSeed(),
                           chunk,
                           chunkPos,
                           sectionPos,
                           islandField,
                           settings
                        )) {
                           return;
                        }

                        weightedEntries.remove(pickedIndex);
                        totalWeight -= picked.weight();
                     }
                  }
               }
            }
         );
      WorldgenPerformanceMetrics.recordStructurePass(System.nanoTime() - passStartNanos);
   }

   private static boolean tryGenerateValidatedStructure(
      SkyIslandChunkGenerator generator,
      BiomeSource biomeSource,
      StructureSelectionEntry entry,
      StructureManager structureManager,
      RegistryAccess registryAccess,
      RandomState randomState,
      StructureTemplateManager templateManager,
      long levelSeed,
      ChunkAccess chunk,
      ChunkPos chunkPos,
      SectionPos sectionPos,
      IslandField islandField,
      SkyIslandSettings settings
   ) {
      Structure structure = (Structure)entry.structure().value();
      Optional<ResourceLocation> registeredStructureId = StructureRegistryGuard.registeredStructureId(registryAccess, structure);
      if (registeredStructureId.isEmpty()) {
         logStructureVerbose("Rejected unregistered structure in chunk [{}, {}]: rejectionStage=unregistered_structure", chunkPos.x, chunkPos.z);
         return false;
      }

      ResourceLocation structureId = registeredStructureId.get();
      StructurePlacementCategory configuredCategory = settings.advanced().structurePlacementPolicy().categoryFor(structureId);
      if (settings.advanced().structurePlacementPolicy().isDenied(structureId)) {
         logStructureVerbose("Skipped denylisted structure {} in chunk [{}, {}]", structureId, chunkPos.x, chunkPos.z);
         return false;
      }

      int references = fetchReferences(structureManager, chunk, sectionPos, structure);
      PlacementRequest.StructureGenerationContext generationContext = new PlacementRequest.StructureGenerationContext(
         generator, biomeSource, registryAccess, randomState, templateManager, references
      );
      if (OceanMonumentPlacementEngine.supports(structureId)) {
         PlacementDecision monumentDecision = OCEAN_MONUMENT_PLACEMENT_ENGINE.place(
            new PlacementRequest(
               structure,
               structureId,
               StructureStart.INVALID_START,
               null,
               structureManager,
               sectionPos,
               chunk,
               chunkPos,
               settings,
               islandField,
               levelSeed,
               null,
               null,
               Level.OVERWORLD,
               generationContext
            )
         );
         if (!monumentDecision.accepted()) {
            logStructureVerbose(
               "Rejected ocean monument {} in chunk [{}, {}]: rejectionStage={}, details={}",
               structureId,
               chunkPos.x,
               chunkPos.z,
               monumentDecision.stage(),
               monumentDecision.details()
            );
            return false;
         } else {
            logStructureVerbose(
               "Accepted ocean monument {} in chunk [{}, {}]: acceptanceStage={}, details={}",
               structureId,
               chunkPos.x,
               chunkPos.z,
               monumentDecision.stage(),
               monumentDecision.details()
            );
            return true;
         }
      } else {
         StructureStart structureStart = structure.generate(
            registryAccess, generator, biomeSource, randomState, templateManager, levelSeed, chunkPos, references, chunk, structure.biomes()::contains
         );
         PreAnchorPlacementContext preAnchorPlacementContext = null;
         if (settings.advanced().customStructureRulesEnabled() && isVanillaVillage(structureId)) {
            StructureFootprint probeFootprint = structureStart.isValid()
               ? new StructureFootprint(
                  structureStart.getBoundingBox().minX(),
                  structureStart.getBoundingBox().maxX(),
                  structureStart.getBoundingBox().minZ(),
                  structureStart.getBoundingBox().maxZ()
               )
               : null;
            VillagePreAnchorPlanner.Plan preAnchorPlan = VILLAGE_PRE_ANCHOR_PLANNER.plan(
               structureId, chunkPos, StructurePlacementPolicies.GROUND_VILLAGE, probeFootprint, islandField, settings
            );
            if (preAnchorPlan.anchors().isEmpty()) {
               logStructureVerbose(
                  "Rejected ground village {} in chunk [{}, {}]: rejectionStage=pre_anchor_no_hosts, previews={}, requiredRadiusStart={}, requiredRadiusFinal={}, relaxationSteps={}, hostRejections={}",
                  structureId,
                  chunkPos.x,
                  chunkPos.z,
                  preAnchorPlan.previewCount(),
                  preAnchorPlan.initialRequiredRadius(),
                  preAnchorPlan.requiredRadius(),
                  preAnchorPlan.relaxationSteps(),
                  preAnchorPlan.rejections()
               );
               return false;
            }

            ArrayList<ChunkPos> attemptedAnchors = new ArrayList<>();
            VillagePreAnchorPlanner.Anchor winningAnchor = null;
            StructureStart winningStart = null;
            int attemptsUsed = 0;
            Iterator overlapPrecheckRejectedCount = preAnchorPlan.anchors().iterator();

            while (true) {
               if (overlapPrecheckRejectedCount.hasNext()) {
                  VillagePreAnchorPlanner.Anchor anchor = (VillagePreAnchorPlanner.Anchor)overlapPrecheckRejectedCount.next();
                  attemptsUsed++;
                  attemptedAnchors.add(anchor.chunkPos());
                  StructureStart attemptStart = structure.generate(
                     registryAccess,
                     generator,
                     biomeSource,
                     randomState,
                     templateManager,
                     levelSeed,
                     anchor.chunkPos(),
                     references,
                     chunk,
                     structure.biomes()::contains
                  );
                  int centerCount = countVillagePiecesOfKind(attemptStart, VillagePieceClassifier.PieceKind.CENTER);
                  int buildingCount = countVillagePiecesOfKind(attemptStart, VillagePieceClassifier.PieceKind.BUILDING);
                  boolean boundsFit = attemptStart.isValid() && VILLAGE_BOUNDS_EVALUATOR.fits(anchor.host(), attemptStart.getBoundingBox());
                  if (!attemptStart.isValid() || centerCount <= 0 || buildingCount <= 0 || !boundsFit) {
                     logStructureVerbose(
                        "Ground village pre-anchor attempt {} {} sourceChunk=[{}, {}] attemptAnchorChunk=[{}, {}] host=({}, {}, {}; radius={}; usable={}) generatedPieces={} centerCount={} buildingCount={} rejectionStage={}",
                        attemptsUsed,
                        structureId,
                        chunkPos.x,
                        chunkPos.z,
                        anchor.chunkPos().x,
                        anchor.chunkPos().z,
                        anchor.host().preview().x(),
                        anchor.host().preview().y(),
                        anchor.host().preview().z(),
                        anchor.host().preview().radius(),
                        anchor.host().usableRadius(),
                        attemptStart.isValid() ? attemptStart.getPieces().size() : 0,
                        centerCount,
                        buildingCount,
                        !attemptStart.isValid() ? "invalid_start" : (centerCount > 0 && buildingCount > 0 ? "bounds_not_fit_host" : "missing_village_nucleus")
                     );
                     continue;
                  }

                  winningAnchor = anchor;
                  winningStart = attemptStart;
               }

               if (winningAnchor == null || winningStart == null) {
                  logStructureVerbose(
                     "Rejected ground village {} in chunk [{}, {}]: rejectionStage=pre_anchor_all_attempts_failed, attempts={}, attemptedAnchorChunks={}",
                     structureId,
                     chunkPos.x,
                     chunkPos.z,
                     attemptsUsed,
                     attemptedAnchors
                  );
                  return false;
               }

               structureStart = winningStart;
               preAnchorPlacementContext = new PreAnchorPlacementContext(
                  winningAnchor.host(), chunkPos, winningAnchor.chunkPos(), attemptsUsed, List.copyOf(attemptedAnchors)
               );
               logStructureVerbose(
                  "Ground village pre-anchor selected {} sourceChunk=[{}, {}] winningAnchorChunk=[{}, {}] attemptsUsed={} generatedPieces={} centerCount={} buildingCount={} requiredRadiusStart={} requiredRadiusFinal={} relaxationSteps={} host=({}, {}, {}; radius={}; usable={})",
                  structureId,
                  chunkPos.x,
                  chunkPos.z,
                  winningAnchor.chunkPos().x,
                  winningAnchor.chunkPos().z,
                  attemptsUsed,
                  structureStart.getPieces().size(),
                  countVillagePiecesOfKind(structureStart, VillagePieceClassifier.PieceKind.CENTER),
                  countVillagePiecesOfKind(structureStart, VillagePieceClassifier.PieceKind.BUILDING),
                  preAnchorPlan.initialRequiredRadius(),
                  preAnchorPlan.requiredRadius(),
                  preAnchorPlan.relaxationSteps(),
                  winningAnchor.host().preview().x(),
                  winningAnchor.host().preview().y(),
                  winningAnchor.host().preview().z(),
                  winningAnchor.host().preview().radius(),
                  winningAnchor.host().usableRadius()
               );
               break;
            }
         }

         if (configuredCategory == StructurePlacementCategory.STRONGHOLD && SkyIslandServerConfig.structureDebugEnabled()) {
            SkyArchipelago.LOGGER
               .info(
                  "Stronghold-category generation attempt id={} chunk=[{}, {}] validStart={} references={}",
                  new Object[]{structureId, chunkPos.x, chunkPos.z, structureStart.isValid(), references}
               );
         }

         if (!structureStart.isValid()) {
            if (!PRE_START_HOST_ISLAND_RETRY_PLANNER.isEligible(structureId, configuredCategory)) {
               if (isV2SmallGroundCategory(configuredCategory)) {
                  logStructureVerbose(
                     "V2 rejected invalid generated start for {} in chunk [{}, {}]: category={}", structureId, chunkPos.x, chunkPos.z, configuredCategory
                  );
               }

               return false;
            }

            List<PreStartHostIslandRetryPlanner.RetryAnchor> retryAnchors = PRE_START_HOST_ISLAND_RETRY_PLANNER.selectRetryAnchors(
               structureId, configuredCategory, settings, islandField, chunkPos
            );
            logStructureVerbose(
               "Pre-start host retry planned for {} in chunk [{}, {}]: category={}, teleportHint={}, candidateCount={}, selectedAnchors={}",
               structureId,
               chunkPos.x,
               chunkPos.z,
               configuredCategory,
               formatChunkTeleportHint(chunkPos, settings),
               retryAnchors.size(),
               retryAnchors.stream()
                  .map(
                     anchor -> "("
                        + anchor.chunkPos().x
                        + ","
                        + anchor.chunkPos().z
                        + "; radius="
                        + anchor.preview().radius()
                        + ", stable="
                        + anchor.stableTopCells()
                        + ", distanceSq="
                        + anchor.distanceSquared()
                        + ")"
                  )
                  .toList()
            );
            PreStartHostIslandRetryPlanner.RetryExecution<StructureStart> retryExecution = PRE_START_HOST_ISLAND_RETRY_PLANNER.executeRetries(
               retryAnchors,
               anchor -> {
                  StructureStart retryStart = structure.generate(
                     registryAccess,
                     generator,
                     biomeSource,
                     randomState,
                     templateManager,
                     levelSeed,
                     anchor.chunkPos(),
                     references,
                     chunk,
                     structure.biomes()::contains
                  );
                  logStructureVerbose(
                     "Pre-start host retry attempt for {} from chunk [{}, {}]: retryAnchor=({}, {}), retryAnchorTeleportHint={}, validStart={}, bounds={}, startCenter={}",
                     structureId,
                     chunkPos.x,
                     chunkPos.z,
                     anchor.chunkPos().x,
                     anchor.chunkPos().z,
                     formatChunkTeleportHint(anchor.chunkPos(), settings),
                     retryStart.isValid(),
                     retryStart.isValid() ? formatBoundingBox(retryStart.getBoundingBox()) : "invalid",
                     retryStart.isValid()
                        ? "("
                           + Mth.floor((retryStart.getBoundingBox().minX() + retryStart.getBoundingBox().maxX()) * 0.5)
                           + ","
                           + Mth.floor((retryStart.getBoundingBox().minY() + retryStart.getBoundingBox().maxY()) * 0.5)
                           + ","
                           + Mth.floor((retryStart.getBoundingBox().minZ() + retryStart.getBoundingBox().maxZ()) * 0.5)
                           + ")"
                        : "none"
                  );
                  return retryStart;
               },
               StructureStart::isValid
            );
            if (!retryExecution.succeeded()) {
               logStructureVerbose(
                  "Pre-start host retry failed for {} in chunk [{}, {}]: category={}, teleportHint={}, attempts={}, candidateCount={}",
                  structureId,
                  chunkPos.x,
                  chunkPos.z,
                  configuredCategory,
                  formatChunkTeleportHint(chunkPos, settings),
                  retryExecution.attempts(),
                  retryAnchors.size()
               );
               return false;
            }

            structureStart = PRE_START_HOST_ISLAND_RETRY_PLANNER.rebindStructureStartChunk(retryExecution.result(), chunkPos);
            logStructureVerbose(
               "Pre-start host retry succeeded for {} in chunk [{}, {}]: category={}, teleportHint={}, retryAnchor=({}, {}), retryAnchorTeleportHint={}, attempts={}",
               structureId,
               chunkPos.x,
               chunkPos.z,
               configuredCategory,
               formatChunkTeleportHint(chunkPos, settings),
               retryExecution.winningAnchor().chunkPos().x,
               retryExecution.winningAnchor().chunkPos().z,
               formatChunkTeleportHint(retryExecution.winningAnchor().chunkPos(), settings),
               retryExecution.attempts()
            );
         }

         if (settings.advanced().customStructureRulesEnabled()) {
            StructureSupportContext supportContext = new StructureSupportContext(structureId, settings, islandField);
            ResolvedStructureSupportPlane effectiveSupportPlane = SUPPORT_PLANE_RESOLVER.resolve(
                  structureId, structureStart, settings.advanced().structurePlacementPolicy().footprintInsetRatioFor(structureId)
               )
               .orElse(null);
            StructurePlacementCategory category = settings.advanced().structurePlacementPolicy().effectiveCategoryFor(structureId, effectiveSupportPlane);
            if (category == StructurePlacementCategory.GROUND_VILLAGE) {
               if (!structureStart.isValid()) {
                  return false;
               } else {
                  PlacementDecision villageDecision = GROUND_VILLAGE_PLACEMENT_ENGINE.place(
                     new PlacementRequest(
                        structure,
                        structureId,
                        structureStart,
                        effectiveSupportPlane,
                        structureManager,
                        sectionPos,
                        chunk,
                        chunkPos,
                        settings,
                        islandField,
                        levelSeed,
                        preAnchorPlacementContext,
                        null,
                        Level.OVERWORLD,
                        generationContext
                     )
                  );
                  if (!villageDecision.accepted()) {
                     logStructureVerbose(
                        "Rejected ground village structure {} in chunk [{}, {}]: rejectionStage={}, details={}",
                        structureId,
                        chunkPos.x,
                        chunkPos.z,
                        villageDecision.stage(),
                        villageDecision.details()
                     );
                     return false;
                  } else {
                     logStructureVerbose(
                        "Accepted ground village structure {} in chunk [{}, {}]: acceptanceStage={}, details={}",
                        structureId,
                        chunkPos.x,
                        chunkPos.z,
                        villageDecision.stage(),
                        villageDecision.details()
                     );
                     return true;
                  }
               }
            } else if (category == StructurePlacementCategory.WATER) {
               PlacementDecision waterDecision = WATER_V2_PLACEMENT_ENGINE.place(
                  new PlacementRequest(
                     structure,
                     structureId,
                     structureStart,
                     effectiveSupportPlane,
                     structureManager,
                     sectionPos,
                     chunk,
                     chunkPos,
                     settings,
                     islandField,
                     levelSeed,
                     null,
                     null,
                     Level.OVERWORLD,
                     generationContext
                  )
               );
               if (!waterDecision.accepted()) {
                  logStructureVerbose(
                     "Rejected water structure {} in chunk [{}, {}]: rejectionStage={}, details={}",
                     structureId,
                     chunkPos.x,
                     chunkPos.z,
                     waterDecision.stage(),
                     waterDecision.details()
                  );
                  return false;
               } else {
                  logStructureVerbose(
                     "Accepted water structure {} in chunk [{}, {}]: acceptanceStage={}, details={}",
                     structureId,
                     chunkPos.x,
                     chunkPos.z,
                     waterDecision.stage(),
                     waterDecision.details()
                  );
                  return true;
               }
            } else if (shouldRouteIglooToAnchorFirst(structureId, category)) {
               PlacementDecision iglooDecision = IGLOO_PLACEMENT_ENGINE.place(
                  new PlacementRequest(
                     structure,
                     structureId,
                     structureStart,
                     effectiveSupportPlane,
                     structureManager,
                     sectionPos,
                     chunk,
                     chunkPos,
                     settings,
                     islandField,
                     levelSeed,
                     null,
                     null,
                     Level.OVERWORLD,
                     generationContext
                  ),
                  category
               );
               if (!iglooDecision.accepted()) {
                  logStructureVerbose(
                     "Rejected igloo {} in chunk [{}, {}]: rejectionStage={}, details={}",
                     structureId,
                     chunkPos.x,
                     chunkPos.z,
                     iglooDecision.stage(),
                     iglooDecision.details()
                  );
                  return false;
               } else {
                  logStructureVerbose(
                     "Accepted igloo {} in chunk [{}, {}]: acceptanceStage={}, details={}",
                     structureId,
                     chunkPos.x,
                     chunkPos.z,
                     iglooDecision.stage(),
                     iglooDecision.details()
                  );
                  return true;
               }
            } else if (shouldRouteSkyToV2(structureId, configuredCategory, category, settings)) {
               PlacementDecision skyDecision = SKY_V2_PLACEMENT_ENGINE.place(
                  new PlacementRequest(
                     structure,
                     structureId,
                     structureStart,
                     effectiveSupportPlane,
                     structureManager,
                     sectionPos,
                     chunk,
                     chunkPos,
                     settings,
                     islandField,
                     levelSeed,
                     null,
                     null,
                     Level.OVERWORLD,
                     generationContext
                  )
               );
               if (!skyDecision.accepted()) {
                  logStructureVerbose(
                     "Rejected structure {} in chunk [{}, {}]: rejectionStage={}, category={}, details={}",
                     structureId,
                     chunkPos.x,
                     chunkPos.z,
                     skyDecision.stage(),
                     category,
                     skyDecision.details()
                  );
                  return false;
               } else {
                  logStructureVerbose(
                     "Accepted structure {} in chunk [{}, {}]: acceptanceStage={}, category=SKY, details={}",
                     structureId,
                     chunkPos.x,
                     chunkPos.z,
                     skyDecision.stage(),
                     skyDecision.details()
                  );
                  return true;
               }
            } else if ((isV2SmallGroundCategory(category) || category == StructurePlacementCategory.DEFAULT)
               && !settings.advanced().structureWhitelist().isWhitelisted(structureId)) {
               StructurePlacementCategory v2Category = category;
               if (v2Category == StructurePlacementCategory.DEFAULT) {
                  if (effectiveSupportPlane == null || effectiveSupportPlane.effectiveFootprint() == null) {
                     logStructureVerbose(
                        "V2 rejected {} in chunk [{}, {}]: rejectionStage=missing_support_plane_for_default_category", structureId, chunkPos.x, chunkPos.z
                     );
                     return false;
                  }

                  v2Category = settings.advanced()
                     .structurePlacementPolicy()
                     .effectiveCategoryFor(structureId, effectiveSupportPlane.effectiveFootprint().area());
               }

               return SMALL_GROUND_STRUCTURE_PLACEMENT_ENGINE.place(
                  structure,
                  structureId,
                  v2Category,
                  structureStart,
                  effectiveSupportPlane,
                  structureManager,
                  sectionPos,
                  chunk,
                  chunkPos,
                  settings,
                  islandField,
                  levelSeed,
                  registryAccess
               );
            } else if (!settings.advanced().structureWhitelist().isWhitelisted(structureId)) {
               PlacementResult placementResult = SKY_STRUCTURE_PLACEMENT_RESOLVER.resolvePlacement(supportContext, structureStart, chunkPos);
               logStructureVerbose(
                  "Island-aware placement telemetry for {} in chunk [{}, {}]: fallbackUsed={}, qualifiedHosts={}, attemptedHosts={}, coarseOffsetsEvaluated={}, fineOffsetsEvaluated={}, hostCapHit={}, offsetCapHit={}",
                  structureId,
                  chunkPos.x,
                  chunkPos.z,
                  placementResult.islandCenteredFallbackUsed(),
                  placementResult.qualifiedHosts(),
                  placementResult.attemptedHosts(),
                  placementResult.coarseOffsetsEvaluated(),
                  placementResult.fineOffsetsEvaluated(),
                  placementResult.hostAttemptCapHit(),
                  placementResult.offsetCapHit()
               );
               boolean overlapChecked = false;
               int overlapRejectedCount = 0;
               int overlapPrecheckRejectedCount = 0;
               int commitConflictCount = 0;
               int overlapWinningCandidateRank = -1;
               boolean overlapCandidateCapHit = false;
               String reservationMode = "commit_on_accept";
               LandRefinementResult landRefinement = LandRefinementResult.notAttempted("land_not_attempted");
               BlockPos vanillaLocatePos = centerOf(structureStart.getBoundingBox());
               boolean relocatedByIslandAware = false;
               if (placementResult.attempted()) {
                  if (!placementResult.successful()) {
                     if (category != StructurePlacementCategory.UNDERGROUND
                        && category != StructurePlacementCategory.STRONGHOLD
                        && category != StructurePlacementCategory.WATER) {
                        PlacementFailureDiagnostics failureDiagnostics = SKY_STRUCTURE_PLACEMENT_RESOLVER.diagnoseRejectedPlacement(
                           supportContext, structureStart, chunkPos
                        );
                        logStructureVerbose(
                           "Rejected structure {} in chunk [{}, {}]: rejectionStage={}, category={}, teleportHint={}, islandAwareAttempted=true, islandAwareResult={}, searchRadiusBlocks={}, failureDiagnostics={totalPreviews={}, qualifiedPreviews={}, rejectedHosts={}, rejectedCandidates={}}",
                           structureId,
                           chunkPos.x,
                           chunkPos.z,
                           "island_aware_candidate_search",
                           category,
                           formatChunkTeleportHint(chunkPos, settings),
                           placementResult.failureReason(),
                           settings.advanced().structurePlacementPolicy().searchRadiusChunksForCategory(category) * 16,
                           failureDiagnostics.totalPreviews(),
                           failureDiagnostics.qualifiedPreviews(),
                           formatRejectedHostCandidates(failureDiagnostics.rejectedHosts()),
                           formatRejectedSkyCandidates(failureDiagnostics.rejectedCandidates())
                        );
                        return false;
                     }

                     logStructureVerbose(
                        "{} structure {} in chunk [{}, {}] continuing without islandAwareTarget: failure={}, searchRadiusBlocks={}",
                        category,
                        structureId,
                        chunkPos.x,
                        chunkPos.z,
                        placementResult.failureReason(),
                        settings.advanced().structurePlacementPolicy().searchRadiusChunksForCategory(category) * 16
                     );
                  }

                  if (placementResult.successful()) {
                     structureStart = STRUCTURE_START_RELOCATOR.relocate(
                        structureStart, chunkPos, placementResult.rawFootprint(), placementResult.supportPlane(), placementResult.target()
                     );
                     relocatedByIslandAware = true;
                  }
               }

               if (shouldRouteStrongholdToHostIslandFlow(category, SkyIslandConfig.strongholdHostIslandEnabled())) {
                  PlacementDecision strongholdDecision = STRONGHOLD_PLACEMENT_ENGINE.place(
                     new PlacementRequest(
                        structure,
                        structureId,
                        structureStart,
                        effectiveSupportPlane,
                        structureManager,
                        sectionPos,
                        chunk,
                        chunkPos,
                        settings,
                        islandField,
                        levelSeed,
                        null,
                        placementResult.target(),
                        Level.OVERWORLD,
                        generationContext
                     )
                  );
                  return strongholdDecision.accepted();
               }

               if (category == StructurePlacementCategory.UNDERGROUND) {
                  PlacementDecision undergroundDecision = UNDERGROUND_V2_PLACEMENT_ENGINE.place(
                     new PlacementRequest(
                        structure,
                        structureId,
                        structureStart,
                        effectiveSupportPlane,
                        structureManager,
                        sectionPos,
                        chunk,
                        chunkPos,
                        settings,
                        islandField,
                        levelSeed,
                        null,
                        placementResult.target(),
                        Level.OVERWORLD,
                        generationContext
                     )
                  );
                  if (!undergroundDecision.accepted()) {
                     return false;
                  }

                  structureStart = undergroundDecision.structureStart();
                  return true;
               } else {
                  StructureSupportValidator.SupportReport supportReport = STRUCTURE_SUPPORT_VALIDATOR.evaluatePlacement(
                     supportContext, structureStart, category
                  );
                  TerrainIntrusionAnalyzer.TerrainIntrusionReport intrusionReport = TERRAIN_INTRUSION_ANALYZER.analyze(
                     category, structureStart, islandField, settings, supportReport.resolvedBaseY()
                  );
                  String landDecisionSource = landRefinement.decisionSource();
                  if (!supportReport.accepted()) {
                     if (category == StructurePlacementCategory.STRONGHOLD && SkyIslandServerConfig.structureDebugEnabled()) {
                        SkyArchipelago.LOGGER
                           .info(
                              "Stronghold-category support rejected id={} chunk=[{}, {}] category={} support={}/{} ratio={} required={} attemptedIslandAware={}",
                              new Object[]{
                                 structureId,
                                 chunkPos.x,
                                 chunkPos.z,
                                 supportReport.category(),
                                 supportReport.supportedPoints(),
                                 supportReport.totalSamples(),
                                 formatRatio(supportReport.supportRatio()),
                                 formatRatio(supportReport.requiredRatio()),
                                 placementResult.attempted()
                              }
                           );
                     }

                     logStructureVerbose(
                        "Rejected structure {} in chunk [{}, {}]: rejectionStage={}, category={}, islandAwareAttempted={}, islandAwareTarget={}, islandAwareFailure={}, landRefineAttempted={}, landRefineMoved={}, landRefineOutcome={}, landDecisionSource={}, landSizeTier={}, landInnerAdjustRadius={}, landOuterFeatherRadius={}, budgetAdd={}, budgetRemove={}, budgetVertical={}, budgetCoreUnsupported={}, budgetEdgeExtension={}, reliefSpan={}, resolvedBaseY={}, supportPlaneFallback={}, supportSliceCount={}, supportSliceArea={}, skyAirValidated={}, skyNearbyIslands={}, skyCollisionSamples={}, skyMinClearance={}, hamletPieceSupportRejected={}, hamletPieces={}/{}, hamletRatio={}, hamletRequired={}, bounds={}, rawFootprint={}, effectiveFootprint={}, scanStartY={}, scanDepth={}, support={}/{}, ratio={}, required={}, failingSamples={}, intrusion={}, overlapChecked={}, overlapRejectedCount={}, overlapPrecheckRejectedCount={}, commitConflictCount={}, overlapWinningCandidateRank={}, overlapMode=2d_effective_footprint, reservationMode={}, overlapCandidateCapHit={}",
                        structureId,
                        chunkPos.x,
                        chunkPos.z,
                        skyAwareRejectionStage(placementResult, supportReport, landRefinement),
                        supportReport.category(),
                        placementResult.attempted(),
                        formatPlacementTarget(placementResult.target()),
                        placementResult.failureReason(),
                        landRefinement.attempted(),
                        landRefinement.moved(),
                        landRefinement.outcome(),
                        landDecisionSource,
                        landRefinement.sizeTier(),
                        landRefinement.innerAdjustRadiusBlocks(),
                        landRefinement.outerFeatherRadiusBlocks(),
                        landRefinement.blocksToAdd(),
                        landRefinement.blocksToRemove(),
                        landRefinement.verticalAdjustment(),
                        landRefinement.unsupportedCoreCells(),
                        landRefinement.edgeExtension(),
                        landRefinement.reliefSpan(),
                        supportReport.resolvedBaseY(),
                        supportReport.usedSupportPlaneFallback(),
                        supportReport.supportSliceCount(),
                        supportReport.supportSliceArea(),
                        supportReport.skyAirValidated(),
                        supportReport.skyNearbyIslandColumns(),
                        supportReport.skyCollisionSamples(),
                        supportReport.skyMinClearanceBlocks() == Integer.MAX_VALUE ? "none" : supportReport.skyMinClearanceBlocks(),
                        supportReport.hamletPieceSupportRejected(),
                        supportReport.hamletGroundedPieces(),
                        supportReport.hamletTotalPieces(),
                        formatRatio(supportReport.hamletGroundedRatio()),
                        formatRatio(supportReport.hamletRequiredRatio()),
                        formatBoundingBox(supportReport.structureBounds()),
                        formatFootprint(supportReport.rawFootprint()),
                        formatFootprint(supportReport.effectiveFootprint()),
                        supportReport.scanStartY(),
                        supportReport.scanDepth(),
                        supportReport.supportedPoints(),
                        supportReport.totalSamples(),
                        formatRatio(supportReport.supportRatio()),
                        formatRatio(supportReport.requiredRatio()),
                        formatSamples(supportReport.failingSamples()),
                        formatIntrusionSummary(intrusionReport),
                        overlapChecked,
                        overlapRejectedCount,
                        overlapPrecheckRejectedCount,
                        commitConflictCount,
                        overlapWinningCandidateRank,
                        reservationMode,
                        overlapCandidateCapHit
                     );
                     return false;
                  } else {
                     if (!suppressSmallLandVerbose(landRefinement)) {
                        logStructureVerbose(
                           "Accepted structure {} in chunk [{}, {}]: acceptanceStage={}, category={}, teleportHint={}, islandAwareAttempted={}, islandAwareTarget={}, landRefineAttempted={}, landRefineMoved={}, landRefineOutcome={}, landRefineLegacyOutcome={}, landDecisionSource={}, landSizeTier={}, landInnerAdjustRadius={}, landOuterFeatherRadius={}, originOutcome={}, originScore={}, bestObservedOutcome={}, bestObservedScore={}, bestObservedDelta={}, originOnlyEvaluated={}, primaryBudgetCounter={}, budgetAdd={}, budgetRemove={}, budgetVertical={}, budgetCoreUnsupported={}, budgetEdgeExtension={}, reliefSpan={}, resolvedBaseY={}, supportPlaneFallback={}, supportSliceCount={}, supportSliceArea={}, skyAirValidated={}, skyNearbyIslands={}, skyCollisionSamples={}, skyMinClearance={}, hamletPieces={}/{}, hamletRatio={}, bounds={}, rawFootprint={}, effectiveFootprint={}, scanStartY={}, scanDepth={}, support={}/{}, ratio={}, intrusion={}, overlapChecked={}, overlapRejectedCount={}, overlapPrecheckRejectedCount={}, commitConflictCount={}, overlapWinningCandidateRank={}, overlapMode=2d_effective_footprint, reservationMode={}, overlapCandidateCapHit={}",
                           structureId,
                           chunkPos.x,
                           chunkPos.z,
                           acceptanceStage(supportReport, placementResult, landRefinement),
                           supportReport.category(),
                           formatChunkTeleportHint(chunkPos, settings),
                           placementResult.attempted(),
                           formatPlacementTarget(placementResult.target()),
                           landRefinement.attempted(),
                           landRefinement.moved(),
                           landRefinement.outcome(),
                           landRefinement.legacyOutcome(),
                           landDecisionSource,
                           landRefinement.sizeTier(),
                           landRefinement.innerAdjustRadiusBlocks(),
                           landRefinement.outerFeatherRadiusBlocks(),
                           landRefinement.originOutcome(),
                           formatRatio(landRefinement.originScore()),
                           landRefinement.bestObservedOutcome(),
                           formatRatio(landRefinement.bestObservedScore()),
                           formatRatio(landRefinement.bestObservedDelta()),
                           landRefinement.originOnlyEvaluated(),
                           landRefinement.primaryBudgetCounter(),
                           landRefinement.blocksToAdd(),
                           landRefinement.blocksToRemove(),
                           landRefinement.verticalAdjustment(),
                           landRefinement.unsupportedCoreCells(),
                           landRefinement.edgeExtension(),
                           landRefinement.reliefSpan(),
                           supportReport.resolvedBaseY(),
                           supportReport.usedSupportPlaneFallback(),
                           supportReport.supportSliceCount(),
                           supportReport.supportSliceArea(),
                           supportReport.skyAirValidated(),
                           supportReport.skyNearbyIslandColumns(),
                           supportReport.skyCollisionSamples(),
                           supportReport.skyMinClearanceBlocks() == Integer.MAX_VALUE ? "none" : supportReport.skyMinClearanceBlocks(),
                           supportReport.hamletGroundedPieces(),
                           supportReport.hamletTotalPieces(),
                           formatRatio(supportReport.hamletGroundedRatio()),
                           formatBoundingBox(supportReport.structureBounds()),
                           formatFootprint(supportReport.rawFootprint()),
                           formatFootprint(supportReport.effectiveFootprint()),
                           supportReport.scanStartY(),
                           supportReport.scanDepth(),
                           supportReport.supportedPoints(),
                           supportReport.totalSamples(),
                           formatRatio(supportReport.supportRatio()),
                           formatIntrusionSummary(intrusionReport),
                           overlapChecked,
                           overlapRejectedCount,
                           overlapPrecheckRejectedCount,
                           commitConflictCount,
                           overlapWinningCandidateRank,
                           reservationMode,
                           overlapCandidateCapHit
                        );
                     }

                     recordLandSummary(structureId, landRefinement);
                     if (category == StructurePlacementCategory.STRONGHOLD && SkyIslandServerConfig.structureDebugEnabled()) {
                        SkyArchipelago.LOGGER
                           .info(
                              "Stronghold-category accepted id={} chunk=[{}, {}] stage={} support={}/{} ratio={}",
                              new Object[]{
                                 structureId,
                                 chunkPos.x,
                                 chunkPos.z,
                                 acceptanceStage(supportReport, placementResult, landRefinement),
                                 supportReport.supportedPoints(),
                                 supportReport.totalSamples(),
                                 formatRatio(supportReport.supportRatio())
                              }
                           );
                     }

                     broadcastAcceptedStructureDebug(structureId, structureStart, supportReport.resolvedBaseY());
                     if (!StructureRegistryGuard.canCommit(structureId, structure, registryAccess, "legacy_island_aware", chunkPos)) {
                        return false;
                     }

                     structureManager.setStartForStructure(sectionPos, structure, structureStart, chunk);
                     if (relocatedByIslandAware) {
                        RelocatedStructureLocateIndex.recordCommittedRelocation(
                           structureId, Level.OVERWORLD, chunkPos, vanillaLocatePos, centerOf(structureStart.getBoundingBox()), chunkPos
                        );
                     }

                     return true;
                  }
               }
            } else {
               if (!passesWhitelistVoidGuard(structureStart.getBoundingBox(), islandField, settings)) {
                  logStructureVerbose(
                     "Rejected whitelisted structure {} in chunk [{}, {}]: rejectionStage=whitelist_void_guard", structureId, chunkPos.x, chunkPos.z
                  );
                  return false;
               }

               logStructureVerbose(
                  "Accepted whitelisted structure {} in chunk [{}, {}]: acceptanceStage=whitelist_void_guard_passed", structureId, chunkPos.x, chunkPos.z
               );
               if (!StructureRegistryGuard.canCommit(structureId, structure, registryAccess, "whitelist_void_guard", chunkPos)) {
                  return false;
               }

               structureManager.setStartForStructure(sectionPos, structure, structureStart, chunk);
               return true;
            }
         } else {
            if (!StructureRegistryGuard.canCommit(structureId, structure, registryAccess, "custom_rules_disabled", chunkPos)) {
               return false;
            }

            structureManager.setStartForStructure(sectionPos, structure, structureStart, chunk);
            return true;
         }
      }
   }

   private static BlockPos centerOf(BoundingBox bounds) {
      return RelocatedStructureLocateIndex.centerOf(bounds);
   }

   private static int fetchReferences(StructureManager structureManager, ChunkAccess chunk, SectionPos sectionPos, Structure structure) {
      StructureStart structureStart = structureManager.getStartForStructure(sectionPos, structure, chunk);
      return structureStart != null ? structureStart.getReferences() : 0;
   }

   private static String formatBoundingBox(BoundingBox boundingBox) {
      return boundingBox == null
         ? "null"
         : "["
            + boundingBox.minX()
            + ","
            + boundingBox.minY()
            + ","
            + boundingBox.minZ()
            + " -> "
            + boundingBox.maxX()
            + ","
            + boundingBox.maxY()
            + ","
            + boundingBox.maxZ()
            + "]";
   }

   private static int countVillagePiecesOfKind(StructureStart structureStart, VillagePieceClassifier.PieceKind kind) {
      return structureStart != null && structureStart.isValid()
         ? (int)structureStart.getPieces().stream().filter(piece -> VILLAGE_PIECE_CLASSIFIER.classify(piece) == kind).count()
         : 0;
   }

   public static String rejectionStage(PlacementResult placementResult, StructurePlacementCategory category, LandRefinementResult landRefinement) {
      if (landRefinement.attempted() && !landRefinement.accepted()) {
         return landRefinement.outcome();
      } else {
         return !placementResult.attempted() ? "terrain_support_default" : "terrain_support_after_island_aware";
      }
   }

   private static String skyAwareRejectionStage(
      PlacementResult placementResult, StructureSupportValidator.SupportReport supportReport, LandRefinementResult landRefinement
   ) {
      if (supportReport.category() == StructurePlacementCategory.SKY) {
         if (supportReport.skyNearbyIslandColumns() <= 0) {
            return "sky_void_guard";
         }

         if (supportReport.skyCollisionSamples() > 0) {
            return "sky_air_clearance";
         }
      }

      return rejectionStage(placementResult, supportReport.category(), landRefinement);
   }

   public static String acceptanceStage(
      StructureSupportValidator.SupportReport supportReport, PlacementResult placementResult, LandRefinementResult landRefinement
   ) {
      if (supportReport.whitelisted()) {
         return "whitelisted_air";
      } else if (supportReport.category() == StructurePlacementCategory.SKY) {
         return "sky_air_clearance";
      } else if (landRefinement.attempted() && landRefinement.accepted()) {
         return landRefinement.moved() ? "accepted_with_repair_and_relocation" : "accepted_with_repair";
      } else {
         return !placementResult.attempted() ? "terrain_support_default" : "terrain_support_after_island_aware";
      }
   }

   private static String formatFootprint(StructureFootprint footprint) {
      return footprint == null ? "null" : "[" + footprint.minX() + "," + footprint.minZ() + " -> " + footprint.maxX() + "," + footprint.maxZ() + "]";
   }

   private static String formatRatio(double ratio) {
      return String.format(Locale.ROOT, "%.2f", ratio);
   }

   private static String formatPlacementTarget(SkyStructurePlacementTarget target) {
      return target == null
         ? "null"
         : "("
            + target.x()
            + ","
            + target.y()
            + ","
            + target.z()
            + "; topY="
            + target.topY()
            + ", offset="
            + target.topOffset()
            + ", local=("
            + target.localOffsetX()
            + ","
            + target.localOffsetZ()
            + "), stable="
            + target.stableTopCells()
            + ", grounded="
            + target.groundedSamples()
            + "/"
            + formatRatio(target.groundedRatio())
            + ", band="
            + target.heightBand()
            + ", family="
            + target.family()
            + ", search="
            + target.searchRadiusBlocks()
            + ")";
   }

   private static String formatRejectedHostCandidates(List<RejectedHostCandidate> rejectedHosts) {
      if (rejectedHosts != null && !rejectedHosts.isEmpty()) {
         List<String> summary = new ArrayList<>(rejectedHosts.size());

         for (int i = 0; i < rejectedHosts.size(); i++) {
            RejectedHostCandidate host = rejectedHosts.get(i);
            summary.add(
               "#"
                  + i
                  + "{tp="
                  + formatBlockTeleportHint(host.preview().x(), host.preview().y(), host.preview().z())
                  + ", center=("
                  + host.preview().x()
                  + ","
                  + host.preview().y()
                  + ","
                  + host.preview().z()
                  + "), family="
                  + host.preview().family()
                  + ", band="
                  + host.preview().heightBand()
                  + ", radius="
                  + host.preview().radius()
                  + ", stable="
                  + host.stableTopCells()
                  + "/"
                  + host.requiredStableTopCells()
                  + ", rejection="
                  + host.rejectionReason()
                  + ", minRadius="
                  + host.minHostIslandRadius()
                  + "}"
            );
         }

         return summary.toString();
      } else {
         return "[]";
      }
   }

   private static String formatRejectedSkyCandidates(List<RejectedSkyCandidate> rejectedCandidates) {
      if (rejectedCandidates != null && !rejectedCandidates.isEmpty()) {
         List<String> summary = new ArrayList<>(rejectedCandidates.size());

         for (int i = 0; i < rejectedCandidates.size(); i++) {
            RejectedSkyCandidate candidate = rejectedCandidates.get(i);
            summary.add(
               "#"
                  + i
                  + "{tp="
                  + formatBlockTeleportHint(candidate.targetCenterX(), candidate.preview().y() + 32, candidate.targetCenterZ())
                  + ", target=("
                  + candidate.targetCenterX()
                  + ","
                  + candidate.targetCenterZ()
                  + "), local=("
                  + candidate.localOffsetX()
                  + ","
                  + candidate.localOffsetZ()
                  + "), stable="
                  + candidate.stableTopCells()
                  + "/"
                  + candidate.requiredStableTopCells()
                  + ", grounded="
                  + candidate.groundedSamples()
                  + "/"
                  + candidate.groundingSampleCount()
                  + "/"
                  + formatRatio(candidate.groundedRatio())
                  + ", threshold="
                  + formatRatio(candidate.groundedThreshold())
                  + ", distanceSq="
                  + candidate.distanceSquared()
                  + ", rejection="
                  + candidate.rejectionReason()
                  + ", family="
                  + candidate.preview().family()
                  + ", band="
                  + candidate.preview().heightBand()
                  + "}"
            );
         }

         return summary.toString();
      } else {
         return "[]";
      }
   }

   private static String formatIntrusionSummary(TerrainIntrusionAnalyzer.TerrainIntrusionReport report) {
      return report != null && report.attempted()
         ? "{samples="
            + report.intersectingSamples()
            + "/"
            + report.totalSamples()
            + ", maxDepth="
            + report.maxIntrusionDepth()
            + ", worst="
            + formatIntrusionSamples(report.worstSamples())
            + "}"
         : "not_attempted";
   }

   private static String formatIntrusionSamples(List<TerrainIntrusionAnalyzer.IntrusionSample> samples) {
      if (samples != null && !samples.isEmpty()) {
         List<String> summary = new ArrayList<>(samples.size());

         for (TerrainIntrusionAnalyzer.IntrusionSample sample : samples) {
            summary.add(
               "("
                  + sample.x()
                  + ","
                  + sample.z()
                  + "; topY="
                  + sample.terrainTopY()
                  + ", range="
                  + sample.intrusionMinY()
                  + ".."
                  + sample.intrusionMaxY()
                  + ", depth="
                  + sample.intrusionDepth()
                  + ")"
            );
         }

         return summary.toString();
      } else {
         return "[]";
      }
   }

   private static String formatChunkTeleportHint(ChunkPos chunkPos, SkyIslandSettings settings) {
      int y = Mth.clamp(settings.terrain().maxIslandY() + 32, settings.terrain().minIslandY(), settings.terrain().maxIslandY() + 96);
      return formatBlockTeleportHint(chunkPos.getMiddleBlockX(), y, chunkPos.getMiddleBlockZ());
   }

   private static String formatBlockTeleportHint(int x, int y, int z) {
      return "/tp @s " + x + " " + y + " " + z;
   }

   private static String formatSamples(List<StructureSupportValidator.SupportSample> samples) {
      if (samples.isEmpty()) {
         return "[]";
      }

      StringBuilder builder = new StringBuilder("[");

      for (int index = 0; index < samples.size(); index++) {
         if (index > 0) {
            builder.append(", ");
         }

         StructureSupportValidator.SupportSample sample = samples.get(index);
         builder.append("(").append(sample.x()).append(",").append(sample.z()).append(")");
      }

      builder.append("]");
      return builder.toString();
   }

   static boolean usesDynamicAnchorPipeline(StructurePlacementCategory category, UndergroundPlacementBehavior undergroundBehavior) {
      return category == StructurePlacementCategory.UNDERGROUND && undergroundBehavior == UndergroundPlacementBehavior.DYNAMIC_ANCHOR_FIRST;
   }

   static boolean isV2SmallGroundCategory(StructurePlacementCategory category) {
      return category == StructurePlacementCategory.SMALL_SKY
         || category == StructurePlacementCategory.SURFACE_SKY
         || category == StructurePlacementCategory.HAMLET_SKY;
   }

   static boolean shouldRouteSkyToV2(
      ResourceLocation structureId, StructurePlacementCategory configuredCategory, StructurePlacementCategory effectiveCategory, SkyIslandSettings settings
   ) {
      if (configuredCategory == StructurePlacementCategory.SKY || effectiveCategory == StructurePlacementCategory.SKY) {
         return true;
      }

      if (configuredCategory == StructurePlacementCategory.DEFAULT && structureId != null) {
         String loweredPath = structureId.getPath().toLowerCase(Locale.ROOT);
         String loweredFullId = structureId.toString().toLowerCase(Locale.ROOT);

         for (String token : settings.advanced().structurePlacementPolicy().skyCategoryTokens()) {
            if (!token.isBlank() && (loweredPath.contains(token) || loweredFullId.contains(token))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   static boolean shouldRouteSkyToV2(ResourceLocation structureId, StructurePlacementCategory category, SkyIslandSettings settings) {
      return shouldRouteSkyToV2(structureId, category, category, settings);
   }

   static boolean isMineshaftDynamic(ResourceLocation structureId) {
      return structureId != null && structureId.getPath().contains("mineshaft");
   }

   static boolean isVanillaVillage(ResourceLocation structureId) {
      return structureId != null && structureId.toString().toLowerCase(Locale.ROOT).contains("village");
   }

   static boolean isVanillaIgloo(ResourceLocation structureId) {
      return IglooPlacementEngine.supports(structureId);
   }

   static boolean shouldRouteIglooToAnchorFirst(ResourceLocation structureId, StructurePlacementCategory category) {
      return isVanillaIgloo(structureId) && (isV2SmallGroundCategory(category) || category == StructurePlacementCategory.DEFAULT);
   }

   static boolean isStrongholdStructure(ResourceLocation structureId) {
      return StructurePlacementPolicy.isStrongholdStructure(structureId);
   }

   static boolean passesWhitelistVoidGuard(BoundingBox bounds, IslandField islandField, SkyIslandSettings settings) {
      int centerX = Mth.floor((bounds.minX() + bounds.maxX()) * 0.5);
      int centerZ = Mth.floor((bounds.minZ() + bounds.maxZ()) * 0.5);
      int nearbyIslands = 0;

      for (int x = centerX - 96; x <= centerX + 96; x += 16) {
         for (int z = centerZ - 96; z <= centerZ + 96; z += 16) {
            if (islandField.sampleColumn(x, z, settings).exists()) {
               if (++nearbyIslands >= 1) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static void broadcastAcceptedStructureDebug(ResourceLocation structureId, StructureStart structureStart, int resolvedBaseY) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null && SkyIslandServerConfig.structureDebugEnabled()) {
         BoundingBox bounds = structureStart.getBoundingBox();
         int x = Mth.floor((bounds.minX() + bounds.maxX()) * 0.5);
         int y = resolvedBaseY;
         int z = Mth.floor((bounds.minZ() + bounds.maxZ()) * 0.5);
         server.execute(
            () -> server.getPlayerList()
               .broadcastSystemMessage(Component.literal("[Sky Archipelago] POI spawned: " + structureId + " @ " + x + ", " + y + ", " + z), false)
         );
      }
   }

   private static void logStructureVerbose(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }

   private static void recordLandSummary(ResourceLocation structureId, LandRefinementResult landRefinement) {
      if (landRefinement.attempted()) {
         if (suppressSmallLandVerbose(landRefinement)) {
            LAND_SMALL_SUPPRESSED_COUNT.incrementAndGet();
         }

         int attempts = LAND_ATTEMPT_COUNT.incrementAndGet();
         String stageKey = landRefinement.accepted() ? "accepted:" + landRefinement.outcome() : "rejected:" + landRefinement.outcome();
         LAND_STAGE_COUNTS.computeIfAbsent(stageKey, ignored -> new AtomicInteger()).incrementAndGet();
         String idKey = structureId + "|" + stageKey + "|" + landRefinement.sizeTier();
         LAND_ID_COUNTS.computeIfAbsent(idKey, ignored -> new AtomicInteger()).incrementAndGet();
         if (attempts % 100 == 0) {
            List<Entry<String, AtomicInteger>> topStages = LAND_STAGE_COUNTS.entrySet()
               .stream()
               .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
               .limit(5L)
               .toList();
            List<Entry<String, AtomicInteger>> topIds = LAND_ID_COUNTS.entrySet()
               .stream()
               .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
               .limit(5L)
               .toList();
            logStructureVerbose(
               "LAND placement summary attempts={} smallSuppressedCount={} topStages={} topIdBuckets={}",
               attempts,
               LAND_SMALL_SUPPRESSED_COUNT.get(),
               topStages.stream().map(entry -> entry.getKey() + "=" + entry.getValue().get()).toList(),
               topIds.stream().map(entry -> entry.getKey() + "=" + entry.getValue().get()).toList()
            );
         }
      }
   }

   private static boolean suppressSmallLandVerbose(LandRefinementResult landRefinement) {
      return false;
   }

   static boolean shouldRouteStrongholdToHostIslandFlow(StructurePlacementCategory category, boolean strongholdHostIslandEnabled) {
      return strongholdHostIslandEnabled && category == StructurePlacementCategory.STRONGHOLD;
   }
}
