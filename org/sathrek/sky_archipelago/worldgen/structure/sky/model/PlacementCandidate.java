package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public record PlacementCandidate(SkyStructurePlacementTarget target, StructureFootprint rawFootprint, StructureFootprint effectiveFootprint, int rank) {
}
