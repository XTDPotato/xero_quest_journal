package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestRewardQuality;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;
import xtdpotato.xero_quest_journal.core.runtime.RewardEffect;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Reward-only drafts never replace task conditions or completion snapshots. */
public final class QuestRewardsScreen extends AnimatedQuestScreen {
    private static final int ROWS_TOP = 106;
    @Override protected int animationHeaderBottom() { return 68; }
    @Override protected int animationFooterTop() { return height - 56; }

    private final Screen parent;
    private final String questId;
    private final String expectedHash;
    private final List<QuestDefinition.Reward> originalRewards;
    private final String originalMode;
    private final BiConsumer<List<QuestDefinition.Reward>, String> draftSaved;
    private final boolean customNode;
    private boolean itemsOnly;
    private java.util.function.Consumer<List<MailboxRuntime.Attachment>> mailSaved;
    private final List<Draft> rewards = new ArrayList<>();
    private String mode;
    private boolean tableMode;
    private int editingReward = -1;
    private Draft editDraft;
    private int scroll;
    private final SmoothScroll motion = new SmoothScroll();
    private boolean listDragging, listMoved;
    private double listStartX, listStartY, listStartScroll;
    private String error;
    private boolean scrollbarDragging;
    private double thumbOffset;
    private ButtonStyleSheet styles;
    private CompletableFuture<QuestAuthoring.Result> pending;
    private final java.util.Set<AbstractWidget> rowWidgets = new java.util.HashSet<>();

    public QuestRewardsScreen(Screen parent, String questId) throws Exception {
        super(text("rewards.edit"));
        this.parent = parent;
        this.questId = questId;
        this.draftSaved = null;
        this.customNode = false;
        String source = QuestRepository.source(questId);
        var quest = QuestRepository.parseDefinition(QuestRepository.fileOf(questId), source);
        expectedHash = QuestRepository.fingerprint(source);
        originalRewards = List.copyOf(quest.rewards());
        originalMode = quest.rewardMode();
        quest.rewards().forEach(reward -> rewards.add(new Draft(reward)));
        mode = quest.rewardMode();
    }

    public QuestRewardsScreen(Screen parent, List<QuestDefinition.Reward> initial, String mode,
                              BiConsumer<List<QuestDefinition.Reward>, String> saved) {
        this(parent, initial, mode, saved, false);
    }
    public QuestRewardsScreen(Screen parent, List<QuestDefinition.Reward> initial, String mode,
                              BiConsumer<List<QuestDefinition.Reward>, String> saved, boolean customNode) {
        super(text(customNode ? "graph.custom_rewards" : "rewards.edit"));
        this.parent = parent;
        this.questId = "";
        this.expectedHash = "";
        this.originalRewards = List.copyOf(initial);
        this.originalMode = mode;
        this.draftSaved = saved;
        this.customNode = customNode;
        initial.forEach(reward -> rewards.add(new Draft(reward)));
        this.mode = mode;
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal." + key);
    }

    public QuestRewardsScreen itemsOnly() {
        itemsOnly = true;
        return this;
    }

    public static QuestRewardsScreen mail(Screen parent, List<MailboxRuntime.Attachment> initial,
                                          java.util.function.Consumer<List<MailboxRuntime.Attachment>> saved) {
        var screen = new QuestRewardsScreen(parent, initial.stream()
                .map(a -> new QuestDefinition.Reward(
                        a.type(), a.item(), a.count(), a.quality(), a.background())).toList(),
                "manual", (values, mode) -> {}, true);
        screen.mailSaved = saved;
        screen.rowHeight = 126;
        for (int i = 0; i < initial.size(); i++) {
            screen.rewards.get(i).hidden = initial.get(i).hidden();
            screen.rewards.get(i).hidePopup = initial.get(i).hidePopup();
        }
        return screen;
    }

    private int rowHeight = 48;
    private int contentRows() { return tableMode ? (rewards.size() + 4) / 5 : rewards.size(); }
    private int rows() { return Math.max(1, (height - ROWS_TOP - 58) / rowHeight); }
    private int maxScroll() { return Math.max(0, (contentRows() - rows()) * rowHeight); }
    private int panelWidth() { return Math.max(1, Math.min(1320, width - 40)); }
    private int left() { return (width - panelWidth()) / 2; }
    private Viewport rowsViewport() {
        return new Viewport(left(), ROWS_TOP, left() + panelWidth(), ROWS_TOP + rows() * rowHeight);
    }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return rowWidgets.contains(widget) ? rowsViewport() : null;
    }
    private int thumbHeight() {
        return Math.max(10, rows() * rowHeight * rows() / Math.max(1, contentRows()));
    }
    private int thumbTop() {
        return ROWS_TOP + (rows() * rowHeight - thumbHeight())
                * scroll / Math.max(1, maxScroll());
    }

    private void scrollTo(double y) {
        motion.jump((y - ROWS_TOP - thumbOffset) * maxScroll()
                / Math.max(1, rows() * rowHeight - thumbHeight()));
        moveRows();
    }

    @Override
    protected void init() {
        clearWidgets();
        rowWidgets.clear();
        styles = ButtonStyleSheet.load();
        rowHeight = mailSaved != null ? 126 : tableMode
                ? Math.max(48, Math.min(64, (panelWidth() - 28) / 5))
                : 132;
        int x = left(), w = panelWidth();
        motion.limit(maxScroll());
        motion.jump(scroll);
        scroll = motion.pixels();
        if (!customNode) addRenderableWidget(new StyledChoiceButton<>(x, 40, Math.max(80, w - 66), 22,
                text("create.reward_mode"), List.of("default", "manual", "automatic"), mode,
                value -> text("reward_mode." + value), value -> mode = value, styles, "rewards-mode"))
                .active = pending == null;
        if (mailSaved == null) {
            var view = new EditorIconButton(x + w - 58, 40,
                    text(tableMode ? "rewards.view_list" : "rewards.view_table"),
                    () -> { tableMode = !tableMode; init(); },
                    styles, "rewards-view-mode", tableMode ? "list" : "grid");
            view.setTooltip(Tooltip.create(text(tableMode ? "rewards.view_list" : "rewards.view_table")));
            addRenderableWidget(view).active = pending == null;
        }
        button("rewards-add", "+", "add_rewards", x + w - 26, 40, 26, () -> {
            rewards.add(new Draft(new QuestDefinition.Reward("item", "minecraft:diamond", 1)));
            scroll = maxScroll();
            init();
        }).active = pending == null && rewards.size() < 128;
        if (tableMode) {
            int cellWidth = Math.max(1, (w - 16) / 5);
            int cellSize = Math.min(52, Math.max(36, cellWidth - 8));
            for (int index = 0; index < rewards.size(); index++) {
                int rewardIndex = index;
                int column = index % 5, row = index / 5;
                int bx = x + 8 + column * cellWidth + (cellWidth - cellSize) / 2;
                int by = ROWS_TOP + row * rowHeight - scroll;
                var tile = new StyledButton(bx, by, cellSize, cellSize, Component.empty(),
                        ignored -> editReward(rewardIndex), styles, "rewards-tile-" + index, "toolbar-button");
                tile.setTooltip(Tooltip.create(rewardTileTooltip(rewards.get(index))));
                addRenderableWidget(tile);
                rowWidgets.add(tile);
            }
        } else for (int row = 0; row < rewards.size(); row++) {
            int index = row;
            Draft draft = rewards.get(index);
            int y = ROWS_TOP + index * rowHeight - scroll;
            int firstWidget = children().size();
            int actionLeft = x + w - 58;
            int typeWidth = Math.max(90, Math.min(260, actionLeft - x - 42));
            addRenderableWidget(new StyledChoiceButton<>(x + 34, y + 8, typeWidth, 22,
                    text("rewards.type"), itemsOnly ? List.of("item")
                    : mailSaved != null ? QuestRewards.TYPES.stream()
                            .filter(type -> !type.equals("mail") && !type.equals("loot_choice")).toList()
                            : QuestRewards.TYPES.stream().filter(type -> !type.equals("loot_choice")
                            || draft.type.equals("loot_choice")).toList(),
                    draft.type, value -> text("reward_type." + value),
                    value -> {
                        draft.type = value;
                        draft.item = defaultTarget(value);
                        draft.count = "1";
                        draft.level = "1";
                        draft.particles = true;
                        draft.quality = "auto";
                        draft.background = "";
                        init();
                    }, styles, "rewards-type-" + index,
                    value -> icon(value, defaultTarget(value)))).active = pending == null;
            if (draft.type.equals("item")) {
                var picker = button("rewards-pick-" + index, "...", "item_picker.title", actionLeft, y + 8, 24,
                        () -> minecraft.setScreen(QuestScreen.pickResource(this, draft.item, true,
                                value -> draft.item = value)));
                picker.active &= pending == null;
            } else if (draft.type.equals("loot")) {
                var lootMode = new StyledChoiceButton<>(x + 34, y + 64, Math.min(190, w / 3), 20,
                        text("rewards.loot_mode"), List.of("default", "random", "choice"), draft.lootMode,
                        value -> text("rewards.loot_mode." + value), value -> {
                            draft.lootMode = value;
                            init();
                        }, styles, "rewards-loot-mode-" + index);
                lootMode.active = pending == null;
                lootMode.setTooltip(Tooltip.create(text("rewards.loot_mode_hint")));
                addRenderableWidget(lootMode);
                var pickLoot = button("rewards-pick-" + index, "...", "loot.title", actionLeft, y + 8, 24,
                        () -> minecraft.setScreen(new QuestLootScreen(this, draft.item,
                                value -> {
                                    draft.item = value;
                                    init();
                                })));
                pickLoot.active &= pending == null;
            } else if (draft.type.equals("mail")) {
                button("rewards-pick-" + index, "...", "mailbox.template_pick", actionLeft, y + 8, 24,
                        () -> {
                            ClientMailbox.requestTemplates();
                            minecraft.setScreen(new QuestValuePickerScreen(this,
                                    text("mailbox.template_pick").getString(), "mail_templates",
                                    () -> ClientMailbox.templates().stream()
                                            .map(mail -> new QuestValuePickerScreen.Option(
                                                    mail.id(), mail.subject())).toList(),
                                    draft.item, value -> { draft.item = value; init(); }));
                        });
            } else {
                if (draft.type.equals("unlock") || draft.type.equals("lock")) {
                    button("rewards-pick-" + index, "\u2637", "task_picker.title", actionLeft, y + 8, 24,
                            () -> minecraft.setScreen(new QuestTaskPickerScreen(this, draft.item, questId,
                                    ids -> {
                                        draft.item = String.join(", ", ids);
                                        init();
                                    })));
                } else if (draft.type.equals("command") || draft.type.equals("function")
                        || draft.type.equals("script")) {
                    int editX = actionLeft;
                    if (draft.type.equals("function") || draft.type.equals("script")) {
                        var folder = new EditorIconButton(actionLeft - 28, y + 8,
                                text("rewards.reward_open_folder"), () -> openRewardFolder(draft),
                                styles, "rewards-open-folder-" + index, "folder");
                        folder.active = pending == null;
                        folder.setTooltip(Tooltip.create(text("rewards.reward_open_folder_hint")));
                        addRenderableWidget(folder);
                        editX -= 28;
                    }
                    var edit = new EditorIconButton(editX, y + 8,
                            text("rewards.edit_source"), () -> editSource(draft),
                            styles, "rewards-edit-source-" + index, "edit");
                    edit.active = pending == null;
                    addRenderableWidget(edit);
                }
            }
            button("rewards-remove-" + index, "\u00d7", "delete", x + w - 28, y + 8, 24, () -> {
                rewards.remove(index);
                init();
            });
            int countWidth = Math.max(68, Math.min(100, w / 8));
            int countX = x + w - countWidth - 40;
            int targetX = x + 34;
            int targetWidth = Math.max(48, countX - targetX - 8);
            var item = new SuggestedEditBox(this, font, targetX, y + 36, targetWidth, 22,
                    text("rewards.target"), draft.type);
            if (draft.type.equals("item") || draft.type.equals("unlock") || draft.type.equals("lock")) item.withoutMenu();
            item.setMaxLength(8192);
            item.setValue(draft.item);
            item.setResponder(value -> draft.item = value);
            item.setEditable(pending == null && !sourceReward(draft.type));
            addRenderableWidget(item);
            EditBox count = new SuggestedEditBox(this, font, countX, y + 36, countWidth, 22,
                    text("create.reward_count"), "count");
            count.setMaxLength(7);
            count.setValue(draft.count);
            count.setResponder(value -> draft.count = value);
            count.setEditable(pending == null);
            addRenderableWidget(count);
            if (draft.type.equals("effect")) {
                var level = new SuggestedEditBox(this, font, x + 34, y + 64, 76, 20,
                        text("rewards.effect_level"), "count");
                level.setHint(text("rewards.effect_level"));
                level.setTooltip(Tooltip.create(text("rewards.effect_level")));
                level.setMaxLength(3);
                level.setValue(draft.level);
                level.setResponder(value -> draft.level = value);
                addRenderableWidget(level);
                addRenderableWidget(Checkbox.builder(text("rewards.effect_particles"), font)
                        .pos(x + 122, y + 64).selected(draft.particles)
                        .onValueChange((box, value) -> draft.particles = value).build());
            }
            if (draft.type.equals("item")) {
                int qualityWidth = qualityWidth(w);
                addRenderableWidget(new StyledChoiceButton<>(x + 34, y + 64, qualityWidth, 20,
                        text("rewards.quality"), QuestRewardQuality.VALUES, draft.quality,
                        value -> text("rewards.quality." + (value.isBlank() ? "auto" : value)),
                        value -> {
                            draft.quality = value;
                            draft.background = "";
                            init();
                        }, styles, "rewards-quality-" + index)).active = pending == null;
                if (mailSaved == null) {
                    int colorX = colorX(x, w);
                    int colorWidth = Math.max(60, x + w - 40 - colorX);
                    var background = new SuggestedEditBox(this, font, colorX, y + 64,
                            Math.max(44, colorWidth - 16), 20, text("rewards.background"), "color");
                    background.setMaxLength(7);
                    background.setHint(text("rewards.background"));
                    background.setValue(draft.background);
                    background.setTooltip(Tooltip.create(text("rewards.background")));
                    background.setResponder(value -> draft.background = value);
                    background.setEditable(pending == null);
                    addRenderableWidget(background);
                }
            }
            if (mailSaved != null) {
                addRenderableWidget(Checkbox.builder(text("mailbox.reward_visible"), font)
                        .pos(x + 34, y + 94)
                        .maxWidth((w - 54) / 2).selected(!draft.hidden)
                        .onValueChange((box, value) -> { draft.hidden = !value; init(); }).build());
                var popup = addRenderableWidget(Checkbox.builder(text("mailbox.reward_popup"), font)
                        .pos(x + w / 2, y + 94)
                        .maxWidth((w - 54) / 2)
                        .selected(!draft.hidden && !draft.hidePopup && (draft.type.equals("item") || draft.type.equals("loot")))
                        .onValueChange((box, value) -> draft.hidePopup = !value).build());
                popup.active = !draft.hidden && (draft.type.equals("item") || draft.type.equals("loot"));
            } else {
                var displayed = Checkbox.builder(text("rewards.displayed"), font)
                        .pos(x + 34, y + 92).selected(draft.displayed)
                        .onValueChange((box, value) -> { draft.displayed = value; }).build();
                displayed.active = pending == null;
                displayed.setTooltip(Tooltip.create(text("rewards.displayed_hint")));
                addRenderableWidget(displayed);
                var claimMode = new StyledChoiceButton<>(x + Math.min(310, w / 3), y + 90,
                        Math.max(190, Math.min(260, w / 3)), 20, text("rewards.claim_mode"),
                        List.of("default", "manual", "automatic"), draft.claimMode,
                        value -> text("rewards.claim_mode." + value),
                        value -> draft.claimMode = value, styles, "rewards-claim-mode-" + index);
                claimMode.active = pending == null;
                claimMode.setTooltip(Tooltip.create(text("rewards.claim_mode_hint")));
                addRenderableWidget(claimMode);
            }
            children().subList(firstWidget, children().size()).stream()
                    .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).forEach(rowWidgets::add);
        }
        var save = new StyledButton(x, height - 30, (w - 6) / 2, 22,
                text(pending == null ? "save" : "saving"), ignored -> save(), styles, "rewards-save");
        save.active = pending == null;
        addRenderableWidget(save);
        var cancel = new StyledButton(x + (w + 6) / 2, height - 30, (w - 6) / 2, 22,
                text("cancel"), ignored -> onClose(), styles, "rewards-cancel");
        cancel.active = pending == null;
        addRenderableWidget(cancel);
        if (editingReward >= 0 && editDraft != null) initRewardDialog();
    }

    private Component rewardTileTooltip(Draft draft) {
        String target = draft.item.isBlank() ? QuestRewardsScreen.icon(draft.type, draft.item) : draft.item;
        return Component.literal(QuestIcons.displayName(target) + " ×" + draft.count);
    }

    private void editReward(int index) {
        if (pending != null || index < 0 || index >= rewards.size()) return;
        editingReward = index;
        editDraft = new Draft(rewards.get(index));
        init();
    }

    private int dialogWidth() { return Math.min(560, width - 32); }
    private int dialogHeight() { return Math.min(330, height - 32); }
    private int dialogLeft() { return (width - dialogWidth()) / 2; }
    private int dialogTop() { return (height - dialogHeight()) / 2; }

    private void initRewardDialog() {
        int x = dialogLeft() + 18, y = dialogTop() + 48, w = dialogWidth() - 36;
        var type = new StyledChoiceButton<>(x, y, Math.min(220, w - 4), 22,
                text("rewards.type"), itemsOnly ? List.of("item")
                        : QuestRewards.TYPES.stream().filter(rewardType -> !rewardType.equals("loot_choice")
                        || editDraft.type.equals("loot_choice")).toList(),
                editDraft.type, value -> text("reward_type." + value), value -> {
                    editDraft.type = value;
                    editDraft.item = defaultTarget(value);
                    editDraft.count = "1";
                    init();
                }, styles, "rewards-dialog-type");
        addRenderableWidget(type);
        y += 30;

        if (editDraft.type.equals("item")) {
            addRenderableWidget(new StyledButton(x, y, w - 94, 22,
                    Component.literal(QuestIcons.displayName(editDraft.item)),
                    ignored -> minecraft.setScreen(QuestScreen.pickResource(this, editDraft.item, true,
                            value -> { editDraft.item = value; init(); })),
                    styles, "rewards-dialog-item", "toolbar-button").leadingIcon(editDraft.item));
            var count = dialogField(editDraft.count, x + w - 86, y, 86, 22, "create.reward_count");
            count.setMaxLength(7);
            count.setResponder(value -> editDraft.count = value);
            addRenderableWidget(count);
            y += 30;
            var quality = new StyledChoiceButton<>(x, y, Math.min(180, w / 2 - 4), 20,
                    text("rewards.quality"), QuestRewardQuality.VALUES, editDraft.quality,
                    value -> text("rewards.quality." + (value.isBlank() ? "auto" : value)),
                    value -> editDraft.quality = value, styles, "rewards-dialog-quality");
            addRenderableWidget(quality);
            if (!editDraft.background.isBlank()) {
                var background = dialogField(editDraft.background, x + w / 2, y, w / 2, 20,
                        "rewards.background");
                background.setResponder(value -> editDraft.background = value);
                addRenderableWidget(background);
            }
        } else if (editDraft.type.equals("loot")) {
            var target = dialogField(editDraft.item, x, y, w - 38, 22, "rewards.target");
            target.setEditable(false);
            addRenderableWidget(target);
            button("rewards-dialog-loot-pick", "...", "loot.title", x + w - 32, y, 32,
                    () -> minecraft.setScreen(new QuestLootScreen(this, editDraft.item, value -> {
                        editDraft.item = value;
                        init();
                    })));
            y += 30;
            var lootMode = new StyledChoiceButton<>(x, y, Math.min(220, w - 90), 20,
                    text("rewards.loot_mode"), List.of("default", "random", "choice"), editDraft.lootMode,
                    value -> text("rewards.loot_mode." + value),
                    value -> editDraft.lootMode = value, styles, "rewards-dialog-loot-mode");
            lootMode.setTooltip(Tooltip.create(text("rewards.loot_mode_hint")));
            addRenderableWidget(lootMode);
            var count = dialogField(editDraft.count, x + w - 82, y, 82, 20, "create.reward_count");
            count.setResponder(value -> editDraft.count = value);
            addRenderableWidget(count);
        } else {
            var target = dialogField(editDraft.item, x, y, w, 22, "rewards.target");
            target.setResponder(value -> editDraft.item = value);
            addRenderableWidget(target);
            y += 30;
            var count = dialogField(editDraft.count, x, y, 86, 20, "create.reward_count");
            count.setResponder(value -> editDraft.count = value);
            addRenderableWidget(count);
        }
        int footerY = dialogTop() + dialogHeight() - 32;
        addRenderableWidget(new StyledButton(x, footerY, (w - 6) / 2, 22,
                text("cancel"), ignored -> closeRewardDialog(), styles, "rewards-dialog-cancel"));
        addRenderableWidget(new StyledButton(x + (w + 6) / 2, footerY, (w - 6) / 2, 22,
                text("apply"), ignored -> {
                    if (editingReward >= 0 && editingReward < rewards.size())
                        rewards.set(editingReward, new Draft(editDraft));
                    closeRewardDialog();
                }, styles, "rewards-dialog-apply"));
    }

    private EditBox dialogField(String value, int x, int y, int w, int h, String label) {
        var field = new SuggestedEditBox(this, font, x, y, w, h, text(label), "reward-dialog");
        field.withoutMenu();
        field.setMaxLength(8192);
        field.setValue(value == null ? "" : value);
        return field;
    }

    private void closeRewardDialog() {
        editingReward = -1;
        editDraft = null;
        init();
    }

    private StyledButton button(String id, String symbol, String tooltip, int x, int y, int w, Runnable action) {
        var button = new StyledButton(x, y, w, 20, Component.literal(symbol),
                ignored -> action.run(), styles, id);
        button.setTooltip(Tooltip.create(text(tooltip)));
        button.active = pending == null;
        return addRenderableWidget(button);
    }

    private static boolean sourceReward(String type) {
        return type.equals("command") || type.equals("function") || type.equals("script");
    }

    private static int qualityWidth(int panelWidth) { return Math.min(136, Math.max(112, panelWidth / 3)); }
    private static int colorX(int left, int panelWidth) { return left + 34 + qualityWidth(panelWidth) + 10; }

    private void editSource(Draft draft) {
        try {
            if (draft.type.equals("command")) {
                minecraft.setScreen(new QuestRewardTextEditorScreen(this,
                        text("reward_type.command"), draft.item, null,
                        value -> CompletableFuture.completedFuture(
                                !value.isBlank() && !value.contains("\n") && !value.contains("\r")),
                        value -> {
                            draft.item = value;
                            init();
                        }));
                return;
            }
            if (draft.type.equals("script")) {
                var server = minecraft.getSingleplayerServer();
                var player = minecraft.player;
                if (server == null || player == null) {
                    error = "create.error.permission";
                    return;
                }
                minecraft.setScreen(new QuestScriptFileScreen(this, draft.item,
                        xtdpotato.xero_quest_journal.core.model.QuestScriptReference.Kind.REWARD_FILE));
                return;
            }
            ResourceLocation id = ResourceLocation.tryParse(draft.item);
            var server = minecraft.getSingleplayerServer();
            if (id == null || server == null) {
                error = "create.error.reward";
                return;
            }
            Path file = findFunctionFile(server.getWorldPath(LevelResource.DATAPACK_DIR), id);
            if (file == null || !Files.isRegularFile(file)) {
                error = "create.error.reward";
                return;
            }
            String source = Files.readString(file);
            minecraft.setScreen(new QuestRewardTextEditorScreen(this,
                    text("reward_type.function"), source, file,
                    value -> CompletableFuture.supplyAsync(() -> {
                                try {
                                    AtomicFiles.write(file, value, true);
                                    return true;
                                } catch (Exception failure) {
                                    return false;
                                }
                            }, server)
                            .thenCompose(written -> written
                                    ? server.reloadResources(server.getPackRepository().getSelectedIds())
                                    .thenApply(ignored -> true)
                                    : CompletableFuture.completedFuture(false)),
                    value -> init()));
        } catch (Exception failure) {
            error = "create.error.reward";
        }
    }

    private static Path findFunctionFile(Path datapacks, ResourceLocation id) {
        if (datapacks == null || !Files.isDirectory(datapacks)) return null;
        String suffix = "/data/" + id.getNamespace() + "/function/" + id.getPath() + ".mcfunction";
        try (var paths = Files.walk(datapacks)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        Path normalized = path.toAbsolutePath().normalize();
                        String value = normalized.toString().replace('\\', '/');
                        return value.endsWith(suffix);
                    })
                    .findFirst().orElse(null);
        } catch (Exception failure) {
            return null;
        }
    }

    private void openRewardFolder(Draft draft) {
        try {
            Path folder;
            if (draft.type.equals("script")) {
                Path root = QuestConfig.root().resolve("scripts").toAbsolutePath().normalize();
                Path script = root.resolve(draft.item).normalize();
                if (!script.startsWith(root)) {
                    error = "create.error.reward";
                    return;
                }
                folder = script.getParent() == null ? root : script.getParent();
                Files.createDirectories(folder);
            } else {
                var server = minecraft.getSingleplayerServer();
                ResourceLocation id = ResourceLocation.tryParse(draft.item);
                if (server == null || id == null) {
                    error = "create.error.reward";
                    return;
                }
                Path datapacks = server.getWorldPath(LevelResource.DATAPACK_DIR);
                Path file = findFunctionFile(datapacks, id);
                if (file != null) folder = file.toAbsolutePath().normalize().getParent();
                else {
                    folder = datapacks.resolve("xero_quest_journal/data")
                            .resolve(id.getNamespace()).resolve("function");
                    Path parentPath = Path.of(id.getPath()).getParent();
                    if (parentPath != null) folder = folder.resolve(parentPath);
                    folder = folder.toAbsolutePath().normalize();
                    Files.createDirectories(folder);
                }
            }
            Util.getPlatform().openFile(folder.toFile());
        } catch (Exception failure) {
            error = "create.error.save";
        }
    }

    private void save() {
        if (pending != null) return;
        if (minecraft.player == null || !minecraft.player.hasPermissions(2)
                || draftSaved == null && minecraft.getSingleplayerServer() == null) {
            error = "create.error.permission";
            return;
        }
        try {
            List<QuestDefinition.Reward> values = rewards.stream().map(draft -> draft.value()).toList();
            if (values.stream().anyMatch(reward -> !QuestRewards.valid(reward))) {
                error = "create.error.reward";
                return;
            }
            if (draftSaved != null) {
                if (mailSaved != null) mailSaved.accept(rewards.stream().map(draft -> {
                    var reward = draft.value();
                    return new MailboxRuntime.Attachment(reward.type(), reward.item(),
                            reward.count(), false, draft.hidden, draft.hidePopup,
                            reward.quality(), reward.background());
                }).toList());
                else draftSaved.accept(values, mode);
                minecraft.setScreen(parent);
                return;
            }
            var server = minecraft.getSingleplayerServer();
            var player = minecraft.player.getUUID();
            String rewardMode = mode;
            pending = CompletableFuture.supplyAsync(() -> {
                String hash = expectedHash;
                try {
                    String latestSource = QuestRepository.source(questId);
                    String latestHash = QuestRepository.fingerprint(latestSource);
                    if (!hash.equals(latestHash)) {
                        var latest = QuestRepository.parseDefinition(QuestRepository.fileOf(questId), latestSource);
                        if (!latest.rewards().equals(originalRewards) || !latest.rewardMode().equals(originalMode))
                            return new QuestAuthoring.Result("", "screen.xero_quest_journal.create.error.conflict");
                        hash = latestHash;
                    }
                } catch (Exception failure) {
                    return new QuestAuthoring.Result("", "screen.xero_quest_journal.create.error.save");
                }
                return QuestAuthoring.editRewards(server.getPlayerList().getPlayer(player), questId, hash, values, rewardMode);
            }, server);
            error = null;
            init();
        } catch (IllegalArgumentException invalid) {
            error = "create.error.reward";
        }
    }

    @Override
    public void tick() {
        if (pending == null || !pending.isDone()) return;
        QuestAuthoring.Result result;
        try {
            result = pending.join();
        } catch (Exception failure) {
            result = new QuestAuthoring.Result("", "screen.xero_quest_journal.create.error.save");
        }
        pending = null;
        if (result.successful()) {
            minecraft.setScreen(parent);
        } else {
            error = result.error().replace("screen.xero_quest_journal.", "");
            init();
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (editingReward >= 0) return true;
        if (pending == null && rowsViewport().contains(x, y)) {
            motion.wheel(vertical * rowHeight);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (clickChoicePopup(x, y, button)) return true;
        if (editingReward >= 0) {
            if (!dialogBounds().contains(x, y)) {
                if (button == 0) closeRewardDialog();
                return true;
            }
            return super.mouseClicked(x, y, button);
        }
        if (button == 0 && pending == null && contentRows() > rows()
                && x >= left() + panelWidth() && x < left() + panelWidth() + 9
                && y >= ROWS_TOP && y < ROWS_TOP + rows() * rowHeight) {
            scrollbarDragging = true;
            thumbOffset = y >= thumbTop() && y < thumbTop() + thumbHeight()
                    ? y - thumbTop() : thumbHeight() / 2.0;
            scrollTo(y);
            return true;
        }
        if (button == 0 && pending == null && rowsViewport().contains(x, y)
                && !(getChildAt(x, y).orElse(null) instanceof EditBox)) {
            listDragging = true;
            listMoved = false;
            listStartX = x; listStartY = y; listStartScroll = motion.position();
            motion.freeze();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragChoicePopup(x, y, button, dx, dy)) return true;
        if (editingReward >= 0) return super.mouseDragged(x, y, button, dx, dy);
        if (button == 0 && listDragging) {
            listMoved |= Math.abs(y - listStartY) >= 4;
            if (listMoved) { motion.jump(listStartScroll + listStartY - y); moveRows(); }
            return true;
        }
        if (button == 0 && scrollbarDragging) {
            scrollTo(y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (releaseChoicePopup(x, y, button)) return true;
        if (editingReward >= 0) return super.mouseReleased(x, y, button);
        if (button == 0 && listDragging) {
            listDragging = false;
            if (!listMoved && rowsViewport().contains(x, y) && Math.abs(x - listStartX) < 4) {
                super.mouseClicked(listStartX, listStartY, button);
                super.mouseReleased(x, y, button);
            }
            return true;
        }
        if (button == 0 && scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    protected void renderContents(GuiGraphics g, int x, int y, float tick) {
        if (hasChoicePopup()) motion.freeze();
        motion.advance();
        moveRows();
        super.renderContents(g, x, y, tick);
        int left = left(), w = panelWidth();
        if (mailSaved == null) {
            int colorX = colorX(left, w), colorWidth = Math.max(60, left + w - 40 - colorX);
            g.enableScissor(rowsViewport().left(), rowsViewport().top(), rowsViewport().right(), rowsViewport().bottom());
            int first = tableMode ? scroll / rowHeight * 5 : scroll / rowHeight;
            int end = Math.min(rewards.size(), tableMode
                    ? (scroll / rowHeight + rows() + 1) * 5
                    : scroll / rowHeight + rows() + 1);
            for (int row = first; row < end; row++) {
                var draft = rewards.get(row);
                if (tableMode || !draft.type.equals("item") || draft.background.isBlank()) continue;
                int top = ROWS_TOP + row * rowHeight - scroll + 64;
                int swatch = Math.max(8, Math.min(12, colorWidth - 4));
                int color;
                try { color = 0xFF000000 | Integer.parseInt(draft.background.strip().substring(1), 16); }
                catch (RuntimeException invalid) { color = 0xFF555963; }
                g.fill(colorX + colorWidth - swatch, top + 5, colorX + colorWidth - 2, top + 5 + swatch, color);
                g.renderOutline(colorX + colorWidth - swatch, top + 5, swatch, swatch, 0xFFD8DDE5);
            }
            g.disableScissor();
        }
        g.drawString(font, title, left + 12, 12, RewardEditorTheme.TEXT);
        g.drawString(font, text(customNode ? "rewards.scope_graph" : "rewards.scope_task"),
                left + 12, 72, RewardEditorTheme.MUTED);
        if (!tableMode) {
            g.drawString(font, text("rewards.target"), left + 34, ROWS_TOP - 17, RewardEditorTheme.MUTED);
            g.drawString(font, text("create.count"), left + w - 142, ROWS_TOP - 17, RewardEditorTheme.MUTED);
        }
        g.enableScissor(rowsViewport().left(), rowsViewport().top(), rowsViewport().right(), rowsViewport().bottom());
        if (tableMode) {
            int cellWidth = Math.max(1, (w - 16) / 5);
            int cellSize = Math.min(52, Math.max(36, cellWidth - 8));
            int firstRow = scroll / rowHeight;
            for (int row = firstRow; row < Math.min(contentRows(), firstRow + rows() + 1); row++) {
                int rowY = ROWS_TOP + row * rowHeight - scroll;
                for (int column = 0; column < 5; column++) {
                    int index = row * 5 + column;
                    if (index >= rewards.size()) break;
                    Draft draft = rewards.get(index);
                    int cellX = left + 8 + column * cellWidth + (cellWidth - cellSize) / 2;
                    int iconSize = Math.min(24, cellSize - 8);
                    QuestIcons.render(g, icon(draft.type, draft.item),
                            cellX + (cellSize - iconSize) / 2, rowY + 5, iconSize);
                    if (!draft.count.equals("1"))
                        g.drawString(font, draft.count, cellX + cellSize - font.width(draft.count) - 3,
                                rowY + cellSize - font.lineHeight, RewardEditorTheme.TEXT);
                }
            }
        } else {
            for (int row = scroll / rowHeight; row < Math.min(rewards.size(), scroll / rowHeight + rows() + 1); row++) {
                int rowY = ROWS_TOP + row * rowHeight - scroll;
                g.drawString(font, Component.literal(String.format(java.util.Locale.ROOT, "%02d", row + 1)),
                        left + 12, rowY + rowHeight - 16, RewardEditorTheme.MUTED);
            }
        }
        if (rewards.isEmpty()) g.drawString(font, text("rewards.empty"), left + 14, ROWS_TOP + 16,
                RewardEditorTheme.MUTED);
        g.disableScissor();
        if (contentRows() > rows()) {
            int track = rows() * rowHeight;
            g.fill(left + w + 3, ROWS_TOP, left + w + 6, ROWS_TOP + track, 0xFF303741);
            g.fill(left + w + 3, thumbTop(), left + w + 6, thumbTop() + thumbHeight(), 0xFF9DB4D2);
        }
        if (error != null) g.drawString(font, font.plainSubstrByWidth(text(error).getString(), w),
                left, height - 46, 0xFFFF7777);
    }
    private void moveRows() {
        int next = motion.pixels(), delta = scroll - next;
        rowWidgets.forEach(widget -> widget.setY(widget.getY() + delta));
        scroll = next;
    }

    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float tick) {
        backdrop(g, 0xF0181D23);
        int left = left(), width = panelWidth();
        RewardEditorTheme.panel(g, left, 4, width, 84,
                customNode ? RewardEditorTheme.GOLD : RewardEditorTheme.GREEN);
        var viewport = rowsViewport();
        int first = tableMode ? scroll / rowHeight * 5 : scroll / rowHeight;
        int end = Math.min(rewards.size(), tableMode
                ? (scroll / rowHeight + rows() + 1) * 5
                : scroll / rowHeight + rows() + 1);
        for (int row = first; row < end; row++) {
            int top = ROWS_TOP + row * rowHeight - scroll + 3;
            int visualRow = tableMode ? row / 5 : row;
            top = ROWS_TOP + visualRow * rowHeight - scroll + 3;
            if (top + rowHeight - 6 < viewport.top() || top > viewport.bottom()) continue;
            Draft draft = rewards.get(row);
            if (tableMode) {
                int cellWidth = Math.max(1, (width - 16) / 5);
                int cellSize = Math.min(52, Math.max(36, cellWidth - 8));
                int cellX = left + 8 + row % 5 * cellWidth + (cellWidth - cellSize) / 2;
                RewardEditorTheme.entry(g, cellX, top, cellSize, rowHeight - 6, draft.type, true, false);
            } else RewardEditorTheme.entry(g, left, top, width, rowHeight - 6,
                    draft.type, true, false);
        }
    }

    private Viewport dialogBounds() {
        return viewport(dialogLeft() - 4, dialogTop() - 4,
                dialogLeft() + dialogWidth() + 4, dialogTop() + dialogHeight() + 4);
    }
    @Override protected Object popupKey() { return editingReward < 0 ? null : editDraft; }
    @Override protected Viewport popupBounds() { return editingReward < 0 ? super.popupBounds() : dialogBounds(); }
    @Override protected int popupScrimColor() { return 0x8013161D; }

    @Override public void renderPopupBase(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderContents(g, mouseX, mouseY, delta);
    }
    @Override protected void renderPopupSurface(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderPopupSurface(g, mouseX, mouseY, delta);
        g.fill(dialogLeft(), dialogTop(), dialogLeft() + dialogWidth(), dialogTop() + dialogHeight(), 0xFF242A32);
        g.fill(dialogLeft(), dialogTop(), dialogLeft() + dialogWidth(), dialogTop() + 2, RewardEditorTheme.GREEN);
        g.renderOutline(dialogLeft(), dialogTop(), dialogWidth(), dialogHeight(), 0xFF91A6B8);
        g.drawString(font, text("rewards.edit_item"), dialogLeft() + 14, dialogTop() + 14,
                RewardEditorTheme.TEXT);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (editingReward >= 0 && key == GLFW.GLFW_KEY_ESCAPE) {
            closeRewardDialog();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() {
        if (editingReward >= 0) closeRewardDialog();
        else if (pending == null) closeTo(parent);
    }
    @Override public boolean isPauseScreen() { return false; }

    private static final class Draft {
        String type;
        String item;
        String count;
        String level = "1";
        String quality = "";
        String background = "";
        boolean particles = true;
        boolean hidden;
        boolean hidePopup;
        boolean displayed = true;
        String claimMode = "default";
        String lootMode = "default";
        Draft(Draft other) {
            type = other.type;
            item = other.item;
            count = other.count;
            level = other.level;
            quality = other.quality;
            background = other.background;
            particles = other.particles;
            hidden = other.hidden;
            hidePopup = other.hidePopup;
            displayed = other.displayed;
            claimMode = other.claimMode;
            lootMode = other.lootMode;
        }
        Draft(QuestDefinition.Reward reward) {
            type = reward.type();
            item = reward.item();
            if (type.equals("loot")) {
                if (item.startsWith("random:")) {
                    lootMode = "random";
                    item = item.substring("random:".length());
                } else if (item.startsWith("choice:")) {
                    lootMode = "choice";
                    item = item.substring("choice:".length());
                }
            }
            count = Integer.toString(reward.count());
            quality = reward.quality().isBlank() ? "auto" : reward.quality();
            background = reward.background();
            displayed = reward.displayed();
            claimMode = reward.claimMode();
            if (type.equals("effect")) {
                var effect = RewardEffect.parse(item);
                if (effect != null) {
                    item = effect.id();
                    level = Integer.toString(effect.level());
                    particles = effect.particles();
                }
            }
        }
        QuestDefinition.Reward value() {
            String target = item.strip();
            if (type.equals("effect")) {
                target = new RewardEffect(target, Integer.parseInt(level.strip()), particles).encoded();
            } else if (type.equals("loot")) target = QuestRewards.lootTarget(target, lootMode);
            return new QuestDefinition.Reward(type, target, Integer.parseInt(count.strip()),
                    type.equals("item") ? quality : "", type.equals("item") ? background : "",
                    displayed, claimMode);
        }
    }

    private static String defaultTarget(String type) {
        return switch (type) {
            case "item" -> "minecraft:diamond";
            case "loot" -> "";
            case "effect" -> "minecraft:speed";
            case "remove_effect" -> "minecraft:speed";
            case "script" -> "reward.js";
            default -> "";
        };
    }

    public static String icon(String type, String target) {
        return switch (type) {
            case "item" -> target.isBlank() ? "minecraft:diamond" : target;
            case "loot" -> ClientQuestDefinitions.lootDisplayIcon(target);
            case "loot_choice" -> QuestRewards.choiceDisplayIcon(target);
            case "mail" -> "minecraft:written_book";
            case "experience", "levels" -> "minecraft:experience_bottle";
            case "command", "function", "script" -> "minecraft:command_block";
            case "effect" -> "minecraft:potion";
            case "remove_effect" -> "minecraft:milk_bucket";
            case "unlock" -> "minecraft:tripwire_hook";
            case "lock" -> "minecraft:iron_door";
            case "complete" -> "minecraft:emerald";
            default -> "minecraft:paper";
        };
    }
}
