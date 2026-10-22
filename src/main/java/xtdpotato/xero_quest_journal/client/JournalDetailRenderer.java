package xtdpotato.xero_quest_journal.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.util.List;

/**
 * Genshin-style task details for tasks without an authored layout (title, place, ornament
 * divider, completed steps, objective bar, description) and the reward footer pinned to the
 * bottom of the detail column for every task.
 */
final class JournalDetailRenderer {
    static final int FOOTER_HEIGHT = 64;
    static final int GOLD = 0xFFE8C774;
    private static final int PLACE = 0xFFEDD394;
    private static final int BONUS_CARD_STEP = 31;
    private static final float BONUS_SCALE = 0.82F;

    private JournalDetailRenderer() {}

    /** Card hit in the footer: {@code bonus} marks the daily commission bonus row. */
    record CardHit(QuestDefinition.Reward reward, boolean bonus) {}

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal." + key);
    }

    /** Unscrolled height of the body for scroll extents. */
    static int bodyHeight(Font font, QuestDefinition quest, int width) {
        return layout(null, font, quest, 0, 0, width, 0, false);
    }

    /** Draws the body inside [left, right] starting at {@code top}, scrolled by {@code offset}. */
    static void renderBody(GuiGraphics g, Font font, QuestDefinition quest, int left, int top, int right, int offset) {
        layout(g, font, quest, left, top, right - left, offset, true);
    }

    static boolean objectiveAt(Font font, QuestDefinition quest, int left, int top, int right, int offset,
                               double mouseX, double mouseY) {
        if (quest == null || mouseX < left + 10 || mouseX >= right - 10) return false;
        var view = ClientQuestProgress.view(quest.id());
        if (view != null && view.completed()) return false;
        int y = top + 10 - offset + Math.round(font.lineHeight * 1.5F) + 6 + 14 + 10;
        int step = QuestText.stepIndex(quest);
        if (step >= 0) y += Math.min(3, step) * 12 + (step > 0 ? 2 : 0);
        return mouseY >= y && mouseY < y + 22;
    }

    private static int layout(GuiGraphics g, Font font, QuestDefinition quest, int left, int top, int width,
                              int offset, boolean draw) {
        int x = left + 10;
        int w = Math.max(24, width - 20);
        int y = top + 10 - offset;
        var view = ClientQuestProgress.view(quest.id());

        // Title, larger like Genshin's quest name.
        float titleScale = 1.5F;
        String title = font.plainSubstrByWidth(QuestText.title(quest).getString(), (int) (w / titleScale));
        if (draw) scaled(g, font, title, x, y, titleScale, 0xFFFFFFFF, true);
        y += Math.round(font.lineHeight * titleScale) + 6;

        // Place line with a map pin.
        String place = QuestText.place(quest).getString();
        String typeLabel = QuestText.type(quest).getString();
        if (!place.isBlank()) {
            if (draw) {
                pin(g, x, y - 1, GOLD);
                g.drawString(font, font.plainSubstrByWidth(place, w - 14 - font.width(typeLabel) - 8),
                        x + 11, y, PLACE, false);
            }
        }
        if (draw && !typeLabel.isBlank()) {
            int labelX = x + w - font.width(typeLabel);
            g.drawString(font, typeLabel, labelX, y, 0xFFB9C4D1, false);
        }
        y += 14;

        if (draw) ornament(g, x, x + w, y, 0x66D9C9A3);
        y += 10;

        // Completed steps (most recent three), struck through like a quest log.
        int stepIndex = QuestText.stepIndex(quest);
        boolean completed = view != null && view.completed();
        if (stepIndex >= 0) {
            int done = completed ? quest.steps().size() : stepIndex;
            for (int i = Math.max(0, done - 3); i < done; i++) {
                var step = quest.steps().get(i);
                if (draw) {
                    QuestStatusIcons.render(g, "complete", x + 2, y, 9);
                    Component line = QuestText.stepTitle(step, Component.literal("#" + (i + 1))).copy()
                            .withStyle(ChatFormatting.STRIKETHROUGH);
                    g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(
                            font.substrByWidth(line, w - 16)), x + 15, y + 1, 0xFF8E99A6, false);
                }
                y += 12;
            }
            if (done > 0) y += 2;
        }

        // Objective bar: hollow diamond, objective text and right-aligned progress.
        if (!completed) {
            int barHeight = 22;
            String progress = view != null && view.required() > 1
                    ? "(" + view.current() + "/" + view.required() + ")" : "";
            if (draw) {
                g.fill(x, y, x + w, y + barHeight, 0x55202836);
                g.fill(x, y, x + w, y + 1, 0x22FFFFFF);
                diamondRing(g, x + 10, y + barHeight / 2, 5, GOLD);
                MailDecor.diamond(g, x + 10, y + barHeight / 2, 2, GOLD);
                int progressWidth = progress.isEmpty() ? 0 : font.width(progress) + 8;
                String objective = font.plainSubstrByWidth(QuestText.objective(quest).getString(),
                        Math.max(1, w - 30 - progressWidth));
                g.drawString(font, objective, x + 22, y + (barHeight - font.lineHeight) / 2 + 1, 0xFFF4F1EA, false);
                if (!progress.isEmpty())
                    g.drawString(font, progress, x + w - 8 - font.width(progress),
                            y + (barHeight - font.lineHeight) / 2 + 1, 0xFFF4F1EA, false);
                if (stepIndex >= 0 && quest.steps().size() > 1) {
                    String stage = Component.translatable("hud.xero_quest_journal.step",
                            stepIndex + 1, quest.steps().size()).getString();
                    g.drawString(font, stage, x + w - font.width(stage), y - 11, 0xFF9FB0C2, false);
                }
            }
            y += barHeight + 9;
        } else {
            if (draw) {
                QuestStatusIcons.render(g, "complete", x, y, 12);
                g.drawString(font, ClientQuestProgress.status(quest.id()), x + 17, y + 2, 0xFF8FD6A0, false);
            }
            y += 20;
        }

        // Description.
        for (var line : font.split(QuestText.description(quest), w)) {
            if (draw) g.drawString(font, line, x, y, 0xFFE4EAF2, false);
            y += font.lineHeight + 3;
        }
        return y + offset - top + 8;
    }

    /** Whether the footer is shown for this task (rewards to display or a daily commission). */
    static boolean footerApplies(QuestDefinition quest, List<QuestDefinition.Reward> rewards) {
        return !rewards.isEmpty() || ClientQuestProgress.isDailyToday(quest.id());
    }

    static void renderFooter(GuiGraphics g, Font font, QuestDefinition quest, List<QuestDefinition.Reward> rewards,
                             int left, int top, int right, int mouseX, int mouseY, boolean claimButtonShown,
                             boolean editMode) {
        int x = left + 10;
        int w = Math.max(24, right - x - 10);
        var view = ClientQuestProgress.view(quest.id());
        String status = rewards.isEmpty() ? "empty" : view == null ? "loading"
                : view.status().equals("claimed") ? "claimed" : view.status().equals("complete")
                ? (!view.rewards().equals(quest.rewards()) ? "historical" : "pending") : "locked";
        g.fill(x, top + 2, x + w, top + 3, 0x30D9C9A3);
        boolean daily = ClientQuestProgress.isDailyToday(quest.id());
        int dailyWidth = daily ? dailyBlockWidth(font) : 0;
        if (!rewards.isEmpty()) {
            int color = switch (status) {
                case "pending", "historical" -> 0xFFF2CF83;
                case "claimed" -> 0xFF9FB4A8;
                default -> 0xFFD8DEE8;
            };
            g.drawString(font, font.plainSubstrByWidth(text("rewards." + status).getString(),
                    Math.max(1, w - dailyWidth - 12)), x, top + 8, color, false);
            boolean claimed = status.equals("claimed");
            int columns = Math.max(1, (w - dailyWidth - 8 + 4) / RewardCardRenderer.COLUMN_STEP);
            int visibleCards = editMode ? Math.max(0, columns - 1) : columns;
            for (int i = 0; i < Math.min(visibleCards, rewards.size()); i++) {
                var reward = rewards.get(i);
                int cardX = x + i * RewardCardRenderer.COLUMN_STEP;
                int cardY = top + 20;
                boolean hovered = mouseX >= cardX && mouseX < cardX + RewardCardRenderer.WIDTH
                        && mouseY >= cardY && mouseY < cardY + RewardCardRenderer.HEIGHT;
                RewardCardRenderer.render(g, font, QuestRewardsScreen.icon(reward.type(), reward.item()),
                        reward.count(), reward.quality(), reward.background(), cardX, cardY,
                        1F, 1F, claimed, hovered);
            }
        }
        if (editMode) {
            if (rewards.isEmpty())
                g.drawString(font, text("rewards.locked"), x, top + 8, 0xFFF2CF83, false);
            int cardX = addButtonX(rewards, x, x + w - dailyWidth);
            int cardY = top + 20;
            int cardRight = cardX + RewardCardRenderer.WIDTH;
            int cardBottom = cardY + RewardCardRenderer.HEIGHT;
            g.fillGradient(cardX, cardY, cardRight, cardY + 28, 0xFF536B83, 0xFF7087A0);
            g.fill(cardX, cardY + 28, cardRight, cardBottom, 0xCC2A2D34);
            g.renderOutline(cardX, cardY, RewardCardRenderer.WIDTH, RewardCardRenderer.HEIGHT, 0xFFD9E5F2);
            JournalNavButton.drawIcon(g, "add", cardX + 9, cardY + 9, 16,
                    mouseX >= cardX && mouseX < cardRight && mouseY >= cardY && mouseY < cardBottom
                            ? 0xFFFFE3A2 : 0xFFF2CF83);
        }
        if (daily) renderDaily(g, font, x + w - dailyWidth, top, dailyWidth, mouseX, mouseY, claimButtonShown);
    }

    static boolean addButtonAt(List<QuestDefinition.Reward> rewards, int left, int top, int right,
                               double mouseX, double mouseY) {
        int cardX = addButtonX(rewards, left + 10, right - 10);
        int cardY = top + 20;
        return mouseX >= cardX && mouseX < cardX + RewardCardRenderer.WIDTH
                && mouseY >= cardY && mouseY < cardY + RewardCardRenderer.HEIGHT;
    }

    private static int addButtonX(List<QuestDefinition.Reward> rewards, int start, int right) {
        int count = rewards == null ? 0 : rewards.size();
        int preferred = start + count * RewardCardRenderer.COLUMN_STEP;
        int maximum = Math.max(start, right - RewardCardRenderer.WIDTH);
        return Math.min(preferred, maximum);
    }

    static int dailyBlockWidth(Font font) {
        int cards = Math.max(1, ClientQuestProgress.daily().bonus().size());
        return Math.max(font.width(dailyHeading().getString()) + 4, cards * BONUS_CARD_STEP + 6);
    }

    static Component dailyHeading() {
        var state = ClientQuestProgress.daily();
        if (state.bonusClaimed()) return text("daily.bonus_claimed");
        return Component.translatable("screen.xero_quest_journal.daily.progress",
                ClientQuestProgress.dailyCompleted(), state.quests().size());
    }

    static String countdown() {
        long seconds = ClientQuestProgress.dailyRemainingMillis() / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private static void renderDaily(GuiGraphics g, Font font, int x, int top, int width, int mouseX, int mouseY,
                                    boolean claimButtonShown) {
        var state = ClientQuestProgress.daily();
        g.fill(x - 7, top + 8, x - 6, top + FOOTER_HEIGHT - 6, 0x30D9C9A3);
        if (!claimButtonShown) {
            Component heading = dailyHeading();
            g.drawString(font, heading, x + width - font.width(heading), top + 8,
                    state.bonusClaimed() ? 0xFF9FB4A8 : 0xFFF2CF83, false);
        }
        int cardY = top + 22;
        for (int i = 0; i < state.bonus().size(); i++) {
            var reward = state.bonus().get(i);
            int cardX = x + width - (state.bonus().size() - i) * BONUS_CARD_STEP;
            int size = Math.round(RewardCardRenderer.WIDTH * BONUS_SCALE);
            boolean hovered = mouseX >= cardX && mouseX < cardX + size
                    && mouseY >= cardY && mouseY < cardY + RewardCardRenderer.scaledHeight(BONUS_SCALE);
            RewardCardRenderer.render(g, font, QuestRewardsScreen.icon(reward.type(), reward.item()),
                    reward.count(), reward.quality(), reward.background(), cardX, cardY,
                    BONUS_SCALE, 1F, state.bonusClaimed(), hovered);
        }
        String refresh = Component.translatable("screen.xero_quest_journal.daily.refresh", countdown()).getString();
        MailDecor.text(g, font, refresh, x + width - Math.round(font.width(refresh) * .75F),
                top + FOOTER_HEIGHT - 9, .75F, 0xFF9FB0C2);
    }

    /** Reward card under the pointer, including the daily bonus row. */
    static CardHit cardAt(Font font, QuestDefinition quest, List<QuestDefinition.Reward> rewards,
                          int left, int top, int right, double mouseX, double mouseY) {
        int x = left + 10;
        int w = Math.max(24, right - x - 10);
        boolean daily = ClientQuestProgress.isDailyToday(quest.id());
        int dailyWidth = daily ? dailyBlockWidth(font) : 0;
        int columns = Math.max(1, (w - dailyWidth - 8 + 4) / RewardCardRenderer.COLUMN_STEP);
        for (int i = 0; i < Math.min(columns, rewards.size()); i++) {
            int cardX = x + i * RewardCardRenderer.COLUMN_STEP;
            int cardY = top + 20;
            if (mouseX >= cardX && mouseX < cardX + RewardCardRenderer.WIDTH
                    && mouseY >= cardY && mouseY < cardY + RewardCardRenderer.HEIGHT)
                return new CardHit(rewards.get(i), false);
        }
        if (daily) {
            var bonus = ClientQuestProgress.daily().bonus();
            int blockX = x + w - dailyWidth;
            int size = Math.round(RewardCardRenderer.WIDTH * BONUS_SCALE);
            for (int i = 0; i < bonus.size(); i++) {
                int cardX = blockX + dailyWidth - (bonus.size() - i) * BONUS_CARD_STEP;
                int cardY = top + 22;
                if (mouseX >= cardX && mouseX < cardX + size && mouseY >= cardY
                        && mouseY < cardY + RewardCardRenderer.scaledHeight(BONUS_SCALE))
                    return new CardHit(bonus.get(i), true);
            }
        }
        return null;
    }

    static void scaled(GuiGraphics g, Font font, String value, int x, int y, float scale, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, value, 0, 0, color, shadow);
        g.pose().popPose();
    }

    /** Small map pin: a disc with a point below and a dark eye. */
    static void pin(GuiGraphics g, int x, int y, int color) {
        MailDecor.circle(g, x, y, 7, color);
        for (int row = 0; row < 4; row++) g.fill(x + 1 + row, y + 5 + row, x + 6 - row, y + 6 + row, color);
        g.fill(x + 3, y + 2, x + 4, y + 4, 0xFF40331E);
    }

    /** One-pixel diamond outline of radius {@code r}. */
    static void diamondRing(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int row = -r; row <= r; row++) {
            int half = r - Math.abs(row);
            g.fill(cx - half, cy + row, cx - half + 1, cy + row + 1, color);
            if (half > 0) g.fill(cx + half, cy + row, cx + half + 1, cy + row + 1, color);
        }
    }

    /** Genshin-like rule: thin line ending in a small diamond on the left and a framed diamond on the right. */
    static void ornament(GuiGraphics g, int x, int right, int y, int color) {
        if (right - x < 24) return;
        MailDecor.diamond(g, x + 3, y, 2, color);
        g.fill(x + 8, y, right - 14, y + 1, color);
        diamondRing(g, right - 7, y, 4, color);
        MailDecor.diamond(g, right - 7, y, 1, color);
        g.fill(right - 2, y, right, y + 1, color);
    }
}
