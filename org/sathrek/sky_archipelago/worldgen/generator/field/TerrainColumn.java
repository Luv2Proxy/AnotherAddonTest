package org.sathrek.sky_archipelago.worldgen.generator.field;

public record TerrainColumn(int bottomY, int topY) {
   public static final TerrainColumn EMPTY = new TerrainColumn(1, 0);

   public boolean exists() {
      return this.topY >= this.bottomY;
   }

   public boolean contains(int y) {
      return this.exists() && y >= this.bottomY && y <= this.topY;
   }

   public int thickness() {
      return this.exists() ? this.topY - this.bottomY + 1 : 0;
   }

   public boolean intersectsInclusive(int minY, int maxY) {
      return this.exists() && this.bottomY <= maxY && this.topY >= minY;
   }
}
