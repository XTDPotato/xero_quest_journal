package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Global centered status notification above the hotbar, near the action bar. */
public final class XeroTips {
    private static Component message;
    private static long expiresAt;
    private static final Deque<Component> pending = new ArrayDeque<>();
    private static final Deque<Component> history = new ArrayDeque<>();
    private static final UiMotion motion = new UiMotion(false, System.nanoTime());
    private static final UiCompositor compositor = new UiCompositor();

    private XeroTips() {
    }

    public static void show(Component value) {
        if (value == null || value.getString().isBlank()) return;
        Component copy = value.copy();
        if (pending.size() >= 64) pending.removeFirst();
        pending.addLast(copy);
        history.addLast(copy);
        while (history.size() > 100) history.removeFirst();
        advance();
    }

    public static Component currentMessage() {
        advance();
        return message;
    }

    public static List<Component> history() { return history.stream().map(Component::copy).map(c -> (Component) c).toList(); }
    public static int queuedCount() { return pending.size(); }

    public static void clear() {
        pending.clear();
        history.clear();
        message = null;
        motion.target(false, System.nanoTime(), 0);
        compositor.close();
    }

    private static void advance() {
        long now = System.nanoTime();
        int duration = AnimatedQuestScreen.animationMillis();
        motion.advance(now, duration);
        if (message != null && now >= expiresAt && motion.visible()) motion.target(false, now, duration);
        if (message != null && !motion.visible() && motion.settled()) message = null;
        if (message == null && !pending.isEmpty()) {
            message = pending.removeFirst();
            expiresAt = now + Math.max(0L, 3_000L - duration) * 1_000_000L;
            motion.target(true, now, duration);
        }
    }

    public static void render(GuiGraphics graphics) {
        advance();
        if (message == null) {
            compositor.close();
            return;
        }
        if (Minecraft.getInstance().player == null) {
            clear();
            message = null;
            return;
        }
        Minecraft game = Minecraft.getInstance();
        int screenWidth = game.getWindow().getGuiScaledWidth();
        int screenHeight = game.getWindow().getGuiScaledHeight();
        var layout = StatusToastRenderer.layout(game.font, message, screenWidth - 24, screenHeight - 84);
        int height = layout.height();
        // Anchor the bottom rather than the first line: wrapped messages grow upward,
        // staying clear of health, hunger, experience and the hotbar even during entry.
        int y = Math.max(4, screenHeight - height - 54);
        Runnable draw = () -> StatusToastRenderer.draw(graphics, game.font, layout, screenWidth / 2, y);
        if (motion.settled()) {
            compositor.close();
            draw.run();
        } else {
            compositor.capture(graphics, draw);
            compositor.band(graphics, screenWidth, screenHeight, y, y + height,
                    6 * (1 - motion.eased()), motion.eased());
        }
    }
}
