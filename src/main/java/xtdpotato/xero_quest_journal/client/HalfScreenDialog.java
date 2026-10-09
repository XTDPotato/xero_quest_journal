package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Shared centered confirmation geometry, including compact GUI scales. */
final class HalfScreenDialog {
    private HalfScreenDialog() {}

    static int width(int viewport) {
        return Math.min(Math.max(120, viewport / 2), Math.max(1, viewport - 16));
    }

    static int height(int viewport) {
        return Math.min(Math.max(112, viewport / 2), Math.max(1, viewport - 16));
    }

    static void drawBody(GuiGraphics graphics, Font font, List<Component> paragraphs,
                         int centerX, int top, int width, int height, int color) {
        float scale = 1.3F;
        List<List<FormattedCharSequence>> wrapped;
        int lineHeight;
        int gap;
        while (true) {
            int textWidth = Math.max(1, (int) (width / scale));
            wrapped = new ArrayList<>(paragraphs.size());
            for (Component paragraph : paragraphs) wrapped.add(font.split(paragraph, textWidth));
            lineHeight = Math.round((font.lineHeight + 2) * scale);
            gap = Math.round(4 * scale);
            int lines = wrapped.stream().mapToInt(List::size).sum();
            int gaps = Math.max(0, paragraphs.size() - 1) * gap;
            if (lines * lineHeight + gaps <= height || scale <= .65F) break;
            scale = Math.max(.65F, scale - .05F);
        }

        int lineCount = wrapped.stream().mapToInt(List::size).sum();
        int totalHeight = lineCount * lineHeight + Math.max(0, paragraphs.size() - 1) * gap;
        int y = top + Math.max(0, (height - totalHeight) / 2);
        graphics.enableScissor(centerX - width / 2, top, centerX + (width + 1) / 2, top + height);
        for (List<FormattedCharSequence> paragraph : wrapped) {
            for (FormattedCharSequence line : paragraph) {
                graphics.pose().pushPose();
                graphics.pose().translate(centerX - font.width(line) * scale / 2F, y, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.drawString(font, line, 0, 0, color, false);
                graphics.pose().popPose();
                y += lineHeight;
            }
            y += gap;
        }
        graphics.disableScissor();
    }
}
