package xtdpotato.xero_quest_journal.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import xtdpotato.xero_quest_journal.core.network.QuestClientInbox;

import java.util.ArrayDeque;
import java.util.Deque;

/** Global top-center messages sent by /xero_toast. */
public final class XeroToast {
    private static final Deque<Component> queue = new ArrayDeque<>();
    private static Component current;
    private static long started;

    private XeroToast() {}

    public static void tick() {
        long now = System.nanoTime();
        while (true) {
            var payload = QuestClientInbox.takeToast();
            if (payload == null) break;
            if (queue.size() >= 16) queue.removeFirst();
            queue.addLast(parse(payload.content()));
        }
        if (current != null && now - started >= 5_000_000_000L) current = null;
        if (current == null && !queue.isEmpty()) {
            current = queue.removeFirst();
            started = now;
        }
    }

    public static void clear() {
        queue.clear();
        current = null;
    }

    public static void render(GuiGraphics graphics) {
        tick();
        if (current == null) return;
        Minecraft game = Minecraft.getInstance();
        int screenWidth = game.getWindow().getGuiScaledWidth();
        int screenHeight = game.getWindow().getGuiScaledHeight();
        var layout = StatusToastRenderer.layout(game.font, current, Math.min(720, screenWidth - 32),
                Math.max(22, screenHeight - 100));
        double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
        float enter = (float) Math.clamp(elapsed / .22, 0, 1);
        float exit = (float) Math.clamp((5 - elapsed) / .28, 0, 1);
        float alpha = Math.min(enter, exit);
        int top = 42 - Math.round((1 - enter) * 12);
        if (alpha > .05F) StatusToastRenderer.draw(graphics, game.font, layout, screenWidth / 2, top, alpha);
    }

    private static Component parse(String raw) {
        MutableComponent result = Component.empty();
        StringBuilder text = new StringBuilder();
        Style style = Style.EMPTY;
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if ((current == '&' || current == '\u00a7') && i + 1 < raw.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(raw.charAt(i + 1));
                if (formatting != null) {
                    if (!text.isEmpty()) {
                        result.append(Component.literal(text.toString()).withStyle(style));
                        text.setLength(0);
                    }
                    style = formatting == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(formatting);
                    i++;
                    continue;
                }
            }
            text.append(current);
        }
        if (!text.isEmpty()) result.append(Component.literal(text.toString()).withStyle(style));
        return result;
    }
}
