package org.sathrek.sky_archipelago.worldgen.generator.field.cache;

public record ColumnCacheKey(int x, int z, IslandFieldSettingsKey settingsKey, long forcedDescriptorRevision) {
}
