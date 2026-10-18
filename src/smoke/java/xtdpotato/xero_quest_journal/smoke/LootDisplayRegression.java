package xtdpotato.xero_quest_journal.smoke;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.QuestLootStore;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxData;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.model.LootDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/** Loot metadata must survive freezing and transport without affecting delivery. */
final class LootDisplayRegression {
    private static final String TABLE = "loot_display_regression", CHOICE = TABLE + "_choice", MAIL = TABLE + "_mail";
    private static final String CUSTOM = "minecraft:emerald";
    private static CompletableFuture<?> work, language;
    private static QuestLootScreen editor;
    private static int stage, wait;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0) return false;
        wait = 8;
        switch (stage) {
            case 0 -> {
                MailArrivalToast.clear();
                game.getLanguageManager().setSelected("zh_cn");
                game.options.languageCode = "zh_cn";
                language = game.reloadResourcePacks();
                work = CompletableFuture.runAsync(() -> setup(player(game)), game.getSingleplayerServer());
            }
            case 1 -> {
                if (!work.isDone() || !language.isDone()) return false;
                work.join(); language.join();
                ClientEditMode.set(true);
                editor = new QuestLootScreen(null, TABLE, null);
                game.setScreen(editor);
            }
            case 2 -> {
                require(QuestIcons.valid(LootDefinition.DEFAULT_DISPLAY_ICON), "Default gift texture is unavailable");
                button(editor, "loot-display-icon").onPress();
                require(game.screen instanceof QuestScreen, "Display icon did not open the shared picker");
                var manual = game.screen.children().stream().filter(EditBox.class::isInstance)
                        .map(EditBox.class::cast).filter(box -> box.getMessage().getString().equals(
                                net.minecraft.network.chat.Component.translatable(
                                        "screen.xero_quest_journal.icon_picker.manual").getString()))
                        .findFirst().orElseThrow();
                manual.setValue(CUSTOM);
                require(field(editor, "displayIcon").equals(LootDefinition.DEFAULT_DISPLAY_ICON),
                        "Selection changed the loot before Apply");
                button(game.screen, "icon-apply").onPress();
                require(game.screen == editor && field(editor, "displayIcon").equals(CUSTOM),
                        "Display icon did not apply");
                editor.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                require(field(editor, "displayIcon").equals(LootDefinition.DEFAULT_DISPLAY_ICON), "Icon undo failed");
                editor.keyPressed(GLFW.GLFW_KEY_Y, 0, GLFW.GLFW_MOD_CONTROL);
                require(field(editor, "displayIcon").equals(CUSTOM), "Icon redo failed");
                button(editor, "loot-display-icon-reset").onPress();
                require(field(editor, "displayIcon").equals(LootDefinition.DEFAULT_DISPLAY_ICON), "Icon reset failed");
                editor.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                editor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .filter(box -> box.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable(
                                "screen.xero_quest_journal.loot.detail_text").getString()))
                        .findFirst().orElseThrow().setValue("补给奖励的自定义详情");
                button(editor, "loot-save").onPress();
                require(field(editor, "error") == null && QuestLootStore.find(TABLE).displayIcon().equals(CUSTOM),
                        "Editor save did not persist display icon");
                require(QuestLootStore.find(TABLE).entries().getFirst().detailText().equals("补给奖励的自定义详情"),
                        "Visual editor did not persist detail text");
                checkGeometry(editor);
                MailArrivalToast.clear();
                game.setScreen(LootTablePopupScreen.inspect(editor, new Reward("loot", TABLE, 1)));
            }
            case 3 -> {
                require(game.screen instanceof LootTablePopupScreen
                                && field(game.screen, "displayIcon").equals(CUSTOM)
                                && ((List<?>) field(game.screen, "entries")).size() == 8,
                        "Loot contents popup omitted its external icon or table entries");
                capture(game, "loot-display-contents-1920");
                game.screen.onClose();
                wait = 12;
            }
            case 4 -> {
                if (game.screen != editor) return false;
                MailArrivalToast.clear();
                capture(game, "loot-display-editor-1920");
                game.options.guiScale().set(3);
                game.resizeDisplay();
            }
            case 5 -> {
                require(editor.width == 640 && editor.height == 360, "Compact editor dimensions were not applied");
                checkGeometry(editor);
                capture(game, "loot-display-editor-labels-compact-1920");
                var motion = field(editor, "entryMotion");
                double before = number(motion, "position");
                editor.mouseScrolled(editor.width - 40, 240, 0, -1);
                require(number(motion, "target") > before && number(motion, "position") == before,
                        "Scroll did not set a smooth target");
            }
            case 6 -> {
                require(number(field(editor, "entryMotion"), "position") > 0, "Smooth scrolling did not advance");
                editor.mouseScrolled(editor.width - 40, 240, 0, -20);
                wait = 28;
            }
            case 7 -> {
                require(button(editor, "loot-entry-quality-7").getY() < editor.height - 62,
                        "Last loot entry did not become reachable after scrolling");
                capture(game, "loot-display-editor-compact-1920");
                game.options.guiScale().set(2);
                game.resizeDisplay();
                ClientMailbox.request();
                game.setScreen(new MailboxScreen(null, MAIL));
            }
            case 8 -> {
                if (ClientMailbox.messages().stream().noneMatch(mail -> mail.id().equals(MAIL))) return false;
                require(game.screen instanceof MailboxScreen && field(game.screen, "selected") != null,
                        "Mail fixture was not selected");
                capture(game, "loot-display-mail-1920");
                work = CompletableFuture.runAsync(() -> {
                    var author = player(game);
                    var view = MailboxRuntime.snapshot(author).stream().filter(mail -> mail.id().equals(MAIL))
                            .findFirst().orElseThrow();
                    int before = author.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND);
                    var receipt = MailboxRuntime.claimChoices(author, List.of(new QuestNetwork.MailboxClaimEntry(
                            MAIL, MailboxRuntime.fingerprint(view), java.util.Map.of(0, 0, 1, 0))), false);
                    require(receipt.result().equals("claimed")
                                    && author.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) == before + 2,
                            "Custom/default icons changed actual mail reward delivery");
                    require(MailboxRuntime.claim(author, MAIL).equals("no_claimable"),
                            "Loot display mail could be claimed twice");
                    QuestLootStore.delete(author, TABLE);
                    QuestLootStore.delete(author, CHOICE);
                    MailboxData.get(author.server).update(author.getUUID(), MAIL, ignored -> null);
                }, game.getSingleplayerServer());
            }
            case 9 -> {
                if (!work.isDone()) return false;
                work.join();
                return true;
            }
            default -> throw new AssertionError("Unknown loot display stage");
        }
        stage++;
        return false;
    }

    private static void setup(ServerPlayer player) {
        Gson gson = new Gson();
        var entries = IntStream.range(0, 8).mapToObj(i -> new LootDefinition.Entry("entry-" + i,
                "item", i % 2 == 0 ? "minecraft:diamond" : "minecraft:emerald", i + 1, 10,
                "always", true, "rare", "")).toList();
        var random = new LootDefinition(TABLE, "探索者补给箱", LootDefinition.Mode.RANDOM, 2, "", entries);
        var legacyJson = gson.toJsonTree(random).getAsJsonObject();
        legacyJson.remove("displayIcon");
        require(gson.fromJson(legacyJson, LootDefinition.class).displayIcon().equals(LootDefinition.DEFAULT_DISPLAY_ICON),
                "Legacy loot JSON did not default to gift");
        QuestLootStore.save(player, random);
        var choice = new LootDefinition(CHOICE, "自选补给", LootDefinition.Mode.CHOICE, 1,
                "选择一份奖励", entries.subList(0, 2), CUSTOM);
        QuestLootStore.save(player, choice);
        QuestLootStore.reload();
        require(QuestLootStore.find(CHOICE).equals(choice), "Display icon did not survive disk reload");
        var frozen = QuestRewards.freeze(player, List.of(new Reward("loot", CHOICE, 1))).getFirst();
        require(QuestRewards.choiceDisplayIcon(frozen.item()).equals(CUSTOM), "Freeze lost display icon");
        require(QuestRewards.choices(frozen).equals(entries.subList(0, 2).stream()
                .map(LootDefinition.Entry::reward).toList()), "Display icon changed actual reward choices");
        var catalog = QuestCatalog.decode(QuestCatalog.serverJson());
        require(catalog.lootDisplayIcons().get(CHOICE).equals(CUSTOM), "Catalog lost remote display icon");
        require(catalog.lootTables().get(CHOICE).equals(choice.display()),
                "Catalog lost safe loot contents for the detail popup");
        var privateCommand = new LootDefinition.Entry("secret", "command", "say private",
                1, 10, "always", true);
        var commandTable = new LootDefinition("loot_display_security", "Safe command table",
                LootDefinition.Mode.RANDOM, 1, "", List.of(privateCommand), CUSTOM);
        require(commandTable.display().entries().getFirst().item().isEmpty(),
                "Loot display DTO leaked command text");
        QuestLootStore.save(player, new LootDefinition(CHOICE, choice.name(), choice.mode(), 1, choice.choiceTitle(),
                choice.entries(), "minecraft:diamond"));
        require(QuestCatalog.decode(QuestCatalog.serverJson()).lootDisplayIcons().get(CHOICE).equals("minecraft:diamond"),
                "Catalog cache did not refresh loot metadata");
        require(QuestRewards.choiceDisplayIcon(frozen.item()).equals(CUSTOM), "Changing table changed frozen icon");
        var legacyChoice = gson.fromJson(frozen.item(), com.google.gson.JsonObject.class);
        legacyChoice.remove("displayIcon");
        require(QuestRewardsScreen.icon("loot_choice", legacyChoice.toString()).equals(LootDefinition.DEFAULT_DISPLAY_ICON),
                "Old choice rewards still show paper");
        require(QuestRewardsScreen.icon("loot_choice", "malformed").equals(LootDefinition.DEFAULT_DISPLAY_ICON),
                "Malformed metadata did not fall back to gift");
        var attachments = List.of(new MailboxRuntime.Attachment(frozen.type(), frozen.item(), 1, false),
                new MailboxRuntime.Attachment("loot_choice", legacyChoice.toString(), 1, false));
        MailboxData.get(player.server).update(player.getUUID(), MAIL, ignored -> null);
        require(MailboxRuntime.sendIfAbsent(player, MAIL, "Xero", "战利品外显图标", "自定义图标与默认礼盒展示测试。",
                0, attachments), "Could not deliver loot display fixture");
        var data = MailboxData.get(player.server);
        var loaded = MailboxData.load(data.save(new CompoundTag(), player.registryAccess()), player.registryAccess());
        require(loaded.messages(player.getUUID()).stream().filter(mail -> mail.id().equals(MAIL)).findFirst()
                .orElseThrow().attachments().equals(attachments), "Mailbox save/load lost frozen icon");
        var snapshot = new QuestNetwork.MailboxSnapshotPayload(MailboxRuntime.snapshot(player));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            QuestNetwork.MailboxSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
            require(QuestNetwork.MailboxSnapshotPayload.STREAM_CODEC.decode(buffer).equals(snapshot),
                    "Mailbox network codec lost frozen icon");
        } finally { buffer.release(); }
    }

    private static void checkGeometry(QuestLootScreen screen) throws Exception {
        @SuppressWarnings("unchecked") var entries = (Set<AbstractWidget>) field(screen, "entryWidgets");
        var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).filter(widget -> widget.visible).toList();
        for (int i = 0; i < widgets.size(); i++) {
            var a = widgets.get(i);
            require(a.getX() >= 0 && a.getRight() <= screen.width, "Control exceeds screen: " + a.getMessage());
            if (!entries.contains(a))
                require(a.getY() >= 0 && a.getBottom() <= screen.height, "Toolbar exceeds screen");
            if (a.getClass().getSimpleName().equals("ScrollRail")) continue;
            for (int j = i + 1; j < widgets.size(); j++) {
                var b = widgets.get(j);
                if (b.getClass().getSimpleName().equals("ScrollRail")
                        || entries.contains(a) != entries.contains(b)) continue;
                require(a.getX() >= b.getRight() || b.getX() >= a.getRight()
                                || a.getY() >= b.getBottom() || b.getY() >= a.getBottom(),
                        "Controls overlap: " + a.getMessage().getString() + " / " + b.getMessage().getString());
            }
        }
    }

    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow();
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static double number(Object target, String name) throws Exception {
        return ((Number) field(target, name)).doubleValue();
    }
    private static ServerPlayer player(Minecraft game) {
        return game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Path output = Path.of("..", "build", "verification");
        Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name + ".png"));
        }
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
