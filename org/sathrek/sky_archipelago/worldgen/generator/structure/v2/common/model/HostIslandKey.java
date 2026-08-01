package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model;

import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

public record HostIslandKey(IslandField.IslandFamily family, int x, int y, int z, int radius) {
   public static HostIslandKey from(HostIsland host) {
      IslandField.IslandPreview preview = host.preview();
      return new HostIslandKey(preview.family(), preview.x(), preview.y(), preview.z(), preview.radius());
   }
}
