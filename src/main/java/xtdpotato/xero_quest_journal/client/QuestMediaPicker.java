package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import xtdpotato.xero_quest_journal.client.media.MediaDecoders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** Imports author-selected files into the portable quest assets directory. */
public final class QuestMediaPicker {
    private static final long MAX_BYTES = 1024L * 1024 * 1024;

    private QuestMediaPicker() {}

    public static String importFile(Path selected, boolean video) throws IOException {
        if (selected == null || !Files.isRegularFile(selected) || Files.isSymbolicLink(selected)
                || Files.size(selected) > MAX_BYTES) throw new IOException("Invalid media file");
        String name = selected.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!MediaDecoders.extensions(video).contains(extension))
            throw new IOException("Unsupported media extension; extended codecs require Xero Quest Media");
        Files.createDirectories(QuestIcons.localRoot().resolve("media"));
        String relative = "media/" + UUID.randomUUID() + "." + extension;
        Path destination = QuestIcons.localRoot().resolve(relative).toAbsolutePath().normalize();
        try {
            Files.copy(selected, destination);
        } catch (IOException failure) {
            Files.deleteIfExists(destination);
            throw failure;
        }
        return relative;
    }

    static boolean supported(Path path, boolean video) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && MediaDecoders.extensions(video)
                .contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static void choose(Screen parent, boolean video, Consumer<String> chosen) {
        Minecraft.getInstance().setScreen(new QuestMediaPickerScreen(parent, video, chosen));
    }
}
