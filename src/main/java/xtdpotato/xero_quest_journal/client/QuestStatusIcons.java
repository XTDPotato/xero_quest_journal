package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class QuestStatusIcons {
    private QuestStatusIcons() {
    }

    public static void render(GuiGraphics graphics, String status, int x, int y, int size) {
        render(graphics, status, x, y, size, false);
    }

    public static void render(GuiGraphics graphics, String status, int x, int y, int size, boolean highlighted) {
        render(graphics, status, x, y, size, highlighted, 1.0F);
    }

    public static void render(GuiGraphics graphics, String status, int x, int y, int size,
                              boolean highlighted, float alpha) {
        String symbol;
        int color;
        switch (status) {
            case "tracked" -> {
                JournalNavButton.drawIcon(graphics, "track", x, y, size,
                        applyAlpha(highlighted ? 0xFF70521C : 0xFFF2D29A, alpha));
                return;
            }
            case "locked" -> {
                JournalNavButton.drawIcon(graphics, "lock", x, y, size,
                        applyAlpha(highlighted ? 0xFF656D7D : 0xFF9AA4B2, alpha));
                return;
            }
            case "complete", "claimed" -> {
                symbol = "\u2713";
                color = highlighted ? 0xFF287348 : 0xFF79D69A;
            }
            case "unavailable" -> {
                symbol = "\u00d7";
                color = highlighted ? 0xFFA42F38 : 0xFFFF7777;
            }
            default -> {
                return;
            }
        }
        var font = Minecraft.getInstance().font;
        int textX = x + (size - font.width(symbol)) / 2;
        int textY = y + (size - font.lineHeight) / 2;
        graphics.drawString(font, Component.literal(symbol), textX, textY,
                applyAlpha(color, alpha), !highlighted);
    }

    private static int applyAlpha(int color, float alpha) {
        int sourceAlpha = color >>> 24;
        return color & 0x00FFFFFF | Math.clamp(Math.round(sourceAlpha * alpha), 0, 255) << 24;
    }
}
