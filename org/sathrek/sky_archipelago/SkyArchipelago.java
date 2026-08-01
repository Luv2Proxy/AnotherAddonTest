package org.sathrek.sky_archipelago;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import org.sathrek.sky_archipelago.config.SkyIslandConfig;
import org.sathrek.sky_archipelago.config.SkyIslandServerConfig;
import org.sathrek.sky_archipelago.worldgen.registry.ModWorldgenRegistries;
import org.slf4j.Logger;

@Mod("sky_archipelago")
public final class SkyArchipelago {
   public static final String MODID = "sky_archipelago";
   public static final Logger LOGGER = LogUtils.getLogger();

   public SkyArchipelago(IEventBus modEventBus, ModContainer modContainer) {
      ModWorldgenRegistries.register(modEventBus);
      modContainer.registerConfig(Type.COMMON, SkyIslandConfig.SPEC);
      modContainer.registerConfig(Type.SERVER, SkyIslandServerConfig.SPEC);
   }

   public static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("sky_archipelago", path);
   }
}
