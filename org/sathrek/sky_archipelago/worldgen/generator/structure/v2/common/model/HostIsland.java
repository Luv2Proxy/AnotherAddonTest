package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model;

import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

public record HostIsland(IslandField.IslandPreview preview, int centerTopY, int stableTopCells, int usableRadius, int requiredRadius) {
}
