package xtdpotato.xero_quest_journal.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, display-only HTML subset for text controls. Unknown markup is never executed. */
public final class QuestRichText {
    private static final Pattern MARKUP = Pattern.compile(
            "(?i)<\\s*/?\\s*(?:b|strong|i|em|u|s|strike|br|p|div|span|font)(?:\\s|/?>)");
    private static final Pattern COLOR = Pattern.compile(
            "(?i)(?:^|;)\\s*color\\s*:\\s*(#[0-9a-f]{3}|#[0-9a-f]{6})(?:\\s*;|\\s*$)");
    private record Key(String value, Style style) {}
    private static final Map<Key, MutableComponent> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, MutableComponent> eldest) {
            return size() > 64;
        }
    };

    private QuestRichText() {}

    public static MutableComponent parse(String value, Style base) {
        if (value == null || value.isEmpty()) return Component.empty();
        if (!MARKUP.matcher(value).find()) return Component.literal(value).setStyle(base);
        Key key = new Key(value, base);
        MutableComponent cached = CACHE.get(key);
        if (cached != null) return cached;
        MutableComponent result = Component.empty();
        for (Node node : Jsoup.parseBodyFragment(value).body().childNodes()) append(result, node, base);
        CACHE.put(key, result);
        return result;
    }

    private static void append(MutableComponent result, Node node, Style style) {
        if (node instanceof TextNode text) {
            result.append(Component.literal(text.getWholeText()).setStyle(style));
            return;
        }
        if (!(node instanceof Element element)) return;
        String tag = element.normalName();
        if (tag.equals("br")) {
            result.append(Component.literal("\n").setStyle(style));
            return;
        }
        if (tag.equals("p") && !result.getString().isEmpty() && !result.getString().endsWith("\n"))
            result.append(Component.literal("\n"));
        Style nested = switch (tag) {
            case "b", "strong" -> style.withBold(true);
            case "i", "em" -> style.withItalic(true);
            case "u" -> style.withUnderlined(true);
            case "s", "strike" -> style.withStrikethrough(true);
            default -> style;
        };
        String color = tag.equals("span") ? cssColor(element.attr("style"))
                : tag.equals("font") ? element.attr("color") : "";
        Integer rgb = rgb(color);
        if (rgb != null) nested = nested.withColor(TextColor.fromRgb(rgb));
        for (Node child : element.childNodes()) append(result, child, nested);
        if ((tag.equals("p") || tag.equals("div")) && !result.getString().endsWith("\n"))
            result.append(Component.literal("\n"));
    }

    private static String cssColor(String style) {
        Matcher match = COLOR.matcher(style);
        return match.find() ? match.group(1) : "";
    }

    private static Integer rgb(String color) {
        if (!color.matches("(?i)#[0-9a-f]{3}(?:[0-9a-f]{3})?")) return null;
        String hex = color.substring(1);
        if (hex.length() == 3) hex = "" + hex.charAt(0) + hex.charAt(0)
                + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        return Integer.parseInt(hex, 16);
    }
}
