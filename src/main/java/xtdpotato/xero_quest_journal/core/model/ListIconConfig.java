package xtdpotato.xero_quest_journal.core.model;

/**
 * Appearance of an icon anchored to a journal list row.
 * Offsets are relative to the icon's normal slot position.
 */
public record ListIconConfig(
        String image,
        String trackedImage,
        boolean visible,
        boolean replaceWhenTracked,
        float alpha,
        int width,
        int height,
        int offsetX,
        int offsetY,
        String trackingStyle
) {
    public ListIconConfig {
        image = image == null ? "" : image;
        trackedImage = trackedImage == null ? "" : trackedImage;
        trackingStyle = QuestTrackingStyle.normalize(trackingStyle);
        if (!Float.isFinite(alpha) || alpha < 0.0F || alpha > 1.0F
                || width < 1 || width > 64 || height < 1 || height > 64
                || offsetX < -64 || offsetX > 64 || offsetY < -64 || offsetY > 64) {
            throw new IllegalArgumentException("Invalid list icon configuration");
        }
    }

    public ListIconConfig(String image, String trackedImage, boolean visible, boolean replaceWhenTracked,
                          float alpha, int width, int height, int offsetX, int offsetY) {
        this(image, trackedImage, visible, replaceWhenTracked, alpha, width, height, offsetX, offsetY, "auto");
    }

    public static ListIconConfig leading(String legacyImage) {
        return new ListIconConfig(legacyImage, "", true, false, 1.0F, 16, 16, 0, 0);
    }

    public static ListIconConfig trailing() {
        return new ListIconConfig("", "", true, false, 1.0F, 14, 14, 0, 0);
    }

    public String image(boolean tracked) {
        return tracked && replaceWhenTracked && !trackedImage.isBlank() ? trackedImage : image;
    }
}
