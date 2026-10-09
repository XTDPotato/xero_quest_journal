package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Isolated client and integrated-server coverage for task reward authoring. */
final class RewardsRegression {
    private static final String GROUP = "rewards_" + System.currentTimeMillis();
    private static final String ID = GROUP + "_task";
    private static final List<Reward> VALUES = List.of(
            new Reward("item", "minecraft:diamond", 2),
            new Reward("item", "minecraft:emerald", 3));
    private static int stage;
    private static int wait;
    private static CompletableFuture<Void> pending;
    private static CompletableFuture<Void> languageReload;
    private static QuestScreen journal;
    private static String control;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        switch (stage) {
            case 0 -> {
                if (game.screen != null) return false;
                game.getLanguageManager().setSelected("zh_cn");
                game.options.languageCode = "zh_cn";
                languageReload = game.reloadResourcePacks();
                pending = CompletableFuture.runAsync(() -> {
                    try {
                        serverChecks(game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID()));
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    }
                }, game.getSingleplayerServer());
            }
            case 1 -> {
                if (!pending.isDone() || !languageReload.isDone()) return false;
                pending.join();
                languageReload.join();
                game.getToasts().clear();
                if (ClientQuestDefinitions.find(ID) == null) return false;
                journal = QuestScreen.task(null, ID);
                set(journal, "editMode", true);
                set(journal, "groupId", GROUP);
                game.setScreen(journal);
            }
            case 2 -> {
                int rightX = (int) field(journal, "detailsLeft") + 20;
                int rightY = (int) invoke(journal, "detailsTop") + 20;
                context(30, 100, null);
                require(keys().containsAll(Set.of("add_task", "add_group", "add_chapter")), "Missing left task actions");
                require(noLayoutEntries(), "Left blank menu leaked content actions");
                context(30, 100, "quest:" + ID);
                require(noLayoutEntries(), "Task row menu leaked content actions");
                context(30, 100, "group:" + GROUP);
                require(noLayoutEntries(), "Group menu leaked content actions");
                context(30, journal.height - 25, "system:add-task");
                require(noLayoutEntries(), "Left add control leaked content actions");
                context(journal.width - 2, journal.height - 2, null);
                require(keys().isEmpty(), "Outside-panel menu offered content actions");
                context(rightX, rightY, null);
                require(keys().containsAll(Set.of("add_rewards", "add_text", "add_image", "add_linear", "add_relative")),
                        "Right canvas missing content actions");
                require(!keys().contains("add_task") && !keys().contains("add_group"), "Right canvas leaked list actions");
                var layout = (LayoutEditorState) field(journal, "layout");
                layout.elements().put("test-overflow", new LayoutEditorState.Element("test-overflow", ID, "text", "", 30, 20));
                layout.setPosition("test-overflow", 4, 85);
                var hit = QuestScreen.class.getDeclaredMethod("hit", double.class, double.class);
                hit.setAccessible(true);
                require(!"test-overflow".equals(hit.invoke(journal, 5.0, 90.0)), "Overflow element captured left panel");
                layout.removeTree("test-overflow");
            }
            case 3 -> {
                capture(game, "rewards-context");
                click(game, menu().stream().filter(b -> b.styleId().equals("context-add_rewards")).findFirst().orElseThrow());
                control = (String) field(field(journal, "form"), "id");
                click(game, find(game, "create"));
                require(field(journal, "form") == null, "Reward layout creation failed");
                var layout = (LayoutEditorState) field(journal, "layout");
                require(layout.elements().get(control).kind().equals("quest_rewards"), "Wrong reward control type");
                context((int) field(journal, "detailsLeft") + 20, 120, control);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-rewards.edit")).findFirst().orElseThrow());
                require(game.screen instanceof QuestRewardsScreen, "Reward editor not opened");
                require(fields(game).size() == 4, "Editor did not load both rewards");
                fields(game).get(1).setValue("7");
                click(game, find(game, "rewards-add"));
                click(game, find(game, "rewards-remove-1"));
                require(((List<?>) field(game.screen, "rewards")).size() == 2, "Delete left a reward draft behind");
                fields(game).get(2).setValue("minecraft:gold_ingot");
                fields(game).get(3).setValue("4");
            }
            case 4 -> {
                capture(game, "rewards-editor");
                click(game, find(game, "rewards-save"));
            }
            case 5 -> {
                if (game.screen instanceof QuestRewardsScreen) {
                    require(field(game.screen, "error") == null, "Reward GUI save failed: " + field(game.screen, "error"));
                    return false;
                }
                require(game.screen == journal, "Editor did not return to task");
                require(QuestRepository.find(ID).rewards().equals(List.of(
                        new Reward("item", "minecraft:diamond", 7),
                        new Reward("item", "minecraft:gold_ingot", 4))), "GUI rewards not saved");
                game.setScreen(new QuestRewardsScreen(journal, ID));
                fields(game).getFirst().setValue("bad:item");
                click(game, find(game, "rewards-save"));
            }
            case 6 -> {
                require(game.screen instanceof QuestRewardsScreen, "Invalid item accepted");
                if (field(game.screen, "pending") != null) return false;
                require(field(game.screen, "error") != null, "Invalid item not reported");
                click(game, find(game, "rewards-cancel"));
                require(QuestRepository.find(ID).rewards().getFirst().item().equals("minecraft:diamond"),
                        "Failed draft changed source");
                game.setScreen(new QuestRewardsScreen(journal, ID));
                for (int i = 0; i < 20; i++) click(game, find(game, "rewards-add"));
                int w = (int) invoke(game.screen, "panelWidth");
                int left = (int) invoke(game.screen, "left");
                game.screen.mouseClicked(left + w + 4, 74, 0);
                game.screen.mouseDragged(left + w + 4, 74, 0, 0, -100);
                game.screen.mouseReleased(left + w + 4, 74, 0);
                require((int) field(game.screen, "scroll") == 0, "Scrollbar did not reach first row");
                fields(game).get(1).setValue("15");
                game.screen.mouseScrolled(left + 30, 80, 0, -1);
                game.screen.mouseScrolled(left + 30, 80, 0, 1);
                require(fields(game).get(1).getValue().equals("15"), "Scrolling discarded unsaved values");
                click(game, find(game, "rewards-cancel"));
                require(QuestRepository.find(ID).rewards().size() == 2, "Cancel wrote reward drafts");
                context((int) field(journal, "detailsLeft") + 20, 120, control);
                click(game, menu().stream().filter(b -> b.styleId().equals("context-delete")).findFirst().orElseThrow());
                var layout = (LayoutEditorState) field(journal, "layout");
                require(!layout.elements().containsKey(control) && !layout.positions().containsKey(control),
                        "Deleted reward control left hit target or position");
            }
            case 7 -> {
                capture(game, "rewards-journal");
                game.options.guiScale().set(1);
                game.resizeDisplay();
                game.setScreen(new QuestRewardsScreen(journal, ID));
            }
            case 8 -> {
                capture(game, "rewards-editor-wide");
                game.screen.onClose();
                game.options.guiScale().set(2);
                game.resizeDisplay();
                var layout = (LayoutEditorState) field(journal, "layout");
                control = "test-rewards-preview";
                layout.elements().put(control, new LayoutEditorState.Element(control, ID, "quest_rewards", "",
                        120, -2, Map.of("layout_width", "120", "layout_height", "-2")));
                layout.setPosition(control, 10, 18);
                game.setScreen(journal);
            }
            case 9 -> {
                capture(game, "rewards-preview");
                return true;
            }
            default -> throw new AssertionError("Unknown rewards stage");
        }
        stage++;
        wait = 15;
        return false;
    }

    private static void serverChecks(ServerPlayer author) throws Exception {
        require(QuestAuthoring.create(author, new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP,
                GROUP, "Rewards", "", "", "", "normal", "", 1)).successful(), "Group creation");
        require(QuestAuthoring.create(author, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,
                ID, "Reward test", "Keep this body", GROUP, "", "guide", "", 1)).successful(), "Task creation");
        require(QuestAuthoring.editConditions(author, ID, hash(ID),
                List.of(new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger("confirm", "", 1)))
                .successful(), "Manual completion condition");
        String source = QuestRepository.source(ID);
        String script = "<script>const keep = 'unchanged';</script>";
        source = source.replace("</quest>", script + "</quest>");
        require(QuestAuthoring.editSource(author, ID, hash(ID), source).successful(), "Script setup");
        require(!QuestAuthoring.editRewards(null, ID, hash(ID), VALUES, "manual").successful(), "Missing author accepted");
        require(!QuestAuthoring.editRewards(author, ID, "stale", VALUES, "manual").successful(), "Stale edit accepted");
        require(!QuestAuthoring.editRewards(author, ID, hash(ID), VALUES, null).successful(), "Null mode accepted");
        require(!QuestAuthoring.editRewards(author, ID, hash(ID), Collections.nCopies(129, VALUES.getFirst()), "manual").successful(),
                "Oversized rewards accepted");
        for (String item : List.of("missing:item", "bad item", "minecraft:air")) {
            require(!QuestAuthoring.editRewards(author, ID, hash(ID),
                    List.of(new Reward("item", item, 1)), "manual").successful(), "Invalid reward accepted: " + item);
        }
        var before = QuestRepository.find(ID);
        require(QuestAuthoring.editRewards(author, ID, hash(ID), VALUES, "manual").successful(), "Rewards save failed");
        var after = QuestRepository.find(ID);
        require(after.rewards().equals(VALUES) && after.rewardMode().equals("manual"), "Rewards round trip");
        require(before.triggers().equals(after.triggers()) && before.description().equals(after.description())
                && QuestRepository.source(ID).contains(script), "Reward edit damaged unrelated content");
        var request = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, ID, "Reward test", "Keep this body",
                GROUP, "", "guide", "", 1, "", "", "minecraft:diamond", 2, "manual");
        require(QuestAuthoring.edit(author, request, hash(ID), false, true).successful(), "Task property edit failed");
        require(QuestRepository.find(ID).rewards().equals(VALUES), "Task properties truncated rewards");
        var changedType = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, ID, "Reward test", "Keep this body",
                GROUP, "", "main", "", 1, "", "", "minecraft:diamond", 2, "manual", "",
                null, null, List.of(), VALUES, null);
        require(QuestAuthoring.edit(author, changedType, hash(ID), false, true, false, true).successful(),
                "Task type and reward draft edit failed");
        require(QuestRepository.find(ID).type().equals("main")
                && QuestRepository.find(ID).rewards().equals(VALUES), "Task type or reward draft lost");

        String selfClosing = QuestDocuments.rewards("<quest id=\"self\"/>", VALUES, "automatic");
        require(QuestRepository.parseMarkup(selfClosing, true).rewards().equals(VALUES), "Self-closing markup failed");
        Path jsonFile = QuestRepository.fileOf(ID).getParent().resolve(GROUP + "_json.json");
        String jsonId = GROUP + "_json";
        Files.writeString(jsonFile, "{\"id\":\"" + jsonId + "\",\"type\":\"guide\",\"description\":\"Keep JSON\",\"custom\":42}");
        QuestRepository.reload();
        require(QuestRepository.errors().isEmpty(), "JSON fixture invalid");
        require(QuestAuthoring.editConditions(author, jsonId, hash(jsonId),
                List.of(new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger("confirm", "", 1)))
                .successful(), "JSON manual completion condition");
        require(QuestAuthoring.editRewards(author, jsonId, hash(jsonId), VALUES, "automatic").successful(), "JSON edit failed");
        require(QuestRepository.find(jsonId).rewards().equals(VALUES)
                && QuestRepository.find(jsonId).rewardMode().equals("automatic")
                && QuestRepository.source(jsonId).contains("\"custom\": 42"), "JSON fields lost");
        var jsonRequest = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, jsonId, jsonId, "Keep JSON",
                "", "", "guide", "", 1, "", "", "", 1, "manual", "",
                null, null, List.of(), VALUES, null);
        require(QuestAuthoring.edit(author, jsonRequest, hash(jsonId), false, false, false, true).successful(),
                "JSON reward draft edit failed");
        require(QuestRepository.find(jsonId).rewardMode().equals("manual"),
                "JSON reward mode did not follow reward draft");

        author.getInventory().clearContent();
        require(QuestRuntime.activate(author, ID).equals("completed"), "Completion failed");
        require(QuestAuthoring.editRewards(author, ID, hash(ID),
                List.of(new Reward("item", "minecraft:gold_ingot", 10)), "manual").successful(), "Post-completion edit failed");
        require(QuestRuntime.snapshot(author).get(ID).rewards().equals(VALUES), "Completion snapshot changed");
        for (int i = 0; i < author.getInventory().items.size(); i++)
            author.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        require(QuestRuntime.claim(author, ID).equals("inventory_full"), "Full inventory accepted");
        require(QuestRuntime.snapshot(author).get(ID).status().equals("complete"), "Full bag consumed eligibility");
        author.getInventory().clearContent();
        require(QuestRuntime.claim(author, ID).equals("claimed"), "Claim failed");
        require(author.getInventory().countItem(Items.DIAMOND) == 2
                && author.getInventory().countItem(Items.EMERALD) == 3, "Wrong frozen rewards delivered");
        require(QuestRuntime.claim(author, ID).equals("already_claimed"), "Duplicate claim accepted");
        require(QuestRuntime.activate(author, jsonId).equals("completed")
                && QuestRuntime.snapshot(author).get(jsonId).status().equals("complete"), "Manual reward mode failed");
        require(QuestRuntime.claim(author, jsonId).equals("claimed")
                && QuestRuntime.snapshot(author).get(jsonId).status().equals("claimed"), "JSON reward claim failed");
        require(QuestAuthoring.editRewards(author, ID, hash(ID), VALUES, "manual").successful(), "Restore GUI fixture");
    }

    private static String hash(String id) throws Exception { return QuestRepository.fingerprint(QuestRepository.source(id)); }
    private static void context(int x, int y, String target) throws Exception {
        var method = QuestScreen.class.getDeclaredMethod("contextMenu", int.class, int.class, String.class);
        method.setAccessible(true);
        method.invoke(journal, x, y, target);
    }
    @SuppressWarnings("unchecked")
    private static List<StyledButton> menu() throws Exception { return (List<StyledButton>) field(journal, "menu"); }
    private static Set<String> keys() throws Exception {
        Set<String> keys = new HashSet<>();
        for (var button : menu()) keys.add(button.styleId().replace("context-", ""));
        return keys;
    }
    private static boolean noLayoutEntries() throws Exception {
        return Collections.disjoint(keys(), Set.of("add_rewards", "add_text", "add_image", "add_linear", "add_relative"));
    }
    private static List<EditBox> fields(Minecraft game) {
        return game.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
    }
    private static StyledButton find(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static void click(Minecraft game, StyledButton button) {
        require(button.active, "Button disabled: " + button.styleId());
        var screen = game.screen;
        require(screen.mouseClicked(button.getX() + button.getWidth() / 2., button.getY() + 10, 0), "Click failed");
        screen.mouseReleased(button.getX() + button.getWidth() / 2., button.getY() + 10, 0);
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static Object invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Path output = Path.of("..", "build", "verification");
        Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name + ".png"));
        }
    }
    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
