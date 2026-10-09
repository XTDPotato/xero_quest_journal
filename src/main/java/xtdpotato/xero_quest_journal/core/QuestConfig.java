package xtdpotato.xero_quest_journal.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class QuestConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FMLPaths.CONFIGDIR.get().resolve("xero_quest");
    private static final Path CONFIG = ROOT.resolve("config.json");
    private static volatile Defaults current = new Defaults();

    private QuestConfig() {
    }

    public static Path root() {
        return ROOT;
    }

    public static void ensureDefaults() {
        try {
            Files.createDirectories(ROOT.resolve("groups"));
            Files.createDirectories(ROOT.resolve("ui"));
            Files.createDirectories(ROOT.resolve("assets"));
            Files.createDirectories(ROOT.resolve("structures"));
            Files.createDirectories(ROOT.resolve("scripts"));
            Files.createDirectories(ROOT.resolve("drafts"));
            Files.createDirectories(ROOT.resolve("history"));
            Files.createDirectories(ROOT.resolve("runtime"));
            Files.createDirectories(ROOT.resolve("cache"));
            QuestTemplateStore.ensureDefaults();
            QuestTypeStore.ensureDefaults();
            ensureStarterDefinitions();
            if (!Files.exists(CONFIG)) {
                var defaults = new Defaults();
                Files.writeString(CONFIG, GSON.toJson(defaults), StandardCharsets.UTF_8);
            }
            reload();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize " + ROOT, exception);
        }
    }

    private static void ensureStarterDefinitions() throws IOException {
        Path marker = ROOT.resolve("runtime/definitions_initialized");
        if (Files.exists(marker)) return;
        Path groups = ROOT.resolve("groups");
        try (var entries = Files.walk(groups)) {
            if (entries.anyMatch(path -> Files.isRegularFile(path)
                    && !path.getFileName().toString().endsWith(".tmp"))) {
                AtomicFiles.write(marker, "existing\n", false);
                return;
            }
        }
        Path group = groups.resolve("01_main");
        Files.createDirectories(group);
        String groupUid = UUID.randomUUID().toString();
        String chapterUid = UUID.randomUUID().toString();
        String taskUid = UUID.randomUUID().toString();
        AtomicFiles.write(group.resolve("group_manifest.json"), """
                {
                  "id": "main",
                  "uid": "%s",
                  "name": "Main",
                  "description": "",
                  "type": "active"
                }
                """.formatted(groupUid), false);
        AtomicFiles.write(group.resolve("intro.chapter.json"), """
                {
                  "id": "intro",
                  "uid": "%s",
                  "name": "Introduction",
                  "description": "",
                  "group": "main",
                  "quests": ["example_intro"]
                }
                """.formatted(chapterUid), false);
        AtomicFiles.write(group.resolve("quest_example_intro.html"), """
                <quest id="example_intro" uid="%s" name="Welcome" type="normal"
                       title-key="quest.xero_quest_journal.welcome"
                       description-key="quest.xero_quest_journal.welcome_description">
                    <content><p>Welcome to Xero Quest Journal.</p></content>
                </quest>
                """.formatted(taskUid), false);
        AtomicFiles.write(marker, "starter\n", false);
    }

    public static boolean reload() {
        try {
            Defaults loaded = GSON.fromJson(Files.readString(CONFIG), Defaults.class);
            if (loaded == null) {
                throw new IllegalArgumentException("Empty configuration");
            }
            loaded.maxTrackedQuests = Math.max(1, Math.min(3, loaded.maxTrackedQuests));
            loaded.uiAnimationMillis = Math.max(0, Math.min(2000, loaded.uiAnimationMillis));
            loaded.defaultItemCount = Math.max(1, Math.min(1000000, loaded.defaultItemCount));
            loaded.defaultTaskWidth = Math.max(40, Math.min(400, loaded.defaultTaskWidth));
            loaded.defaultTaskHeight = Math.max(20, Math.min(160, loaded.defaultTaskHeight));
            loaded.dailyCommissionCount = Math.max(0, Math.min(16, loaded.dailyCommissionCount));
            loaded.dailyResetHour = Math.max(0, Math.min(23, loaded.dailyResetHour));
            loaded.dailyBonusRewards = loaded.dailyBonusRewards == null ? java.util.List.of()
                    : java.util.List.copyOf(loaded.dailyBonusRewards.stream()
                    .filter(java.util.Objects::nonNull).limit(32).toList());
            loaded.uiTheme = new xtdpotato.xero_quest_journal.core.model.QuestUiTheme(
                    loaded.uiTheme == null ? java.util.Map.of() : loaded.uiTheme.colors(),
                    loaded.uiTheme == null ? "" : loaded.uiTheme.overviewImage(),
                    loaded.uiTheme == null ? xtdpotato.xero_quest_journal.core.model.JourneyAppearance.empty()
                            : loaded.uiTheme.journeyAppearance());
            current = loaded;
            return true;
        } catch (Exception exception) {
            xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.error("Unable to reload quest defaults", exception);
            return false;
        }
    }

    public static Defaults defaults() {
        return current;
    }

    public static boolean save() {
        try {
            AtomicFiles.write(CONFIG, GSON.toJson(current), true);
            return reload();
        } catch (Exception exception) {
            xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.error("Unable to save quest defaults", exception);
            return false;
        }
    }

    public static final class Defaults {
        int schemaVersion = 1;
        String defaultView = "journal";
        public int maxTrackedQuests = 1;
        public boolean defaultManualRewards = true;
        String defaultProgressMode = "individual";
        String defaultLayout = "linear";
        public int uiAnimationMillis = 220;
        public int defaultItemCount = 16;
        public int defaultTaskWidth = 180;
        public int defaultTaskHeight = 22;
        public boolean taskPagination = false;
        public xtdpotato.xero_quest_journal.core.model.QuestUiTheme uiTheme =
                xtdpotato.xero_quest_journal.core.model.QuestUiTheme.empty();
        /** Commissions rolled per player and day from tasks marked {@code daily}; 0 disables the system. */
        public int dailyCommissionCount = 4;
        /** Local server hour (0-23) at which a new commission day starts. */
        public int dailyResetHour = 4;
        /** Granted once per day after all of that day's commissions are complete. */
        public java.util.List<xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward> dailyBonusRewards =
                java.util.List.of(
                        new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward("item", "minecraft:emerald", 8),
                        new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward("experience", "", 120));
    }
}
