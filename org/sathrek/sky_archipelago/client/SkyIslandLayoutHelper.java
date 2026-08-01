package org.sathrek.sky_archipelago.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class SkyIslandLayoutHelper {
   private static final int NARROW_WIDTH_THRESHOLD = 460;

   private SkyIslandLayoutHelper() {
   }

   static boolean isNarrowWidth(int contentWidth) {
      return contentWidth < 460;
   }

   static int wrappedLineCount(Font font, Component text, int maxWidth) {
      int safeWidth = Math.max(40, maxWidth);
      return Math.max(1, font.split(text, safeWidth).size());
   }

   static int drawWrappedText(GuiGraphics guiGraphics, Font font, Component text, int x, int y, int maxWidth, int color) {
      int safeWidth = Math.max(40, maxWidth);
      int lineHeight = 9 + 2;
      int cursor = y;

      for (FormattedCharSequence line : font.split(text, safeWidth)) {
         guiGraphics.drawString(font, line, x, cursor, color);
         cursor += lineHeight;
      }

      return cursor;
   }

   static List<SkyIslandLayoutHelper.ButtonBounds> layoutButtonGridFromBottom(
      int centerX, int bottomY, int availableWidth, int buttonWidth, int buttonHeight, int gap, int count
   ) {
      int perRow = buttonsPerRow(availableWidth, buttonWidth, gap);
      int rows = (count + perRow - 1) / perRow;
      int totalHeight = rows * buttonHeight + (rows - 1) * gap;
      int startY = bottomY - totalHeight;
      List<SkyIslandLayoutHelper.ButtonBounds> bounds = new ArrayList<>(count);

      for (int row = 0; row < rows; row++) {
         int rowStart = row * perRow;
         int rowCount = Math.min(perRow, count - rowStart);
         int rowWidth = rowCount * buttonWidth + (rowCount - 1) * gap;
         int rowX = centerX - rowWidth / 2;
         int rowY = startY + row * (buttonHeight + gap);

         for (int col = 0; col < rowCount; col++) {
            bounds.add(new SkyIslandLayoutHelper.ButtonBounds(rowX + col * (buttonWidth + gap), rowY, buttonWidth, buttonHeight));
         }
      }

      return bounds;
   }

   static int buttonsPerRow(int availableWidth, int buttonWidth, int gap) {
      int safeWidth = Math.max(buttonWidth, availableWidth);
      return Math.max(1, (safeWidth + gap) / (buttonWidth + gap));
   }

   record ButtonBounds(int x, int y, int width, int height) {
   }
}
