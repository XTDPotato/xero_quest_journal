package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** One stable image slot for all states. Coordinates are relative to the padded content box. */
public final class ButtonControlContent {
    private ButtonControlContent() {}
    public record Box(int x, int y, int width, int height) {}
    public record Layout(Box image, Box text) {}
    public record Size(int width, int height) {}

    public static boolean present(String value) {
        return value != null && !value.isBlank() && !value.equals("inherit") && !value.equals("none");
    }
    public static boolean hasImage(LayoutEditorState.Element element) {
        return java.util.List.of("", "Hover", "Pressed").stream()
                .anyMatch(state -> present(element.attribute("buttonImage" + state, "")));
    }
    public static String image(LayoutEditorState.Element element, String state) {
        String value = state.isEmpty() ? "" : element.attribute("buttonImage" + state, "");
        return value.isBlank() || value.equals("inherit") ? element.attribute("buttonImage", "") : value;
    }
    private static int number(LayoutEditorState.Element element, String key, int fallback, int min, int max) {
        try { return Math.clamp(Integer.parseInt(element.attribute(key, Integer.toString(fallback))), min, max); }
        catch (NumberFormatException invalid) { return fallback; }
    }
    public static int imageWidth(LayoutEditorState.Element e) { return number(e, "buttonImageWidth", 16, 1, 256); }
    public static int imageHeight(LayoutEditorState.Element e) { return number(e, "buttonImageHeight", 16, 1, 256); }
    public static int gap(LayoutEditorState.Element e) { return number(e, "buttonImageGap", 4, 0, 128); }
    public static String position(LayoutEditorState.Element e) {
        String value = e.attribute("buttonImagePosition", "left");
        return java.util.List.of("left", "right", "top", "bottom", "only").contains(value) ? value : "left";
    }
    public static boolean vertical(LayoutEditorState.Element e) {
        return position(e).equals("top") || position(e).equals("bottom");
    }
    public static Size textSize(Font font, Component text, int width, float scale, int maxLines) {
        if (text.getString().isEmpty() || width <= 0) return new Size(0, 0);
        var lines = font.split(text, Math.max(1, (int) Math.floor(width / scale)));
        if (maxLines > 0) lines = lines.subList(0, Math.min(maxLines, lines.size()));
        int w = Math.min(width, (int) Math.ceil(lines.stream().mapToInt(font::width).max().orElse(0) * scale));
        return new Size(w, lines.size() * Math.max(1, Math.round((font.lineHeight + 1) * scale)));
    }
    public static Size naturalSize(LayoutEditorState.Element e, Font font, Component text, float scale, int maxWidth) {
        int iw = imageWidth(e), ih = imageHeight(e);
        if (position(e).equals("only")) return new Size(iw, ih);
        int gap = text.getString().isEmpty() ? 0 : gap(e);
        var label = textSize(font, text, Math.max(1, maxWidth - (vertical(e) ? 0 : iw + gap)), scale,
                number(e, "maxLines", 0, 0, 256));
        return vertical(e) ? new Size(Math.max(iw, label.width), ih + gap + label.height)
                : new Size(iw + gap + label.width, Math.max(ih, label.height));
    }
    public static Layout layout(LayoutEditorState.Element e, Font font, Component text, float textScale,
                                double canvasScale, int width, int height) {
        width = Math.max(0, width); height = Math.max(0, height);
        int iw = Math.min(width, Math.max(1, (int) Math.round(imageWidth(e) * canvasScale)));
        int ih = Math.min(height, Math.max(1, (int) Math.round(imageHeight(e) * canvasScale)));
        boolean only = position(e).equals("only"), vertical = vertical(e);
        boolean labelVisible = !only && !text.getString().isEmpty();
        int gap = labelVisible ? Math.min((int) Math.round(gap(e) * canvasScale),
                Math.max(0, vertical ? height - ih : width - iw)) : 0;
        int availableWidth = only ? 0 : vertical ? width : width - iw - gap;
        int availableHeight = only ? 0 : vertical ? height - ih - gap : height;
        Size label = labelVisible && availableHeight > 0 ? textSize(font, text, availableWidth, textScale,
                number(e, "maxLines", 0, 0, 256)) : new Size(0, 0);
        int tw = label.width, th = Math.min(availableHeight, label.height);
        int groupWidth = only ? iw : vertical ? Math.max(iw, tw) : iw + gap + tw;
        int groupHeight = only ? ih : vertical ? ih + gap + th : Math.max(ih, th);
        int x = (width - groupWidth) / 2, y = (height - groupHeight) / 2;
        if (only) return new Layout(new Box(x, y, iw, ih), new Box(x, y, 0, 0));
        if (vertical) {
            int imageY = position(e).equals("bottom") ? y + th + gap : y;
            int textY = position(e).equals("bottom") ? y : y + ih + gap;
            return new Layout(new Box(x + (groupWidth - iw) / 2, imageY, iw, ih),
                    new Box(x + (groupWidth - tw) / 2, textY, tw, th));
        }
        int imageX = position(e).equals("right") ? x + tw + gap : x;
        int textX = position(e).equals("right") ? x : x + iw + gap;
        return new Layout(new Box(imageX, y + (groupHeight - ih) / 2, iw, ih),
                new Box(textX, y + (groupHeight - th) / 2, tw, th));
    }
}
