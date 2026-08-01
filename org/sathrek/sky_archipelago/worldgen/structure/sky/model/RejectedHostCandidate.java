package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

public record RejectedHostCandidate(
   IslandField.IslandPreview preview, String rejectionReason, int stableTopCells, int requiredStableTopCells, int minHostIslandRadius
) {
}
