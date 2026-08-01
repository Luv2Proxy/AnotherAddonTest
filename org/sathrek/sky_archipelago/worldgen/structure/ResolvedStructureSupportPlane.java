package org.sathrek.sky_archipelago.worldgen.structure;

public record ResolvedStructureSupportPlane(
   int baseY, StructureFootprint rawFootprint, StructureFootprint effectiveFootprint, boolean usedFallback, int supportSliceCount, int supportSliceArea
) {
   public int scanStartY() {
      return this.baseY - 1;
   }
}
