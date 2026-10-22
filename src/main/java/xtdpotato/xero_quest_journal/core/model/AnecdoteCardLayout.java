package xtdpotato.xero_quest_journal.core.model;

/** Per-entry layout in card-relative percentages; old definitions inherit the classic arrangement. */
public record AnecdoteCardLayout(Part background, Part image, Part text, String backgroundImage,
                                 String backgroundColor, String textColor, double fontSize) {
    public record Part(double x, double y, double width, double height, double scale) {
        public Part {
            x = bounded(x, -100, 200, 0);
            y = bounded(y, -100, 200, 0);
            width = bounded(width, 1, 300, 100);
            height = bounded(height, 1, 300, 100);
            scale = bounded(scale, .1, 4, 1);
        }
    }

    public AnecdoteCardLayout {
        background = background == null ? new Part(0, 0, 100, 100, 1) : background;
        image = image == null ? new Part(7, 4, 86, 68.8, 1) : image;
        text = text == null ? new Part(4, 81, 92, 16, 1) : text;
        backgroundImage = backgroundImage == null ? "" : backgroundImage.strip();
        if (backgroundImage.length() > 2048) throw new IllegalArgumentException("Anecdote background path exceeds limit");
        backgroundColor = color(backgroundColor, "#F8F0DF");
        textColor = color(textColor, "#4F5664");
        fontSize = bounded(fontSize, 3, 48, 9);
    }

    public static AnecdoteCardLayout defaults() { return new AnecdoteCardLayout(null, null, null, "", "", "", 9); }
    public Part part(String slot) {
        return switch (slot) { case "background" -> background; case "text" -> text; default -> image; };
    }
    public AnecdoteCardLayout withPart(String slot, Part value) {
        return new AnecdoteCardLayout(slot.equals("background") ? value : background,
                slot.equals("image") ? value : image, slot.equals("text") ? value : text,
                backgroundImage, backgroundColor, textColor, fontSize);
    }
    private static double bounded(double value, double min, double max, double fallback) {
        return !Double.isFinite(value) || value == 0 && min > 0 ? fallback : Math.max(min, Math.min(max, value));
    }
    private static String color(String value, String fallback) {
        return value != null && value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?") ? value : fallback;
    }
}
