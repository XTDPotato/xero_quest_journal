package xtdpotato.xero_quest_journal.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.*;
import xtdpotato.xero_quest_journal.core.runtime.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Verifies real input, server results and rendered overlay depth after UI corrections. */
final class TaskUiCorrectionsRegression {
    private static final String ID = "ui_correction_" + System.currentTimeMillis();
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static int stage, wait;
    private static long nextFrame;
    private static CompletableFuture<?> pending;
    private static QuestScreen journal;
    private static QuestUnlockGraphScreen graph;
    private static Map<String, Set<String>> filters;
    private static Set<String> scopes;
    private static List<QuestPresentation.Control> initialControls;
    private static int oldPageSize;
    private static boolean oldPagination;
    private static boolean oldReducedMotion;

    static boolean tick(Minecraft game) throws Exception {
        if (--wait > 0 || ClientSmokeTest.renderedFrames < nextFrame
                || game.screen instanceof AnimatedQuestScreen a && a.inputBlocked()) return false;
        if (pending != null) { if (!pending.isDone()) return false; pending.join(); pending = null; }
        switch (stage) {
            case 0 -> {
                oldPageSize = QuestConfig.defaults().tasksPerPage; oldPagination = QuestConfig.defaults().taskPagination;
                oldReducedMotion = ClientPreferences.get().reducedMotion; ClientPreferences.get().reducedMotion = true;
                filters = ClientPreferences.get().questSearchFilters; scopes = ClientPreferences.get().questSearchScopes;
                ClientPreferences.get().questSearchFilters = Map.of(); ClientPreferences.get().questSearchScopes = Set.of("title", "description");
                game.getLanguageManager().setSelected("zh_cn"); game.options.languageCode = "zh_cn";
                pending = game.reloadResourcePacks();
            }
            case 1 -> {
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    for (String id : List.of(ID, ID + "_next")) {
                        var result = QuestAuthoring.create(player, new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, id,
                                "无模板任务 " + id.substring(id.length() - 4), "目标背景、分隔装饰和内容在编辑前后保持一致。", "", "", "world",
                                "minecraft:stone", 5, "minecraft:diamond", "", "minecraft:diamond", 1, "manual", ""));
                        require(result.successful(), "Native task fixture failed: " + result.error());
                    }
                    QuestConfig.defaults().taskPagination = true; QuestConfig.defaults().tasksPerPage = 10;
                    require(QuestConfig.save(), "Page config save failed");
                }, game.getSingleplayerServer());
            }
            case 2 -> {
                if (ClientQuestDefinitions.find(ID) == null) return false;
                require(ClientQuestProgress.tasksPerPage() == 10, "Page size not used in local world");
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), game.player.registryAccess());
                try {
                    var packet = new QuestNetwork.ProgressSnapshotPayload(0, 1, true, Map.of(), List.of(), "", 1000);
                    QuestNetwork.ProgressSnapshotPayload.STREAM_CODEC.encode(buffer, packet);
                    require(QuestNetwork.ProgressSnapshotPayload.STREAM_CODEC.decode(buffer).tasksPerPage() == 1000, "Page size codec lost 1000");
                } finally { buffer.release(); }
                for (int invalid : new int[]{9, 1001}) {
                    try { new QuestNetwork.PageSizePayload(1, invalid); throw new AssertionError("Invalid page size accepted"); }
                    catch (IllegalArgumentException expected) { }
                }
                journal = QuestScreen.task(null, ID); game.setScreen(journal);
                initialControls = controls(journal);
                require(initialControls.stream().anyMatch(c -> c.kind().equals("quest_objective") && c.properties().containsKey("background")), "Native objective bar missing");
                require(initialControls.stream().anyMatch(c -> c.properties().containsKey("journal_ornament")), "Header ornament missing");
                button(journal, "edit").onPress(); wait = 4;
            }
            case 3 -> {
                require((boolean) field(journal, "editMode"), "Edit did not open");
                require(controls(journal).equals(initialControls), "Entering edit changed native controls");
                capture(game, "native-task-edit-consistency-zh-1920");
                button(journal, "edit").onPress(); wait = 4;
            }
            case 4 -> {
                require(controls(journal).equals(initialControls), "Leaving edit changed native controls");
                call(journal, "saveLayout"); wait = 6;
            }
            case 5 -> {
                var ids = ((List<QuestDefinition>) field(journal, "quests")).stream().map(QuestDefinition::id).toList();
                int index = ids.indexOf(ID); require(index >= 0, "Native selection absent");
                journal.setFocused(null);
                if (index + 1 < ids.size()) {
                    journal.keyPressed(GLFW.GLFW_KEY_S, 0, 0);
                    require(field(journal, "selectedId").equals(ids.get(index + 1)), "S did not select next task");
                    journal.keyPressed(GLFW.GLFW_KEY_W, 0, 0);
                    require(field(journal, "selectedId").equals(ID), "W did not select previous task");
                }
                game.setScreen(new QuestSearchSettingsScreen(journal)); wait = 4;
            }
            case 6 -> {
                Screen screen = game.screen;
                require(button(screen, "search-filter-cancel") != null, "Search cancel button missing");
                button(screen, "search-filter-collapse-all").onPress();
                require(((List<?>) field(screen, "rows")).stream().allMatch(row -> enumValue(row, "kind").equals("HEADER")), "Collapse all retained filter options");
                invoke(screen, "toggleSection", new Class<?>[]{String.class}, "type");
                require(((List<?>) field(screen, "rows")).stream().anyMatch(row -> !enumValue(row, "kind").equals("HEADER")), "Section cannot expand independently");
                invoke(screen, "toggle", new Class<?>[]{String.class, String.class}, "status", "locked");
                ((EditBox) field(screen, "taskSearch")).setValue("cancelled draft");
                button(screen, "search-filter-cancel").onPress(); wait = 4;
            }
            case 7 -> {
                require(game.screen == journal && ((String) field(journal, "searchQuery")).isEmpty(), "Cancel did not restore query");
                require(ClientPreferences.get().questSearchFilters.isEmpty(), "Cancel saved draft filters");
                pending = CompletableFuture.runAsync(() -> {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
                    QuestProgressData.get(player.getServer()).complete(player.getUUID(), QuestRepository.find(ID));
                    QuestClientInbox.accept(new QuestNetwork.ProgressSnapshotPayload(QuestRuntime.snapshot(player), List.of()));
                }, game.getSingleplayerServer()); wait = 4;
            }
            case 8 -> {
                if (ClientQuestProgress.view(ID) == null || !ClientQuestProgress.view(ID).status().equals("complete")) return false;
                journal = QuestScreen.task(null, ID); game.setScreen(journal); wait = 4;
            }
            case 9 -> {
                var row = (JournalListButton) button(journal, "quest-" + ID);
                require(row.claimable(), "Pending reward row lacks claim button");
                require((int) field(row, "claimIconSize") == 16, "Claim button not compact");
                capture(game, "compact-claim-button-zh-1920");
                invoke(journal, "runAction", new Class<?>[]{String.class, boolean.class}, ID, true); wait = 6;
            }
            case 10 -> {
                if (ClientQuestProgress.view(ID) == null || !ClientQuestProgress.view(ID).status().equals("claimed")) return false;
                if (game.screen != journal) { game.screen.onClose(); wait = 4; return false; }
                call(journal, "init");
                require(field(journal, "category").equals("completed") && field(journal, "selectedId").equals(ID), "Claim did not reveal same task in completed tab");
                graph = new QuestUnlockGraphScreen(journal, ID); game.setScreen(graph); wait = 4;
            }
            case 11 -> {
                var p = ((Map<String, QuestUnlockLayout.Position>) field(graph, "positions")).get(ID);
                double zoom = (double) field(graph, "zoom"), x = (double) field(graph, "panX") + (p.x() + 95) * zoom;
                double y = (double) field(graph, "panY") + (p.y() + 40) * zoom;
                graph.mouseClicked(x, y, 0); graph.mouseReleased(x, y, 0);
                graph.mouseClicked(x, y, 0); graph.mouseReleased(x, y, 0);
                require(game.screen instanceof QuestScreen && field(game.screen, "selectedId").equals(ID), "Double click did not open requested task");
                game.screen.onClose(); wait = 4;
            }
            case 12 -> {
                require(game.screen == graph, "Task return lost graph draft");
                set(graph, "propertiesTask", ID); wait = 4;
            }
            case 13 -> {
                require(field(graph, "propertyPreview") instanceof QuestScreen, "Graph properties did not render task preview");
                capture(game, "unlock-task-preview-zh-1920");
                graph.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                invoke(graph, "openTask", new Class<?>[]{String.class}, ID + "_next");
                require(game.screen instanceof QuestScreen && field(game.screen, "selectedId").equals(ID + "_next"), "Context open cannot select task");
                game.setScreen(null); wait = 2;
            }
            case 14 -> {
                QuestUnlockToast.clear(); QuestUnlockToast.enqueue(ID); QuestUnlockToast.enqueue(ID + "_next"); wait = 3;
            }
            case 15 -> {
                require(QuestKeyMappings.DISMISS_NEW_TASK.getDefaultKey().getValue() == GLFW.GLFW_KEY_GRAVE_ACCENT, "Wrong notice dismiss default");
                String first = QuestUnlockToast.currentQuestId(); require(!first.isBlank(), "No active notice");
                net.minecraft.client.KeyMapping.click(QuestKeyMappings.DISMISS_NEW_TASK.getKey()); wait = 3;
            }
            case 16 -> {
                require(QuestUnlockToast.currentQuestId().equals(ID + "_next"), "Tilde did not dismiss only the current notice");
                net.minecraft.client.KeyMapping.click(QuestKeyMappings.OPEN.getKey()); wait = 3;
            }
            case 17 -> {
                require(game.screen instanceof QuestScreen && field(game.screen, "selectedId").equals(ID + "_next"), "J did not open notice task");
                journal = (QuestScreen) game.screen;
                invoke(journal, "markAllTasksRead", new Class<?>[0]);
                require(!QuestUnlockToast.isPresenting(), "Read-all retained queued new-task notices");
                game.setScreen(new OverlayProbe()); wait = 4;
            }
            case 18 -> {
                capture(game, "toast-above-item-zh-1920");
                try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    int white = 0; double dpi = game.getWindow().getGuiScale();
                    int cx = game.screen.width / 2;
                    for (int x = (int)((cx - 6) * dpi); x < (cx + 6) * dpi; x++)
                        for (int y = (int)(103 * dpi); y < 107 * dpi; y++) {
                            int color = image.getPixelRGBA(x, y);
                            if ((color & 255) > 240 && (color >> 8 & 255) > 240 && (color >> 16 & 255) > 240) white++;
                        }
                    require(white > 30, "Item/check overlay occluded the toast capsule");
                }
                var config = new QuestConfigScreen(journal); game.setScreen(config);
                invoke(config, "selectTab", new Class<?>[]{String.class}, "notifications");
                require(config.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                        .noneMatch(b -> b.styleId().equals("notification-preview")), "Obscured notification preview button remains");
                invoke(config, "selectTab", new Class<?>[]{String.class}, "tasks"); wait = 4;
            }
            case 19 -> {
                capture(game, "task-page-size-setting-zh-1920");
                pending = ClientQuestProgress.setTasksPerPage(1000); wait = 5;
            }
            case 20 -> {
                require(ClientQuestProgress.tasksPerPage() == 1000, "Server page size update not applied");
                for (String suffix : List.of("add_item_display", "add_image", "add_scroll", "config.tasks_per_page", "unlock_graph.open_task"))
                    require(net.minecraft.client.resources.language.I18n.exists("screen.xero_quest_journal." + suffix), "Missing lang " + suffix);
                pending = CompletableFuture.runAsync(() -> {
                    QuestConfig.defaults().tasksPerPage = oldPageSize; QuestConfig.defaults().taskPagination = oldPagination;
                    require(QuestConfig.save(), "Could not restore pagination settings");
                }, game.getSingleplayerServer());
                ClientPreferences.get().questSearchFilters = filters; ClientPreferences.get().questSearchScopes = scopes;
                ClientPreferences.get().reducedMotion = oldReducedMotion; ClientPreferences.get().save();
            }
            case 21 -> {
                game.setScreen(null);
                Files.writeString(OUTPUT.resolve("task-ui-corrections-result.txt"), "PASS: native task controls survive edit/preview; search collapse/expand/cancel; compact claim and claimed-task navigation; W/S task selection; graph double-click/task preview/open/return; tilde and J notification keys; read-all clears queue; toast above item/check pixels; server page size 10/1000 + protocol bounds/round-trip; removed hidden preview; localized controls.\n");
                return true;
            }
        }
        stage++; nextFrame = ClientSmokeTest.renderedFrames + 2; return false;
    }

    private static final class OverlayProbe extends Screen {
        OverlayProbe() { super(Component.literal("Overlay depth verification")); }
        @Override public void render(GuiGraphics g, int mx, int my, float delta) {
            g.fill(0, 0, width, height, 0xFF343D46);
            var layout = StatusToastRenderer.layout(font, Component.literal("Toast stays above reward items"), 360, 40);
            StatusToastRenderer.drawAnimated(g, font, layout, width / 2, 100, 1);
            g.pose().pushPose(); g.pose().translate(0, 0, 500);
            g.renderItem(new ItemStack(Items.DIAMOND), width / 2 - 8, 100);
            QuestStatusIcons.renderCheckbox(g, true, width / 2 - 8, 100, 16, false, 1);
            g.pose().popPose(); g.flush();
        }
    }
    private static List<QuestPresentation.Control> controls(QuestScreen screen) throws Exception {
        var cls = Class.forName("xtdpotato.xero_quest_journal.client.QuestPresentationClient");
        var method = cls.getDeclaredMethod("snapshot", String.class, LayoutEditorState.class); method.setAccessible(true);
        return ((QuestPresentation) method.invoke(null, ID, field(screen, "layout"))).controls();
    }
    private static Field property(Object target, String name) throws Exception {
        for (var type = target.getClass(); type != null; type = type.getSuperclass())
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    private static Object field(Object target, String name) throws Exception { return property(target, name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { property(target, name).set(target, value); }
    private static String enumValue(Object target, String name) { try { return field(target, name).toString(); } catch (Exception e) { throw new RuntimeException(e); } }
    private static Object call(Object target, String name) throws Exception { return invoke(target, name, new Class<?>[0]); }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        for (var type = target.getClass(); type != null; type = type.getSuperclass())
            try { var method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args); }
            catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(name);
    }
    private static StyledButton button(Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow(() -> new AssertionError("Missing button " + id));
    }
    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(OUTPUT.resolve(name + ".png")); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message + " at UI stage " + stage); }
}
