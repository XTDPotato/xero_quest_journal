package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/** Serializes quest notifications and advances their clocks only while the HUD is active. */
final class QuestHudToastQueue {
    enum Type { UNLOCK, COMPLETION }
    private record Notice(Type type, String questId) {}

    private static final long UNLOCK_DURATION = 5_000_000_000L;
    private static final long COMPLETION_DURATION = 4_200_000_000L;
    private static final Deque<Notice> queued = new ArrayDeque<>();
    private static Notice current;
    private static long startedAt;
    private static long pausedAt;
    private static boolean hudActive;

    private QuestHudToastQueue() {}

    static void enqueue(Type type, String questId) {
        if (questId == null || questId.isBlank()) return;
        if (queued.size() >= 24) queued.removeFirst();
        queued.addLast(new Notice(type, questId));
        advance();
    }

    static void setHudActive(boolean active) {
        active &= Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null;
        if (hudActive == active) return;
        long now = System.nanoTime();
        hudActive = active;
        if (!active) {
            pausedAt = now;
            return;
        }
        if (pausedAt != 0 && current != null) startedAt += now - pausedAt;
        pausedAt = 0;
        advance();
    }

    static String current(Type type) {
        advance();
        return current != null && current.type() == type ? current.questId() : "";
    }

    static boolean has(Type type) {
        advance();
        if (current != null && current.type() == type) return true;
        return queued.stream().anyMatch(notice -> notice.type() == type);
    }

    static boolean has(Type type, String questId) {
        advance();
        if (questId == null || questId.isBlank()) return false;
        return current != null && current.type() == type && current.questId().equals(questId)
                || queued.stream().anyMatch(notice -> notice.type() == type && notice.questId().equals(questId));
    }

    static boolean dismiss(Type type, String questId) {
        if (questId == null || questId.isBlank()) return false;
        boolean changed = false;
        if (current != null && current.type() == type && current.questId().equals(questId)) {
            current = null;
            changed = true;
        }
        for (var iterator = queued.iterator(); iterator.hasNext();) {
            Notice notice = iterator.next();
            if (notice.type() == type && notice.questId().equals(questId)) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) advance();
        return changed;
    }

    static boolean hasAny() {
        advance();
        return current != null || !queued.isEmpty();
    }

    static long elapsed(Type type) {
        advance();
        return current != null && current.type() == type ? System.nanoTime() - startedAt : -1;
    }

    static void dismiss(Type type) {
        advance();
        if (current != null && current.type() == type) current = null;
        advance();
    }

    static void clear() {
        queued.clear();
        current = null;
        startedAt = 0;
        pausedAt = 0;
    }

    static void clear(Type type) {
        queued.removeIf(notice -> notice.type() == type);
        if (current != null && current.type() == type) current = null;
        advance();
    }

    private static void advance() {
        if (!hudActive || Minecraft.getInstance().player == null || Minecraft.getInstance().screen != null) return;
        long now = System.nanoTime();
        long duration = current == null ? 0 : duration(current.type());
        if (current != null && now - startedAt >= duration) current = null;
        while (current == null && !queued.isEmpty()) {
            Notice next = queued.removeFirst();
            if (QuestScreen.questById(next.questId()) == null) continue;
            current = next;
            startedAt = now;
        }
    }

    private static long duration(Type type) {
        return type == Type.UNLOCK ? UNLOCK_DURATION : COMPLETION_DURATION;
    }
}
