package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import xtdpotato.xero_quest_journal.core.runtime.QuestLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Portable place targets use registry identities, not author-world search results. */
public final class QuestLocationScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final Consumer<String> changed;
    private String mode = "coords", dimension = "minecraft:overworld", target = "";
    private String x = "0", y = "64", z = "0", radius = "8";
    private ButtonStyleSheet styles;
    private String error;
    private boolean targetOnly;
    private StyledButton previewButton;

    public static QuestLocationScreen pickTarget(Screen parent, String kind, String target, Consumer<String> changed) {
        if (!List.of("structure", "biome").contains(kind)) throw new IllegalArgumentException("Invalid target kind");
        var screen = new QuestLocationScreen(parent, "", changed);
        screen.mode = kind;
        screen.target = target == null ? "" : target;
        screen.dimension = "";
        screen.targetOnly = true;
        return screen;
    }

    public QuestLocationScreen(Screen parent, String encoded, Consumer<String> changed) {
        super(text("location.title"));
        this.parent = parent;
        this.changed = changed;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            dimension = player.level().dimension().location().toString();
            x = Integer.toString(player.blockPosition().getX());
            y = Integer.toString(player.blockPosition().getY());
            z = Integer.toString(player.blockPosition().getZ());
        }
        var saved = QuestLocation.parse(encoded);
        if (saved != null) {
            mode = saved.kind(); dimension = saved.dimension(); target = saved.target();
            if (saved.resolved()) {
                radius = Double.toString(saved.radius());
                x = Double.toString(saved.x()); y = Double.toString(saved.y()); z = Double.toString(saved.z());
            }
        }
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal." + key);
    }
    private int panelWidth() { return Math.min(520, width - 24); }
    private int left() { return (width - panelWidth()) / 2; }

    @Override protected void init() {
        clearWidgets();
        styles = ButtonStyleSheet.load();
        int lx = left(), w = panelWidth(), split = (w - 8) / 2;
        var kind = addRenderableWidget(new StyledChoiceButton<>(lx, 44, split, 22, text("location.kind"),
                List.of("coords", "structure", "biome"), mode, value -> text("location." + value),
                value -> {
                    if (mode.equals("coords") && !value.equals("coords")) dimension = "";
                    if (value.equals("coords") && dimension.isBlank()) dimension = minecraft.player == null
                            ? "minecraft:overworld" : minecraft.player.level().dimension().location().toString();
                    mode = value; target = ""; error = null; init();
                },
                styles, "location-kind"));
        kind.active = !targetOnly;
        var dimensions = new SuggestedEditBox(this, font, lx + split + 8, 44, w - split - 8, 22,
                text(mode.equals("coords") ? "location.dimension" : "location.dimension_optional"), "dimension")
                .options(this::dimensionOptions);
        dimensions.setMaxLength(256);
        dimensions.setValue(dimension);
        if (!mode.equals("coords")) dimensions.setHint(text("location.any_dimension"));
        dimensions.setResponder(value -> dimension = value);
        dimensions.setEditable(!targetOnly);
        addRenderableWidget(dimensions);
        if (mode.equals("coords")) {
            int cell = (w - 18) / 4;
            number("location.x", x, lx, 88, cell, value -> x = value);
            number("location.y", y, lx + cell + 6, 88, cell, value -> y = value);
            number("location.z", z, lx + (cell + 6) * 2, 88, cell, value -> z = value);
            number("location.radius", radius, lx + (cell + 6) * 3, 88, cell, value -> radius = value);
        } else {
            var targets = new SuggestedEditBox(this, font, lx, 88, w, 20,
                    text("location.choose_target"), mode).options(this::targetOptions);
            targets.setMaxLength(256);
            targets.setValue(target);
            targets.setResponder(value -> {
                target = value;
                if (previewButton != null) previewButton.active = !target.isBlank();
            });
            if (targetOnly) targets.withoutMenu();
            addRenderableWidget(targets);
            addRenderableWidget(new StyledButton(lx, 118, mode.equals("structure") ? split : w, 22,
                    text("location.choose_target"), ignored -> minecraft.setScreen(new QuestValuePickerScreen(
                    this, text("location." + mode).getString(), "", this::targetOptions, target,
                    value -> target = value)), styles, "location-choose"));
            if (mode.equals("structure")) {
                previewButton = addRenderableWidget(new StyledButton(lx + split + 8, 118, w - split - 8, 22,
                        text("structure.preview"), ignored ->
                        minecraft.setScreen(QuestStructurePickerScreen.inspectTarget(this, target)),
                        styles, "location-preview"));
                previewButton.active = !target.isBlank();
            }
        }
        var apply = new StyledButton(lx, height - 32, (w - 6) / 2, 22, text("location.apply"),
                ignored -> apply(), styles, "location-apply");
        addRenderableWidget(apply);
        addRenderableWidget(new StyledButton(lx + (w + 6) / 2, height - 32, (w - 6) / 2, 22,
                text("cancel"), ignored -> onClose(), styles, "location-cancel"));
    }

    private EditBox number(String label, String value, int lx, int ly, int w, Consumer<String> responder) {
        var field = new SuggestedEditBox(this, font, lx, ly, w, 20, text(label), label);
        field.setMaxLength(12);
        field.setValue(value);
        field.setResponder(responder);
        return addRenderableWidget(field);
    }

    private List<QuestValuePickerScreen.Option> targetOptions() {
        return targetOptions(mode);
    }

    static List<QuestValuePickerScreen.Option> targetOptions(String mode) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return List.of();
        List<QuestValuePickerScreen.Option> result = new ArrayList<>();
        var registry = mode.equals("structure") ? server.registryAccess().registryOrThrow(Registries.STRUCTURE)
                : server.registryAccess().registryOrThrow(Registries.BIOME);
        registry.keySet().stream().map(ResourceLocation::toString).sorted()
                .forEach(id -> result.add(new QuestValuePickerScreen.Option(id, id)));
        registry.getTagNames().map(tag -> "#" + tag.location()).sorted()
                .forEach(id -> result.add(new QuestValuePickerScreen.Option(id, id)));
        return result;
    }

    private List<QuestValuePickerScreen.Option> dimensionOptions() {
        List<QuestValuePickerScreen.Option> values = new ArrayList<>();
        if (!mode.equals("coords")) values.add(new QuestValuePickerScreen.Option("",
                text("location.any_dimension").getString()));
        QuestSuggestions.options("dimension", dimension).stream()
                .filter(option -> !option.value().isEmpty()).forEach(values::add);
        return values;
    }

    private void apply() {
        var value = QuestLocation.parse(mode.equals("coords")
                ? "coords|" + dimension + "|" + x + "|" + y + "|" + z + "|" + radius
                : mode + "|" + dimension + "|" + target);
        var server = minecraft.getSingleplayerServer();
        if (value == null || server == null || (!dimension.isBlank()
                && server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension))) == null)) {
            error = "location.invalid"; return;
        }
        if (!mode.equals("coords") && targetOptions().stream().noneMatch(option -> option.value().equals(target))) {
            error = "location.invalid"; return;
        }
        changed.accept(targetOnly ? value.target() : value.encode());
        closeTo(parent);
    }

    @Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderContents(graphics, mouseX, mouseY, partialTick);
        int lx = left(), w = panelWidth(), split = (w - 8) / 2;
        graphics.drawCenteredString(font, title, width / 2, 14, -1);
        graphics.drawString(font, text("location.kind"), lx, 30, 0xFFCCD5DE);
        graphics.drawString(font, text(mode.equals("coords") ? "location.dimension" : "location.dimension_optional"),
                lx + split + 8, 30, 0xFFCCD5DE);
        if (mode.equals("coords")) {
            int cell = (w - 18) / 4;
            String[] labels = {"x", "y", "z", "radius"};
            for (int i = 0; i < labels.length; i++)
                graphics.drawString(font, text("location." + labels[i]), lx + (cell + 6) * i, 74, 0xFFCCD5DE);
        } else {
            graphics.drawString(font, text("location." + mode), lx, 74, 0xFFCCD5DE);
        }
        if (error != null) graphics.drawString(font, font.plainSubstrByWidth(text(error).getString(), w),
                lx, height - 48, 0xFFFF7777);
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backdrop(graphics, 0xF01C2026);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected int animationHeaderBottom() { return 38; }
    @Override protected int animationFooterTop() { return height - 48; }
}
