package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
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
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRegistryGuard;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureSaveDataSanitizer;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;

public final class RelocatedStructureReferenceRegistry {
   private static final Map<Long, Set<RelocatedStructureReferenceRegistry.PendingReference>> PENDING_REFERENCES = new ConcurrentHashMap<>();
   private static final Map<Long, Set<RelocatedStructureReferenceRegistry.PendingStart>> PENDING_STARTS = new ConcurrentHashMap<>();
   private static final Map<RelocatedStructureReferenceRegistry.MaterializationKey, RelocatedStructureReferenceRegistry.MaterializationState> MATERIALIZATION_STATES = new ConcurrentHashMap<>();

   private RelocatedStructureReferenceRegistry() {
   }

   public static RelocatedStructureReferenceRegistry.RegistrationResult registerTouchedChunks(
      PlacementRequest request, StructureStart relocatedStart, BoundingBox finalBounds
   ) {
      return request == null
         ? emptyResult(null)
         : registerTouchedChunks(
            request.structureManager(),
            request.structureId(),
            request.structure(),
            relocatedStart,
            finalBounds,
            request.chunk(),
            request.dimension(),
            request.generationContext() == null ? null : request.generationContext().registryAccess(),
            "relocated_reference_registry"
         );
   }

   public static RelocatedStructureReferenceRegistry.RegistrationResult registerTouchedChunks(
      StructureManager structureManager,
      ResourceLocation structureId,
      Structure structure,
      StructureStart relocatedStart,
      BoundingBox finalBounds,
      ChunkAccess currentChunk
   ) {
      return registerTouchedChunks(
         structureManager, structureId, structure, relocatedStart, finalBounds, currentChunk, Level.OVERWORLD, null, "relocated_reference_registry"
      );
   }

   public static RelocatedStructureReferenceRegistry.RegistrationResult registerTouchedChunks(
      StructureManager structureManager,
      ResourceLocation structureId,
      Structure structure,
      StructureStart relocatedStart,
      BoundingBox finalBounds,
      ChunkAccess currentChunk,
      ResourceKey<Level> dimension,
      RegistryAccess registryAccess,
      String context
   ) {
      ChunkPos fallbackChunkPos = currentChunk == null ? new ChunkPos(0, 0) : currentChunk.getPos();
      if (structureManager != null && currentChunk != null && relocatedStart != null && finalBounds != null) {
         if (registryAccess == null) {
            SkyArchipelago.LOGGER
               .warn(
                  "Skipping relocated structure reference registration: registryAccess was null id={} context={} chunk=[{},{}]",
                  new Object[]{structureId, context, currentChunk.getPos().x, currentChunk.getPos().z}
               );
            return new RelocatedStructureReferenceRegistry.RegistrationResult(List.of(), 0, 0, fallbackChunkPos);
         }

         if (!StructureRegistryGuard.canCommit(structureId, structure, registryAccess, context, currentChunk.getPos())) {
            return new RelocatedStructureReferenceRegistry.RegistrationResult(List.of(), 0, 0, fallbackChunkPos);
         }

         StructureSaveDataSanitizer.sanitize(registryAccess, currentChunk, context + "_before_register");
         List<ChunkPos> touchedChunks = touchedChunks(finalBounds);
         if (touchedChunks.isEmpty()) {
            return new RelocatedStructureReferenceRegistry.RegistrationResult(List.of(), 0, 0, fallbackChunkPos);
         }

         ChunkPos anchorChunk = anchorChunk(finalBounds, touchedChunks);
         int appliedNow = 0;
         int queued = 0;
         int appliedStartsNow = 0;
         int queuedStarts = 0;
         long sourceChunkKey = ChunkPos.asLong(anchorChunk.x, anchorChunk.z);
         ChunkPos currentChunkPos = currentChunk.getPos();
         ResourceKey<Level> effectiveDimension = dimension == null ? Level.OVERWORLD : dimension;
         RelocatedStructureReferenceRegistry.MaterializationKey materializationKey = new RelocatedStructureReferenceRegistry.MaterializationKey(
            structureId == null ? ResourceLocation.withDefaultNamespace("unknown") : structureId, currentChunkPos, effectiveDimension
         );
         RelocatedStructureReferenceRegistry.PendingStart pendingStart = new RelocatedStructureReferenceRegistry.PendingStart(
            structureId, structure, relocatedStart, registryAccess, materializationKey
         );
         if (anchorChunk.equals(currentChunkPos)) {
            structureManager.setStartForStructure(SectionPos.bottomOf(currentChunk), structure, relocatedStart, currentChunk);
            appliedStartsNow++;
         } else {
            PENDING_STARTS.computeIfAbsent(sourceChunkKey, ignored -> ConcurrentHashMap.newKeySet()).add(pendingStart);
            queuedStarts++;
         }

         for (ChunkPos targetChunk : touchedChunks) {
            RelocatedStructureReferenceRegistry.PendingReference reference = new RelocatedStructureReferenceRegistry.PendingReference(
               structureId, structure, sourceChunkKey, registryAccess, materializationKey
            );
            if (targetChunk.equals(currentChunkPos)) {
               structureManager.addReferenceForStructure(SectionPos.bottomOf(currentChunk), structure, sourceChunkKey, currentChunk);
               appliedNow++;
            } else {
               PENDING_REFERENCES.computeIfAbsent(ChunkPos.asLong(targetChunk.x, targetChunk.z), ignored -> ConcurrentHashMap.newKeySet()).add(reference);
               queued++;
            }
         }

         MATERIALIZATION_STATES.put(
            materializationKey,
            RelocatedStructureReferenceRegistry.MaterializationState.initialize(
               materializationKey, anchorChunk, queuedStarts, appliedStartsNow, queued, appliedNow
            )
         );
         return new RelocatedStructureReferenceRegistry.RegistrationResult(touchedChunks, appliedNow, queued, anchorChunk);
      } else {
         return new RelocatedStructureReferenceRegistry.RegistrationResult(List.of(), 0, 0, fallbackChunkPos);
      }
   }

   public static int applyPending(StructureManager structureManager, ChunkAccess chunk) {
      return applyPending(structureManager, chunk, null);
   }

   public static int applyPending(StructureManager structureManager, ChunkAccess chunk, RegistryAccess registryAccess) {
      if (structureManager != null && chunk != null) {
         StructureSaveDataSanitizer.sanitize(registryAccess, chunk, "relocated_pending_before_apply");
         long chunkKey = ChunkPos.asLong(chunk.getPos().x, chunk.getPos().z);
         Set<RelocatedStructureReferenceRegistry.PendingStart> starts = PENDING_STARTS.remove(chunkKey);
         if (starts != null && !starts.isEmpty()) {
            SectionPos sectionPos = SectionPos.bottomOf(chunk);

            for (RelocatedStructureReferenceRegistry.PendingStart start : starts) {
               RegistryAccess effectiveRegistryAccess = start.registryAccess() == null ? registryAccess : start.registryAccess();
               if (StructureRegistryGuard.canCommit(start.structureId(), start.structure(), effectiveRegistryAccess, "relocated_pending_start", chunk.getPos())
                  )
                {
                  structureManager.setStartForStructure(sectionPos, start.structure(), start.relocatedStart(), chunk);
                  MATERIALIZATION_STATES.computeIfPresent(start.materializationKey(), (ignored, state) -> {
                     RelocatedStructureReferenceRegistry.MaterializationState next = state.withAppliedStart();
                     logMaterializationProgress("materialization_progress_start", next);
                     logMaterializationReady(state, next);
                     return next;
                  });
               }
            }
         }

         Set<RelocatedStructureReferenceRegistry.PendingReference> references = PENDING_REFERENCES.remove(chunkKey);
         if (references != null && !references.isEmpty()) {
            int applied = 0;
            SectionPos sectionPos = SectionPos.bottomOf(chunk);

            for (RelocatedStructureReferenceRegistry.PendingReference reference : references) {
               RegistryAccess effectiveRegistryAccess = reference.registryAccess() == null ? registryAccess : reference.registryAccess();
               if (StructureRegistryGuard.canCommit(
                  reference.structureId(), reference.structure(), effectiveRegistryAccess, "relocated_pending_reference", chunk.getPos()
               )) {
                  structureManager.addReferenceForStructure(sectionPos, reference.structure(), reference.sourceChunkKey(), chunk);
                  applied++;
                  MATERIALIZATION_STATES.computeIfPresent(reference.materializationKey(), (ignored, state) -> {
                     RelocatedStructureReferenceRegistry.MaterializationState next = state.withAppliedReference();
                     logMaterializationProgress("materialization_progress_reference", next);
                     logMaterializationReady(state, next);
                     return next;
                  });
               }
            }

            return applied;
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   public static List<ChunkPos> touchedChunks(BoundingBox bounds) {
      if (bounds == null) {
         return List.of();
      }

      int minChunkX = SectionPos.blockToSectionCoord(bounds.minX());
      int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX());
      int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ());
      int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ());
      List<ChunkPos> chunks = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));

      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
         for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            chunks.add(new ChunkPos(chunkX, chunkZ));
         }
      }

      chunks.sort(Comparator.<ChunkPos>comparingInt(pos -> pos.x).thenComparingInt(pos -> pos.z));
      return List.copyOf(chunks);
   }

   public static String describeSpan(List<ChunkPos> chunks) {
      if (chunks.isEmpty()) {
         return "empty";
      }

      int minX = chunks.stream().mapToInt(pos -> pos.x).min().orElse(0);
      int maxX = chunks.stream().mapToInt(pos -> pos.x).max().orElse(0);
      int minZ = chunks.stream().mapToInt(pos -> pos.z).min().orElse(0);
      int maxZ = chunks.stream().mapToInt(pos -> pos.z).max().orElse(0);
      return "[" + minX + ".." + maxX + ", " + minZ + ".." + maxZ + "]";
   }

   public static void clearForTests() {
      PENDING_REFERENCES.clear();
      PENDING_STARTS.clear();
      MATERIALIZATION_STATES.clear();
   }

   public static boolean isMaterialized(RelocatedStructureReferenceRegistry.MaterializationKey key) {
      if (key == null) {
         return false;
      }

      RelocatedStructureReferenceRegistry.MaterializationState state = MATERIALIZATION_STATES.get(key);
      return state != null && state.ready();
   }

   public static boolean isMaterialized(ResourceLocation structureId, ChunkPos startChunk, ResourceKey<Level> dimension) {
      RelocatedStructureReferenceRegistry.MaterializationKey key = materializationKeyFor(structureId, startChunk, dimension);
      return isMaterialized(key);
   }

   public static RelocatedStructureReferenceRegistry.MaterializationState materializationProgress(RelocatedStructureReferenceRegistry.MaterializationKey key) {
      return key == null ? null : MATERIALIZATION_STATES.get(key);
   }

   static void putMaterializationStateForTests(RelocatedStructureReferenceRegistry.MaterializationState state) {
      if (state != null) {
         MATERIALIZATION_STATES.put(state.key(), state);
      }
   }

   private static RelocatedStructureReferenceRegistry.RegistrationResult emptyResult(ChunkAccess chunk) {
      return new RelocatedStructureReferenceRegistry.RegistrationResult(List.of(), 0, 0, chunk == null ? new ChunkPos(0, 0) : chunk.getPos());
   }

   private static RelocatedStructureReferenceRegistry.MaterializationKey materializationKeyFor(
      ResourceLocation structureId, ChunkPos startChunk, ResourceKey<Level> dimension
   ) {
      return structureId != null && startChunk != null && dimension != null
         ? new RelocatedStructureReferenceRegistry.MaterializationKey(structureId, startChunk, dimension)
         : null;
   }

   private static ChunkPos anchorChunk(BoundingBox bounds, List<ChunkPos> touchedChunks) {
      int centerChunkX = SectionPos.blockToSectionCoord((bounds.minX() + bounds.maxX()) / 2);
      int centerChunkZ = SectionPos.blockToSectionCoord((bounds.minZ() + bounds.maxZ()) / 2);
      ChunkPos center = new ChunkPos(centerChunkX, centerChunkZ);
      return touchedChunks.contains(center) ? center : touchedChunks.get(0);
   }

   private static void logMaterializationProgress(String stage, RelocatedStructureReferenceRegistry.MaterializationState state) {
      if (state != null && SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER
            .info(
               "{} id={} startChunk=[{}, {}] appliedStarts={}/{} appliedReferences={}/{} ready={}",
               new Object[]{
                  stage,
                  state.key().structureId(),
                  state.key().startChunk().x,
                  state.key().startChunk().z,
                  state.appliedStarts(),
                  state.queuedStarts(),
                  state.appliedReferences(),
                  state.queuedReferences(),
                  state.ready()
               }
            );
      }
   }

   private static void logMaterializationReady(
      RelocatedStructureReferenceRegistry.MaterializationState previous, RelocatedStructureReferenceRegistry.MaterializationState current
   ) {
      if (previous != null && current != null && !previous.ready() && current.ready() && SkyIslandServerConfig.structureDebugEnabled()) {
         SkyArchipelago.LOGGER
            .info(
               "materialization_ready id={} startChunk=[{}, {}] anchorChunk=[{}, {}] appliedStarts={}/{} appliedReferences={}/{}",
               new Object[]{
                  current.key().structureId(),
                  current.key().startChunk().x,
                  current.key().startChunk().z,
                  current.anchorChunk().x,
                  current.anchorChunk().z,
                  current.appliedStarts(),
                  current.queuedStarts(),
                  current.appliedReferences(),
                  current.queuedReferences()
               }
            );
      }
   }

   public record MaterializationKey(ResourceLocation structureId, ChunkPos startChunk, ResourceKey<Level> dimension) {
   }

   public record MaterializationState(
      RelocatedStructureReferenceRegistry.MaterializationKey key,
      ChunkPos anchorChunk,
      int queuedStarts,
      int appliedStarts,
      int queuedReferences,
      int appliedReferences,
      boolean ready
   ) {
      static RelocatedStructureReferenceRegistry.MaterializationState initialize(
         RelocatedStructureReferenceRegistry.MaterializationKey key,
         ChunkPos anchorChunk,
         int queuedStarts,
         int appliedStarts,
         int queuedReferences,
         int appliedReferences
      ) {
         boolean ready = appliedStarts >= queuedStarts && appliedReferences >= queuedReferences;
         return new RelocatedStructureReferenceRegistry.MaterializationState(
            key, anchorChunk, queuedStarts, appliedStarts, queuedReferences, appliedReferences, ready
         );
      }

      RelocatedStructureReferenceRegistry.MaterializationState withAppliedStart() {
         int nextAppliedStarts = this.appliedStarts + 1;
         boolean nextReady = nextAppliedStarts >= this.queuedStarts && this.appliedReferences >= this.queuedReferences;
         return new RelocatedStructureReferenceRegistry.MaterializationState(
            this.key, this.anchorChunk, this.queuedStarts, nextAppliedStarts, this.queuedReferences, this.appliedReferences, nextReady
         );
      }

      RelocatedStructureReferenceRegistry.MaterializationState withAppliedReference() {
         int nextAppliedReferences = this.appliedReferences + 1;
         boolean nextReady = this.appliedStarts >= this.queuedStarts && nextAppliedReferences >= this.queuedReferences;
         return new RelocatedStructureReferenceRegistry.MaterializationState(
            this.key, this.anchorChunk, this.queuedStarts, this.appliedStarts, this.queuedReferences, nextAppliedReferences, nextReady
         );
      }
   }

   private record PendingReference(
      ResourceLocation structureId,
      Structure structure,
      long sourceChunkKey,
      RegistryAccess registryAccess,
      RelocatedStructureReferenceRegistry.MaterializationKey materializationKey
   ) {
   }

   private record PendingStart(
      ResourceLocation structureId,
      Structure structure,
      StructureStart relocatedStart,
      RegistryAccess registryAccess,
      RelocatedStructureReferenceRegistry.MaterializationKey materializationKey
   ) {
   }

   public record RegistrationResult(List<ChunkPos> touchedChunks, int appliedNow, int queued, ChunkPos anchorChunk) {
      public String span() {
         return RelocatedStructureReferenceRegistry.describeSpan(this.touchedChunks);
      }
   }
}
