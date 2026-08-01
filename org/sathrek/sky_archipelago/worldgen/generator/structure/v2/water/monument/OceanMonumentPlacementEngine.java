package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementDecision;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.AnchoredStartFinalizer;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandColumnMaterialPlan;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class OceanMonumentPlacementEngine {
   public static final ResourceLocation STRUCTURE_ID = ResourceLocation.parse("minecraft:monument");
   private static final int MONUMENT_HEIGHT_BLOCKS = 23;
   private static final int MONUMENT_ISLAND_CLEARANCE_BLOCKS = 16;
   private final AnchoredStartFinalizer finalizer;

   public OceanMonumentPlacementEngine() {
      this(new AnchoredStartFinalizer());
   }

   OceanMonumentPlacementEngine(AnchoredStartFinalizer finalizer) {
      this.finalizer = finalizer;
   }

   public PlacementDecision place(PlacementRequest request) {
      PlacementRequest.StructureGenerationContext generation = request.generationContext();
      return generation == null
         ? PlacementDecision.rejected("ocean_monument_anchor_first_rejected", "missing_generation_context")
         : this.place(
            request.structure(),
            request.structureId(),
            request.structureManager(),
            request.sectionPos(),
            request.chunk(),
            request.chunkPos(),
            request.settings(),
            request.islandField(),
            request.levelSeed(),
            generation.references(),
            generation
         );
   }

   public PlacementDecision place(
      Structure structure,
      ResourceLocation structureId,
      StructureManager structureManager,
      SectionPos sectionPos,
      ChunkAccess chunk,
      ChunkPos chunkPos,
      SkyIslandSettings settings,
      IslandField islandField,
      long levelSeed,
      int references,
      PlacementRequest.StructureGenerationContext generationContext
   ) {
      if (!supports(structureId)) {
         return PlacementDecision.rejected("ocean_monument_anchor_first_rejected", "unsupported_structure");
      }

      if (!settings.terrain().ocean().oceanEnabled()) {
         debug(
            "OCEAN_MONUMENT_DEBUG stage=reject_requires_ocean chunk=[{}, {}] levelSeed={} layoutSeed={} oceanEnabled=false",
            chunkPos.x,
            chunkPos.z,
            levelSeed,
            islandField.layoutSeed()
         );
         return PlacementDecision.rejected("ocean_monument_anchor_first_rejected", "rejected_ocean_monument_requires_ocean");
      }

      if (!StructureRegistryGuard.canCommit(structureId, structure, "ocean_monument")) {
         return PlacementDecision.rejected("unregistered_structure", "unregistered_structure");
      }

      int searchRadiusBlocks = settings.advanced().structurePlacementPolicy().localSearchRadiusBlocksForCategory(StructurePlacementCategory.WATER);
      int searchStepBlocks = settings.advanced().structurePlacementPolicy().localSearchStepBlocksForCategory(StructurePlacementCategory.WATER);
      debug(
         "OCEAN_MONUMENT_DEBUG stage=probe_start chunk=[{}, {}] levelSeed={} layoutSeed={} oceanLevelY={} buildY=[{}, {}) searchRadiusBlocks={} searchStepBlocks={} references={} mode={}",
         chunkPos.x,
         chunkPos.z,
         levelSeed,
         islandField.layoutSeed(),
         settings.terrain().ocean().oceanLevelY(),
         chunk.getMinBuildHeight(),
         chunk.getMaxBuildHeight(),
         searchRadiusBlocks,
         searchStepBlocks,
         references,
         "OCEAN_FLOOR"
      );
      OceanMonumentPlacementEngine.SimpleAnchor probe = findSimpleAnchor(
         chunkPos, searchRadiusBlocks, searchStepBlocks, islandField, settings, chunk.getMinBuildHeight(), chunk.getMaxBuildHeight()
      );
      if (probe == null) {
         debug(
            "OCEAN_MONUMENT_DEBUG stage=probe_rejected chunk=[{}, {}] reason={} scannedCandidates={} samplesPerCandidate={} ",
            chunkPos.x,
            chunkPos.z,
            "rejected_ocean_monument_no_ocean_floor_anchor",
            0,
            1
         );
         return PlacementDecision.rejected("ocean_monument_anchor_first_rejected", "rejected_ocean_monument_no_ocean_floor_anchor");
      }

      ChunkPos anchorChunk = new ChunkPos(probe.centerX() >> 4, probe.centerZ() >> 4);
      int targetMinY = computeTargetMinY(probe.bodyFloorY());
      if (!isTargetYWithinBuildBounds(targetMinY, chunk.getMinBuildHeight(), chunk.getMaxBuildHeight())) {
         return PlacementDecision.rejected("ocean_monument_anchor_first_rejected", "rejected_ocean_monument_target_y_out_of_bounds");
      }

      StructureStart structureStart;
      try (OceanMonumentBuildingYOverride.Scope ignored = OceanMonumentBuildingYOverride.push(targetMinY)) {
         structureStart = structure.generate(
            generationContext.registryAccess(),
            generationContext.generator(),
            generationContext.biomeSource(),
            generationContext.randomState(),
            generationContext.templateManager(),
            levelSeed,
            anchorChunk,
            references,
            chunk,
            structure.biomes()::contains
         );
      }

      if (!structureStart.isValid()) {
         return PlacementDecision.rejected("ocean_monument_anchor_first_rejected", "rejected_ocean_monument_invalid_start");
      }

      BoundingBox finalBounds = structureStart.getBoundingBox();
      if (finalBounds == null) {
         return PlacementDecision.rejected("ocean_monument_anchor_first_rejected", "rejected_ocean_monument_missing_bounds");
      }

      debug(
         "OCEAN_MONUMENT_DEBUG stage=start_created chunk=[{}, {}] anchorChunk=[{}, {}] center=({}, {}) sampledBodyFloorY={} appliedOverrideMinY={} finalBoundsMinY={} actualBounds={} pieceCount={} levelSeed={} layoutSeed={} mode={}",
         chunkPos.x,
         chunkPos.z,
         anchorChunk.x,
         anchorChunk.z,
         probe.centerX(),
         probe.centerZ(),
         probe.bodyFloorY(),
         targetMinY,
         finalBounds.minY(),
         formatBounds(finalBounds),
         structureStart.getPieces().size(),
         levelSeed,
         islandField.layoutSeed(),
         "OCEAN_FLOOR"
      );
      OceanMonumentPlacementEngine.OceanMonumentPreAnchorContext preAnchorContext = new OceanMonumentPlacementEngine.OceanMonumentPreAnchorContext(
         chunkPos, probe.scannedCandidates()
      );
      AnchoredStartFinalizer.FinalizationResult finalization = this.finalizer
         .finalizeAnchoredStart(
            structureId,
            structure,
            structureManager,
            sectionPos,
            chunk,
            structureStart,
            finalBounds,
            preAnchorContext.sourceChunk(),
            anchorChunk,
            Level.OVERWORLD,
            generationContext.registryAccess(),
            new BlockPos(probe.centerX(), probe.bodyFloorY(), probe.centerZ()),
            "ocean_monument"
         );
      if (!finalization.accepted()) {
         return PlacementDecision.rejected("ocean_monument_anchor_first_rejected", "rejected_ocean_monument_finalize_failed:" + finalization.stage());
      }

      debug(
         "OCEAN_MONUMENT_DEBUG stage=registered chunk=[{}, {}] sourceChunk=[{}, {}] winningAnchorChunk=[{}, {}] scannedCandidates={} touchedChunks={} appliedReferencesNow={} queuedReferences={} bounds={}",
         chunkPos.x,
         chunkPos.z,
         preAnchorContext.sourceChunk().x,
         preAnchorContext.sourceChunk().z,
         anchorChunk.x,
         anchorChunk.z,
         preAnchorContext.scannedCandidates(),
         finalization.references().span(),
         finalization.references().appliedNow(),
         finalization.references().queued(),
         formatBounds(finalBounds)
      );
      return PlacementDecision.accepted(
         "ocean_monument_anchor_first_accepted",
         "bodyFloorY="
            + probe.bodyFloorY()
            + ",appliedOverrideMinY="
            + targetMinY
            + ",minY="
            + finalBounds.minY()
            + ",center=("
            + probe.centerX()
            + ","
            + probe.centerZ()
            + "),mode=OCEAN_FLOOR,scannedCandidates="
            + probe.scannedCandidates()
            + ",samplesPerCandidate=1,sourceChunk=["
            + preAnchorContext.sourceChunk().x
            + ","
            + preAnchorContext.sourceChunk().z
            + "],winningAnchorChunk=["
            + anchorChunk.x
            + ","
            + anchorChunk.z
            + "]",
         structureStart
      );
   }

   private static OceanMonumentPlacementEngine.SimpleAnchor findSimpleAnchor(
      ChunkPos candidateChunk, int searchRadiusBlocks, int searchStepBlocks, IslandField islandField, SkyIslandSettings settings, int minBuildY, int maxBuildY
   ) {
      int step = Math.max(4, searchStepBlocks);
      int radius = Math.max(0, searchRadiusBlocks);
      int scanned = 0;

      for (int dx = -radius; dx <= radius; dx += step) {
         for (int dz = -radius; dz <= radius; dz += step) {
            scanned++;
            int centerX = candidateChunk.getMinBlockX() + 8 + dx;
            int centerZ = candidateChunk.getMinBlockZ() + 8 + dz;
            if (islandField.isOceanBiomeAt(centerX, settings.terrain().ocean().oceanLevelY(), centerZ)
               && isClearOfNearbyIslands(centerX, centerZ, islandField, settings)) {
               SkyIslandColumnMaterialPlan plan = SkyIslandColumnMaterialPlan.create(
                  islandField.sampleSolidSegments(centerX, centerZ, settings), minBuildY, maxBuildY, settings, centerX, centerZ, islandField.layoutSeed()
               );
               if (plan.oceanEnabled() && plan.oceanFloorTopY() < settings.terrain().ocean().oceanLevelY()) {
                  return new OceanMonumentPlacementEngine.SimpleAnchor(centerX, centerZ, plan.oceanFloorTopY(), scanned);
               }
            }
         }
      }

      return null;
   }

   static boolean isClearOfNearbyIslands(int centerX, int centerZ, IslandField islandField, SkyIslandSettings settings) {
      int requiredClearDistance = 45;
      int searchRadius = requiredClearDistance + settings.terrain().maxIslandRadius();
      return isClearOfNearbyIslands(
         centerX, centerZ, requiredClearDistance, islandField.collectIslandPreviewsInRadius(centerX, centerZ, searchRadius, settings)
      );
   }

   static boolean isClearOfNearbyIslands(int centerX, int centerZ, int requiredClearDistance, List<IslandField.IslandPreview> previews) {
      for (IslandField.IslandPreview preview : previews) {
         int dx = centerX - preview.x();
         int dz = centerZ - preview.z();
         int minAllowed = Math.max(1, preview.radius()) + requiredClearDistance;
         if ((long)dx * dx + (long)dz * dz <= (long)minAllowed * minAllowed) {
            return false;
         }
      }

      return true;
   }

   static int computeTargetMinY(int bodyFloorY) {
      return bodyFloorY - 8;
   }

   static boolean isTargetYWithinBuildBounds(int targetMinY, int minBuildHeight, int maxBuildHeight) {
      int targetMaxY = targetMinY + 23 - 1;
      return targetMinY >= minBuildHeight && targetMaxY < maxBuildHeight;
   }

   public static boolean supports(ResourceLocation structureId) {
      return STRUCTURE_ID.equals(structureId);
   }

   private static void debug(String message, Object... args) {
      if (SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER.info(message, args);
      }
   }

   private static String formatBounds(BoundingBox bounds) {
      return "[" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + " -> " + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ() + "]";
   }

   private record OceanMonumentPreAnchorContext(ChunkPos sourceChunk, int scannedCandidates) {
   }

   private record SimpleAnchor(int centerX, int centerZ, int bodyFloorY, int scannedCandidates) {
   }
}
