package org.sathrek.sky_archipelago.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.sathrek.sky_archipelago.config.OceanBlockType;
import org.sathrek.sky_archipelago.config.SkyIslandGeneratorSettings;
import org.sathrek.sky_archipelago.config.TerrainOverlapMode;

final class SkyIslandAdvancedCustomizeScreen extends Screen {
   private static final Component TITLE = Component.literal("Sky Islands Advanced");
   private static final Component ARCHETYPE_SECTION_LABEL = Component.literal("Island Archetypes For Terrain Generation");
   private static final Component WEIGHT_COLUMN_LABEL = Component.literal("Weight");
   private static final Component OCEAN_FLOOR_BASE_OFFSET_HINT = Component.literal("Base offset: average depth below ocean level (blocks).");
   private static final Component OCEAN_FLOOR_AMPLITUDE_HINT = Component.literal("Noise amplitude: max depth variation from base (blocks).");
   private static final Component OCEAN_FLOOR_SCALE_HINT = Component.literal("Noise scale: lower = broader dunes, higher = tighter variation.");
   private static final Component OCEAN_FLOOR_MIN_DEPTH_HINT = Component.literal("Min depth clamp below ocean level.");
   private static final Component OCEAN_FLOOR_MAX_DEPTH_HINT = Component.literal("Max depth clamp below ocean level.");
   private static final Component DEEPSLATE_START_Y_HINT = Component.literal(
      "Blocks at or below this Y use deepslate inside islands (above 320 needs taller world height mods/settings)."
   );
   private static final int SECTION_LABEL_OFFSET = 16;
   private static final int BUTTON_HEIGHT = 20;
   private static final int BUTTON_GAP = 6;
   private static final int OCEAN_FIELD_STEP = 34;
   private static final int OCEAN_HINT_GAP_ABOVE_FIELD = 4;
   private final SkyIslandCustomizeScreen parent;
   private final SkyIslandCustomizeScreen.SettingsModel model;
   private CycleButton<Boolean> nearMissToggle;
   private CycleButton<Boolean> customStructureRulesToggle;
   private CycleButton<Boolean> clusterCompanionIslandsToggle;
   private CycleButton<Boolean> anchorFragmentationToggle;
   private CycleButton<Boolean> disableIslandsOverOceanBiomesToggle;
   private CycleButton<OceanBlockType> oceanBlockTypeToggle;
   private CycleButton<TerrainOverlapMode> terrainOverlapModeToggle;
   private CycleButton<Boolean> oceanFloorNoiseToggle;
   private EditBox oceanFloorBaseOffsetBox;
   private EditBox oceanFloorNoiseAmplitudeBox;
   private EditBox oceanFloorNoiseScaleBox;
   private EditBox oceanFloorMinDepthBox;
   private EditBox oceanFloorMaxDepthBox;
   private EditBox deepslateStartYBox;
   private CycleButton<Boolean> classicArchetypeToggle;
   private CycleButton<Boolean> bowlCraterArchetypeToggle;
   private CycleButton<Boolean> crescentArchetypeToggle;
   private CycleButton<Boolean> terraceArchetypeToggle;
   private EditBox classicArchetypeWeightBox;
   private EditBox bowlCraterArchetypeWeightBox;
   private EditBox crescentArchetypeWeightBox;
   private EditBox terraceArchetypeWeightBox;
   private Component errorMessage;
   private final List<AbstractWidget> scrollableWidgets = new ArrayList<>();
   private final Map<AbstractWidget, Integer> scrollableBaseY = new IdentityHashMap<>();
   private int contentTopY;
   private int contentBottomY;
   private int contentWidgetBottomY;
   private int scrollOffset;
   private int maxScrollOffset;

   SkyIslandAdvancedCustomizeScreen(SkyIslandCustomizeScreen parent, SkyIslandCustomizeScreen.SettingsModel model) {
      super(TITLE);
      this.parent = parent;
      this.model = model;
   }

   protected void init() {
      this.scrollableWidgets.clear();
      this.scrollableBaseY.clear();
      this.scrollOffset = 0;
      this.maxScrollOffset = 0;
      int centerX = this.width / 2;
      int formWidth = Math.max(220, Math.min(720, this.width - 40));
      boolean narrow = SkyIslandLayoutHelper.isNarrowWidth(formWidth);
      int left = centerX - formWidth / 2;
      int y = 54;
      int toggleWidth = narrow ? formWidth : Math.min(260, formWidth / 3);
      int weightX = narrow ? left : left + toggleWidth + 14;
      int weightWidth = narrow ? formWidth : formWidth - toggleWidth - 14;
      this.classicArchetypeToggle = CycleButton.onOffBuilder(this.model.classicArchetypeEnabled)
         .create(left, y, toggleWidth, 20, Component.literal("Classic archetype"), (button, value) -> this.model.classicArchetypeEnabled = value);
      this.addRenderableWidget(this.classicArchetypeToggle);
      if (narrow) {
         y += 24;
      }

      this.classicArchetypeWeightBox = this.createWeightField(weightX, y, weightWidth, this.model.classicArchetypeWeight, "Classic weight");
      y += narrow ? 32 : 34;
      this.bowlCraterArchetypeToggle = CycleButton.onOffBuilder(this.model.bowlCraterArchetypeEnabled)
         .create(left, y, toggleWidth, 20, Component.literal("Bowl/crater archetype"), (button, value) -> this.model.bowlCraterArchetypeEnabled = value);
      this.addRenderableWidget(this.bowlCraterArchetypeToggle);
      if (narrow) {
         y += 24;
      }

      this.bowlCraterArchetypeWeightBox = this.createWeightField(weightX, y, weightWidth, this.model.bowlCraterArchetypeWeight, "Bowl/crater weight");
      y += narrow ? 32 : 34;
      this.crescentArchetypeToggle = CycleButton.onOffBuilder(this.model.crescentArchetypeEnabled)
         .create(left, y, toggleWidth, 20, Component.literal("Crescent archetype"), (button, value) -> this.model.crescentArchetypeEnabled = value);
      this.addRenderableWidget(this.crescentArchetypeToggle);
      if (narrow) {
         y += 24;
      }

      this.crescentArchetypeWeightBox = this.createWeightField(weightX, y, weightWidth, this.model.crescentArchetypeWeight, "Crescent weight");
      y += narrow ? 32 : 34;
      this.terraceArchetypeToggle = CycleButton.onOffBuilder(this.model.terraceArchetypeEnabled)
         .create(left, y, toggleWidth, 20, Component.literal("Terrace archetype"), (button, value) -> this.model.terraceArchetypeEnabled = value);
      this.addRenderableWidget(this.terraceArchetypeToggle);
      if (narrow) {
         y += 24;
      }

      this.terraceArchetypeWeightBox = this.createWeightField(weightX, y, weightWidth, this.model.terraceArchetypeWeight, "Terrace weight");
      y += narrow ? 36 : 42;
      this.terrainOverlapModeToggle = CycleButton.builder(mode -> Component.literal(mode.displayName()))
         .withValues(List.of(TerrainOverlapMode.values()))
         .withInitialValue(this.model.terrainOverlapMode)
         .create(left, y, formWidth, 20, Component.literal("Island overlap behavior"), (button, value) -> this.model.terrainOverlapMode = value);
      this.addRenderableWidget(this.terrainOverlapModeToggle);
      y += 34;
      this.oceanBlockTypeToggle = CycleButton.builder(type -> Component.literal(type.serializedName()))
         .withValues(List.of(OceanBlockType.values()))
         .withInitialValue(this.model.oceanBlockType)
         .create(left, y, formWidth, 20, Component.literal("Ocean block type"), (button, value) -> this.model.oceanBlockType = value);
      this.addRenderableWidget(this.oceanBlockTypeToggle);
      y += 34;
      this.oceanFloorNoiseToggle = CycleButton.onOffBuilder(this.model.oceanFloorNoiseEnabled)
         .create(left, y, formWidth, 20, Component.literal("Ocean floor noise"), (button, value) -> this.model.oceanFloorNoiseEnabled = value);
      this.addRenderableWidget(this.oceanFloorNoiseToggle);
      y += 34;
      this.oceanFloorBaseOffsetBox = this.createIntField(left, y, formWidth, this.model.oceanFloorBaseOffset, "Ocean floor base offset");
      y += 34;
      this.oceanFloorNoiseAmplitudeBox = this.createIntField(left, y, formWidth, this.model.oceanFloorNoiseAmplitude, "Ocean floor noise amplitude");
      y += 34;
      this.oceanFloorNoiseScaleBox = this.createWeightField(left, y, formWidth, this.model.oceanFloorNoiseScale, "Ocean floor noise scale");
      y += 34;
      this.oceanFloorMinDepthBox = this.createIntField(left, y, formWidth, this.model.oceanFloorMinDepth, "Ocean floor min depth");
      y += 34;
      this.oceanFloorMaxDepthBox = this.createIntField(left, y, formWidth, this.model.oceanFloorMaxDepth, "Ocean floor max depth");
      y += 34;
      this.nearMissToggle = CycleButton.onOffBuilder(this.model.defaultNearMissFallbackEnabled)
         .create(left, y, formWidth, 20, Component.literal("Relaxed POI spawning rules"), (button, value) -> this.model.defaultNearMissFallbackEnabled = value);
      this.addRenderableWidget(this.nearMissToggle);
      y += 34;
      this.customStructureRulesToggle = CycleButton.onOffBuilder(this.model.customStructureRulesEnabled)
         .create(
            left, y, formWidth, 20, Component.literal("Custom structure placement rules"), (button, value) -> this.model.customStructureRulesEnabled = value
         );
      this.addRenderableWidget(this.customStructureRulesToggle);
      y += 34;
      this.clusterCompanionIslandsToggle = CycleButton.onOffBuilder(this.model.clusterCompanionIslandsEnabled)
         .create(
            left,
            y,
            formWidth,
            20,
            Component.literal("Cluster companion islands (satellite/spire)"),
            (button, value) -> this.model.clusterCompanionIslandsEnabled = value
         );
      this.addRenderableWidget(this.clusterCompanionIslandsToggle);
      y += 34;
      this.anchorFragmentationToggle = CycleButton.onOffBuilder(this.model.anchorFragmentationEnabled)
         .create(
            left,
            y,
            formWidth,
            20,
            Component.literal("Anchor fragmentation (debris/outcroppings)"),
            (button, value) -> this.model.anchorFragmentationEnabled = value
         );
      this.addRenderableWidget(this.anchorFragmentationToggle);
      y += 34;
      this.disableIslandsOverOceanBiomesToggle = CycleButton.onOffBuilder(this.model.disableIslandsOverOceanBiomes)
         .create(
            left, y, formWidth, 20, Component.literal("Disable islands over ocean biomes"), (button, value) -> this.model.disableIslandsOverOceanBiomes = value
         );
      this.addRenderableWidget(this.disableIslandsOverOceanBiomesToggle);
      y += 34;
      this.deepslateStartYBox = this.createIntField(left, y, formWidth, this.model.deepslateStartY, "Deepslate start Y");
      int buttonWidth = narrow ? Math.max(92, Math.min(130, formWidth - 16)) : 150;
      List<SkyIslandLayoutHelper.ButtonBounds> bounds = SkyIslandLayoutHelper.layoutButtonGridFromBottom(
         centerX, this.height - 10, formWidth, buttonWidth, 20, 6, 3
      );
      this.contentTopY = 50;
      this.contentBottomY = bounds.getFirst().y() - 8;
      this.contentWidgetBottomY = this.contentBottomY;
      this.addRenderableWidget(
         Button.builder(Component.literal("Reset defaults"), button -> this.resetToDefaults())
            .bounds(bounds.get(0).x(), bounds.get(0).y(), bounds.get(0).width(), bounds.get(0).height())
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Cancel"), button -> this.parent.returnFromAdvanced())
            .bounds(bounds.get(1).x(), bounds.get(1).y(), bounds.get(1).width(), bounds.get(1).height())
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Done"), button -> this.finish())
            .bounds(bounds.get(2).x(), bounds.get(2).y(), bounds.get(2).width(), bounds.get(2).height())
            .build()
      );
      this.registerScrollableWidget(this.classicArchetypeToggle);
      this.registerScrollableWidget(this.classicArchetypeWeightBox);
      this.registerScrollableWidget(this.bowlCraterArchetypeToggle);
      this.registerScrollableWidget(this.bowlCraterArchetypeWeightBox);
      this.registerScrollableWidget(this.crescentArchetypeToggle);
      this.registerScrollableWidget(this.crescentArchetypeWeightBox);
      this.registerScrollableWidget(this.terraceArchetypeToggle);
      this.registerScrollableWidget(this.terraceArchetypeWeightBox);
      this.registerScrollableWidget(this.terrainOverlapModeToggle);
      this.registerScrollableWidget(this.oceanBlockTypeToggle);
      this.registerScrollableWidget(this.oceanFloorNoiseToggle);
      this.registerScrollableWidget(this.oceanFloorBaseOffsetBox);
      this.registerScrollableWidget(this.oceanFloorNoiseAmplitudeBox);
      this.registerScrollableWidget(this.oceanFloorNoiseScaleBox);
      this.registerScrollableWidget(this.oceanFloorMinDepthBox);
      this.registerScrollableWidget(this.oceanFloorMaxDepthBox);
      this.registerScrollableWidget(this.nearMissToggle);
      this.registerScrollableWidget(this.customStructureRulesToggle);
      this.registerScrollableWidget(this.clusterCompanionIslandsToggle);
      this.registerScrollableWidget(this.anchorFragmentationToggle);
      this.registerScrollableWidget(this.disableIslandsOverOceanBiomesToggle);
      this.registerScrollableWidget(this.deepslateStartYBox);
      this.maxScrollOffset = this.computeMaxScrollOffset();
      if (this.maxScrollOffset > 0) {
         this.contentWidgetBottomY = this.contentBottomY - (9 + 4);
         this.maxScrollOffset = this.computeMaxScrollOffset();
      }

      this.applyScrollOffset();
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      int centerX = this.width / 2;
      int formWidth = Math.max(220, Math.min(720, this.width - 40));
      boolean narrow = SkyIslandLayoutHelper.isNarrowWidth(formWidth);
      int left = centerX - formWidth / 2;
      guiGraphics.drawCenteredString(this.font, this.title, centerX, 12, 16777215);
      if (this.classicArchetypeToggle != null && this.classicArchetypeWeightBox != null) {
         int sectionY = this.classicArchetypeToggle.getY() - 16;
         int usedY = sectionY;
         if (sectionY + 9 >= this.contentTopY && sectionY <= this.contentBottomY) {
            usedY = SkyIslandLayoutHelper.drawWrappedText(
               guiGraphics, this.font, ARCHETYPE_SECTION_LABEL, left, sectionY, narrow ? formWidth : Math.max(120, formWidth / 2), 13684944
            );
         }

         if (!narrow) {
            if (sectionY + 9 >= this.contentTopY && sectionY <= this.contentBottomY) {
               guiGraphics.drawString(this.font, WEIGHT_COLUMN_LABEL, this.classicArchetypeWeightBox.getX(), sectionY, 13684944);
            }
         } else {
            int weightLabelY = usedY + 2;
            if (weightLabelY + 9 >= this.contentTopY && weightLabelY <= this.contentBottomY) {
               guiGraphics.drawString(this.font, WEIGHT_COLUMN_LABEL, left, weightLabelY, 11579568);
            }
         }
      }

      if (this.maxScrollOffset > 0) {
         guiGraphics.drawString(this.font, Component.literal("Scroll to view more"), left, this.contentWidgetBottomY + 8, 8421504);
      }

      this.drawOceanFloorHint(guiGraphics, left, formWidth, this.oceanFloorBaseOffsetBox, OCEAN_FLOOR_BASE_OFFSET_HINT);
      this.drawOceanFloorHint(guiGraphics, left, formWidth, this.oceanFloorNoiseAmplitudeBox, OCEAN_FLOOR_AMPLITUDE_HINT);
      this.drawOceanFloorHint(guiGraphics, left, formWidth, this.oceanFloorNoiseScaleBox, OCEAN_FLOOR_SCALE_HINT);
      this.drawOceanFloorHint(guiGraphics, left, formWidth, this.oceanFloorMinDepthBox, OCEAN_FLOOR_MIN_DEPTH_HINT);
      this.drawOceanFloorHint(guiGraphics, left, formWidth, this.oceanFloorMaxDepthBox, OCEAN_FLOOR_MAX_DEPTH_HINT);
      this.drawOceanFloorHint(guiGraphics, left, formWidth, this.deepslateStartYBox, DEEPSLATE_START_Y_HINT);
      if (this.errorMessage != null) {
         SkyIslandLayoutHelper.drawWrappedText(
            guiGraphics, this.font, this.errorMessage, left, this.contentBottomY + 2, Math.max(180, formWidth - 10), ChatFormatting.RED.getColor()
         );
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.maxScrollOffset <= 0) {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }

      int delta = (int)Math.round(-scrollY * 16.0);
      if (delta == 0) {
         delta = scrollY > 0.0 ? -1 : 1;
      }

      int updated = Mth.clamp(this.scrollOffset + delta, 0, this.maxScrollOffset);
      if (updated != this.scrollOffset) {
         this.scrollOffset = updated;
         this.applyScrollOffset();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   public void onClose() {
      this.parent.returnFromAdvanced();
   }

   private EditBox createWeightField(int x, int y, int width, double value, String hint) {
      EditBox editBox = new EditBox(this.font, x, y, width, 20, Component.empty());
      editBox.setMaxLength(32);
      editBox.setValue(Double.toString(value));
      editBox.setHint(Component.literal(hint));
      this.addRenderableWidget(editBox);
      return editBox;
   }

   private EditBox createIntField(int x, int y, int width, int value, String hint) {
      EditBox editBox = new EditBox(this.font, x, y, width, 20, Component.empty());
      editBox.setMaxLength(32);
      editBox.setValue(Integer.toString(value));
      editBox.setHint(Component.literal(hint));
      this.addRenderableWidget(editBox);
      return editBox;
   }

   private void drawOceanFloorHint(GuiGraphics guiGraphics, int left, int width, EditBox box, Component hint) {
      if (box != null && box.visible) {
         int hintY = box.getY() - (9 + 4);
         if (hintY >= this.contentTopY && hintY <= this.contentBottomY) {
            SkyIslandLayoutHelper.drawWrappedText(guiGraphics, this.font, hint, left, hintY, width, 11053224);
         }
      }
   }

   private void resetToDefaults() {
      SkyIslandGeneratorSettings defaultSettings = SkyIslandCustomizeScreen.resolveDefaultSettings();
      SkyIslandCustomizeScreen.SettingsModel defaults = SkyIslandCustomizeScreen.SettingsModel.from(defaultSettings);
      this.model.defaultNearMissFallbackEnabled = defaults.defaultNearMissFallbackEnabled;
      this.model.customStructureRulesEnabled = defaults.customStructureRulesEnabled;
      this.model.clusterCompanionIslandsEnabled = defaults.clusterCompanionIslandsEnabled;
      this.model.anchorFragmentationEnabled = defaults.anchorFragmentationEnabled;
      this.model.disableIslandsOverOceanBiomes = defaults.disableIslandsOverOceanBiomes;
      this.model.deepslateStartY = defaults.deepslateStartY;
      this.model.oceanBlockType = defaults.oceanBlockType;
      this.model.terrainOverlapMode = defaults.terrainOverlapMode;
      this.model.oceanFloorNoiseEnabled = defaults.oceanFloorNoiseEnabled;
      this.model.oceanFloorBaseOffset = defaults.oceanFloorBaseOffset;
      this.model.oceanFloorNoiseAmplitude = defaults.oceanFloorNoiseAmplitude;
      this.model.oceanFloorNoiseScale = defaults.oceanFloorNoiseScale;
      this.model.oceanFloorMinDepth = defaults.oceanFloorMinDepth;
      this.model.oceanFloorMaxDepth = defaults.oceanFloorMaxDepth;
      this.model.classicArchetypeEnabled = defaults.classicArchetypeEnabled;
      this.model.classicArchetypeWeight = defaults.classicArchetypeWeight;
      this.model.bowlCraterArchetypeEnabled = defaults.bowlCraterArchetypeEnabled;
      this.model.bowlCraterArchetypeWeight = defaults.bowlCraterArchetypeWeight;
      this.model.crescentArchetypeEnabled = defaults.crescentArchetypeEnabled;
      this.model.crescentArchetypeWeight = defaults.crescentArchetypeWeight;
      this.model.terraceArchetypeEnabled = defaults.terraceArchetypeEnabled;
      this.model.terraceArchetypeWeight = defaults.terraceArchetypeWeight;
      this.nearMissToggle.setValue(this.model.defaultNearMissFallbackEnabled);
      this.customStructureRulesToggle.setValue(this.model.customStructureRulesEnabled);
      this.clusterCompanionIslandsToggle.setValue(this.model.clusterCompanionIslandsEnabled);
      this.anchorFragmentationToggle.setValue(this.model.anchorFragmentationEnabled);
      this.disableIslandsOverOceanBiomesToggle.setValue(this.model.disableIslandsOverOceanBiomes);
      this.oceanBlockTypeToggle.setValue(this.model.oceanBlockType);
      this.terrainOverlapModeToggle.setValue(this.model.terrainOverlapMode);
      this.oceanFloorNoiseToggle.setValue(this.model.oceanFloorNoiseEnabled);
      this.oceanFloorBaseOffsetBox.setValue(Integer.toString(this.model.oceanFloorBaseOffset));
      this.oceanFloorNoiseAmplitudeBox.setValue(Integer.toString(this.model.oceanFloorNoiseAmplitude));
      this.oceanFloorNoiseScaleBox.setValue(Double.toString(this.model.oceanFloorNoiseScale));
      this.oceanFloorMinDepthBox.setValue(Integer.toString(this.model.oceanFloorMinDepth));
      this.oceanFloorMaxDepthBox.setValue(Integer.toString(this.model.oceanFloorMaxDepth));
      this.deepslateStartYBox.setValue(Integer.toString(this.model.deepslateStartY));
      this.classicArchetypeToggle.setValue(this.model.classicArchetypeEnabled);
      this.classicArchetypeWeightBox.setValue(Double.toString(this.model.classicArchetypeWeight));
      this.bowlCraterArchetypeToggle.setValue(this.model.bowlCraterArchetypeEnabled);
      this.bowlCraterArchetypeWeightBox.setValue(Double.toString(this.model.bowlCraterArchetypeWeight));
      this.crescentArchetypeToggle.setValue(this.model.crescentArchetypeEnabled);
      this.crescentArchetypeWeightBox.setValue(Double.toString(this.model.crescentArchetypeWeight));
      this.terraceArchetypeToggle.setValue(this.model.terraceArchetypeEnabled);
      this.terraceArchetypeWeightBox.setValue(Double.toString(this.model.terraceArchetypeWeight));
      this.errorMessage = null;
   }

   private void finish() {
      Double classicWeight = this.parseWeight(this.classicArchetypeWeightBox.getValue(), "Classic archetype weight");
      if (classicWeight != null) {
         Double bowlCraterWeight = this.parseWeight(this.bowlCraterArchetypeWeightBox.getValue(), "Bowl/crater archetype weight");
         if (bowlCraterWeight != null) {
            Double crescentWeight = this.parseWeight(this.crescentArchetypeWeightBox.getValue(), "Crescent archetype weight");
            if (crescentWeight != null) {
               Double terraceWeight = this.parseWeight(this.terraceArchetypeWeightBox.getValue(), "Terrace archetype weight");
               if (terraceWeight != null) {
                  Integer oceanFloorBaseOffset = this.parseIntInRange(this.oceanFloorBaseOffsetBox.getValue(), "Ocean floor base offset", 0, 2000);
                  if (oceanFloorBaseOffset != null) {
                     Integer oceanFloorNoiseAmplitude = this.parseIntInRange(
                        this.oceanFloorNoiseAmplitudeBox.getValue(), "Ocean floor noise amplitude", 0, 2000
                     );
                     if (oceanFloorNoiseAmplitude != null) {
                        Double oceanFloorNoiseScale = this.parseDoubleInRange(this.oceanFloorNoiseScaleBox.getValue(), "Ocean floor noise scale", 0.001, 1.0);
                        if (oceanFloorNoiseScale != null) {
                           Integer oceanFloorMinDepth = this.parseIntInRange(this.oceanFloorMinDepthBox.getValue(), "Ocean floor min depth", 1, 2000);
                           if (oceanFloorMinDepth != null) {
                              Integer oceanFloorMaxDepth = this.parseIntInRange(this.oceanFloorMaxDepthBox.getValue(), "Ocean floor max depth", 1, 2000);
                              if (oceanFloorMaxDepth != null) {
                                 if (oceanFloorMaxDepth < oceanFloorMinDepth) {
                                    this.errorMessage = Component.literal("Ocean floor max depth must be >= min depth.");
                                 } else {
                                    Integer deepslateStartY = this.parseIntInRange(this.deepslateStartYBox.getValue(), "Deepslate start Y", -64, 2000);
                                    if (deepslateStartY != null) {
                                       this.model.classicArchetypeWeight = classicWeight;
                                       this.model.bowlCraterArchetypeWeight = bowlCraterWeight;
                                       this.model.crescentArchetypeWeight = crescentWeight;
                                       this.model.terraceArchetypeWeight = terraceWeight;
                                       this.model.oceanFloorBaseOffset = oceanFloorBaseOffset;
                                       this.model.oceanFloorNoiseAmplitude = oceanFloorNoiseAmplitude;
                                       this.model.oceanFloorNoiseScale = oceanFloorNoiseScale;
                                       this.model.oceanFloorMinDepth = oceanFloorMinDepth;
                                       this.model.oceanFloorMaxDepth = oceanFloorMaxDepth;
                                       this.model.deepslateStartY = deepslateStartY;
                                       this.errorMessage = null;
                                       this.parent.returnFromAdvanced();
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private Double parseWeight(String rawValue, String label) {
      double parsed;
      try {
         parsed = Double.parseDouble(rawValue.trim());
      } catch (NumberFormatException ex) {
         this.errorMessage = Component.literal(label + " must be a number.");
         return null;
      }

      if (!(parsed < 0.0) && !(parsed > 10.0)) {
         return parsed;
      }

      this.errorMessage = Component.literal(label + " must be between 0.0 and 10.0.");
      return null;
   }

   private Integer parseIntInRange(String rawValue, String label, int min, int max) {
      int parsed;
      try {
         parsed = Integer.parseInt(rawValue.trim());
      } catch (NumberFormatException ex) {
         this.errorMessage = Component.literal(label + " must be an integer.");
         return null;
      }

      if (parsed >= min && parsed <= max) {
         return parsed;
      }

      this.errorMessage = Component.literal(label + " must be between " + min + " and " + max + ".");
      return null;
   }

   private Double parseDoubleInRange(String rawValue, String label, double min, double max) {
      double parsed;
      try {
         parsed = Double.parseDouble(rawValue.trim());
      } catch (NumberFormatException ex) {
         this.errorMessage = Component.literal(label + " must be a number.");
         return null;
      }

      if (!(parsed < min) && !(parsed > max)) {
         return parsed;
      }

      this.errorMessage = Component.literal(label + " must be between " + min + " and " + max + ".");
      return null;
   }

   private void registerScrollableWidget(AbstractWidget widget) {
      this.scrollableWidgets.add(widget);
      this.scrollableBaseY.put(widget, widget.getY());
   }

   private int computeMaxScrollOffset() {
      int maxBottom = this.contentTopY;

      for (AbstractWidget widget : this.scrollableWidgets) {
         int baseY = this.scrollableBaseY.getOrDefault(widget, widget.getY());
         maxBottom = Math.max(maxBottom, baseY + widget.getHeight());
      }

      return Math.max(0, maxBottom - this.contentBottomY + 4);
   }

   private void applyScrollOffset() {
      for (AbstractWidget widget : this.scrollableWidgets) {
         int baseY = this.scrollableBaseY.getOrDefault(widget, widget.getY());
         int y = baseY - this.scrollOffset;
         widget.setY(y);
         widget.visible = y + widget.getHeight() >= this.contentTopY && y <= this.contentWidgetBottomY;
         widget.active = widget.visible;
      }
   }
}
