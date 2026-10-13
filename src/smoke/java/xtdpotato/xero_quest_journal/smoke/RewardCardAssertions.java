package xtdpotato.xero_quest_journal.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;

final class RewardCardAssertions {
    private RewardCardAssertions() {}

    static void claimedBadge(Minecraft game, Screen screen, int cardX, int cardY, float scale,
                             String name) throws Exception {
        var texture = ResourceLocation.fromNamespaceAndPath("xero_quest_journal",
                "textures/gui/navigation/mail_check.png");
        int size = Math.max(1, Math.round(21 * scale));
        float x = cardX + (Math.max(1, Math.round(34 * scale)) - size) / 2F;
        float y = cardY + Math.max(1, Math.round(2 * scale))
                + (Math.max(1, Math.round(24 * scale)) - size) / 2F;
        int checked = 0;
        try (var input = game.getResourceManager().getResourceOrThrow(texture).open();
             var mask = NativeImage.read(input);
             var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            var output = Path.of("..", "build", "verification");
            Files.createDirectories(output);
            image.writeToFile(output.resolve(name + ".png"));
            for (int dy = 0; dy < size; dy++) {
                for (int dx = 0; dx < size; dx++) {
                    int tx = (int) ((dx + .5) * mask.getWidth() / size);
                    int ty = (int) ((dy + .5) * mask.getHeight() / size);
                    if (!opaqueCore(mask, tx, ty)) continue;
                    int pixel = image.getPixelRGBA((int) ((x + dx + .5) * image.getWidth() / screen.width),
                            (int) ((y + dy + .5) * image.getHeight() / screen.height));
                    int red = pixel & 255, green = pixel >>> 8 & 255, blue = pixel >>> 16 & 255;
                    if (Math.abs(red - 168) > 4 || Math.abs(green - 245) > 4 || Math.abs(blue - 20) > 4) {
                        throw new AssertionError("Claim marker is covered at " + (x + dx) + "," + (y + dy)
                                + " in " + name + ": " + red + "," + green + "," + blue);
                    }
                    checked++;
                }
            }
        }
        if (checked < 3) throw new AssertionError("Insufficient claim-marker coverage in " + name);
    }

    static void opaquePixel(Minecraft game, Screen screen, int x, int y, int expected) {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            int pixel = image.getPixelRGBA((int) ((x + .5) * image.getWidth() / screen.width),
                    (int) ((y + .5) * image.getHeight() / screen.height));
            int rgb = (pixel & 255) << 16 | pixel & 0xFF00 | pixel >>> 16 & 255;
            if (rgb != expected) throw new AssertionError("Parent item leaked through dialog at "
                    + x + "," + y + ": " + Integer.toHexString(rgb));
        }
    }

    static void circularConfirm(Minecraft game, Screen screen, int x, int y, int height) {
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            int left = height, top = height, right = -1, bottom = -1;
            for (int dy = 0; dy < height; dy++) {
                for (int dx = 0; dx < height; dx++) {
                    int pixel = image.getPixelRGBA((int) ((x + dx + .5) * image.getWidth() / screen.width),
                            (int) ((y + dy + .5) * image.getHeight() / screen.height));
                    if ((pixel & 0xFFFFFF) != 0x00D9FF) continue;
                    left = Math.min(left, dx);
                    right = Math.max(right, dx);
                    top = Math.min(top, dy);
                    bottom = Math.max(bottom, dy);
                }
            }
            if (right < left || right - left != bottom - top)
                throw new AssertionError("Confirmation ring is not circular: "
                        + (right - left + 1) + "x" + (bottom - top + 1));
        }
    }

    private static boolean opaqueCore(NativeImage mask, int x, int y) {
        if (x < 1 || y < 1 || x >= mask.getWidth() - 1 || y >= mask.getHeight() - 1) return false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((mask.getPixelRGBA(x + dx, y + dy) >>> 24) != 255) return false;
            }
        }
        return true;
    }
}
