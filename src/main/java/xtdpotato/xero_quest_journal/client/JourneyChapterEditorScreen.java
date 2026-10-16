package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;
import xtdpotato.xero_quest_journal.core.network.QuestJourneyNetwork;

import java.util.List;
import java.util.function.Consumer;

/** Chapter identity and appearance are saved as a single conflict-checked draft. */
final class JourneyChapterEditorScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final QuestCatalog.Chapter chapter;
    private final List<String> members;
    private final Consumer<QuestJourneyNetwork.Draft> saved;
    private final String initialPart;
    private String name, icon, error = "";
    private JourneyAppearance appearance;

    JourneyChapterEditorScreen(Screen parent, QuestCatalog.Chapter chapter, List<String> members,
                               String part, Consumer<QuestJourneyNetwork.Draft> saved) {
        super(text("edit_chapter"));
        this.parent = parent; this.chapter = chapter; this.members = members; this.saved = saved;
        initialPart = part; name = chapter.name(); icon = chapter.icon(); appearance = chapter.journeyAppearance();
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.journey." + key);
    }
    private int propertyX() { return Math.max(12, width - Math.min(300, width / 2)); }
    @Override protected void init() {
        clearWidgets();
        var styles = ButtonStyleSheet.load();
        int x = propertyX(), w = width - x - 18;
        var titleInput = new EditBox(font, x, 68, w, 20, text("edit_chapter_name"));
        titleInput.setMaxLength(128); titleInput.setValue(name); titleInput.setResponder(value -> name = value);
        addRenderableWidget(titleInput);
        var iconInput = new EditBox(font, x, 116, w - 30, 20, text("edit_chapter_icon"));
        iconInput.setMaxLength(2048); iconInput.setValue(icon); iconInput.setResponder(value -> icon = value);
        addRenderableWidget(iconInput);
        addRenderableWidget(new EditorIconButton(x + w - 24, 114, text("choose_icon"),
                () -> minecraft.setScreen(QuestScreen.pickResource(this, icon, false, value -> icon = value)),
                styles, "journey-chapter-icon", "choose"));
        addRenderableWidget(new StyledButton(x, 155, w, 22, text("appearance"), ignored ->
                minecraft.setScreen(new JourneyAppearanceScreen(this, appearance, value -> appearance = value)
                        .chapterContext(new QuestCatalog.Chapter(chapter.id(), name, chapter.description(), icon,
                                chapter.groupId(), members, chapter.backgrounds(), appearance),
                                members.stream().map(ClientQuestDefinitions::find).filter(java.util.Objects::nonNull).toList())
                        .select("chapter", initialPart)),
                styles, "journey-chapter-appearance"));
        addRenderableWidget(new StyledButton(width - 158, height - 30, 70, 22,
                text("save"), ignored -> save(), styles, "journey-chapter-save"));
        addRenderableWidget(new EditorIconButton(width - 38, 9, Component.translatable("screen.xero_quest_journal.close"),
                this::onClose, styles, "journey-chapter-close", "close"));
        setInitialFocus(initialPart.equals("icon") ? iconInput : titleInput);
    }
    private void save() {
        try {
            if (!icon.isBlank() && !QuestIcons.valid(icon)) throw new IllegalArgumentException("Missing icon");
            saved.accept(new QuestJourneyNetwork.Draft(members, chapter.timeline(), chapter.backgrounds(),
                    name.strip(), icon.strip(), appearance));
            closeTo(parent);
        } catch (IllegalArgumentException invalid) { error = text("appearance_invalid").getString(); }
    }
    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        backdrop(g, 0xFF1B2027);
        g.drawString(font, title, 14, 15, 0xFFF1D49B, false);
        int px = propertyX();
        g.fill(px - 12, 40, width, height - 40, 0xFF292F38);
        g.fill(14, 52, px - 24, height - 54, 0xFF284143);
        int cx = Math.max(30, (px - 10) / 2), cy = height / 2;
        var style = appearance.over(ClientQuestProgress.uiTheme().journeyAppearance()).get("chapter");
        g.enableScissor(14, 52, px - 24, height - 54);
        JourneyAppearanceRenderer.icon(g, style, icon, cx - 22, cy - 66, 44, 44, 0xFFF2DBA7, false);
        JourneyAppearanceRenderer.text(g, font, style, name, cx, cy + 8, Math.max(20, px - 64), 0xFFF4EBDD, true);
        g.disableScissor();
        g.drawString(font, text("edit_chapter_name"), px, 53, 0xFFBECCD0, false);
        g.drawString(font, text("edit_chapter_icon"), px, 101, 0xFFBECCD0, false);
        if (!error.isBlank()) g.drawString(font, error, 14, height - 42, 0xFFFF9E90, false);
        super.renderContents(g, mx, my, delta);
    }
    @Override public void onClose() { closeTo(parent); }
}
