package org.sathrek.sky_archipelago.worldgen.structure.mineshafts;

import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.structure.JigsawAnchorResolver;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;
import org.sathrek.sky_archipelago.worldgen.structure.underground.DynamicUndergroundPlacementCoordinator;

public final class MineshaftPlacementCoordinator {
   private final DynamicUndergroundPlacementCoordinator delegate;

   public MineshaftPlacementCoordinator(MineshaftAnchorResolver anchorResolver) {
      this.delegate = new DynamicUndergroundPlacementCoordinator(new MineshaftAnchorStrategy(anchorResolver), new JigsawAnchorResolver());
   }

   public MineshaftPlacementDecision decide(
      StructureSupportContext context, StructureStart structureStart, SkyStructurePlacementTarget islandAwareTarget, int worldMinY, int maxIslandSpawnY
   ) {
      return this.delegate.decide(context, structureStart, islandAwareTarget, worldMinY, maxIslandSpawnY, false);
   }

   static double edgeSoftCapPenalty(IslandField.IslandPreview host, int candidateX, int candidateZ, double preferredInteriorMargin, double edgePenaltyWeight) {
      int radius = Math.max(1, host.radius());
      double dx = candidateX - host.x();
      double dz = candidateZ - host.z();
      double radialDistance = Math.sqrt(dx * dx + dz * dz);
      double interiorMargin = radius - radialDistance;
      if (interiorMargin >= preferredInteriorMargin) {
         return 0.0;
      }

      double deficit = preferredInteriorMargin - interiorMargin;
      double normalizedDeficit = Math.max(0.0, deficit) / Math.max(1.0, preferredInteriorMargin);
      return normalizedDeficit * edgePenaltyWeight;
   }
}
