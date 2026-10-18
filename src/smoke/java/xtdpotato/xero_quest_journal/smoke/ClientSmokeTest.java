package xtdpotato.xero_quest_journal.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.client.AnimatedQuestScreen;
import xtdpotato.xero_quest_journal.client.QuestHudRenderer;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.StyledButton;
import xtdpotato.xero_quest_journal.client.StyledChoiceButton;
import xtdpotato.xero_quest_journal.client.ButtonStyleSheet;
import xtdpotato.xero_quest_journal.client.LayoutStore;
import xtdpotato.xero_quest_journal.client.LayoutEditorState;
import xtdpotato.xero_quest_journal.client.ClientQuestProgress;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.QuestDocuments;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.ListIconConfig;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;
import xtdpotato.xero_quest_journal.core.runtime.QuestRuntime;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import java.util.List;
import java.util.Set;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Runs inside the real client; this source set is never packaged in the mod JAR. */
@EventBusSubscriber(modid = XeroQuestJournal.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    static long renderedFrames;
    @SubscribeEvent
    public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        renderedFrames++;
    }
    private static final Path OUTPUT = Path.of("..", "build", "verification");
    private static final long START = System.nanoTime();
    private static int stage;
    private static int waitTicks = 40;
    private static boolean finished;
    private static CompletableFuture<Integer> commands;
    private static final String PREFIX = "smoke_" + System.currentTimeMillis();
    private static int authoringWait;
    private static CompletableFuture<Void> safetyTests;
    private static int contextX;
    private static int contextY;
    private static int propertyKind;
    private static int propertyUiStep;
    private static CompletableFuture<Void> languageReload;
    private static CompletableFuture<Void> runtimeSetup;
    private static CompletableFuture<String> runtimeCheck;
    private static CompletableFuture<String> duplicateClaim;
    private static int diamondBefore;
    private static int emeraldBefore;
    private static String trackingTestId;
    private static boolean lootEditorOpened;
    private static boolean lootQualityVerified;
    private static int lootEditorWait;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event) {
        if ((!Boolean.getBoolean("xero.smokeTest") && !Boolean.getBoolean("xero.mailboxTest") && !Boolean.getBoolean("xero.mailReferenceTest")
                && !Boolean.getBoolean("xero.authoringDocsTest")
                && !Boolean.getBoolean("xero.anecdoteTest")
                && !Boolean.getBoolean("xero.popupAnimationTest")
                && !Boolean.getBoolean("xero.mailBodyEditorTest")
                && !Boolean.getBoolean("xero.mailAdminTest")
                && !Boolean.getBoolean("xero.hotEventsTest")
                && !Boolean.getBoolean("xero.spotlightUiTest")
                && !Boolean.getBoolean("xero.journeyTest")
                && !Boolean.getBoolean("xero.journeyEditingTest")
                && !Boolean.getBoolean("xero.overviewTest")
                && !Boolean.getBoolean("xero.overviewAuthoringTest")
                && !Boolean.getBoolean("xero.journalFocusTest")
                && !Boolean.getBoolean("xero.journalOrderTest")
                && !Boolean.getBoolean("xero.generateTasksTest")
                && !Boolean.getBoolean("xero.tacticalCompatibilityTest")
                && !Boolean.getBoolean("xero.generatedDeletionTest")
                && !Boolean.getBoolean("xero.trackingHudTest")
                && !Boolean.getBoolean("xero.lootTableTest")
                && !Boolean.getBoolean("xero.canvasEditorTest")
                && !Boolean.getBoolean("xero.locationItemTest")
                && !Boolean.getBoolean("xero.mediaTest")
                && !Boolean.getBoolean("xero.controlGraphTest")
                && !Boolean.getBoolean("xero.unlockGraphTest")
                && !Boolean.getBoolean("xero.rewardPopupTest")
                && !Boolean.getBoolean("xero.claimReceiptTest")
                && !Boolean.getBoolean("xero.rewardQualityTest")
                && !Boolean.getBoolean("xero.creationTest")
                && !Boolean.getBoolean("xero.animationTest")
                && !Boolean.getBoolean("xero.runtimeTest")
                && !Boolean.getBoolean("xero.structureSceneTest")
                && !Boolean.getBoolean("xero.buttonImageTest")
                && !Boolean.getBoolean("xero.rewardsTest")
                && !Boolean.getBoolean("xero.templateTest")
                && System.getProperty("xero.recipeViewerTest") == null) || finished) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        if (game.getSingleplayerServer() != null && !Boolean.getBoolean("xero.mailAdminTest")
                && !Boolean.getBoolean("xero.rewardQualityTest")
                && !Boolean.getBoolean("xero.journalOrderTest") && !Boolean.getBoolean("xero.spotlightUiTest")) {
            xtdpotato.xero_quest_journal.client.ClientEditMode.set(true);
        }
        try {
            long timeout = Boolean.getBoolean("xero.anecdoteTest")
                    ? 90_000_000_000L : 360_000_000_000L;
            require(System.nanoTime() - START < timeout, "Client test timed out at stage " + stage);
            if (game.getOverlay() != null || --waitTicks > 0) {
                return;
            }
            if (Boolean.getBoolean("xero.mailboxTest")) {
                if (MailboxRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: mailbox M key, inventory entry, server snapshots, read state,\n"
                                    + "delete-read confirmation, unclaimed attachment protection, claim-all,\n"
                                    + "inventory delivery, no-claimable Xero tip, world-data round-trip,\n"
                                    + "mail send/read commands and command-opened mailbox/task screens,\n"
                                    + "recipient/icon pickers, multiple mixed rewards, component attachments, bundle commands,\n"
                                    + "independent star/keepsake state, expiry/quick-delete protection, metadata codecs,\n"
                                    + "keepsake search, draggable smooth scrolling, help and confirmation dialogs,\n"
                                    + "15-entry reward editing, item source popup, 34x38 claim cards,\n"
                                    + "opaque help/delete dialogs and a circular confirmation icon,\n"
                                    + "white favorite surface, legacy palette migration with custom colors preserved,\n"
                                    + "staggered mailbox/keepsake entrance, stable refresh, reduced motion and unchanged read state,\n"
                                    + "single/bulk item-only mail receipts, per-reward visibility and popup settings,\n"
                                    + "hidden reward execution, editor/NBT/bundle/network round-trips and no receipt on failed claims.\n");
                    Files.copy(OUTPUT.resolve("result.txt"), OUTPUT.resolve("mailbox-result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_MAILBOX_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.trackingHudTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (TrackingHudRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("tracking-hud-result.txt"),
                            "PASS: four default tracking HUD emblems, list/HUD configuration consistency,"
                                    + " blank/unrelated cover icons, explicit style overrides, custom tracked image,"
                                    + " new normal-terrain world structure locate, server-to-client located coordinates,"
                                    + " world marker projection and V animation replay cooldown;"
                                    + " 1920x1080 and compact screenshots, and HUD hide/show preference;"
                                    + " camera-basis left/right/up/front/behind across yaw/pitch and reversed view;"
                                    + " mail banner template/reward/JSON/NBT/network/edit round-trips, click/release/task/back;"
                                    + " countdown persistence, real/game clocks, live-variable timing and invalid-value fallback;"
                                    + " compact banner editor bounds and opaque task-style status capsule pixels;\n");
                    Files.copy(OUTPUT.resolve("tracking-hud-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.mailReferenceTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (MailReferenceRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"), "PASS: unread badges, treasure archive/arrival/open states;"
                            + " NBT and protocol round-trip; real/game expiry; edge selection scrolling;"
                            + " half-screen confirmations; customizable press-and-release mail notification.\n");
                    Files.copy(OUTPUT.resolve("result.txt"), OUTPUT.resolve("mail-reference-result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.lootTableTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                int[] distributed = xtdpotato.xero_quest_journal.client.LootWeightDistribution
                        .redistribute(new int[]{34, 33, 33}, new boolean[]{true, true, true}, 0, 10);
                require(java.util.Arrays.equals(distributed, new int[]{10, 45, 45}),
                        "Loot slider did not evenly redistribute remaining weight");
                int[] withDisabled = xtdpotato.xero_quest_journal.client.LootWeightDistribution
                        .redistribute(new int[]{50, 20, 30}, new boolean[]{true, false, true}, 0, 10);
                require(java.util.Arrays.equals(withDisabled, new int[]{10, 20, 90}),
                        "Disabled loot rows did not retain their weight");
                require(java.util.Arrays.equals(
                                xtdpotato.xero_quest_journal.client.LootWeightDistribution
                                        .equalize(new int[]{1, 1, 1}, new boolean[]{true, true, true}),
                                new int[]{34, 33, 33}),
                        "Initial loot weight remainder was not deterministic");
                if (!lootQualityVerified) {
                    var server = game.getSingleplayerServer();
                    var author = server.getPlayerList().getPlayer(game.player.getUUID());
                    String lootId = "loot_quality_" + System.currentTimeMillis();
                    var entry = new xtdpotato.xero_quest_journal.core.model.LootDefinition.Entry(
                            "quality-entry", "item", "minecraft:diamond", 1, 100, "always", true,
                            "epic", "#327A65");
                    var table = new xtdpotato.xero_quest_journal.core.model.LootDefinition(
                            lootId, "Quality fixture", xtdpotato.xero_quest_journal.core.model.LootDefinition.Mode.RANDOM,
                            1, "", List.of(entry));
                    try {
                        xtdpotato.xero_quest_journal.core.io.QuestLootStore.save(author, table);
                        var loaded = xtdpotato.xero_quest_journal.core.io.QuestLootStore.find(lootId);
                        require(loaded != null && loaded.entries().getFirst().equals(entry),
                                "Loot item quality/background did not survive table save/load");
                        var frozen = xtdpotato.xero_quest_journal.core.runtime.QuestRewards.freeze(author,
                                List.of(new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward(
                                        "loot", lootId, 1)));
                        require(frozen.equals(List.of(new xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward(
                                        "item", "minecraft:diamond", 1, "epic", "#327A65"))),
                                "Loot selection did not preserve item quality/background");
                    } finally {
                        xtdpotato.xero_quest_journal.core.io.QuestLootStore.delete(author, lootId);
                    }
                    lootQualityVerified = true;
                }
                if (!lootEditorOpened) {
                    game.setScreen(new xtdpotato.xero_quest_journal.client.QuestLootScreen(game.screen));
                    lootEditorOpened = true;
                    return;
                }
                if (++lootEditorWait < 12) return;
                require(game.getWindow().getWidth() >= 1600 && game.getWindow().getHeight() >= 900,
                        "Loot editor regression did not run at desktop size");
                if (lootEditorWait == 12) require(game.screen.children().stream().anyMatch(child ->
                                child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                                        && widget instanceof xtdpotato.xero_quest_journal.client.StyledChoiceButton<?>
                                        && ((xtdpotato.xero_quest_journal.client.StyledChoiceButton<?>) widget)
                                        .styleId().equals("loot-entry-quality-0")),
                        "Loot item editor did not expose its quality selector");
                if (!LootDisplayRegression.tick(game)) return;
                Files.createDirectories(OUTPUT);
                Files.writeString(OUTPUT.resolve("loot-table-result.txt"),
                        "PASS: weight redistribution, quality/background round-trip, legacy gift fallback, display icon save/reload, frozen choice icon, catalog cache, mailbox NBT and network round-trips, picker Apply/reset/undo/redo, desktop/compact non-overlap geometry, smooth scrolling, Chinese 1920x1080 editor/mail screenshots.\n");
                Files.copy(OUTPUT.resolve("loot-table-result.txt"), OUTPUT.resolve("result.txt"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                finished = true;
                game.stop();
                return;
            }
            if (Boolean.getBoolean("xero.authoringDocsTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (AuthoringDocumentationRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: real script examples and predicate result types, graph true/false/once/error behavior, "
                                    + "syntax-only editor validation, draft retention, JS inspector bounds, complete command export, "
                                    + "six full-screen settings tabs and 100ms tab timing, node quick editing/full-content display, "
                                    + "pinyin field menus, hold-drag owner-preserving variables, anchored independent inventory entries, "
                                    + "Chinese 1920x1080 screenshots.\n");
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.popupAnimationTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (PopupAnimationRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: centered popup scale/fade opening, reversible closing, context/dropdown entry/exit,\n"
                                    + "early-return menu cleanup, stationary background, input recovery,\n"
                                    + "hierarchy expand/collapse icon feedback and real journal context menu animations.\n");
                    Files.copy(OUTPUT.resolve("result.txt"), OUTPUT.resolve("popup-animation-result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_POPUP_ANIMATION_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.mailBodyEditorTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (MailBodyEditorRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("mail-body-editor-result.txt"),
                            "PASS: inline body typing/paste undo/redo, redo invalidation, toolbar states,\n"
                                    + "resize/child-editor history retention, shared rich editor cancel/save/styles/limit,\n"
                                    + "mail clipboard and delivery preservation, styled wrapping, desktop/compact screenshots.\n");
                    Files.copy(OUTPUT.resolve("mail-body-editor-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_MAIL_BODY_EDITOR_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.mailAdminTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (MailAdminRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("mail-admin-result.txt"),
                            "PASS: mail choice preflight/cancel/claim/exact receipts/duplicate protection,\n"
                                    + "inventory-full/invalid/stale choices and legacy uncertain receipts,\n"
                                    + "shared task choice screen, task claim/cancel/receipt regression, one bulk choice confirmation,\n"
                                    + "mail edit/re-send, expiry and claimed-state preservation,\n"
                                    + "legacy recipient compatibility and frozen script edit/re-send,\n"
                                    + "multi-selection/Ctrl+A/inversion/atomic batch deletion,\n"
                                    + "edit-mode server permission gates and packet codec validation.\n");
                    Files.copy(OUTPUT.resolve("mail-admin-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_MAIL_ADMIN_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.hotEventsTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (HotEventsRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: hot-event cards, synchronized start point, visual links and canvas navigation,\n"
                                    + "overview replacement navigation, equal button heights, wider recommended card,\n"
                                    + "reversible hover animation, screen-centre zoom, inverse pointer transforms and horizontal rebound.\n");
                    Files.copy(OUTPUT.resolve("result.txt"), OUTPUT.resolve("hot-events-result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_HOT_EVENTS_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.spotlightUiTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (SpotlightUiRegression.tick(game)) {
                    Files.copy(OUTPUT.resolve("spotlight-ui-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.journalOrderTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (JournalOrderRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("journal-order-result.txt"),
                            "PASS: grouped-only rows, ungrouped footer, task movement/menu/buttons/keys,\n"
                                    + "selection and scroll reveal, completed-last defaults, tab move/cancel,\n"
                                    + "atomic order persistence, filtered neighbors, catalog/codec round-trip,\n"
                                    + "membership isolation, unchanged task sources/runtime order, stale revisions,\n"
                                    + "server/client edit-mode denial, desktop/compact screenshots.\n");
                    Files.writeString(OUTPUT.resolve("journal-group-slide-result.txt"),
                            "PASS: 100ms timing, upward close/downward open, height/neighbor geometry,\n"
                                    + "header/body clipping and hit testing, rapid reversal, settled cleanup,\n"
                                    + "reduced motion, opening/closing/collapsed/expanded screenshots.\n");
                    Files.copy(OUTPUT.resolve("journal-order-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.journalFocusTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (JournalFocusRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("journal-focus-result.txt"),
                            "PASS: group-only focus, first unlocked quest selection, manual in-group switching,\n"
                                    + "automatic group progression and ordinary tracked-successor progression,\n"
                                    + "focus restore/exit/reset, player/NBT/network isolation,\n"
                                    + "all unlocked tasks in active, category order, uncategorized footer, editor locks and circular tools,\n"
                                    + "selected-tab category headings/icons, group-toggle retention and scroll-row geometry,\n"
                                    + "task settings round-trip, list accent defaults/overrides, tracking styles and double-wave timing,\n"
                                    + "desktop/small-window focus switch, selection arrow bounds and rendered icon atlas.\n");
                    Files.copy(OUTPUT.resolve("journal-focus-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_JOURNAL_FOCUS_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.overviewAuthoringTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (OverviewAuthoringRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("overview-authoring-result.txt"),
                            "PASS: overview edit menu/all-task visibility, global add group/task menu,\n"
                                    + "four group-plus actions via real mouse input, template/form cancellation,\n"
                                    + "task top/bottom and sibling group before/after creation,\n"
                                    + "disk/catalog persistence, unchanged memberships/runtime order,\n"
                                    + "missing target/edit-mode denial, legacy order JSON, compact menu bounds,\n"
                                    + "scale/fade menu entry/exit and post-dismissal input recovery.\n");
                    Files.copy(OUTPUT.resolve("overview-authoring-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.overviewTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (OverviewRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("overview-result.txt"),
                            "PASS: configurable overview background, image picker, default/player node scopes,\n"
                                    + "server save/reload, player isolation, once/reset, theme codecs,\n"
                                    + "live update, responsive layout, guide navigation and preserved property nodes.\n");
                    Files.copy(OUTPUT.resolve("overview-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_OVERVIEW_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.journeyEditingTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (JourneyEditingRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("journey-editing-result.txt"),
                            "PASS: recommended journey focus, task default inclusion and visibility,\n"
                                    + "chapter task picker, empty membership, dragged positions and display-only connections,\n"
                                    + "server permission/conflict validation, catalog round-trip,\n"
                                    + "chapter background/music switch and playback release,\n"
                                    + "chapter-only appearance isolation, full-scene multiple selection,\n"
                                    + "live width/height/ratio handles, framed chapter markers,\n"
                                    + "background mode/music graph execution, gallery draft save, chapter switch events,\n"
                                    + "click-opened quest details with explicit navigation,\n"
                                    + "distinct icons, finite line endpoints, overscroll rebound,\n"
                                    + "desktop/compact editor geometry, loot toolbar and hot-event exit icon,\n"
                                    + "single native tooltip per frame, edit/exit glyphs, chapter selection glyph and multi-selector.\n");
                    Files.copy(OUTPUT.resolve("journey-editing-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_JOURNEY_EDITING_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.journeyTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (JourneyRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: overview hover/press/release cancellation and sequential exit/navigation; "
                                    + "chapter background round-trip and rapid crossfade, reversible tab underlines, "
                                    + "active-tab bottom progress bounds, half-size and half-opacity marker, "
                                    + "selected-tab no-op, tab/content swipes, frame-independent scrolling, "
                                    + "branch layout, reduced motion, compact scaling, task return and background editor; "
                                    + "interactive condition cards, pinned reward footer and item-source inspection; "
                                    + "chapter/global icon and text offset editor, preview, picker, validation, "
                                    + "cancel/reset, persistence, inheritance and network round-trip; "
                                    + "continuous chapter crossing/reversal, vertical wheel/scrollbar limits, "
                                    + "per-task text/subtitle/background/color authoring and XML/JSON/catalog round-trip.\n");
                    Files.copy(OUTPUT.resolve("result.txt"), OUTPUT.resolve("journey-continuous-result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.canvasEditorTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (CanvasEditorRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("canvas-editor-result.txt"),
                            "PASS: inline coordinates, zoom keyboard steps, per-task canvas dimensions,\n"
                                    + "default 395x275, context canvas settings, preview-only pointer zoom,\n"
                                    + "middle pan/double-click reset, scrolling and coordinate round-trip,\n"
                                    + (Boolean.getBoolean("xero.canvasHistoryOnly")
                                    ? "saved canvas size undo/redo on the server; source/structure suites excluded.\n"
                                    : "source undo/redo/format and resize.\n"));
                    Files.copy(OUTPUT.resolve("canvas-editor-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.locationItemTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (LocationItemRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: portable biome/structure membership without locate; explicit coordinates; location round-trip;\n"
                            + "contextual input menus; creative variants and item tags; component identity,\n"
                            + "hold/submit/acquire/reward preservation; long item values over network;\n"
                            + "optional place dimension; separate completion/submission footer; small/wide UI.\n");
                    Files.copy(OUTPUT.resolve("result.txt"), OUTPUT.resolve("location-item-result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_LOCATION_ITEM_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.mediaTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (MediaRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: media file import, local audio decode, URL validation, "
                                    + "display text edit/save/reopen and source picker layout; "
                                    + (Boolean.getBoolean("xero.mediaAddonExpected")
                                    ? "optional backend discovery, Vorbis and MP4 frame decoding to EOF.\n"
                                    : "built-in Vorbis/WAV, optional library absence and video capability gating.\n"));
                    Files.copy(OUTPUT.resolve("result.txt"), OUTPUT.resolve(Boolean.getBoolean("xero.mediaAddonExpected")
                                    ? "media-addon-result.txt" : "media-core-result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_MEDIA_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.structureSceneTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (StructureSceneRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: structure scene settings, real world ticks, equipment components, server permissions,\n"
                                    + "completion block, attached triggers, reset/reward separation, version gate,\n"
                                    + "safe return, editor copies, independent versions and settings UI.\n");
                    Files.copy(OUTPUT.resolve("result.txt"), OUTPUT.resolve("structure-scenes-result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.buttonImageTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (ButtonImageRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("button-images-result.txt"),
                            "PASS: image picker, state inheritance/clear, property save/reopen, XML/HTML/JSON,\n"
                                    + "localized property catalog, bounded image/text layout, real normal/hover/pressed pixels,\n"
                                    + "mouse release and small viewport.\n");
                    Files.copy(OUTPUT.resolve("button-images-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.controlGraphTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (ControlGraphRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: control source round-trip, isolated IDs, publication, graph validation,\n"
                            + "server events, JS branches, completion/unlock/property actions, once/cooldown,\n"
                            + "categorized node library, mod gating, event variables, property restore, independent rewards,\n"
                            + "reward catalog redaction, optional KubeJS graph execution and one-shot guards,\n"
                            + "visual graph/source editor, structure preview and scene return isolation.\n");
                    Files.copy(OUTPUT.resolve("result.txt"), OUTPUT.resolve("control-graph-result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(OUTPUT.resolve("result.txt"), OUTPUT.resolve(
                            xtdpotato.xero_quest_journal.core.runtime.QuestKubeJs.available()
                                    ? "control-graph-kubejs-result.txt" : "control-graph-without-kubejs-result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_CONTROL_GRAPH_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.unlockGraphTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (UnlockGraphRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("unlock-graph-result.txt"),
                            "PASS: editor entry, branching/chain links, cycle rejection, task source and layout persistence,\n"
                                    + "unlock runtime, unsaved exit, reversible drawer motion, coordinate validation/undo/redo,\n"
                                    + "persistent multi-selection, Ctrl+A/inversion, batch lock/unlock codec and server execution,\n"
                                    + "green/red status pixels, drawer scrolling and close animation,\n"
                                    + "desktop and compact drawer/canvas screenshots.\n");
                    Files.copy(OUTPUT.resolve("unlock-graph-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.rewardQualityTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (RewardQualityRegression.tick(game)) {
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.claimReceiptTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (ClaimReceiptRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("claim-receipts-result.txt"),
                            "PASS: bulk partial delivery, receipt filtering, full-inventory confirmation,\n"
                                    + "overflow cancellation, confirmed overflow, no duplicate receipt.\n");
                    Files.copy(OUTPUT.resolve("claim-receipts-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.rewardPopupTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (RewardPopupRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("reward-popup-result.txt"),
                            "PASS: empty groups hidden outside editor, half-size Q/E, source inspection without claiming,\n"
                                    + "receipt/source navigation, inside/outside modal clicks, centered fade/scale,\n"
                                    + "claimed-icon repeat clicks, deferred/exactly-once receipts,\n"
                                    + "centered single reward, smooth carousel, drag/scrollbar, partial bulk and overflow claims,\n"
                                    + "item depth isolation, consistent borders and reward hover outline,\n"
                                    + "adaptive source bounds, full component tooltip, scrolling, optional recipe viewer: "
                                    + System.getProperty("xero.recipeViewerTest", "none") + ",\n"
                                    + "create/edit condition drafts, atomic save, cancel, reopen and clear-all;\n"
                                    + "silent automatic delivery after reset, silent action-only manual claims,\n"
                                    + "mixed receipts containing only visible rewards, circular close controls,\n"
                                    + "proportional custom canvases;\n"
                                    + (Boolean.getBoolean("xero.forge1201Test")
                                    ? "SKIPPED: task/mail/receipt background blur is unavailable in Minecraft 1.20.1.\n"
                                    : "task/mail background blur pixel checks.\n"));
                    Files.copy(OUTPUT.resolve("reward-popup-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_REWARD_POPUP_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.creationTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (Boolean.getBoolean("xero.candidatePickerOnly")) {
                    if (CandidatePickerRegression.tick(game)) {
                        Files.createDirectories(OUTPUT);
                        Files.writeString(OUTPUT.resolve("result.txt"),
                                "PASS: item candidate apply/cancel, candidate/field separation, generic registry list,\n"
                                + "world structure ID preservation, actual village start-pool discovery and 3D preview,\n"
                                + "preview zoom/return, NBT-library routing/cancel;\n"
                                + "sanitized jigsaw metadata, vanilla matching rules, searchable connector list,\n"
                                + "projected model picking, pool-template preview/return, instructions and narrow scrolling layout;\n"
                                + "completion title parameters, newline/gold link and ordinary-player command syntax.\n");
                        finished = true;
                        XeroQuestJournal.LOGGER.info("XERO_CANDIDATE_PICKER_PASS");
                        game.stop();
                    }
                    return;
                }
                if (CreationRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: All-scope task/group/chapter menus, ungrouped task creation,\n"
                            + "group/chapter destination selectors, chapter retained across template selection,\n"
                            + "icon/image/background/item pickers, draft preservation, validation,\n"
                            + "resource search, small-window picker bounds, layout save/reopen;\n"
                            + "saved task/reward/condition undo-redo, atomic batch/group deletion restore,\n"
                            + "reference recovery, conflict protection, permissions, unchanged player progress,\n"
                            + "journal toolbar/keyboard and history retained across screen reopening.\n");
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.animationTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (AnimationRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: reversible animation timing, screen opening/closing, input isolation,\n"
                            + "parent state retention, zero-duration, personal defaults, notification queue,\n"
                            + "template resource selection and extended property persistence,\n"
                            + "wide/small property input clipping pixels, viewport clicks, wheel and focus isolation.\n");
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.runtimeTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (RuntimeRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: mixed item/XP/level/command/function/script/effect/unlock/completion rewards,\n"
                            + "frozen scripts, no executable catalog leaks, failure receipts and duplicate protection,\n"
                            + "nested conditions, atomic submissions, counters, events, admin permission checks,\n"
                            + "NBT persistence, overflow confirmation path, reward and condition editor screenshots.\n");
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.rewardsTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (RewardsRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: reward-only source edits, multiple rewards, author permissions/conflicts/validation,\n"
                            + "completion snapshots, inventory-full safety, duplicate claim protection,\n"
                            + "left/right context isolation, reward controls, reward editor add/remove/save/cancel.\n");
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.templateTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (TemplateRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: template selection/search/context multi-select, no ghost menu widgets,\n"
                            + "commission template, GUI creation in target group, template controls editor,\n"
                            + "per-quest content isolation, shared property update, parent validation,\n"
                            + "server authority, atomic overwrite/conflict, catalog network synchronization,\n"
                            + "one-time legacy migration, deletion without default resurrection, confirmed single deletion"
                            + " and confirmed multi-delete.\n");
                    finished = true;
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.generateTasksTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (TaskGenerationRegression.tick(game)) {
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_TASK_GENERATION_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.tacticalCompatibilityTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (TaskGenerationRegression.tick(game, true)) {
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_TACTICAL_COMPATIBILITY_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.generatedDeletionTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (GeneratedDeletionRegression.tick(game)) {
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_GENERATED_DELETION_PASS");
                    game.stop();
                }
                return;
            }
            if (Boolean.getBoolean("xero.anecdoteTest") && stage >= 2) {
                if (game.player == null || game.level == null) return;
                if (AnecdoteRegression.tick(game)) {
                    Files.createDirectories(OUTPUT);
                    Files.writeString(OUTPUT.resolve("anecdote-result.txt"),
                            "PASS: command/selector parsing and permission checks, queue payload codec,\n"
                                    + "single-shot playback, serial queue including 24 identical requests, full fade-out gate,\n"
                                    + "no idle replay, world/screen overlays, reduced motion, disconnect cleanup,\n"
                                    + "1920x1080 and compact screenshots with transparent outer background;\n"
                                    + "independent JSON entries, hidden/discovered state and NBT round-trip,\n"
                                    + "multiple records per entry, press/release selection, outside-release cancellation,\n"
                                    + "right-click author editor, recent completed sticker page and chronicle screenshot.\n");
                    Files.copy(OUTPUT.resolve("anecdote-result.txt"), OUTPUT.resolve("result.txt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    finished = true;
                    game.stop();
                }
                return;
            }
            switch (stage) {
                case 0 -> {
                    game.options.pauseOnLostFocus = false;
                    game.getWindow().setWindowed(1920, 1080);
                    game.getWindow().setWidth(1920);
                    game.getWindow().setHeight(1080);
                    game.options.renderDistance().set(4);
                    game.options.simulationDistance().set(5);
                    game.options.guiScale().set(2);
                    game.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                    game.resizeDisplay();
                    game.setScreen(new TitleScreen());
                    var css = ButtonStyleSheet.parse("/* multiline\n comment */ button { color:#123456; opacity:0.5; }"
                            + "button:hover { background-color:#112233; } button:disabled { color:#8899aa; }");
                    require(css.resolve("test", Set.of(), true, false, false, false).textColor() == 0xff123456, "CSS cascade");
                    require(css.resolve("test", Set.of(), true, false, false, false).backgroundColor() == 0xff112233, "CSS hover");
                    require(css.resolve("test", Set.of(), false, false, true, false).textColor() == 0xff8899aa, "CSS disabled");
                    String rich = "<quest id=\"rich\" name=\"Rich\" title-key=\"custom.title\" icon=\"minecraft:book\">"
                            + "<content><b>Bold</b></content><script>if (a < 10 && b > 2) run();</script></quest>";
                    String metadata = QuestDocuments.metadata(rich, "Rich", "minecraft:diamond", "ignored", false);
                    require(metadata.contains("if (a < 10 && b > 2) run();"), "Property edit escaped script");
                    require(metadata.contains("title-key=\"custom.title\""), "Icon edit removed title key");
                    String rules = QuestDocuments.rules(metadata, "guide", "minecraft:oak_log", 1,
                            "", "minecraft:diamond", 2, "manual");
                    require(rules.contains("if (a < 10 && b > 2) run();"), "Rule edit escaped script");
                    require(QuestConfig.defaults().defaultItemCount >= 1, "Global defaults were not loaded");
                    String iconless = QuestDocuments.metadata(rich, "Rich", "", "ignored", false);
                    require(!iconless.contains(" icon="), "Clearing an icon kept the icon attribute");
                    var leading = new ListIconConfig("minecraft:diamond", "minecraft:emerald", true,
                            true, 0.5F, 20, 18, 3, -2);
                    var trailing = new ListIconConfig("", "minecraft:compass", false,
                            true, 0.75F, 14, 12, -4, 5);
                    String configured = QuestDocuments.metadata(rich, "Rich", "minecraft:diamond",
                            "ignored", false, leading, trailing);
                    QuestDefinition configuredQuest = QuestRepository.parseMarkup(configured, false);
                    require(configuredQuest.leadingIcon().equals(leading)
                                    && configuredQuest.trailingIcon().equals(trailing),
                            "List icon properties did not round-trip");
                    require(QuestRepository.parseMarkup("<quest id=\"iconless\" name=\"Iconless\"></quest>", false)
                            .icon().isBlank(), "Missing icons did not stay empty");
                    require(QuestRepository.parseMarkup("<quest id=\"legacy\" name=\"Legacy\" icon=\"minecraft:book\"></quest>",
                                    false).leadingIcon().image().equals("minecraft:book"),
                            "Legacy icon did not populate the left list slot");
                    var layout = new LayoutEditorState();
                    layout.elements().put("parent", new LayoutEditorState.Element("parent", "quest", "linear", "",
                            120, 80, java.util.Map.of("paddingLeft", "4", "paddingTop", "6",
                            "orientation", "horizontal", "spacing", "8")));
                    layout.elements().put("child", new LayoutEditorState.Element("child", "quest", "text", "Hello",
                            60, 20, java.util.Map.of("textColor", "#ffffff", "visibility", "visible",
                            "layout_gravity", "center", "maxLines", "2")));
                    layout.setParent("child", "parent");
                    layout.renameElement("child", "renamed");
                    var restoredLayout = LayoutStore.restore(LayoutStore.snapshot(layout));
                    require("parent".equals(restoredLayout.parentOf("renamed"))
                                    && "#ffffff".equals(restoredLayout.elements().get("renamed")
                                    .attribute("textColor", ""))
                                    && "horizontal".equals(restoredLayout.elements().get("parent")
                                    .attribute("orientation", ""))
                                    && "center".equals(restoredLayout.elements().get("renamed")
                                    .attribute("layout_gravity", "")),
                            "Nested layout properties did not round-trip");
                    require(IConfigScreenFactory.getForMod(ModList.get().getModContainerById(XeroQuestJournal.MOD_ID)
                            .orElseThrow().getModInfo()).isPresent(), "Config factory missing");
                }
                case 1 -> {
                    capture(game, "01-title");
                    // The base flow clicks through screens within single ticks, which only works without
                    // opening/popup animations. Pin reduced motion instead of depending on whatever an
                    // earlier run left in run-smoke's client.json (animation suites test motion themselves).
                    if (!Boolean.getBoolean("xero.animationTest") && !Boolean.getBoolean("xero.popupAnimationTest"))
                        xtdpotato.xero_quest_journal.client.ClientPreferences.get().reducedMotion = true;
                    stage++;
                    waitTicks = 40;
                    game.createWorldOpenFlows().createFreshLevel(
                            "Xero-Smoke-" + System.currentTimeMillis(),
                            new LevelSettings("Xero Smoke Test", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                    true, new GameRules(), WorldDataConfiguration.DEFAULT),
                            new WorldOptions(42L, Boolean.getBoolean("xero.trackingHudTest"), false),
                            registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                    .getHolderOrThrow(Boolean.getBoolean("xero.trackingHudTest")
                                            ? WorldPresets.NORMAL : WorldPresets.FLAT)
                                    .value().createWorldDimensions(),
                            new TitleScreen());
                    return;
                }
                case 2 -> {
                    if (game.player == null || game.level == null || game.screen != null) {
                        return;
                    }
                    require(!QuestRepository.all().isEmpty(), "Quest repository is empty");
                    capture(game, "02-world");
                    pressJ();
                }
                case 3 -> {
                    require(game.screen instanceof QuestScreen, "J did not open QuestScreen");
                    capture(game, "03-journal-small");
                    click(game, Component.translatable("screen.xero_quest_journal.track").getString());
                    game.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    require(game.screen == null || game.screen instanceof xtdpotato.xero_quest_journal.client.AnimatedQuestScreen closing
                            && closing.isClosing(), "Escape did not start closing QuestScreen");
                }
                case 4 -> {
                    require(game.screen == null, "Quest close animation did not finish");
                    if (QuestHudRenderer.trackedId() == null) {
                        return;
                    }
                    var tracked = QuestHudRenderer.trackedId();
                    trackingTestId = tracked;
                    var server = game.getSingleplayerServer();
                    var uuid = game.player.getUUID();
                    safetyTests = CompletableFuture.runAsync(() -> {
                        var data = QuestProgressData.get(server);
                        var saved = data.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
                        var restored = QuestProgressData.load(saved, server.registryAccess());
                        require(restored.tracked(uuid).contains(tracked), "Track-only player was not saved");
                        var isolated = new QuestProgressData();
                        require(isolated.tracked(uuid).isEmpty(), "Tracking leaked to a new world");
                        isolated.toggleTracked(uuid, "one", 3);
                        isolated.toggleTracked(uuid, "two", 3);
                        isolated.toggleTracked(uuid, "three", 3);
                        var reloaded = QuestProgressData.load(isolated.save(new net.minecraft.nbt.CompoundTag(),
                                server.registryAccess()), server.registryAccess());
                        require(reloaded.tracked(uuid).equals(java.util.List.of("one", "two", "three")),
                                "Three tracking entries did not round-trip");
                        require(reloaded.tracked(java.util.UUID.randomUUID()).isEmpty(), "Tracking leaked between players");
                    }, server);
                    capture(game, "04-tracking-hud");
                    pressJ();
                }
                case 5 -> {
                    if (!safetyTests.isDone()) return;
                    safetyTests.join();
                    require(!ClientQuestProgress.catalog().quests().isEmpty(), "Server catalog was not synchronized");
                    require(game.screen instanceof QuestScreen, "J did not reopen QuestScreen");
                    TrackingIndicatorRegression.check(game, trackingTestId, true);
                    capture(game, "tracking-list-active");
                    click(game, Component.translatable("screen.xero_quest_journal.untrack").getString());
                }
                case 6 -> {
                    if (QuestHudRenderer.trackedId() != null) {
                        return;
                    }
                    TrackingIndicatorRegression.check(game, trackingTestId, false);
                    clickId(game, "edit");
                    capture(game, "05-editor");
                    clickId(game, "close");
                    require(game.screen == null || game.screen instanceof xtdpotato.xero_quest_journal.client.AnimatedQuestScreen closing
                            && closing.isClosing(), "Close button did not start closing QuestScreen");
                    var server = game.getSingleplayerServer();
                    require(server != null, "Integrated server missing");
                    commands = CompletableFuture.supplyAsync(() -> {
                        var dispatcher = server.getCommands().getDispatcher();
                        var source = server.createCommandSourceStack();
                        try {
                            dispatcher.execute("xero_quest list", source);
                            return dispatcher.execute("xero_quest reload", source);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    }, server);
                }
                case 7 -> {
                    require(game.screen == null, "Close button animation did not finish");
                    if (!commands.isDone()) {
                        return;
                    }
                    require(commands.join() > 0, "Reload command failed");
                    game.options.guiScale().set(1);
                    game.resizeDisplay();
                    pressJ();
                }
                case 8 -> {
                    require(game.screen instanceof QuestScreen, "Reopen after reload failed");
                    capture(game, "06-journal-wide");
                    game.screen.onClose();
                    game.options.guiScale().set(2);
                    game.resizeDisplay();
                    game.setScreen(new InventoryScreen(game.player));
                }
                case 9 -> {
                    if (!(game.screen instanceof QuestScreen)) {
                        capture(game, "07-inventory");
                        clickId(game, "inventory-entry");
                        require(game.screen instanceof QuestScreen, "Inventory entry failed");
                        return;
                    }
                    // A freshly opened journal ignores input until its opening animation has played;
                    // clicking in the same tick only worked when reduced motion was left enabled.
                    if (((QuestScreen) game.screen).inputBlocked()) return;
                    clickId(game, "edit");
                    require((boolean) field(game.screen, "editMode"), "Edit button did not enter edit mode");
                    context(game, 20, 80, "system:add-group");
                    EditorRegression.menu(game, "add_group");
                    fill(game, PREFIX + "_group", "Smoke group", "Group description");
                    capture(game, "08-add-group");
                    clickId(game, "create");
                }
                case 10 -> {
                    if (QuestRepository.groups().stream().noneMatch(group -> group.id().equals(PREFIX + "_group"))) {
                        require(++authoringWait < 300, "Group was not created");
                        return;
                    }
                    authoringWait = 0;
                    context(game, 20, 80, "group:" + PREFIX + "_group");
                    EditorRegression.menu(game, "add_chapter");
                    fill(game, PREFIX + "_chapter", "Smoke chapter", "Chapter description");
                    clickId(game, "create");
                }
                case 11 -> {
                    if (QuestRepository.chapters().stream().noneMatch(chapter -> chapter.id().equals(PREFIX + "_chapter"))) {
                        require(++authoringWait < 300, "Chapter was not created");
                        return;
                    }
                    authoringWait = 0;
                    context(game, 20, 80, "group:" + PREFIX + "_group");
                    EditorRegression.menu(game, "add_task");
                    clickId(game, "template-use");
                    fill(game, PREFIX + "_task", "Smoke task <>&", "Description <>&");
                    var chapterChoice = stringChoiceId(game, "create-chapter");
                    chapterChoice.select(PREFIX + "_chapter");
                    clickId(game, "create");
                }
                case 12 -> {
                    if (QuestRepository.find(PREFIX + "_task") == null) {
                        require(++authoringWait < 300, "Task was not created");
                        return;
                    }
                    require(QuestRepository.find(PREFIX + "_task").title().equals("Smoke task <>&"), "HTML roundtrip");
                    require(QuestRepository.find(PREFIX + "_task").description().equals("Description <>&"), "Content roundtrip");
                    require(QuestRepository.chapters().stream().anyMatch(chapter -> chapter.id().equals(PREFIX + "_chapter")
                            && (chapter.quests().contains(PREFIX + "_task")
                            || chapter.quests().isEmpty() && !chapter.timeline().explicitTasks()
                            && chapter.groupId().equals(QuestRepository.groupOf(PREFIX + "_task")))),
                            "Chapter membership missing");
                    capture(game, "09-created-hierarchy");
                    context(game, 20, 80, "group:" + PREFIX + "_group");
                    EditorRegression.menu(game, "add_task");
                    clickId(game, "template-use");
                    fill(game, PREFIX + "_task", "Duplicate", "");
                    clickId(game, "create");
                }
                case 13 -> {
                    capture(game, "10-duplicate-id");
                    require(game.screen.children().stream().anyMatch(EditBox.class::isInstance), "Duplicate closed the form");
                    require(QuestRepository.find(PREFIX + "_task").title().equals("Smoke task <>&"), "Duplicate replaced task");
                    clickId(game, "cancel");
                    contextX = game.screen.width - 40;
                    contextY = 85;
                    context(game, contextX, contextY, null);
                }
                case 14 -> {
                    capture(game, "11-context-menu");
                    EditorRegression.menu(game, "add_text");
                    require(game.screen.children().stream().anyMatch(EditBox.class::isInstance), "Text form not opened");
                    var layoutFields = game.screen.children().stream().filter(EditBox.class::isInstance)
                            .map(EditBox.class::cast).toList();
                    require(layoutFields.size() >= 2, "Layout text fields missing");
                    layoutFields.get(0).setValue(PREFIX + "_text");
                    layoutFields.get(1).setValue("Layout note");
                    capture(game, "11-layout-content-form");
                    clickId(game, "create");
                    require(LayoutStore.load("journal").elements().values().stream().anyMatch(e -> e.value().equals("Layout note")
                                    && e.attribute("layout_width", "").equals("-2")
                                    && e.attribute("layout_height", "").equals("-2")),
                            "Layout element not saved");
                    safetyTests = CompletableFuture.runAsync(() -> {
                        var request = new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP, PREFIX + "_bad",
                                "Unauthorized", "", "", "", "normal", "minecraft:oak_log", 16);
                        require(!QuestAuthoring.create(null, request).successful(), "Null author accepted");
                    }, game.getSingleplayerServer());
                }
                case 15 -> {
                    if (!safetyTests.isDone()) {
                        return;
                    }
                    safetyTests.join();
                    capture(game, "12-layout-note");
                    clickId(game, "settings");
                }
                case 16 -> {
                    capture(game, "13-settings");
                    requireModernButtons(game);
                    var prefs = xtdpotato.xero_quest_journal.client.ClientPreferences.get();
                    boolean previous = prefs.trackingHud;
                    clickId(game, "config-hud");
                    var hudToggle = choiceId(game, "config-hud");
                    require(hudToggle.isOpen(), "Styled toggle list did not open");
                    require(prefs.trackingHud == previous, "Styled toggle changed before choosing a list item");
                    selectChoice(game, hudToggle, !previous);
                    require(prefs.trackingHud != previous, "Styled toggle list selection failed");
                    clickId(game, "config-hud");
                    selectChoice(game, hudToggle, previous);
                    require(prefs.trackingHud == previous, "Styled toggle did not restore its original value");
                    clickId(game, "done");
                }
                case 17 -> {
                    if (propertyUiStep == 1) {
                        capture(game, "14-icon-picker-" + propertyKind);
                        var pickerFields = game.screen.children().stream().filter(EditBox.class::isInstance)
                                .map(EditBox.class::cast).toList();
                        require(pickerFields.size() >= 2, "Icon picker fields missing");
                        pickerFields.getLast().setValue("minecraft:diamond");
                        clickId(game, "icon-apply");
                        propertyUiStep = 2;
                        waitTicks = 3;
                        return;
                    }
                    if (propertyUiStep == 2) {
                        capture(game, "14-properties-" + propertyKind);
                        clickId(game, "create");
                        propertyUiStep = 0;
                        authoringWait = 0;
                        break;
                    }
                    requireModernButtons(game);
                    if (propertyKind == 1) {
                        var editProperties = QuestScreen.class.getDeclaredMethod(
                                "editProperties", String.class, String.class);
                        editProperties.setAccessible(true);
                        editProperties.invoke(game.screen, "chapter", PREFIX + "_chapter");
                    } else {
                        if (propertyKind == 0) {
                            EditorRegression.revealRow(game, "group:" + PREFIX + "_group");
                        } else {
                            EditorRegression.reveal(game, PREFIX + "_task");
                        }
                        String target = propertyKind == 0
                                ? "group-" + PREFIX + "_group" : "quest-" + PREFIX + "_task";
                        var button = findId(game, target);
                        contextX = button.getX() + button.getWidth() / 2;
                        contextY = button.getY() + button.getHeight() / 2;
                        game.screen.mouseClicked(contextX, contextY, 1);
                        EditorRegression.menu(game, "properties");
                    }
                    requireModernButtons(game);
                    var fields = game.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
                    require(fields.size() >= 3, "Property fields missing");
                    fields.get(1).setValue("Edited " + propertyKind);
                    if (propertyKind == 1) {
                        clickId(game, "choose-icon");
                    } else {
                        clickId(game, "authoring-page-leading_icon");
                        clickId(game, "leading-image");
                    }
                    propertyUiStep = 1;
                    waitTicks = 3;
                    return;
                }
                case 18 -> {
                    boolean saved = switch (propertyKind) {
                        case 0 -> QuestRepository.groups().stream().anyMatch(g -> g.id().equals(PREFIX + "_group")
                                && g.name().equals("Edited 0") && g.icon().equals("minecraft:diamond")
                                && g.leadingIcon().image().equals("minecraft:diamond"));
                        case 1 -> QuestRepository.chapters().stream().anyMatch(c -> c.id().equals(PREFIX + "_chapter")
                                && c.name().equals("Edited 1") && c.icon().equals("minecraft:diamond"));
                        default -> QuestRepository.find(PREFIX + "_task").title().equals("Edited 2")
                                && QuestRepository.find(PREFIX + "_task").icon().equals("minecraft:diamond")
                                && QuestRepository.find(PREFIX + "_task").leadingIcon().image()
                                .equals("minecraft:diamond");
                    };
                    if (!saved) {
                        require(++authoringWait < 300, "Property edit did not persist");
                        return;
                    }
                    if (++propertyKind < 3) {
                        stage = 17;
                        waitTicks = 10;
                        return;
                    }
                    var disabled = findId(game, "redo");
                    require(!disabled.active, "Redo should be disabled");
                    require(!disabled.mouseClicked(disabled.getX() + 2, disabled.getY() + 2, 0),
                            "Disabled button accepted click");
                    capture(game, "15-edited-hierarchy");
                }
                case 19 -> {
                    CategoryRegression.check(game);
                    var server = game.getSingleplayerServer();
                    runtimeSetup = CompletableFuture.runAsync(() -> {
                        var player = server.getPlayerList().getPlayer(game.player.getUUID());
                        CategoryRegression.serverChecks(player, PREFIX);
                        var parent = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, PREFIX + "_guide",
                                "Playable guide", "Click to complete", PREFIX + "_group", PREFIX + "_chapter",
                                "guide", "minecraft:oak_log", 1, "minecraft:book",
                                "", "minecraft:diamond", 2, "manual");
                        var child = new QuestAuthoring.Request(QuestAuthoring.Kind.TASK, PREFIX + "_item",
                                "Playable item", "Hold sixteen logs", PREFIX + "_group", PREFIX + "_chapter",
                                "item", "minecraft:oak_log", 16, "minecraft:oak_log",
                                PREFIX + "_guide", "minecraft:emerald", 1, "manual");
                        require(QuestAuthoring.create(player, parent).successful(), "Playable guide creation failed");
                        require(QuestAuthoring.create(player, child).successful(), "Playable item creation failed");
                        try {
                            String parentHash = QuestRepository.fingerprint(QuestRepository.source(PREFIX + "_guide"));
                            String childHash = QuestRepository.fingerprint(QuestRepository.source(PREFIX + "_item"));
                            require(QuestAuthoring.editConditions(player, PREFIX + "_guide", parentHash,
                                    java.util.List.of(new QuestDefinition.Trigger("confirm", "", 1))).successful(),
                                    "Playable guide condition failed");
                            require(QuestAuthoring.editConditions(player, PREFIX + "_item", childHash,
                                    java.util.List.of(new QuestDefinition.Trigger("item_hold", "minecraft:oak_log", 16))).successful(),
                                    "Playable item condition failed");
                        } catch (java.io.IOException failure) {
                            throw new IllegalStateException(failure);
                        }
                        require(QuestRuntime.toggleTracking(player, PREFIX + "_guide").equals("tracked"),
                                "Playable guide tracking failed");
                    }, server);
                }
                case 20 -> {
                    if (!runtimeSetup.isDone() || ClientQuestProgress.view(PREFIX + "_guide") == null
                            || ClientQuestProgress.view(PREFIX + "_item") == null
                            || ClientQuestProgress.catalogRevision() != QuestRepository.revision()) return;
                    runtimeSetup.join();
                    require(ClientQuestProgress.view(PREFIX + "_guide").status().equals("active"), "Guide should be active");
                    require(ClientQuestProgress.view(PREFIX + "_item").status().equals("locked"), "Child should be locked");
                    require(ClientQuestProgress.isTracked(PREFIX + "_guide"), "Guide tracking was not synchronized");
                    diamondBefore = count(game, Items.DIAMOND);
                    emeraldBefore = count(game, Items.EMERALD);
                    QuestConfig.defaults().taskPagination = false;
                    require(QuestConfig.save(), "Default pagination setting could not be saved");
                    clickId(game, "edit");
                    clickId(game, "category-main");
                    require((int) field(game.screen, "listPages") == 1,
                            "Task pagination should be disabled by default");
                    QuestConfig.defaults().taskPagination = true;
                    require(QuestConfig.save(), "Pagination test setting could not be saved");
                    game.screen.init(game, game.screen.width, game.screen.height);
                    EditorRegression.checkPagination(game);
                    QuestConfig.defaults().taskPagination = false;
                    require(QuestConfig.save(), "Pagination setting could not be restored");
                    clickId(game, "quest-" + PREFIX + "_guide");
                    require(ClientQuestProgress.view(PREFIX + "_guide").status().equals("active"),
                            "Selecting a confirmation task completed it");
                    clickId(game, "complete");
                }
                case 21 -> {
                    var parent = ClientQuestProgress.view(PREFIX + "_guide");
                    var child = ClientQuestProgress.view(PREFIX + "_item");
                    if (parent == null || !parent.status().equals("complete") || child == null
                            || !child.status().equals("active")) return;
                    require(!ClientQuestProgress.isTracked(PREFIX + "_guide"), "Completed guide remained tracked");
                    var setCategory = QuestScreen.class.getDeclaredMethod("setCategory", String.class);
                    setCategory.setAccessible(true);
                    setCategory.invoke(game.screen, "completed");
                    TrackingIndicatorRegression.check(game, PREFIX + "_guide", false);
                    require(ClientQuestProgress.completionNotices().stream()
                            .anyMatch(notice -> notice.questId().equals(PREFIX + "_guide")),
                            "Completed guide did not create a HUD notice");
                    require(count(game, Items.DIAMOND) == diamondBefore, "Manual reward was issued automatically");
                    clickId(game, "claim");
                }
                case 22 -> {
                    var parent = ClientQuestProgress.view(PREFIX + "_guide");
                    if (parent == null || !parent.status().equals("claimed")
                            || count(game, Items.DIAMOND) != diamondBefore + 2) return;
                    if (!(game.screen instanceof xtdpotato.xero_quest_journal.client.QuestRewardPopupScreen)) return;
                    game.screen.onClose();
                    var server = game.getSingleplayerServer();
                    runtimeCheck = CompletableFuture.supplyAsync(() -> {
                        var player = server.getPlayerList().getPlayer(game.player.getUUID());
                        require(QuestRuntime.claim(player, PREFIX + "_guide").equals("already_claimed"),
                                "Duplicate reward claim was accepted");
                        player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
                        player.getInventory().setChanged();
                        player.containerMenu.broadcastChanges();
                        return "items_added";
                    }, server);
                }
                case 23 -> {
                    if (!runtimeCheck.isDone()) return;
                    require(runtimeCheck.join().equals("items_added"), "Runtime item setup failed");
                    var child = ClientQuestProgress.view(PREFIX + "_item");
                    if (child == null || !child.status().equals("complete")) return;
                    if (ClientQuestProgress.completionNotices().stream()
                            .anyMatch(notice -> notice.questId().equals(PREFIX + "_item"))) return;
                    var setCategory = QuestScreen.class.getDeclaredMethod("setCategory", String.class);
                    setCategory.setAccessible(true);
                    setCategory.invoke(game.screen, "completed");
                    var model = (xtdpotato.xero_quest_journal.client.JournalListModel)
                            field(game.screen, "journalList");
                    require(xtdpotato.xero_quest_journal.client.ClientQuestDefinitions.find(PREFIX + "_item") != null
                                    && model.rows().stream().anyMatch(row -> row.key().equals("quest:" + PREFIX + "_item")),
                            "Completed item missing from client list; type="
                                    + QuestRepository.find(PREFIX + "_item").type()
                                    + ", category=" + field(game.screen, "category")
                                    + ", rows=" + model.rows().stream().map(row -> row.key()).toList());
                    clickButton(game, findId(game, "quest-" + PREFIX + "_item"));
                    require(game.screen instanceof QuestScreen
                                    && field(game.screen, "selectedId").equals(PREFIX + "_item"),
                            "Item selection did not reach details; screen=" + game.screen.getClass().getSimpleName()
                                    + ", category=" + field(game.screen, "category")
                                    + ", selected=" + field(game.screen, "selectedId"));
                    clickId(game, "claim");
                }
                case 24 -> {
                    var child = ClientQuestProgress.view(PREFIX + "_item");
                    if (child == null || !child.status().equals("claimed")
                            || count(game, Items.EMERALD) != emeraldBefore + 1) return;
                    if (!(game.screen instanceof xtdpotato.xero_quest_journal.client.QuestRewardPopupScreen)) return;
                    game.screen.onClose();
                    var server = game.getSingleplayerServer();
                    runtimeCheck = CompletableFuture.supplyAsync(() -> {
                        var player = server.getPlayerList().getPlayer(game.player.getUUID());
                        require(QuestProgressData.get(server).entry(player.getUUID(),
                                QuestRepository.find(PREFIX + "_guide")) != null, "Guide progress missing");
                        require(QuestProgressData.get(server).entry(player.getUUID(),
                                QuestRepository.find(PREFIX + "_item")).claimed(), "Child reward record missing");
                        server.saveEverything(false, true, false);
                        return QuestRuntime.claim(player, PREFIX + "_item");
                    }, server);
                    duplicateClaim = ClientQuestProgress.action(PREFIX + "_item", true);
                }
                case 25 -> {
                    if (!duplicateClaim.isDone() || !runtimeCheck.isDone()) return;
                    require(runtimeCheck.join().equals("already_claimed"), "Saved child claim record missing");
                    require(duplicateClaim.join().equals("already_claimed"), "Child duplicate claim was accepted");
                    require(ClientQuestProgress.catalog().quests().stream().anyMatch(q -> q.id().equals(PREFIX + "_item")),
                            "Updated quest catalog was not synchronized");
                    var catalog = xtdpotato.xero_quest_journal.core.network.QuestCatalog.serverJson();
                    require(!catalog.contains("\"directory\"") && !catalog.contains("\"file\""), "Catalog leaks filesystem paths");
                    capture(game, "16-playable-loop");
                    game.getLanguageManager().setSelected("zh_cn");
                    game.options.languageCode = "zh_cn";
                    languageReload = game.reloadResourcePacks();
                }
                case 26 -> {
                    if (!languageReload.isDone()) {
                        return;
                    }
                    languageReload.join();
                    if (!(game.screen instanceof QuestScreen)
                            || game.screen instanceof AnimatedQuestScreen animated && animated.isClosing()) {
                        return;
                    }
                    require(Component.translatable("screen.xero_quest_journal.properties").getString().equals("\u7f16\u8f91\u5c5e\u6027"),
                            "Chinese properties translation missing");
                    capture(game, "17-journal-chinese");
                    clickId(game, "settings");
                }
                case 27 -> {
                    capture(game, "18-settings-chinese");
                    clickId(game, "done");
                    safetyTests = CompletableFuture.runAsync(() -> {
                        try {
                            var server = game.getSingleplayerServer();
                            EditorRegression.serverChecks(server.getPlayerList().getPlayer(game.player.getUUID()),
                                    PREFIX + "_group", PREFIX + "_chapter", PREFIX + "_guide", PREFIX + "_item");
                        } catch (Exception failure) {
                            throw new RuntimeException(failure);
                        }
                    }, game.getSingleplayerServer());
                }
                case 28 -> {
                    if (!safetyTests.isDone()) return;
                    safetyTests.join();
                    if (!EditorRegression.tick(game, PREFIX + "_guide")) return;
                }
                case 29 -> {
                    if (!SelectionRegression.tick(game, PREFIX + "_guide")) return;
                }
                case 30 -> {
                    if (!NavigationRegression.tick(game, PREFIX + "_guide")) return;
                }
                case 31 -> {
                    Files.writeString(OUTPUT.resolve("result.txt"),
                            "PASS: client/world, J, tracking, list tracking icon add/remove/completion pixels,\n"
                                    + "edit, commands, GUI scales, inventory entry, settings factory,\n"
                                    + "group/chapter/task creation, chapter reference, HTML escaping, duplicate rejection,\n"
                                    + "context menu text creation and persistence, unauthorized author rejection, CSS cascade,\n"
                                    + "group/chapter/task right-click property editing and icon persistence, styled selectors/toggles,\n"
                                    + "no vanilla buttons on journal/forms/settings, disabled button rejection, Chinese resources,\n"
                                    + "script/title-key preservation, prerequisite lock/unlock, confirmation completion,\n"
                                    + "automatic item completion, persistent world records, manual rewards and duplicate-claim rejection,\n"
                                    + "serialized progress/actions/catalog, network duplicate-claim rejection,\n"
                                    + "track-only player persistence, three-entry tracking NBT round-trip and player/world isolation,\n"
                                    + "clipboard snapshots, batch IDs/UIDs/prerequisites, rollback, task/group deletion and backups,\n"
                                    + "Ctrl selection, middle duplicate, deletion confirmation, smooth wheel/scrollbar/swipe,\n"
                                    + "network reset preserving claims, source save/conflict/rollback and add/group menus,\n"
                                    + "group headers/indentation, collapse/expand, visible row extent, standalone-last ordering,\n"
                                    + "header Ctrl selection/property target, refresh/resize retention and preview no-confirm,\n"
                                    + "selected task/group pixels, hover persistence, cross-group selection, standalone selection,\n"
                                    + "multi-selection distinction and selected CSS overrides,\n"
                                    + "player/admin navigation, Q-left/E-right with wrapping, compact header/detail-first layout,\n"
                                    + "category tabs, overview return without confirmation,\n"
                                    + "permission revocation refresh and narrow/wide navigation bounds.\n");
                    finished = true;
                    XeroQuestJournal.LOGGER.info("XERO_SMOKE_PASS");
                    game.stop();
                }
                default -> throw new IllegalStateException("Unknown test stage");
            }
            stage++;
            waitTicks = 30;
        } catch (Throwable failure) {
            finished = true;
            XeroQuestJournal.LOGGER.error("XERO_SMOKE_FAIL stage {}", stage, failure);
            try {
                Files.createDirectories(OUTPUT);
                capture(game, "failure");
                Files.writeString(OUTPUT.resolve("result.txt"), "FAIL stage " + stage + ": " + failure);
            } catch (Exception ignored) {
            }
            game.stop();
        }
    }

    private static void pressJ() {
        KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));
    }

    private static void click(Minecraft game, String label) {
        var screen = game.screen;
        Button button = screen.children().stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(candidate -> candidate.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing button: " + label));
        clickButton(game, button);
    }

    private static void clickId(Minecraft game, String id) throws Exception {
        if (game.screen instanceof QuestScreen && id.startsWith("quest-")) {
            EditorRegression.reveal(game, id.substring("quest-".length()));
        }
        StyledButton button = findId(game, id);
        require(button.active, "Disabled button: " + id);
        clickButton(game, button);
    }

    private static void context(Minecraft game, int x, int y, String target) throws Exception {
        var method = QuestScreen.class.getDeclaredMethod("contextMenu", int.class, int.class, String.class);
        method.setAccessible(true);
        method.invoke(game.screen, x, y, target);
    }

    private static StyledButton findId(Minecraft game, String id) {
        return game.screen.children().stream().filter(StyledButton.class::isInstance)
                .map(StyledButton.class::cast).filter(candidate -> candidate.styleId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing button ID: " + id));
    }

    @SuppressWarnings("unchecked")
    private static StyledChoiceButton<Boolean> choiceId(Minecraft game, String id) {
        return (StyledChoiceButton<Boolean>) findId(game, id);
    }

    @SuppressWarnings("unchecked")
    private static StyledChoiceButton<String> stringChoiceId(Minecraft game, String id) {
        return (StyledChoiceButton<String>) findId(game, id);
    }

    @SuppressWarnings("unchecked")
    private static <T> void selectChoice(Minecraft game, StyledChoiceButton<T> choice, T value) throws Exception {
        var valuesField = StyledChoiceButton.class.getDeclaredField("values");
        valuesField.setAccessible(true);
        var values = (java.util.List<T>) valuesField.get(choice);
        int index = values.indexOf(value);
        require(index >= 0, "Missing choice value: " + value);

        var scrollField = StyledChoiceButton.class.getDeclaredField("scroll");
        scrollField.setAccessible(true);
        double scroll = scrollField.getDouble(choice);
        var popupX = StyledChoiceButton.class.getDeclaredMethod("popupX");
        var popupY = StyledChoiceButton.class.getDeclaredMethod("popupY");
        var rowHeight = StyledChoiceButton.class.getDeclaredMethod("rowHeight");
        popupX.setAccessible(true);
        popupY.setAccessible(true);
        rowHeight.setAccessible(true);
        int row = (int) rowHeight.invoke(choice);
        double x = (int) popupX.invoke(choice) + 6;
        double y = (int) popupY.invoke(choice) + index * row - Math.round(scroll) + row / 2.0;
        require(game.screen.mouseClicked(x, y, 0), "Choice list item did not handle click");
        game.screen.mouseReleased(x, y, 0);
    }

    private static void requireModernButtons(Minecraft game) {
        require(game.screen.children().stream().filter(net.minecraft.client.gui.components.AbstractButton.class::isInstance)
                .allMatch(StyledButton.class::isInstance), "Vanilla button leaked into journal UI");
    }

    private static void clickButton(Minecraft game, Button button) {
        var screen = game.screen;
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        require(screen.mouseClicked(x, y, 0), "Button click not handled: " + button.getMessage().getString());
        screen.mouseReleased(x, y, 0);
    }

    private static void fill(Minecraft game, String id, String name, String description) {
        var fields = game.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
        fields.get(0).setValue(id);
        fields.get(1).setValue(name);
        fields.get(2).setValue(description);
    }

    private static int count(Minecraft game, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < game.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = game.player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void capture(Minecraft game, String name) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(OUTPUT.resolve(name + ".png"));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
