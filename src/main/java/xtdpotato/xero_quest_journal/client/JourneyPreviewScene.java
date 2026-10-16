package xtdpotato.xero_quest_journal.client;

import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;

import java.util.ArrayList;
import java.util.List;

/** Design-space component anchors shared by the chapter preview, hit tests and selection outlines. */
final class JourneyPreviewScene {
    static final int WIDTH = 640, HEIGHT = 360;
    record Part(String slot, String kind, String fallback, int x, int y, int width, int height, boolean centered) {}

    private JourneyPreviewScene() {}

    static List<Part> parts(QuestCatalog.Chapter chapter, List<QuestDefinition> tasks) {
        var result = new ArrayList<Part>();
        result.add(icon("header", "guide", 16, 12, 20, 20));
        result.add(text("header", label("title"), 44, 18, 138, 12, false));
        result.add(icon("previous", "back", 214, 19, 12, 12));
        result.add(icon("tab", chapter.icon(), 240, 17, 16, 16));
        result.add(text("tab", chapter.name(), 320, 20, 126, 12, true));
        result.add(icon("next", "@next", 408, 19, 12, 12));
        result.add(icon("close", "close", 606, 12, 20, 20));
        result.add(icon("chapter", chapter.icon().isBlank() ? "minecraft:book" : chapter.icon(), 78, 136, 44, 44));
        result.add(text("chapter", chapter.name(), 100, 198, 138, 12, true));
        result.add(icon("marker", "@marker", 89, 215, 22, 22));
        var first = tasks.isEmpty() ? null : tasks.getFirst();
        String image = first == null ? "minecraft:chest"
                : first.hotEvent().image().isBlank() ? first.icon() : first.hotEvent().image();
        String name = first == null ? label("element.card") : QuestText.title(first).getString();
        result.add(icon("card", image, 226, 128, 166, 82));
        result.add(text("card", name, 233, 223, 136, 12, false));
        result.add(icon("complete", "@complete", 366, 218, 18, 18));
        result.add(text("subtitle", first == null ? label("element.subtitle") : first.subtitle(),
                233, 248, 154, 14, false));
        var second = tasks.size() > 1 ? tasks.get(1) : first;
        result.add(icon("branch", second == null ? "minecraft:book" : second.icon(), 451, 252, 24, 27));
        result.add(text("branch", second == null ? label("element.branch") : QuestText.title(second).getString(),
                478, 263, 128, 14, false));
        result.add(icon("scroll_thumb", "@vertical", 318, 337, 6, 10));
        return List.copyOf(result);
    }
    private static Part icon(String slot, String fallback, int x, int y, int w, int h) {
        return new Part(slot, "icon", fallback, x, y, w, h, false);
    }
    private static Part text(String slot, String fallback, int x, int y, int w, int h, boolean centered) {
        return new Part(slot, "text", fallback, x, y, w, h, centered);
    }
    private static String label(String key) {
        return Component.translatable("screen.xero_quest_journal.journey." + key).getString();
    }
}
