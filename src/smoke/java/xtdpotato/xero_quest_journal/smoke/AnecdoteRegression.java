package xtdpotato.xero_quest_journal.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.AnecdoteToast;
import xtdpotato.xero_quest_journal.client.ClientPreferences;
import xtdpotato.xero_quest_journal.client.AnecdoteClientState;
import xtdpotato.xero_quest_journal.client.AnecdoteScreen;
import xtdpotato.xero_quest_journal.client.AnecdoteRewardsScreen;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.QuestRewardPopupScreen;
import xtdpotato.xero_quest_journal.client.StyledButton;
import xtdpotato.xero_quest_journal.client.ClientEditMode;
import xtdpotato.xero_quest_journal.core.io.AnecdoteStore;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;
import xtdpotato.xero_quest_journal.core.runtime.AnecdoteRuntime;
import xtdpotato.xero_quest_journal.core.network.QuestClientInbox;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.List;

final class AnecdoteRegression {
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static final long DURATION = 3_200_000_000L;
    private static final AnecdoteDefinition ENTRY_A = new AnecdoteDefinition("smoke_anecdote_a",
            "风雪来信", "风雪", "", "山路上的一封信",
            List.of(new AnecdoteDefinition.Record("arrival", "雪夜相遇", "在雪夜的山路上，遇到了迷路的旅人。",
                            "minecraft:diamond"),
                    new AnecdoteDefinition.Record("after", "后来", "后来，她寄来了一封感谢信。", "minecraft:emerald"),
                    new AnecdoteDefinition.Record("farewell", "再会", "旅人终于抵达了目的地。", "minecraft:book")),
            true, false, 0);
    private static final AnecdoteDefinition ENTRY_B = new AnecdoteDefinition("smoke_anecdote_b",
            "尚未发现", "文字超出五个字符", "minecraft:paper", "被遗忘的故事", List.of(), false, false, 1);
    private static final AnecdoteDefinition ENTRY_C = new AnecdoteDefinition("smoke_anecdote_c",
            "图节点联动", "记录测试", "minecraft:book", "自动解锁测试",
            List.of(new AnecdoteDefinition.Record("first", "初次记录", "由节点完成时自动解锁逸闻。")),
            false, false, 2);
    private static int stage, played;
    private static long lastStarted;
    private static long rewardsOpenedAt;
    private static long rewardLayoutEditorOpenedAt;
    private static long rewardLayoutSaveStartedAt;
    private static xtdpotato.xero_quest_journal.core.model.AnecdoteRewardLayout savedRewardLayoutBeforeEdit;
    private static AnecdoteScreen chronicleScreen;

    static boolean tick(Minecraft game) throws Exception {
        switch (stage) {
            case 0 -> {
                var defaults = new QuestNetwork.AnecdotePayload();
                require(defaults.scale() == .7 && defaults.position().equals("top"),
                        "Default size or anchor changed");
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920);
                game.getWindow().setHeight(1080);
                game.options.guiScale().set(2);
                game.resizeDisplay();
                game.setScreen(new Probe());
                AnecdoteToast.clear();
                ClientPreferences.get().reducedMotion = false;
                var server = game.getSingleplayerServer();
                var source = game.player.createCommandSourceStack().withPermission(2);
                server.getPlayerList().op(game.player.getGameProfile());
                var root = server.getCommands().getDispatcher().getRoot().getChild("xero_anecdote");
                require(root != null, "Missing command");
                require(!root.canUse(source.withPermission(0)), "Unprivileged command access");
                var dispatcher = server.getCommands().getDispatcher();
                AnecdoteStore.save(ENTRY_A);
                AnecdoteStore.save(ENTRY_B);
                AnecdoteStore.save(ENTRY_C);
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(game.player.getUUID());
                    server.getPlayerList().op(player.getGameProfile());
                    require(AnecdoteRuntime.discover(player, ENTRY_A.id()), "Graph discover action failed");
                    require(AnecdoteRuntime.completeRecord(player, ENTRY_A.id(), "arrival"),
                            "Record completion action failed");
                    require(AnecdoteRuntime.completeRecord(player, ENTRY_A.id(), "after")
                                    && AnecdoteRuntime.completeRecord(player, ENTRY_A.id(), "farewell"),
                            "The other chronicle records did not complete");
                    require(AnecdoteRuntime.completeRecord(player, ENTRY_C.id(), "first"),
                            "Completing a graph record did not unlock its anecdote");
                    var state = QuestProgressData.get(player.getServer());
                    require(state.anecdoteDiscovered(player.getUUID(), ENTRY_C.id())
                                    && state.anecdoteCompleted(player.getUUID(), ENTRY_C.id(), "first"),
                            "Anecdote node action did not persist discovered/completed state");
                });
                require(dispatcher.parse("xero_quest open anecdotes", source).getExceptions().isEmpty(),
                        "Chronicle open command did not parse");
                String[] commands = {"xero_anecdote @s", "xero_anecdote @s 24 -12 0.8 top,left",
                        "xero_anecdote @a 0 0 0.7 bottom,right",
                        "xero_anecdote " + game.player.getGameProfile().getName() + " 0 0 0.7 center"};
                for (String command : commands) {
                    var parsed = dispatcher.parse(command, source);
                    require(parsed.getExceptions().isEmpty(),
                            "Command/selector did not parse: " + command + " " + parsed.getExceptions());
                }
                var left = new QuestNetwork.AnecdotePayload(24, -12, .8, "start,top");
                require(left.position().equals("left,top"), "Start alias was not normalized");
                try {
                    new QuestNetwork.AnecdotePayload(0, 0, .7, "top,bottom");
                    throw new AssertionError("Conflicting vertical anchors were accepted");
                } catch (IllegalArgumentException expected) {}
                for (var payload : java.util.List.of(new QuestNetwork.AnecdotePayload(), left,
                        new QuestNetwork.AnecdotePayload(0, 0, .7, "bottom,right"),
                        new QuestNetwork.AnecdotePayload(0, 0, .7, "center")))
                    QuestClientInbox.accept(payload);
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                        io.netty.buffer.Unpooled.buffer(), game.player.registryAccess());
                try {
                    var packet = new QuestNetwork.AnecdotePayload(24, -12, .8, "start,top");
                    QuestNetwork.AnecdotePayload.STREAM_CODEC.encode(buffer, packet);
                    require(QuestNetwork.AnecdotePayload.STREAM_CODEC.decode(buffer).equals(packet),
                            "Packet codec");
                } finally { buffer.release(); }
                var progress = new QuestProgressData();
                var owner = game.player.getUUID();
                progress.discoverAnecdote(owner, ENTRY_A.id());
                require(progress.completeAnecdoteRecord(owner, ENTRY_A.id(), "arrival"),
                        "First anecdote record completion was not recorded");
                require(!progress.completeAnecdoteRecord(owner, ENTRY_A.id(), "arrival"),
                        "Completed anecdote record was duplicated");
                var saved = progress.save(new net.minecraft.nbt.CompoundTag(), game.player.registryAccess());
                var restored = QuestProgressData.load(saved, game.player.registryAccess());
                require(restored.anecdoteDiscovered(owner, ENTRY_A.id())
                                && restored.anecdoteCompleted(owner, ENTRY_A.id(), "arrival")
                                && restored.newAnecdoteCompletions(owner).contains(ENTRY_A.id() + "/arrival"),
                        "Anecdote discovery/completion state did not survive save/load");
                stage = Boolean.getBoolean("xero.anecdoteUiTest") ? 5 : 1;
            }
            case 1 -> {
                if (!active()) return false;
                long current = started();
                if (current != lastStarted) {
                    if (played > 0) require(current - lastStarted >= DURATION, "Overlapping banners");
                    lastStarted = current;
                    played++;
                }
                long age = System.nanoTime() - current;
                if (played == 1 && age > 600_000_000L && age < 2_000_000_000L) {
                    capture(game, "anecdote-world-1920");
                    stage++;
                }
            }
            case 2 -> {
                long current = started();
                if (current != lastStarted) {
                    require(current - lastStarted >= DURATION, "Queue restarted a running banner");
                    lastStarted = current;
                    played++;
                }
                if (System.nanoTime() - current > 500_000_000L) {
                    try (NativeImage image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                        Files.createDirectories(OUTPUT);
                        image.writeToFile(OUTPUT.resolve("anecdote-screen-1920.png"));
                        int outside = image.getPixelRGBA(4, 4);
                        require((outside & 255) == 0x36 && (outside >> 8 & 255) == 0x53
                                && (outside >> 16 & 255) == 0x65, "Banner darkened outside world");
                        for (int y = 0; y < 8; y++)
                            require(image.getPixelRGBA(image.getWidth() / 2, y) == outside,
                                    "Top anchor is less than 4 pixels from the edge");
                        int center = image.getPixelRGBA(image.getWidth() / 2,
                                Math.round(image.getHeight() * .16F));
                        require(center != outside, "Banner did not render");
                    }
                    stage++;
                }
            }
            case 3 -> {
                long current = started();
                if (current != lastStarted) {
                    require(current - lastStarted >= DURATION, "Next notice began before full fade-out");
                    lastStarted = current;
                    played++;
                }
                if (active()) return false;
                require(played == 4, "Each configured command must play exactly once");
                lastStarted = System.nanoTime();
                stage++;
            }
            case 4 -> {
                require(!active(), "Idle notification replayed");
                if (System.nanoTime() - lastStarted < 400_000_000L) return false;
                for (int i = 0; i < 24; i++) QuestClientInbox.accept(new QuestNetwork.AnecdotePayload());
                for (int i = 0; i < 24; i++) {
                    AnecdoteToast.tick();
                    require(active(), "Dropped queued command " + i);
                    require(started() >= lastStarted, "Invalid queue start");
                    field("current").set(null, new QuestNetwork.AnecdotePayload());
                    field("started").setLong(null, System.nanoTime() - DURATION);
                }
                AnecdoteToast.tick();
                require(!active(), "Duplicate event after burst");
                QuestClientInbox.accept(new QuestNetwork.AnecdotePayload());
                AnecdoteToast.tick();
                ClientPreferences.get().reducedMotion = true;
                field("started").setLong(null, System.nanoTime() - 900_000_000L);
                GLFW.glfwSetWindowSize(game.getWindow().getWindow(), 640, 360);
                game.getWindow().setWidth(640);
                game.getWindow().setHeight(360);
                game.resizeDisplay();
                stage++;
            }
            case 5 -> {
                capture(game, "anecdote-compact-reduced");
                QuestClientInbox.accept(new QuestNetwork.AnecdotePayload());
                QuestClientInbox.clear();
                AnecdoteToast.clear();
                AnecdoteToast.tick();
                require(!active() && QuestClientInbox.takeAnecdote() == null, "Connection cleanup");
                ClientEditMode.set(true);
                ClientPreferences.get().reducedMotion = false;
                game.getWindow().setWindowed(1920, 1080);
                game.getWindow().setWidth(1920);
                game.getWindow().setHeight(1080);
                game.options.guiScale().set(2);
                game.resizeDisplay();
                AnecdoteClientState.request();
                game.setScreen(QuestScreen.overview(null));
                stage++;
            }
            case 6 -> {
                if (!(game.screen instanceof QuestScreen overview)) return false;
                var card = overview.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast)
                        .filter(button -> button.styleId().equals("overview-anecdotes"))
                        .findFirst().orElseThrow();
                    require(field(card, "icon").equals("category_anecdotes"),
                        "Overview did not use the dedicated pixel map-pin icon for Anecdote Chronicle");
                require(card.getMessage().equals(Component.translatable(
                                "screen.xero_quest_journal.overview.category.anecdotes")),
                        "Anecdote Chronicle overview action uses the wrong localized title");
                require(overview.children().stream().filter(StyledButton.class::isInstance)
                                .map(StyledButton.class::cast)
                                .noneMatch(button -> button.styleId().equals("overview-world")),
                        "Duplicate World Quests action occupies the Travel Anecdotes slot");
                card.onPress();
                stage++;
                return false;
            }
            case 7 -> {
                if (game.screen instanceof QuestScreen) return false;
                if (!(game.screen instanceof AnecdoteScreen chronicle)) return false;
                {
                    var opening = field(chronicle, "pageOpen");
                    if (!(boolean) call(opening, "settled")) return false;
                    if (AnecdoteClientState.snapshot().entries().size() < 2
                            || AnecdoteClientState.snapshot().completed().size() < 3) return false;
                    if (((java.util.List<?>) field(chronicle, "cardBounds")).size() < 2) return false;
                    chronicleScreen = chronicle;
                    capture(game, "anecdote-chronicle-1920");
                    var cards = (java.util.List<?>) field(game.screen, "cardBounds");
                    var entries = (java.util.List<?>) field(game.screen, "visibleEntries");
                    require(cards.size() >= 2 && entries.size() >= 2, "Chronicle cards missing");
                    var selectedEntry = AnecdoteStore.find(ENTRY_A.id());
                    require(selectedEntry != null && selectedEntry.icon().isBlank(),
                            "Default scroll icon fixture was replaced with a custom icon");
                    require(((java.util.List<?>) field(game.screen, "recordRows")).size() == 3,
                            "Multiple records did not remain visible under one anecdote");
                    var add = field(game.screen, "addBounds");
                    int addX = (int) call(add, "x") + (int) call(add, "width") / 2;
                    int addY = (int) call(add, "y") + (int) call(add, "height") / 2;
                    require(game.screen.mouseClicked(addX, addY, 0), "Anecdote create button press was not handled");
                    game.screen.mouseReleased(addX, addY, 0);
                    require(game.screen.getClass().getSimpleName().equals("AnecdoteEntryEditorScreen")
                                    && (boolean) field(game.screen, "creating"),
                            "Anecdote toolbar + did not open the create editor");
                    button(game.screen, "anecdote-records").onPress();
                    require(game.screen.getClass().getSimpleName().equals("AnecdoteRecordsEditorScreen"),
                            "Entry editor did not open its records list");
                    require(!game.screen.getTitle().getString().contains("screen.xero_quest_journal.")
                                    && game.screen.getTitle().getString().equals(Component.translatable(
                                    "screen.xero_quest_journal.anecdote.records_title",
                                    field(game.screen, "entry") instanceof AnecdoteDefinition entry ? entry.title() : "").getString()),
                            "Anecdote record list title is missing its Chinese translation");
                    var recordsClose = button(game.screen, "anecdote-records-close");
                    require("close".equals(field(recordsClose, "icon")),
                            "Anecdote record list does not use the shared close icon");
                    recordsClose.onPress();
                    require(game.screen.getClass().getSimpleName().equals("AnecdoteEntryEditorScreen"),
                            "Record-list close button did not return to entry editor");
                    game.screen.onClose();
                    require(game.screen == chronicleScreen && !chronicleScreen.isClosing(),
                            "Closing an unrendered entry editor did not restore its chronicle");
                    requireNoField(chronicleScreen, "rewardAddBounds");
                    require(chronicleScreen.children().stream().filter(StyledButton.class::isInstance)
                                    .map(StyledButton.class::cast)
                                    .noneMatch(button -> button.styleId().equals("anecdote-reward-add")),
                            "Chronicle toolbar retained the external reward-add plus button");
                    var entryEditorType = Class.forName(
                            "xtdpotato.xero_quest_journal.client.AnecdoteEntryEditorScreen");
                    var entryEditorConstructor = entryEditorType.getDeclaredConstructor(
                            Screen.class, AnecdoteDefinition.class, boolean.class);
                    entryEditorConstructor.setAccessible(true);
                    Screen entryEditor = (Screen) entryEditorConstructor.newInstance(chronicleScreen, ENTRY_A, false);
                    game.setScreen(entryEditor);
                    button(entryEditor, "anecdote-rewards").onPress();
                    require(game.screen.getClass().getSimpleName().equals("QuestRewardsScreen"),
                            "Entry editor did not open its reward editor");
                    game.screen.onClose();
                    require(game.screen == entryEditor, "Reward editor did not return to anecdote properties");
                    var detailScreen = new QuestRewardPopupScreen(entryEditor,
                            ENTRY_A.rewards().getFirst());
                    game.setScreen(detailScreen);
                    require(detailScreen.getClass().getSimpleName().equals("QuestRewardPopupScreen"),
                            "Reward item detail did not open from the anecdote reward item");
                    detailScreen.onClose();
                    require(game.screen == entryEditor, "Reward item detail did not return to its owner editor");
                    entryEditor.onClose();
                    require(game.screen == chronicleScreen, "Entry reward editor did not return to chronicle");
                    var recordAdd = field(chronicleScreen, "recordAddBounds");
                    int recordX = (int) call(recordAdd, "x") + (int) call(recordAdd, "width") / 2;
                    int recordY = (int) call(recordAdd, "y") + (int) call(recordAdd, "height") / 2;
                    require(chronicleScreen.mouseClicked(recordX, recordY, 0), "Record add toolbar click was not handled");
                    require(game.screen.getClass().getSimpleName().equals("AnecdoteRecordEditorScreen"),
                            "Anecdote record + did not open its editor");
                    var recordClose = button(game.screen, "anecdote-record-editor-close");
                    require("close".equals(field(recordClose, "icon")),
                            "Anecdote record editor does not use the shared close icon");
                    recordClose.onPress();
                    require(game.screen == chronicleScreen,
                            "Record editor close button did not return to its parent");
                    require(chronicleScreen.mouseClicked(recordX, recordY, 0),
                            "Record add toolbar did not reopen after closing the editor");
                    require(game.screen.getClass().getSimpleName().equals("AnecdoteRecordEditorScreen"),
                            "Record add button did not reopen the record editor");
                    game.screen.onClose();
                    require(chronicleScreen.mouseClicked(recordX, recordY, 0), "Empty-page record add click was not handled");
                    require(game.screen.getClass().getSimpleName().equals("AnecdoteRecordEditorScreen"),
                            "Record add button did not work while the page was otherwise empty");
                    game.screen.onClose();
                    var lockedIndex = entries.indexOf(ENTRY_B);
                    require(lockedIndex >= 0, "Undiscovered entry was hidden despite its visibility setting");
                    int x = (int) call(cards.get(lockedIndex), "x") + (int) call(cards.get(lockedIndex), "width") / 2;
                    int y = (int) call(cards.get(lockedIndex), "y") + (int) call(cards.get(lockedIndex), "height") / 2;
                    String previous = (String) field(chronicleScreen, "selectedId");
                    chronicleScreen.mouseClicked(x, y, 0);
                    require(previous.equals(field(chronicleScreen, "selectedId")),
                            "Chronicle selected on press instead of release");
                    chronicleScreen.mouseReleased(x, y, 0);
                    require(ENTRY_B.id().equals(field(chronicleScreen, "selectedId")),
                            "Chronicle did not select on release inside the same card");
                    chronicleScreen.onClose();
                    require(chronicleScreen.isClosing() && (boolean) field(chronicleScreen, "closingBook"),
                            "Chronicle did not use the book-close transition");
                    chronicleScreen.reopen();
                    var rewards = new AnecdoteRewardsScreen(chronicleScreen);
                    game.setScreen(rewards);
                    require(rewards.animationPosition() < 1 && (boolean) call(rewards, "isModalScreen"),
                            "Anecdote rewards did not open as an adaptive floating book popup");
                    require((int) call(rewards, "popupScrimColor") == 0x33808080,
                            "Anecdote reward popup must use a 20% gray backdrop");
                    require((int) field(rewards, "WATERMARK_WIDTH") <= 180
                                    && (int) field(rewards, "WATERMARK_HEIGHT") <= 115
                                    && (int) field(rewards, "WATERMARK_X") == 325
                                    && (int) field(rewards, "WATERMARK_Y") == 368,
                            "Anecdote notebook watermark must fit the reserved bottom sticker slots");
                    call(rewards, "layout");
                    int popupWidth = (int) field(rewards, "widthPage");
                    int popupHeight = (int) field(rewards, "heightPage");
                    require(popupWidth < game.screen.width && popupHeight < game.screen.height,
                            "Anecdote reward popup overflowed its viewport");
                    var rewardLayout = (xtdpotato.xero_quest_journal.core.model.AnecdoteRewardLayout)
                            call(rewards, "activeLayout");
                    int ticketWidth = (int) Math.round(538 * rewardLayout.ticket().scale() * 400.0 / 768.0);
                    int giftX = (int) rewardLayout.claim().x();
                    int ticketX = (int) rewardLayout.ticket().x();
                    require(ticketWidth >= 275 && ticketWidth < 300
                                    && Math.abs(ticketX + ticketWidth / 2 - 770) <= 1
                                    && giftX >= ticketX && giftX + 49 <= ticketX + ticketWidth,
                            "Anecdote claim button escaped its cropped reward ticket");
                    var detailMethod = AnecdoteRewardsScreen.class.getDeclaredMethod("drawRecordPanel",
                            GuiGraphics.class, int.class, int.class);
                    require(detailMethod != null, "Reward panel item hit testing was not wired");
                    rewardsOpenedAt = System.nanoTime();
                    stage++;
                }
            }
            case 8 -> {
                if (System.nanoTime() - rewardsOpenedAt < 650_000_000L) return false;
                require(game.screen instanceof AnecdoteRewardsScreen, "Anecdote reward sticker page did not open");
                capture(game, "anecdote-rewards-1920");
                require(!AnecdoteClientState.snapshot().completed().isEmpty(),
                        "Reward stickers removed the completed record");
                var rewards = (AnecdoteRewardsScreen) game.screen;
                var hits = (List<?>) field(rewards, "rewardHits");
                require(!hits.isEmpty(), "Anecdote reward ticket did not create an item hit target");
                var item = hits.getFirst();
                double scale = (double) call(rewards, "scale");
                int x = (int) Math.round((int) field(rewards, "left")
                        + ((int) call(item, "x") + (int) call(item, "width") / 2.0) * scale);
                int y = (int) Math.round((int) field(rewards, "top")
                        + ((int) call(item, "y") + (int) call(item, "height") / 2.0) * scale);
                require(rewards.mouseClicked(x, y, 0), "Anecdote reward item press was not handled");
                rewards.mouseReleased(x, y, 0);
                require(game.screen instanceof QuestRewardPopupScreen
                                && !((ItemStack) field(game.screen, "inspectedStack")).isEmpty()
                                && ((ItemStack) field(game.screen, "inspectedStack")).getCount() == 50,
                        "Clicking an anecdote reward did not open its item details");
                game.screen.onClose();
                require(game.screen == rewards, "Reward item details did not return to the sticker book");
                double pageScale = (double) call(rewards, "scale");
                int pageLeft = (int) field(rewards, "left"), pageTop = (int) field(rewards, "top");
                int editX = (int) Math.round(pageLeft + 914 * pageScale);
                int editY = (int) Math.round(pageTop + 87 * pageScale);
                require(rewards.mouseClicked(editX, editY, 0), "Reward layout edit button press was not handled");
                rewards.mouseReleased(editX, editY, 0);
                require((boolean) field(rewards, "layoutEditing"),
                        "Reward layout edit mode did not open from the visible edit control");
                rewardLayoutEditorOpenedAt = System.nanoTime();
                stage++;
                return false;
            }
            case 9 -> {
                if (System.nanoTime() - rewardLayoutEditorOpenedAt < 150_000_000L) return false;
                capture(game, "anecdote-reward-layout-editor-1920");
                var rewards = (AnecdoteRewardsScreen) game.screen;
                double pageScale = (double) call(rewards, "scale");
                int pageLeft = (int) field(rewards, "left"), pageTop = (int) field(rewards, "top");
                var layoutOwner = (AnecdoteDefinition) call(rewards, "rewardEntry");
                require(layoutOwner != null, "Reward layout editor has no active anecdote entry");
                var originalLayout = layoutOwner.rewardLayout();
                var persistedLayoutOwner = AnecdoteStore.find(layoutOwner.id());
                require(persistedLayoutOwner != null, "Active anecdote entry is not persisted");
                savedRewardLayoutBeforeEdit = persistedLayoutOwner.rewardLayout();
                var draft = (xtdpotato.xero_quest_journal.core.model.AnecdoteRewardLayout)
                        field(rewards, "rewardLayoutDraft");
                require(draft.equals(originalLayout), "Reward layout editor did not load the saved geometry");
                var xInput = (net.minecraft.client.gui.components.EditBox) field(rewards, "layoutXInput");
                invoke(rewards, "applyNumericInput", new Class<?>[]{String.class, String.class},
                        "reward_layout.x", "701.25");
                require(((xtdpotato.xero_quest_journal.core.model.AnecdoteRewardLayout)
                                field(rewards, "rewardLayoutDraft")).control("ticket").x() == 701.25,
                        "Precise reward control X input did not update the live preview");
                invoke(rewards, "applyNumericInput", new Class<?>[]{String.class, String.class},
                        "reward_layout.x", Double.toString(originalLayout.ticket().x()));
                call(rewards, "commitNumericEdit");
                require((double) invoke(AnecdoteRewardsScreen.class, "snapCoordinate",
                                new Class<?>[]{double.class, boolean.class}, 13.0, true) == 16.0
                                && (double) invoke(AnecdoteRewardsScreen.class, "snapCoordinate",
                                new Class<?>[]{double.class, boolean.class}, 13.0, false) == 13.0,
                        "Reward control snap or Shift snap bypass is incorrect");
                int selectClaimX = (int) Math.round(pageLeft + 780 * pageScale);
                int selectClaimY = (int) Math.round(pageTop + 87 * pageScale);
                rewards.mouseClicked(selectClaimX, selectClaimY, 0);
                rewards.mouseReleased(selectClaimX, selectClaimY, 0);
                double claimCenterX = originalLayout.claim().x() + 24;
                double claimCenterY = originalLayout.claim().y() + 24;
                int claimX = (int) Math.round(pageLeft + claimCenterX * pageScale);
                int claimY = (int) Math.round(pageTop + claimCenterY * pageScale);
                rewards.mouseClicked(claimX, claimY, 0);
                rewards.mouseDragged(claimX + (int) Math.round(9 * pageScale), claimY, 0,
                        9 * pageScale, 0);
                rewards.mouseReleased(claimX + (int) Math.round(9 * pageScale), claimY, 0);
                draft = (xtdpotato.xero_quest_journal.core.model.AnecdoteRewardLayout)
                        field(rewards, "rewardLayoutDraft");
                require(draft.claim().x() > originalLayout.claim().x(),
                        "Dragging the claim button did not update its live layout");
                int undoX = (int) Math.round(pageLeft + 616 * pageScale);
                int undoY = (int) Math.round(pageTop + 114 * pageScale);
                rewards.mouseClicked(undoX, undoY, 0);
                require(((xtdpotato.xero_quest_journal.core.model.AnecdoteRewardLayout)
                                field(rewards, "rewardLayoutDraft")).claim().x() == originalLayout.claim().x(),
                        "Reward layout undo did not restore the claim position");
                int redoX = (int) Math.round(pageLeft + 640 * pageScale);
                rewards.mouseClicked(redoX, undoY, 0);
                draft = (xtdpotato.xero_quest_journal.core.model.AnecdoteRewardLayout)
                        field(rewards, "rewardLayoutDraft");
                require(draft.claim().x() > originalLayout.claim().x(),
                        "Reward layout redo did not restore the moved claim button");
                int saveX = (int) Math.round(pageLeft + 849 * pageScale);
                int saveY = (int) Math.round(pageTop + 87 * pageScale);
                rewards.mouseClicked(saveX, saveY, 0);
                rewards.mouseReleased(saveX, saveY, 0);
                rewardLayoutSaveStartedAt = System.nanoTime();
                stage++;
                return false;
            }
            case 10 -> {
                var activeEntry = (AnecdoteDefinition) call(game.screen, "rewardEntry");
                require(activeEntry != null, "Reward layout save lost its active anecdote entry");
                var savedEntry = AnecdoteStore.find(activeEntry.id());
                if (savedEntry == null || savedEntry.rewardLayout().claim().x()
                        == savedRewardLayoutBeforeEdit.claim().x()) {
                    if (AnecdoteClientState.lastResult().equals("invalid")
                            || AnecdoteClientState.lastResult().equals("permission"))
                        throw new AssertionError("Reward layout save was rejected: " + AnecdoteClientState.lastResult());
                    if (System.nanoTime() - rewardLayoutSaveStartedAt > 15_000_000_000L)
                        throw new AssertionError("Reward layout save timed out, result="
                                + AnecdoteClientState.lastResult() + ", editing=" + field(game.screen, "layoutEditing"));
                    return false;
                }
                require(!(boolean) field(game.screen, "layoutEditing"),
                        "Saved reward geometry did not leave edit mode");
                game.setScreen(null);
                AnecdoteStore.delete(ENTRY_A.id());
                AnecdoteStore.delete(ENTRY_B.id());
                AnecdoteStore.delete(ENTRY_C.id());
                return true;
            }
            default -> throw new AssertionError("Unexpected stage");
        }
        return false;
    }

    private static Field field(String name) throws Exception {
        var result = AnecdoteToast.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals(id)).findFirst().orElseThrow();
    }

    private static Field reflected(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var value = type.getDeclaredField(name);
                value.setAccessible(true);
                return value;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object object, String name) throws Exception {
        return reflected(object, name).get(object);
    }
    private static void requireNoField(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { type.getDeclaredField(name); }
            catch (NoSuchFieldException expected) { continue; }
            throw new AssertionError("Unexpected field remains: " + name);
        }
    }

    private static Object call(Object object, String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }
    private static Object invoke(Class<?> type, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        var method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        var method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static boolean active() throws Exception { return field("current").get(null) != null; }
    private static long started() throws Exception { return field("started").getLong(null); }

    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message + " (anecdote stage " + stage + ")");
    }

    private static final class Probe extends Screen {
        Probe() { super(Component.literal("Notification backdrop")); }
        @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
            graphics.fill(0, 0, width, height, 0xFF365365);
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
