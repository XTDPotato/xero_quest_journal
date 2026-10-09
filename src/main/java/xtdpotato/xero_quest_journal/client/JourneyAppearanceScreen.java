package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Shared server-default / chapter-draft editor. Cancel never publishes a draft. */
public final class JourneyAppearanceScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final Consumer<JourneyAppearance> saved;
    private final JourneyAppearance inherited;
    private List<String> availableSlots = JourneyAppearance.SLOTS;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final Map<String, Draft> drafts = new LinkedHashMap<>();
    private final Set<AbstractWidget> bodyWidgets = new HashSet<>();
    private final List<Label> labels = new ArrayList<>();
    private final Map<String, EditBox> fields = new HashMap<>();
    private final Map<String, Preview> previews = new HashMap<>();
    private final SmoothScroll scroll = new SmoothScroll();
    private String selected = "chapter", message = "";
    private int lastScroll = -1;
    private CompletableFuture<String> pending;

    private record Label(String key, int x, int y) {}
    public record Preview(String icon, String text) {}
    private static final class Draft {
        String icon, ix, iy, tx, ty, iconScale, textScale, text, background, textColor;
        boolean iconVisible, textVisible, overridden;
        Draft(JourneyAppearance.Element value, boolean overridden) {
            icon = value.icon();
            text = value.text(); background = value.background();
            textColor = value.textColor();
            ix = Integer.toString(value.iconOffsetX()); iy = Integer.toString(value.iconOffsetY());
            tx = Integer.toString(value.textOffsetX()); ty = Integer.toString(value.textOffsetY());
            iconScale = Float.toString(value.iconScale()); textScale = Float.toString(value.textScale());
            iconVisible = value.iconVisible(); textVisible = value.textVisible();
            this.overridden = overridden;
        }
        JourneyAppearance.Element value() {
            return new JourneyAppearance.Element(icon, Integer.parseInt(ix.strip()), Integer.parseInt(iy.strip()),
                    Float.parseFloat(iconScale.strip()), Integer.parseInt(tx.strip()), Integer.parseInt(ty.strip()),
                    Float.parseFloat(textScale.strip()), iconVisible, textVisible, text, background, textColor);
        }
        JourneyAppearance.Element preview() {
            try {
                return previewWithIcon(icon);
            } catch (IllegalArgumentException invalid) {
                return previewWithIcon("");
            }
        }
        private JourneyAppearance.Element previewWithIcon(String source) {
            return new JourneyAppearance.Element(source, offset(ix), offset(iy), scale(iconScale, .1F, 4F),
                    offset(tx), offset(ty), scale(textScale, .5F, 3F), iconVisible, textVisible, text,
                    net.minecraft.resources.ResourceLocation.tryParse(background) == null || background.contains("..")
                            ? "" : background,
                    textColor.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?") ? textColor : "");
        }
        private static int offset(String input) {
            try {
                int value = Integer.parseInt(input.strip());
                return Math.clamp(value, -256, 256);
            } catch (NumberFormatException invalid) { return 0; }
        }
        private static float scale(String input, float min, float max) {
            try {
                float value = Float.parseFloat(input.strip());
                return Float.isFinite(value) ? Math.clamp(value, min, max) : 1F;
            } catch (NumberFormatException invalid) { return 1F; }
        }
    }

    public JourneyAppearanceScreen(Screen parent) {
        this(parent, ClientQuestProgress.uiTheme().journeyAppearance(), JourneyAppearance.empty(), null);
    }
    public JourneyAppearanceScreen(Screen parent, JourneyAppearance value, Consumer<JourneyAppearance> saved) {
        this(parent, value, ClientQuestProgress.uiTheme().journeyAppearance(), saved);
    }
    public static JourneyAppearanceScreen task(Screen parent, JourneyAppearance value,
                                               Consumer<JourneyAppearance> saved) {
        var screen = new JourneyAppearanceScreen(parent, value, saved);
        screen.availableSlots = List.of("card", "branch", "subtitle", "complete", "marker");
        screen.selected = "branch";
        return screen;
    }
    public JourneyAppearanceScreen preview(String slot, String icon, String text) {
        previews.put(slot, new Preview(icon == null ? "" : icon, text == null ? "" : text));
        return this;
    }
    private JourneyAppearanceScreen(Screen parent, JourneyAppearance value, JourneyAppearance inherited,
                                    Consumer<JourneyAppearance> saved) {
        super(text("appearance"));
        this.parent = parent;
        this.saved = saved;
        this.inherited = inherited;
        JourneyAppearance effective = value.over(inherited);
        for (String slot : JourneyAppearance.SLOTS)
            drafts.put(slot, new Draft(effective.get(slot), value.elements().containsKey(slot)));
    }

    private static Component text(String key) { return Component.translatable("screen.xero_quest_journal.journey." + key); }
    private int contentWidth() { return Math.min(540, width - 24); }
    private int left() { return (width - contentWidth()) / 2; }
    private int bodyTop() { return height < 300 ? 112 : 134; }
    private int bodyBottom() { return height - 47; }
    private Draft current() { return drafts.get(selected); }

    @Override protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        clearWidgets(); bodyWidgets.clear(); labels.clear(); fields.clear();
        scroll.limit(342 - Math.max(1, bodyBottom() - bodyTop()));
        lastScroll = scroll.pixels();
        int x = left(), w = contentWidth(), half = (w - 8) / 2, y = bodyTop() - lastScroll;
        addRenderableWidget(new StyledChoiceButton<>(x, 34, w, 20, text("element"), availableSlots,
                selected, key -> text("element." + key), key -> {
                    selected = key; message = ""; scroll.jump(0); init();
                }, styles, "journey-appearance-element"));
        Draft draft = current();
        field("icon", draft.icon, x, y, w - 30, 2048, value -> draft.icon = value);
        var picker = new EditorIconButton(x + w - 24, y + 12, text("choose_icon"), () -> {
            minecraft.setScreen(QuestScreen.pickResource(this, draft.icon, false, value -> {
                draft.icon = value;
                draft.overridden = true;
            }));
        }, styles, "journey-appearance-pick", "minecraft:painting");
        addBody(picker);
        field("icon_x", draft.ix, x, y + 42, half, 8, value -> draft.ix = value);
        field("icon_y", draft.iy, x + half + 8, y + 42, half, 8, value -> draft.iy = value);
        field("text_x", draft.tx, x, y + 84, half, 8, value -> draft.tx = value);
        field("text_y", draft.ty, x + half + 8, y + 84, half, 8, value -> draft.ty = value);
        field("icon_scale", draft.iconScale, x, y + 126, half, 8, value -> draft.iconScale = value);
        field("text_scale", draft.textScale, x + half + 8, y + 126, half, 8, value -> draft.textScale = value);
        addBody(Checkbox.builder(text("icon_visible"), font).pos(x, y + 173).selected(draft.iconVisible)
                .onValueChange((box, value) -> { draft.iconVisible = value; draft.overridden = true; }).build());
        addBody(Checkbox.builder(text("text_visible"), font).pos(x + half + 8, y + 173).selected(draft.textVisible)
                .onValueChange((box, value) -> { draft.textVisible = value; draft.overridden = true; }).build());
        field("text_override", draft.text, x, y + 207, w, 256, value -> draft.text = value);
        field("background", draft.background, x, y + 249, w - 30, 2048, value -> draft.background = value);
        addBody(new EditorIconButton(x + w - 24, y + 262, text("choose_background"), () ->
                minecraft.setScreen(QuestScreen.pickResource(this, draft.background, false, value -> {
                    draft.background = value; draft.overridden = true;
                })), styles, "journey-appearance-background", "minecraft:painting"));
        field("text_color", draft.textColor, x, y + 291, w - 28, 9, value -> draft.textColor = value);
        addRenderableWidget(new ScrollRail(x + w + 2, bodyTop(), bodyBottom() - bodyTop(), scroll));
        int part = (w - 12) / 3;
        addRenderableWidget(new StyledButton(x, height - 29, part, 21, text("reset_element"), ignored -> {
            drafts.put(selected, new Draft(inherited.get(selected), false));
            message = ""; init();
        }, styles, "journey-appearance-reset"));
        addRenderableWidget(new StyledButton(x + part + 6, height - 29, part, 21, text("save"),
                ignored -> save(), styles, "journey-appearance-save"));
        addRenderableWidget(new StyledButton(x + (part + 6) * 2, height - 29, part, 21,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "journey-appearance-cancel"));
        if (pending != null) children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).forEach(widget -> widget.active = false);
    }

    private void addBody(AbstractWidget widget) { bodyWidgets.add(widget); addRenderableWidget(widget); }
    private void moveBody(int pixels) {
        int change = pixels - lastScroll;
        if (change == 0) return;
        bodyWidgets.forEach(widget -> widget.setY(widget.getY() - change));
        labels.replaceAll(label -> new Label(label.key(), label.x(), label.y() - change));
        lastScroll = pixels;
    }
    private void field(String key, String value, int x, int y, int w, int max, Consumer<String> changed) {
        labels.add(new Label(key, x, y));
        var field = new EditBox(font, x, y + 13, w, 20, text(key));
        field.setMaxLength(max); field.setValue(value);
        field.setTooltip(Tooltip.create(text(key)));
        Draft owner = current();
        field.setResponder(next -> { changed.accept(next); owner.overridden = true; });
        fields.put(key, field);
        addBody(field);
    }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return bodyWidgets.contains(widget) ? viewport(left(), bodyTop(), left() + contentWidth(), bodyBottom()) : null;
    }
    @Override protected boolean blurWorldBackground() { return true; }

    private void save() {
        if (pending != null) return;
        try {
            var values = new LinkedHashMap<String, JourneyAppearance.Element>();
            for (var entry : drafts.entrySet()) if (entry.getValue().overridden) {
                try {
                    JourneyAppearance.Element value = entry.getValue().value();
                    String source = value.icon();
                    if (!value.background().isBlank() && !ClientMediaManager.validImageSource(value.background())
                            && !(value.background().matches(".*\\.(png|jpg|jpeg|bmp)") && QuestIcons.valid(value.background())))
                        throw new IllegalArgumentException("Missing background image");
                    if (!source.isBlank() && !QuestIcons.valid(source) && !(source.matches("[a-z0-9_]+")
                            && minecraft.getResourceManager().getResource(net.minecraft.resources.ResourceLocation
                            .fromNamespaceAndPath("xero_quest_journal", "textures/gui/navigation/" + source + ".png")).isPresent()))
                        throw new IllegalArgumentException("Missing icon resource");
                    values.put(entry.getKey(), value);
                } catch (IllegalArgumentException invalid) {
                    selected = entry.getKey();
                    throw invalid;
                }
            }
            var value = new JourneyAppearance(values);
            if (saved != null) { saved.accept(value); closeTo(parent); }
            else {
                pending = ClientQuestProgress.setUiTheme(ClientQuestProgress.uiTheme().withJourneyAppearance(value));
                message = ""; init();
            }
        } catch (IllegalArgumentException invalid) {
            message = text("appearance_invalid").getString();
            init();
        }
    }
    @Override public void tick() {
        if (pending == null || !pending.isDone()) return;
        try {
            String result = pending.join();
            pending = null;
            if (result.equals("config_saved")) closeTo(parent);
            else { message = Component.translatable("screen.xero_quest_journal.action." + result).getString(); init(); }
        } catch (RuntimeException failure) {
            pending = null; message = text("appearance_invalid").getString(); init();
        }
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        backdrop(g, 0xED22262D);
        scroll.advance();
        moveBody(scroll.pixels());
        int x = left(), w = contentWidth();
        g.drawString(font, title, x, 13, 0xFFF4DCB1, false);
        g.enableScissor(x, 61, x + w, bodyTop() - 8);
        g.fill(x, 61, x + w, bodyTop() - 8, 0xFF35434B);
        JourneyAppearance.Element value = previewValue();
        if (!value.background().isBlank())
            ClientMediaManager.renderImageCover(g, value.background(), x, 61, w, bodyTop() - 69, 1);
        String fallback = switch (selected) {
            case "previous" -> "back"; case "next" -> "@next"; case "close" -> "close";
            case "complete" -> "@complete"; case "marker" -> "@marker";
            case "scroll_thumb" -> "@vertical";
            case "card", "branch" -> "minecraft:book"; default -> "guide";
        };
        Preview context = previews.get(selected);
        if (context != null) fallback = context.icon();
        int centerY = (61 + bodyTop() - 8) / 2;
        JourneyAppearanceRenderer.icon(g, value, fallback, x + 15, centerY - 14, 28, 28, 0xFFF4DCB1, false);
        JourneyAppearanceRenderer.text(g, font, value,
                context == null ? text("element." + selected).getString() : context.text(),
                x + 62, centerY - 4, w - 70, 0xFFF5E8CF, false);
        g.disableScissor();
        g.enableScissor(x, bodyTop(), x + w, bodyBottom());
        g.fill(x + w - 20, bodyTop() - lastScroll + 306, x + w - 4, bodyTop() - lastScroll + 322,
                ButtonStyle.parseColor(current().textColor, 0xFFFFFFFF));
        for (Label label : labels) g.drawString(font, text(label.key()), label.x(), label.y(), 0xFFD2D6DE, false);
        g.disableScissor();
        if (!message.isBlank()) g.drawString(font, font.plainSubstrByWidth(message, w), x,
                height - 43, 0xFFFFA6A6, false);
        super.renderContents(g, mouseX, mouseY, delta);
    }
    private JourneyAppearance.Element previewValue() {
        // The visible field is authoritative after returning from a resource picker.
        EditBox icon = fields.get("icon");
        if (icon != null) current().icon = icon.getValue();
        return current().preview();
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked() || pending != null) return true;
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (x >= left() && x < left() + contentWidth() + 10 && y >= bodyTop() && y < bodyBottom()) {
            scroll.wheel(vertical * 28); return true;
        }
        return false;
    }
    @Override public void onClose() { if (pending == null) closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
