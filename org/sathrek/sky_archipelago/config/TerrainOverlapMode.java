package org.sathrek.sky_archipelago.config;

import com.mojang.serialization.Codec;
import java.util.Locale;

public enum TerrainOverlapMode {
   VOID,
   OVERLAP,
   CRATER;

   public static final Codec<TerrainOverlapMode> CODEC = Codec.STRING.xmap(TerrainOverlapMode::fromSerializedName, TerrainOverlapMode::serializedName);

   public String serializedName() {
      return switch (this) {
         case VOID -> "void";
         case OVERLAP -> "overlap";
         case CRATER -> "crater";
      };
   }

   public String displayName() {
      return switch (this) {
         case VOID -> "Void";
         case OVERLAP -> "Overlap";
         case CRATER -> "Crater";
      };
   }

   public static TerrainOverlapMode fromSerializedName(String value) {
      TerrainOverlapMode var10000;
      if (value == null) {
         var10000 = VOID;
      } else {
         switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "void":
               var10000 = VOID;
               break;
            case "overlap":
               var10000 = OVERLAP;
               break;
            case "crater":
               var10000 = CRATER;
               break;
            default:
               var10000 = VOID;
         }
      }

      return var10000;
   }
}
