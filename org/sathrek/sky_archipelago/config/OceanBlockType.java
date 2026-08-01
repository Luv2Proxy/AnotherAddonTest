package org.sathrek.sky_archipelago.config;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public enum OceanBlockType {
   WATER,
   LAVA;

   public static final Codec<OceanBlockType> CODEC = Codec.STRING.xmap(OceanBlockType::fromSerializedName, OceanBlockType::serializedName);

   public String serializedName() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public static OceanBlockType fromSerializedName(String value) {
      OceanBlockType var10000;
      if (value == null) {
         var10000 = WATER;
      } else {
         switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "lava":
               var10000 = LAVA;
               break;
            case "water":
               var10000 = WATER;
               break;
            default:
               var10000 = WATER;
         }
      }

      return var10000;
   }

   public BlockState blockState() {
      return switch (this) {
         case WATER -> Blocks.WATER.defaultBlockState();
         case LAVA -> Blocks.LAVA.defaultBlockState();
      };
   }

   public Fluid fluid() {
      return switch (this) {
         case WATER -> Fluids.WATER;
         case LAVA -> Fluids.LAVA;
      };
   }
}
