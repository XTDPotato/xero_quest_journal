package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;

import java.util.ArrayList;
import java.util.List;

/** The same measured geometry is used for rendering, hit testing and graph overviews. */
final class TriggerNodeLayout {
    static final int MIN_WIDTH = 118, MIN_HEIGHT = 54;
    /** Left inset of the header title, leaving room for the category glyph. */
    static final int TITLE_X = 20;
    static final int GLYPH_SIZE = 9;
    /** {@code boxed} text sits in a dark value field, like the inputs of the reference editor. */
    record Text(String value, int x, int y, int width, int color, boolean boxed) {}
    record Slot(String suffix, boolean left, String value, int x, int y, int width, int height, boolean bound) {}
    record Layout(int width, int height, int headerHeight, List<Text> texts, List<Slot> slots) {
        void render(GuiGraphics g, Font font) {
            for (Text text : texts) {
                if (text.boxed()) {
                    int lines = Math.max(1, font.split(Component.literal(text.value()), Math.max(1, text.width())).size());
                    ControlAppearance.draw(g, text.x() - 3, text.y() - 3, text.width() + 6,
                            lines * (font.lineHeight + 1) + 4, "rounded", 3,
                            TriggerNodeStyle.VALUE_BOX, TriggerNodeStyle.VALUE_BORDER, 1);
                }
                draw(g, font, text.value(), text.x(), text.y(), text.width(), text.color());
            }
            for (Slot slot : slots) {
                // Bound slots reference a field or variable and read blue, literals stay neutral.
                int edge = slot.bound() ? 0xFF4C7FD1 : TriggerNodeStyle.VALUE_BORDER;
                ControlAppearance.draw(g, slot.x(), slot.y(), slot.width(), slot.height(), "rounded", 3,
                        TriggerNodeStyle.VALUE_BOX, edge, 1);
                draw(g, font, slot.value(), slot.x() + 4, slot.y() + 4, slot.width() - 8,
                        slot.bound() ? 0xFF8DB8F2 : 0xFFD6DBE4);
            }
        }
    }
    private static String text(String key) {
        return Component.translatable("screen.xero_quest_journal.graph." + key).getString();
    }
    private static int textHeight(Font font, String value, int width) {
        return Math.max(1, font.split(Component.literal(value), Math.max(1, width)).size()) * (font.lineHeight + 1);
    }
    private static void draw(GuiGraphics g, Font font, String value, int x, int y, int width, int color) {
        for (var line : font.split(Component.literal(value), Math.max(1, width))) {
            g.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 1;
        }
    }
    private static String left(TriggerGraph.Node node, String suffix) {
        String field = node.get("field" + suffix, suffix.isEmpty() ? "eventType" : "eventTarget");
        return field.startsWith("var:") ? field.substring(4)
                : TriggerGraph.FIELDS.contains(field) ? text("field." + field) : text("variable.slot_left");
    }
    private static String right(TriggerGraph.Node node, String suffix) {
        String variable = node.get("expectedVar" + suffix, ""), field = node.get("expectedField" + suffix, "");
        return !variable.isBlank() ? variable : !field.isBlank() ? text("field." + field)
                : node.get("expected" + suffix, "").isBlank() ? text("variable.slot_right") : node.get("expected" + suffix, "");
    }
    static Layout measure(TriggerGraph.Node node, Font font) {
        String title = QuestTriggerGraphScreen.nodeTitle(node).getString();
        boolean visual = node.type().equals("js") && node.get("mode", "script").equals("visual");
        List<String> clauses = List.of("and", "or").contains(node.get("logic", "single")) ? List.of("", "2") : List.of("");
        String detail = node.type().equals("context") ? text("field." + node.get("field", ""))
                : node.type().equals("variable") ? node.get("variable", "") + " " + text("operator." + node.get("operator", "eq"))
                        + " " + right(node, "")
                : node.type().equals("event") ? text("event." + node.get("event", "click"))
                : node.type().equals("js") ? node.get("script", "false").replace('\n', ' ')
                : node.type().equals("set_variable") ? node.get("variable", "") + " = " + node.get("value", "")
                : node.type().equals("delay") ? Component.translatable(
                        "screen.xero_quest_journal.graph.delay.summary", node.get("ticks", "20")).getString()
                : node.type().equals("set_context") ? text("field." + node.get("field", "experienceLevel"))
                        + " = " + node.get("value", "0") + "\n"
                        + (node.get("scope", "player").equals("player") ? node.get("player", "@s")
                        : node.get("dimension", "").isBlank() ? text("context.current_dimension") : node.get("dimension", ""))
                : node.type().equals("command") ? node.get("command", "")
                : node.type().equals("set_overview_background") ? text("overview.scope." + node.get("scope", "player"))
                        + "\n" + (node.get("source", "").isBlank() ? text("overview.reset") : node.get("source", ""))
                : node.type().startsWith("mail_") ? node.get("mailId", "") + "\n"
                        + (node.type().equals("mail_check") ? text("mail.state." + node.get("state", "exists"))
                        : node.type().equals("mail_send") ? node.get("subject", "")
                        : node.type().equals("mail_set_property") ? text("mail." + node.get("property", "subject"))
                        + ": " + node.get("value", "") : "")
                : node.type().equals("advance_step") && node.get("target", "").isBlank() ? text("current_task")
                : node.get("target", node.get("property", node.id()));
        int width = Math.max(MIN_WIDTH, font.width(title) + TITLE_X + 10);
        if (visual) {
            for (String suffix : clauses) {
                width = Math.max(width, 2 * Math.max(font.width(left(node, suffix)), font.width(right(node, suffix))) + 40);
                width = Math.max(width, font.width(text("operator." + node.get("operator" + suffix, "eq"))) + 16);
            }
        } else width = Math.max(width, font.width(detail) + 16);
        width = Math.min(360, Math.max(width, font.width(node.get("note", "")) + 16));
        int requestedWidth = Integer.parseInt(node.get("layoutWidth", "0"));
        if (requestedWidth > 0) width = Math.max(MIN_WIDTH, requestedWidth);
        var texts = new ArrayList<Text>();
        var slots = new ArrayList<Slot>();
        int titleWidth = width - TITLE_X - 6;
        texts.add(new Text(title, TITLE_X, 5, titleWidth, TriggerNodeStyle.kind(node).text, false));
        int header = 5 + textHeight(font, title, titleWidth) + 4;
        int y = header + 6;
        String note = node.get("note", "");
        if (visual) {
            if (!note.isBlank()) {
                texts.add(new Text(note, 7, y, width - 14, 0xFF9A9AA3, false));
                y += textHeight(font, note, width - 14) + 5;
            }
            if (!node.get("logic", "single").equals("single")) {
                String logic = text("logic." + node.get("logic", "single"));
                texts.add(new Text(logic, 7, y, width - 14, 0xFFF3C46A, false));
                y += textHeight(font, logic, width - 14) + 5;
            }
            int column = (width - 20) / 2;
            for (String suffix : clauses) {
                String operator = text("operator." + node.get("operator" + suffix, "eq"));
                texts.add(new Text(operator, 7, y, width - 14, 0xFFC9CDD6, false));
                y += textHeight(font, operator, width - 14) + 4;
                int h = Math.max(textHeight(font, left(node, suffix), column - 8),
                        textHeight(font, right(node, suffix), column - 8)) + 8;
                slots.add(new Slot(suffix, true, left(node, suffix), 7, y, column, h, true));
                slots.add(new Slot(suffix, false, right(node, suffix), 13 + column, y, column, h,
                        !node.get("expectedVar" + suffix, "").isBlank() || !node.get("expectedField" + suffix, "").isBlank()));
                y += h + 8;
            }
        } else {
            if (!note.isBlank()) {
                texts.add(new Text(note, 7, y, width - 14, 0xFF9A9AA3, false));
                y += textHeight(font, note, width - 14) + 3;
            }
            texts.add(new Text(detail, 7, y, width - 14, 0xFFD6DBE4, !detail.isBlank()));
            y += textHeight(font, detail, width - 14) + 7;
        }
        int requestedHeight = Integer.parseInt(node.get("layoutHeight", "0"));
        return new Layout(width, Math.max(Math.max(MIN_HEIGHT, y), requestedHeight), header,
                List.copyOf(texts), List.copyOf(slots));
    }
}
