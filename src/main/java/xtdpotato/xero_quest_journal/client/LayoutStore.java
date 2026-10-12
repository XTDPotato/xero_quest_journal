package xtdpotato.xero_quest_journal.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LayoutStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LayoutStore() {
    }

    public static LayoutEditorState load(String screenId) {
        Path file = file(screenId);
        try {
            if (Files.exists(file)) {
                LayoutEditorState state = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                        LayoutEditorState.class);
                return state == null ? new LayoutEditorState() : state;
            }
        } catch (Exception exception) {
            XeroQuestJournal.LOGGER.warn("Unable to load layout state {}", file, exception);
        }
        return new LayoutEditorState();
    }

    public static boolean save(String screenId, LayoutEditorState state) {
        Path file = file(screenId);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, GSON.toJson(state), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            XeroQuestJournal.LOGGER.warn("Unable to save layout state {}", file, exception);
            return false;
        }
    }

    public static String snapshot(LayoutEditorState state) {
        return GSON.toJson(state);
    }

    public static LayoutEditorState restore(String state) {
        return GSON.fromJson(state, LayoutEditorState.class);
    }

    private static Path file(String screenId) {
        return PlatformServices.configDir().resolve("xero_quest")
                .resolve("ui").resolve("layouts").resolve(screenId + ".json");
    }
}
