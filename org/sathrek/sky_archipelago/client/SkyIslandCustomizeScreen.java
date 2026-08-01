package org.sathrek.sky_archipelago.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList.Entry;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.Component;
import org.sathrek.sky_archipelago.config.ClusterSpacingMode;
import org.sathrek.sky_archipelago.config.IslandSizeBand;
import org.sathrek.sky_archipelago.config.IslandSizeMode;
import org.sathrek.sky_archipelago.config.OceanBlockType;
import org.sathrek.sky_archipelago.config.SkyIslandConfig;
import org.sathrek.sky_archipelago.config.SkyIslandDefaultPresetStore;
import org.sathrek.sky_archipelago.config.SkyIslandGeneratorSettings;
import org.sathrek.sky_archipelago.config.SkyIslandSavedPresetRepository;
import org.sathrek.sky_archipelago.config.TerrainOverlapMode;
import org.sathrek.sky_archipelago.config.settings.IslandSizeSettings;
import org.sathrek.sky_archipelago.config.settings.SkyIslandSettingsFactory;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

public final class SkyIslandCustomizeScreen extends Screen {
   private static final Component TITLE = Component.literal("Customize Sky Islands");
   private static final Component ADVANCED_HINT = Component.literal("Advanced contains archetype mix, overlap behavior, ocean block type, and fallback tuning.");
   private static final int ROW_HEIGHT = 60;
   private static final int LIST_SIDE_PADDING = 20;
   private static final int VALUE_BOX_MIN_WIDTH = 116;
   private static final int VALUE_BOX_MAX_WIDTH = 320;
   private static final int BUTTON_HEIGHT = 20;
   private static final int BUTTON_GAP = 6;
   private static final List<String> MAIN_FIELD_IDS = List.of(
      "islandDensity",
      "clusterSpacingMode",
      "clusterSpacing",
      "minClusterSpacing",
      "maxClusterSpacing",
      "islandSizeMode",
      "minIslandRadius",
      "maxIslandRadius",
      "smallIslandMinRadius",
      "smallIslandMaxRadius",
      "smallIslandWeight",
      "mediumIslandMinRadius",
      "mediumIslandMaxRadius",
      "mediumIslandWeight",
      "largeIslandMinRadius",
      "largeIslandMaxRadius",
      "largeIslandWeight",
      "minIslandY",
      "maxIslandY",
      "maxIslandThicknessBlocks",
      "lowBandWeight",
      "midHighBandWeight",
      "veryHighBandWeight",
      "oceanEnabled",
      "oceanLevelY"
   );
   private static final SkyIslandCustomizeScreen.FieldSpec ISLAND_DENSITY = SkyIslandCustomizeScreen.FieldSpec.doubleField(
      "islandDensity", "Island spawn density", "Density", 0.01, 1.0, m -> m.islandDensity, (m, value) -> m.islandDensity = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec CLUSTER_SPACING = SkyIslandCustomizeScreen.FieldSpec.intField(
      "clusterSpacing", "Island cluster spacing", "Spacing", 32, 2000, m -> m.clusterSpacing, (m, value) -> m.clusterSpacing = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MIN_CLUSTER_SPACING = SkyIslandCustomizeScreen.FieldSpec.intField(
      "minClusterSpacing", "Min cluster spacing", "Min spacing", 32, 2000, m -> m.minClusterSpacing, (m, value) -> m.minClusterSpacing = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MAX_CLUSTER_SPACING = SkyIslandCustomizeScreen.FieldSpec.intField(
      "maxClusterSpacing", "Max cluster spacing", "Max spacing", 32, 2000, m -> m.maxClusterSpacing, (m, value) -> m.maxClusterSpacing = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MIN_ISLAND_RADIUS = SkyIslandCustomizeScreen.FieldSpec.intField(
      "minIslandRadius", "Min island radius", "Min radius", 8, 500, m -> m.minIslandRadius, (m, value) -> m.minIslandRadius = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MAX_ISLAND_RADIUS = SkyIslandCustomizeScreen.FieldSpec.intField(
      "maxIslandRadius", "Max island radius", "Max radius", 8, 500, m -> m.maxIslandRadius, (m, value) -> m.maxIslandRadius = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec SMALL_ISLAND_MIN_RADIUS = SkyIslandCustomizeScreen.FieldSpec.intField(
      "smallIslandMinRadius", "Small min radius", "Small min", 8, 500, m -> m.smallIslandMinRadius, (m, value) -> m.smallIslandMinRadius = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec SMALL_ISLAND_MAX_RADIUS = SkyIslandCustomizeScreen.FieldSpec.intField(
      "smallIslandMaxRadius", "Small max radius", "Small max", 8, 500, m -> m.smallIslandMaxRadius, (m, value) -> m.smallIslandMaxRadius = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec SMALL_ISLAND_WEIGHT = SkyIslandCustomizeScreen.FieldSpec.doubleField(
      "smallIslandWeight", "Small spawn weight", "Small wt", 0.0, 1.0, m -> m.smallIslandWeight, (m, value) -> m.smallIslandWeight = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MEDIUM_ISLAND_MIN_RADIUS = SkyIslandCustomizeScreen.FieldSpec.intField(
      "mediumIslandMinRadius", "Medium min radius", "Med min", 8, 500, m -> m.mediumIslandMinRadius, (m, value) -> m.mediumIslandMinRadius = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MEDIUM_ISLAND_MAX_RADIUS = SkyIslandCustomizeScreen.FieldSpec.intField(
      "mediumIslandMaxRadius", "Medium max radius", "Med max", 8, 500, m -> m.mediumIslandMaxRadius, (m, value) -> m.mediumIslandMaxRadius = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MEDIUM_ISLAND_WEIGHT = SkyIslandCustomizeScreen.FieldSpec.doubleField(
      "mediumIslandWeight", "Medium spawn weight", "Med wt", 0.0, 1.0, m -> m.mediumIslandWeight, (m, value) -> m.mediumIslandWeight = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec LARGE_ISLAND_MIN_RADIUS = SkyIslandCustomizeScreen.FieldSpec.intField(
      "largeIslandMinRadius", "Large min radius", "Large min", 8, 500, m -> m.largeIslandMinRadius, (m, value) -> m.largeIslandMinRadius = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec LARGE_ISLAND_MAX_RADIUS = SkyIslandCustomizeScreen.FieldSpec.intField(
      "largeIslandMaxRadius", "Large max radius", "Large max", 8, 500, m -> m.largeIslandMaxRadius, (m, value) -> m.largeIslandMaxRadius = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec LARGE_ISLAND_WEIGHT = SkyIslandCustomizeScreen.FieldSpec.doubleField(
      "largeIslandWeight", "Large spawn weight", "Large wt", 0.0, 1.0, m -> m.largeIslandWeight, (m, value) -> m.largeIslandWeight = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MIN_ISLAND_Y = SkyIslandCustomizeScreen.FieldSpec.intField(
      "minIslandY", "Min island Y", "Min Y", -32, 2000, m -> m.minIslandY, (m, value) -> m.minIslandY = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MAX_ISLAND_Y = SkyIslandCustomizeScreen.FieldSpec.intField(
      "maxIslandY", "Max island Y", "Max Y", -32, 2000, m -> m.maxIslandY, (m, value) -> m.maxIslandY = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MAX_ISLAND_THICKNESS_BLOCKS = new SkyIslandCustomizeScreen.FieldSpec(
      "maxIslandThicknessBlocks",
      "Max island thickness",
      "Thickness",
      "Range: 24 to 1024 (full island height, top-to-bottom; higher values may impact performance)",
      SkyIslandCustomizeScreen.ControlType.TEXT,
      m -> Integer.toString(m.maxIslandThicknessBlocks),
      (model, rawValue) -> {
         int parsed;
         try {
            parsed = Integer.parseInt(rawValue.trim());
         } catch (NumberFormatException ex) {
            return "Max island thickness must be an integer.";
         }

         if (parsed >= 24 && parsed <= 1024) {
            model.maxIslandThicknessBlocks = parsed;
            return null;
         } else {
            return "Max island thickness must be between 24 and 1024.";
         }
      },
      null,
      null
   );
   private static final SkyIslandCustomizeScreen.FieldSpec LOW_BAND_WEIGHT = SkyIslandCustomizeScreen.FieldSpec.doubleField(
      "lowBandWeight", "Low band spawn weight", "Low", 0.0, 10.0, m -> m.lowBandWeight, (m, value) -> m.lowBandWeight = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec MID_BAND_WEIGHT = SkyIslandCustomizeScreen.FieldSpec.doubleField(
      "midHighBandWeight", "Mid band spawn weight", "Mid", 0.0, 10.0, m -> m.midHighBandWeight, (m, value) -> m.midHighBandWeight = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec HIGH_BAND_WEIGHT = SkyIslandCustomizeScreen.FieldSpec.doubleField(
      "veryHighBandWeight", "High band spawn weight", "High", 0.0, 10.0, m -> m.veryHighBandWeight, (m, value) -> m.veryHighBandWeight = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec OCEAN_ENABLED = SkyIslandCustomizeScreen.FieldSpec.booleanField(
      "oceanEnabled", "Ocean enabled", "Ocean", m -> m.oceanEnabled, (m, value) -> m.oceanEnabled = value
   );
   private static final SkyIslandCustomizeScreen.FieldSpec OCEAN_LEVEL_Y = SkyIslandCustomizeScreen.FieldSpec.intField(
      "oceanLevelY", "Ocean level Y", "Ocean level", -64, 2000, m -> m.oceanLevelY, (m, value) -> m.oceanLevelY = value
   );
   private final CreateWorldScreen parentScreen;
   private final SkyIslandCustomizeScreen.SettingsModel model;
   private SkyIslandCustomizeScreen.FieldList fieldList;
   private Component errorMessage;
   private int footerTopY;
   private int footerLeftX;
   private int footerWidth;

   public SkyIslandCustomizeScreen(CreateWorldScreen parentScreen, WorldCreationContext context) {
      super(TITLE);
      this.parentScreen = parentScreen;
      this.model = SkyIslandCustomizeScreen.SettingsModel.from(resolveInitialSettings(context));
   }

   protected void init() {
      int contentWidth = Math.max(220, this.width - 40);
      int centerX = this.width / 2;
      int buttonWidth = SkyIslandLayoutHelper.isNarrowWidth(contentWidth) ? Math.max(92, Math.min(130, contentWidth - 24)) : 110;
      List<SkyIslandLayoutHelper.ButtonBounds> buttonBounds = SkyIslandLayoutHelper.layoutButtonGridFromBottom(
         centerX, this.height - 10, contentWidth, buttonWidth, 20, 6, 5
      );
      int buttonTopY = buttonBounds.getFirst().y();
      int lineHeight = 9 + 2;
      int hintLines = SkyIslandLayoutHelper.wrappedLineCount(this.font, ADVANCED_HINT, contentWidth);
      int errorLines = this.errorMessage != null ? SkyIslandLayoutHelper.wrappedLineCount(this.font, this.errorMessage, contentWidth) : 0;
      int footerTextHeight = (hintLines + errorLines) * lineHeight + (errorLines > 0 ? 4 : 0);
      this.footerTopY = buttonTopY - 10 - footerTextHeight;
      this.footerLeftX = centerX - contentWidth / 2;
      this.footerWidth = contentWidth;
      int listTop = 34;
      int listBottomY = Math.max(listTop + 96, this.footerTopY - 8);
      int listHeight = listBottomY - listTop;
      this.fieldList = new SkyIslandCustomizeScreen.FieldList(this.minecraft, this.width, listHeight, listTop, 60);
      this.buildRows();
      this.syncFieldEntriesFromModel();
      this.addRenderableWidget(this.fieldList);
      this.addRenderableWidget(
         Button.builder(Component.literal("Advanced..."), button -> this.openAdvancedScreen())
            .bounds(buttonBounds.get(0).x(), buttonBounds.get(0).y(), buttonBounds.get(0).width(), buttonBounds.get(0).height())
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Presets..."), button -> this.openPresetScreen())
            .bounds(buttonBounds.get(1).x(), buttonBounds.get(1).y(), buttonBounds.get(1).width(), buttonBounds.get(1).height())
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Reset defaults"), button -> this.resetToDefaults())
            .bounds(buttonBounds.get(2).x(), buttonBounds.get(2).y(), buttonBounds.get(2).width(), buttonBounds.get(2).height())
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Done"), button -> this.finishAndApply())
            .bounds(buttonBounds.get(3).x(), buttonBounds.get(3).y(), buttonBounds.get(3).width(), buttonBounds.get(3).height())
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Cancel"), button -> this.minecraft.setScreen(this.parentScreen))
            .bounds(buttonBounds.get(4).x(), buttonBounds.get(4).y(), buttonBounds.get(4).width(), buttonBounds.get(4).height())
            .build()
      );
   }

   private void buildRows() {
      this.fieldList.addFormEntry(new SkyIslandCustomizeScreen.SectionHeaderEntry("Island Layout", "Density, spacing, and footprint."));
      this.fieldList.addFormEntry(new SkyIslandCustomizeScreen.SingleFieldEntry(ISLAND_DENSITY, this.model));
      this.fieldList.addFormEntry(new SkyIslandCustomizeScreen.ClusterSpacingModeEntry());
      if (this.model.clusterSpacingMode == ClusterSpacingMode.CONSISTENT) {
         this.fieldList.addFormEntry(new SkyIslandCustomizeScreen.SingleFieldEntry(CLUSTER_SPACING, this.model));
      } else {
         this.fieldList
            .addFormEntry(
               new SkyIslandCustomizeScreen.MultiFieldEntry(
                  "Cluster spacing range", "Min/Max spacing: 32 to 2000", List.of(MIN_CLUSTER_SPACING, MAX_CLUSTER_SPACING), this.model
               )
            );
      }

      this.fieldList.addFormEntry(new SkyIslandCustomizeScreen.SizeModeEntry());
      if (this.model.islandSizeMode == IslandSizeMode.RANDOM) {
         this.fieldList
            .addFormEntry(
               new SkyIslandCustomizeScreen.MultiFieldEntry(
                  "Island radius", "Min: 8 to 500, Max: 8 to 500", List.of(MIN_ISLAND_RADIUS, MAX_ISLAND_RADIUS), this.model
               )
            );
      } else {
         this.fieldList
            .addFormEntry(
               new SkyIslandCustomizeScreen.MultiFieldEntry(
                  "Small islands (min/max/spawn %)",
                  "Radius: 8 to 500, spawn %: 0.0 to 1.0",
                  List.of(SMALL_ISLAND_MIN_RADIUS, SMALL_ISLAND_MAX_RADIUS, SMALL_ISLAND_WEIGHT),
                  this.model
               )
            );
         this.fieldList
            .addFormEntry(
               new SkyIslandCustomizeScreen.MultiFieldEntry(
                  "Medium islands (min/max/spawn %)",
                  "Radius: 8 to 500, spawn %: 0.0 to 1.0",
                  List.of(MEDIUM_ISLAND_MIN_RADIUS, MEDIUM_ISLAND_MAX_RADIUS, MEDIUM_ISLAND_WEIGHT),
                  this.model
               )
            );
         this.fieldList
            .addFormEntry(
               new SkyIslandCustomizeScreen.MultiFieldEntry(
                  "Large islands (min/max/spawn %)",
                  "Radius: 8 to 500, spawn %: 0.0 to 1.0",
                  List.of(LARGE_ISLAND_MIN_RADIUS, LARGE_ISLAND_MAX_RADIUS, LARGE_ISLAND_WEIGHT),
                  this.model
               )
            );
      }

      this.fieldList.addFormEntry(new SkyIslandCustomizeScreen.SectionHeaderEntry("Height Range", "Vertical spread and elevation mix."));
      this.fieldList
         .addFormEntry(
            new SkyIslandCustomizeScreen.MultiFieldEntry(
               "Island Y range",
               "Min: -32 to 2000, Max: -32 to 2000 (above 320 requires taller world height mods/settings)",
               List.of(MIN_ISLAND_Y, MAX_ISLAND_Y),
               this.model
            )
         );
      this.fieldList
         .addFormEntry(
            new SkyIslandCustomizeScreen.MultiFieldEntry(
               "Spawn height distribution", "Low, Mid, and High: 0.0 to 1.0", List.of(LOW_BAND_WEIGHT, MID_BAND_WEIGHT, HIGH_BAND_WEIGHT), this.model
            )
         );
      this.fieldList.addFormEntry(new SkyIslandCustomizeScreen.SingleFieldEntry(MAX_ISLAND_THICKNESS_BLOCKS, this.model));
      this.fieldList.addFormEntry(new SkyIslandCustomizeScreen.SectionHeaderEntry("Ocean", "Sea layer and level."));
      this.fieldList.addFormEntry(new SkyIslandCustomizeScreen.SingleFieldEntry(OCEAN_ENABLED, this.model));
      this.fieldList.addFormEntry(new SkyIslandCustomizeScreen.SingleFieldEntry(OCEAN_LEVEL_Y, this.model));
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 16777215);
      int footerTextY = this.footerTopY;
      if (this.errorMessage != null) {
         footerTextY = SkyIslandLayoutHelper.drawWrappedText(
               guiGraphics, this.font, this.errorMessage, this.footerLeftX, footerTextY, this.footerWidth, ChatFormatting.RED.getColor()
            )
            + 4;
      }

      SkyIslandLayoutHelper.drawWrappedText(guiGraphics, this.font, ADVANCED_HINT, this.footerLeftX, footerTextY, this.footerWidth, 11579568);
   }

   public boolean shouldCloseOnEsc() {
      return true;
   }

   public void onClose() {
      this.minecraft.setScreen(this.parentScreen);
   }

   private void openAdvancedScreen() {
      if (this.applyMainFields()) {
         this.minecraft.setScreen(new SkyIslandAdvancedCustomizeScreen(this, this.model));
      }
   }

   private void openPresetScreen() {
      if (this.applyMainFields()) {
         this.minecraft.setScreen(new SkyIslandPresetCustomizeScreen(this, this.model));
      }
   }

   void returnFromAdvanced() {
      this.errorMessage = null;
      this.syncFieldEntriesFromModel();
      this.minecraft.setScreen(this);
   }

   void returnFromPreset() {
      this.errorMessage = null;
      this.syncFieldEntriesFromModel();
      this.minecraft.setScreen(this);
   }

   void resetToDefaultsForTest() {
      this.resetToDefaults();
   }

   private void resetToDefaults() {
      SkyIslandGeneratorSettings defaults = resolveDefaultSettings();
      this.model.copyFrom(SkyIslandCustomizeScreen.SettingsModel.from(defaults));
      this.syncFieldEntriesFromModel();
      this.errorMessage = null;
   }

   static SkyIslandGeneratorSettings resolveDefaultSettings() {
      return resolveDefaultSettings(SkyIslandDefaultPresetStore.atDefaultLocation(), SkyIslandSavedPresetRepository.atDefaultLocation());
   }

   static SkyIslandGeneratorSettings resolveDefaultSettings(SkyIslandDefaultPresetStore store, SkyIslandSavedPresetRepository repository) {
      return store.resolveDefaultSettings(repository).orElseGet(SkyIslandConfig::defaultGeneratorSettings);
   }

   private void syncFieldEntriesFromModel() {
      if (this.fieldList != null) {
         for (SkyIslandCustomizeScreen.BaseEntry entry : this.fieldList.children()) {
            entry.resetFromModel(this.model);
         }
      }
   }

   private boolean applyMainFields() {
      for (SkyIslandCustomizeScreen.BaseEntry entry : this.fieldList.children()) {
         String result = entry.apply(this.model);
         if (result != null) {
            this.errorMessage = Component.literal(result);
            return false;
         }
      }

      if (this.model.islandSizeMode == IslandSizeMode.SPECIFIC) {
         if (this.model.smallIslandMinRadius > this.model.smallIslandMaxRadius
            || this.model.mediumIslandMinRadius > this.model.mediumIslandMaxRadius
            || this.model.largeIslandMinRadius > this.model.largeIslandMaxRadius) {
            this.errorMessage = Component.literal("Each specific island size must have min radius <= max radius.");
            return false;
         }

         double sum = this.model.smallIslandWeight + this.model.mediumIslandWeight + this.model.largeIslandWeight;
         if (Math.abs(sum - 1.0) > 0.001) {
            this.errorMessage = Component.literal("Specific island sizes spawn % must sum to 1.0.");
            return false;
         }
      }

      this.errorMessage = null;
      return true;
   }

   private void finishAndApply() {
      if (this.applyMainFields()) {
         SkyIslandGeneratorSettings settings;
         try {
            settings = this.model.toSettings();
         } catch (IllegalArgumentException exception) {
            this.errorMessage = Component.literal(exception.getMessage());
            return;
         }

         WorldCreationContext baseContext = this.parentScreen.getUiState().getSettings();
         this.parentScreen
            .getUiState()
            .setSettings(
               baseContext.withDimensions(
                  (registryAccess, worldDimensions) -> worldDimensions.overworld() instanceof SkyIslandChunkGenerator skyIslandChunkGenerator
                     ? worldDimensions.replaceOverworldGenerator(registryAccess, skyIslandChunkGenerator.withSkyIslandSettings(settings))
                     : worldDimensions
               )
            );
         this.minecraft.setScreen(this.parentScreen);
      }
   }

   private static SkyIslandGeneratorSettings resolveInitialSettings(WorldCreationContext context) {
      if (context.selectedDimensions().overworld() instanceof SkyIslandChunkGenerator skyIslandChunkGenerator) {
         Optional<SkyIslandGeneratorSettings> embeddedSettings = skyIslandChunkGenerator.embeddedSettings();
         if (embeddedSettings.isPresent() && !isBaselineSettings(embeddedSettings.get())) {
            return embeddedSettings.get();
         }
      }

      return resolveDefaultSettings();
   }

   static SkyIslandGeneratorSettings resolveInitialSettings(Optional<SkyIslandGeneratorSettings> embeddedSettings) {
      return embeddedSettings.orElseGet(SkyIslandCustomizeScreen::resolveDefaultSettings);
   }

   static SkyIslandGeneratorSettings resolveInitialSettings(
      Optional<SkyIslandGeneratorSettings> embeddedSettings, SkyIslandDefaultPresetStore store, SkyIslandSavedPresetRepository repository
   ) {
      return embeddedSettings.isPresent() && !isBaselineSettings(embeddedSettings.get()) ? embeddedSettings.get() : resolveDefaultSettings(store, repository);
   }

   private static boolean isBaselineSettings(SkyIslandGeneratorSettings settings) {
      return SkyIslandConfig.isStockEmbeddedSettings(settings);
   }

   static List<String> mainFieldIds() {
      return MAIN_FIELD_IDS;
   }

   private abstract static class BaseEntry extends Entry<SkyIslandCustomizeScreen.BaseEntry> {
      void resetFromModel(SkyIslandCustomizeScreen.SettingsModel model) {
      }

      String apply(SkyIslandCustomizeScreen.SettingsModel model) {
         return null;
      }
   }

   @FunctionalInterface
   private interface BooleanGetter {
      boolean get(SkyIslandCustomizeScreen.SettingsModel var1);
   }

   @FunctionalInterface
   private interface BooleanSetter {
      void set(SkyIslandCustomizeScreen.SettingsModel var1, boolean var2);
   }

   private final class ClusterSpacingModeEntry extends SkyIslandCustomizeScreen.BaseEntry {
      private final CycleButton<ClusterSpacingMode> modeToggle = CycleButton.builder(mode -> Component.literal(mode.name()))
         .withValues(List.of(ClusterSpacingMode.CONSISTENT, ClusterSpacingMode.DYNAMIC))
         .withInitialValue(SkyIslandCustomizeScreen.this.model.clusterSpacingMode)
         .create(0, 0, 160, 20, Component.literal("Cluster spacing mode"), (button, value) -> {
            SkyIslandCustomizeScreen.this.model.clusterSpacingMode = value;
            SkyIslandCustomizeScreen.this.errorMessage = null;
            SkyIslandCustomizeScreen.this.minecraft.setScreen(SkyIslandCustomizeScreen.this);
         });

      ClusterSpacingModeEntry() {
      }

      @Override
      void resetFromModel(SkyIslandCustomizeScreen.SettingsModel model) {
         this.modeToggle.setValue(model.clusterSpacingMode);
      }

      @Override
      String apply(SkyIslandCustomizeScreen.SettingsModel model) {
         model.clusterSpacingMode = (ClusterSpacingMode)this.modeToggle.getValue();
         return null;
      }

      public List<? extends GuiEventListener> children() {
         return List.of(this.modeToggle);
      }

      public List<? extends NarratableEntry> narratables() {
         return List.of(this.modeToggle);
      }

      public void render(
         GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick
      ) {
         Font font = Minecraft.getInstance().font;
         guiGraphics.drawString(font, "Cluster spacing mode", left + 4, top + 4, 14737632);
         SkyIslandLayoutHelper.drawWrappedText(
            guiGraphics,
            font,
            Component.literal("CONSISTENT: Uses fixed cluster spacing.\nDYNAMIC: Chooses deterministic spacing from Min/Max per world seed."),
            left + 4,
            top + 16,
            Math.max(80, width / 2 - 12),
            9474192
         );
         int controlWidth = Math.min(320, Math.max(116, width / 3));
         this.modeToggle.setWidth(controlWidth);
         this.modeToggle.setX(left + width - controlWidth - 4);
         this.modeToggle.setY(top + 18);
         this.modeToggle.render(guiGraphics, mouseX, mouseY, partialTick);
      }
   }

   private enum ControlType {
      TEXT,
      BOOLEAN;
   }

   @FunctionalInterface
   private interface DoubleGetter {
      double get(SkyIslandCustomizeScreen.SettingsModel var1);
   }

   @FunctionalInterface
   private interface DoubleSetter {
      void set(SkyIslandCustomizeScreen.SettingsModel var1, double var2);
   }

   @FunctionalInterface
   private interface FieldApplier {
      String apply(SkyIslandCustomizeScreen.SettingsModel var1, String var2);
   }

   private static final class FieldList extends ContainerObjectSelectionList<SkyIslandCustomizeScreen.BaseEntry> {
      FieldList(Minecraft minecraft, int width, int y, int yEnd, int itemHeight) {
         super(minecraft, width, y, yEnd, itemHeight);
      }

      void addFormEntry(SkyIslandCustomizeScreen.BaseEntry entry) {
         this.addEntry(entry);
      }

      public int getRowWidth() {
         return Math.max(220, this.width - 40);
      }
   }

   private record FieldSpec(
      String id,
      String label,
      String shortLabel,
      String rangeHint,
      SkyIslandCustomizeScreen.ControlType controlType,
      Function<SkyIslandCustomizeScreen.SettingsModel, String> valueProvider,
      SkyIslandCustomizeScreen.FieldApplier applier,
      SkyIslandCustomizeScreen.BooleanGetter booleanGetter,
      SkyIslandCustomizeScreen.BooleanSetter booleanSetter
   ) {
      static SkyIslandCustomizeScreen.FieldSpec intField(
         String id, String label, String shortLabel, int min, int max, SkyIslandCustomizeScreen.IntGetter getter, SkyIslandCustomizeScreen.IntSetter setter
      ) {
         return new SkyIslandCustomizeScreen.FieldSpec(
            id,
            label,
            shortLabel,
            "Range: " + min + " to " + max,
            SkyIslandCustomizeScreen.ControlType.TEXT,
            model -> Integer.toString(getter.get(model)),
            (model, rawValue) -> {
               int parsed;
               try {
                  parsed = Integer.parseInt(rawValue.trim());
               } catch (NumberFormatException ex) {
                  return label + " must be an integer.";
               }

               if (parsed >= min && parsed <= max) {
                  setter.set(model, parsed);
                  return null;
               } else {
                  return label + " must be between " + min + " and " + max + ".";
               }
            },
            null,
            null
         );
      }

      static SkyIslandCustomizeScreen.FieldSpec doubleField(
         String id,
         String label,
         String shortLabel,
         double min,
         double max,
         SkyIslandCustomizeScreen.DoubleGetter getter,
         SkyIslandCustomizeScreen.DoubleSetter setter
      ) {
         return new SkyIslandCustomizeScreen.FieldSpec(
            id,
            label,
            shortLabel,
            "Range: " + min + " to " + max,
            SkyIslandCustomizeScreen.ControlType.TEXT,
            model -> Double.toString(getter.get(model)),
            (model, rawValue) -> {
               double parsed;
               try {
                  parsed = Double.parseDouble(rawValue.trim());
               } catch (NumberFormatException ex) {
                  return label + " must be a number.";
               }

               if (!(parsed < min) && !(parsed > max)) {
                  setter.set(model, parsed);
                  return null;
               } else {
                  return label + " must be between " + min + " and " + max + ".";
               }
            },
            null,
            null
         );
      }

      static SkyIslandCustomizeScreen.FieldSpec booleanField(
         String id, String label, String shortLabel, SkyIslandCustomizeScreen.BooleanGetter getter, SkyIslandCustomizeScreen.BooleanSetter setter
      ) {
         return new SkyIslandCustomizeScreen.FieldSpec(
            id,
            label,
            shortLabel,
            "Toggle on or off",
            SkyIslandCustomizeScreen.ControlType.BOOLEAN,
            model -> Boolean.toString(getter.get(model)),
            (model, rawValue) -> null,
            getter,
            setter
         );
      }

      String currentValue(SkyIslandCustomizeScreen.SettingsModel model) {
         return this.valueProvider.apply(model);
      }

      String currentValueText(SkyIslandGeneratorSettings defaults, SkyIslandCustomizeScreen.SettingsModel model) {
         return model != null ? this.currentValue(model) : this.valueProvider.apply(SkyIslandCustomizeScreen.SettingsModel.from(defaults));
      }

      boolean currentBooleanValue(SkyIslandCustomizeScreen.SettingsModel model) {
         return this.booleanGetter != null && this.booleanGetter.get(model);
      }

      void applyBoolean(SkyIslandCustomizeScreen.SettingsModel model, boolean value) {
         if (this.booleanSetter != null) {
            this.booleanSetter.set(model, value);
         }
      }

      String apply(SkyIslandCustomizeScreen.SettingsModel model, String rawValue) {
         return this.applier.apply(model, rawValue);
      }
   }

   @FunctionalInterface
   private interface IntGetter {
      int get(SkyIslandCustomizeScreen.SettingsModel var1);
   }

   @FunctionalInterface
   private interface IntSetter {
      void set(SkyIslandCustomizeScreen.SettingsModel var1, int var2);
   }

   private static final class MultiFieldEntry extends SkyIslandCustomizeScreen.BaseEntry {
      private final String label;
      private final String hint;
      private final List<SkyIslandCustomizeScreen.FieldSpec> specs;
      private final List<EditBox> boxes;

      MultiFieldEntry(String label, String hint, List<SkyIslandCustomizeScreen.FieldSpec> specs, SkyIslandCustomizeScreen.SettingsModel model) {
         this.label = label;
         this.hint = hint;
         this.specs = specs;
         this.boxes = specs.stream().map(spec -> {
            EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 20, Component.literal(spec.label));
            box.setMaxLength(120);
            box.setHint(Component.literal(spec.shortLabel));
            box.setValue(spec.currentValue(model));
            return box;
         }).toList();
      }

      @Override
      void resetFromModel(SkyIslandCustomizeScreen.SettingsModel model) {
         for (int index = 0; index < this.specs.size(); index++) {
            this.boxes.get(index).setValue(this.specs.get(index).currentValue(model));
         }
      }

      @Override
      String apply(SkyIslandCustomizeScreen.SettingsModel model) {
         for (int index = 0; index < this.specs.size(); index++) {
            String result = this.specs.get(index).apply(model, this.boxes.get(index).getValue());
            if (result != null) {
               return result;
            }
         }

         return null;
      }

      public List<? extends GuiEventListener> children() {
         return this.boxes;
      }

      public List<? extends NarratableEntry> narratables() {
         return this.boxes;
      }

      public void render(
         GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick
      ) {
         Font font = Minecraft.getInstance().font;
         int contentWidth = Math.max(160, width - 8);
         boolean narrow = SkyIslandLayoutHelper.isNarrowWidth(contentWidth);
         int gap = 6;
         int controlY = narrow ? top + height - 24 : top + 18;
         int boxWidth;
         int startX;
         if (narrow) {
            guiGraphics.drawString(font, this.label, left + 4, top + 4, 14737632);
            SkyIslandLayoutHelper.drawWrappedText(guiGraphics, font, Component.literal(this.hint), left + 4, top + 16, contentWidth - 8, 9474192);
            int availableWidth = Math.max(96, contentWidth - 8);
            boxWidth = Math.max(56, (availableWidth - gap * (this.boxes.size() - 1)) / this.boxes.size());
            int usedWidth = boxWidth * this.boxes.size() + gap * (this.boxes.size() - 1);
            startX = left + 4 + Math.max(0, (availableWidth - usedWidth) / 2);
         } else {
            guiGraphics.drawString(font, this.label, left + 4, top + 4, 14737632);
            SkyIslandLayoutHelper.drawWrappedText(
               guiGraphics, font, Component.literal(this.hint), left + 4, top + 16, Math.max(80, contentWidth / 2 - 12), 9474192
            );
            int availableWidth = Math.min(320 * this.boxes.size(), Math.max(96 * this.boxes.size() + gap * (this.boxes.size() - 1), width / 2));
            boxWidth = (availableWidth - gap * (this.boxes.size() - 1)) / this.boxes.size();
            startX = left + width - availableWidth - 4;
         }

         for (int boxIndex = 0; boxIndex < this.boxes.size(); boxIndex++) {
            EditBox box = this.boxes.get(boxIndex);
            box.setWidth(boxWidth);
            box.setX(startX + boxIndex * (boxWidth + gap));
            box.setY(controlY);
            box.render(guiGraphics, mouseX, mouseY, partialTick);
         }
      }
   }

   private static final class SectionHeaderEntry extends SkyIslandCustomizeScreen.BaseEntry {
      private final String title;
      private final String description;

      SectionHeaderEntry(String title, String description) {
         this.title = title;
         this.description = description;
      }

      public List<? extends GuiEventListener> children() {
         return List.of();
      }

      public List<? extends NarratableEntry> narratables() {
         return List.of();
      }

      public void render(
         GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick
      ) {
         Font font = Minecraft.getInstance().font;
         int lineY = top + 10;
         guiGraphics.fill(left + 4, lineY, left + width - 4, lineY + 1, 1895825407);
         guiGraphics.drawString(font, "[" + this.title.toUpperCase(Locale.ROOT) + "]", left + 8, top + 2, 15790320);
         SkyIslandLayoutHelper.drawWrappedText(guiGraphics, font, Component.literal(this.description), left + 8, top + 16, width - 16, 9474192);
      }
   }

   static final class SettingsModel {
      private SkyIslandGeneratorSettings baseSettings;
      double islandDensity;
      int minIslandRadius;
      int maxIslandRadius;
      IslandSizeMode islandSizeMode;
      int smallIslandMinRadius;
      int smallIslandMaxRadius;
      double smallIslandWeight;
      int mediumIslandMinRadius;
      int mediumIslandMaxRadius;
      double mediumIslandWeight;
      int largeIslandMinRadius;
      int largeIslandMaxRadius;
      double largeIslandWeight;
      int minIslandY;
      int maxIslandY;
      int maxIslandThicknessBlocks;
      boolean classicArchetypeEnabled;
      double classicArchetypeWeight;
      boolean bowlCraterArchetypeEnabled;
      double bowlCraterArchetypeWeight;
      boolean crescentArchetypeEnabled;
      double crescentArchetypeWeight;
      boolean terraceArchetypeEnabled;
      double terraceArchetypeWeight;
      boolean oceanEnabled;
      int oceanLevelY;
      boolean oceanFloorNoiseEnabled;
      int oceanFloorBaseOffset;
      int oceanFloorNoiseAmplitude;
      double oceanFloorNoiseScale;
      int oceanFloorMinDepth;
      int oceanFloorMaxDepth;
      OceanBlockType oceanBlockType;
      TerrainOverlapMode terrainOverlapMode;
      double lowBandWeight;
      double midHighBandWeight;
      double veryHighBandWeight;
      ClusterSpacingMode clusterSpacingMode;
      int clusterSpacing;
      int minClusterSpacing;
      int maxClusterSpacing;
      boolean defaultNearMissFallbackEnabled;
      boolean customStructureRulesEnabled;
      boolean clusterCompanionIslandsEnabled;
      boolean anchorFragmentationEnabled;
      boolean disableIslandsOverOceanBiomes;
      int deepslateStartY;
      List<String> structureWhitelistEntries;
      List<String> structureDenylistEntries;
      List<String> structureCategoryOverrideEntries;
      List<String> waterStructureCategoryTokens;
      List<String> skyStructureCategoryTokens;
      List<String> groundStructureCategoryTokens;
      List<String> undergroundStructureCategoryTokens;
      List<String> villageStructureCategoryTokens;
      List<String> strongholdStructureCategoryTokens;

      static SkyIslandCustomizeScreen.SettingsModel from(SkyIslandGeneratorSettings settings) {
         SkyIslandCustomizeScreen.SettingsModel model = new SkyIslandCustomizeScreen.SettingsModel();
         model.baseSettings = settings;
         model.islandDensity = settings.terrain().islandDensity();
         model.minIslandRadius = settings.terrain().minIslandRadius();
         model.maxIslandRadius = settings.terrain().maxIslandRadius();
         model.islandSizeMode = settings.terrain().islandSize().islandSizeMode();
         model.smallIslandMinRadius = settings.terrain().islandSize().smallIslandSizeBand().minRadius();
         model.smallIslandMaxRadius = settings.terrain().islandSize().smallIslandSizeBand().maxRadius();
         model.smallIslandWeight = settings.terrain().islandSize().smallIslandSizeBand().weight();
         model.mediumIslandMinRadius = settings.terrain().islandSize().mediumIslandSizeBand().minRadius();
         model.mediumIslandMaxRadius = settings.terrain().islandSize().mediumIslandSizeBand().maxRadius();
         model.mediumIslandWeight = settings.terrain().islandSize().mediumIslandSizeBand().weight();
         model.largeIslandMinRadius = settings.terrain().islandSize().largeIslandSizeBand().minRadius();
         model.largeIslandMaxRadius = settings.terrain().islandSize().largeIslandSizeBand().maxRadius();
         model.largeIslandWeight = settings.terrain().islandSize().largeIslandSizeBand().weight();
         model.minIslandY = settings.terrain().minIslandY();
         model.maxIslandY = settings.terrain().maxIslandY();
         model.maxIslandThicknessBlocks = settings.terrain().maxIslandThicknessBlocks();
         model.classicArchetypeEnabled = settings.terrain().archetypes().classicArchetypeEnabled();
         model.classicArchetypeWeight = settings.terrain().archetypes().classicArchetypeWeight();
         model.bowlCraterArchetypeEnabled = settings.terrain().archetypes().bowlCraterArchetypeEnabled();
         model.bowlCraterArchetypeWeight = settings.terrain().archetypes().bowlCraterArchetypeWeight();
         model.crescentArchetypeEnabled = settings.terrain().archetypes().crescentArchetypeEnabled();
         model.crescentArchetypeWeight = settings.terrain().archetypes().crescentArchetypeWeight();
         model.terraceArchetypeEnabled = settings.terrain().archetypes().terraceArchetypeEnabled();
         model.terraceArchetypeWeight = settings.terrain().archetypes().terraceArchetypeWeight();
         model.oceanEnabled = settings.terrain().ocean().oceanEnabled();
         model.oceanLevelY = settings.terrain().ocean().oceanLevelY();
         model.oceanFloorNoiseEnabled = settings.terrain().ocean().oceanFloorNoiseEnabled();
         model.oceanFloorBaseOffset = settings.terrain().ocean().oceanFloorBaseOffset();
         model.oceanFloorNoiseAmplitude = settings.terrain().ocean().oceanFloorNoiseAmplitude();
         model.oceanFloorNoiseScale = settings.terrain().ocean().oceanFloorNoiseScale();
         model.oceanFloorMinDepth = settings.terrain().ocean().oceanFloorMinDepth();
         model.oceanFloorMaxDepth = settings.terrain().ocean().oceanFloorMaxDepth();
         model.oceanBlockType = settings.advanced().oceanBlockType();
         model.terrainOverlapMode = settings.advanced().terrainOverlapMode();
         model.lowBandWeight = settings.terrain().lowBandWeight();
         model.midHighBandWeight = settings.terrain().midHighBandWeight();
         model.veryHighBandWeight = settings.terrain().veryHighBandWeight();
         model.clusterSpacingMode = settings.terrain().spacing().clusterSpacingMode();
         model.clusterSpacing = settings.terrain().spacing().clusterSpacing();
         model.minClusterSpacing = settings.terrain().spacing().minClusterSpacing();
         model.maxClusterSpacing = settings.terrain().spacing().maxClusterSpacing();
         model.defaultNearMissFallbackEnabled = settings.advanced().defaultNearMissFallbackEnabled();
         model.customStructureRulesEnabled = settings.advanced().customStructureRulesEnabled();
         model.clusterCompanionIslandsEnabled = settings.advanced().clusterCompanionIslandsEnabled();
         model.anchorFragmentationEnabled = settings.advanced().anchorFragmentationEnabled();
         model.disableIslandsOverOceanBiomes = settings.advanced().disableIslandsOverOceanBiomes();
         model.deepslateStartY = settings.advanced().deepslateStartY();
         model.structureWhitelistEntries = new ArrayList<>(settings.advanced().structureWhitelistEntries());
         model.structureDenylistEntries = new ArrayList<>(settings.advanced().structureDenylistEntries());
         model.structureCategoryOverrideEntries = new ArrayList<>(settings.advanced().structureCategoryOverrideEntries());
         model.waterStructureCategoryTokens = new ArrayList<>(settings.advanced().waterStructureCategoryTokens());
         model.skyStructureCategoryTokens = new ArrayList<>(settings.advanced().skyStructureCategoryTokens());
         model.groundStructureCategoryTokens = new ArrayList<>(settings.advanced().groundStructureCategoryTokens());
         model.undergroundStructureCategoryTokens = new ArrayList<>(settings.advanced().undergroundStructureCategoryTokens());
         model.villageStructureCategoryTokens = new ArrayList<>(settings.advanced().villageStructureCategoryTokens());
         model.strongholdStructureCategoryTokens = new ArrayList<>(settings.advanced().strongholdStructureCategoryTokens());
         return model;
      }

      void copyFrom(SkyIslandCustomizeScreen.SettingsModel other) {
         this.baseSettings = other.baseSettings;
         this.islandDensity = other.islandDensity;
         this.minIslandRadius = other.minIslandRadius;
         this.maxIslandRadius = other.maxIslandRadius;
         this.islandSizeMode = other.islandSizeMode;
         this.smallIslandMinRadius = other.smallIslandMinRadius;
         this.smallIslandMaxRadius = other.smallIslandMaxRadius;
         this.smallIslandWeight = other.smallIslandWeight;
         this.mediumIslandMinRadius = other.mediumIslandMinRadius;
         this.mediumIslandMaxRadius = other.mediumIslandMaxRadius;
         this.mediumIslandWeight = other.mediumIslandWeight;
         this.largeIslandMinRadius = other.largeIslandMinRadius;
         this.largeIslandMaxRadius = other.largeIslandMaxRadius;
         this.largeIslandWeight = other.largeIslandWeight;
         this.minIslandY = other.minIslandY;
         this.maxIslandY = other.maxIslandY;
         this.maxIslandThicknessBlocks = other.maxIslandThicknessBlocks;
         this.classicArchetypeEnabled = other.classicArchetypeEnabled;
         this.classicArchetypeWeight = other.classicArchetypeWeight;
         this.bowlCraterArchetypeEnabled = other.bowlCraterArchetypeEnabled;
         this.bowlCraterArchetypeWeight = other.bowlCraterArchetypeWeight;
         this.crescentArchetypeEnabled = other.crescentArchetypeEnabled;
         this.crescentArchetypeWeight = other.crescentArchetypeWeight;
         this.terraceArchetypeEnabled = other.terraceArchetypeEnabled;
         this.terraceArchetypeWeight = other.terraceArchetypeWeight;
         this.oceanEnabled = other.oceanEnabled;
         this.oceanLevelY = other.oceanLevelY;
         this.oceanFloorNoiseEnabled = other.oceanFloorNoiseEnabled;
         this.oceanFloorBaseOffset = other.oceanFloorBaseOffset;
         this.oceanFloorNoiseAmplitude = other.oceanFloorNoiseAmplitude;
         this.oceanFloorNoiseScale = other.oceanFloorNoiseScale;
         this.oceanFloorMinDepth = other.oceanFloorMinDepth;
         this.oceanFloorMaxDepth = other.oceanFloorMaxDepth;
         this.oceanBlockType = other.oceanBlockType;
         this.terrainOverlapMode = other.terrainOverlapMode;
         this.lowBandWeight = other.lowBandWeight;
         this.midHighBandWeight = other.midHighBandWeight;
         this.veryHighBandWeight = other.veryHighBandWeight;
         this.clusterSpacingMode = other.clusterSpacingMode;
         this.clusterSpacing = other.clusterSpacing;
         this.minClusterSpacing = other.minClusterSpacing;
         this.maxClusterSpacing = other.maxClusterSpacing;
         this.defaultNearMissFallbackEnabled = other.defaultNearMissFallbackEnabled;
         this.customStructureRulesEnabled = other.customStructureRulesEnabled;
         this.clusterCompanionIslandsEnabled = other.clusterCompanionIslandsEnabled;
         this.anchorFragmentationEnabled = other.anchorFragmentationEnabled;
         this.disableIslandsOverOceanBiomes = other.disableIslandsOverOceanBiomes;
         this.deepslateStartY = other.deepslateStartY;
         this.structureWhitelistEntries = new ArrayList<>(other.structureWhitelistEntries);
         this.structureDenylistEntries = new ArrayList<>(other.structureDenylistEntries);
         this.structureCategoryOverrideEntries = new ArrayList<>(other.structureCategoryOverrideEntries);
         this.waterStructureCategoryTokens = new ArrayList<>(other.waterStructureCategoryTokens);
         this.skyStructureCategoryTokens = new ArrayList<>(other.skyStructureCategoryTokens);
         this.groundStructureCategoryTokens = new ArrayList<>(other.groundStructureCategoryTokens);
         this.undergroundStructureCategoryTokens = new ArrayList<>(other.undergroundStructureCategoryTokens);
         this.villageStructureCategoryTokens = new ArrayList<>(other.villageStructureCategoryTokens);
         this.strongholdStructureCategoryTokens = new ArrayList<>(other.strongholdStructureCategoryTokens);
      }

      SkyIslandGeneratorSettings toSettings() {
         SkyIslandGeneratorSettings settings = this.baseSettings;
         IslandSizeBand smallBand;
         IslandSizeBand mediumBand;
         IslandSizeBand largeBand;
         if (this.islandSizeMode == IslandSizeMode.RANDOM) {
            smallBand = IslandSizeSettings.deriveSmallBand(this.minIslandRadius, this.maxIslandRadius);
            mediumBand = IslandSizeSettings.deriveMediumBand(this.minIslandRadius, this.maxIslandRadius);
            largeBand = IslandSizeSettings.deriveLargeBand(this.minIslandRadius, this.maxIslandRadius);
         } else {
            smallBand = new IslandSizeBand(this.smallIslandMinRadius, this.smallIslandMaxRadius, this.smallIslandWeight);
            mediumBand = new IslandSizeBand(this.mediumIslandMinRadius, this.mediumIslandMaxRadius, this.mediumIslandWeight);
            largeBand = new IslandSizeBand(this.largeIslandMinRadius, this.largeIslandMaxRadius, this.largeIslandWeight);
         }

         return SkyIslandSettingsFactory.generator(
            this.islandDensity,
            this.minIslandRadius,
            this.maxIslandRadius,
            this.islandSizeMode,
            smallBand,
            mediumBand,
            largeBand,
            this.minIslandY,
            this.maxIslandY,
            this.maxIslandThicknessBlocks,
            this.lowBandWeight,
            this.midHighBandWeight,
            this.veryHighBandWeight,
            settings.terrain().lowBandCenterOffset(),
            settings.terrain().veryHighBandCenterOffset(),
            this.clusterSpacingMode,
            this.clusterSpacing,
            this.minClusterSpacing,
            this.maxClusterSpacing,
            settings.terrain().terrainReliefScale(),
            this.classicArchetypeEnabled,
            this.classicArchetypeWeight,
            this.bowlCraterArchetypeEnabled,
            this.bowlCraterArchetypeWeight,
            this.crescentArchetypeEnabled,
            this.crescentArchetypeWeight,
            this.terraceArchetypeEnabled,
            this.terraceArchetypeWeight,
            this.oceanEnabled,
            this.oceanLevelY,
            this.oceanFloorNoiseEnabled,
            this.oceanFloorBaseOffset,
            this.oceanFloorNoiseAmplitude,
            this.oceanFloorNoiseScale,
            this.oceanFloorMinDepth,
            this.oceanFloorMaxDepth,
            this.oceanBlockType,
            settings.structureSupport().supportCheckDepth(),
            settings.structureSupport().supportSampleGridSize(),
            settings.structureSupport().supportThreshold(),
            settings.surfaceSky().supportThreshold(),
            settings.smallSky().supportThreshold(),
            settings.surfaceSky().footprintInsetRatio(),
            settings.smallSky().footprintInsetRatio(),
            settings.surfaceSky().searchRadiusChunks(),
            settings.smallSky().searchRadiusChunks(),
            settings.surfaceSky().minStableTopCells(),
            settings.smallSky().minStableTopCells(),
            settings.surfaceSky().topOffset(),
            settings.smallSky().topOffset(),
            settings.surfaceSky().localSearchStepBlocks(),
            settings.smallSky().localSearchStepBlocks(),
            settings.surfaceSky().localSearchRadiusBlocks(),
            settings.smallSky().localSearchRadiusBlocks(),
            settings.surfaceSky().groundedSampleThreshold(),
            settings.smallSky().groundedSampleThreshold(),
            settings.surfaceSky().maxGroundGapBlocks(),
            settings.smallSky().maxGroundGapBlocks(),
            settings.surfaceSky().minHostIslandRadius(),
            settings.smallSky().minHostIslandRadius(),
            settings.surfaceSky().minHostStableTopCells(),
            settings.smallSky().minHostStableTopCells(),
            this.defaultNearMissFallbackEnabled,
            this.customStructureRulesEnabled,
            this.clusterCompanionIslandsEnabled,
            this.anchorFragmentationEnabled,
            this.disableIslandsOverOceanBiomes,
            this.deepslateStartY,
            settings.advanced().biomeProfileBlendingEnabled(),
            settings.advanced().biomeProfileBlendingRadiusBlocks(),
            settings.advanced().biomeProfileBlendingQuantizationSteps(),
            settings.advanced().biomeProfileBlendingBoundaryOnly(),
            this.terrainOverlapMode,
            this.structureWhitelistEntries,
            this.structureDenylistEntries,
            this.structureCategoryOverrideEntries,
            this.waterStructureCategoryTokens,
            this.skyStructureCategoryTokens,
            this.groundStructureCategoryTokens,
            this.undergroundStructureCategoryTokens,
            this.villageStructureCategoryTokens,
            this.strongholdStructureCategoryTokens
         );
      }
   }

   private static final class SingleFieldEntry extends SkyIslandCustomizeScreen.BaseEntry {
      private final SkyIslandCustomizeScreen.FieldSpec spec;
      private final EditBox valueBox;
      private final CycleButton<Boolean> booleanToggle;

      SingleFieldEntry(SkyIslandCustomizeScreen.FieldSpec spec, SkyIslandCustomizeScreen.SettingsModel model) {
         this.spec = spec;
         if (spec.controlType == SkyIslandCustomizeScreen.ControlType.BOOLEAN) {
            this.booleanToggle = CycleButton.onOffBuilder(false)
               .displayOnlyValue()
               .create(0, 0, 120, 20, Component.literal(spec.shortLabel), (button, value) -> {});
            this.valueBox = null;
         } else {
            this.valueBox = new EditBox(Minecraft.getInstance().font, 0, 0, 210, 20, Component.literal(spec.label));
            this.valueBox.setMaxLength(120);
            this.valueBox.setHint(Component.literal(spec.shortLabel));
            this.valueBox.setValue(spec.currentValue(model));
            this.booleanToggle = null;
         }
      }

      @Override
      void resetFromModel(SkyIslandCustomizeScreen.SettingsModel model) {
         if (this.booleanToggle != null) {
            this.booleanToggle.setValue(this.spec.currentBooleanValue(model));
         } else if (this.valueBox != null) {
            this.valueBox.setValue(this.spec.currentValue(model));
         }
      }

      @Override
      String apply(SkyIslandCustomizeScreen.SettingsModel model) {
         if (this.booleanToggle != null) {
            this.spec.applyBoolean(model, (Boolean)this.booleanToggle.getValue());
            return null;
         } else {
            return this.spec.apply(model, this.valueBox.getValue());
         }
      }

      public List<? extends GuiEventListener> children() {
         return this.booleanToggle != null ? List.of(this.booleanToggle) : List.of(this.valueBox);
      }

      public List<? extends NarratableEntry> narratables() {
         return this.booleanToggle != null ? List.of(this.booleanToggle) : List.of(this.valueBox);
      }

      public void render(
         GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick
      ) {
         Font font = Minecraft.getInstance().font;
         int contentWidth = Math.max(160, width - 8);
         boolean narrow = SkyIslandLayoutHelper.isNarrowWidth(contentWidth);
         int controlWidth;
         int controlX;
         int controlY;
         if (narrow) {
            controlWidth = Math.max(96, contentWidth - 8);
            controlX = left + 4;
            controlY = top + height - 24;
            guiGraphics.drawString(font, this.spec.label, left + 4, top + 4, 14737632);
            SkyIslandLayoutHelper.drawWrappedText(guiGraphics, font, Component.literal(this.spec.rangeHint), left + 4, top + 16, contentWidth - 8, 9474192);
         } else {
            controlWidth = Math.min(320, Math.max(116, width / 3));
            int textWidth = Math.max(80, contentWidth - controlWidth - 12);
            controlY = top + 18;
            guiGraphics.drawString(font, this.spec.label, left + 4, top + 4, 14737632);
            SkyIslandLayoutHelper.drawWrappedText(guiGraphics, font, Component.literal(this.spec.rangeHint), left + 4, top + 16, textWidth, 9474192);
            controlX = left + width - controlWidth - 4;
         }

         if (this.booleanToggle != null) {
            this.booleanToggle.setWidth(controlWidth);
            this.booleanToggle.setX(controlX);
            this.booleanToggle.setY(controlY);
            this.booleanToggle.render(guiGraphics, mouseX, mouseY, partialTick);
         } else {
            this.valueBox.setWidth(controlWidth);
            this.valueBox.setX(controlX);
            this.valueBox.setY(controlY);
            this.valueBox.render(guiGraphics, mouseX, mouseY, partialTick);
         }
      }
   }

   private final class SizeModeEntry extends SkyIslandCustomizeScreen.BaseEntry {
      private final CycleButton<IslandSizeMode> modeToggle = CycleButton.builder(mode -> Component.literal(mode.name()))
         .withValues(List.of(IslandSizeMode.RANDOM, IslandSizeMode.SPECIFIC))
         .withInitialValue(SkyIslandCustomizeScreen.this.model.islandSizeMode)
         .create(0, 0, 160, 20, Component.literal("Island size mode"), (button, value) -> {
            SkyIslandCustomizeScreen.this.model.islandSizeMode = value;
            SkyIslandCustomizeScreen.this.errorMessage = null;
            SkyIslandCustomizeScreen.this.minecraft.setScreen(SkyIslandCustomizeScreen.this);
         });

      SizeModeEntry() {
      }

      @Override
      void resetFromModel(SkyIslandCustomizeScreen.SettingsModel model) {
         this.modeToggle.setValue(model.islandSizeMode);
      }

      @Override
      String apply(SkyIslandCustomizeScreen.SettingsModel model) {
         model.islandSizeMode = (IslandSizeMode)this.modeToggle.getValue();
         return null;
      }

      public List<? extends GuiEventListener> children() {
         return List.of(this.modeToggle);
      }

      public List<? extends NarratableEntry> narratables() {
         return List.of(this.modeToggle);
      }

      public void render(
         GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick
      ) {
         Font font = Minecraft.getInstance().font;
         guiGraphics.drawString(font, "Island size mode", left + 4, top + 4, 14737632);
         SkyIslandLayoutHelper.drawWrappedText(
            guiGraphics,
            font,
            Component.literal("RANDOM: Uses Min/Max island radius fields.\nSPECIFIC: Uses Small/Medium/Large min/max/spawn % islands configuration."),
            left + 4,
            top + 16,
            Math.max(80, width / 2 - 12),
            9474192
         );
         int controlWidth = Math.min(320, Math.max(116, width / 3));
         this.modeToggle.setWidth(controlWidth);
         this.modeToggle.setX(left + width - controlWidth - 4);
         this.modeToggle.setY(top + 18);
         this.modeToggle.render(guiGraphics, mouseX, mouseY, partialTick);
      }
   }
}
