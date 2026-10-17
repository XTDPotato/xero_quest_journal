package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;

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
    private String selectedPart = "icon", dragMode = "";
    private QuestCatalog.Chapter chapterContext;
    private List<JourneyPreviewScene.Part> scene = List.of();
    private final LinkedHashSet<Selection> selections = new LinkedHashSet<>();
    private final Map<Selection, JourneyAppearance.Element> dragValues = new LinkedHashMap<>();
    private final Map<Selection, int[]> dragBounds = new LinkedHashMap<>();
    private final Deque<Map<String, Draft>> undo = new ArrayDeque<>();
    private final Deque<Map<String, Draft>> redo = new ArrayDeque<>();
    private boolean multiSelect, syncingFields;
    private Selection pendingToggle;
    private boolean selectionDragged;
    private int[] dragGroupBounds;
    private record Selection(String slot, String kind) {}
    private double dragX, dragY;
    private int startWidth, startHeight;

    private record Label(String key, int x, int y) {}
    public record Preview(String icon, String text) {}
    private static final class Draft {
        String icon, ix, iy, tx, ty, iconScale, textScale, text, background, textColor;
        String iw, ih, tw, th;
        boolean iconVisible, textVisible, overridden;
        Draft(Draft other) {
            this.icon = other.icon; this.ix = other.ix; this.iy = other.iy;
            this.tx = other.tx; this.ty = other.ty; this.iconScale = other.iconScale;
            this.textScale = other.textScale; this.text = other.text; this.background = other.background;
            this.textColor = other.textColor; this.iw = other.iw; this.ih = other.ih;
            this.tw = other.tw; this.th = other.th; this.iconVisible = other.iconVisible;
            this.textVisible = other.textVisible; this.overridden = other.overridden;
        }
        Draft(JourneyAppearance.Element value, boolean overridden) {
            icon = value.icon();
            text = value.text(); background = value.background();
            textColor = value.textColor();
            iw = Integer.toString(value.iconWidth()); ih = Integer.toString(value.iconHeight());
            tw = Integer.toString(value.textWidth()); th = Integer.toString(value.textHeight());
            ix = Integer.toString(value.iconOffsetX()); iy = Integer.toString(value.iconOffsetY());
            tx = Integer.toString(value.textOffsetX()); ty = Integer.toString(value.textOffsetY());
            iconScale = Float.toString(value.iconScale()); textScale = Float.toString(value.textScale());
            iconVisible = value.iconVisible(); textVisible = value.textVisible();
            this.overridden = overridden;
        }
        JourneyAppearance.Element value() {
            return new JourneyAppearance.Element(icon, Integer.parseInt(ix.strip()), Integer.parseInt(iy.strip()),
                    Float.parseFloat(iconScale.strip()), Integer.parseInt(tx.strip()), Integer.parseInt(ty.strip()),
                    Float.parseFloat(textScale.strip()), iconVisible, textVisible, text, background, textColor,
                    Integer.parseInt(iw), Integer.parseInt(ih), Integer.parseInt(tw), Integer.parseInt(th));
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
                    textColor.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?") ? textColor : "",
                    size(iw), size(ih), size(tw), size(th));
        }
        private static int size(String input) {
            try { return Math.clamp(Integer.parseInt(input), 0, 2048); }
            catch (NumberFormatException invalid) { return 0; }
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
    public JourneyAppearanceScreen select(String slot, String part) {
        if (availableSlots.contains(slot)) selected = slot;
        selectedPart = part.equals("text") ? "text" : "icon";
        return this;
    }
    public JourneyAppearanceScreen chapterContext(QuestCatalog.Chapter chapter, List<QuestDefinition> tasks) {
        chapterContext = chapter;
        scene = JourneyPreviewScene.parts(chapter, tasks);
        preview("chapter", chapter.icon(), chapter.name()).preview("tab", chapter.icon(), chapter.name());
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
    private int contentWidth() { return Math.min(294, Math.max(158, width / 3)) - 24; }
    private int left() { return width - contentWidth() - 24; }
    private int bodyTop() { return 92; }
    private int bodyBottom() { return height - 47; }
    private Draft current() { return drafts.get(selected); }
    private Map<String, Draft> snapshot() {
        var copy = new LinkedHashMap<String, Draft>();
        drafts.forEach((key, value) -> copy.put(key, new Draft(value)));
        return copy;
    }
    private void pushUndo() {
        undo.push(snapshot());
        while (undo.size() > 64) undo.removeLast();
        redo.clear();
        updateHistoryButtons();
    }
    private void updateHistoryButtons() {
        children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals("journey-appearance-undo"))
                .forEach(button -> button.active = !undo.isEmpty());
        children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(button -> button.styleId().equals("journey-appearance-redo"))
                .forEach(button -> button.active = !redo.isEmpty());
    }
    private void restore(Map<String, Draft> state) {
        drafts.clear();
        state.forEach((key, value) -> drafts.put(key, new Draft(value)));
        init();
    }
    private void undoEdit() {
        if (undo.isEmpty()) return;
        redo.push(snapshot());
        restore(undo.pop());
    }
    private void redoEdit() {
        if (redo.isEmpty()) return;
        undo.push(snapshot());
        restore(redo.pop());
    }

    @Override protected void init() {
        if (!ClientEditMode.enabled()) { onClose(); return; }
        clearWidgets(); bodyWidgets.clear(); labels.clear(); fields.clear();
        scroll.limit(468 - Math.max(1, bodyBottom() - bodyTop()));
        lastScroll = scroll.pixels();
        int x = left(), w = contentWidth(), half = (w - 8) / 2, y = bodyTop() - lastScroll;
        addRenderableWidget(new StyledChoiceButton<>(x, 42, w, 20, text("element"), availableSlots,
                selected, key -> text("element." + key), key -> {
                    if (!scene.isEmpty() && scene.stream().noneMatch(part -> part.slot().equals(key)
                            && part.kind().equals(selectedPart))) selectedPart = "icon";
                    selected = key; selections.clear(); selections.add(new Selection(selected, selectedPart));
                    message = ""; scroll.jump(0); init();
                }, styles, "journey-appearance-element"));
        List<String> components = scene.isEmpty() ? List.of("icon", "text") : scene.stream()
                .filter(part -> part.slot().equals(selected)).map(JourneyPreviewScene.Part::kind).distinct().toList();
        if (!components.contains(selectedPart) && !components.isEmpty()) selectedPart = components.getFirst();
        if (!selections.contains(new Selection(selected, selectedPart))) {
            selections.clear(); selections.add(new Selection(selected, selectedPart));
        }
        addRenderableWidget(new StyledChoiceButton<>(x, 67, w, 20, text("component"),
                components.isEmpty() ? List.of("icon", "text") : components, selectedPart, key -> text("component." + key), key -> {
                    selectedPart = key; selections.clear(); selections.add(new Selection(selected, selectedPart)); init();
                }, styles, "journey-appearance-component"));
        addRenderableWidget(Checkbox.builder(text("multi_select"), font).pos(14, 41).selected(multiSelect)
                .onValueChange((box, value) -> multiSelect = value).build());
        addRenderableWidget(new EditorIconButton(110, 38, text("select_all"), () -> selectComponents(false),
                styles, "journey-appearance-select-all", "all"));
        addRenderableWidget(new EditorIconButton(138, 38, text("invert_selection"), () -> selectComponents(true),
                styles, "journey-appearance-invert", "choose"));
        Draft draft = current();
        field("icon", draft.icon, x, y, w - 30, 2048, value -> draft.icon = value);
        var picker = new EditorIconButton(x + w - 24, y + 12, text("choose_icon"), () -> {
            minecraft.setScreen(QuestScreen.pickResource(this, draft.icon, false, value -> {
                if (!draft.icon.equals(value)) pushUndo();
                draft.icon = value;
                draft.overridden = true;
                applyFieldToSelection("icon", value);
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
                .onValueChange((box, value) -> setVisible("icon", value)).build());
        addBody(Checkbox.builder(text("text_visible"), font).pos(x + half + 8, y + 173).selected(draft.textVisible)
                .onValueChange((box, value) -> setVisible("text", value)).build());
        field("text_override", draft.text, x, y + 207, w, 256, value -> draft.text = value);
        field("background", draft.background, x, y + 249, w - 30, 2048, value -> draft.background = value);
        addBody(new EditorIconButton(x + w - 24, y + 262, text("choose_background"), () ->
                minecraft.setScreen(QuestScreen.pickResource(this, draft.background, false, value -> {
                    if (!draft.background.equals(value)) pushUndo();
                    draft.background = value; draft.overridden = true;
                    applyFieldToSelection("background", value);
                })), styles, "journey-appearance-background", "minecraft:painting"));
        field("text_color", draft.textColor, x, y + 291, w - 28, 9, value -> draft.textColor = value);
        field("icon_width", draft.iw, x, y + 333, half, 8, value -> draft.iw = value);
        field("icon_height", draft.ih, x + half + 8, y + 333, half, 8, value -> draft.ih = value);
        field("text_width", draft.tw, x, y + 375, half, 8, value -> draft.tw = value);
        field("text_height", draft.th, x + half + 8, y + 375, half, 8, value -> draft.th = value);
        addRenderableWidget(new ScrollRail(x + w + 2, bodyTop(), bodyBottom() - bodyTop(), scroll));
        addRenderableWidget(new EditorIconButton(x, height - 30, text("reset_element"),
                this::resetSelection, styles, "journey-appearance-reset", "refresh"));
        addRenderableWidget(new EditorIconButton(x + 28, height - 30, text("undo"),
                this::undoEdit, styles, "journey-appearance-undo", "undo")).active = !undo.isEmpty();
        addRenderableWidget(new EditorIconButton(x + 56, height - 30, text("redo"),
                this::redoEdit, styles, "journey-appearance-redo", "redo")).active = !redo.isEmpty();
        int actionLeft = x + 80;
        int actionWidth = Math.max(20, (w - 84) / 2);
        addRenderableWidget(new StyledButton(actionLeft, height - 29, actionWidth, 21, text("save"),
                ignored -> save(), styles, "journey-appearance-save"));
        addRenderableWidget(new StyledButton(actionLeft + actionWidth + 4, height - 29,
                actionWidth, 21,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles, "journey-appearance-cancel"));
        if (pending != null) children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).forEach(widget -> widget.active = false);
    }
    private void selectComponents(boolean invert) {
        var previous = Set.copyOf(selections);
        selections.clear();
        var candidates = scene.isEmpty()
                ? List.of(new Selection(selected, "icon"), new Selection(selected, "text"))
                : scene.stream().filter(part -> availableSlots.contains(part.slot()))
                .map(part -> new Selection(part.slot(), part.kind())).distinct().toList();
        for (var ref : candidates) if (!invert || !previous.contains(ref)) selections.add(ref);
        if (selections.isEmpty()) selections.add(new Selection(selected, selectedPart));
        var primary = selections.iterator().next();
        selected = primary.slot(); selectedPart = primary.kind();
        multiSelect = true; init();
    }

    private void resetSelection() {
        pushUndo();
        for (Selection ref : selections) {
            Draft draft = drafts.get(ref.slot());
            var base = inherited.get(ref.slot());
            if (ref.kind().equals("icon")) {
                draft.icon = base.icon();
                draft.ix = Integer.toString(base.iconOffsetX()); draft.iy = Integer.toString(base.iconOffsetY());
                draft.iconScale = Float.toString(base.iconScale()); draft.iconVisible = base.iconVisible();
                draft.iw = Integer.toString(base.iconWidth()); draft.ih = Integer.toString(base.iconHeight());
            } else {
                draft.text = base.text(); draft.background = base.background(); draft.textColor = base.textColor();
                draft.tx = Integer.toString(base.textOffsetX()); draft.ty = Integer.toString(base.textOffsetY());
                draft.textScale = Float.toString(base.textScale()); draft.textVisible = base.textVisible();
                draft.tw = Integer.toString(base.textWidth()); draft.th = Integer.toString(base.textHeight());
            }
            try { draft.overridden = !draft.value().equals(base); }
            catch (IllegalArgumentException invalid) { draft.overridden = true; }
        }
        message = ""; init();
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
        field.setResponder(next -> {
            if (!syncingFields && !next.equals(value)) pushUndo();
            changed.accept(next); owner.overridden = true;
            if (!syncingFields) applyFieldToSelection(key, next);
        });
        fields.put(key, field);
        addBody(field);
    }
    private void applyFieldToSelection(String key, String value) {
        String kind = key.startsWith("icon") ? "icon" : key.startsWith("text") ? "text" : "";
        for (Selection ref : selections) {
            if (!kind.isBlank() && !ref.kind().equals(kind)) continue;
            Draft draft = drafts.get(ref.slot());
            if (draft == current()) continue;
            switch (key) {
                case "icon" -> draft.icon = value;
                case "icon_x" -> draft.ix = value;
                case "icon_y" -> draft.iy = value;
                case "icon_scale" -> draft.iconScale = value;
                case "icon_width" -> draft.iw = value;
                case "icon_height" -> draft.ih = value;
                case "text_x" -> draft.tx = value;
                case "text_y" -> draft.ty = value;
                case "text_scale" -> draft.textScale = value;
                case "text_width" -> draft.tw = value;
                case "text_height" -> draft.th = value;
                case "text_override" -> draft.text = value;
                case "text_color" -> draft.textColor = value;
                case "background" -> draft.background = value;
                default -> { continue; }
            }
            draft.overridden = true;
        }
    }
    private void setVisible(String kind, boolean value) {
        pushUndo();
        if (kind.equals("icon")) current().iconVisible = value;
        else current().textVisible = value;
        current().overridden = true;
        for (Selection ref : selections) if (ref.kind().equals(kind)) {
            var draft = drafts.get(ref.slot());
            if (kind.equals("icon")) draft.iconVisible = value;
            else draft.textVisible = value;
            draft.overridden = true;
        }
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
        String caption = chapterContext == null ? title.getString() : chapterContext.name();
        g.drawString(font, font.plainSubstrByWidth(caption, Math.max(1, left() - 28)), 14, 13, 0xFFF4DCB1, false);
        g.fill(left() - 12, 34, width, height - 38, 0xFF282E36);
        g.enableScissor(12, 72, left() - 20, bodyBottom());
        g.fill(12, 72, left() - 20, bodyBottom(), 0xFF243E40);
        for (int gx = 12; gx < left() - 20; gx += 24) g.fill(gx, 72, gx + 1, bodyBottom(), 0x155A9294);
        for (int gy = 72; gy < bodyBottom(); gy += 24) g.fill(12, gy, left() - 20, gy + 1, 0x155A9294);
        if (scene.isEmpty()) {
        JourneyAppearance.Element value = previewValue();
        if (!value.background().isBlank())
            ClientMediaManager.renderImageCover(g, value.background(), 12, 44, left() - 32, bodyBottom() - 44, 1);
        String fallback = switch (selected) {
            case "previous" -> "back"; case "next" -> "@next"; case "close" -> "close";
            case "complete" -> "@complete"; case "marker" -> "@marker";
            case "scroll_thumb" -> "@vertical";
            case "card", "branch" -> "minecraft:book"; default -> "guide";
        };
        Preview context = previews.get(selected);
        if (context != null) fallback = context.icon();
        JourneyAppearanceRenderer.icon(g, value, fallback, previewIconX(), previewIconY(), 28, 28, 0xFFF4DCB1, false);
        JourneyAppearanceRenderer.text(g, font, value,
                context == null ? text("element." + selected).getString() : context.text(),
                previewTextX(), previewTextY(), previewTextWidth(), 0xFFF5E8CF, false);
        } else renderScene(g);
        g.disableScissor();
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                net.minecraft.client.Minecraft.ON_OSX);
        int[] bounds = selectionBounds();
        g.enableScissor(12, 72, left() - 20, bodyBottom());
        if (selections.size() > 1) for (var ref : selections) {
            int[] box = componentBounds(ref);
            g.renderOutline(box[0] - 1, box[1] - 1, box[2] + 2, box[3] + 2, 0xAA8DC8D5);
        }
        g.renderOutline(bounds[0] - 1, bounds[1] - 1, bounds[2] + 2, bounds[3] + 2, 0xFFF0CF87);
        for (int[] handle : List.of(new int[]{bounds[0] + bounds[2], bounds[1] + bounds[3] / 2},
                new int[]{bounds[0] + bounds[2] / 2, bounds[1] + bounds[3]},
                new int[]{bounds[0] + bounds[2], bounds[1] + bounds[3]}))
            g.fill(handle[0] - 3, handle[1] - 3, handle[0] + 3, handle[1] + 3, 0xFFF0CF87);
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
    private double previewScale() {
        if (scene.isEmpty()) return 1;
        return Math.max(.1, Math.min((left() - 44.) / JourneyPreviewScene.WIDTH,
                (bodyBottom() - 88.) / JourneyPreviewScene.HEIGHT));
    }
    private double previewOriginX() { return 14 + (left() - 42 - JourneyPreviewScene.WIDTH * previewScale()) / 2.; }
    private double previewOriginY() { return 78 + (bodyBottom() - 86 - JourneyPreviewScene.HEIGHT * previewScale()) / 2.; }
    private void renderScene(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(previewOriginX(), previewOriginY(), 0);
        g.pose().scale((float) previewScale(), (float) previewScale(), 1);
        g.fill(16, 226, 624, 227, 0xBEE9E3D5);
        g.fill(226, 128, 394, 212, 0xFF303C46);
        g.fill(226, 216, 394, 240, 0xFFF5F3EE);
        g.fill(444, 252, 624, 284, 0xFFF5F3EE);
        g.fill(220, 49, 422, 50, 0x99D9D3C4);
        g.fill(182, 342, 452, 343, 0x886B7882);
        for (var part : scene) {
            if (!availableSlots.contains(part.slot())) continue;
            var value = drafts.get(part.slot()).preview();
            if (part.kind().equals("icon")) {
                JourneyAppearanceRenderer.icon(g, value, part.fallback(), part.x(), part.y(),
                        part.width(), part.height(), 0xFFFFFFFF, part.slot().equals("card"));
            } else {
                if (!value.background().isBlank()) ClientMediaManager.renderImageCover(g, value.background(),
                        part.x() - (part.centered() ? part.width() / 2 : 0), part.y() - 4,
                        value.textWidth() > 0 ? value.textWidth() : part.width(),
                        value.textHeight() > 0 ? value.textHeight() : part.height() + 8, 1);
                JourneyAppearanceRenderer.text(g, font, value, part.fallback(), part.x(), part.y(), part.width(),
                        List.of("card", "branch").contains(part.slot()) ? 0xFF394452 : 0xFFF5E8CF, part.centered());
            }
        }
        g.pose().popPose();
    }
    private JourneyAppearance.Element previewValue() {
        // The visible field is authoritative after returning from a resource picker.
        EditBox icon = fields.get("icon");
        if (icon != null) current().icon = icon.getValue();
        return current().preview();
    }
    private int previewIconX() { return 32; }
    private int previewIconY() { return (44 + bodyBottom()) / 2 - 14; }
    private int previewTextX() { return 90; }
    private int previewTextY() { return (44 + bodyBottom()) / 2 - 4; }
    private int previewTextWidth() { return Math.max(20, left() - 126); }
    private int[] componentBounds(String part) {
        return componentBounds(new Selection(selected, part));
    }
    private int[] componentBounds(Selection ref) {
        if (!scene.isEmpty()) {
            var part = scene.stream().filter(item -> item.slot().equals(ref.slot()) && item.kind().equals(ref.kind()))
                    .findFirst().orElse(null);
            if (part != null) {
                var style = drafts.get(ref.slot()).preview();
                boolean icon = ref.kind().equals("icon");
                double s = previewScale();
                double w = icon ? (style.iconWidth() > 0 ? style.iconWidth() : part.width()) * style.iconScale()
                        : style.textWidth() > 0 ? style.textWidth() : part.width();
                double h = icon ? (style.iconHeight() > 0 ? style.iconHeight() : part.height()) * style.iconScale()
                        : style.textHeight() > 0 ? style.textHeight() : font.lineHeight * style.textScale();
                double x = icon ? part.x() + part.width() / 2. + style.iconOffsetX() - w / 2
                        : part.x() + style.textOffsetX() - (part.centered() ? w / 2 : 0);
                double y = icon ? part.y() + part.height() / 2. + style.iconOffsetY() - h / 2
                        : part.y() + style.textOffsetY();
                return new int[]{(int) Math.round(previewOriginX() + x * s),
                        (int) Math.round(previewOriginY() + y * s),
                        Math.max(1, (int) Math.round(w * s)), Math.max(1, (int) Math.round(h * s))};
            }
        }
        String part = ref.kind();
        var value = previewValue();
        if (part.equals("icon")) {
            int w = Math.max(1, Math.round((value.iconWidth() > 0 ? value.iconWidth() : 28) * value.iconScale()));
            int h = Math.max(1, Math.round((value.iconHeight() > 0 ? value.iconHeight() : 28) * value.iconScale()));
            return new int[]{previewIconX() + 14 + value.iconOffsetX() - w / 2,
                    previewIconY() + 14 + value.iconOffsetY() - h / 2, w, h};
        }
        int w = value.textWidth() > 0 ? value.textWidth() : previewTextWidth();
        int h = value.textHeight() > 0 ? value.textHeight() : Math.round(font.lineHeight * value.textScale());
        return new int[]{previewTextX() + value.textOffsetX(), previewTextY() + value.textOffsetY(), w, h};
    }
    private int[] selectionBounds() {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (var ref : selections) {
            int[] b = componentBounds(ref);
            x = Math.min(x, b[0]); y = Math.min(y, b[1]); right = Math.max(right, b[0] + b[2]);
            bottom = Math.max(bottom, b[1] + b[3]);
        }
        return new int[]{x, y, Math.max(1, right - x), Math.max(1, bottom - y)};
    }
    private boolean inPreview(double x, double y) {
        return x >= 12 && x < left() - 20 && y >= 72 && y < bodyBottom();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked() || pending != null) return true;
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0 || !inPreview(x, y)) return false;
        boolean additive = multiSelect || Screen.hasControlDown();
        int[] group = selectionBounds();
        boolean groupRight = Math.abs(x - group[0] - group[2]) <= 5 && y >= group[1] && y <= group[1] + group[3] + 5;
        boolean groupBottom = Math.abs(y - group[1] - group[3]) <= 5 && x >= group[0] && x <= group[0] + group[2] + 5;
        if (groupRight || groupBottom) {
            beginDrag(x, y, groupRight && groupBottom ? "ratio" : groupRight ? "width" : "height");
            return true;
        }
        var candidates = new ArrayList<Selection>();
        if (scene.isEmpty()) {
            candidates.add(new Selection(selected, selectedPart));
            candidates.add(new Selection(selected, selectedPart.equals("icon") ? "text" : "icon"));
        } else {
            for (var part : scene) if (availableSlots.contains(part.slot()))
                candidates.add(new Selection(part.slot(), part.kind()));
            Collections.reverse(candidates);
        }
        for (Selection ref : candidates) {
            int[] b = componentBounds(ref);
            if (x < b[0] - 5 || x > b[0] + b[2] + 5 || y < b[1] - 5 || y > b[1] + b[3] + 5) continue;
            if (additive && selections.contains(ref)) {
                beginDrag(x, y, "move"); pendingToggle = ref; return true;
            }
            if (!additive && !selections.contains(ref)) selections.clear();
            selections.add(ref);
            selected = ref.slot(); selectedPart = ref.kind();
            init();
            boolean right = Math.abs(x - b[0] - b[2]) <= 6;
            boolean bottom = Math.abs(y - b[1] - b[3]) <= 6;
            beginDrag(x, y, right && bottom ? "ratio" : right ? "width" : bottom ? "height" : "move");
            return true;
        }
        return true;
    }
    private void beginDrag(double x, double y, String mode) {
        dragMode = mode; dragX = x; dragY = y;
        pendingToggle = null; selectionDragged = false;
        dragGroupBounds = selectionBounds();
        startWidth = dragGroupBounds[2]; startHeight = dragGroupBounds[3];
        dragValues.clear(); dragBounds.clear();
        for (var ref : selections) {
            dragValues.put(ref, drafts.get(ref.slot()).preview());
            dragBounds.put(ref, componentBounds(ref));
        }
        setFocused(null);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragMode.isBlank()) return super.mouseDragged(x, y, button, dx, dy);
        if (!selectionDragged && Math.hypot(x - dragX, y - dragY) < 2) return true;
        if (!selectionDragged) pushUndo();
        selectionDragged = true; pendingToggle = null;
        int mx = (int) Math.round(x - dragX), my = (int) Math.round(y - dragY);
        double sx = 1, sy = 1;
        if (!dragMode.equals("move")) {
            if (!dragMode.equals("height")) sx = Math.max(.1, (startWidth + mx) / (double) startWidth);
            if (!dragMode.equals("width")) sy = Math.max(.1, (startHeight + my) / (double) startHeight);
            if (dragMode.equals("ratio")) {
                double fx = mx / (double) startWidth, fy = my / (double) startHeight;
                sx = sy = Math.max(.1, 1 + (Math.abs(fx) >= Math.abs(fy) ? fx : fy));
            }
        }
        for (var ref : selections) {
            var before = dragValues.get(ref);
            var draft = drafts.get(ref.slot());
            var b = dragBounds.get(ref);
            boolean icon = ref.kind().equals("icon");
            double offsetX = icon ? before.iconOffsetX() : before.textOffsetX();
            double offsetY = icon ? before.iconOffsetY() : before.textOffsetY();
            if (dragMode.equals("move")) {
                offsetX += mx / previewScale(); offsetY += my / previewScale();
            } else {
                double w = Math.max(4, b[2] * sx), h = Math.max(4, b[3] * sy);
                double factor = previewScale() * (icon ? before.iconScale() : 1);
                if (icon) {
                    draft.iw = Integer.toString(Math.clamp((int) Math.round(w / factor), 1, 2048));
                    draft.ih = Integer.toString(Math.clamp((int) Math.round(h / factor), 1, 2048));
                } else {
                    draft.tw = Integer.toString(Math.clamp((int) Math.round(w / factor), 1, 2048));
                    draft.th = Integer.toString(Math.clamp((int) Math.round(h / factor), 1, 2048));
                }
                double ax = icon || scene.stream().anyMatch(p -> p.slot().equals(ref.slot())
                        && p.kind().equals(ref.kind()) && p.centered()) ? .5 : 0;
                offsetX += ((b[0] - dragGroupBounds[0]) * (sx - 1) + ax * (w - b[2])) / previewScale();
                offsetY += ((b[1] - dragGroupBounds[1]) * (sy - 1) + (icon ? .5 : 0) * (h - b[3])) / previewScale();
            }
            String ox = Integer.toString(Math.clamp((int) Math.round(offsetX), -256, 256));
            String oy = Integer.toString(Math.clamp((int) Math.round(offsetY), -256, 256));
            if (icon) { draft.ix = ox; draft.iy = oy; }
            else { draft.tx = ox; draft.ty = oy; }
            draft.overridden = true;
        }
        syncCurrentFields();
        return true;
    }
    private void syncCurrentFields() {
        var draft = current();
        syncingFields = true;
        try {
            for (var pair : Map.of("icon_x", draft.ix, "icon_y", draft.iy, "icon_width", draft.iw,
                    "icon_height", draft.ih, "text_x", draft.tx, "text_y", draft.ty,
                    "text_width", draft.tw, "text_height", draft.th).entrySet()) {
                if (fields.containsKey(pair.getKey())) fields.get(pair.getKey()).setValue(pair.getValue());
            }
        } finally { syncingFields = false; }
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (!dragMode.isBlank()) {
            dragMode = "";
            if (pendingToggle != null && selections.size() > 1 && !selectionDragged) {
                selections.remove(pendingToggle);
                var primary = selections.stream().reduce((first, last) -> last).orElseThrow();
                selected = primary.slot(); selectedPart = primary.kind();
                init();
            }
            pendingToggle = null;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked() || pending != null) return true;
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (x >= left() && x < left() + contentWidth() + 10 && y >= bodyTop() && y < bodyBottom()) {
            scroll.wheel(vertical * 28); return true;
        }
        return false;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!(getFocused() instanceof EditBox) && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_Z) {
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) redoEdit();
                else undoEdit();
                return true;
            }
            if (key == GLFW.GLFW_KEY_Y) { redoEdit(); return true; }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void onClose() { if (pending == null) closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
