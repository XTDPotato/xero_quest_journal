package xtdpotato.xero_quest_journal.core.model;

public record JourneyBackdrop(int startX, String source, String type, double scale, int offsetX, int offsetY,
                              String music, String mode) {
    public JourneyBackdrop(int startX, String source, String type, double scale, int offsetX, int offsetY, String music) {
        this(startX, source, type, scale, offsetX, offsetY, music, "cover");
    }
    public JourneyBackdrop(int startX, String source, String type, double scale, int offsetX, int offsetY) {
        this(startX, source, type, scale, offsetX, offsetY, "");
    }
    public JourneyBackdrop {
        if (startX < 0 || startX > 100000 || !Double.isFinite(scale) || scale < 0.1 || scale > 10)
            throw new IllegalArgumentException("Invalid journey background position or scale");
        if (Math.abs(offsetX) > 100000 || Math.abs(offsetY) > 100000)
            throw new IllegalArgumentException("Invalid journey background offset");
        source = source == null ? "" : source.strip();
        music = music == null ? "" : music.strip();
        mode = mode == null || mode.isBlank() ? "cover" : mode;
        type = type == null || type.isBlank() ? "image" : type;
        if (!type.equals("image") && !type.equals("video") || source.length() > 4096 || music.length() > 4096
                || !java.util.List.of("cover", "contain", "stretch", "adaptive").contains(mode))
            throw new IllegalArgumentException("Invalid journey background source or type");
    }
}
