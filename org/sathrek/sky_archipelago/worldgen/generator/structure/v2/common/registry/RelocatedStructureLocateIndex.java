package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;

public final class RelocatedStructureLocateIndex {
   private static final String DATA_NAME = "sky_archipelago_relocated_structure_locate_index";

   private RelocatedStructureLocateIndex() {
   }

   public static void recordCommittedRelocation(
      ResourceLocation structureId, ResourceKey<Level> dimension, ChunkPos startChunk, BlockPos vanillaPos, BlockPos relocatedPos, ChunkPos anchorChunk
   ) {
      if (structureId != null && dimension != null && startChunk != null && vanillaPos != null && relocatedPos != null) {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null && server.overworld() != null) {
            RelocatedStructureLocateIndex.IndexData data = load(server.overworld());
            data.put(
               new RelocatedStructureLocateIndex.RelocatedStructureEntry(
                  structureId, dimension, startChunk, vanillaPos, relocatedPos, anchorChunk == null ? startChunk : anchorChunk
               )
            );
            if (SkyIslandServerConfig.structureDebugEnabled()) {
               Optional<BlockPos> verify = nearestForEntries(data.entries(), id -> id.equals(structureId), entry -> true, dimension, relocatedPos, 2);
               if (verify.isEmpty() || !verify.get().equals(relocatedPos)) {
                  SkyArchipelago.LOGGER
                     .warn(
                        "LOCATE_INDEX_RECONCILE_MISMATCH id={} startChunk=[{}, {}] expected={} observed={}",
                        new Object[]{structureId, startChunk.x, startChunk.z, relocatedPos, verify.orElse(null)}
                     );
               }
            }

            data.setDirty();
         }
      }
   }

   public static Optional<BlockPos> findNearestForTag(ServerLevel level, TagKey<Structure> tag, BlockPos origin, int radiusChunks) {
      if (level != null && tag != null && origin != null && radiusChunks > 0) {
         Registry<Structure> structureRegistry = registry(level.registryAccess());
         Set<ResourceLocation> taggedIds = new HashSet<>();
         structureRegistry.getTag(tag).ifPresent(holders -> {
            for (Holder<Structure> holder : holders) {
               holder.unwrapKey().ifPresent(key -> taggedIds.add(key.location()));
            }
         });
         if (taggedIds.isEmpty()) {
            return Optional.empty();
         }

         Predicate<ResourceLocation> tagMatcher = taggedIds::contains;
         RelocatedStructureLocateIndex.IndexData data = load(level);
         return nearestForEntries(data.entries(), tagMatcher, id -> isEntryLive(level, structureRegistry, id), level.dimension(), origin, radiusChunks * 16);
      } else {
         return Optional.empty();
      }
   }

   public static Optional<BlockPos> findNearestForStructureIds(ServerLevel level, Set<ResourceLocation> structureIds, BlockPos origin, int radiusChunks) {
      if (level != null && structureIds != null && !structureIds.isEmpty() && origin != null && radiusChunks > 0) {
         Registry<Structure> structureRegistry = registry(level.registryAccess());
         RelocatedStructureLocateIndex.IndexData data = load(level);
         return nearestForEntries(
            data.entries(), structureIds::contains, id -> isEntryLive(level, structureRegistry, id), level.dimension(), origin, radiusChunks * 16
         );
      } else {
         return Optional.empty();
      }
   }

   static Optional<BlockPos> nearestForEntries(
      List<RelocatedStructureLocateIndex.RelocatedStructureEntry> entries,
      Predicate<ResourceLocation> structurePredicate,
      Predicate<RelocatedStructureLocateIndex.RelocatedStructureEntry> staleGuard,
      ResourceKey<Level> dimension,
      BlockPos origin,
      int radiusBlocks
   ) {
      if (entries != null && !entries.isEmpty()) {
         long radiusSq = (long)radiusBlocks * radiusBlocks;
         Map<RelocatedStructureLocateIndex.EntryKey, RelocatedStructureLocateIndex.RelocatedStructureEntry> deduped = new HashMap<>();

         for (RelocatedStructureLocateIndex.RelocatedStructureEntry entry : entries) {
            if (entry != null && entry.dimension().equals(dimension) && structurePredicate.test(entry.structure()) && staleGuard.test(entry)) {
               RelocatedStructureLocateIndex.EntryKey key = new RelocatedStructureLocateIndex.EntryKey(
                  entry.structure(), ChunkPos.asLong(entry.startChunk().x, entry.startChunk().z)
               );
               deduped.put(key, entry);
            }
         }

         return deduped.values()
            .stream()
            .sorted(Comparator.comparingLong(entryx -> horizontalDistanceSquared(origin, entryx.relocatedPos())))
            .map(RelocatedStructureLocateIndex.RelocatedStructureEntry::relocatedPos)
            .filter(pos -> horizontalDistanceSquared(origin, pos) <= radiusSq)
            .findFirst();
      } else {
         return Optional.empty();
      }
   }

   private static boolean isEntryLive(ServerLevel level, Registry<Structure> structureRegistry, RelocatedStructureLocateIndex.RelocatedStructureEntry entry) {
      if (!RelocatedStructureReferenceRegistry.isMaterialized(entry.structure(), entry.startChunk(), entry.dimension())) {
         if (SkyIslandServerConfig.structureDebugEnabled()) {
            RelocatedStructureReferenceRegistry.MaterializationState state = RelocatedStructureReferenceRegistry.materializationProgress(
               new RelocatedStructureReferenceRegistry.MaterializationKey(entry.structure(), entry.startChunk(), entry.dimension())
            );
            SkyArchipelago.LOGGER
               .info(
                  "LOCATE_INDEX_SKIP_NOT_MATERIALIZED id={} startChunk=[{}, {}] anchorChunk=[{}, {}] progress={}",
                  new Object[]{entry.structure(), entry.startChunk().x, entry.startChunk().z, entry.anchorChunk().x, entry.anchorChunk().z, state}
               );
         }

         return false;
      } else {
         Structure structure = (Structure)structureRegistry.get(entry.structure());
         if (structure == null) {
            return false;
         }

         LevelChunk chunk = level.getChunkSource().getChunkNow(entry.startChunk().x, entry.startChunk().z);
         if (chunk == null) {
            return true;
         }

         StructureStart start = level.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
         return start != null && start.isValid();
      }
   }

   private static long horizontalDistanceSquared(BlockPos a, BlockPos b) {
      long dx = (long)a.getX() - b.getX();
      long dz = (long)a.getZ() - b.getZ();
      return dx * dx + dz * dz;
   }

   private static Registry<Structure> registry(RegistryAccess access) {
      return access.registryOrThrow(Registries.STRUCTURE);
   }

   private static RelocatedStructureLocateIndex.IndexData load(ServerLevel level) {
      return (RelocatedStructureLocateIndex.IndexData)level.getDataStorage()
         .computeIfAbsent(RelocatedStructureLocateIndex.IndexData.factory(), "sky_archipelago_relocated_structure_locate_index");
   }

   public static BlockPos centerOf(BoundingBox bounds) {
      return new BlockPos(
         (int)Math.floor((bounds.minX() + bounds.maxX()) * 0.5),
         (int)Math.floor((bounds.minY() + bounds.maxY()) * 0.5),
         (int)Math.floor((bounds.minZ() + bounds.maxZ()) * 0.5)
      );
   }

   private record EntryKey(ResourceLocation structure, long startChunkKey) {
   }

   private static final class IndexData extends SavedData {
      private static final String TAG_ENTRIES = "entries";
      private static final String TAG_STRUCTURE = "structure";
      private static final String TAG_DIMENSION = "dimension";
      private static final String TAG_START_CHUNK_X = "startChunkX";
      private static final String TAG_START_CHUNK_Z = "startChunkZ";
      private static final String TAG_VANILLA_POS = "vanillaPos";
      private static final String TAG_RELOCATED_POS = "relocatedPos";
      private static final String TAG_ANCHOR_CHUNK_X = "anchorChunkX";
      private static final String TAG_ANCHOR_CHUNK_Z = "anchorChunkZ";
      private final List<RelocatedStructureLocateIndex.RelocatedStructureEntry> entries = new ArrayList<>();

      static RelocatedStructureLocateIndex.IndexData load(CompoundTag tag) {
         RelocatedStructureLocateIndex.IndexData data = new RelocatedStructureLocateIndex.IndexData();

         for (Tag entryTag : tag.getList("entries", 10)) {
            if (entryTag instanceof CompoundTag entry) {
               ResourceLocation structureId = ResourceLocation.tryParse(entry.getString("structure"));
               ResourceLocation dimensionId = ResourceLocation.tryParse(entry.getString("dimension"));
               if (structureId != null && dimensionId != null) {
                  ChunkPos startChunk = new ChunkPos(entry.getInt("startChunkX"), entry.getInt("startChunkZ"));
                  ChunkPos anchorChunk = new ChunkPos(entry.getInt("anchorChunkX"), entry.getInt("anchorChunkZ"));
                  BlockPos vanillaPos = readPos(entry.getCompound("vanillaPos"));
                  BlockPos relocatedPos = readPos(entry.getCompound("relocatedPos"));
                  if (vanillaPos != null && relocatedPos != null) {
                     data.entries
                        .add(
                           new RelocatedStructureLocateIndex.RelocatedStructureEntry(
                              structureId, ResourceKey.create(Registries.DIMENSION, dimensionId), startChunk, vanillaPos, relocatedPos, anchorChunk
                           )
                        );
                  }
               }
            }
         }

         return data;
      }

      static Factory<RelocatedStructureLocateIndex.IndexData> factory() {
         return new Factory(RelocatedStructureLocateIndex.IndexData::new, (tag, registries) -> load(tag), null);
      }

      void put(RelocatedStructureLocateIndex.RelocatedStructureEntry newEntry) {
         long startChunkKey = ChunkPos.asLong(newEntry.startChunk().x, newEntry.startChunk().z);
         this.entries
            .removeIf(
               entry -> entry.structure().equals(newEntry.structure())
                  && entry.dimension().equals(newEntry.dimension())
                  && ChunkPos.asLong(entry.startChunk().x, entry.startChunk().z) == startChunkKey
            );
         this.entries.add(newEntry);
      }

      List<RelocatedStructureLocateIndex.RelocatedStructureEntry> entries() {
         return List.copyOf(this.entries);
      }

      public CompoundTag save(CompoundTag tag, Provider registries) {
         ListTag list = new ListTag();

         for (RelocatedStructureLocateIndex.RelocatedStructureEntry entry : this.entries) {
            CompoundTag row = new CompoundTag();
            row.putString("structure", entry.structure().toString());
            row.putString("dimension", entry.dimension().location().toString());
            row.putInt("startChunkX", entry.startChunk().x);
            row.putInt("startChunkZ", entry.startChunk().z);
            row.put("vanillaPos", writePos(entry.vanillaPos()));
            row.put("relocatedPos", writePos(entry.relocatedPos()));
            row.putInt("anchorChunkX", entry.anchorChunk().x);
            row.putInt("anchorChunkZ", entry.anchorChunk().z);
            list.add(row);
         }

         tag.put("entries", list);
         return tag;
      }

      private static CompoundTag writePos(BlockPos pos) {
         CompoundTag tag = new CompoundTag();
         tag.putInt("x", pos.getX());
         tag.putInt("y", pos.getY());
         tag.putInt("z", pos.getZ());
         return tag;
      }

      private static BlockPos readPos(CompoundTag tag) {
         return tag != null && tag.contains("x") && tag.contains("y") && tag.contains("z")
            ? new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"))
            : null;
      }
   }

   public record RelocatedStructureEntry(
      ResourceLocation structure, ResourceKey<Level> dimension, ChunkPos startChunk, BlockPos vanillaPos, BlockPos relocatedPos, ChunkPos anchorChunk
   ) {
   }
}
