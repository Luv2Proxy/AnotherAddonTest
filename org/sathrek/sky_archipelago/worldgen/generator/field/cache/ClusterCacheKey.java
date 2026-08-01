package org.sathrek.sky_archipelago.worldgen.generator.field.cache;

public record ClusterCacheKey(int cellX, int cellZ, IslandFieldSettingsKey settingsKey, long forcedDescriptorRevision) {
}
