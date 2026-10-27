package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger;

import java.util.*;

/** Conditions are evaluated on the server. Submission plans never mutate the live inventory. */
public final class QuestConditions {
    public static final List<String> TYPES = List.of("confirm", "item_hold", "item_submit", "item_acquire",
            "kill", "block_break", "block_interact", "dimension", "region", "location", "advancement", "script",
            "event", "stage", "quest", "unlock", "lock", "all", "any", "count", "ratio");
    private static final Set<String> COUNTERS = Set.of("item_acquire", "kill", "block_break", "block_interact", "event", "stage");
    private QuestConditions() {}
    private static boolean action(Trigger trigger) {
        return trigger.type().equals("unlock") || trigger.type().equals("lock");
    }

    public record Progress(int current, int required) {}
    public record Plan(boolean satisfied, List<ItemStack> inventory) {}

    public static boolean valid(List<Trigger> triggers) {
        int[] budget = {256};
        return triggers.size() <= 128 && triggers.stream().allMatch(t -> valid(t, 0, budget));
    }
    private static boolean valid(Trigger t, int depth, int[] budget) {
        if (t == null || depth > 16 || --budget[0] < 0 || t.item().length() > 8192) return false;
        if (List.of("all", "any", "count", "ratio").contains(t.type())) {
            return !t.children().isEmpty() && t.children().stream().noneMatch(QuestConditions::action)
                    && (t.type().equals("ratio") ? t.count() <= 100
                    : !t.type().equals("count") || t.count() <= t.children().size())
                    && t.children().stream().allMatch(c -> valid(c, depth + 1, budget));
        }
        if (!t.children().isEmpty()) return false;
        var id = ResourceLocation.tryParse(t.item());
        return switch (t.type()) {
            case "confirm" -> true;
            // item_get remains a legacy hold alias. New acquisition tasks explicitly use item_acquire.
            case "item_get", "item_hold", "item_submit", "item_acquire" -> validItem(t.item());
            case "kill" -> id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            case "block_break", "block_interact" -> id != null && BuiltInRegistries.BLOCK.containsKey(id);
            case "dimension", "advancement" -> id != null;
            case "quest" -> validQuestTargets(t.item(), false);
            case "unlock", "lock" -> depth == 0 && validQuestTargets(t.item(), true);
            case "script" -> {
                try { QuestScripts.source(t.item()); yield true; }
                catch (Exception invalid) { yield false; }
            }
            case "region" -> region(t.item()) != null;
            case "location" -> QuestLocation.parse(t.item()) != null;
            case "event", "stage" -> t.item().matches("[a-zA-Z0-9_.:/-]{1,128}");
            default -> false;
        };
    }

    private static boolean validQuestTargets(String value, boolean includeGroups) {
        String[] ids = value.split("\\s*,\\s*", -1);
        return ids.length > 0 && ids.length <= 128
                && Arrays.stream(ids).allMatch(id -> !id.isBlank()
                && (QuestRepository.find(id) != null
                || includeGroups && QuestRepository.groups().stream().anyMatch(group -> group.id().equals(id))))
                && Arrays.stream(ids).distinct().count() == ids.length;
    }
    private static boolean validItem(String target) {
        if (QuestItemStackValue.encoded(target)) {
            var id = ResourceLocation.tryParse(QuestItemStackValue.itemId(target));
            return id != null && BuiltInRegistries.ITEM.containsKey(id)
                    && QuestItemStackValue.structurallyValid(target);
        }
        if (target.startsWith("#")) return ResourceLocation.tryParse(target.substring(1)) != null;
        var id = ResourceLocation.tryParse(target);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                && !new ItemStack(BuiltInRegistries.ITEM.get(id)).isEmpty();
    }
    public static boolean matches(ItemStack stack, String target) {
        return matches(stack, target, null);
    }
    public static boolean matches(ItemStack stack, String target,
                                   net.minecraft.core.HolderLookup.Provider registries) {
        if (stack.isEmpty()) return false;
        if (QuestItemStackValue.encoded(target)) {
            return registries != null && QuestItemStackValue.matches(stack, target, registries);
        }
        if (target.startsWith("#")) {
            var id = ResourceLocation.tryParse(target.substring(1));
            return id != null && stack.is(TagKey.create(Registries.ITEM, id));
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(target);
    }
    public static String key(QuestDefinition quest, Trigger trigger) {
        return quest.progressKey() + "/" + trigger.type() + ":" + trigger.item();
    }
    public static Progress progress(ServerPlayer player, QuestDefinition quest) {
        int current = 0, required = 0;
        for (Trigger t : quest.triggers()) {
            if (action(t)) continue;
            Progress p = progress(player, quest, t);
            current += p.current();
            required += p.required();
        }
        return new Progress(current, required);
    }
    public static Progress progress(ServerPlayer player, QuestDefinition quest, Trigger t) {
        if (!t.children().isEmpty()) {
            int done = (int) t.children().stream().filter(c -> {
                var p = progress(player, quest, c);
                return p.current() >= p.required();
            }).count();
            int required = needed(t);
            return new Progress(Math.min(done, required), required);
        }
        int value = switch (t.type()) {
            case "confirm" -> 0;
            case "item_get", "item_hold" -> QuestCarriedInventory.count(player, t.item());
            case "item_submit" -> count(player, t.item());
            case "item_acquire", "kill", "block_break", "block_interact", "event", "stage" ->
                    QuestProgressData.get(player.getServer()).counter(player.getUUID(), key(quest, t));
            default -> predicate(player, t) ? t.count() : 0;
        };
        return new Progress(Math.min(value, t.count()), t.count());
    }
    private static int needed(Trigger t) {
        return switch (t.type()) {
            case "any" -> 1;
            case "count" -> t.count();
            case "ratio" -> (t.children().size() * t.count() + 99) / 100;
            default -> t.children().size();
        };
    }
    private static boolean predicate(ServerPlayer player, Trigger t) {
        try {
            return switch (t.type()) {
                case "dimension" -> player.level().dimension().location().toString().equals(t.item());
                case "region" -> {
                    String[] r = region(t.item());
                    yield r != null && player.level().dimension().location().toString().equals(r[0])
                            && between(player.getX(), r[1], r[4]) && between(player.getY(), r[2], r[5])
                            && between(player.getZ(), r[3], r[6]);
                }
                case "location" -> {
                    var location = QuestLocation.parse(t.item());
                    yield location != null && location.reached(player);
                }
                case "advancement" -> {
                    var advancement = player.getServer().getAdvancements().get(ResourceLocation.parse(t.item()));
                    yield advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
                }
                case "script" -> Boolean.TRUE.equals(QuestScripts.run(player, QuestScripts.source(t.item()), true));
                case "quest" -> {
                    yield Arrays.stream(t.item().split("\\s*,\\s*"))
                            .map(QuestRepository::find).allMatch(target -> target != null
                                    && QuestProgressData.get(player.getServer())
                                    .entry(player.getUUID(), target) != null);
                }
                default -> false;
            };
        } catch (Exception failure) { return false; }
    }
    private static String[] region(String target) {
        String[] values = target.strip().split("\\s+");
        if (values.length != 7 || ResourceLocation.tryParse(values[0]) == null) return null;
        try {
            for (int i = 1; i < 7; i++) if (!Double.isFinite(Double.parseDouble(values[i]))) return null;
            return values;
        } catch (NumberFormatException invalid) { return null; }
    }
    private static boolean between(double value, String first, String second) {
        double a = Double.parseDouble(first), b = Double.parseDouble(second);
        return value >= Math.min(a, b) && value <= Math.max(a, b);
    }
    private static int count(ServerPlayer player, String target) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matches(stack, target, player.registryAccess())) total += stack.getCount();
        }
        return total;
    }
    public static Plan plan(ServerPlayer player, QuestDefinition quest, boolean manual) {
        if (manual) QuestCarriedInventory.invalidate(player);
        if (quest.triggers().stream().noneMatch(t -> !action(t)) || !valid(quest.triggers()))
            return new Plan(false, List.of());
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            inventory.add(player.getInventory().getItem(i).copy());
        // Bounded backtracking handles overlapping tag/ID submissions without partial consumption.
        var root = new Trigger("all", "", 1, quest.triggers().stream().filter(t -> !action(t)).toList());
        List<List<ItemStack>> candidates = plans(player, quest, root, manual, inventory, new int[]{2048});
        return candidates.isEmpty() ? new Plan(false, List.of()) : new Plan(true, candidates.getFirst());
    }
    private static List<List<ItemStack>> plans(ServerPlayer player, QuestDefinition quest, Trigger t,
                                               boolean manual, List<ItemStack> inventory, int[] budget) {
        if (--budget[0] < 0) return List.of();
        if (!t.children().isEmpty()) {
            List<List<ItemStack>> result = new ArrayList<>();
            choose(player, quest, t.children(), 0, needed(t), manual, inventory, budget, result);
            return result;
        }
        if (t.type().equals("confirm")) return manual ? List.of(inventory) : List.of();
        if (t.type().equals("item_submit")) {
            if (!manual) return List.of();
            List<ItemStack> planned = inventory.stream().map(ItemStack::copy).toList();
            int remaining = t.count();
            for (ItemStack stack : planned) {
                if (!matches(stack, t.item(), player.registryAccess())) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (remaining == 0) break;
            }
            return remaining == 0 ? List.of(planned) : List.of();
        }
        Progress p = progress(player, quest, t);
        return p.current() >= p.required() ? List.of(inventory) : List.of();
    }
    private static void choose(ServerPlayer player, QuestDefinition quest, List<Trigger> children, int index,
                               int remaining, boolean manual, List<ItemStack> inventory, int[] budget,
                               List<List<ItemStack>> result) {
        if (result.size() >= 32 || budget[0] <= 0) return;
        if (remaining == 0) { result.add(inventory); return; }
        if (children.size() - index < remaining) return;
        for (var planned : plans(player, quest, children.get(index), manual, inventory, budget))
            choose(player, quest, children, index + 1, remaining - 1, manual, planned, budget, result);
        if (children.size() - index > remaining)
            choose(player, quest, children, index + 1, remaining, manual, inventory, budget, result);
    }
    public static void record(ServerPlayer player, String type, String target, int amount) {
        record(player, type, target, amount, null);
    }

    public static void recordItemAcquired(ServerPlayer player, ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty()) return;
        record(player, "item_acquire", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), amount, stack);
    }

    private static void record(ServerPlayer player, String type, String target, int amount, ItemStack acquiredStack) {
        if (!COUNTERS.contains(type) || amount <= 0) return;
        ItemStack acquired = type.equals("item_acquire")
                ? acquiredStack == null ? recordedStack(target, player) : acquiredStack : null;
        var data = QuestProgressData.get(player.getServer());
        for (var quest : QuestRepository.all()) {
            if (!QuestFocus.allows(player, quest)) continue;
            if (!QuestStructures.allowsProgress(player, quest)) continue;
            if (data.entry(player.getUUID(), quest) != null || !QuestRuntime.unlocked(data, player, quest)
                    || !QuestRuntime.triggersEnabled(data, player, quest)) continue;
            Set<String> recorded = new HashSet<>();
            // Multi-step tasks only count toward the current step, under that step's own counter keys.
            var active = QuestSteps.current(data, player.getUUID(), quest);
            for (var t : active.triggers())
                record(player, active, t, type, target, amount, recorded, data, acquired);
        }
    }
    private static void record(ServerPlayer player, QuestDefinition quest, Trigger t, String type, String target,
                               int amount, Set<String> recorded, QuestProgressData data, ItemStack acquiredStack) {
        boolean matches;
        if (type.equals("item_acquire")) {
            ItemStack stack = acquiredStack == null ? recordedStack(target, player) : acquiredStack;
            matches = !stack.isEmpty() && matches(stack, t.item(), player.registryAccess());
        } else {
            matches = t.item().equals(target);
        }
        if (t.type().equals(type) && matches && recorded.add(key(quest, t)))
            data.increment(player.getUUID(), key(quest, t), amount);
        t.children().forEach(child ->
                record(player, quest, child, type, target, amount, recorded, data, acquiredStack));
    }

    private static ItemStack recordedStack(String target, ServerPlayer player) {
        if (QuestItemStackValue.encoded(target))
            return QuestItemStackValue.decode(target, player.registryAccess()).orElse(ItemStack.EMPTY);
        ResourceLocation id = ResourceLocation.tryParse(target);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
    }
}
