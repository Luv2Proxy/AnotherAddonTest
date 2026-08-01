package org.sathrek.sky_archipelago.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.fml.loading.FMLPaths;

public final class SkyIslandDefaultPresetStore {
   private static final String FILE_NAME = "default-preset.toml";
   private static final String KEY = "default_preset";
   private static final Pattern DEFAULT_PRESET_PATTERN = Pattern.compile("^\\s*default_preset\\s*=\\s*\"([^\"]*)\"\\s*$");
   private final Path filePath;

   public SkyIslandDefaultPresetStore(Path filePath) {
      this.filePath = filePath;
   }

   public static SkyIslandDefaultPresetStore atDefaultLocation() {
      return new SkyIslandDefaultPresetStore(FMLPaths.CONFIGDIR.get().resolve("sky_archipelago").resolve("default-preset.toml"));
   }

   public Optional<String> loadDefaultPresetStem() {
      if (!Files.exists(this.filePath)) {
         return Optional.empty();
      }

      try {
         for (String line : Files.readAllLines(this.filePath, StandardCharsets.UTF_8)) {
            Matcher matcher = DEFAULT_PRESET_PATTERN.matcher(line);
            if (matcher.matches()) {
               String stem = matcher.group(1).trim();
               return stem.isBlank() ? Optional.empty() : Optional.of(stem);
            }
         }
      } catch (IOException exception) {
         return Optional.empty();
      }

      return Optional.empty();
   }

   public boolean hasDefaultPresetFile() {
      return Files.exists(this.filePath);
   }

   public Optional<SkyIslandGeneratorSettings> resolveDefaultSettings(SkyIslandSavedPresetRepository repository) {
      return repository == null
         ? Optional.empty()
         : this.loadDefaultPresetStem().flatMap(repository::loadPreset).map(SkyIslandSavedPresetRepository.SavedPreset::settings);
   }

   public SkyIslandDefaultPresetStore.OperationResult saveDefaultPresetStem(String stem) {
      String trimmed = stem == null ? "" : stem.trim();
      if (trimmed.isBlank()) {
         return SkyIslandDefaultPresetStore.OperationResult.error("Preset name is required.");
      }

      try {
         Files.createDirectories(this.filePath.getParent());
         String payload = "# Sky Archipelago default preset\ndefault_preset = \"" + trimmed.replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n";
         Files.writeString(this.filePath, payload, StandardCharsets.UTF_8);
         return SkyIslandDefaultPresetStore.OperationResult.success(this.filePath);
      } catch (IOException exception) {
         return SkyIslandDefaultPresetStore.OperationResult.error("Unable to save default preset: " + exception.getMessage());
      }
   }

   public SkyIslandDefaultPresetStore.OperationResult clearDefaultPreset() {
      try {
         if (Files.exists(this.filePath)) {
            Files.delete(this.filePath);
         }

         return SkyIslandDefaultPresetStore.OperationResult.success(this.filePath);
      } catch (IOException exception) {
         return SkyIslandDefaultPresetStore.OperationResult.error("Unable to clear default preset: " + exception.getMessage());
      }
   }

   public record OperationResult(boolean success, String message, Path path) {
      static SkyIslandDefaultPresetStore.OperationResult success(Path path) {
         return new SkyIslandDefaultPresetStore.OperationResult(true, "", path);
      }

      static SkyIslandDefaultPresetStore.OperationResult error(String message) {
         return new SkyIslandDefaultPresetStore.OperationResult(false, message, null);
      }
   }
}
