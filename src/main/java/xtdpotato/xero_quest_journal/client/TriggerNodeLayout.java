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
    record Text(String value, int x, int y, int width, int color, boolean boxed,
                String property, List<net.minecraft.util.FormattedCharSequence> lines) {
        int height(Font font) { return Math.max(1, lines.size()) * (font.lineHeight + 1); }
    }
    record Slot(String suffix, boolean left, String value, int x, int y, int width, int height, boolean bound) {}
    record Layout(int width, int height, int headerHeight, List<Text> texts, List<Slot> slots) {
        void render(GuiGraphics g, Font font) { render(g, font, Integer.MIN_VALUE, Integer.MAX_VALUE); }
        void render(GuiGraphics g, Font font, int visibleTop, int visibleBottom) {
            for (Text text : texts) {
                if (text.boxed()) {
                    ControlAppearance.draw(g, text.x() - 3, text.y() - 3, text.width() + 6,
                            text.height(font) + 4, "rounded", 3,
                            TriggerNodeStyle.VALUE_BOX, TriggerNodeStyle.VALUE_BORDER, 1);
                }
                int lineY = text.y();
                for (var line : text.lines()) {
                    if (lineY + font.lineHeight >= visibleTop && lineY < visibleBottom)
                        g.drawString(font, line, text.x(), lineY, text.color(), false);
                    lineY += font.lineHeight + 1;
                }
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
    private static Text block(Font font, String value, int x, int y, int width, int color,
                              boolean boxed, String property, boolean full) {
        String display = preview(font, value, width, full);
        return new Text(display, x, y, width, color, boxed, property,
                List.copyOf(font.split(Component.literal(display), Math.max(1, width))));
    }
    private static String plain(net.minecraft.util.FormattedCharSequence line) {
        var result = new StringBuilder();
        line.accept((index, style, codePoint) -> { result.appendCodePoint(codePoint); return true; });
        return result.toString();
    }
    private static String preview(Font font, String value, int width, boolean full) {
        if (full) return value;
        var lines = font.split(Component.literal(value), Math.max(1, width));
        if (lines.size() <= 2) return value;
        return plain(lines.getFirst()) + "\n"
                + font.plainSubstrByWidth(plain(lines.get(1)), Math.max(1, width - font.width("..."))) + "...";
    }
    static String editableProperty(TriggerGraph.Node node) {
        return switch (node.type()) {
            case "js" -> node.get("mode", "script").equals("script") ? "script" : "";
            case "kubejs_script" -> "script";
            case "command" -> "command";
            case "set_property", "set_theme", "set_variable", "calculate_variable", "set_context" -> "value";
            case "mail_send" -> "body";
            case "mail_set_property" -> List.of("read", "starred", "treasured").contains(
                    node.get("property", "subject")) ? "" : "value";
            default -> "";
        };
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
        boolean full = Boolean.parseBoolean(node.get("showAll", "true"));
        String editable = editableProperty(node);
        List<String> clauses = List.of("and", "or").contains(node.get("logic", "single")) ? List.of("", "2") : List.of("");
        String detail = node.type().equals("context") ? text("field." + node.get("field", ""))
                : node.type().equals("variable") ? node.get("variable", "") + " " + text("operator." + node.get("operator", "eq"))
                        + " " + right(node, "")
                : node.type().equals("event") ? text("event." + node.get("event", "click"))
                : node.type().equals("js") ? node.get("script", "false")
                : node.type().equals("kubejs_script") ? node.get("script", "")
                : node.type().equals("set_variable") ? node.get("variable", "") + " = " + node.get("value", "")
                : node.type().equals("calculate_variable") ? node.get("variable", "") + " ← "
                + node.get("operation", "add") + " " + node.get("operandVariable", node.get("operand", ""))
                : node.type().equals("delay") ? Component.translatable(
                        "screen.xero_quest_journal.graph.delay.summary", node.get("ticks", "20")).getString()
                : node.type().equals("set_context") ? text("field." + node.get("field", "experienceLevel"))
                        + " = " + node.get("value", "0") + "\n"
                        + (node.get("scope", "player").equals("player") ? node.get("player", "@s")
                        : node.get("dimension", "").isBlank() ? text("context.current_dimension") : node.get("dimension", ""))
                : node.type().equals("command") ? node.get("command", "")
                : node.type().equals("set_overview_background") ? text("overview.scope." + node.get("scope", "player"))
                        + "\n" + (node.get("source", "").isBlank() ? text("overview.reset") : node.get("source", ""))
                : node.type().equals("set_hot_event_background") ? node.get("mode", "cover")
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
        texts.add(block(font, title, TITLE_X, 5, titleWidth, TriggerNodeStyle.kind(node).text, false, "", true));
        int header = 5 + textHeight(font, title, titleWidth) + 4;
        int y = header + 6;
        String note = node.get("note", "");
        if (visual) {
            if (!note.isBlank()) {
                var block = block(font, note, 7, y, width - 14, 0xFF9A9AA3, false, "note", full);
                texts.add(block);
                y += block.height(font) + 5;
            }
            if (!node.get("logic", "single").equals("single")) {
                String logic = text("logic." + node.get("logic", "single"));
                texts.add(block(font, logic, 7, y, width - 14, 0xFFF3C46A, false, "logic", true));
                y += textHeight(font, logic, width - 14) + 5;
            }
            int column = (width - 20) / 2;
            for (String suffix : clauses) {
                String operator = text("operator." + node.get("operator" + suffix, "eq"));
                texts.add(block(font, operator, 7, y, width - 14, 0xFFC9CDD6, false, "operator" + suffix, true));
                y += textHeight(font, operator, width - 14) + 4;
                String left = preview(font, left(node, suffix), column - 8, full);
                String right = preview(font, right(node, suffix), column - 8, full);
                int h = Math.max(textHeight(font, left, column - 8), textHeight(font, right, column - 8)) + 8;
                slots.add(new Slot(suffix, true, left, 7, y, column, h, true));
                slots.add(new Slot(suffix, false, right, 13 + column, y, column, h,
                        !node.get("expectedVar" + suffix, "").isBlank() || !node.get("expectedField" + suffix, "").isBlank()));
                y += h + 8;
            }
        } else {
            if (!note.isBlank()) {
                var block = block(font, note, 7, y, width - 14, 0xFF9A9AA3, false, "note", full);
                texts.add(block);
                y += block.height(font) + 3;
            }
            if (!editable.isEmpty() && !List.of("js", "kubejs_script", "command").contains(node.type())) {
                var summary = block(font, detail, 7, y, width - 14, 0xFF9A9AA3, false, "", full);
                texts.add(summary);
                y += summary.height(font) + 5;
                detail = node.get(editable, "");
            }
            var block = block(font, detail, 7, y, width - 14, 0xFFD6DBE4, true,
                    node.type().equals("context") ? "field" : editable, full);
            texts.add(block);
            y += block.height(font) + 7;
            if (node.type().equals("context") && xtdpotato.xero_quest_journal.core.model.TriggerNodeLibrary.PLAYER_VALUES
                    .contains(node.get("field", "")) && !List.of("eventType", "eventTarget").contains(node.get("field", ""))) {
                var owner = block(font, text("context.player") + ": " + node.get("player", "@s"),
                        7, y, width - 14, 0xFF91B2D6, false, "player", full);
                texts.add(owner);
                y += owner.height(font) + 5;
            }
        }
        int requestedHeight = Integer.parseInt(node.get("layoutHeight", "0"));
        return new Layout(width, Math.max(Math.max(MIN_HEIGHT, y), requestedHeight), header,
                List.copyOf(texts), List.copyOf(slots));
    }
}
