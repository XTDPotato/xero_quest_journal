package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** Shared task-style capsule for transient status messages. */
public final class StatusToastRenderer {
    public record Layout(int width, int height, List<FormattedCharSequence> lines) {}

    private StatusToastRenderer() {}

    public static Layout layout(Font font, Component message, int maxWidth, int maxHeight) {
        int widthLimit = Math.max(24, maxWidth);
        int width = Math.min(widthLimit, Math.max(36, font.width(message) + 36));
        var all = font.split(message, Math.max(1, width - 36));
        int lineLimit = Math.max(1, (maxHeight - 12) / (font.lineHeight + 2));
        var lines = List.copyOf(all.subList(0, Math.min(lineLimit, all.size())));
        int height = Math.max(22, lines.size() * (font.lineHeight + 2) + 10);
        return new Layout(width, height, lines);
    }

    public static void draw(GuiGraphics graphics, Font font, Layout layout, int centerX, int y) {
        draw(graphics, font, layout, centerX, y, 1);
    }

    public static void draw(GuiGraphics graphics, Font font, Layout layout, int centerX, int y, float opacity) {
        int width = layout.width(), height = layout.height(), x = centerX - width / 2;
        int cap = Math.min(height, width);
        int alpha = Math.clamp(Math.round(opacity * 255), 0, 255) << 24;
        MailDecor.circle(graphics, x, y + (height - cap) / 2, cap, alpha | 0xFFFFFE);
        MailDecor.circle(graphics, x + width - cap, y + (height - cap) / 2, cap, alpha | 0xFFFFFE);
        graphics.fill(x + cap / 2, y, x + width - cap / 2, y + height, alpha | 0xFFFFFE);
        int textHeight = layout.lines().size() * (font.lineHeight + 2) - 2;
        int lineY = y + (height - textHeight) / 2 + 1;
        for (var line : layout.lines()) {
            graphics.drawString(font, line, centerX - font.width(line) / 2, lineY, alpha | 0x50586D, false);
            lineY += font.lineHeight + 2;
        }
    }
}
