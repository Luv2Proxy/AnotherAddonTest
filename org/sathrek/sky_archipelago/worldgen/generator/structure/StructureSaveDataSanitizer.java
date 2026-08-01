package org.sathrek.sky_archipelago.worldgen.generator.structure;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.SkyArchipelago;

public final class StructureSaveDataSanitizer {
   private StructureSaveDataSanitizer() {
   }

   public static StructureSaveDataSanitizer.SanitizerResult sanitize(RegistryAccess registryAccess, ChunkAccess chunk, String context) {
      if (registryAccess != null && chunk != null) {
         Map<Structure, StructureStart> starts = chunk.getAllStarts();
         Map<Structure, LongSet> references = chunk.getAllReferences();
         int removedStarts = removeInvalidStarts(registryAccess, chunk, starts, context);
         int removedReferences = removeInvalidReferences(registryAccess, chunk, references, context);
         if (removedStarts > 0 || removedReferences > 0) {
            SkyArchipelago.LOGGER
               .warn(
                  "Sanitized invalid structure save data context={} chunk=[{},{}] removedStarts={} removedReferences={}",
                  new Object[]{context, chunk.getPos().x, chunk.getPos().z, removedStarts, removedReferences}
               );
         }

         return new StructureSaveDataSanitizer.SanitizerResult(removedStarts, removedReferences);
      } else {
         return StructureSaveDataSanitizer.SanitizerResult.empty();
      }
   }

   private static int removeInvalidStarts(RegistryAccess registryAccess, ChunkAccess chunk, Map<Structure, StructureStart> starts, String context) {
      if (starts != null && !starts.isEmpty()) {
         Map<Structure, StructureStart> sanitized = new LinkedHashMap<>();
         int removed = 0;

         for (Entry<Structure, StructureStart> entry : starts.entrySet()) {
            if (isRegistered(registryAccess, entry.getKey(), context, chunk)) {
               sanitized.put(entry.getKey(), entry.getValue());
            } else {
               removed++;
            }
         }

         if (removed > 0) {
            chunk.setAllStarts(sanitized);
         }

         return removed;
      } else {
         return 0;
      }
   }

   private static int removeInvalidReferences(RegistryAccess registryAccess, ChunkAccess chunk, Map<Structure, LongSet> references, String context) {
      if (references != null && !references.isEmpty()) {
         Map<Structure, LongSet> sanitized = new LinkedHashMap<>();
         int removed = 0;

         for (Entry<Structure, LongSet> entry : references.entrySet()) {
            if (isRegistered(registryAccess, entry.getKey(), context, chunk)) {
               sanitized.put(entry.getKey(), entry.getValue());
            } else {
               removed++;
            }
         }

         if (removed > 0) {
            chunk.setAllReferences(sanitized);
         }

         return removed;
      } else {
         return 0;
      }
   }

   private static boolean isRegistered(RegistryAccess registryAccess, Structure structure, String context, ChunkAccess chunk) {
      if (structure == null) {
         SkyArchipelago.LOGGER
            .warn("Dropping null structure save-data key context={} chunk=[{},{}]", new Object[]{context, chunk.getPos().x, chunk.getPos().z});
         return false;
      } else {
         ResourceLocation id = registryAccess.registry(Registries.STRUCTURE).map(registry -> registry.getKey(structure)).orElse(null);
         if (id == null) {
            SkyArchipelago.LOGGER
               .warn(
                  "Dropping unregistered structure save-data key type={} context={} chunk=[{},{}]",
                  new Object[]{structure.getClass().getName(), context, chunk.getPos().x, chunk.getPos().z}
               );
            return false;
         } else {
            return true;
         }
      }
   }

   public record SanitizerResult(int removedStarts, int removedReferences) {
      static StructureSaveDataSanitizer.SanitizerResult empty() {
         return new StructureSaveDataSanitizer.SanitizerResult(0, 0);
      }
   }
}
