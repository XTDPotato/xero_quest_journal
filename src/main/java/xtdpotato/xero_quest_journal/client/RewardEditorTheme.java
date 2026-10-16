package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared parchment-metal palette and layered surfaces for reward and loot authoring. */
final class RewardEditorTheme {
    static final int GOLD = 0xFFE6C47A;
    static final int GREEN = 0xFF78B79F;
    static final int TEXT = 0xFFF1E9D7;
    static final int MUTED = 0xFFB6C0CB;
    static final int BORDER = 0xFF5A6673;

    private RewardEditorTheme() {}

    static void workspace(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xF0181D23);
        graphics.fillGradient(0, 0, width, Math.min(70, height), 0x302D3844, 0x00181D23);
        graphics.fillGradient(0, Math.max(0, height - 54), width, height, 0x00181D23, 0x50101419);
    }

    static void panel(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        if (width <= 1 || height <= 1) return;
        graphics.fill(x, y, x + width, y + height, 0xA51D242C);
        graphics.fill(x, y, x + width, Math.min(y + 2, y + height), accent);
        graphics.fillGradient(x + 1, y + 2, x + width - 1, Math.min(y + 30, y + height),
                (accent & 0x00FFFFFF) | 0x1D000000, 0x001D242C);
        graphics.renderOutline(x, y, width, height, BORDER);
    }

    static void entry(GuiGraphics graphics, int x, int y, int width, int height,
                      String type, boolean enabled, boolean selected) {
        int accent = enabled ? accent(type) : 0xFF65707B;
        graphics.fill(x, y, x + width, y + height, selected ? 0xC02B3540 : 0xA020272F);
        graphics.fill(x, y + 3, x + 3, y + height - 3, accent);
        graphics.fillGradient(x + 3, y + 1, x + width - 1, y + 28,
                selected ? 0x183F4A57 : 0x102D3844, 0x001D242C);
        graphics.renderOutline(x, y, width, height, selected ? accent : 0xFF46515D);
        graphics.fill(x + 12, y + 29, x + width - 12, y + 30, 0x304E5965);
    }

    static int accent(String type) {
        return switch (type == null ? "" : type) {
            case "item" -> 0xFFE4C06E;
            case "experience", "levels" -> 0xFF86C6E8;
            case "effect", "remove_effect" -> 0xFF82C5A3;
            case "loot", "loot_choice" -> 0xFFC0A0E7;
            case "command", "function", "script" -> 0xFFE38B72;
            case "mail" -> 0xFFE38FA6;
            default -> 0xFFA9BACB;
        };
    }
}
