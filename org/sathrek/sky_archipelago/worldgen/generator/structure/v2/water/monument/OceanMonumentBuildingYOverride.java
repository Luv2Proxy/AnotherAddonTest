package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.monument;

public final class OceanMonumentBuildingYOverride {
   private static final ThreadLocal<Integer> OVERRIDE_Y = new ThreadLocal<>();

   private OceanMonumentBuildingYOverride() {
   }

   public static int resolve(int vanillaY) {
      Integer override = OVERRIDE_Y.get();
      return override == null ? vanillaY : override;
   }

   public static boolean active() {
      return OVERRIDE_Y.get() != null;
   }

   public static OceanMonumentBuildingYOverride.Scope push(int y) {
      OVERRIDE_Y.set(y);
      return new OceanMonumentBuildingYOverride.Scope();
   }

   public static final class Scope implements AutoCloseable {
      private boolean closed;

      private Scope() {
      }

      @Override
      public void close() {
         if (!this.closed) {
            OceanMonumentBuildingYOverride.OVERRIDE_Y.remove();
            this.closed = true;
         }
      }
   }
}
