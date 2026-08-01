package org.sathrek.sky_archipelago.client;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sathrek.sky_archipelago.config.SkyIslandDedicatedConfigExporter;
import org.sathrek.sky_archipelago.config.SkyIslandDefaultPresetStore;
import org.sathrek.sky_archipelago.config.SkyIslandGeneratorSettings;
import org.sathrek.sky_archipelago.config.SkyIslandPresetJson;
import org.sathrek.sky_archipelago.config.SkyIslandSavedPresetRepository;

final class SkyIslandPresetCustomizeScreen extends Screen {
   private static final Component TITLE = Component.literal("Sky Islands Presets");
   private static final String WRAPPER_KEY = "sky_island_settings";
   private static final int PANEL_BG = 2047808284;
   private static final int PANEL_BORDER = -1664955689;
   private static final int ROW_BG = 704643072;
   private static final int ROW_HOVER_BG = 1430478474;
   private static final int ROW_SELECTED_BG = -1603434829;
   private static final int SUCCESS_COLOR = 7529123;
   private static final int ERROR_COLOR = ChatFormatting.RED.getColor();
   private static final int MUTED_TEXT = 11120830;
   private static final int LABEL_TEXT = 15133684;
   private static final int ACTION_ROW_HEIGHT = 20;
   private static final int ACTION_GAP = 6;
   private static final int ROW_HEIGHT = 24;
   private static final int CARD_PADDING = 8;
   private static final int RIGHT_INSET_X = 10;
   private static final int RIGHT_INSET_TOP = 8;
   private static final int RIGHT_INSET_BOTTOM = 10;
   private static final int MIN_LIST_CARD_HEIGHT = 170;
   private static final int MIN_RIGHT_CARD_HEIGHT = 230;
   private static final int DESCRIPTION_SCROLL_PAUSE_TICKS = 20;
   private static final int DESCRIPTION_SCROLL_PIXELS_PER_TICK = 1;
   private final SkyIslandCustomizeScreen parent;
   private final SkyIslandCustomizeScreen.SettingsModel model;
   private final SkyIslandSavedPresetRepository repository;
   private final SkyIslandDefaultPresetStore defaultPresetStore;
   private EditBox presetNameBox;
   private EditBox presetDescriptionBox;
   private EditBox jsonBox;
   private Component statusMessage;
   private boolean statusIsError;
   private String pendingDeleteStem;
   private int contentLeftX;
   private int contentWidth;
   private int buttonTopY;
   private int listLeftX;
   private int listTopY;
   private int listWidth;
   private int listBottomY;
   private int listActionY;
   private int rightLeftX;
   private int rightWidth;
   private int rightContentLeftX;
   private int rightContentWidth;
   private int rightTopY;
   private int rightBottomY;
   private int cardTopY;
   private int columnTopY;
   private int columnBottomY;
   private int jsonSectionTopY;
   private int nameLabelY;
   private int descriptionLabelY;
   private int rightStatusY;
   private int jsonActionY;
   private int backButtonX;
   private int backButtonY;
   private int backButtonWidth;
   private Button overwriteButton;
   private Button deleteButton;
   private Button setDefaultButton;
   private Button clearDefaultButton;
   private int selectedPresetIndex = -1;
   private int listScrollOffset;
   private List<SkyIslandSavedPresetRepository.SavedPresetSummary> visiblePresets = List.of();

   SkyIslandPresetCustomizeScreen(SkyIslandCustomizeScreen parent, SkyIslandCustomizeScreen.SettingsModel model) {
      this(parent, model, SkyIslandSavedPresetRepository.atDefaultLocation(), SkyIslandDefaultPresetStore.atDefaultLocation());
   }

   SkyIslandPresetCustomizeScreen(SkyIslandCustomizeScreen parent, SkyIslandCustomizeScreen.SettingsModel model, SkyIslandSavedPresetRepository repository) {
      this(parent, model, repository, SkyIslandDefaultPresetStore.atDefaultLocation());
   }

   SkyIslandPresetCustomizeScreen(
      SkyIslandCustomizeScreen parent,
      SkyIslandCustomizeScreen.SettingsModel model,
      SkyIslandSavedPresetRepository repository,
      SkyIslandDefaultPresetStore defaultPresetStore
   ) {
      super(TITLE);
      this.parent = parent;
      this.model = model;
      this.repository = repository;
      this.defaultPresetStore = defaultPresetStore;
   }

   protected void init() {
      int centerX = this.width / 2;
      this.contentWidth = Math.max(320, Math.min(940, this.width - 24));
      this.contentLeftX = centerX - this.contentWidth / 2;
      int contentRightX = this.contentLeftX + this.contentWidth;
      int top = 10;
      int gap = 12;
      int lineHeight = 9 + 2;
      boolean compact = this.height < 560 || this.contentWidth < 760;
      int sectionGap = compact ? 16 : 20;
      int labelToFieldGap = compact ? 2 : 4;
      int rowGap = compact ? 6 : 10;
      int idealListWidth = (this.contentWidth - gap) * 45 / 100;
      this.listWidth = Math.max(220, Math.min(idealListWidth, this.contentWidth - 220 - gap));
      this.rightLeftX = this.contentLeftX + this.listWidth + gap;
      this.rightWidth = Math.max(220, contentRightX - this.rightLeftX);
      this.rightContentLeftX = this.rightLeftX + 10;
      this.rightContentWidth = Math.max(180, this.rightWidth - 20);
      this.listLeftX = this.contentLeftX;
      this.backButtonWidth = Math.max(110, Math.min(150, this.contentWidth / 5 + 10));
      this.backButtonX = contentRightX - this.backButtonWidth;
      this.backButtonY = this.height - 30;
      this.buttonTopY = this.backButtonY;
      int introLines = SkyIslandLayoutHelper.wrappedLineCount(
         this.font, Component.literal("Manage your saved presets locally or share them as JSON."), this.contentWidth
      );
      this.cardTopY = top + 9 + 2 + introLines * lineHeight + 4;
      this.columnTopY = this.cardTopY;
      this.listTopY = this.columnTopY + 28;
      this.rightTopY = this.columnTopY;
      this.listActionY = this.backButtonY - 34;
      this.listBottomY = Math.max(this.listTopY + 170, this.listActionY - 8);
      int desiredRightBottom = Math.max(this.rightTopY + 230 + 20 + 6, this.listBottomY + 28);
      this.columnBottomY = Math.max(this.listBottomY, desiredRightBottom);
      this.rightBottomY = this.columnBottomY;
      this.nameLabelY = this.rightTopY + 8;
      int fieldY = this.nameLabelY + 9 + labelToFieldGap;
      int saveButtonWidth = Math.max(96, Math.min(132, (this.rightContentWidth - 6) / 2));
      int nameWidth = Math.max(110, this.rightContentWidth - 12 - saveButtonWidth * 2);
      this.presetNameBox = new EditBox(this.font, this.rightContentLeftX, fieldY, nameWidth, 20, Component.literal("Preset name"));
      this.presetNameBox.setMaxLength(80);
      this.presetNameBox.setHint(Component.literal("Preset name (required)"));
      this.addRenderableWidget(this.presetNameBox);
      int saveX = this.rightContentLeftX + nameWidth + 6;
      this.addRenderableWidget(Button.builder(Component.literal("Save New"), button -> this.saveCurrent()).bounds(saveX, fieldY, saveButtonWidth, 20).build());
      this.overwriteButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Overwrite Selected"), button -> this.overwriteCurrent())
            .bounds(saveX + saveButtonWidth + 6, fieldY, saveButtonWidth, 20)
            .build()
      );
      this.descriptionLabelY = fieldY + 20 + rowGap;
      fieldY = this.descriptionLabelY + 9 + labelToFieldGap;
      this.presetDescriptionBox = new EditBox(this.font, this.rightContentLeftX, fieldY, this.rightContentWidth, 20, Component.literal("Description"));
      this.presetDescriptionBox.setMaxLength(160);
      this.presetDescriptionBox.setHint(Component.literal("Description (optional)"));
      this.addRenderableWidget(this.presetDescriptionBox);
      this.jsonSectionTopY = fieldY + 20 + sectionGap;
      int jsonHelperY = this.jsonSectionTopY + 9 + 2;
      int jsonFieldY = jsonHelperY + 9 + 4;
      this.jsonBox = new EditBox(this.font, this.rightContentLeftX, jsonFieldY, this.rightContentWidth, 20, Component.literal("Preset JSON"));
      this.jsonBox.setMaxLength(65535);
      this.jsonBox.setHint(Component.literal("Paste raw JSON or {\"sky_island_settings\": {...}}"));
      this.addRenderableWidget(this.jsonBox);
      int listActionWidth = (this.listWidth - 16 - 18) / 4;
      this.addRenderableWidget(
         Button.builder(Component.literal("Load Selected"), button -> this.loadSelected())
            .bounds(this.listLeftX + 8, this.listActionY, listActionWidth, 20)
            .build()
      );
      this.deleteButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Delete Selected"), button -> this.deleteSelected())
            .bounds(this.listLeftX + 8 + listActionWidth + 6, this.listActionY, listActionWidth, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Export Dedicated"), button -> this.copyDedicatedFromSelectedPreset())
            .bounds(this.listLeftX + 8 + (listActionWidth + 6) * 2, this.listActionY, listActionWidth, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Refresh"), button -> this.refreshPresetList())
            .bounds(this.listLeftX + 8 + (listActionWidth + 6) * 3, this.listActionY, listActionWidth, 20)
            .build()
      );
      this.jsonActionY = jsonFieldY + 20 + rowGap;
      int jsonActionWidth = Math.max(90, (this.rightContentWidth - 12) / 3);
      int jsonButtonsBottom = this.jsonActionY + 20;
      int minStatusGap = 4;
      int maxStatusTop = this.rightBottomY - 10 - 9;
      boolean wrapJsonButtons = this.rightContentWidth < 360 || jsonButtonsBottom + 18 > maxStatusTop;
      this.addRenderableWidget(
         Button.builder(Component.literal("Paste JSON"), button -> this.pasteClipboard())
            .bounds(this.rightContentLeftX, this.jsonActionY, wrapJsonButtons ? (this.rightContentWidth - 6) / 2 : jsonActionWidth, 20)
            .build()
      );
      if (wrapJsonButtons) {
         int topRowWidth = (this.rightContentWidth - 6) / 2;
         this.addRenderableWidget(
            Button.builder(Component.literal("Load JSON"), button -> this.applyJson())
               .bounds(this.rightContentLeftX + topRowWidth + 6, this.jsonActionY, topRowWidth, 20)
               .build()
         );
         int secondRowY = this.jsonActionY + 20 + 6;
         this.addRenderableWidget(
            Button.builder(Component.literal("Copy JSON"), button -> this.copyCurrent())
               .bounds(this.rightContentLeftX, secondRowY, this.rightContentWidth, 20)
               .build()
         );
         this.rightStatusY = secondRowY + 20 + minStatusGap;
      } else {
         this.addRenderableWidget(
            Button.builder(Component.literal("Load JSON"), button -> this.applyJson())
               .bounds(this.rightContentLeftX + jsonActionWidth + 6, this.jsonActionY, jsonActionWidth, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.literal("Copy JSON"), button -> this.copyCurrent())
               .bounds(this.rightContentLeftX + (jsonActionWidth + 6) * 2, this.jsonActionY, jsonActionWidth, 20)
               .build()
         );
         this.rightStatusY = this.jsonActionY + 20 + minStatusGap;
      }

      int sessionActionY = this.rightStatusY;
      int sessionActionWidth = (this.rightContentWidth - 6) / 2;
      this.setDefaultButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Set As Default"), button -> this.setSelectedAsDefault())
            .bounds(this.rightContentLeftX, sessionActionY, sessionActionWidth, 20)
            .build()
      );
      this.clearDefaultButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Clear Default"), button -> this.clearDefaultPreset())
            .bounds(this.rightContentLeftX + sessionActionWidth + 6, sessionActionY, sessionActionWidth, 20)
            .build()
      );
      this.rightStatusY = sessionActionY + 20 + minStatusGap;
      this.rightStatusY = Math.min(this.rightStatusY, maxStatusTop);
      this.addRenderableWidget(
         Button.builder(Component.literal("Back"), button -> this.parent.returnFromPreset())
            .bounds(this.backButtonX, this.backButtonY, this.backButtonWidth, 20)
            .build()
      );
      this.refreshPresetList();
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
      this.renderChrome(guiGraphics, mouseX, mouseY);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      this.renderOverlayText(guiGraphics, mouseX, mouseY);
   }

   private void renderChrome(GuiGraphics guiGraphics, int mouseX, int mouseY) {
      this.drawCard(guiGraphics, this.listLeftX, this.listTopY - 30, this.listWidth, this.listBottomY - (this.listTopY - 30));
      this.drawCard(guiGraphics, this.rightLeftX, this.rightTopY, this.rightWidth, this.rightBottomY - this.rightTopY);
      this.renderPresetList(guiGraphics, mouseX, mouseY, false);
   }

   private void renderOverlayText(GuiGraphics guiGraphics, int mouseX, int mouseY) {
      int centerX = this.width / 2;
      guiGraphics.drawCenteredString(this.font, this.title, centerX, 10, 16777215);
      SkyIslandLayoutHelper.drawWrappedText(
         guiGraphics,
         this.font,
         Component.literal("Manage your saved presets locally or share them as JSON."),
         this.contentLeftX,
         24,
         this.contentWidth,
         11120830
      );
      guiGraphics.drawString(this.font, "Saved Presets (Local)", this.listLeftX + 4, this.listTopY - 22, 15133684);
      guiGraphics.drawString(this.font, "Preset Name", this.rightContentLeftX, this.nameLabelY, 15133684);
      guiGraphics.drawString(this.font, "Description (optional)", this.rightContentLeftX, this.descriptionLabelY, 15133684);
      guiGraphics.drawString(this.font, "Share or Import Preset (JSON)", this.rightContentLeftX, this.jsonSectionTopY, 15133684);
      guiGraphics.drawString(this.font, "Paste JSON and click \"Load JSON\" to apply it.", this.rightContentLeftX, this.jsonSectionTopY + 14, 11120830);
      this.renderPresetList(guiGraphics, mouseX, mouseY, true);
      guiGraphics.drawString(
         this.font,
         this.selectedSummary() == null
            ? "Select a preset to load, delete, or overwrite it."
            : (
               this.selectedSummary().builtIn()
                  ? "Built-in presets can be loaded and copied, but only saved as regular player presets."
                  : "Selected preset is ready for load/delete/overwrite."
            ),
         this.listLeftX + 2,
         this.listActionY + 20 + 4,
         11120830
      );
      if (this.statusMessage != null) {
         int color = this.statusIsError ? ERROR_COLOR : 7529123;
         SkyIslandLayoutHelper.drawWrappedText(
            guiGraphics, this.font, this.statusMessage, this.rightContentLeftX, this.rightStatusY, this.rightContentWidth, color
         );
      }
   }

   public void onClose() {
      this.parent.returnFromPreset();
   }

   private void refreshPresetList() {
      this.pendingDeleteStem = null;
      SkyIslandSavedPresetRepository.ListResult result = this.repository.listPresets();
      this.visiblePresets = result.presets();
      if (this.selectedPresetIndex >= this.visiblePresets.size()) {
         this.selectedPresetIndex = -1;
      }

      this.listScrollOffset = clampListScrollOffset(this.listScrollOffset, this.visiblePresets.size(), this.maxVisibleRows());
      this.updateActionButtonStates();
      if (!result.warnings().isEmpty()) {
         this.setStatus(true, result.warnings().get(0));
      } else if (result.presets().isEmpty()) {
         this.setStatus(false, "No saved presets yet. Save one using \"Preset Name\" and \"Save New\".");
      }
   }

   private void loadSelected() {
      SkyIslandSavedPresetRepository.SavedPresetSummary selected = this.selectedSummary();
      if (selected == null) {
         this.setStatus(true, "Select a saved preset first.");
      } else {
         Optional<SkyIslandSavedPresetRepository.SavedPreset> loaded = this.repository.loadPreset(selected.stem());
         if (loaded.isEmpty()) {
            this.setStatus(true, "Unable to load selected preset file.");
         } else {
            this.model.copyFrom(SkyIslandCustomizeScreen.SettingsModel.from(loaded.get().settings()));
            if (this.presetNameBox != null) {
               this.presetNameBox.setValue(loaded.get().name());
            }

            if (this.presetDescriptionBox != null) {
               this.presetDescriptionBox.setValue(loaded.get().description());
            }

            this.updateActionButtonStates();
            this.setStatus(
               false,
               selected.builtIn()
                  ? "Built-in preset loaded into current customization. Use Set As Default if you want future worlds to start from it."
                  : "Preset loaded into current customization. Use Set As Default if you want future worlds to start from it."
            );
         }
      }
   }

   private void setSelectedAsDefault() {
      SkyIslandSavedPresetRepository.SavedPresetSummary selected = this.selectedSummary();
      if (selected == null) {
         this.setStatus(true, "Select a saved preset first.");
      } else {
         Optional<SkyIslandSavedPresetRepository.SavedPreset> loaded = this.repository.loadPreset(selected);
         if (loaded.isEmpty()) {
            this.setStatus(true, "Unable to load selected preset file.");
         } else {
            SkyIslandDefaultPresetStore.OperationResult result = this.defaultPresetStore.saveDefaultPresetStem(loaded.get().stem());
            if (!result.success()) {
               this.setStatus(true, result.message());
            } else {
               this.updateActionButtonStates();
               this.setStatus(
                  false,
                  selected.builtIn()
                     ? "Built-in preset saved as the default for future Sky Islands worlds."
                     : "Preset saved as the default for future Sky Islands worlds."
               );
            }
         }
      }
   }

   private void clearDefaultPreset() {
      SkyIslandDefaultPresetStore.OperationResult result = this.defaultPresetStore.clearDefaultPreset();
      if (!result.success()) {
         this.setStatus(true, result.message());
      } else {
         this.updateActionButtonStates();
         this.setStatus(false, "Default preset cleared. New Sky Islands worlds will use the hardcoded defaults again.");
      }
   }

   private void saveCurrent() {
      this.writeCurrent(false);
   }

   private void overwriteCurrent() {
      this.writeCurrent(true);
   }

   private void writeCurrent(boolean overwrite) {
      SkyIslandGeneratorSettings settings;
      try {
         settings = this.model.toSettings();
      } catch (IllegalArgumentException exception) {
         this.setStatus(true, exception.getMessage());
         return;
      }

      SkyIslandSavedPresetRepository.SavedPresetSummary selected = this.selectedSummary();
      if (overwrite && selected != null && selected.builtIn()) {
         this.setStatus(true, "Built-in presets cannot be overwritten. Save New to create a regular player preset copy.");
      } else {
         SkyIslandSavedPresetRepository.OperationResult result = this.repository
            .savePreset(this.presetNameBox.getValue(), this.presetDescriptionBox.getValue(), settings, overwrite);
         if (!result.success()) {
            this.setStatus(true, result.message());
         } else {
            this.setStatus(false, overwrite ? "Preset overwritten from current settings." : "Preset saved from current settings.");
            this.refreshPresetList();
         }
      }
   }

   private void deleteSelected() {
      SkyIslandSavedPresetRepository.SavedPresetSummary selected = this.selectedSummary();
      if (selected == null) {
         this.setStatus(true, "Select a saved preset to delete.");
      } else if (selected.builtIn()) {
         this.setStatus(true, "Built-in presets cannot be deleted. Save New to create a regular player preset copy.");
      } else if (!selected.stem().equals(this.pendingDeleteStem)) {
         this.pendingDeleteStem = selected.stem();
         this.setStatus(true, "Confirm delete: click \"Delete Selected\" again for \"" + selected.name() + "\".");
      } else {
         SkyIslandSavedPresetRepository.OperationResult result = this.repository.deletePreset(selected.stem());
         if (!result.success()) {
            this.setStatus(true, result.message());
         } else {
            this.pendingDeleteStem = null;
            this.setStatus(false, "Preset deleted.");
            this.refreshPresetList();
         }
      }
   }

   private void updateActionButtonStates() {
      SkyIslandSavedPresetRepository.SavedPresetSummary selected = this.selectedSummary();
      boolean canModify = selected != null && !selected.builtIn();
      if (this.deleteButton != null) {
         this.deleteButton.active = canModify;
      }

      if (this.overwriteButton != null) {
         this.overwriteButton.active = canModify;
      }

      if (this.setDefaultButton != null) {
         this.setDefaultButton.active = selected != null;
      }

      if (this.clearDefaultButton != null) {
         this.clearDefaultButton.active = this.defaultPresetStore.hasDefaultPresetFile();
      }
   }

   private void pasteClipboard() {
      if (this.minecraft != null) {
         this.jsonBox.setValue(this.minecraft.keyboardHandler.getClipboard());
         this.setStatus(false, "Clipboard JSON pasted.");
      }
   }

   private void copyCurrent() {
      if (this.minecraft != null) {
         SkyIslandGeneratorSettings settings = this.model.toSettings();
         SkyIslandPresetJson.EncodeResult result = SkyIslandPresetJson.encodeWrapped(settings, true);
         if (!result.success()) {
            this.setStatus(true, result.message());
         } else {
            this.minecraft.keyboardHandler.setClipboard(result.message());
            this.jsonBox.setValue(result.message());
            this.setStatus(false, "Current settings copied as wrapped JSON.");
         }
      }
   }

   private void applyJson() {
      SkyIslandPresetJson.DecodeResult result = SkyIslandPresetJson.decodeSettings(this.jsonBox.getValue());
      if (!result.success()) {
         this.setStatus(true, result.message());
      } else {
         this.model.copyFrom(SkyIslandCustomizeScreen.SettingsModel.from(result.settings()));
         this.setStatus(false, "JSON loaded into current customization values.");
      }
   }

   private void copyDedicatedFromSelectedPreset() {
      if (this.minecraft != null) {
         SkyIslandSavedPresetRepository.SavedPresetSummary selected = this.selectedSummary();
         if (selected == null) {
            this.setStatus(true, "Select a saved preset to export dedicated config text.");
         } else {
            Optional<SkyIslandSavedPresetRepository.SavedPreset> loaded = this.repository.loadPreset(selected.stem());
            if (loaded.isEmpty()) {
               this.setStatus(true, "Unable to load selected preset file.");
            } else {
               String payload = toDedicatedConfigSnippet(loaded.get().settings());
               this.minecraft.keyboardHandler.setClipboard(payload);
               this.setStatus(false, "Dedicated config text copied to clipboard.");
            }
         }
      }
   }

   private static String toDedicatedConfigSnippet(SkyIslandGeneratorSettings settings) {
      return SkyIslandDedicatedConfigExporter.toDedicatedConfigSnippet(settings);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return button == 0 && this.trySelectPresetRow(mouseX, mouseY) ? true : super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.isWithinPresetList(mouseX, mouseY)) {
         int maxOffset = Math.max(0, this.visiblePresets.size() - this.maxVisibleRows());
         if (scrollY > 0.0) {
            this.listScrollOffset = Math.max(0, this.listScrollOffset - 1);
         } else if (scrollY < 0.0) {
            this.listScrollOffset = Math.min(maxOffset, this.listScrollOffset + 1);
         }

         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   private void renderPresetList(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean drawText) {
      if (!drawText) {
         guiGraphics.fill(this.listLeftX, this.listTopY, this.listLeftX + this.listWidth, this.listBottomY, 1174405120);
      }

      int start = this.listScrollOffset;
      int end = Math.min(this.visiblePresets.size(), start + this.maxVisibleRows());
      int rowY = this.listTopY + 2;

      for (int i = start; i < end; i++) {
         SkyIslandSavedPresetRepository.SavedPresetSummary summary = this.visiblePresets.get(i);
         boolean selected = i == this.selectedPresetIndex;
         boolean hovered = mouseX >= this.listLeftX + 2 && mouseX <= this.listLeftX + this.listWidth - 4 && mouseY >= rowY && mouseY <= rowY + 24 - 2;
         if (!drawText) {
            int bg = selected ? -1603434829 : (hovered ? 1430478474 : 704643072);
            guiGraphics.fill(this.listLeftX + 2, rowY, this.listLeftX + this.listWidth - 4, rowY + 24 - 2, bg);
         } else {
            int nameColor = selected ? 16579839 : 15593976;
            int descColor = selected ? 15199478 : 10990276;
            guiGraphics.drawString(this.font, summary.displayName(), this.listLeftX + 6, rowY + 3, nameColor);
            String description = summary.description().isBlank() ? "(no description)" : summary.description();
            int descX = this.listLeftX + 6;
            int descY = rowY + 13;
            int descAvailableWidth = Math.max(20, this.listWidth - 16);
            this.drawMarqueeTextIfNeeded(guiGraphics, description, descX, descY, descAvailableWidth, hovered, descColor);
         }

         rowY += 24;
      }

      if (!drawText) {
         this.renderListScrollbar(guiGraphics);
      }
   }

   private void renderListScrollbar(GuiGraphics guiGraphics) {
      int rows = this.maxVisibleRows();
      int total = this.visiblePresets.size();
      if (total > rows) {
         int trackLeft = this.listLeftX + this.listWidth - 8;
         int trackTop = this.listTopY + 2;
         int trackBottom = this.listBottomY - 2;
         int trackHeight = trackBottom - trackTop;
         guiGraphics.fill(trackLeft, trackTop, trackLeft + 4, trackBottom, 1879048192);
         int thumbHeight = Math.max(12, (int)((double)rows / total * trackHeight));
         int maxOffset = Math.max(1, total - rows);
         int thumbTravel = Math.max(1, trackHeight - thumbHeight);
         int thumbTop = trackTop + (int)((double)this.listScrollOffset / maxOffset * thumbTravel);
         guiGraphics.fill(trackLeft, thumbTop, trackLeft + 4, thumbTop + thumbHeight, -788529153);
      }
   }

   private boolean trySelectPresetRow(double mouseX, double mouseY) {
      if (!this.isWithinPresetList(mouseX, mouseY)) {
         return false;
      } else {
         int clickedRow = (int)((mouseY - (this.listTopY + 2)) / 24.0);
         int absoluteIndex = this.listScrollOffset + clickedRow;
         if (clickedRow >= 0 && absoluteIndex >= 0 && absoluteIndex < this.visiblePresets.size()) {
            this.selectPresetIndex(absoluteIndex);
            return true;
         } else {
            return false;
         }
      }
   }

   private int maxVisibleRows() {
      return Math.max(1, (this.listBottomY - this.listTopY - 4) / 24);
   }

   private boolean isWithinPresetList(double mouseX, double mouseY) {
      return mouseX >= this.listLeftX && mouseX <= this.listLeftX + this.listWidth && mouseY >= this.listTopY && mouseY <= this.listBottomY;
   }

   private SkyIslandSavedPresetRepository.SavedPresetSummary selectedSummary() {
      return this.selectedPresetIndex >= 0 && this.selectedPresetIndex < this.visiblePresets.size() ? this.visiblePresets.get(this.selectedPresetIndex) : null;
   }

   private void drawCard(GuiGraphics guiGraphics, int x, int y, int width, int height) {
      guiGraphics.fill(x, y, x + width, y + height, 2047808284);
      guiGraphics.fill(x, y, x + width, y + 1, -1664955689);
      guiGraphics.fill(x, y + height - 1, x + width, y + height, -1664955689);
      guiGraphics.fill(x, y, x + 1, y + height, -1664955689);
      guiGraphics.fill(x + width - 1, y, x + width, y + height, -1664955689);
   }

   private void setStatus(boolean isError, String message) {
      this.statusIsError = isError;
      this.statusMessage = message == null ? null : Component.literal(message);
   }

   private void selectPresetIndex(int absoluteIndex) {
      this.selectedPresetIndex = absoluteIndex;
      SkyIslandSavedPresetRepository.SavedPresetSummary selected = this.visiblePresets.get(absoluteIndex);
      if (this.presetNameBox != null) {
         this.presetNameBox.setValue(selected.name());
      }

      if (this.presetDescriptionBox != null) {
         this.presetDescriptionBox.setValue(selected.description());
      }

      this.pendingDeleteStem = null;
      this.updateActionButtonStates();
      this.setStatus(
         false, selected.builtIn() ? "Built-in preset selected. Load Selected applies it now; Set As Default stores it for future worlds." : "Preset selected."
      );
   }

   static int clampListScrollOffset(int offset, int presetCount, int visibleRows) {
      int maxOffset = Math.max(0, presetCount - Math.max(1, visibleRows));
      return Math.max(0, Math.min(offset, maxOffset));
   }

   void setVisiblePresetsForTest(List<SkyIslandSavedPresetRepository.SavedPresetSummary> presets) {
      this.visiblePresets = List.copyOf(presets);
   }

   void refreshPresetListForTest() {
      this.refreshPresetList();
   }

   void selectPresetIndexForTest(int absoluteIndex) {
      this.selectPresetIndex(absoluteIndex);
   }

   void deleteSelectedForTest() {
      this.deleteSelected();
   }

   void loadSelectedForTest() {
      this.loadSelected();
   }

   void setSelectedAsDefaultForTest() {
      this.setSelectedAsDefault();
   }

   void clearDefaultPresetForTest() {
      this.clearDefaultPreset();
   }

   int selectedPresetIndexForTest() {
      return this.selectedPresetIndex;
   }

   int visiblePresetCountForTest() {
      return this.visiblePresets.size();
   }

   String pendingDeleteStemForTest() {
      return this.pendingDeleteStem;
   }

   String statusTextForTest() {
      return this.statusMessage == null ? null : this.statusMessage.getString();
   }

   boolean statusIsErrorForTest() {
      return this.statusIsError;
   }

   static SkyIslandPresetCustomizeScreen.LayoutDebug layoutDebugForTest(int width, int height, int fontLineHeight) {
      int contentWidth = Math.max(320, Math.min(940, width - 24));
      int contentLeftX = width / 2 - contentWidth / 2;
      int gap = 12;
      int idealListWidth = (contentWidth - gap) * 45 / 100;
      int listWidth = Math.max(220, Math.min(idealListWidth, contentWidth - 220 - gap));
      int rightWidth = Math.max(220, contentLeftX + contentWidth - (contentLeftX + listWidth + gap));
      int rightContentWidth = Math.max(180, rightWidth - 20);
      int top = 10;
      int lineHeight = fontLineHeight + 2;
      int introLines = 1;
      int cardTop = top + fontLineHeight + 2 + introLines * lineHeight + 4;
      int listTop = cardTop + 28;
      int backY = height - 30;
      int listActionY = backY - 34;
      int listBottom = Math.max(listTop + 170, listActionY - 8);
      int rightBottom = Math.max(listBottom, Math.max(cardTop + 230, listBottom + 28));
      int leftCardTop = listTop - 30;
      int leftCardHeight = listBottom - leftCardTop;
      int rightCardHeight = rightBottom - cardTop;
      return new SkyIslandPresetCustomizeScreen.LayoutDebug(cardTop, listTop, listBottom, rightWidth, rightContentWidth, leftCardHeight, rightCardHeight);
   }

   private void drawMarqueeTextIfNeeded(GuiGraphics guiGraphics, String text, int x, int y, int availableWidth, boolean hovered, int color) {
      int textWidth = this.font.width(text);
      if (textWidth <= availableWidth) {
         guiGraphics.drawString(this.font, text, x, y, color);
      } else if (!hovered) {
         guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(text, availableWidth), x, y, color);
      } else {
         int hiddenWidth = textWidth - availableWidth;
         int period = hiddenWidth + 40;
         int tick = (int)(Util.getMillis() / 50L % Math.max(1, period));
         int offset;
         if (tick < 20) {
            offset = 0;
         } else if (tick >= 20 + hiddenWidth) {
            offset = hiddenWidth;
         } else {
            offset = (tick - 20) * 1;
            offset = Math.min(hiddenWidth, offset);
         }

         guiGraphics.enableScissor(x, y, x + availableWidth, y + 9 + 1);
         guiGraphics.drawString(this.font, text, x - offset, y, color);
         guiGraphics.disableScissor();
      }
   }

   record LayoutDebug(int cardTopY, int listTopY, int listBottomY, int rightWidth, int rightContentWidth, int leftCardHeight, int rightCardHeight) {
   }
}
