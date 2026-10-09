package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.QuestTypeStore;
import xtdpotato.xero_quest_journal.core.io.QuestAuthoring;
import xtdpotato.xero_quest_journal.core.model.QuestType;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class QuestTypeScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private QuestType selected;
    private String id = "";
    private String name = "";
    private String icon = "";
    private String error = "";
    private CompletableFuture<String> pending;

    public QuestTypeScreen(Screen parent) {
        this(parent, "");
    }

    public QuestTypeScreen(Screen parent, String initialId) {
        super(text("title"));
        this.parent = parent;
        if (initialId.isEmpty()) {
            id = "type_" + UUID.randomUUID().toString().substring(0, 8);
        } else {
            selected = ClientQuestDefinitions.types().stream()
                    .filter(type -> type.id().equals(initialId)).findFirst().orElse(null);
            if (selected == null && initialId.equals("active")) {
                selected = new QuestType("active",
                        Component.translatable("screen.xero_quest_journal.category.active").getString(),
                        "xero_quest_journal:textures/gui/navigation/all.png");
            }
            if (selected != null) select(selected);
        }
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.quest_types." + key);
    }

    private int split() {
        return Math.min(220, Math.max(150, width / 3));
    }

    private void select(QuestType type) {
        selected = type;
        id = type.id();
        name = type.name();
        icon = type.icon();
        error = "";
    }

    private void createNew() {
        selected = null;
        id = "type_" + UUID.randomUUID().toString().substring(0, 8);
        name = "";
        icon = "";
        error = "";
        init();
    }

    @Override
    protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        clearWidgets();
        int left = 12;
        int listWidth = split() - 20;
        addRenderableWidget(new StyledButton(left, 30, listWidth, 20, text("add"),
                ignored -> createNew(), styles, "quest-type-add", "toolbar-button"));
        int y = 56;
        for (QuestType type : ClientQuestDefinitions.types()) {
            StyledButton row = new StyledButton(left, y, listWidth, 22, Component.literal(type.name()), ignored -> {
                select(type);
                init();
            }, styles, "quest-type-" + type.id(), "template-row");
            row.leadingIcon(type.icon());
            row.setSelectionHighlighted(selected != null && selected.id().equals(type.id()));
            addRenderableWidget(row);
            y += 24;
        }

        int x = split() + 16;
        int w = width - x - 16;
        EditBox idField = field(x, 54, w, text("id"), id, value -> id = value);
        idField.setEditable(selected == null && pending == null);
        field(x, 92, w, text("name"), name, value -> name = value);
        EditBox iconField = field(x, 130, Math.max(40, w - 28), text("icon"), icon, value -> icon = value);
        ((SuggestedEditBox) iconField).withoutMenu();
        StyledButton picker = new StyledButton(x + w - 24, 130, 24, 20, Component.literal("..."),
                ignored -> minecraft.setScreen(QuestScreen.pickResource(this, icon, false, value -> icon = value)),
                styles, "quest-type-icon", "toolbar-button");
        picker.leadingIcon(icon);
        picker.setTooltip(Tooltip.create(text("icon")));
        addRenderableWidget(picker);

        StyledButton save = new StyledButton(x, height - 32, Math.max(60, (w - 8) / 2), 22,
                text(pending == null ? "save" : "saving"), ignored -> save(), styles,
                "quest-type-save", "toolbar-button");
        save.active = pending == null;
        addRenderableWidget(save);
        addRenderableWidget(new StyledButton(x + (w + 8) / 2, height - 32, Math.max(60, (w - 8) / 2), 22,
                Component.translatable("gui.done"), ignored -> onClose(), styles,
                "quest-type-done", "toolbar-button"));
    }

    private EditBox field(int x, int y, int w, Component label, String value,
                          java.util.function.Consumer<String> changed) {
        var box = new SuggestedEditBox(this, font, x, y, w, 20, label, "");
        box.setMaxLength(8192);
        box.setValue(value);
        box.setHint(label);
        box.setResponder(changed);
        box.setEditable(pending == null);
        return addRenderableWidget(box);
    }

    private void save() {
        if (pending != null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        try {
            QuestType type = new QuestType(id.strip(), name.strip(), icon.strip());
            var server = minecraft.getSingleplayerServer();
            var playerId = minecraft.player.getUUID();
            boolean editing = selected != null;
            pending = CompletableFuture.supplyAsync(() ->
                    QuestTypeStore.save(server.getPlayerList().getPlayer(playerId), type, editing), server);
            error = "";
            init();
        } catch (IllegalArgumentException invalid) {
            error = "invalid";
        }
    }

    @Override
    public void tick() {
        if (pending == null || !pending.isDone()) return;
        try {
            error = pending.join();
        } catch (RuntimeException failure) {
            error = "save";
        }
        pending = null;
        if (error.isEmpty()) {
            selected = ClientQuestDefinitions.types().stream().filter(type -> type.id().equals(id)).findFirst().orElse(null);
        }
        init();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        backdrop(g, 0xF01C2026);
        g.fill(split(), 26, split() + 1, height - 42, 0xFF81858B);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        int x = split() + 16;
        g.drawString(font, text("id"), x, 42, 0xFFD5DEE8);
        g.drawString(font, text("name"), x, 80, 0xFFD5DEE8);
        g.drawString(font, text("icon"), x, 118, 0xFFD5DEE8);
        if (!error.isEmpty()) g.drawString(font, text("error." + error), x, 162, 0xFFFF7777);
    }

    @Override
    public void onClose() {
        if (pending == null) closeTo(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
