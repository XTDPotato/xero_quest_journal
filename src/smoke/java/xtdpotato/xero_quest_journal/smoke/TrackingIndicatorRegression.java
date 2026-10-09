package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import xtdpotato.xero_quest_journal.client.JournalListButton;

/** Reads the actual rendered right-hand slot, not merely the synchronized tracking flag. */
public final class TrackingIndicatorRegression {
    private TrackingIndicatorRegression() {}

    public static void check(Minecraft game, String id, boolean expected) throws Exception {
        var row = game.screen.children().stream().filter(JournalListButton.class::isInstance)
                .map(JournalListButton.class::cast).filter(b -> b.styleId().equals("quest-" + id))
                .findFirst().orElseThrow();
        if (!row.visible) throw new IllegalStateException("Tracking test row is outside viewport");
        int color = switch (row.trackingStyle()) {
            case "main" -> 0xFFEB30;
            case "commission" -> 0xD6B7FF;
            default -> 0x9DF2FF;
        };
        int matches = 0;
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            int top = row.getY() + (row.getHeight() - 14) / 2;
            for (int x = row.getRight() - 18; x < row.getRight() - 4; x++) {
                for (int y = top; y < top + 14; y++) {
                    int px = (int) ((x + 0.5) * image.getWidth() / game.screen.width);
                    int py = (int) ((y + 0.5) * image.getHeight() / game.screen.height);
                    int abgr = image.getPixelRGBA(px, py);
                    int rgb = (abgr & 255) << 16 | abgr & 0xFF00 | abgr >>> 16 & 255;
                    if (Math.abs((rgb >>> 16 & 255) - (color >>> 16 & 255)) <= 2
                            && Math.abs((rgb >>> 8 & 255) - (color >>> 8 & 255)) <= 2
                            && Math.abs((rgb & 255) - (color & 255)) <= 2) matches++;
                }
            }
        }
        if (expected ? matches < 2 : matches != 0) {
            throw new IllegalStateException("Tracking icon pixels mismatch on " + id + ": " + matches);
        }
    }
}
