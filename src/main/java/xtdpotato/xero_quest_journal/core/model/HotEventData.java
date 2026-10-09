package xtdpotato.xero_quest_journal.core.model;

/** Optional overview presentation data attached to a task or group. */
public record HotEventData(boolean enabled, String image, String title,
                           int x, int y, boolean positioned, int imageWidth, int imageHeight,
                           boolean hideImage) {
    public HotEventData {
        image = image == null ? "" : image;
        title = title == null ? "" : title;
        if (image.length() > 4096 || title.length() > 128
                || Math.abs((long) x) > 1_000_000 || Math.abs((long) y) > 1_000_000
                || imageWidth < 0 || imageHeight < 0 || imageWidth > 4096 || imageHeight > 4096) {
            throw new IllegalArgumentException("Invalid hot-event metadata");
        }
    }

    public HotEventData(boolean enabled, String image, String title, int x, int y,
                        boolean positioned, int imageWidth, int imageHeight) {
        this(enabled, image, title, x, y, positioned, imageWidth, imageHeight, false);
    }

    public HotEventData(boolean enabled, String image, String title, int x, int y, boolean positioned) {
        this(enabled, image, title, x, y, positioned, 0, 0);
    }

    public static HotEventData empty() {
        return new HotEventData(false, "", "", 0, 0, false, 0, 0);
    }
}
