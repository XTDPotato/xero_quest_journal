package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import xtdpotato.xero_quest_journal.core.network.QuestClientInbox;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

/** One complete, non-interactive banner per command, including repeated identical commands. */
public final class AnecdoteToast {
    private static final long DURATION_NANOS = 3_200_000_000L;
    private static QuestNetwork.AnecdotePayload current;
    private static long started;

    private AnecdoteToast() {}

    public static void clear() {
        current = null;
        started = 0;
    }

    public static void tick() {
        if (Minecraft.getInstance().player == null) return;
        long now = System.nanoTime();
        if (current != null && now - started >= DURATION_NANOS) current = null;
        // Consume exactly one event after the previous banner has fully faded out.
        if (current == null) {
            current = QuestClientInbox.takeAnecdote();
            if (current != null) started = now;
        }
    }

    public static void render(GuiGraphics graphics) {
        Minecraft game = Minecraft.getInstance();
        if (current == null || game.player == null || game.options.hideGui) return;
        draw(graphics, game.getWindow().getGuiScaledWidth(), game.getWindow().getGuiScaledHeight(),
                System.nanoTime() - started, ClientPreferences.get().reducedMotion, current);
    }

    private static void draw(GuiGraphics graphics, int width, int height, long elapsed, boolean reduced,
                             QuestNetwork.AnecdotePayload payload) {
        if (elapsed < 0 || elapsed >= DURATION_NANOS) return;
        float time = elapsed / 1_000_000_000F;
        float enter = smooth(time / .3F);
        float exit = smooth((3.2F - time) / .32F);
        float alpha = Math.min(enter, exit);
        if (alpha <= 0) return;
        float requestedScale = (float) payload.scale();
        float fit = Math.min(1, Math.min(width / (480F * requestedScale),
                height / (141F * requestedScale)));
        int bandWidth = Math.round(Math.min(820, width * .86F / requestedScale) / fit);
        int half = bandWidth / 2;
        float scale = (reduced ? 1 : .96F + .04F * enter) * (float) payload.scale();
        boolean left = has(payload, "left") || has(payload, "start");
        boolean right = has(payload, "right") || has(payload, "end");
        boolean top = has(payload, "top");
        boolean bottom = has(payload, "bottom");
        float centerX = left ? 4 + half * fit * scale
                : right ? width - 4 - half * fit * scale : width / 2F;
        float centerY = top ? 4 + 111 * fit * scale
                : bottom ? height - 4 - 22 * fit * scale : height / 2F + 44.5F * fit * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX + payload.offsetX(),
                centerY + payload.offsetY() + (reduced ? 0 : (1 - enter) * 8), 0);
        graphics.pose().scale(fit * scale, fit * scale, 1);
        try {
            // Feather the orange band at both ends, without darkening the surrounding world.
            for (int part = 0; part < 80; part++) {
                int x0 = -half + bandWidth * part / 80;
                int x1 = -half + bandWidth * (part + 1) / 80;
                float edge = Math.min(1, Math.min(part + .5F, 79.5F - part) / 14);
                graphics.fillGradient(x0, -22, x1, 22,
                        fade(0xBBA4612E, alpha * edge), fade(0xA77D4728, alpha * edge));
                graphics.fill(x0, -22, x1, -21, fade(0xFFE8B864, alpha * edge * .8F));
                graphics.fill(x0, 21, x1, 22, fade(0xFFDC9A45, alpha * edge * .7F));
                graphics.fill(x0, -19, x1, -18, fade(0xFFFFD991, alpha * edge * .16F));
            }
            float stamp = reduced ? 1 : smooth(Math.min(1, time / .25F));
            graphics.pose().pushPose();
            graphics.pose().translate(0, -67 - (reduced ? 0 : (1 - stamp) * 5), 0);
            float iconScale = reduced ? 1 : .85F + .15F * stamp;
            graphics.pose().scale(iconScale, iconScale, 1);
            ResourceLocation emblem = ResourceLocation.fromNamespaceAndPath(
                    "xero_quest_journal", "textures/gui/anecdote_emblem.png");
            graphics.setColor(1, 1, 1, alpha);
            graphics.blit(emblem, -44, -44, 88, 88, 0, 0, 256, 256, 256, 256);
            graphics.setColor(1, 1, 1, 1);
            graphics.pose().popPose();
            Component title = Component.translatable("screen.xero_quest_journal.anecdote.recorded");
            float textScale = Math.min(2, (bandWidth - 40F)
                    / Math.max(1, Minecraft.getInstance().font.width(title)));
            MailDecor.centered(graphics, Minecraft.getInstance().font, title.getString(), 0,
                    -Math.round(Minecraft.getInstance().font.lineHeight * textScale / 2F),
                    textScale, fade(0xFFFFFCED, alpha));
            if (!reduced) {
                sparkle(graphics, -45, -87, 7, alpha * pulse(time, .12F, .65F));
                sparkle(graphics, 37, -45, 5, alpha * pulse(time, .38F, .7F));
                sparkle(graphics, -8, -24, 4, alpha * pulse(time, .55F, .6F));
                float sweep = Math.clamp((time - .2F) / .7F, 0, 1);
                if (sweep > 0 && sweep < 1) {
                    int x = Math.round(-half + bandWidth * sweep);
                    sparkle(graphics, x, -21, 3, alpha * (float) Math.sin(Math.PI * sweep));
                }
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static boolean has(QuestNetwork.AnecdotePayload payload, String anchor) {
        for (String part : payload.position().split(",")) if (part.equals(anchor)) return true;
        return false;
    }

    private static void sparkle(GuiGraphics graphics, int x, int y, int size, float alpha) {
        if (alpha <= 0) return;
        for (int row = -size; row <= size; row++) {
            int half = Math.max(0, (size - Math.abs(row)) / 4);
            graphics.fill(x - half, y + row, x + half + 1, y + row + 1,
                    fade(0xFFFFE7A3, alpha));
        }
        graphics.fill(x - size, y, x + size + 1, y + 1, fade(0xFFFFE7A3, alpha));
        MailDecor.diamond(graphics, x, y, 1, fade(0xFFFFFFFF, alpha));
    }

    private static float pulse(float time, float start, float duration) {
        float phase = Math.clamp((time - start) / duration, 0, 1);
        return (float) Math.sin(phase * Math.PI);
    }

    private static float smooth(float value) {
        float bounded = Math.clamp(value, 0, 1);
        return bounded * bounded * (3 - 2 * bounded);
    }

    private static int fade(int color, float alpha) {
        return color & 0xFFFFFF | Math.clamp(Math.round((color >>> 24) * alpha), 0, 255) << 24;
    }
}
