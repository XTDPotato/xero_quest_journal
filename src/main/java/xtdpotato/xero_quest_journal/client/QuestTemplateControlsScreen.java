package xtdpotato.xero_quest_journal.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestTemplate;

import java.util.*;

/** Draft-only editor: cancel never changes the shared template on disk. */
final class QuestTemplateControlsScreen extends AnimatedQuestScreen {
    @Override protected int animationHeaderBottom() { return 26; }
    @Override protected int animationFooterTop() { return bottom(); }

    private static final List<String> KINDS = List.of("text", "image", "video", "audio", "linear", "relative",
            "quest_title", "quest_type", "quest_status", "quest_progress", "quest_description",
            "quest_icon", "quest_rewards", "quest_objective", "quest_place", "quest_entry_text", "button",
            "structure_preview", "structure_enter");
    private final QuestTemplateEditorScreen parent;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private final List<QuestTemplate.Element> elements;
    private final Map<String, String> draft = new LinkedHashMap<>();
    private final Map<AbstractWidget, Integer> fields = new LinkedHashMap<>();
    private final Set<AbstractWidget> unlabeled = new HashSet<>();
    private int selected;
    private int rowScroll;
    private int propertyScroll;
    private final SmoothScroll rowMotion = new SmoothScroll(), propertyMotion = new SmoothScroll();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private int dragPane = -1;
    private boolean dragMoved;
    private double dragX, dragY, dragStart;
    private String error = "";
    private QuestScreen preview;
    private QuestTemplate previewed;
    private final SmoothScroll previewMotion = new SmoothScroll();
    private boolean properties;
    private final List<StyledButton> contextMenu = new ArrayList<>();
    private int contextX, contextY;
    private int previewDragIndex = -1;
    private boolean previewMoved;
    private double previewDragX, previewDragY;
    private int previewStartX, previewStartY;

    QuestTemplateControlsScreen(QuestTemplateEditorScreen parent, List<QuestTemplate.Element> elements) {
        super(QuestTemplateEditorScreen.text("controls"));
        this.parent = parent;
        this.elements = new ArrayList<>(elements);
        selected = elements.isEmpty() ? -1 : 0;
        loadDraft();
    }

    private boolean wide() { return width >= 720; }
    private int split() { return Math.min(180, width / 3); }
    private int propertyLeft() { return wide() ? width - Math.min(270, width / 3) : split() + 8; }
    private int fieldWidth() { return width - propertyLeft() - 28; }
    private int visibleRows() { return Math.max(1, (height - 104) / 22); }
    private int bottom() { return height - 46; }
    private Viewport fieldViewport() { return new Viewport(propertyLeft() + 8, 54, width - 12, bottom()); }
    private Viewport previewViewport() {
        return viewport(split() + 14, 58, wide() ? propertyLeft() - 12 : width - 18, bottom() - 4);
    }
    private boolean showProperties() { return wide() || properties; }
    @Override protected Viewport widgetViewport(AbstractWidget widget) {
        return fields.containsKey(widget) ? showProperties() ? fieldViewport() : viewport(0, 0, 0, 0)
                : rowWidgets.contains(widget) ? viewport(12, 54, split() - 8, bottom()) : null;
    }

    private void loadDraft() {
        draft.clear();
        propertyScroll = 0;
        if (selected < 0) return;
        var e = elements.get(selected);
        draft.put("id", e.id());
        draft.put("kind", e.kind());
        draft.put("parent", e.parent());
        if (e.kind().equals("video") || e.kind().equals("audio"))
            draft.put("displayText", e.attributes().getOrDefault("displayText", ""));
        draft.put("value", e.value());
        draft.put("x", Integer.toString(e.x()));
        draft.put("y", Integer.toString(e.y()));
        draft.put("width", Integer.toString(e.width()));
        draft.put("height", Integer.toString(e.height()));
        draft.put("attributes", new Gson().toJson(e.attributes()));
        if (e.kind().equals("quest_entry_text")) {
            draft.put("entryIcon", e.attributes().getOrDefault("entryIcon", "minecraft:paper"));
            draft.put("entryProgress", e.attributes().getOrDefault("entryProgress", "0/1"));
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        contextMenu.clear();
        fields.clear();
        rowWidgets.clear();
        unlabeled.clear();
        addRenderableWidget(new JournalNavButton(12, 28, split() - 20, 24,
                text("add"), ignored -> add(), styles, "template-element.add", JournalNavButton.Kind.ACTION, "add"));
        addRenderableWidget(new JournalNavButton(width - 38, 8, 24, 24,
                Component.translatable("gui.cancel"), ignored -> onClose(), styles,
                "template-close", JournalNavButton.Kind.ICON, "close"));
        if (!wide()) addRenderableWidget(new JournalNavButton(width - 68, 8, 24, 24,
                QuestTemplateEditorScreen.text("properties"), ignored -> { properties = !properties; init(); },
                styles, "template-properties", JournalNavButton.Kind.ICON, "settings"));
        rowMotion.limit(elements.size() * 22 - (bottom() - 54));
        rowMotion.jump(rowScroll);
        rowScroll = rowMotion.pixels();
        for (int row = 0; row < elements.size(); row++) {
            int index = row;
            var e = elements.get(index);
            var button = new StyledButton(12, 54 + row * 22 - rowScroll, split() - 20, 20,
                    Component.literal(e.id()), ignored -> {
                if (!commit()) return;
                selected = index;
                loadDraft();
                init();
            }, styles, "template-control-" + e.id());
            button.defaultCss("button:selected { background-color:#e4e7e8; color:#30343b; }");
            button.setSelectionHighlighted(index == selected);
            button.clipTo(12, 54, split() - 8, bottom());
            rowWidgets.add(addRenderableWidget(button));
        }
        int index = 0;
        for (var entry : draft.entrySet()) {
            int y = 70 + index++ * 38;
            if (entry.getKey().equals("kind")) {
                var choice = new StyledChoiceButton<>(propertyLeft() + 12, y, fieldWidth(), 20,
                        text("kind"), KINDS, entry.getValue(),
                        value -> Component.translatable("screen.xero_quest_journal.template.kind." + value),
                        value -> {
                            draft.put("kind", value);
                            if (value.equals("quest_entry_text")) {
                                draft.putIfAbsent("entryIcon", "minecraft:paper");
                                draft.putIfAbsent("entryProgress", "0/1");
                                Map<String, String> attrs = new LinkedHashMap<>();
                                attrs.put("layout_width", "-1");
                                attrs.put("layout_height", "24");
                                attrs.put("background", "#80000000");
                                attrs.put("padding", "4");
                                attrs.put("entryIcon", draft.get("entryIcon"));
                                attrs.put("entryProgress", draft.get("entryProgress"));
                                draft.put("attributes", new Gson().toJson(attrs));
                            } else {
                                draft.remove("entryIcon");
                                draft.remove("entryProgress");
                            }
                            if (value.equals("video") || value.equals("audio")) {
                                var reordered = new LinkedHashMap<String, String>();
                                draft.forEach((key, existing) -> {
                                    if (key.equals("value")) reordered.put("displayText",
                                            draft.getOrDefault("displayText", ""));
                                    if (!key.equals("displayText")) reordered.put(key, existing);
                                });
                                draft.clear();
                                draft.putAll(reordered);
                            } else draft.remove("displayText");
                            init();
                        }, styles, "template-element-kind");
                fields.put(addRenderableWidget(choice), y);
                continue;
            }
            boolean image = entry.getKey().equals("value") && draft.get("kind").equals("image")
                    || entry.getKey().equals("entryIcon");
            boolean media = entry.getKey().equals("value")
                    && (draft.get("kind").equals("video") || draft.get("kind").equals("audio"));
            var box = new SuggestedEditBox(this, font, propertyLeft() + 12, y,
                    fieldWidth() - (image || media ? 28 : 0), 20,
                    media ? text("media_source") : text(entry.getKey()), entry.getKey());
            if (image || media) box.withoutMenu();
            box.setMaxLength(entry.getKey().equals("attributes") ? 32768 : image ? 8192 : 4096);
            box.setValue(entry.getValue());
            box.setResponder(value -> draft.put(entry.getKey(), value));
            fields.put(addRenderableWidget(box), box.getY());
            if (image) {
                String resourceKey = entry.getKey();
                var pick = new StyledButton(width - 40, y, 24, 20, Component.literal("..."),
                        ignored -> minecraft.setScreen(QuestScreen.pickResource(this, draft.get(resourceKey), false,
                                value -> draft.put(resourceKey, value))), styles, "template-element-image", "toolbar-button");
                pick.leadingIcon(entry.getValue());
                pick.setTooltip(Tooltip.create(Component.translatable("screen.xero_quest_journal.icon_picker.choose")));
                fields.put(addRenderableWidget(pick), y);
                unlabeled.add(pick);
            } else if (media) {
                String kind = draft.get("kind");
                int control = selected;
                var pick = new StyledButton(width - 40, y, 24, 20, Component.literal("..."),
                        ignored -> QuestMediaPicker.choose(this, kind.equals("video"), value -> {
                            if (selected != control || !Objects.equals(draft.get("kind"), kind)) return;
                            draft.put("value", value);
                            init();
                        }),
                        styles, "template-element-media", "toolbar-button");
                pick.setTooltip(Tooltip.create(Component.translatable(
                        "screen.xero_quest_journal.layout.choose_media")));
                fields.put(addRenderableWidget(pick), y);
                unlabeled.add(pick);
            }
        }
        addRenderableWidget(new JournalNavButton(12, height - 32, 24, 24, text("remove"), ignored -> remove(),
                styles, "template-element.remove", JournalNavButton.Kind.ICON, "delete")).active = selected >= 0;
        addRenderableWidget(new JournalNavButton(44, height - 32, 24, 24,
                QuestTemplateEditorScreen.text("graph"), ignored -> editGraph(), styles,
                "template-element-graph", JournalNavButton.Kind.ICON, "visual")).active = selected >= 0;
        button("save", width - 180, height - 32, 80, () -> {
            if (commit()) { parent.updateTemplateElements(elements); minecraft.setScreen(parent); }
        });
        button("cancel", width - 92, height - 32, 80, this::onClose);
        propertyMotion.limit(Math.max(0, 70 + draft.size() * 38 - bottom()));
        propertyMotion.jump(propertyScroll);
        updateScroll();
        addRenderableWidget(new ScrollRail(split() - 8, 54, bottom() - 54, rowMotion));
        if (showProperties()) addRenderableWidget(new ScrollRail(width - 10, 54, bottom() - 54, propertyMotion));
        else addRenderableWidget(new ScrollRail(width - 10, 58, bottom() - 58, previewMotion));
        updatePreview();
    }

    private Component text(String key) { return QuestTemplateEditorScreen.text("element." + key); }

    private StyledButton button(String key, int x, int y, int w, Runnable action) {
        if (key.equals("cancel")) {
            return addRenderableWidget(new StyledButton(x, y, w, 24, QuestTemplateEditorScreen.text(key),
                    ignored -> action.run(), styles, "template-cancel", "toolbar-button"));
        }
        return addRenderableWidget(new JournalNavButton(x, y, w, 24, QuestTemplateEditorScreen.text(key),
                ignored -> action.run(), styles, "template-" + key, JournalNavButton.Kind.ACTION,
                "save").compactContent());
    }

    private List<QuestTemplate.Element> candidateElements() {
        if (selected < 0) return List.copyOf(elements);
            Map<String, String> attrs = new LinkedHashMap<>();
            JsonParser.parseString(draft.get("attributes")).getAsJsonObject().entrySet().forEach(entry -> {
                if (!entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("Attribute must be scalar");
                attrs.put(entry.getKey(), entry.getValue().getAsString());
            });
            if (draft.get("kind").equals("quest_entry_text")) {
                attrs.put("entryIcon", draft.getOrDefault("entryIcon", "minecraft:paper"));
                attrs.put("entryProgress", draft.getOrDefault("entryProgress", "0/1"));
                attrs.putIfAbsent("background", "#80000000");
            }
            if (draft.get("kind").equals("video") || draft.get("kind").equals("audio")) {
                boolean video = draft.get("kind").equals("video");
                if (video ? !ClientMediaManager.validVideoSource(draft.get("value"))
                        : !ClientMediaManager.validAudioSource(draft.get("value")))
                    throw new IllegalArgumentException("Invalid media source");
                attrs.put("displayText", draft.getOrDefault("displayText", ""));
            }
            var next = new QuestTemplate.Element(draft.get("id").strip(), draft.get("kind").strip(), draft.get("value"),
                    Integer.parseInt(draft.get("x").strip()), Integer.parseInt(draft.get("y").strip()),
                    Integer.parseInt(draft.get("width").strip()), Integer.parseInt(draft.get("height").strip()),
                    attrs, draft.get("parent").strip());
            var candidate = new ArrayList<>(elements);
            var old = candidate.set(selected, next);
            if (!next.id().equals(old.id())) {
                candidate.replaceAll(e -> e.parent().equals(old.id())
                        ? new QuestTemplate.Element(e.id(), e.kind(), e.value(), e.x(), e.y(),
                        e.width(), e.height(), e.attributes(), next.id()) : e);
            }
            // Reuse the model's duplicate, parent-type, cycle and size validation.
            new QuestTemplate("validation", "", "", "commission", "", "", 1, "", 1, "", "", candidate);
            return List.copyOf(candidate);
    }

    private boolean commit() {
        try {
            var candidate = candidateElements();
            elements.clear(); elements.addAll(candidate);
            error = "";
            return true;
        } catch (RuntimeException invalid) {
            error = "invalid";
            return false;
        }
    }

    private void editGraph() {
        if (selected < 0 || !commit()) return;
        var element = elements.get(selected);
        var gson = new Gson();
        var graph = gson.fromJson(element.attributes().getOrDefault("eventGraph", "{\"nodes\":[],\"edges\":[]}"),
                xtdpotato.xero_quest_journal.core.model.TriggerGraph.class);
        var controls = new LinkedHashMap<String, xtdpotato.xero_quest_journal.core.model.QuestPresentation.Control>();
        for (var e : elements) controls.put(e.id(),
                new xtdpotato.xero_quest_journal.core.model.QuestPresentation.Control(e.id(), e.kind(), e.value(),
                        e.x(), e.y(), e.width(), e.height(), e.parent(), e.attributes(), graph));
        minecraft.setScreen(new QuestTriggerGraphScreen(this, graph, element.id(), controls, updated -> {
            var attrs = new LinkedHashMap<>(element.attributes());
            attrs.put("eventGraph", gson.toJson(updated));
            elements.set(selected, new QuestTemplate.Element(element.id(), element.kind(), element.value(),
                    element.x(), element.y(), element.width(), element.height(), attrs, element.parent()));
            loadDraft();
        }));
    }

    private void updatePreview() {
        try {
            var next = parent.previewTemplate(candidateElements());
            if (!next.equals(previewed)) {
                if (preview == null) { preview = QuestScreen.templatePreview(next); preview.init(minecraft, width, height); }
                else preview.updateTemplatePreview(next);
                previewed = next;
            }
            previewMotion.limit(preview.templatePreviewMaxScroll(previewViewport()));
        } catch (RuntimeException incompleteDraft) {
            // Keep the last valid canvas while a numeric/JSON field is being typed.
        }
    }

    private void add() {
        if (!commit()) return;
        String id = "control_" + UUID.randomUUID().toString().substring(0, 8);
        elements.add(new QuestTemplate.Element(id, "text", "", 0, 0, 140, 24, Map.of(), ""));
        selected = elements.size() - 1;
        rowScroll = Math.max(0, elements.size() * 22 - (bottom() - 54));
        loadDraft();
        init();
    }

    private void remove() {
        if (selected < 0) return;
        String removed = elements.remove(selected).id();
        elements.replaceAll(e -> e.parent().equals(removed) ? new QuestTemplate.Element(e.id(), e.kind(),
                e.value(), e.x(), e.y(), e.width(), e.height(), e.attributes(), "") : e);
        selected = Math.min(selected, elements.size() - 1);
        loadDraft();
        init();
    }

    private void updateScroll() {
        propertyScroll = Math.max(0, Math.min(propertyScroll, Math.max(0, 70 + draft.size() * 38 - bottom())));
        fields.forEach((box, originalY) -> {
            box.setY(originalY - propertyScroll);
            box.visible = showProperties() && fieldViewport().intersects(box);
            if (!box.visible && getFocused() == box) setFocused(null);
        });
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (scrollChoicePopup(x, y, horizontal, vertical)) return true;
        if (x >= 12 && x < split() - 8 && y >= 54 && y < bottom()) {
            rowMotion.wheel(vertical * 44);
            return true;
        }
        if (showProperties() && fieldViewport().contains(x, y)) {
            propertyMotion.wheel(vertical * 30);
            return true;
        }
        if ((!properties || wide()) && previewViewport().contains(x, y)) {
            previewMotion.wheel(vertical * 30); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    protected void renderContents(GuiGraphics g, int x, int y, float delta) {
        if (hasChoicePopup()) { rowMotion.freeze(); propertyMotion.freeze(); }
        rowMotion.advance(); propertyMotion.advance(); previewMotion.advance();
        moveContent();
        updatePreview();
        super.renderContents(g, x, y, delta);
        g.drawString(font, title, 16, 10, 0xFFF2D29A);
        g.fill(split(), 28, split() + 1, bottom(), 0xFF81858B);
        if ((!properties || wide()) && preview != null) {
            g.drawString(font, QuestTemplateEditorScreen.text("preview"), split() + 14, 38, 0xFFD5DEE8);
            preview.renderTemplatePreview(g, previewViewport(), previewMotion.position(), x, y,
                    selected < 0 ? "" : draft.get("id"));
        }
        if (wide()) g.fill(propertyLeft(), 28, propertyLeft() + 1, bottom(), 0x408FA5AA);
        if (showProperties()) g.drawString(font, QuestTemplateEditorScreen.text("properties"),
                propertyLeft() + 12, 38, 0xFFF2D29A);
        g.enableScissor(propertyLeft() + 8, 54, width - 12, bottom());
        fields.forEach((box, unused) -> {
            if (box.visible && !unlabeled.contains(box)) g.drawString(font, box instanceof StyledChoiceButton<?> ? text("kind")
                    : box.getMessage(), box.getX(), box.getY() - 12, 0xFFE4EAF2);
        });
        g.disableScissor();
        if (!error.isEmpty()) g.drawCenteredString(font, QuestTemplateEditorScreen.text("error." + error),
                width / 2, height - 42, 0xFFFF7777);
        renderAnimatedMenu(g, "context", contextMenu.isEmpty() ? null : contextMenu.getFirst(),
                contextMenu.isEmpty() ? viewport(0, 0, 0, 0) : viewport(contextX, contextY,
                        contextMenu.getFirst().getRight(), contextMenu.getLast().getBottom()), () -> {
                    g.pose().pushPose(); g.pose().translate(0, 0, 1000);
                    contextMenu.forEach(button -> button.render(g, x, y, delta));
                    g.pose().popPose();
                });
    }
    private void moveContent() {
        int offset = rowScroll - rowMotion.pixels();
        rowWidgets.forEach(widget -> widget.setY(widget.getY() + offset));
        rowScroll = rowMotion.pixels();
        propertyScroll = propertyMotion.pixels();
        updateScroll();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (clickChoicePopup(x, y, button)) return true;
        if (!contextMenu.isEmpty()) {
            if (button == 0) for (var entry : List.copyOf(contextMenu))
                if (entry.mouseClicked(x, y, button)) return true;
            hideContextMenu();
            return true;
        }
        boolean list = x >= 12 && x < split() - 8 && y >= 54 && y < bottom();
        if (button == 1 && list) {
            int index = (int) Math.floor(rowMotion.position() / 22 + (y - 54) / 22);
            if (index >= 0 && index < elements.size()) {
                if (!commit()) return true;
                selected = index;
                loadDraft();
                showContextMenu((int) x, (int) y);
            }
            return true;
        }
        if (button == 1 && (!properties || wide()) && preview != null && previewViewport().contains(x, y)) {
            String id = preview.templatePreviewHit(x, y);
            if (!id.isBlank()) {
                if (!commit()) return true;
                for (int i = 0; i < elements.size(); i++) {
                    if (elements.get(i).id().equals(id)) {
                        selected = i;
                        loadDraft();
                        showContextMenu((int) x, (int) y);
                        break;
                    }
                }
            }
            return true;
        }
        if (button == 0 && (!properties || wide()) && preview != null && previewViewport().contains(x, y)) {
            String id = preview.templatePreviewHit(x, y);
            if (!id.isBlank() && commit()) {
                for (int i = 0; i < elements.size(); i++) if (elements.get(i).id().equals(id)) {
                    selected = i;
                    previewDragIndex = i;
                    previewMoved = false;
                    previewDragX = x;
                    previewDragY = y;
                    previewStartX = elements.get(i).x();
                    previewStartY = elements.get(i).y();
                    loadDraft();
                    break;
                }
            }
            return true;
        }
        if (button == 0 && (list || showProperties() && fieldViewport().contains(x, y))
                && !(getChildAt(x, y).orElse(null) instanceof EditBox)) {
            dragPane = list ? 0 : 1; dragMoved = false;
            dragX = x; dragY = y; dragStart = (list ? rowMotion : propertyMotion).position();
            (list ? rowMotion : propertyMotion).freeze();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private void showContextMenu(int mouseX, int mouseY) {
        hideContextMenu();
        int width = Math.min(180, this.width - 16);
        contextX = Math.max(8, Math.min(mouseX, this.width - width - 8));
        contextY = Math.max(8, Math.min(mouseY, this.height - 3 * 20 - 8));
        contextMenu.add(new StyledButton(contextX, contextY, width, 20,
                QuestTemplateEditorScreen.text("properties"), ignored -> {
                    hideContextMenu();
                    properties = true;
                    init();
                }, styles, "template-context-properties", "toolbar-button"));
        contextMenu.add(new StyledButton(contextX, contextY + 20, width, 20,
                QuestTemplateEditorScreen.text("graph"), ignored -> {
                    hideContextMenu();
                    editGraph();
                }, styles, "template-context-graph", "toolbar-button"));
        contextMenu.add(new StyledButton(contextX, contextY + 40, width, 20,
                text("remove"), ignored -> {
                    hideContextMenu();
                    remove();
                }, styles, "template-context-remove", "toolbar-button"));
    }

    private void hideContextMenu() {
        for (var widget : List.copyOf(contextMenu)) removeWidget(widget);
        contextMenu.clear();
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragChoicePopup(x, y, button, dx, dy)) return true;
        if (button == 0 && previewDragIndex >= 0) {
            if (Math.abs(x - previewDragX) >= 3 || Math.abs(y - previewDragY) >= 3) previewMoved = true;
            if (previewMoved) {
                int scaleX = Math.max(1, previewViewport().right() - previewViewport().left());
                int scaleY = Math.max(1, previewViewport().bottom() - previewViewport().top());
                int nextX = previewStartX + (int) Math.round((x - previewDragX) * 395.0 / scaleX);
                int nextY = previewStartY + (int) Math.round((y - previewDragY) * 275.0 / scaleY);
                var current = elements.get(previewDragIndex);
                elements.set(previewDragIndex, new QuestTemplate.Element(current.id(), current.kind(),
                        current.value(), nextX, nextY, current.width(), current.height(),
                        current.attributes(), current.parent()));
                loadDraft();
                updatePreview();
            }
            return true;
        }
        if (button == 0 && dragPane >= 0) {
            dragMoved |= Math.abs(y - dragY) >= 4;
            if (dragMoved) { (dragPane == 0 ? rowMotion : propertyMotion).jump(dragStart + dragY - y); moveContent(); }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (releaseChoicePopup(x, y, button)) return true;
        if (!contextMenu.isEmpty()) {
            for (var entry : List.copyOf(contextMenu)) entry.mouseReleased(x, y, button);
            return true;
        }
        if (button == 0 && previewDragIndex >= 0) {
            int index = previewDragIndex;
            boolean moved = previewMoved;
            previewDragIndex = -1;
            previewMoved = false;
            if (!moved) init();
            return true;
        }
        if (button == 0 && dragPane >= 0) {
            dragPane = -1;
            if (!dragMoved && Math.abs(x - dragX) < 4) {
                super.mouseClicked(dragX, dragY, button); super.mouseReleased(x, y, button);
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) {
        super.renderBackground(g, x, y, delta);
        backdrop(g, JOURNAL_BACKDROP);
        g.fill(0, 0, width, 26, JOURNAL_HEADER);
        MailDecor.divider(g, 12, width - 12, bottom() + 3, 0x408FA5AA);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (inputBlocked()) return true;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !contextMenu.isEmpty()) {
            hideContextMenu();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { closeTo(parent); }
    @Override public void removed() {
        if (preview != null) preview.removed();
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
