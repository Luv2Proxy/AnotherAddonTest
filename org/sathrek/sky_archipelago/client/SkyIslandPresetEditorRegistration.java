package org.sathrek.sky_archipelago.client;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;
import org.sathrek.sky_archipelago.SkyArchipelago;

@EventBusSubscriber(modid = "sky_archipelago", value = Dist.CLIENT)
public final class SkyIslandPresetEditorRegistration {
   private static final ResourceKey<WorldPreset> SKY_ISLANDS_PRESET = ResourceKey.create(Registries.WORLD_PRESET, SkyArchipelago.id("sky_islands"));

   private SkyIslandPresetEditorRegistration() {
   }

   @SubscribeEvent
   public static void registerPresetEditors(RegisterPresetEditorsEvent event) {
      event.register(SKY_ISLANDS_PRESET, SkyIslandCustomizeScreen::new);
   }
}
