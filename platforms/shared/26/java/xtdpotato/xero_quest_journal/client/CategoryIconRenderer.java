package xtdpotato.xero_quest_journal.client;

import net.minecraft.resources.Identifier;

/** Uses native GUI texture extraction rather than an immediate-mode framebuffer shader. */
final class CategoryIconRenderer {
    private CategoryIconRenderer() {}
    static void shader(Object ignored) {}
    static void close() {}
    static void render(ModernGuiGraphics graphics, String icon, int x, int y, int size, int color) {
        if (icon == null || icon.isBlank()) return;
        Identifier texture = Identifier.fromNamespaceAndPath(
                "xero_quest_journal", "textures/gui/navigation/" + icon + ".png");
        graphics.blit(texture, x, y, size, size, 0, 0, 32, 32, 32, 32);
    }
}
