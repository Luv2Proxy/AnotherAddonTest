package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.cache.IslandFieldSettingsKey;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostQuery;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class IslandHostIndex {
   private final Map<IslandHostIndex.Key, List<IslandField.IslandPreview>> previewCache = new ConcurrentHashMap<>();

   public List<IslandField.IslandPreview> previewsFor(HostQuery query, IslandField islandField, SkyIslandSettings settings) {
      IslandHostIndex.Key key = new IslandHostIndex.Key(
         query.vanillaOrigin().x,
         query.vanillaOrigin().z,
         IslandFieldSettingsKey.from(settings),
         islandField.forcedDescriptorRevision(),
         query.category(),
         query.searchRadius()
      );
      return this.previewCache
         .computeIfAbsent(
            key, ignored -> List.copyOf(islandField.collectIslandPreviewsInRadius(query.originX(), query.originZ(), query.searchRadius(), settings))
         );
   }

   public int size() {
      return this.previewCache.size();
   }

   private record Key(
      int originChunkX,
      int originChunkZ,
      IslandFieldSettingsKey settingsKey,
      long forcedDescriptorRevision,
      StructurePlacementCategory category,
      int searchRadius
   ) {
   }
}
