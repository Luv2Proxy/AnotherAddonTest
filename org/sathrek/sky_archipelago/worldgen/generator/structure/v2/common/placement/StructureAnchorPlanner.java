package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.IslandPlacementSite;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.PlannedStructurePlacement;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public final class StructureAnchorPlanner {
   public PlannedStructurePlacement plan(
      IslandPlacementSite site,
      StructurePlacementPolicy policy,
      StructureFootprint rawFootprint,
      StructureFootprint effectiveFootprint,
      StructureFootprint alignmentFootprint,
      int planningBaseY,
      int verticalStartOffset
   ) {
      int targetX = site.x();
      int targetZ = site.z();
      int targetY = site.topY() + policy.topOffset() + verticalStartOffset;
      int dx = targetX - alignmentFootprint.centerX();
      int dz = targetZ - alignmentFootprint.centerZ();
      int dy = targetY - planningBaseY;
      return new PlannedStructurePlacement(site, dx, dy, dz, rawFootprint.translate(dx, dz), effectiveFootprint.translate(dx, dz));
   }
}
