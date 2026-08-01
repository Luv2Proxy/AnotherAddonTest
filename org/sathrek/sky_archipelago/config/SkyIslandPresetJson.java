package org.sathrek.sky_archipelago.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.DataResult.Error;
import java.util.List;

public final class SkyIslandPresetJson {
   public static final String WRAPPED_KEY = "sky_island_settings";
   private static final Gson GSON_PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final Gson GSON_COMPACT = new GsonBuilder().disableHtmlEscaping().create();

   private SkyIslandPresetJson() {
   }

   public static SkyIslandPresetJson.DecodeResult decodeSettings(String rawJson) {
      if (rawJson != null && !rawJson.isBlank()) {
         JsonElement parsedRoot;
         try {
            parsedRoot = JsonParser.parseString(rawJson);
         } catch (JsonSyntaxException syntaxException) {
            return SkyIslandPresetJson.DecodeResult.error("Invalid JSON syntax: " + syntaxException.getMessage());
         }

         JsonElement settingsElement = unwrapSettingsElement(parsedRoot);
         DataResult<SkyIslandGeneratorSettings> result = SkyIslandGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, settingsElement);
         return result.result().isPresent()
            ? SkyIslandPresetJson.DecodeResult.success((SkyIslandGeneratorSettings)result.result().orElseThrow())
            : SkyIslandPresetJson.DecodeResult.error(result.error().<String>map(Error::message).orElse("Failed to decode settings JSON."));
      } else {
         return SkyIslandPresetJson.DecodeResult.error("Preset JSON cannot be empty.");
      }
   }

   public static SkyIslandPresetJson.EncodeResult encodeWrapped(SkyIslandGeneratorSettings settings, boolean pretty) {
      DataResult<JsonElement> encodeResult = SkyIslandGeneratorSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings);
      if (encodeResult.result().isEmpty()) {
         return SkyIslandPresetJson.EncodeResult.error(encodeResult.error().<String>map(Error::message).orElse("Failed to encode settings JSON."));
      }

      JsonElement settingsJson = (JsonElement)encodeResult.result().orElseThrow();
      exposeAdvancedDefaults(settingsJson.getAsJsonObject(), settings);
      JsonObject wrapped = new JsonObject();
      wrapped.add("sky_island_settings", settingsJson);
      String payload = pretty ? GSON_PRETTY.toJson(wrapped) : GSON_COMPACT.toJson(wrapped);
      return SkyIslandPresetJson.EncodeResult.success(payload);
   }

   private static void exposeAdvancedDefaults(JsonObject settingsJson, SkyIslandGeneratorSettings settings) {
      JsonObject advanced = settingsJson.getAsJsonObject("advanced");
      if (advanced == null) {
         advanced = new JsonObject();
         settingsJson.add("advanced", advanced);
      }

      advanced.addProperty("terrain_overlap_mode", settings.advanced().terrainOverlapMode().serializedName());
      advanced.add("water_structure_category_tokens", stringArray(settings.advanced().waterStructureCategoryTokens()));
      advanced.add("sky_structure_category_tokens", stringArray(settings.advanced().skyStructureCategoryTokens()));
      advanced.add("ground_structure_category_tokens", stringArray(settings.advanced().groundStructureCategoryTokens()));
      advanced.add("underground_structure_category_tokens", stringArray(settings.advanced().undergroundStructureCategoryTokens()));
      advanced.add("village_structure_category_tokens", stringArray(settings.advanced().villageStructureCategoryTokens()));
      advanced.add("stronghold_structure_category_tokens", stringArray(settings.advanced().strongholdStructureCategoryTokens()));
   }

   private static JsonArray stringArray(List<String> values) {
      JsonArray array = new JsonArray();

      for (String value : values) {
         array.add(value);
      }

      return array;
   }

   private static JsonElement unwrapSettingsElement(JsonElement root) {
      if (root instanceof JsonObject rootObject && rootObject.has("sky_island_settings")) {
         JsonElement wrapped = rootObject.get("sky_island_settings");
         if (wrapped != null && wrapped.isJsonObject()) {
            return wrapped;
         }
      }

      return root;
   }

   public record DecodeResult(boolean success, SkyIslandGeneratorSettings settings, String message) {
      static SkyIslandPresetJson.DecodeResult success(SkyIslandGeneratorSettings settings) {
         return new SkyIslandPresetJson.DecodeResult(true, settings, "");
      }

      static SkyIslandPresetJson.DecodeResult error(String message) {
         return new SkyIslandPresetJson.DecodeResult(false, null, message);
      }
   }

   public record EncodeResult(boolean success, String message) {
      static SkyIslandPresetJson.EncodeResult success(String payload) {
         return new SkyIslandPresetJson.EncodeResult(true, payload);
      }

      static SkyIslandPresetJson.EncodeResult error(String message) {
         return new SkyIslandPresetJson.EncodeResult(false, message);
      }
   }
}
