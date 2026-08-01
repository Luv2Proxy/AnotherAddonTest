package org.sathrek.sky_archipelago.config;

import com.mojang.serialization.Codec;
import java.util.Locale;

public enum IslandSizeMode {
   RANDOM,
   SPECIFIC;

   public static final Codec<IslandSizeMode> CODEC = Codec.STRING.xmap(IslandSizeMode::fromSerialized, IslandSizeMode::serializedName);

   public String serializedName() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public static IslandSizeMode fromSerialized(String raw) {
      if (raw == null) {
         return RANDOM;
      }

      return switch (raw.trim().toLowerCase(Locale.ROOT)) {
         case "specific" -> SPECIFIC;
         default -> RANDOM;
      };
   }
}
