package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Exercises the explicit delete command against isolated generated/manual fixtures. */
final class GeneratedDeletionRegression {
    private static final String SUFFIX = Long.toUnsignedString(System.currentTimeMillis(), 36);
    private static final String GROUP = "auto_cleanup_" + SUFFIX;
    private static final String EXTRA_GROUP = "auto_cleanup_extra_" + SUFFIX;
    private static final String EXTERNAL_GROUP = "cleanup_external_" + SUFFIX;
    private static final String GENERATED = "auto_item_tacz_cleanup_" + SUFFIX;
    private static final String EXTRA_GENERATED = "auto_item_gun_tacz_cleanup_extra_" + SUFFIX;
    private static final String MANUAL = "cleanup_manual_" + SUFFIX;
    private static final String CHILD = "cleanup_child_" + SUFFIX;
    private static CompletableFuture<Void> setup;
    private static CompletableFuture<Integer> dryRun;
    private static CompletableFuture<Integer> deletion;
    private static int stage;

    private GeneratedDeletionRegression() {}

    static boolean tick(Minecraft game) throws Exception {
        MinecraftServer server = game.getSingleplayerServer();
        switch (stage) {
            case 0 -> setup = CompletableFuture.runAsync(() -> createFixtures(server), server);
            case 1 -> {
                if (!setup.isDone()) return false;
                setup.join();
                dryRun = command(server, "xero_quest delete_generated all");
            }
            case 2 -> {
                if (!dryRun.isDone()) return false;
                require(dryRun.join() == 0, "Delete command without confirmation was accepted");
                require(QuestRepository.find(GENERATED) != null && QuestRepository.find(EXTRA_GENERATED) != null,
                        "Unconfirmed command changed generated tasks");
                deletion = command(server, "xero_quest delete_generated all confirm");
            }
            case 3 -> {
                if (!deletion.isDone()) return false;
                require(deletion.join() == 2, "Confirmed all command did not remove both generated fixtures");
                QuestRepository.reload();
                require(QuestRepository.errors().isEmpty(), "Deletion left invalid task references");
                require(QuestRepository.find(GENERATED) == null, "Generated fixture was not deleted");
                require(QuestRepository.find(EXTRA_GENERATED) == null, "Second generated group was not deleted");
                require(QuestRepository.groups().stream().noneMatch(group -> group.id().equals(EXTRA_GROUP)),
                        "Empty all-generated group was not removed");
                require(QuestRepository.find(MANUAL) != null, "Manual task in an auto group was deleted");
                var child = QuestRepository.find(CHILD);
                require(child != null && child.parents().isEmpty(),
                        "External prerequisite still points at deleted task");
                var chapter = QuestRepository.chapters().stream().filter(value -> value.id()
                        .equals(GROUP + "_1_advancements")).findFirst().orElseThrow();
                require(chapter.quests().equals(List.of(MANUAL)), "Chapter did not retain only its manual task");

                Path history = QuestRepository.root().getParent().resolve("history");
                try (var backups = Files.list(history)) {
                    Path latest = backups.filter(Files::isDirectory)
                            .filter(path -> path.getFileName().toString().startsWith("batch-"))
                            .max(Path::compareTo).orElseThrow();
                    Path journal = latest.resolve("before.json");
                    require(Files.isRegularFile(journal), "Delete did not create a rollback backup");
                    JsonObject saved = JsonParser.parseString(Files.readString(journal)).getAsJsonObject();
                    List<String> paths = saved.keySet().stream().map(value -> value.replace('\\', '/')).toList();
                    require(paths.stream().anyMatch(path -> path.endsWith(GENERATED + ".json"))
                                    && paths.stream().anyMatch(path -> path.endsWith(
                                    GROUP + "_1_advancements.chapter.json"))
                                    && paths.stream().anyMatch(path -> path.endsWith(
                                    EXTRA_GROUP + "/group_manifest.json")),
                            "Rollback backup omitted generated task, chapter, or removed group source");
                }
                Path output = Path.of("..", "build", "verification");
                Files.createDirectories(output);
                Files.writeString(output.resolve("generated-delete-result.txt"),
                        "PASS: confirm gate, delete-all across generated groups, manual task preservation,\n"
                                + "chapter reference cleanup, external prerequisite cleanup and history backup.\n");
                Files.writeString(output.resolve("result.txt"),
                        "PASS: automatic task deletion only removed the marked generated fixture and created a backup.\n");
                return true;
            }
            default -> throw new AssertionError("Unknown generated-delete stage " + stage);
        }
        stage++;
        return false;
    }

    private static CompletableFuture<Integer> command(MinecraftServer server, String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return server.getCommands().getDispatcher().execute(
                        text, server.createCommandSourceStack().withPermission(4));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                throw new IllegalStateException("Command parse failed: " + text, failure);
            }
        }, server);
    }

    private static void createFixtures(MinecraftServer server) {
        try {
            Path root = QuestRepository.root();
            Path generatedGroup = root.resolve(GROUP);
            Path extraGroup = root.resolve(EXTRA_GROUP);
            Path manualGroup = root.resolve(EXTERNAL_GROUP);
            Path generatedDirectory = generatedGroup.resolve("advancements");
            Path extraDirectory = extraGroup.resolve("items");
            Files.createDirectories(generatedDirectory);
            Files.createDirectories(extraDirectory);
            Files.createDirectories(manualGroup);
            Files.writeString(generatedGroup.resolve("group_manifest.json"),
                    "{\"id\":\"" + GROUP + "\",\"name\":\"Generated fixture\",\"type\":\"active\"}");
            Files.writeString(extraGroup.resolve("group_manifest.json"),
                    "{\"id\":\"" + EXTRA_GROUP + "\",\"name\":\"Second generated fixture\",\"type\":\"active\"}");
            Files.writeString(manualGroup.resolve("group_manifest.json"),
                    "{\"id\":\"" + EXTERNAL_GROUP + "\",\"name\":\"Manual fixture\",\"type\":\"active\"}");
            Files.writeString(generatedDirectory.resolve(GROUP + "_1_advancements.chapter.json"),
                    "{\"id\":\"" + GROUP + "_1_advancements\",\"name\":\"Fixture chapter\",\"group\":\""
                            + GROUP + "\",\"quests\":[\"" + GENERATED + "\",\"" + MANUAL + "\"]}");
            Files.writeString(generatedDirectory.resolve(GENERATED + ".json"), """
                    {"id":"%s","title":"Generated fixture task","type":"world","defaultUnlocked":true,
                     "triggers":[{"type":"confirm","item":"","count":1}],
                     "generatedBy":"xero_quest_journal","generatedNamespace":"tacz"}
                    """.formatted(GENERATED));
            Files.writeString(extraDirectory.resolve(EXTRA_GROUP + "_3_craft_basic.chapter.json"),
                    "{\"id\":\"" + EXTRA_GROUP + "_3_craft_basic\",\"name\":\"Fixture crafting chapter\","
                            + "\"group\":\"" + EXTRA_GROUP + "\",\"quests\":[\"" + EXTRA_GENERATED + "\"]}");
            Files.writeString(extraDirectory.resolve(EXTRA_GENERATED + ".json"), """
                    {"id":"%s","title":"Second generated fixture task","type":"world","defaultUnlocked":true,
                     "triggers":[{"type":"confirm","item":"","count":1}],
                     "generatedBy":"xero_quest_journal","generatedNamespace":"tacz"}
                    """.formatted(EXTRA_GENERATED));
            Files.writeString(generatedDirectory.resolve(MANUAL + ".json"), """
                    {"id":"%s","title":"Manual fixture task","type":"world","defaultUnlocked":true,
                     "triggers":[{"type":"confirm","item":"","count":1}]}
                    """.formatted(MANUAL));
            Files.writeString(manualGroup.resolve(CHILD + ".json"), """
                    {"id":"%s","title":"Manual dependent task","type":"world","defaultUnlocked":true,
                     "parents":["%s"],"triggers":[{"type":"confirm","item":"","count":1}]}
                    """.formatted(CHILD, GENERATED));
            QuestRepository.reload();
            if (!QuestRepository.errors().isEmpty())
                throw new AssertionError("Could not load deletion fixtures: " + QuestRepository.errors());
        } catch (Exception failure) {
            throw new IllegalStateException("Could not create generated-delete fixtures", failure);
        }
    }

    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
