package org.sathrek.sky_archipelago.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.SkyIslandDedicatedConfigExporter;
import org.sathrek.sky_archipelago.config.SkyIslandGeneratorSettings;
import org.sathrek.sky_archipelago.config.SkyIslandPresetJson;
import org.sathrek.sky_archipelago.config.SkyIslandSavedPresetRepository;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

@EventBusSubscriber(modid = "sky_archipelago")
public final class SkyArchipelagoCommands {
   private SkyArchipelagoCommands() {
   }

   @SubscribeEvent
   public static void onRegisterCommands(RegisterCommandsEvent event) {
      event.getDispatcher().register(rootCommand());
   }

   static LiteralArgumentBuilder<CommandSourceStack> rootCommand() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)Commands.literal("skyarchipelago")
            .requires(source -> source.hasPermission(2)))
         .then(
            ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("preset")
                        .then(
                           Commands.literal("create")
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("name", StringArgumentType.word())
                                       .executes(
                                          context -> createPreset(
                                             (CommandSourceStack)context.getSource(), StringArgumentType.getString(context, "name"), "", false
                                          )
                                       ))
                                    .then(
                                       Commands.argument("description", StringArgumentType.greedyString())
                                          .executes(
                                             context -> createPreset(
                                                (CommandSourceStack)context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "description"),
                                                false
                                             )
                                          )
                                    )
                              )
                        ))
                     .then(
                        Commands.literal("overwrite")
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("name", StringArgumentType.word())
                                    .executes(
                                       context -> createPreset((CommandSourceStack)context.getSource(), StringArgumentType.getString(context, "name"), "", true)
                                    ))
                                 .then(
                                    Commands.argument("description", StringArgumentType.greedyString())
                                       .executes(
                                          context -> createPreset(
                                             (CommandSourceStack)context.getSource(),
                                             StringArgumentType.getString(context, "name"),
                                             StringArgumentType.getString(context, "description"),
                                             true
                                          )
                                       )
                                 )
                           )
                     ))
                  .then(Commands.literal("export").executes(context -> exportPreset((CommandSourceStack)context.getSource()))))
               .then(Commands.literal("export_dedicated").executes(context -> exportDedicatedPreset((CommandSourceStack)context.getSource())))
         );
   }

   private static int exportPreset(CommandSourceStack source) {
      if (source.getLevel().getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator skyIslandChunkGenerator) {
         SkyIslandGeneratorSettings activeSettings = skyIslandChunkGenerator.activeGeneratorSettings();
         SkyIslandPresetJson.EncodeResult encoded = SkyIslandPresetJson.encodeWrapped(activeSettings, true);
         if (!encoded.success()) {
            SkyArchipelago.LOGGER.error("Failed to export Sky Archipelago preset JSON: {}", encoded.message());
            source.sendFailure(Component.literal("Unable to export preset JSON. Check server logs for details."));
            return 0;
         } else {
            MutableComponent copyAction = copyPresetComponent(encoded.message());
            source.sendSuccess(() -> Component.literal("Sky Archipelago preset ready: ").append(copyAction), false);
            return 1;
         }
      } else {
         source.sendFailure(Component.literal("This world is not using the Sky Archipelago chunk generator."));
         return 0;
      }
   }

   private static int createPreset(CommandSourceStack source, String name, String description, boolean overwrite) {
      if (source.getLevel().getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator skyIslandChunkGenerator) {
         SkyIslandGeneratorSettings activeSettings = skyIslandChunkGenerator.activeGeneratorSettings();
         SkyIslandSavedPresetRepository repository = SkyIslandSavedPresetRepository.atDefaultLocation();
         SkyIslandSavedPresetRepository.OperationResult result = repository.savePreset(name, description, activeSettings, overwrite);
         if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
         } else {
            String mode = overwrite ? "overwritten" : "created";
            source.sendSuccess(() -> Component.literal("Saved preset " + mode + ": " + name), false);
            return 1;
         }
      } else {
         source.sendFailure(Component.literal("This world is not using the Sky Archipelago chunk generator."));
         return 0;
      }
   }

   private static int exportDedicatedPreset(CommandSourceStack source) {
      if (source.getLevel().getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator skyIslandChunkGenerator) {
         SkyIslandGeneratorSettings activeSettings = skyIslandChunkGenerator.activeGeneratorSettings();
         String payload = SkyIslandDedicatedConfigExporter.toDedicatedConfigSnippet(activeSettings);
         MutableComponent copyAction = copyDedicatedComponent(payload);
         source.sendSuccess(() -> Component.literal("Sky Archipelago dedicated config ready: ").append(copyAction), false);
         return 1;
      } else {
         source.sendFailure(Component.literal("This world is not using the Sky Archipelago chunk generator."));
         return 0;
      }
   }

   static MutableComponent copyPresetComponent(String payload) {
      return Component.literal("[Copy Preset JSON]")
         .withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(Action.COPY_TO_CLIPBOARD, payload)));
   }

   static MutableComponent copyDedicatedComponent(String payload) {
      return Component.literal("[Copy Dedicated Config]")
         .withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(Action.COPY_TO_CLIPBOARD, payload)));
   }
}
