package org.sathrek.sky_archipelago.worldgen.generator.structure;

import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;

public final class StructureRegistryGuard {
   private StructureRegistryGuard() {
   }

   public static Optional<ResourceLocation> registeredStructureId(RegistryAccess registryAccess, Structure structure) {
      if (registryAccess == null) {
         SkyArchipelago.LOGGER.warn("Skipping structure placement: registryAccess was null");
         return Optional.empty();
      } else if (structure == null) {
         SkyArchipelago.LOGGER.warn("Skipping structure placement: structure was null");
         return Optional.empty();
      } else {
         ResourceLocation id = registryAccess.registry(Registries.STRUCTURE).map(registry -> registry.getKey(structure)).orElse(null);
         if (id == null) {
            SkyArchipelago.LOGGER.warn("Skipping structure placement: unregistered structure instance type={}", structure.getClass().getName());
            return Optional.empty();
         } else {
            return Optional.of(id);
         }
      }
   }

   public static boolean canCommit(PlacementRequest request, String context) {
      if (request == null) {
         SkyArchipelago.LOGGER.warn("Skipping structure commit: request was null context={}", context);
         return false;
      } else {
         ChunkPos chunkPos = request.chunkPos();
         return canCommit(
            request.structureId(),
            request.structure(),
            request.generationContext() == null ? null : request.generationContext().registryAccess(),
            context,
            chunkPos
         );
      }
   }

   public static boolean canCommit(ResourceLocation structureId, Structure structure, String context) {
      return canCommit(structureId, structure, null, context, null);
   }

   public static boolean canCommit(ResourceLocation structureId, Structure structure, RegistryAccess registryAccess, String context) {
      return canCommit(structureId, structure, registryAccess, context, null);
   }

   public static boolean canCommit(ResourceLocation structureId, Structure structure, RegistryAccess registryAccess, String context, ChunkPos chunkPos) {
      if (structure == null) {
         SkyArchipelago.LOGGER.warn("Skipping structure commit: structure was null context={} chunk={}", context, formatChunk(chunkPos));
         return false;
      }

      if (structureId == null) {
         SkyArchipelago.LOGGER
            .warn(
               "Skipping structure commit: structure id was null type={} context={} chunk={}",
               new Object[]{structure.getClass().getName(), context, formatChunk(chunkPos)}
            );
         return false;
      }

      if (registryAccess != null) {
         Optional<ResourceLocation> registeredId = registeredStructureId(registryAccess, structure);
         if (registeredId.isEmpty()) {
            SkyArchipelago.LOGGER
               .warn(
                  "Skipping structure commit: registry lookup failed for id={} type={} context={} chunk={}",
                  new Object[]{structureId, structure.getClass().getName(), context, formatChunk(chunkPos)}
               );
            return false;
         }

         if (!registryIdMatches(structureId, registeredId.get())) {
            SkyArchipelago.LOGGER
               .warn(
                  "Skipping structure commit: registry id mismatch requested={} registered={} type={} context={} chunk={}",
                  new Object[]{structureId, registeredId.get(), structure.getClass().getName(), context, formatChunk(chunkPos)}
               );
            return false;
         }
      }

      return true;
   }

   static boolean registryIdMatches(ResourceLocation requestedId, ResourceLocation registeredId) {
      return requestedId != null && requestedId.equals(registeredId);
   }

   private static String formatChunk(ChunkPos chunkPos) {
      return chunkPos == null ? "unknown" : "[" + chunkPos.x + "," + chunkPos.z + "]";
   }
}
