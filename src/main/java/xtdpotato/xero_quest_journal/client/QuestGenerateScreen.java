package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.network.QuestGenerationNetwork;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Generation is an explicit server-authorized choice; opening this screen writes no tasks. */
public final class QuestGenerateScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final QuestGenerationNetwork.Offer offer;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String mode = "progress", namespace = "all", status = "";
    private boolean recipes = true;
    private CompletableFuture<String> pending;
    public QuestGenerateScreen(Screen parent, QuestGenerationNetwork.Offer offer) {
        super(text("title")); this.parent = parent; this.offer = offer;
    }
    private static Component text(String key) { return Component.translatable("screen.xero_quest_journal.generate." + key); }
    protected void init() {
        clearWidgets(); int w = Math.min(440, width - 40), x = (width - w) / 2;
        int top = Math.max(40, (height - 235) / 2);
        var modes = new StyledChoiceButton<>(x, top + 24, w, 24, text("mode"),
                List.of("progress", "structure", "both", "catalog"), mode, QuestGenerateScreen::text,
                value -> mode = value, styles, "generate-mode");
        modes.active = pending == null; addRenderableWidget(modes);
        var namespaces = new ArrayList<String>(); namespaces.add("all"); namespaces.addAll(offer.namespaces());
        var target = new StyledChoiceButton<>(x, top + 68, w, 24, text("namespace"), namespaces, namespace,
                value -> value.equals("all") ? text("all") : Component.literal(value), value -> namespace = value, styles, "generate-namespace");
        target.active = pending == null; addRenderableWidget(target);
        var check = Checkbox.builder(text("recipes"), font).pos(x, top + 102).maxWidth(w).selected(recipes)
                .onValueChange((box, value) -> recipes = value).build();
        check.active = pending == null; addRenderableWidget(check);
        var generate = new StyledButton(x, top + 174, (w - 8) / 2, 24, text("start"), ignored -> {
            pending = ClientQuestProgress.chooseGenerationMode(mode, namespace, recipes); init();
        }, styles, "generate-start");
        generate.active = offer.allowed() && pending == null && !status.equals("generation_started"); addRenderableWidget(generate);
        addRenderableWidget(new StyledButton(x + (w + 8) / 2, top + 174, (w - 8) / 2, 24,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "generate-cancel"));
        addRenderableWidget(new EditorIconButton(width - 36, 8, Component.translatable("gui.cancel"), this::onClose, styles, "generate-close", "close"));
    }
    public void tick() {
        if (pending != null && pending.isDone()) {
            try { status = pending.join(); } catch (Exception failure) { status = "remote_unavailable"; }
            pending = null; init();
        }
    }
    protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        backdrop(g, JOURNAL_BACKDROP); super.renderContents(g, x, y, delta);
        int w = Math.min(440, width - 40), left = (width - w) / 2, top = Math.max(40, (height - 235) / 2);
        g.drawCenteredString(font, title, width / 2, 16, 0xFFF2D29A);
        g.drawString(font, text("mode"), left, top + 10, 0xFFE2D6BD);
        g.drawString(font, text("namespace"), left, top + 54, 0xFFE2D6BD);
        g.drawWordWrap(font, text(!offer.allowed() ? "permission" : mode + "_description"), left, top + 130, w, 0xFFE5E8ED);
        if (!status.isEmpty()) g.drawWordWrap(font, Component.translatable("screen.xero_quest_journal.action." + status), left, top + 206, w, 0xFFF2D29A);
    }
    public void onClose() { ClientQuestProgress.chooseGenerationMode("dismiss", "all", false); closeTo(parent); }
    public boolean isPauseScreen() { return false; }
}
