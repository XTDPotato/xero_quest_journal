package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;

/** Pixel-built book frame and the overview map-scroll emblem. */
final class AnecdoteBookArt {
    private AnecdoteBookArt() {}

    static void spread(GuiGraphics g, int left, int top, int width, int height) {
        g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("xero_quest_journal",
                        "textures/gui/anecdote/book_spread.png"), left - 8, top - 8, width + 16, height + 16,
                0, 0, 1400, 800, 1400, 800);
    }




    static void ribbon(GuiGraphics g, int x, int y, int width, String title) {
        g.fill(x + 4, y + 5, x + width + 4, y + 30, 0xFFCF9B55);
        g.fill(x, y, x + width, y + 26, 0xFFFFE9B6);
        g.fill(x + 5, y + 4, x + width - 5, y + 22, 0xFFFFF2D4);
        g.fill(x + 4, y + 26, x + 13, y + 35, 0xFFCC9852);
        g.fill(x + width - 13, y + 26, x + width - 4, y + 35, 0xFFCC9852);
        g.renderOutline(x, y, width, 26, 0xFFE2BD76);
        int textWidth = MinecraftFonts.width(title);
        g.drawString(MinecraftFonts.font(), title, x + (width - textWidth) / 2, y + 8, 0xFF765221, false);
    }

    private static final class MinecraftFonts {
        static net.minecraft.client.gui.Font font() { return net.minecraft.client.Minecraft.getInstance().font; }
        static int width(String value) { return font().width(value); }
    }

    static void mapScroll(GuiGraphics g, int x, int y, int size, int color) {
        JournalNavButton.drawIcon(g, "category_anecdotes", x, y, size, color);
    }

}
