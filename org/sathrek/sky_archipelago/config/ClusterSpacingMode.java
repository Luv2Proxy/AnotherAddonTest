package org.sathrek.sky_archipelago.config;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum ClusterSpacingMode implements StringRepresentable {
   CONSISTENT("consistent"),
   DYNAMIC("dynamic");

   public static final Codec<ClusterSpacingMode> CODEC = StringRepresentable.fromEnum(ClusterSpacingMode::values);
   private final String serializedName;

   ClusterSpacingMode(String serializedName) {
      this.serializedName = serializedName;
   }

   public String getSerializedName() {
      return this.serializedName;
   }
}
