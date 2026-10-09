package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestRewardQuality;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.runtime.QuestRewards;
import xtdpotato.xero_quest_journal.core.runtime.RewardEffect;
import xtdpotato.xero_quest_journal.core.runtime.QuestScripts;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Reward-only drafts never replace task conditions or completion snapshots. */
public final class QuestRewardsScreen extends AnimatedQuestScreen {
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
                .map(a -> new QuestDefinition.Reward(a.type(), a.item(), a.count())).toList(),
                "manual", (values, mode) -> {}, true);
        screen.mailSaved = saved;
        screen.rowHeight = 72;
        for (int i = 0; i < initial.size(); i++) {
            screen.rewards.get(i).hidden = initial.get(i).hidden();
            screen.rewards.get(i).hidePopup = initial.get(i).hidePopup();
        }
        return screen;
    }

    private int rowHeight = 48;
    private int rows() { return Math.max(1, (height - 132) / rowHeight); }
    private int panelWidth() { return Math.min(480, width - 24); }
    private int left() { return (width - panelWidth()) / 2; }
    private Viewport rowsViewport() { return new Viewport(left(), 74, left() + panelWidth(), 74 + rows() * rowHeight); }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return rowWidgets.contains(widget) ? rowsViewport() : null;
    }
    private int thumbHeight() { return Math.max(10, rows() * rowHeight * rows() / Math.max(1, rewards.size())); }
    private int thumbTop() {
        return 74 + (rows() * rowHeight - thumbHeight()) * scroll / Math.max(1, (rewards.size() - rows()) * rowHeight);
    }

    private void scrollTo(double y) {
        motion.jump((y - 74 - thumbOffset) * Math.max(0, (rewards.size() - rows()) * rowHeight)
                / Math.max(1, rows() * rowHeight - thumbHeight()));
        moveRows();
    }

    @Override
    protected void init() {
        clearWidgets();
        rowWidgets.clear();
        styles = ButtonStyleSheet.load();
        rowHeight = rewards.stream().anyMatch(d -> d.type.equals("effect"))
                ? (mailSaved == null ? 72 : 96)
                : mailSaved == null && rewards.stream().anyMatch(d -> d.type.equals("item")) ? 72 : 48;
        if (mailSaved != null) rowHeight = Math.max(rowHeight, 72);
        int x = left(), w = panelWidth();
        motion.limit((rewards.size() - rows()) * rowHeight);
        motion.jump(scroll);
        scroll = motion.pixels();
        if (!customNode) addRenderableWidget(new StyledChoiceButton<>(x, 32, Math.max(100, w - 30), 20,
                text("create.reward_mode"), List.of("default", "manual", "automatic"), mode,
                value -> text("reward_mode." + value), value -> mode = value, styles, "rewards-mode")).active = pending == null;
        button("rewards-add", "+", "add_rewards", x + w - 24, 32, 24, () -> {
            rewards.add(new Draft(new QuestDefinition.Reward("item", "minecraft:diamond", 1)));
            scroll = Math.max(0, rewards.size() - rows()) * rowHeight;
            init();
        }).active = pending == null && rewards.size() < 128;
        for (int row = 0; row < rewards.size(); row++) {
            int index = row;
            Draft draft = rewards.get(index);
            int y = 74 + index * rowHeight - scroll;
            int firstWidget = children().size();
            addRenderableWidget(new StyledChoiceButton<>(x + 24, y, Math.max(80, w - 84), 20,
                    text("rewards.type"), itemsOnly ? List.of("item")
                            : mailSaved != null ? QuestRewards.TYPES.stream().filter(type -> !type.equals("mail")).toList()
                            : QuestRewards.TYPES, draft.type, value -> text("reward_type." + value),
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
                var picker = button("rewards-pick-" + index, "...", "item_picker.title", x + w - 54, y, 24,
                        () -> minecraft.setScreen(QuestScreen.pickResource(this, draft.item, true,
                                value -> draft.item = value)));
                picker.active &= pending == null;
            } else if (draft.type.equals("loot")) {
                button("rewards-pick-" + index, "...", "loot.title", x + w - 54, y, 24,
                        () -> minecraft.setScreen(new QuestLootScreen(this, draft.item,
                                value -> {
                                    draft.item = value;
                                    init();
                                })));
            } else if (draft.type.equals("mail")) {
                button("rewards-pick-" + index, "...", "mailbox.template_pick", x + w - 54, y, 24,
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
                    button("rewards-pick-" + index, "\u2637", "task_picker.title", x + w - 54, y, 24,
                            () -> minecraft.setScreen(new QuestTaskPickerScreen(this, draft.item, questId,
                                    ids -> {
                                        draft.item = String.join(", ", ids);
                                        init();
                                    })));
                } else if (draft.type.equals("command") || draft.type.equals("function")
                        || draft.type.equals("script")) {
                    var edit = new EditorIconButton(x + w - 54, y,
                            text("rewards.edit_source"), () -> editSource(draft),
                            styles, "rewards-edit-source-" + index, "edit");
                    edit.active = pending == null;
                    addRenderableWidget(edit);
                }
            }
            var item = new SuggestedEditBox(this, font, x + 24, y + 22, Math.max(32, w - 120), 20,
                    text("rewards.target"), draft.type);
            if (draft.type.equals("item") || draft.type.equals("unlock") || draft.type.equals("lock")) item.withoutMenu();
            item.setMaxLength(8192);
            item.setValue(draft.item);
            item.setResponder(value -> draft.item = value);
            item.setEditable(pending == null && !sourceReward(draft.type));
            addRenderableWidget(item);
            EditBox count = new SuggestedEditBox(this, font, x + w - 90, y + 22, 60, 20, text("create.reward_count"), "count");
            count.setMaxLength(7);
            count.setValue(draft.count);
            count.setResponder(value -> draft.count = value);
            count.setEditable(pending == null);
            addRenderableWidget(count);
            if (draft.type.equals("effect")) {
                var level = new SuggestedEditBox(this, font, x + 24, y + 46, 65, 20,
                        text("rewards.effect_level"), "count");
                level.setHint(text("rewards.effect_level"));
                level.setTooltip(Tooltip.create(text("rewards.effect_level")));
                level.setMaxLength(3);
                level.setValue(draft.level);
                level.setResponder(value -> draft.level = value);
                addRenderableWidget(level);
                addRenderableWidget(Checkbox.builder(text("rewards.effect_particles"), font)
                        .pos(x + 104, y + 46).selected(draft.particles)
                        .onValueChange((box, value) -> draft.particles = value).build());
            }
            if (draft.type.equals("item") && mailSaved == null) {
                int qualityWidth = qualityWidth(w);
                addRenderableWidget(new StyledChoiceButton<>(x + 24, y + 44, qualityWidth, 20,
                        text("rewards.quality"), QuestRewardQuality.VALUES, draft.quality,
                        value -> text("rewards.quality." + (value.isBlank() ? "auto" : value)),
                        value -> {
                            draft.quality = value;
                            draft.background = "";
                            init();
                        }, styles, "rewards-quality-" + index)).active = pending == null;
                int colorX = colorX(x, w);
                int colorWidth = Math.max(60, x + w - 24 - colorX);
                var background = new SuggestedEditBox(this, font, colorX, y + 44,
                        Math.max(44, colorWidth - 16), 20, text("rewards.background"), "color");
                background.setMaxLength(7);
                background.setHint(text("rewards.background"));
                background.setValue(draft.background);
                background.setTooltip(Tooltip.create(text("rewards.background")));
                background.setResponder(value -> draft.background = value);
                background.setEditable(pending == null);
                addRenderableWidget(background);
            }
            button("rewards-remove-" + index, "\u00d7", "delete", x + w - 24, y, 24, () -> {
                rewards.remove(index);
                init();
            });
            if (mailSaved != null) {
                addRenderableWidget(Checkbox.builder(text("mailbox.reward_visible"), font)
                        .pos(x + 24, y + (draft.type.equals("effect") ? 70 : 46))
                        .maxWidth((w - 54) / 2).selected(!draft.hidden)
                        .onValueChange((box, value) -> { draft.hidden = !value; init(); }).build());
                var popup = addRenderableWidget(Checkbox.builder(text("mailbox.reward_popup"), font)
                        .pos(x + 24 + (w - 24) / 2, y + (draft.type.equals("effect") ? 70 : 46))
                        .maxWidth((w - 54) / 2)
                        .selected(!draft.hidden && !draft.hidePopup && (draft.type.equals("item") || draft.type.equals("loot")))
                        .onValueChange((box, value) -> draft.hidePopup = !value).build());
                popup.active = !draft.hidden && (draft.type.equals("item") || draft.type.equals("loot"));
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
    private static int colorX(int left, int panelWidth) { return left + 24 + qualityWidth(panelWidth) + 8; }

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
                Path root = QuestConfig.root().resolve("scripts").toAbsolutePath().normalize();
                Path file = root.resolve(draft.item).normalize();
                if (!file.startsWith(root) || !draft.item.endsWith(".js")) {
                    error = "create.error.reward";
                    return;
                }
                String source = QuestScripts.source(draft.item);
                var server = minecraft.getSingleplayerServer();
                var player = minecraft.player;
                if (server == null || player == null) {
                    error = "create.error.permission";
                    return;
                }
                String reference = draft.item;
                String expected = QuestRepository.fingerprint(source);
                var uuid = player.getUUID();
                minecraft.setScreen(new QuestRewardTextEditorScreen(this,
                        text("reward_type.script"), source, file,
                        value -> CompletableFuture.supplyAsync(() ->
                                QuestScripts.editSource(server.getPlayerList().getPlayer(uuid),
                                        reference, expected, value), server),
                        value -> init()));
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
                    }, server),
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
                            reward.count(), false, draft.hidden, draft.hidePopup);
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
        if (pending == null && rowsViewport().contains(x, y)) {
            motion.wheel(vertical * rowHeight);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (clickChoicePopup(x, y, button)) return true;
        if (button == 0 && pending == null && rewards.size() > rows()
                && x >= left() + panelWidth() && x < left() + panelWidth() + 9
                && y >= 74 && y < 74 + rows() * rowHeight) {
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
            int colorX = colorX(left, w), colorWidth = Math.max(60, left + w - 24 - colorX);
            g.enableScissor(rowsViewport().left(), rowsViewport().top(), rowsViewport().right(), rowsViewport().bottom());
            for (int row = scroll / rowHeight; row < Math.min(rewards.size(), scroll / rowHeight + rows() + 1); row++) {
                var draft = rewards.get(row);
                if (!draft.type.equals("item") || draft.background.isBlank()) continue;
                int top = 74 + row * rowHeight - scroll + 44;
                int swatch = Math.max(8, Math.min(12, colorWidth - 4));
                int color;
                try { color = 0xFF000000 | Integer.parseInt(draft.background.strip().substring(1), 16); }
                catch (RuntimeException invalid) { color = 0xFF555963; }
                g.fill(colorX + colorWidth - swatch, top + 5, colorX + colorWidth - 2, top + 5 + swatch, color);
                g.renderOutline(colorX + colorWidth - swatch, top + 5, swatch, swatch, 0xFFD8DDE5);
            }
            g.disableScissor();
        }
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        g.drawString(font, text("rewards.target"), left + 24, 60, 0xFFCCD5DE);
        g.drawString(font, text("create.count"), left + w - 90, 60, 0xFFCCD5DE);
        g.enableScissor(rowsViewport().left(), rowsViewport().top(), rowsViewport().right(), rowsViewport().bottom());
        for (int row = scroll / rowHeight; row < Math.min(rewards.size(), scroll / rowHeight + rows() + 1); row++) {
            var draft = rewards.get(row);
            QuestIcons.render(g, icon(draft.type, draft.item), left + 2, 76 + row * rowHeight - scroll, 16);
        }
        if (rewards.isEmpty()) g.drawString(font, text("rewards.empty"), left, 82, 0xFFABB8C3);
        g.disableScissor();
        if (rewards.size() > rows()) {
            int track = rows() * rowHeight;
            g.fill(left + w + 3, 74, left + w + 6, 74 + track, 0xFF303741);
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
        backdrop(g, 0xF01C2026);
    }

    @Override public void onClose() { if (pending == null) closeTo(parent); }
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
        Draft(QuestDefinition.Reward reward) {
            type = reward.type();
            item = reward.item();
            count = Integer.toString(reward.count());
            quality = reward.quality().isBlank() ? "auto" : reward.quality();
            background = reward.background();
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
            }
            return new QuestDefinition.Reward(type, target, Integer.parseInt(count.strip()),
                    type.equals("item") ? quality : "", type.equals("item") ? background : "");
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
            case "loot" -> "minecraft:chest";
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
