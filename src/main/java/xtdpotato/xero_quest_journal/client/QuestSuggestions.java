package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuestSuggestions {
    private QuestSuggestions() {}

    public static List<QuestValuePickerScreen.Option> options(String context, String current) {
        var game = Minecraft.getInstance();
        var server = game.getSingleplayerServer();
        String key = context.toLowerCase(Locale.ROOT);
        List<QuestValuePickerScreen.Option> result = new ArrayList<>();
        if (key.contains("dimension")) {
            if (game.getConnection() != null) game.getConnection().levels().stream()
                    .map(level -> level.location().toString()).sorted().forEach(id -> add(result, id));
            if (server != null) server.levelKeys().forEach(level -> add(result, level.location().toString()));
        } else if (key.equals("kill") || key.equals("entity")) {
            BuiltInRegistries.ENTITY_TYPE.forEach(type -> result.add(new QuestValuePickerScreen.Option(
                    BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), type.getDescription().getString()
                    + " (" + BuiltInRegistries.ENTITY_TYPE.getKey(type) + ")")));
        } else if (key.startsWith("block_")) {
            BuiltInRegistries.BLOCK.forEach(block -> result.add(new QuestValuePickerScreen.Option(
                    BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName().getString()
                    + " (" + BuiltInRegistries.BLOCK.getKey(block) + ")")));
        } else if (key.equals("effect")) {
            BuiltInRegistries.MOB_EFFECT.forEach(effect -> result.add(new QuestValuePickerScreen.Option(
                    BuiltInRegistries.MOB_EFFECT.getKey(effect).toString(), effect.getDisplayName().getString()
                    + " (" + BuiltInRegistries.MOB_EFFECT.getKey(effect) + ")")));
        } else if (key.equals("advancement") && server != null) {
            server.getAdvancements().getAllAdvancements().forEach(holder -> result.add(
                    new QuestValuePickerScreen.Option(holder.id().toString(), () -> holder.value().display()
                            .map(display -> display.getTitle().getString()).orElse(holder.id().toString())
                            + " (" + holder.id() + ")")));
        } else if (key.equals("quest") || key.equals("unlock") || key.equals("complete")) {
            QuestRepository.all().forEach(quest -> result.add(
                    new QuestValuePickerScreen.Option(quest.id(),
                            QuestText.title(quest).getString() + " (" + quest.id() + ")")));
        } else if (key.equals("function") && server != null) {
            server.getFunctions().getFunctionNames().forEach(id -> add(result, id.toString()));
        } else if (key.equals("script")) {
            var root = xtdpotato.xero_quest_journal.core.QuestConfig.root().resolve("scripts");
            if (java.nio.file.Files.isDirectory(root)) {
                try (var files = java.nio.file.Files.walk(root, 8)) {
                    files.filter(path -> java.nio.file.Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                            && path.toString().endsWith(".js")).limit(2048)
                            .map(path -> root.relativize(path).toString().replace('\\', '/'))
                            .sorted().forEach(value -> add(result, value));
                } catch (java.io.IOException ignored) { }
            }
        } else if (key.equals("event") || key.equals("stage")) {
            if (key.equals("event")) {
                xtdpotato.xero_quest_journal.core.model.TriggerGraph.EVENTS.forEach(value -> add(result, value));
            }
            QuestRepository.all().forEach(quest -> collectTargets(result, quest.triggers(), key));
        } else if (key.contains("count") || key.contains("radius") || key.contains("size")
                || key.contains("width") || key.contains("height") || key.contains("scale")
                || key.matches("(.*[._])?(x|y|z)$")) {
            for (String value : List.of("0", "1", "2", "4", "8", "16", "32", "64", "100")) add(result, value);
            if (game.player != null && key.matches("(.*[._])?(x|y|z)$")) {
                String axis = key.substring(key.length() - 1);
                int value = axis.equals("x") ? game.player.blockPosition().getX()
                        : axis.equals("y") ? game.player.blockPosition().getY() : game.player.blockPosition().getZ();
                result.addFirst(new QuestValuePickerScreen.Option(Integer.toString(value),
                        Component.translatable("screen.xero_quest_journal.value_picker.player_position", value).getString()));
            }
        }
        if (result.stream().noneMatch(option -> option.value().isEmpty())) {
            result.addFirst(new QuestValuePickerScreen.Option("", Component.translatable(
                    "screen.xero_quest_journal.value_picker.clear").getString()));
        }
        if (result.size() == 1) {
            String clipboard = game.keyboardHandler.getClipboard();
            if (!clipboard.isBlank() && clipboard.length() <= 8192 && !clipboard.contains("\n"))
                result.add(new QuestValuePickerScreen.Option(clipboard, Component.translatable(
                        "screen.xero_quest_journal.value_picker.clipboard", clipboard).getString()));
        }
        if (!current.isBlank()) add(result, current);
        return List.copyOf(result);
    }

    private static void add(List<QuestValuePickerScreen.Option> result, String value) {
        if (result.stream().noneMatch(option -> option.value().equals(value)))
            result.add(new QuestValuePickerScreen.Option(value, value));
    }

    private static void collectTargets(List<QuestValuePickerScreen.Option> result,
                                       List<xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger> conditions,
                                       String type) {
        for (var condition : conditions) {
            if (condition.type().equals(type)) add(result, condition.item());
            collectTargets(result, condition.children(), type);
        }
    }
}
