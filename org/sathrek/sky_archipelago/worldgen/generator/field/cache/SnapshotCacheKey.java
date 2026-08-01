package org.sathrek.sky_archipelago.worldgen.generator.field.cache;

public record SnapshotCacheKey(int chunkX, int chunkZ, int halo, IslandFieldSettingsKey settingsKey, long forcedDescriptorRevision) {
}
