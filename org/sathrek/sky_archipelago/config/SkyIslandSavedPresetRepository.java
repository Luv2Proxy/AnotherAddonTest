package org.sathrek.sky_archipelago.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLPaths;

public final class SkyIslandSavedPresetRepository {
   private static final String BUILTIN_INDEX_RESOURCE = "data/sky_archipelago/presets/index.json";
   private static final String BUILTIN_RESOURCE_ROOT = "data/sky_archipelago/presets/";
   private static final Pattern SAFE_STEM = Pattern.compile("[a-z0-9_-]+");
   private final Path presetDir;

   public SkyIslandSavedPresetRepository(Path presetDir) {
      this.presetDir = presetDir;
   }

   public static SkyIslandSavedPresetRepository atDefaultLocation() {
      return new SkyIslandSavedPresetRepository(FMLPaths.CONFIGDIR.get().resolve("sky_archipelago").resolve("presets"));
   }

   public SkyIslandSavedPresetRepository.ListResult listPresets() {
      Map<String, SkyIslandSavedPresetRepository.SavedPresetSummary> summariesByStem = new LinkedHashMap<>();
      List<String> warnings = new ArrayList<>();
      this.loadUserPresetSummaries(summariesByStem, warnings);
      this.loadBuiltInPresetSummaries(summariesByStem, warnings);

      try {
         Files.createDirectories(this.presetDir);
      } catch (IOException exception) {
         warnings.add("Unable to list saved presets: " + exception.getMessage());
      }

      List<SkyIslandSavedPresetRepository.SavedPresetSummary> summaries = new ArrayList<>(summariesByStem.values());
      summaries.sort(
         Comparator.<SkyIslandSavedPresetRepository.SavedPresetSummary>comparingInt(summary -> summary.source().sortOrder())
            .thenComparing(summary -> summary.name().toLowerCase(Locale.ROOT))
      );
      return new SkyIslandSavedPresetRepository.ListResult(List.copyOf(summaries), List.copyOf(warnings));
   }

   public Optional<SkyIslandSavedPresetRepository.SavedPreset> loadPreset(String name) {
      SkyIslandSavedPresetRepository.Validation validation = this.validateName(name);
      if (!validation.valid()) {
         return Optional.empty();
      }

      Optional<SkyIslandSavedPresetRepository.SavedPreset> userSaved = this.loadUserPreset(validation.stem());
      return userSaved.isPresent() ? userSaved : this.loadBuiltInPreset(validation.stem()).map(SkyIslandSavedPresetRepository.BuiltInPreset::toSavedPreset);
   }

   public Optional<SkyIslandSavedPresetRepository.SavedPreset> loadPreset(SkyIslandSavedPresetRepository.SavedPresetSummary summary) {
      if (summary == null) {
         return Optional.empty();
      } else {
         return summary.source() == SkyIslandSavedPresetRepository.PresetSource.BUILT_IN
            ? this.loadBuiltInPreset(summary.stem()).map(SkyIslandSavedPresetRepository.BuiltInPreset::toSavedPreset)
            : this.loadUserPreset(summary.stem())
               .or(() -> this.loadBuiltInPreset(summary.stem()).map(SkyIslandSavedPresetRepository.BuiltInPreset::toSavedPreset));
      }
   }

   private Optional<SkyIslandSavedPresetRepository.SavedPreset> loadUserPreset(String stem) {
      SkyIslandSavedPresetRepository.Validation validation = this.validateName(stem);
      if (!validation.valid()) {
         return Optional.empty();
      }

      Path file = this.fileForStem(validation.stem());
      if (!Files.exists(file)) {
         return Optional.empty();
      }

      try {
         String raw = Files.readString(file, StandardCharsets.UTF_8);
         SkyIslandPresetJson.DecodeResult decoded = SkyIslandPresetJson.decodeSettings(raw);
         if (!decoded.success()) {
            return Optional.empty();
         }

         JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
         String storedName = root.has("name") ? root.get("name").getAsString() : validation.displayName();
         String description = root.has("description") ? root.get("description").getAsString() : "";
         return Optional.of(
            new SkyIslandSavedPresetRepository.SavedPreset(
               storedName, validation.stem(), description, decoded.settings(), SkyIslandSavedPresetRepository.PresetSource.USER_SAVED
            )
         );
      } catch (Exception exception) {
         return Optional.empty();
      }
   }

   public SkyIslandSavedPresetRepository.OperationResult savePreset(String name, String description, SkyIslandGeneratorSettings settings, boolean overwrite) {
      SkyIslandSavedPresetRepository.Validation validation = this.validateName(name);
      if (!validation.valid()) {
         return SkyIslandSavedPresetRepository.OperationResult.error(validation.message());
      }

      try {
         Files.createDirectories(this.presetDir);
         Path file = this.fileForStem(validation.stem());
         if (Files.exists(file) && !overwrite) {
            return SkyIslandSavedPresetRepository.OperationResult.error("A preset with this name already exists.");
         }

         SkyIslandPresetJson.EncodeResult encoded = SkyIslandPresetJson.encodeWrapped(settings, true);
         if (!encoded.success()) {
            return SkyIslandSavedPresetRepository.OperationResult.error(encoded.message());
         }

         JsonObject wrapped = JsonParser.parseString(encoded.message()).getAsJsonObject();
         JsonObject persist = new JsonObject();
         persist.addProperty("name", validation.displayName());
         persist.addProperty("description", description == null ? "" : description.trim());
         persist.add("sky_island_settings", wrapped.get("sky_island_settings"));
         Files.writeString(file, persist.toString(), StandardCharsets.UTF_8);
         return SkyIslandSavedPresetRepository.OperationResult.success(file);
      } catch (Exception exception) {
         return SkyIslandSavedPresetRepository.OperationResult.error("Unable to save preset: " + exception.getMessage());
      }
   }

   public SkyIslandSavedPresetRepository.OperationResult deletePreset(String name) {
      SkyIslandSavedPresetRepository.Validation validation = this.validateName(name);
      if (!validation.valid()) {
         return SkyIslandSavedPresetRepository.OperationResult.error(validation.message());
      }

      Path file = this.fileForStem(validation.stem());

      try {
         if (!Files.exists(file)) {
            return SkyIslandSavedPresetRepository.OperationResult.error("Preset not found.");
         }

         Files.delete(file);
         return SkyIslandSavedPresetRepository.OperationResult.success(file);
      } catch (IOException exception) {
         return SkyIslandSavedPresetRepository.OperationResult.error("Unable to delete preset: " + exception.getMessage());
      }
   }

   private void loadUserPresetSummaries(Map<String, SkyIslandSavedPresetRepository.SavedPresetSummary> summariesByStem, List<String> warnings) {
      try {
         Files.createDirectories(this.presetDir);

         try (Stream<Path> stream = Files.list(this.presetDir)) {
            stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
               .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
               .forEach(path -> this.readUserSummary(path, summariesByStem, warnings));
         }
      } catch (IOException exception) {
         warnings.add("Unable to list saved presets: " + exception.getMessage());
      }
   }

   private void readUserSummary(Path path, Map<String, SkyIslandSavedPresetRepository.SavedPresetSummary> summariesByStem, List<String> warnings) {
      try {
         String raw = Files.readString(path, StandardCharsets.UTF_8);
         SkyIslandPresetJson.DecodeResult decoded = SkyIslandPresetJson.decodeSettings(raw);
         if (!decoded.success()) {
            warnings.add("Skipping invalid preset file " + path.getFileName() + ": " + decoded.message());
            return;
         }

         JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
         String stem = stripJson(path.getFileName().toString());
         String name = root.has("name") ? root.get("name").getAsString() : stem;
         String description = root.has("description") ? root.get("description").getAsString() : "";
         summariesByStem.put(
            stem, new SkyIslandSavedPresetRepository.SavedPresetSummary(name, stem, description, SkyIslandSavedPresetRepository.PresetSource.USER_SAVED)
         );
      } catch (Exception exception) {
         warnings.add("Skipping unreadable preset file " + path.getFileName() + ": " + exception.getMessage());
      }
   }

   private void loadBuiltInPresetSummaries(Map<String, SkyIslandSavedPresetRepository.SavedPresetSummary> summariesByStem, List<String> warnings) {
      for (SkyIslandSavedPresetRepository.BuiltInPresetDefinition definition : this.readBuiltInPresetDefinitions(warnings)) {
         Optional<SkyIslandSavedPresetRepository.BuiltInPreset> loaded = this.loadBuiltInPreset(definition, warnings);
         if (loaded.isPresent()) {
            summariesByStem.putIfAbsent(loaded.get().summary().stem(), loaded.get().summary());
         }
      }
   }

   private List<SkyIslandSavedPresetRepository.BuiltInPresetDefinition> readBuiltInPresetDefinitions(List<String> warnings) {
      Optional<String> rawIndex = this.readResourceText("data/sky_archipelago/presets/index.json");
      if (rawIndex.isEmpty()) {
         warnings.add("Missing built-in preset index: data/sky_archipelago/presets/index.json");
         return List.of();
      }

      try {
         JsonElement parsed = JsonParser.parseString(rawIndex.get());
         JsonArray entries;
         if (parsed.isJsonArray()) {
            entries = parsed.getAsJsonArray();
         } else {
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("presets")) {
               warnings.add("Built-in preset index must be a JSON array or an object with a 'presets' array.");
               return List.of();
            }

            entries = parsed.getAsJsonObject().getAsJsonArray("presets");
         }

         List<SkyIslandSavedPresetRepository.BuiltInPresetDefinition> definitions = new ArrayList<>();

         for (JsonElement entry : entries) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
               String stem = entry.getAsString().trim();
               if (!stem.isBlank()) {
                  definitions.add(new SkyIslandSavedPresetRepository.BuiltInPresetDefinition(stem, stem, ""));
               }
            } else if (!entry.isJsonObject()) {
               warnings.add("Skipping invalid built-in preset index entry: " + entry);
            } else {
               JsonObject object = entry.getAsJsonObject();
               String stem = object.has("stem") ? object.get("stem").getAsString().trim() : "";
               if (stem.isBlank()) {
                  warnings.add("Skipping built-in preset index entry without a stem.");
               } else {
                  String name = object.has("name") ? object.get("name").getAsString() : stem;
                  String description = object.has("description") ? object.get("description").getAsString() : "";
                  definitions.add(new SkyIslandSavedPresetRepository.BuiltInPresetDefinition(stem, name, description));
               }
            }
         }

         return List.copyOf(definitions);
      } catch (Exception exception) {
         warnings.add("Unable to read built-in preset index: " + exception.getMessage());
         return List.of();
      }
   }

   private Optional<SkyIslandSavedPresetRepository.BuiltInPreset> loadBuiltInPreset(String stem) {
      SkyIslandSavedPresetRepository.Validation validation = this.validateName(stem);
      if (!validation.valid()) {
         return Optional.empty();
      }

      List<String> warnings = new ArrayList<>();

      for (SkyIslandSavedPresetRepository.BuiltInPresetDefinition definition : this.readBuiltInPresetDefinitions(warnings)) {
         if (definition.stem().equals(validation.stem())) {
            return this.loadBuiltInPreset(definition, warnings);
         }
      }

      return Optional.empty();
   }

   private Optional<SkyIslandSavedPresetRepository.BuiltInPreset> loadBuiltInPreset(
      SkyIslandSavedPresetRepository.BuiltInPresetDefinition definition, List<String> warnings
   ) {
      String resourcePath = "data/sky_archipelago/presets/" + definition.stem() + ".json";
      Optional<String> rawPreset = this.readResourceText(resourcePath);
      if (rawPreset.isEmpty()) {
         warnings.add("Missing built-in preset resource: " + resourcePath);
         return Optional.empty();
      } else {
         SkyIslandPresetJson.DecodeResult decoded = SkyIslandPresetJson.decodeSettings(rawPreset.get());
         if (!decoded.success()) {
            warnings.add("Skipping invalid built-in preset " + resourcePath + ": " + decoded.message());
            return Optional.empty();
         } else {
            return Optional.of(
               new SkyIslandSavedPresetRepository.BuiltInPreset(
                  new SkyIslandSavedPresetRepository.SavedPresetSummary(
                     definition.name(), definition.stem(), definition.description(), SkyIslandSavedPresetRepository.PresetSource.BUILT_IN
                  ),
                  decoded.settings()
               )
            );
         }
      }
   }

   private Optional<String> readResourceText(String resourcePath) {
      ClassLoader loader = SkyIslandSavedPresetRepository.class.getClassLoader();

      try (InputStream inputStream = loader.getResourceAsStream(resourcePath)) {
         return inputStream == null ? Optional.empty() : Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
      } catch (IOException exception) {
         return Optional.empty();
      }
   }

   private Path fileForStem(String stem) {
      return this.presetDir.resolve(stem + ".json");
   }

   private static String stripJson(String filename) {
      return filename.toLowerCase(Locale.ROOT).endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
   }

   private SkyIslandSavedPresetRepository.Validation validateName(String name) {
      if (name != null && !name.isBlank()) {
         String display = name.trim();
         String stem = display.toLowerCase(Locale.ROOT).replaceAll("\\s+", "_").replaceAll("[^a-z0-9_-]", "");
         return !SAFE_STEM.matcher(stem).matches()
            ? SkyIslandSavedPresetRepository.Validation.invalid("Preset name must contain letters, numbers, spaces, '_' or '-'.")
            : SkyIslandSavedPresetRepository.Validation.valid(display, stem);
      } else {
         return SkyIslandSavedPresetRepository.Validation.invalid("Preset name is required.");
      }
   }

   private record BuiltInPreset(SkyIslandSavedPresetRepository.SavedPresetSummary summary, SkyIslandGeneratorSettings settings) {
      private SkyIslandSavedPresetRepository.SavedPreset toSavedPreset() {
         return new SkyIslandSavedPresetRepository.SavedPreset(
            this.summary.name(), this.summary.stem(), this.summary.description(), this.settings, SkyIslandSavedPresetRepository.PresetSource.BUILT_IN
         );
      }
   }

   private record BuiltInPresetDefinition(String stem, String name, String description) {
   }

   public record ListResult(List<SkyIslandSavedPresetRepository.SavedPresetSummary> presets, List<String> warnings) {
   }

   public record OperationResult(boolean success, String message, Path path) {
      static SkyIslandSavedPresetRepository.OperationResult success(Path path) {
         return new SkyIslandSavedPresetRepository.OperationResult(true, "", path);
      }

      static SkyIslandSavedPresetRepository.OperationResult error(String message) {
         return new SkyIslandSavedPresetRepository.OperationResult(false, message, null);
      }
   }

   public enum PresetSource {
      BUILT_IN(0, "Built-in"),
      USER_SAVED(1, "Saved");

      private final int sortOrder;
      private final String displayLabel;

      PresetSource(int sortOrder, String displayLabel) {
         this.sortOrder = sortOrder;
         this.displayLabel = displayLabel;
      }

      public int sortOrder() {
         return this.sortOrder;
      }

      public String displayLabel() {
         return this.displayLabel;
      }
   }

   public record SavedPreset(
      String name, String stem, String description, SkyIslandGeneratorSettings settings, SkyIslandSavedPresetRepository.PresetSource source
   ) {
      public SavedPreset(String name, String stem, String description, SkyIslandGeneratorSettings settings) {
         this(name, stem, description, settings, SkyIslandSavedPresetRepository.PresetSource.USER_SAVED);
      }

      public boolean builtIn() {
         return this.source == SkyIslandSavedPresetRepository.PresetSource.BUILT_IN;
      }
   }

   public record SavedPresetSummary(String name, String stem, String description, SkyIslandSavedPresetRepository.PresetSource source) {
      public SavedPresetSummary(String name, String stem, String description) {
         this(name, stem, description, SkyIslandSavedPresetRepository.PresetSource.USER_SAVED);
      }

      public boolean builtIn() {
         return this.source == SkyIslandSavedPresetRepository.PresetSource.BUILT_IN;
      }

      public String displayName() {
         return this.builtIn() ? "[" + this.source.displayLabel() + "] " + this.name : this.name;
      }
   }

   private record Validation(boolean valid, String displayName, String stem, String message) {
      static SkyIslandSavedPresetRepository.Validation valid(String displayName, String stem) {
         return new SkyIslandSavedPresetRepository.Validation(true, displayName, stem, "");
      }

      static SkyIslandSavedPresetRepository.Validation invalid(String message) {
         return new SkyIslandSavedPresetRepository.Validation(false, "", "", message);
      }
   }
}
