package xtdpotato.xero_quest_journal.client;

import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;
import java.math.BigDecimal;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue;
import xtdpotato.xero_quest_journal.core.model.*;
import org.lwjgl.glfw.GLFW;

/** A four-column stamp book. The right page always shows progress and the milestone reward. */
public final class AnecdoteRewardsScreen extends AnimatedQuestScreen {
    private static final int WATERMARK_X = 325, WATERMARK_Y = 368;
    private static final int WATERMARK_WIDTH = 170, WATERMARK_HEIGHT = 109;
    private record RewardHit(QuestDefinition.Reward reward, int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    private final Screen parent;
    private final UiMotion bookOpen = new UiMotion(false, System.nanoTime());
    private final AnecdoteRewardAnimation stamps;
    private List<RewardHit> rewardHits = List.of();
    private List<AnecdoteSnapshot.Completed> visible = List.of();
    private int acknowledged;
    private boolean rewardPressed, itemPressed, closePressed, waitingClaim;
    private int pressedRewardIndex = -1;
    private boolean layoutEditing, layoutDragging;
    private boolean savingLayout;
    private String layoutError = "";
    private String selectedControl = "ticket";
    private double layoutGrabX, layoutGrabY;
    private AnecdoteRewardLayout rewardLayoutDraft = AnecdoteRewardLayout.defaults();
    private AnecdoteRewardLayout dragLayoutBefore;
    private final Deque<AnecdoteRewardLayout> layoutUndo = new ArrayDeque<>(), layoutRedo = new ArrayDeque<>();
    private EditBox layoutXInput, layoutYInput, layoutScaleInput;
    private AnecdoteRewardLayout numericEditBefore;
    private boolean syncingLayoutInputs;
    private long claimSentAt;
    private int left, top, widthPage, heightPage;
    private static final int CANVAS_W = 1000, CANVAS_H = 570;
    private static final int TICKET_SOURCE_WIDTH = 768, TICKET_CROP = 230, TICKET_LEFT_SLICE = 300;
    private static final float TICKET_SCALE = 400F / TICKET_SOURCE_WIDTH;
    private static final int TICKET_WIDTH = Math.round((TICKET_SOURCE_WIDTH - TICKET_CROP) * TICKET_SCALE);

    public AnecdoteRewardsScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.anecdote.rewards"));
        this.parent = parent;
        visible = AnecdoteRewardPage.ordered(AnecdoteClientState.snapshot().completed());
        var unread = AnecdoteClientState.snapshot().newCompletions();
        int baseline = visible.size();
        for (int i = 0; i < visible.size(); i++) if (unread.contains(key(visible.get(i)))) { baseline = i; break; }
        stamps = new AnecdoteRewardAnimation(baseline);
        acknowledged = baseline;
    }
    private static String key(AnecdoteSnapshot.Completed record) { return record.entryId() + "/" + record.recordId(); }
    private Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.anecdote." + key, args);
    }
    private double scale() { return Math.max(.05, Math.min(.76,
            Math.min((width - 36.) / CANVAS_W, (height - 36.) / CANVAS_H))); }
    private void layout() {
        widthPage = (int) Math.round(CANVAS_W * scale()); heightPage = (int) Math.round(CANVAS_H * scale());
        left = (width - widthPage) / 2; top = (height - heightPage) / 2;
    }
    private double localX(double x) { return (x - left) / scale(); }
    private double localY(double y) { return (y - top) / scale(); }
    private boolean closeAt(double x, double y) { return x >= 955 && x < 999 && y >= 20 && y < 64; }
    private int rewardX() { return 770 - TICKET_WIDTH / 2; }
    private boolean rewardAt(double x, double y) {
        var control = activeLayout().claim();
        int size = Math.round(49 * (float) control.scale());
        return x >= control.x() && x < control.x() + size
                && y >= control.y() && y < control.y() + size;
    }
    private int rewardIndexAt(double x, double y) {
        for (int i = 0; i < rewardHits.size(); i++) if (rewardHits.get(i).contains(x, y)) return i;
        return -1;
    }
    private void drawEditEntry(GuiGraphics g) {
        if (!canEditLayout() || layoutEditing) return;
        drawEditChip(g, 890, 78, 48, text("edit"), "edit", "");
    }
    private String claimable() {
        var claimed = AnecdoteClientState.snapshot().claimedRewards();
        for (int i = 0; i < visible.size() / 10; i++) {
            String batch = AnecdoteRewardPage.batchId(visible, i);
            if (!claimed.contains(batch)) return batch;
        }
        return "";
    }
    private AnecdoteDefinition rewardEntry() {
        int index = visible.isEmpty() ? -1 : Math.min(visible.size() - 1, (visible.size() / 10) * 10 + 9);
        String batch = claimable();
        if (!batch.isBlank()) for (int i = 0; i < visible.size() / 10; i++)
            if (AnecdoteRewardPage.batchId(visible, i).equals(batch)) { index = i * 10 + 9; break; }
        if (index >= 0) return AnecdoteClientState.find(visible.get(index).entryId()).orElse(null);
        if (parent instanceof AnecdoteScreen chronicle)
            return AnecdoteClientState.find(chronicle.selectedEntryId()).orElse(null);
        return null;
    }
    private boolean canEditLayout() {
        return ClientEditMode.enabled() && minecraft.player != null && minecraft.player.hasPermissions(2);
    }
    private AnecdoteRewardLayout activeLayout() {
        if (layoutEditing) return rewardLayoutDraft;
        var entry = rewardEntry();
        return entry == null ? AnecdoteRewardLayout.defaults() : entry.rewardLayout();
    }
    private List<QuestDefinition.Reward> rewards() {
        int index = Math.min(visible.size() - 1, (visible.size() / 10) * 10 + 9);
        String batch = claimable();
        if (!batch.isBlank()) for (int i = 0; i < visible.size() / 10; i++)
            if (AnecdoteRewardPage.batchId(visible, i).equals(batch)) { index = i * 10 + 9; break; }
        if (index >= 0) {
            String entryId = visible.get(index).entryId();
            var entry = AnecdoteClientState.find(entryId).orElse(null);
            if (entry != null) return entry.rewards();
        }
        return List.of(new QuestDefinition.Reward("item", "minecraft:diamond", 50, "legendary", ""));
    }

    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        layout();
        visible = AnecdoteRewardPage.ordered(AnecdoteClientState.snapshot().completed());
        long now = System.nanoTime();
        stamps.target(visible.size()); stamps.advance(now, ClientPreferences.get().reducedMotion);
        if (stamps.displayed() > acknowledged) {
            var read = visible.subList(Math.min(acknowledged, visible.size()), Math.min(stamps.displayed(), visible.size()))
                    .stream().map(AnecdoteRewardsScreen::key).toList();
            AnecdoteClientState.markRewardRecordsRead(read); acknowledged = stamps.displayed();
        }
        if (waitingClaim && !AnecdoteClientState.lastResult().isBlank()) {
            waitingClaim = false;
            if (!AnecdoteClientState.lastResult().equals("claimed"))
                XeroTips.show(Component.translatable("screen.xero_quest_journal.action." + AnecdoteClientState.lastResult()));
        }
        if (waitingClaim && now - claimSentAt > 5_000_000_000L) waitingClaim = false;
        if (savingLayout && !AnecdoteClientState.lastResult().isBlank()) {
            String result = AnecdoteClientState.lastResult();
            AnecdoteClientState.clearResult();
            savingLayout = false;
            if (result.equals("saved")) {
                var entry = rewardEntry();
                rewardLayoutDraft = entry == null ? AnecdoteRewardLayout.defaults() : entry.rewardLayout();
                layoutEditing = false;
                removeLayoutInputs();
                layoutUndo.clear();
                layoutRedo.clear();
                layoutError = "";
            } else {
                layoutEditing = true;
                layoutError = "保存奖励布局失败";
            }
        }
        if (!layoutEditing) {
            var entry = rewardEntry();
            rewardLayoutDraft = entry == null ? AnecdoteRewardLayout.defaults() : entry.rewardLayout();
        }
        updateLayoutInputs();
        commitNumericEditIfBlurred();
        backdrop(g, 0x00000000);
        if (!isClosingBook()) bookOpen.target(true, now, popupAnimationMillis());
        bookOpen.advance(now, popupAnimationMillis());
        double entrance = .88 + .12 * bookOpen.eased();
        double drawScale = scale() * entrance;
        g.pose().pushPose();
        g.pose().translate(width / 2., height / 2., 0);
        g.pose().scale((float) drawScale, (float) drawScale, 1);
        g.pose().translate(-CANVAS_W / 2., -CANVAS_H / 2., 0);
        image(g, "reward_book_spread", 12, 16, 966, 536, 1800, 1000);
        image(g, "title_note", 44, -7, 322, 75, 1024, 240);
        centered(g, text("rewards").copy().withStyle(net.minecraft.ChatFormatting.BOLD), 216, 23, 2.1F, 0xFF815A22);
        drawStickers(g, now);
        drawRecordPanel(g, mx, my);
        image(g, "close_tab", 955, 20, 44, 44, 96, 96);
        if (closeAt(localX(mx), localY(my))) g.renderOutline(958, 24, 36, 36, 0xFFEED7A1);
        g.pose().popPose();
        super.renderContents(g, mx, my, delta);
        int hoveredReward = rewardIndexAt(localX(mx), localY(my));
        if (hoveredReward >= 0 && minecraft.player != null) {
            ItemStack stack = rewardStack(rewardHits.get(hoveredReward).reward());
            if (!stack.isEmpty()) g.renderTooltip(font, stack, mx, my);
        }
    }
    private void drawStickers(GuiGraphics g, long now) {
        int pageStart = stamps.page() * 10;
        float clear = stamps.clear(now);
        g.setColor(1, 1, 1, .4F);
        image(g, "notebook_imprint", WATERMARK_X, WATERMARK_Y,
                WATERMARK_WIDTH, WATERMARK_HEIGHT, 790, 505);
        g.setColor(1, 1, 1, 1);
        for (int i = 0; i < 10; i++) {
            int x = 57 + (i % 4) * 122, y = 100 + (i / 4) * 137;
            g.pose().pushPose();
            g.pose().translate(x + 48, y + 48, 0);
            float clearScale = 1 - clear * .8F;
            g.pose().scale(clearScale, clearScale, 1);
            g.pose().translate(-48, -48, 0);
            g.setColor(1, 1, 1, 1 - clear);
            image(g, "sticker_empty", 0, 0, 96, 96, 192, 192);
            g.setColor(1, 1, 1, 1);
            int index = pageStart + i;
            boolean occupied = index < stamps.displayed() || index == stamps.entering();
            if (occupied && index < visible.size()) {
                var completed = visible.get(index);
                var entry = AnecdoteClientState.find(completed.entryId()).orElse(null);
                var record = entry == null ? null : entry.records().stream()
                        .filter(value -> value.id().equals(completed.recordId())).findFirst().orElse(null);
                String icon = record != null && !record.completedIcon().isBlank() ? record.completedIcon()
                        : entry == null ? "" : entry.icon();
                float progress = index == stamps.entering() ? stamps.stamp(now) : 1;
                float grow = 1.5F - .5F * (1 - (float) Math.pow(1 - progress, 3));
                g.pose().pushPose(); g.pose().translate(48, 48, 210); g.pose().scale(grow, grow, 1);
                if (icon.isBlank()) JournalNavButton.drawIcon(g, "category_anecdotes", -36, -36, 72, 0xFFB58C41);
                else QuestIcons.render(g, icon, -36, -36, 72, 72, (1 - clear) * progress);
                g.flush(); g.pose().popPose();
            }
            g.pose().popPose();
        }
    }
    private void drawRecordPanel(GuiGraphics g, int mouseX, int mouseY) {
        if (layoutEditing) {
            drawLayoutProperties(g);
        } else {
            centered(g, text("rewards.record_heading").copy().withStyle(net.minecraft.ChatFormatting.BOLD),
                    770, 73, 1.9F, 0xFF55586A);
            image(g, "seal", 632, 119, 276, 138, 512, 256);
            centered(g, Component.literal(stamps.countOnPage() + "/10").withStyle(net.minecraft.ChatFormatting.BOLD),
                    770, 160, 2.8F, 0xFF875A17);
            int lineY = 284;
            for (var line : font.split(text("rewards.progress_description", stamps.countOnPage()).copy()
                    .withStyle(net.minecraft.ChatFormatting.BOLD), 217)) {
                g.pose().pushPose(); g.pose().translate(600, lineY, 0); g.pose().scale(1.5F, 1.5F, 1);
                g.drawString(font, line, 0, 0, 0xFF646272, false); g.pose().popPose(); lineY += 19;
            }
            drawEditEntry(g);
        }
        var controls = activeLayout();
        var ticket = controls.ticket();
        int rewardX = (int) Math.round(ticket.x()), rewardY = (int) Math.round(ticket.y());
        int ticketW = scaledTicketWidth(ticket.scale()), ticketH = Math.round(72 * (float) ticket.scale());
        drawRewardTicket(g, rewardX, rewardY, ticket.scale());
        var itemControl = controls.items();
        int itemX = (int) Math.round(itemControl.x()), itemY = (int) Math.round(itemControl.y());
        float itemScale = (float) itemControl.scale();
        var hits = new java.util.ArrayList<RewardHit>();
        List<QuestDefinition.Reward> displayedRewards = rewards();
        for (int i = 0; i < displayedRewards.size(); i++) {
            var reward = displayedRewards.get(i);
            int cardWidth = Math.round(RewardCardRenderer.WIDTH * itemScale);
            int cardHeight = RewardCardRenderer.scaledHeight(itemScale);
            int iconSize = Math.min(24, cardWidth - 4);
            int iconY = Math.max(1, (RewardCardRenderer.HEIGHT - iconSize) / 2 - 1);
            if (itemX + cardWidth > rewardX + Math.round(TICKET_LEFT_SLICE * TICKET_SCALE * ticket.scale()) - 6)
                break;
            boolean hovered = mouseX >= itemX && mouseX < itemX + cardWidth
                    && mouseY >= itemY + iconY && mouseY < itemY + iconY + cardHeight;
            RewardCardRenderer.render(g, font, QuestRewardsScreen.icon(reward.type(), reward.item()),
                    reward.count(), reward.quality(), reward.background(), itemX, itemY + iconY,
                    itemScale, 1, false, hovered);
            hits.add(new RewardHit(reward, itemX, itemY + iconY, cardWidth, cardHeight));
            itemX += Math.round(RewardCardRenderer.COLUMN_STEP * itemScale) + 4;
        }
        rewardHits = List.copyOf(hits);
        boolean available = !claimable().isBlank() && !waitingClaim;
        var claim = controls.claim();
        int claimX = (int) Math.round(claim.x()), claimY = (int) Math.round(claim.y());
        int claimSize = Math.round(49 * (float) claim.scale());
        MailDecor.circle(g, claimX, claimY, claimSize,
                available ? rewardPressed ? 0xFFEF9E82 : 0xFFBF5448 : 0xFFDDD0AE);
        JournalNavButton.drawIcon(g, "gift", claimX + Math.round(8 * (float) claim.scale()),
                claimY + Math.round(8 * (float) claim.scale()),
                Math.round(33 * (float) claim.scale()), 0xFFFFF9E9);
        if (layoutEditing) drawLayoutEditor(g, controls, rewardHits, ticketW, ticketH);
    }
    private static int scaledTicketWidth(double scale) {
        return Math.round((TICKET_SOURCE_WIDTH - TICKET_CROP) * TICKET_SCALE * (float) scale);
    }
    private static void drawRewardTicket(GuiGraphics g, int x, int y, double scale) {
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        float textureScale = TICKET_SCALE * (float) scale;
        int leftWidth = Math.round(TICKET_LEFT_SLICE * textureScale);
        int rightSource = TICKET_LEFT_SLICE + TICKET_CROP;
        int rightWidth = Math.round((TICKET_SOURCE_WIDTH - rightSource) * textureScale);
        int height = Math.round(72 * (float) scale);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                "xero_quest_journal", "textures/gui/anecdote/reward_ticket.png");
        g.blit(texture, x, y, leftWidth, height, 0, 0, TICKET_LEFT_SLICE, 160, TICKET_SOURCE_WIDTH, 160);
        g.blit(texture, x + leftWidth, y, rightWidth, height, rightSource, 0,
                TICKET_SOURCE_WIDTH - rightSource, 160, TICKET_SOURCE_WIDTH, 160);
    }
    private void drawLayoutEditor(GuiGraphics g, AnecdoteRewardLayout layout,
                                  List<RewardHit> hits, int ticketWidth, int ticketHeight) {
        drawEditChip(g, 600, 78, 70, text("reward_layout.ticket"), "ticket", selectedControl);
        drawEditChip(g, 674, 78, 70, text("reward_layout.items"), "items", selectedControl);
        drawEditChip(g, 748, 78, 70, text("reward_layout.claim"), "claim", selectedControl);
        drawEditChip(g, 822, 78, 54, text(savingLayout ? "reward_layout.saving" : "reward_layout.save"), "save", "");
        drawEditChip(g, 880, 78, 54, text("reward_layout.cancel"), "cancel", "");
        drawEditTool(g, 600, 104, "undo", !layoutUndo.isEmpty());
        drawEditTool(g, 624, 104, "redo", !layoutRedo.isEmpty());
        var control = layout.control(selectedControl);
        if (selectedControl.equals("ticket"))
            g.renderOutline((int) control.x() - 2, (int) control.y() - 2,
                    ticketWidth + 4, ticketHeight + 4, 0xFF54D8B0);
        else if (selectedControl.equals("items") && !hits.isEmpty()) {
            var first = hits.getFirst(); var last = hits.getLast();
            g.renderOutline(first.x() - 3, first.y() - 3,
                    last.x() + last.width() - first.x() + 6,
                    last.y() + last.height() - first.y() + 6, 0xFF54D8B0);
        } else if (selectedControl.equals("claim")) {
            int size = Math.round(49 * (float) control.scale());
            g.renderOutline((int) control.x() - 3, (int) control.y() - 3,
                    size + 6, size + 6, 0xFF54D8B0);
        }
        centered(g, text("reward_layout.hint"), 785, 132, .75F, 0xFF378E77);
        if (!layoutError.isBlank()) centered(g, Component.literal(layoutError), 785, 118, .8F, 0xFFB94D4D);
    }
    private void drawLayoutProperties(GuiGraphics g) {
        centered(g, text("reward_layout.precision"), 770, 154, 1.1F, 0xFF55586A);
        g.drawString(font, text("reward_layout.x"), 605, 182, 0xFF646272, false);
        g.drawString(font, text("reward_layout.y"), 605, 218, 0xFF646272, false);
        g.drawString(font, text("reward_layout.size"), 605, 254, 0xFF646272, false);
    }
    private void drawEditChip(GuiGraphics g, int x, int y, int w,
                              Component label, String id, String selected) {
        int color = id.equals(selected) ? 0xFF38866F : 0xFF455568;
        g.fill(x, y, x + w, y + 19, color);
        g.renderOutline(x, y, w, 19, id.equals(selected) ? 0xFFBDE9D5 : 0xFF9CB0BD);
        g.drawCenteredString(font, font.plainSubstrByWidth(label.getString(), w - 4),
                x + w / 2, y + 5, 0xFFFFFFFF);
    }
    private static void drawEditTool(GuiGraphics g, int x, int y, String icon, boolean enabled) {
        g.fill(x, y, x + 20, y + 20, enabled ? 0xFF455568 : 0xFF3B4248);
        g.renderOutline(x, y, 20, 20, enabled ? 0xFF9CB0BD : 0xFF62686B);
        JournalNavButton.drawIcon(g, icon, x + 3, y + 3, 14,
                enabled ? 0xFFE8F1EF : 0xFF8D928E);
    }
    private void saveRewardLayout() {
        if (savingLayout) return;
        commitNumericEdit();
        var entry = rewardEntry();
        if (entry == null) { layoutError = text("reward_layout.no_entry").getString(); return; }
        AnecdoteClientState.clearResult();
        AnecdoteClientState.save(entry.withRewardLayout(rewardLayoutDraft));
        savingLayout = true;
        layoutEditing = false;
        removeLayoutInputs();
        layoutDragging = false;
        layoutError = "";
    }
    private void rememberLayout() {
        if (layoutUndo.isEmpty() || !layoutUndo.peek().equals(rewardLayoutDraft))
            layoutUndo.push(rewardLayoutDraft);
        while (layoutUndo.size() > 64) layoutUndo.removeLast();
        layoutRedo.clear();
    }
    private void restoreLayout(Deque<AnecdoteRewardLayout> from, Deque<AnecdoteRewardLayout> to) {
        if (!layoutEditing || savingLayout || from.isEmpty()) return;
        commitNumericEdit();
        to.push(rewardLayoutDraft);
        rewardLayoutDraft = from.pop();
        layoutDragging = false;
        syncLayoutInputValues();
    }
    private void selectLayoutControl(String id) {
        commitNumericEditIfBlurred();
        if (layoutInputFocused()) setFocused(null);
        selectedControl = id;
        syncLayoutInputValues();
    }
    private void updateLayoutInputs() {
        if (!layoutEditing || savingLayout) {
            removeLayoutInputs();
            return;
        }
        if (layoutXInput == null) {
            layoutXInput = addRenderableWidget(numericInput("x"));
            layoutYInput = addRenderableWidget(numericInput("y"));
            layoutScaleInput = addRenderableWidget(numericInput("size"));
            syncLayoutInputValues();
        }
        positionLayoutInput(layoutXInput, 690, 174, 125, 20);
        positionLayoutInput(layoutYInput, 690, 210, 125, 20);
        positionLayoutInput(layoutScaleInput, 690, 246, 125, 20);
        syncLayoutInputValues();
    }
    private EditBox numericInput(String field) {
        var input = new EditBox(font, 0, 0, 96, 20, text("reward_layout." + field));
        input.setMaxLength(16);
        input.setFilter(value -> value.matches("-?\\d*(\\.\\d*)?"));
        input.setResponder(value -> applyNumericInput(field, value));
        return input;
    }
    private void positionLayoutInput(EditBox input, int x, int y, int w, int h) {
        double scale = scale();
        input.setX(left + (int) Math.round(x * scale));
        input.setY(top + (int) Math.round(y * scale));
        input.setWidth(Math.max(38, (int) Math.round(w * scale)));
    }
    private void applyNumericInput(String field, String raw) {
        if (syncingLayoutInputs || raw.isBlank() || raw.equals("-") || raw.equals(".") || raw.equals("-.")) return;
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException invalid) {
            return;
        }
        if (!Double.isFinite(value)) return;
        var control = rewardLayoutDraft.control(selectedControl);
        switch (field) {
            case "reward_layout.x" -> {
                if (value < -1000 || value > 2000) return;
                updateNumericDraft(control.move(value, control.y()));
            }
            case "reward_layout.y" -> {
                if (value < -1000 || value > 2000) return;
                updateNumericDraft(control.move(control.x(), value));
            }
            case "reward_layout.size" -> {
                if (value < .4 || value > 2.5) return;
                updateNumericDraft(control.resize(value));
            }
            default -> { }
        }
    }
    private void updateNumericDraft(AnecdoteRewardLayout.Control next) {
        if (numericEditBefore == null) numericEditBefore = rewardLayoutDraft;
        rewardLayoutDraft = rewardLayoutDraft.withControl(selectedControl, next);
    }
    private void syncLayoutInputValues() {
        if (layoutXInput == null) return;
        var control = rewardLayoutDraft.control(selectedControl);
        syncingLayoutInputs = true;
        try {
            if (!layoutXInput.isFocused()) layoutXInput.setValue(numberText(control.x()));
            if (!layoutYInput.isFocused()) layoutYInput.setValue(numberText(control.y()));
            if (!layoutScaleInput.isFocused()) layoutScaleInput.setValue(numberText(control.scale()));
        } finally {
            syncingLayoutInputs = false;
        }
    }
    private static String numberText(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
    private boolean layoutInputFocused() {
        return layoutXInput != null && (layoutXInput.isFocused() || layoutYInput.isFocused()
                || layoutScaleInput.isFocused());
    }
    private void commitNumericEditIfBlurred() {
        if (numericEditBefore != null && !layoutInputFocused()) commitNumericEdit();
    }
    private void commitNumericEdit() {
        if (numericEditBefore == null) return;
        if (!numericEditBefore.equals(rewardLayoutDraft)) {
            layoutUndo.push(numericEditBefore);
            while (layoutUndo.size() > 64) layoutUndo.removeLast();
            layoutRedo.clear();
        }
        numericEditBefore = null;
    }
    private void removeLayoutInputs() {
        if (layoutXInput != null) removeWidget(layoutXInput);
        if (layoutYInput != null) removeWidget(layoutYInput);
        if (layoutScaleInput != null) removeWidget(layoutScaleInput);
        layoutXInput = layoutYInput = layoutScaleInput = null;
        numericEditBefore = null;
    }
    private boolean isLayoutInput(double x, double y) {
        return layoutXInput != null && (layoutXInput.isMouseOver(x, y)
                || layoutYInput.isMouseOver(x, y) || layoutScaleInput.isMouseOver(x, y));
    }
    private String controlAt(double x, double y) {
        var layout = activeLayout();
        var claim = layout.claim();
        double claimSize = 49 * claim.scale();
        if (x >= claim.x() && x < claim.x() + claimSize && y >= claim.y() && y < claim.y() + claimSize)
            return "claim";
        var items = layout.items();
        var ticket = layout.ticket();
        if (x >= items.x() && x < ticket.x() + TICKET_LEFT_SLICE * TICKET_SCALE * ticket.scale()
                && y >= items.y() && y < items.y() + 58 * items.scale()) return "items";
        double ticketW = scaledTicketWidth(ticket.scale()), ticketH = 72 * ticket.scale();
        if (x >= ticket.x() && x < ticket.x() + ticketW && y >= ticket.y() && y < ticket.y() + ticketH)
            return "ticket";
        return "";
    }
    private void beginLayoutDrag(double x, double y, String controlId) {
        commitNumericEditIfBlurred();
        selectLayoutControl(controlId);
        dragLayoutBefore = rewardLayoutDraft;
        var control = rewardLayoutDraft.control(controlId);
        layoutGrabX = x - control.x();
        layoutGrabY = y - control.y();
        layoutDragging = true;
    }
    private void moveLayoutControl(double x, double y) {
        var control = rewardLayoutDraft.control(selectedControl);
        var ticket = rewardLayoutDraft.ticket();
        double ticketWidth = scaledTicketWidth(ticket.scale()), ticketHeight = 72 * ticket.scale();
        double minX = 560, minY = 390, maxX, maxY;
        if (selectedControl.equals("ticket")) {
            maxX = 970 - ticketWidth;
            maxY = 530 - ticketHeight;
        } else if (selectedControl.equals("claim")) {
            double size = 49 * control.scale();
            minX = ticket.x() + TICKET_LEFT_SLICE * TICKET_SCALE * ticket.scale();
            maxX = ticket.x() + ticketWidth - size - 5;
            minY = ticket.y() + 2;
            maxY = ticket.y() + ticketHeight - size - 2;
        } else {
            double leftWidth = TICKET_LEFT_SLICE * TICKET_SCALE * ticket.scale();
            double groupWidth = rewardHits.isEmpty() ? 34 * control.scale()
                    : rewardHits.getLast().x() + rewardHits.getLast().width() - rewardHits.getFirst().x();
            double groupHeight = rewardHits.stream().mapToInt(hit -> hit.height()).max().orElse(42);
            minX = ticket.x() + 6;
            maxX = ticket.x() + leftWidth - groupWidth - 6;
            minY = ticket.y() + 2;
            maxY = ticket.y() + ticketHeight - groupHeight - 2;
        }
        double nextX = x - layoutGrabX, nextY = y - layoutGrabY;
        boolean snap = !shiftDown();
        nextX = snapCoordinate(nextX, snap);
        nextY = snapCoordinate(nextY, snap);
        var moved = control.move(Math.clamp(nextX, minX, Math.max(minX, maxX)),
                Math.clamp(nextY, minY, Math.max(minY, maxY)));
        rewardLayoutDraft = rewardLayoutDraft.withControl(selectedControl, moved);
        syncLayoutInputValues();
    }
    private boolean shiftDown() {
        long window = minecraft.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
    private static double snapCoordinate(double value, boolean snap) {
        return snap ? Math.rint(value / 8.0) * 8.0 : value;
    }
    private ItemStack rewardStack(QuestDefinition.Reward reward) {
        if (reward == null || !reward.type().equals("item") || reward.item().isBlank()
                || minecraft.player == null) return ItemStack.EMPTY;
        if (QuestItemStackValue.encoded(reward.item()))
            return QuestItemStackValue.decode(reward.item(), minecraft.player.registryAccess())
                    .map(stack -> stack.copyWithCount(Math.min(64, reward.count()))).orElse(ItemStack.EMPTY);
        ResourceLocation id = ResourceLocation.tryParse(reward.item());
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id), Math.min(64, reward.count())) : ItemStack.EMPTY;
    }
    private static void image(GuiGraphics g, String name, int x, int y, int w, int h, int tw, int th) {
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "textures/gui/anecdote/" + name + ".png"),
                x, y, w, h, 0, 0, tw, th, tw, th);
    }
    private void centered(GuiGraphics g, Component text, int center, int y, float scale, int color) {
        g.pose().pushPose(); g.pose().translate(center, y, 220); g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false); g.pose().popPose();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked() || isClosingBook()) return true;
        if (layoutEditing && button == 0 && isLayoutInput(x, y))
            return super.mouseClicked(x, y, button);
        double lx = localX(x), ly = localY(y);
        if (button == 0 && !layoutEditing && canEditLayout()
                && lx >= 890 && lx < 938 && ly >= 78 && ly < 97) {
            var entry = rewardEntry();
            if (entry != null) {
                rewardLayoutDraft = entry.rewardLayout();
                layoutEditing = true;
                selectLayoutControl("ticket");
            }
            return true;
        }
        if (button == 0 && layoutEditing && !savingLayout) {
            if (ly >= 78 && ly < 97) {
                if (lx >= 600 && lx < 670) selectLayoutControl("ticket");
                else if (lx >= 674 && lx < 744) selectLayoutControl("items");
                else if (lx >= 748 && lx < 818) selectLayoutControl("claim");
                else if (lx >= 822 && lx < 876) saveRewardLayout();
                else if (lx >= 880 && lx < 934) {
                    commitNumericEdit();
                    layoutEditing = false;
                    removeLayoutInputs();
                    layoutDragging = false;
                    var entry = rewardEntry();
                    rewardLayoutDraft = entry == null ? AnecdoteRewardLayout.defaults() : entry.rewardLayout();
                }
                return true;
            }
            if (ly >= 102 && ly < 124) {
                if (lx >= 600 && lx < 622) restoreLayout(layoutUndo, layoutRedo);
                else if (lx >= 624 && lx < 646) restoreLayout(layoutRedo, layoutUndo);
                return true;
            }
            String hit = controlAt(lx, ly);
            if (!hit.isBlank()) {
                beginLayoutDrag(lx, ly, hit);
                return true;
            }
            return true;
        }
        if (button == 0) {
            closePressed = closeAt(lx, ly);
            rewardPressed = rewardAt(lx, ly) && !claimable().isBlank() && !waitingClaim;
            pressedRewardIndex = rewardIndexAt(lx, ly);
            itemPressed = pressedRewardIndex >= 0;
            if (closePressed || rewardPressed || itemPressed) return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && layoutEditing && isLayoutInput(x, y))
            return super.mouseReleased(x, y, button);
        if (button == 0 && layoutEditing && layoutDragging) {
            if (dragLayoutBefore != null && !dragLayoutBefore.equals(rewardLayoutDraft)) {
                layoutUndo.push(dragLayoutBefore);
                while (layoutUndo.size() > 64) layoutUndo.removeLast();
                layoutRedo.clear();
            }
            dragLayoutBefore = null;
            layoutDragging = false;
            return true;
        }
        if (button == 0 && itemPressed) {
            int selected = pressedRewardIndex;
            itemPressed = false;
            pressedRewardIndex = -1;
            if (!inputBlocked() && selected >= 0 && selected < rewardHits.size()
                    && rewardHits.get(selected).contains(localX(x), localY(y)))
                minecraft.setScreen(new QuestRewardPopupScreen(this, rewardHits.get(selected).reward()));
            return true;
        }
        if (button == 0) {
            if (closePressed) { closePressed = false; if (closeAt(localX(x), localY(y))) onClose(); return true; }
            if (rewardPressed) {
                rewardPressed = false;
                String batch = claimable();
                if (rewardAt(localX(x), localY(y)) && !batch.isBlank()) {
                    waitingClaim = true; claimSentAt = System.nanoTime();
                    AnecdoteClientState.clearResult(); AnecdoteClientState.claimReward(batch);
                }
                return true;
            }
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (layoutEditing && layoutDragging && button == 0) {
            moveLayoutControl(localX(x), localY(y));
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (layoutEditing && !savingLayout && vertical != 0) {
            var control = rewardLayoutDraft.control(selectedControl);
            rememberLayout();
            double amount = control.scale() < 1 ? .05 : .1;
            double next = Math.clamp(control.scale() + Math.signum(vertical) * amount, .4, 2.5);
            rewardLayoutDraft = rewardLayoutDraft.withControl(selectedControl, control.resize(next));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (layoutEditing && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (savingLayout) return true;
            if (layoutInputFocused()) {
                setFocused(null);
                commitNumericEdit();
                return true;
            }
            layoutEditing = false;
            removeLayoutInputs();
            var entry = rewardEntry();
            rewardLayoutDraft = entry == null ? AnecdoteRewardLayout.defaults() : entry.rewardLayout();
            layoutError = "";
            return true;
        }
        if (layoutEditing && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_Z) {
                if (layoutInputFocused()) { setFocused(null); commitNumericEdit(); }
                restoreLayout(layoutUndo, layoutRedo); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) {
                if (layoutInputFocused()) { setFocused(null); commitNumericEdit(); }
                restoreLayout(layoutRedo, layoutUndo); return true;
            }
        }
        if (layoutEditing) return layoutInputFocused() && super.keyPressed(key, scanCode, modifiers);
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override protected boolean isModalScreen() { return true; }
    @Override protected void renderModalBackground(GuiGraphics graphics, float delta) {
        if (parent instanceof AnimatedQuestScreen animated)
            animated.renderContents(graphics, -10000, -10000, delta);
        else if (parent != null) parent.render(graphics, -10000, -10000, delta);
    }
    @Override protected Viewport popupBounds() {
        layout();
        return viewport(left, top, left + widthPage, top + heightPage);
    }
    @Override protected Viewport modalScrimBounds() { return popupBounds(); }
    @Override protected int popupScrimColor() { return 0x33808080; }
    @Override public void onClose() {
        if (isClosing()) return;
        closeBookTo(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
}
