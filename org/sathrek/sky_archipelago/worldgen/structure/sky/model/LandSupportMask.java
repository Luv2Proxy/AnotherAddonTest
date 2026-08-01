package org.sathrek.sky_archipelago.worldgen.structure.sky.model;

import java.util.List;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;

public record LandSupportMask(List<RelativePoint> coreRequired, List<RelativePoint> centralCoreRequired, List<RelativePoint> edgeOptional) {
   public List<StructureFootprint.GridPoint> coreRequiredPoints(int centerX, int centerZ, StructureFootprint fallback, int fallbackGrid) {
      return this.coreRequired.isEmpty()
         ? fallback.sampleGrid(fallbackGrid)
         : this.coreRequired.stream().map(point -> new StructureFootprint.GridPoint(centerX + point.dx(), centerZ + point.dz())).toList();
   }

   public List<StructureFootprint.GridPoint> centralCorePoints(int centerX, int centerZ, StructureFootprint fallback, int fallbackGrid) {
      return this.centralCoreRequired.isEmpty()
         ? fallback.sampleGrid(fallbackGrid)
         : this.centralCoreRequired.stream().map(point -> new StructureFootprint.GridPoint(centerX + point.dx(), centerZ + point.dz())).toList();
   }

   public List<StructureFootprint.GridPoint> edgeOptionalPoints(int centerX, int centerZ, StructureFootprint fallback, int fallbackGrid) {
      return this.edgeOptional.isEmpty()
         ? fallback.sampleGrid(fallbackGrid)
         : this.edgeOptional.stream().map(point -> new StructureFootprint.GridPoint(centerX + point.dx(), centerZ + point.dz())).toList();
   }
}
