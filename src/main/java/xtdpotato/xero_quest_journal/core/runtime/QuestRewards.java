package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.io.QuestLootStore;
import xtdpotato.xero_quest_journal.core.io.MailTemplateStore;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.model.LootDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;

import java.util.*;
import com.google.gson.Gson;
import xtdpotato.xero_quest_journal.core.QuestConfig;

/** Reward execution stays server-side; ambiguous receipts block automatic retries. */
public final class QuestRewards {
    public static final List<String> TYPES = List.of("item", "experience", "levels", "command", "function",
            "script", "effect", "remove_effect", "unlock", "lock", "complete", "message", "loot", "loot_choice", "mail");
    private static final Gson GSON = new Gson();
    private static final Set<UUID> EXECUTING = new HashSet<>();
    private QuestRewards() {}

    public static boolean valid(QuestDefinition.Reward reward) {
        if (reward == null || !TYPES.contains(reward.type()) || reward.item().length() > 8192) return false;
        if (reward.type().equals("loot")) {
            return QuestLootStore.find(reward.item()) != null && reward.count() <= 128;
        }
        if (reward.type().equals("loot_choice")) {
            return !choices(reward).isEmpty();
        }
        if (reward.type().equals("mail")) {
            return reward.count() == 1 && MailTemplateStore.find(reward.item()) != null;
        }
        if (reward.type().equals("item") && QuestItemStackValue.encoded(reward.item())) {
            return QuestItemStackValue.structurallyValid(reward.item());
        }
        var id = ResourceLocation.tryParse(reward.item());
        return switch (reward.type()) {
            case "item" -> id != null && BuiltInRegistries.ITEM.containsKey(id)
                    && !new ItemStack(BuiltInRegistries.ITEM.get(id)).isEmpty();
            case "experience", "levels" -> reward.type().equals("experience") || reward.count() <= 10000;
            case "command" -> !reward.item().isBlank() && !reward.item().contains("\n")
                    && !reward.item().contains("\r") && reward.count() == 1;
            case "function" -> id != null && reward.count() == 1;
            case "script" -> {
                try { QuestScripts.source(reward.item()); yield reward.count() == 1; }
                catch (Exception invalid) { yield false; }
            }
            case "effect" -> RewardEffect.parse(reward.item()) != null && reward.count() <= 1000000;
            case "remove_effect" -> id != null && BuiltInRegistries.MOB_EFFECT.containsKey(id)
                    && reward.count() == 1;
            case "unlock", "lock" -> validQuestTargets(reward.item()) && reward.count() == 1;
            case "complete" -> QuestRepository.find(reward.item()) != null && reward.count() == 1;
            case "message" -> !reward.item().isBlank() && reward.count() == 1;
            default -> false;
        };
    }

    public static List<QuestDefinition.Reward> freeze(List<QuestDefinition.Reward> rewards) {
        return freeze(null, rewards);
    }

    public static List<QuestDefinition.Reward> freeze(ServerPlayer player, List<QuestDefinition.Reward> rewards) {
        if (rewards.size() > 128 || rewards.stream().anyMatch(reward -> !valid(reward)))
            throw new IllegalArgumentException("Invalid reward definition");
        List<QuestDefinition.Reward> result = new ArrayList<>();
        for (var reward : rewards) {
            if (reward.type().equals("loot")) {
                LootDefinition table = QuestLootStore.find(reward.item());
                if (table == null) throw new IllegalArgumentException("Missing loot table");
                for (var resolved : resolve(table, player, reward.count()))
                    result.add(withSettings(resolved, reward));
            } else if (reward.type().equals("script")) {
                try { result.add(new QuestDefinition.Reward("script", QuestScripts.source(reward.item()), 1,
                        reward.quality(), reward.background(), reward.displayed(), reward.claimMode())); }
                catch (Exception failure) { throw new IllegalArgumentException("Cannot freeze script", failure); }
            } else {
                result.add(reward);
            }
            if (result.size() > 128) throw new IllegalArgumentException("Loot result is too large");
        }
        return List.copyOf(result);
    }

    private static QuestDefinition.Reward withSettings(QuestDefinition.Reward resolved,
                                                        QuestDefinition.Reward settings) {
        return new QuestDefinition.Reward(resolved.type(), resolved.item(), resolved.count(),
                resolved.quality(), resolved.background(), settings.displayed(), settings.claimMode());
    }

    private static List<QuestDefinition.Reward> resolve(LootDefinition table, ServerPlayer player, int repeats) {
        List<LootDefinition.Entry> eligible = table.entries().stream()
                .filter(entry -> entry.enabled()
                        && (table.mode() == LootDefinition.Mode.CHOICE || entry.weight() > 0)
                        && (player == null || QuestLootConditions.matches(player, entry.condition())))
                .toList();
        if (eligible.isEmpty()) throw new IllegalArgumentException("Loot table has no eligible entries");
        if (table.mode() == LootDefinition.Mode.CHOICE) {
            List<QuestDefinition.Reward> choices = eligible.stream().map(LootDefinition.Entry::reward).toList();
            var encoded = new com.google.gson.JsonObject();
            encoded.addProperty("title", table.choiceTitle());
            encoded.addProperty("displayIcon", table.displayIcon());
            encoded.add("choices", GSON.toJsonTree(choices));
            encoded.add("details", GSON.toJsonTree(eligible.stream().map(LootDefinition.Entry::detailText).toList()));
            return List.of(new QuestDefinition.Reward("loot_choice", encoded.toString(), 1));
        }
        List<QuestDefinition.Reward> result = new ArrayList<>();
        int draws;
        try {
            draws = Math.min(128, Math.multiplyExact(table.rolls(), repeats));
        } catch (ArithmeticException overflow) {
            draws = 128;
        }
        List<LootDefinition.Entry> pool = new ArrayList<>(eligible);
        for (int draw = 0; draw < draws && !pool.isEmpty(); draw++) {
            int total = pool.stream().mapToInt(LootDefinition.Entry::weight).sum();
            int pick = player == null ? 0 : player.getRandom().nextInt(Math.max(1, total));
            LootDefinition.Entry selected = pool.getFirst();
            for (var entry : pool) {
                if (pick < entry.weight()) { selected = entry; break; }
                pick -= entry.weight();
            }
            result.add(selected.reward());
            pool.remove(selected);
        }
        return result;
    }

    public static List<QuestDefinition.Reward> choices(QuestDefinition.Reward reward) {
        if (reward == null || !reward.type().equals("loot_choice")) return List.of();
        try {
            String source = reward.item();
            var parsed = GSON.fromJson(source, com.google.gson.JsonElement.class);
            if (parsed != null && parsed.isJsonObject() && parsed.getAsJsonObject().has("choices"))
                source = parsed.getAsJsonObject().get("choices").toString();
            var values = GSON.fromJson(source, QuestDefinition.Reward[].class);
            if (values == null || values.length < 2 || values.length > 256
                    || Arrays.stream(values).anyMatch(value -> value == null || value.type().equals("loot")
                    || value.type().equals("loot_choice") || !valid(value)))
                return List.of();
            return List.of(values);
        } catch (RuntimeException invalid) {
            return List.of();
        }
    }

    public static String choiceTitle(QuestDefinition.Reward reward) {
        if (reward == null || !reward.type().equals("loot_choice")) return "";
        try {
            var parsed = GSON.fromJson(reward.item(), com.google.gson.JsonElement.class);
            if (parsed != null && parsed.isJsonObject() && parsed.getAsJsonObject().has("title"))
                return parsed.getAsJsonObject().get("title").getAsString();
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    public static String choiceDetail(QuestDefinition.Reward reward, int index) {
        if (reward == null || !reward.type().equals("loot_choice") || index < 0 || index >= 256) return "";
        try {
            var json = GSON.fromJson(reward.item(), com.google.gson.JsonObject.class);
            if (json != null && json.has("details") && json.get("details").isJsonArray()) {
                var details = json.getAsJsonArray("details");
                if (index < details.size() && details.get(index).isJsonPrimitive()) {
                    String text = details.get(index).getAsString();
                    return text.length() <= 512 ? text : "";
                }
            }
        } catch (RuntimeException ignored) { }
        return "";
    }

    /** Frozen choice metadata travels with the reward, including old mailbox saves. */
    public static String choiceDisplayIcon(String target) {
        try {
            var parsed = GSON.fromJson(target, com.google.gson.JsonElement.class);
            if (parsed != null && parsed.isJsonObject() && parsed.getAsJsonObject().has("displayIcon"))
                return LootDefinition.normalizeDisplayIcon(parsed.getAsJsonObject().get("displayIcon").getAsString());
        } catch (RuntimeException ignored) {
        }
        return LootDefinition.DEFAULT_DISPLAY_ICON;
    }

    private static boolean validQuestTargets(String value) {
        String[] ids = value.split("\\s*,\\s*", -1);
        return ids.length > 0 && ids.length <= 128
                && Arrays.stream(ids).allMatch(id -> !id.isBlank()
                && (QuestRepository.find(id) != null
                || QuestRepository.groups().stream().anyMatch(group -> group.id().equals(id))))
                && Arrays.stream(ids).distinct().count() == ids.length;
    }

    public static int command(ServerPlayer player, String command) {
        return QuestCommands.execute(player, command);
    }

    /** Graph rewards have separate receipts and never create or claim a task completion entry. */
    public static void custom(ServerPlayer player, QuestDefinition quest, String key,
                              List<QuestDefinition.Reward> rewards, boolean once) {
        if (player == null || !player.server.isSameThread()) throw new IllegalArgumentException("Server thread required");
        if (QuestStructures.active(player)) throw new IllegalArgumentException("Leave the scene before receiving rewards");
        var state = QuestControlData.get(player);
        String receipt = state.actionReceipt(player, quest, key);
        if (receipt.equals("done") && once) return;
        if (receipt.equals("running") || receipt.equals("failed"))
            throw new IllegalStateException("Custom reward needs administrator review: " + key);
        List<QuestDefinition.Reward> frozen = freeze(player, rewards);
        if (frozen.isEmpty()) throw new IllegalArgumentException("No custom rewards configured");
        if (frozen.stream().anyMatch(reward -> reward.type().equals("loot_choice"))) {
            throw new IllegalArgumentException("Choice loot requires a task reward selection screen");
        }
        if (!EXECUTING.add(player.getUUID())) throw new IllegalStateException("Reward execution already in progress");
        boolean reserved = false;
        try {
            List<ItemStack> planned = new ArrayList<>(player.getInventory().items.stream().map(ItemStack::copy).toList());
            for (var reward : frozen) if (reward.type().equals("item")) {
                ItemStack prototype = QuestItemStackValue.encoded(reward.item())
                        ? QuestItemStackValue.decode(reward.item(), player.registryAccess()).orElse(ItemStack.EMPTY)
                        : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.item())));
                if (prototype.isEmpty()) throw new IllegalArgumentException("Unavailable reward item");
                int remaining = reward.count();
                for (var stack : planned) if (ItemStack.isSameItemSameComponents(stack, prototype)) {
                    int amount = Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                    stack.grow(amount);
                    remaining -= amount;
                }
                for (int slot = 0; slot < planned.size() && remaining > 0; slot++) if (planned.get(slot).isEmpty()) {
                    int amount = Math.min(remaining, prototype.getMaxStackSize());
                    planned.set(slot, prototype.copyWithCount(amount));
                    remaining -= amount;
                }
                if (remaining > 0) throw new IllegalStateException("Inventory full");
            }
            state.actionReceipt(player, quest, key, "running");
            reserved = true;
            player.server.overworld().getDataStorage().save();
            for (int slot = 0; slot < planned.size(); slot++) player.getInventory().setItem(slot, planned.get(slot));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            for (var reward : frozen) if (!reward.type().equals("item")) execute(player, reward);
            player.server.getPlayerList().saveAll();
            state.actionReceipt(player, quest, key, "done");
            player.server.overworld().getDataStorage().save();
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new xtdpotato.xero_quest_journal.core.network.QuestNetwork.AutomaticClaimPayload(
                            "", Component.translatable("screen.xero_quest_journal.graph.custom_rewards").getString(),
                            rewards.stream().map(QuestDefinition.Reward::display).toList()));
        } catch (Exception failure) {
            if (reserved) {
                state.actionReceipt(player, quest, key, "failed");
                player.server.overworld().getDataStorage().save();
            }
            throw failure;
        } finally { EXECUTING.remove(player.getUUID()); }
    }

    public static String claim(ServerPlayer player, QuestDefinition quest, boolean allowDrop) {
        return claim(player, quest, allowDrop, Map.of(), false);
    }

    public static boolean automatic(QuestDefinition quest, QuestDefinition.Reward reward) {
        return switch (reward.claimMode()) {
            case "automatic" -> true;
            case "manual" -> false;
            default -> quest.rewardMode().equals("automatic")
                    || quest.rewardMode().equals("default")
                    && (quest.daily() || !QuestConfig.defaults().defaultManualRewards);
        };
    }

    public static boolean hasAutomaticPending(ServerPlayer player, QuestDefinition quest,
                                              List<QuestDefinition.Reward> rewards) {
        var data = QuestProgressData.get(player.getServer());
        var entry = data.entry(player.getUUID(), quest);
        if (entry == null || entry.claimed()) return false;
        for (int index = 0; index < rewards.size(); index++) {
            if (automatic(quest, rewards.get(index))
                    && !data.receipt(player.getUUID(), quest, index).equals("done")) return true;
        }
        return false;
    }

    public static List<QuestDefinition.Reward> automaticPopupRewards(QuestDefinition quest,
                                                                      List<QuestDefinition.Reward> rewards) {
        var result = new ArrayList<QuestDefinition.Reward>();
        for (var reward : rewards) {
            if (!reward.displayed() || !automatic(quest, reward)) continue;
            if (reward.type().equals("loot_choice")) {
                var candidates = choices(reward);
                if (!candidates.isEmpty()) result.add(candidates.getFirst().display());
            } else result.add(reward.display());
        }
        return List.copyOf(result);
    }

    public static String claimAutomatic(ServerPlayer player, QuestDefinition quest) {
        return claim(player, quest, false, Map.of(), true);
    }

    public static String claimChoice(ServerPlayer player, QuestDefinition quest, int rewardIndex, int choiceIndex) {
        return claim(player, quest, false, Map.of(rewardIndex, choiceIndex), false);
    }

    public static String claimChoices(ServerPlayer player, QuestDefinition quest, Map<Integer, Integer> selections) {
        return claimChoices(player, quest, selections, false);
    }

    public static String claimChoices(ServerPlayer player, QuestDefinition quest,
                                      Map<Integer, Integer> selections, boolean allowDrop) {
        return claim(player, quest, allowDrop, selections == null ? Map.of() : selections, false);
    }

    private static String claim(ServerPlayer player, QuestDefinition quest, boolean allowDrop,
                                Map<Integer, Integer> selections, boolean automaticOnly) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()) return "permission";
        if (!QuestFocus.allowsReward(player, quest)) return "focus_active";
        if (QuestStructures.active(player)) return "scene_active";
        var data = QuestProgressData.get(player.getServer());
        var entry = data.entry(player.getUUID(), quest);
        if (entry == null) return "incomplete";
        if (entry.claimed()) return "already_claimed";
        List<QuestDefinition.Reward> rewards = new ArrayList<>(entry.rewards());
        if (selections.keySet().stream().anyMatch(index -> index < 0 || index >= rewards.size()
                || !rewards.get(index).type().equals("loot_choice"))) return "invalid_choice";
        for (int index = 0; index < rewards.size(); index++) {
            QuestDefinition.Reward reward = rewards.get(index);
            if (!reward.type().equals("loot_choice")) continue;
            if (automaticOnly && !automatic(quest, reward)) continue;
            if (data.receipt(player.getUUID(), quest, index).equals("done")) continue;
            List<QuestDefinition.Reward> candidates = choices(reward);
            Integer selected = automaticOnly ? 0 : selections.get(index);
            if (selected == null || selected < 0 || selected >= candidates.size()) return "choice_required";
            QuestDefinition.Reward resolved = withSettings(candidates.get(selected), reward);
            rewards.set(index, resolved);
            if (automaticOnly) data.replaceReward(player.getUUID(), quest, index, resolved);
        }
        if (!automaticOnly && rewards.stream().anyMatch(reward -> reward.type().equals("loot_choice")))
            return "choice_required";
        if (!EXECUTING.add(player.getUUID())) return "busy";
        try {
            List<ItemStack> planned = new ArrayList<>(player.getInventory().items.stream().map(ItemStack::copy).toList());
            List<ItemStack> drops = new ArrayList<>();
            for (int i = 0; i < rewards.size(); i++) {
                var reward = rewards.get(i);
                String receipt = data.receipt(player.getUUID(), quest, i);
                if (receipt.equals("done")) continue;
                if (automaticOnly && !automatic(quest, reward)) continue;
                if (!receipt.isEmpty()) return "reward_failed";
                if (!reward.type().equals("script") && !valid(reward)) return "unavailable";
                if (!reward.type().equals("item")) continue;
                ItemStack prototype = QuestItemStackValue.encoded(reward.item())
                        ? QuestItemStackValue.decode(reward.item(), player.registryAccess()).orElse(ItemStack.EMPTY)
                        : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.item())));
                if (prototype.isEmpty()) return "unavailable";
                int remaining = reward.count();
                for (int slot = 0; slot < planned.size() && remaining > 0; slot++) {
                    var stack = planned.get(slot);
                    if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, prototype)) {
                        int amount = Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                        stack.grow(amount);
                        remaining -= amount;
                    }
                }
                for (int slot = 0; slot < planned.size() && remaining > 0; slot++) {
                    if (planned.get(slot).isEmpty()) {
                        int amount = Math.min(remaining, prototype.getMaxStackSize());
                        planned.set(slot, prototype.copyWithCount(amount));
                        remaining -= amount;
                    }
                }
                if (remaining > 0 && !allowDrop) return "inventory_full";
                while (remaining > 0) {
                    if (drops.size() >= 256) return "unavailable";
                    int amount = Math.min(remaining, prototype.getMaxStackSize());
                    drops.add(prototype.copyWithCount(amount));
                    remaining -= amount;
                }
            }
            // Reserve the item batch before any side effect. A crash leaves an explicit uncertain receipt.
            for (int i = 0; i < rewards.size(); i++) {
                if (rewards.get(i).type().equals("item")
                        && (!automaticOnly || automatic(quest, rewards.get(i)))
                        && data.receipt(player.getUUID(), quest, i).isEmpty())
                    data.receipt(player.getUUID(), quest, i, "running");
            }
            player.getServer().overworld().getDataStorage().save();
            for (int slot = 0; slot < planned.size(); slot++) player.getInventory().setItem(slot, planned.get(slot));
            for (ItemStack drop : drops) {
                var entity = player.drop(drop, false);
                if (entity == null) throw new IllegalStateException("Reward drop canceled");
            }
            for (int i = 0; i < rewards.size(); i++) {
                if (rewards.get(i).type().equals("item")
                        && (!automaticOnly || automatic(quest, rewards.get(i)))
                        && data.receipt(player.getUUID(), quest, i).equals("running"))
                    data.receipt(player.getUUID(), quest, i, "done");
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.getServer().getPlayerList().saveAll();
            for (int i = 0; i < rewards.size(); i++) {
                var reward = rewards.get(i);
                if (data.receipt(player.getUUID(), quest, i).equals("done")) continue;
                if (automaticOnly && !automatic(quest, reward)) continue;
                data.receipt(player.getUUID(), quest, i, "running");
                player.getServer().overworld().getDataStorage().save();
                try {
                    execute(player, reward);
                    player.getServer().getPlayerList().saveAll();
                    data.receipt(player.getUUID(), quest, i, "done");
                } catch (Exception failure) {
                    data.receipt(player.getUUID(), quest, i, "failed");
                    XeroQuestJournal.LOGGER.error("Reward {} of {} failed; automatic retry blocked", i, quest.id(), failure);
                    return "reward_failed";
                } finally {
                    player.getServer().overworld().getDataStorage().save();
                }
            }
            boolean allDelivered = true;
            for (int index = 0; index < rewards.size(); index++)
                allDelivered &= data.receipt(player.getUUID(), quest, index).equals("done");
            if (allDelivered) {
                data.claimed(player.getUUID(), quest, rewards);
                player.getServer().overworld().getDataStorage().save();
            }
            return "claimed";
        } catch (Exception failure) {
            XeroQuestJournal.LOGGER.error("Reward batch {} needs administrator review", quest.id(), failure);
            return "reward_failed";
        } finally {
            EXECUTING.remove(player.getUUID());
        }
    }

    /** Executes a frozen non-item reward. The caller must persist its delivery receipt first. */
    public static void execute(ServerPlayer player, QuestDefinition.Reward reward) {
        switch (reward.type()) {
            case "experience" -> player.giveExperiencePoints(reward.count());
            case "levels" -> player.giveExperienceLevels(reward.count());
            case "command" -> command(player, reward.item());
            case "function" -> command(player, "function " + reward.item());
            case "script" -> QuestScripts.run(player, reward.item(), false);
            case "effect" -> {
                var effect = RewardEffect.parse(reward.item());
                if (effect == null) throw new IllegalArgumentException("Invalid effect reward");
                command(player, "effect give @s " + effect.id() + " " + reward.count()
                        + " " + (effect.level() - 1) + " " + !effect.particles());
            }
            case "remove_effect" -> command(player, "effect clear @s " + reward.item());
            case "message" -> player.sendSystemMessage(Component.literal(reward.item()));
            case "mail" -> {
                var draft = MailTemplateStore.find(reward.item());
                if (draft == null || !MailboxRuntime.sendIfAbsent(player, draft.id(), draft.sender(),
                        draft.subject(), draft.body(), draft.expiresIn(), draft.gameTime(),
                        draft.icon(), draft.treasured(), draft.attachments(), draft.banner(), draft.targetTaskId(),
                        draft.bannerTiming()))
                    throw new IllegalStateException("Mail template unavailable or mailbox full");
            }
            case "unlock", "lock" -> {
                for (String id : reward.item().split("\\s*,\\s*")) {
                    String result = QuestRuntime.trigger(player, id, reward.type(), false);
                    if (!result.equals(reward.type().equals("unlock") ? "unlocked" : "locked"))
                        throw new IllegalArgumentException(result);
                }
            }
            case "complete" -> {
                String result = QuestRuntime.trigger(player, reward.item(), reward.type(), false);
                if (!List.of("completed", "already_complete", "unlocked").contains(result))
                    throw new IllegalArgumentException(result);
            }
            default -> throw new IllegalArgumentException("Unknown reward type");
        }
    }
}
