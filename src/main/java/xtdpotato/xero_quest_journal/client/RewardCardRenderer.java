package xtdpotato.xero_quest_journal.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared task, mailbox and receipt cards, including their foreground claim marker.
 * Authored quality and background overrides fall back to the item's Minecraft rarity.
 */
final class RewardCardRenderer {
    static final int WIDTH = 34;
    static final int HEIGHT = 38;
    static final int COLUMN_STEP = WIDTH + 4;
    static final int ROW_STEP = HEIGHT + 4;
    /** Rarity gradients (top, bottom): common through mythic. */
    private static final int[][] RARITY = {
            {0xFF585B62, 0xFF82858D},
            {0xFF3F704A, 0xFF68A66F},
            {0xFF3D6696, 0xFF6698CD},
            {0xFF674590, 0xFFA375D1},
            {0xFF916022, 0xFFD4A342},
            {0xFF84353E, 0xFFCF5962}
    };

    private RewardCardRenderer() {}

    static void render(GuiGraphics graphics, Font font, String icon, int count, int x, int y,
                       float scale, float alpha, boolean claimed, boolean hovered) {
        render(graphics, font, icon, count, "", "", x, y, scale, alpha, claimed, hovered);
    }

    static void render(GuiGraphics graphics, Font font, String icon, int count,
                       String quality, String background, int x, int y,
                       float scale, float alpha, boolean claimed, boolean hovered) {
        int width = pixels(WIDTH, scale), height = pixels(HEIGHT, scale);
        int iconSize = pixels(24, scale), iconY = y + pixels(2, scale);
        int band = pixels(28, scale);
        int[] palette = background == null || background.isBlank()
                ? RARITY[rarity(icon, quality) - 1] : customPalette(background);
        graphics.fillGradient(x, y, x + width, y + band, color(palette[0], alpha), color(palette[1], alpha));
        // Soft diagonal sheen in the upper-right corner, like Genshin's item cards.
        int sheen = Math.max(2, width / 3);
        for (int row = 0; row < sheen; row++)
            graphics.fill(x + width - sheen + row, y + row, x + width, y + row + 1, color(0x14FFFFFF, alpha));
        graphics.fill(x, y, x + width, y + 1, color(0x38FFFFFF, alpha));
        if (claimed) graphics.fill(x, y, x + width, y + band, color(0x7A1C1F25, alpha));
        QuestIcons.render(graphics, icon, x + (width - iconSize) / 2, iconY,
                iconSize, iconSize, claimed ? alpha * 0.75F : alpha);
        graphics.fill(x, y + band, x + width, y + height, color(claimed ? 0xCC3A3D44 : 0xCC2A2D34, alpha));
        if (hovered) {
            graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, color(0xFFFFE6B4, alpha));
            graphics.renderOutline(x, y, width, height, color(0x80FFF4D6, alpha));
        }
        String amount = Integer.toString(count);
        float textScale = Math.min(scale, Math.max(1, width - pixels(4, scale)) / (float) font.width(amount));
        graphics.pose().pushPose();
        graphics.pose().translate(x + width / 2F, y + pixels(29, scale), 0);
        graphics.pose().scale(textScale, textScale, 1);
        graphics.drawString(font, amount, -font.width(amount) / 2, 0, color(0xFFFFFFFF, alpha), false);
        graphics.pose().popPose();
        if (claimed) {
            int badgeSize = pixels(21, scale);
            renderClaimedBadge(graphics, x + (width - badgeSize) / 2F,
                    iconY + (iconSize - badgeSize) / 2F, badgeSize, color(0xFFA8F514, alpha));
        }
    }

    /** Six display tiers; empty quality derives the tier from Minecraft item rarity. */
    static int rarity(String icon) {
        return rarity(icon, "");
    }

    static int rarity(String icon, String quality) {
        if (quality != null && !quality.isBlank()) {
            return switch (quality) {
                case "common" -> 1;
                case "uncommon" -> 2;
                case "rare" -> 3;
                case "epic" -> 4;
                case "legendary" -> 5;
                case "mythic" -> 6;
                default -> rarityFromItem(icon);
            };
        }
        return rarityFromItem(icon);
    }

    private static int rarityFromItem(String icon) {
        var stack = QuestIcons.stack(icon);
        if (stack.isEmpty()) return 1;
        return switch (stack.getRarity()) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            default -> 1;
        };
    }

    private static int[] customPalette(String value) {
        int color;
        try {
            color = 0xFF000000 | Integer.parseInt(value.substring(1), 16);
        } catch (RuntimeException invalid) {
            return RARITY[0];
        }
        return new int[]{color, color};
    }

    static int scaledHeight(float scale) {
        return pixels(HEIGHT, scale);
    }

    private static void renderClaimedBadge(GuiGraphics graphics, float x, float y, int size, int color) {
        // Finish buffered items before the immediate texture draw; model depth must not mask the check.
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        try {
            JournalNavButton.drawIcon(graphics, "mail_check", 0, 0, size, color);
        } finally {
            graphics.pose().popPose();
            RenderSystem.enableDepthTest();
        }
    }

    private static int pixels(int value, float scale) {
        return Math.max(1, Math.round(value * scale));
    }

    private static int color(int value, float alpha) {
        return value & 0x00FFFFFF | Math.clamp(Math.round((value >>> 24) * alpha), 0, 255) << 24;
    }
}
