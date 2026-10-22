package xtdpotato.xero_quest_journal.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

import java.util.Collection;
import java.util.List;

public final class QuestCommand {
    private QuestCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xero_quest")
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            if (!QuestConfig.reload()) {
                                context.getSource().sendFailure(Component.translatable(
                                        "command.xero_quest_journal.reload_failed", "config.json"));
                                return 0;
                            }
                            QuestRepository.reload();
                            if (!QuestRepository.errors().isEmpty()) {
                                context.getSource().sendFailure(Component.translatable(
                                        "command.xero_quest_journal.reload_failed", QuestRepository.errors().getFirst()));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "command.xero_quest_journal.reloaded"), true);
                            return 1;
                        }))
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            int count = QuestRepository.all().size();
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "command.xero_quest_journal.loaded", count), false);
                            return count;
                        }))
                .then(openCommands())
                .then(triggerCommands())
                .then(eventCommands())
                .then(rewardCommands())
                .then(configCommands())
                .then(editModeCommand())
                .then(dailyCommands())
                .then(generateCommand())
                .then(Commands.literal("locate").then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            String id = StringArgumentType.getString(context, "id");
                            if (!xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.tracked(player).contains(id))
                                xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.toggleTracking(player, id);
                            String result = xtdpotato.xero_quest_journal.core.runtime.QuestStructureGuidance.request(player, id);
                            player.sendSystemMessage(Component.translatable("screen.xero_quest_journal.action." + result));
                            return result.startsWith("guidance_") ? 1 : 0;
                        })))
                .then(Commands.literal("start_generate_gui").executes(context -> {
                    xtdpotato.xero_quest_journal.core.network.QuestGenerationNetwork.open(
                            context.getSource().getPlayerOrException(), false);
                    return 1;
                }))
                .then(deleteGeneratedCommand())
                .then(mailCommands()));
        dispatcher.register(toastCommand());
        dispatcher.register(anecdoteCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> anecdoteCommand() {
        var positions = Commands.argument("position", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    for (String value : List.of("top", "bottom", "left", "start", "right", "end", "center",
                            "top,left", "top,right", "bottom,left", "bottom,right", "top,center",
                            "bottom,center", "left,center", "right,center")) builder.suggest(value);
                    return builder.buildFuture();
                })
                .executes(context -> sendAnecdote(context.getSource(),
                        EntityArgument.getPlayers(context, "targets"),
                        DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"),
                        DoubleArgumentType.getDouble(context, "scale"),
                        StringArgumentType.getString(context, "position")));
        var scale = Commands.argument("scale", DoubleArgumentType.doubleArg(.1, 2))
                .executes(context -> sendAnecdote(context.getSource(),
                        EntityArgument.getPlayers(context, "targets"),
                        DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"),
                        DoubleArgumentType.getDouble(context, "scale"), "top"))
                .then(positions);
        var y = Commands.argument("y", DoubleArgumentType.doubleArg(-8192, 8192))
                .executes(context -> sendAnecdote(context.getSource(),
                        EntityArgument.getPlayers(context, "targets"),
                        DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"),
                        .7, "top"))
                .then(scale);
        var x = Commands.argument("x", DoubleArgumentType.doubleArg(-8192, 8192))
                .executes(context -> sendAnecdote(context.getSource(),
                        EntityArgument.getPlayers(context, "targets"),
                        DoubleArgumentType.getDouble(context, "x"), 0, .7, "top"))
                .then(y);
        return Commands.literal("xero_anecdote").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> sendAnecdote(context.getSource(),
                                EntityArgument.getPlayers(context, "targets"), 0, 0, .7, "top"))
                        .then(x));
    }

    private static int sendAnecdote(CommandSourceStack source, Collection<ServerPlayer> targets,
                                    double x, double y, double scale, String position) {
        final QuestNetwork.AnecdotePayload payload;
        try {
            payload = new QuestNetwork.AnecdotePayload(x, y, scale, position);
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
        for (ServerPlayer target : targets) PacketDistributor.sendToPlayer(target, payload);
        return targets.size();
    }

    /** Daily commissions: players claim their bonus; administrators inspect or re-roll a selection. */
    private static LiteralArgumentBuilder<CommandSourceStack> dailyCommands() {
        var root = Commands.literal("daily");
        root.then(Commands.literal("claim").executes(context -> {
            String result = xtdpotato.xero_quest_journal.core.runtime.QuestDaily.claimBonus(
                    context.getSource().getPlayerOrException());
            context.getSource().sendSuccess(() -> Component.translatable(
                    "screen.xero_quest_journal.action." + result), false);
            return result.equals("daily_claimed") ? 1 : 0;
        }));
        root.then(Commands.literal("reroll").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players()).executes(context -> {
                    var targets = EntityArgument.getPlayers(context, "targets");
                    for (ServerPlayer target : targets)
                        xtdpotato.xero_quest_journal.core.runtime.QuestDaily.reroll(target);
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "command.xero_quest_journal.daily_rerolled", targets.size()), true);
                    return targets.size();
                })));
        root.then(Commands.literal("status").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                    xtdpotato.xero_quest_journal.core.runtime.QuestDaily.refresh(target);
                    var data = xtdpotato.xero_quest_journal.core.runtime.QuestProgressData.get(target.getServer());
                    var quests = xtdpotato.xero_quest_journal.core.runtime.QuestDaily.todays(data, target.getUUID());
                    int done = xtdpotato.xero_quest_journal.core.runtime.QuestDaily.completed(data, target.getUUID());
                    var roll = data.daily(target.getUUID());
                    boolean claimed = roll != null && roll.bonusClaimed();
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "command.xero_quest_journal.daily_status", target.getGameProfile().getName(),
                            done, quests.size(), String.join(", ", quests),
                            Component.translatable(claimed ? "gui.yes" : "gui.no")), false);
                    return quests.size();
                })));
        return root;
    }

    /**
     * {@code /xero_quest generate [namespace]}: builds tasks from advancements, items/recipes and
     * hostile mobs of every installed mod (or just one namespace). Existing identities and authored edits are kept.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> generateCommand() {
        // /xero_quest generate [all|<namespace>] [tier|recipe|output|none] [multiplier 1-64]
        return Commands.literal("generate").requires(source -> source.hasPermission(2))
                .executes(context -> generateTasks(context.getSource(), "", "tier", 1))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("all");
                            xtdpotato.xero_quest_journal.core.io.QuestGenerator
                                    .namespaces(context.getSource().getServer()).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> generateTasks(context.getSource(),
                                StringArgumentType.getString(context, "target"), "tier", 1))
                        .then(Commands.argument("rewards", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String mode : List.of("tier", "recipe", "output", "none")) builder.suggest(mode);
                                    return builder.buildFuture();
                                })
                                .executes(context -> generateTasks(context.getSource(),
                                        StringArgumentType.getString(context, "target"),
                                        StringArgumentType.getString(context, "rewards"), 1))
                                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> generateTasks(context.getSource(),
                                                StringArgumentType.getString(context, "target"),
                                                StringArgumentType.getString(context, "rewards"),
                                                IntegerArgumentType.getInteger(context, "multiplier"))))));
    }

    /** Runs in the background; progress lines come back on the server thread as each mod is saved and loaded. */
    private static int generateTasks(CommandSourceStack source, String namespace, String rewards, int multiplier) {
        var mode = xtdpotato.xero_quest_journal.core.io.QuestGenerator.RewardMode.parse(rewards);
        if (mode == null) {
            source.sendFailure(Component.translatable("command.xero_quest_journal.generate_invalid_rewards", rewards));
            return 0;
        }
        var server = source.getServer();
        boolean started = xtdpotato.xero_quest_journal.core.io.QuestGenerator.start(server, namespace, mode, multiplier,
                (message, failure) -> server.execute(() -> {
                    if (failure) source.sendFailure(message);
                    else source.sendSuccess(() -> message, true);
                }));
        if (!started) {
            source.sendFailure(Component.translatable("command.xero_quest_journal.generate_busy"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.xero_quest_journal.generate_analyzing"), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> deleteGeneratedCommand() {
        return Commands.literal("delete_generated").requires(source -> source.hasPermission(2))
                .executes(context -> {
                    context.getSource().sendFailure(Component.translatable(
                            "command.xero_quest_journal.delete_generated_confirm"));
                    return 0;
                })
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("all");
                            QuestRepository.groups().stream().filter(g -> g.id().startsWith("auto_"))
                                    .forEach(g -> builder.suggest(g.id().substring(5)));
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            context.getSource().sendFailure(Component.translatable(
                                    "command.xero_quest_journal.delete_generated_confirm"));
                            return 0;
                        })
                        .then(Commands.literal("confirm").executes(context -> {
                            var source = context.getSource();
                            var result = xtdpotato.xero_quest_journal.core.io.QuestGenerator.deleteGenerated(
                                    source.getServer(), StringArgumentType.getString(context, "target"));
                            if (result.error() != null) {
                                source.sendFailure(Component.translatable(
                                        "command.xero_quest_journal.delete_generated_failed", result.error()));
                                return 0;
                            }
                            source.sendSuccess(() -> Component.translatable(
                                    "command.xero_quest_journal.delete_generated_done", result.tasks()), true);
                            return result.tasks();
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> editModeCommand() {
        return Commands.literal("edit_mode").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    var targets = EntityArgument.getPlayers(context, "targets");
                                    for (ServerPlayer target : targets) {
                                        target.getPersistentData().putBoolean(MailboxRuntime.EDIT_MODE_KEY, enabled);
                                        PacketDistributor.sendToPlayer(target,
                                                new QuestNetwork.EditModePayload(enabled));
                                    }
                                    return targets.size();
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toastCommand() {
        return Commands.literal("xero_toast").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("content", StringArgumentType.greedyString())
                                .executes(context -> {
                                    var targets = EntityArgument.getPlayers(context, "targets");
                                    String content = StringArgumentType.getString(context, "content");
                                    if (content.isBlank() || content.length() > 2048) {
                                        context.getSource().sendFailure(Component.translatable(
                                                "command.xero_quest_journal.toast_invalid"));
                                        return 0;
                                    }
                                    for (ServerPlayer target : targets) {
                                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(target,
                                                new QuestNetwork.ToastPayload(content));
                                    }
                                    return targets.size();
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> triggerCommands() {
        var root = Commands.literal("trigger").requires(source -> source.hasPermission(2));
        for (String action : List.of("complete", "evaluate", "advance_step", "unlock", "lock", "reset")) {
            root.then(Commands.literal(action).then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("id", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                QuestRepository.all().forEach(quest -> builder.suggest(quest.id()));
                                if (action.equals("unlock") || action.equals("lock"))
                                    QuestRepository.groups().forEach(group -> builder.suggest(group.id()));
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                int changed = 0;
                                for (var player : EntityArgument.getPlayers(context, "targets")) {
                                    String id = StringArgumentType.getString(context, "id");
                                    String result = action.equals("reset")
                                            ? xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.resetTrusted(player, id)
                                            : xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.trigger(player, id, action, false);
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "screen.xero_quest_journal.action." + result), true);
                                    if (List.of("completed", "step_completed", "unlocked", "locked", "reset")
                                            .contains(result)) changed++;
                                }
                                return changed;
                            }))));
        }
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> eventCommands() {
        var root = Commands.literal("event").requires(source -> source.hasPermission(2));
        for (String type : List.of("event", "stage")) {
            root.then(Commands.literal(type).then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                    .executes(context -> {
                                        var players = EntityArgument.getPlayers(context, "targets");
                                        for (var player : players) {
                                            xtdpotato.xero_quest_journal.core.runtime.QuestConditions.record(player, type,
                                                    StringArgumentType.getString(context, "name"),
                                                    IntegerArgumentType.getInteger(context, "amount"));
                                            xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.evaluate(player);
                                        }
                                        context.getSource().sendSuccess(() -> Component.translatable(
                                                "command.xero_quest_journal.event_recorded", players.size()), true);
                                        return players.size();
                                    })))));
        }
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rewardCommands() {
        var root = Commands.literal("reward");
        root.then(Commands.literal("claim_all").executes(context -> rewardResult(context.getSource(),
                xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.claimAll(context.getSource().getPlayerOrException()))));
        for (String action : List.of("claim", "claim_drop")) {
            root.then(Commands.literal(action).then(Commands.argument("id", StringArgumentType.word())
                    .executes(context -> rewardResult(context.getSource(),
                            xtdpotato.xero_quest_journal.core.runtime.QuestRuntime.claim(
                                    context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "id"),
                                    action.equals("claim_drop"))))));
        }
        root.then(Commands.literal("receipts").requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player()).then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> {
                            var player = EntityArgument.getPlayer(context, "target");
                            var quest = QuestRepository.find(StringArgumentType.getString(context, "id"));
                            if (quest == null) return rewardResult(context.getSource(), "missing");
                            var data = xtdpotato.xero_quest_journal.core.runtime.QuestProgressData.get(player.getServer());
                            var entry = data.entry(player.getUUID(), quest);
                            if (entry == null) return rewardResult(context.getSource(), "incomplete");
                            for (int i = 0; i < entry.rewards().size(); i++) {
                                String line = i + ": " + entry.rewards().get(i).type() + " = " + data.receipt(player.getUUID(), quest, i);
                                context.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            return entry.rewards().size();
                        }))));
        // Only acknowledge a manually verified receipt. Never expose a blind retry of non-idempotent scripts.
        root.then(Commands.literal("acknowledge").requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player()).then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, 127)).executes(context -> {
                            var player = EntityArgument.getPlayer(context, "target");
                            var quest = QuestRepository.find(StringArgumentType.getString(context, "id"));
                            if (quest == null) return rewardResult(context.getSource(), "missing");
                            var data = xtdpotato.xero_quest_journal.core.runtime.QuestProgressData.get(player.getServer());
                            var entry = data.entry(player.getUUID(), quest);
                            int index = IntegerArgumentType.getInteger(context, "index");
                            if (entry == null || index >= entry.rewards().size()) return rewardResult(context.getSource(), "unavailable");
                            String receipt = data.receipt(player.getUUID(), quest, index);
                            if (!List.of("failed", "running").contains(receipt)) return rewardResult(context.getSource(), "unavailable");
                            data.receipt(player.getUUID(), quest, index, "done");
                            player.getServer().overworld().getDataStorage().save();
                            xtdpotato.xero_quest_journal.XeroQuestJournal.LOGGER.warn(
                                    "Administrator {} acknowledged reward {} for {} / {} (previous {})",
                                    context.getSource().getTextName(), index, player.getUUID(), quest.id(), receipt);
                            return rewardResult(context.getSource(), "acknowledged");
                        })))));
        return root;
    }

    private static int rewardResult(CommandSourceStack source, String result) {
        var message = Component.translatable("screen.xero_quest_journal.action." + result);
        boolean success = List.of("claimed", "partially_claimed", "acknowledged").contains(result);
        if (success) source.sendSuccess(() -> message, false);
        else source.sendFailure(message);
        return success ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> openCommands() {
        var root = Commands.literal("open");
        for (String screen : List.of("journal", "overview", "mailbox", "settings", "templates", "anecdotes")) {
            root.then(Commands.literal(screen)
                    .executes(context -> open(context.getSource(),
                            List.of(context.getSource().getPlayerOrException()), screen, ""))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .requires(source -> source.hasPermission(2))
                            .executes(context -> open(context.getSource(),
                                    EntityArgument.getPlayers(context, "targets"), screen, ""))));
        }
        root.then(Commands.literal("task")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            QuestRepository.all().forEach(quest -> builder.suggest(quest.id()));
                            return builder.buildFuture();
                        })
                        .executes(context -> open(context.getSource(),
                                List.of(context.getSource().getPlayerOrException()), "task",
                                StringArgumentType.getString(context, "id")))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> open(context.getSource(),
                                        EntityArgument.getPlayers(context, "targets"), "task",
                                        StringArgumentType.getString(context, "id"))))));
        return root;
    }

    private static int open(CommandSourceStack source, Collection<ServerPlayer> targets,
                            String screen, String targetId) {
        if (screen.equals("task") && QuestRepository.find(targetId) == null) {
            source.sendFailure(Component.translatable("command.xero_quest_journal.task_missing", targetId));
            return 0;
        }
        for (ServerPlayer player : targets) {
            PacketDistributor.sendToPlayer(player, new QuestNetwork.OpenScreenPayload(screen, targetId));
        }
        source.sendSuccess(() -> Component.translatable("command.xero_quest_journal.opened",
                screen, targets.size()), true);
        return targets.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mailCommands() {
        var root = Commands.literal("mail").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("list")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer player = EntityArgument.getPlayer(context, "target");
                            int count = MailboxRuntime.snapshot(player).size();
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "command.xero_quest_journal.mail_count", player.getDisplayName(), count), false);
                            return count;
                        })));
        root.then(sendMailCommand(false));
        root.then(sendMailCommand(true));
        root.then(sendEncodedMailCommand(false));
        root.then(sendEncodedMailCommand(true));
        root.then(Commands.literal("send_saved").then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("template", StringArgumentType.word()).executes(context -> {
                    var draft = xtdpotato.xero_quest_journal.core.io.MailTemplateStore.find(
                            StringArgumentType.getString(context, "template"));
                    if (draft == null) return 0;
                    int sent = 0;
                    for (var target : EntityArgument.getPlayers(context, "targets"))
                        if (MailboxRuntime.sendIfAbsent(target, draft.id(), draft.sender(), draft.subject(),
                                draft.body(), draft.expiresIn(), draft.gameTime(), draft.icon(), draft.treasured(),
                                draft.attachments(), draft.banner(), draft.targetTaskId(), draft.bannerTiming()))
                            sent++;
                    return sent;
                }))));
        root.then(Commands.literal("send_bundle").then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("bundle", StringArgumentType.word()).executes(context -> {
                    try {
                        String encoded = StringArgumentType.getString(context, "bundle");
                        if (encoded.length() > 32000) return 0;
                        var draft = new com.google.gson.Gson().fromJson(new String(
                                java.util.Base64.getUrlDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8),
                                xtdpotato.xero_quest_journal.core.mailbox.MailDraft.class);
                        xtdpotato.xero_quest_journal.core.io.MailTemplateStore.validate(draft);
                        int sent = 0;
                        for (var target : EntityArgument.getPlayers(context, "targets"))
                            if (MailboxRuntime.sendIfAbsent(target, draft.id(), draft.sender(), draft.subject(),
                                    draft.body(), draft.expiresIn(), draft.gameTime(), draft.icon(), draft.treasured(),
                                    draft.attachments(), draft.banner(), draft.targetTaskId(), draft.bannerTiming()))
                                sent++;
                        return sent;
                    } catch (Exception invalid) { return 0; }
                }))));
        addMailAction(root, "read", MailboxRuntime::markRead, true);
        addMailAction(root, "star", MailboxRuntime::toggleStarred, true);
        addMailAction(root, "delete", MailboxRuntime::delete, true);
        addMailAction(root, "claim", MailboxRuntime::claim, true);
        addMailAction(root, "claim_all", (player, ignored) -> MailboxRuntime.claimAll(player), false);
        addMailAction(root, "delete_read", (player, ignored) -> MailboxRuntime.deleteRead(player), false);
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> configCommands() {
        var root = Commands.literal("config").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("show").executes(context -> {
            var value = QuestConfig.defaults();
            context.getSource().sendSuccess(() -> Component.literal(
                    "maxTrackedQuests=" + value.maxTrackedQuests
                            + ", defaultManualRewards=" + value.defaultManualRewards
                            + ", defaultItemCount=" + value.defaultItemCount
                            + ", taskPagination=" + value.taskPagination
                            + ", uiAnimationMillis=" + value.uiAnimationMillis), false);
            return 1;
        }));
        root.then(Commands.literal("set")
                .then(Commands.literal("max_tracked")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 3))
                                .executes(context -> saveConfig(context.getSource(), () ->
                                        QuestConfig.defaults().maxTrackedQuests =
                                                IntegerArgumentType.getInteger(context, "value")))))
                .then(Commands.literal("manual_rewards")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> saveConfig(context.getSource(), () ->
                                        QuestConfig.defaults().defaultManualRewards =
                                                BoolArgumentType.getBool(context, "value")))))
                .then(Commands.literal("default_item_count")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(context -> saveConfig(context.getSource(), () ->
                                        QuestConfig.defaults().defaultItemCount =
                                                IntegerArgumentType.getInteger(context, "value")))))
                .then(Commands.literal("animation_ms")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 2000))
                                .executes(context -> saveConfig(context.getSource(), () ->
                                        QuestConfig.defaults().uiAnimationMillis =
                                                IntegerArgumentType.getInteger(context, "value")))))
                .then(Commands.literal("task_pagination")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> saveConfig(context.getSource(), () ->
                                        QuestConfig.defaults().taskPagination =
                                                BoolArgumentType.getBool(context, "value"))))));
        return root;
    }

    private static int saveConfig(CommandSourceStack source, Runnable update) {
        update.run();
        if (!QuestConfig.save()) {
            source.sendFailure(Component.translatable("command.xero_quest_journal.config_save_failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.xero_quest_journal.config_saved"), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sendMailCommand(boolean withItem) {
        var body = Commands.argument("body", StringArgumentType.greedyString())
                .executes(context -> sendMail(
                        context.getSource(),
                        EntityArgument.getPlayers(context, "targets"),
                        StringArgumentType.getString(context, "id"),
                        StringArgumentType.getString(context, "sender"),
                        StringArgumentType.getString(context, "subject"),
                        IntegerArgumentType.getInteger(context, "expires_seconds"),
                        withItem ? ResourceLocationArgument.getId(context, "item") : null,
                        withItem ? IntegerArgumentType.getInteger(context, "count") : 0,
                        StringArgumentType.getString(context, "body")));
        var tail = withItem
                ? Commands.argument("item", ResourceLocationArgument.id())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 1_000_000)).then(body))
                : body;
        var expires = Commands.argument("expires_seconds", IntegerArgumentType.integer(0, 31_536_000)).then(tail);
        var subject = Commands.argument("subject", StringArgumentType.string()).then(expires);
        var sender = Commands.argument("sender", StringArgumentType.string()).then(subject);
        var id = Commands.argument("id", StringArgumentType.word()).then(sender);
        var targets = Commands.argument("targets", EntityArgument.players()).then(id);
        return Commands.literal(withItem ? "send_item" : "send").then(targets);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sendEncodedMailCommand(boolean withItem) {
        var body = Commands.argument("encoded_body", StringArgumentType.word())
                .executes(context -> {
                    try {
                        byte[] decoded = java.util.Base64.getUrlDecoder().decode(
                                StringArgumentType.getString(context, "encoded_body"));
                        if (decoded.length > 8192) return 0;
                        String text = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                        return sendMail(context.getSource(), EntityArgument.getPlayers(context, "targets"),
                                StringArgumentType.getString(context, "id"),
                                StringArgumentType.getString(context, "sender"),
                                StringArgumentType.getString(context, "subject"), 0,
                                withItem ? ResourceLocationArgument.getId(context, "item") : null,
                                withItem ? IntegerArgumentType.getInteger(context, "count") : 0, text);
                    } catch (IllegalArgumentException invalid) {
                        return 0;
                    }
                });
        var tail = withItem
                ? Commands.argument("item", ResourceLocationArgument.id())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 1_000_000)).then(body))
                : body;
        return Commands.literal(withItem ? "send_item_encoded" : "send_encoded")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("sender", StringArgumentType.string())
                                        .then(Commands.argument("subject", StringArgumentType.string())
                                                .then(tail)))));
    }

    private static int sendMail(CommandSourceStack source, Collection<ServerPlayer> targets, String id,
                                String sender, String subject, int expiresSeconds, ResourceLocation item,
                                int count, String body) {
        List<MailboxRuntime.Attachment> attachments = List.of();
        if (item != null) {
            if (!BuiltInRegistries.ITEM.containsKey(item)) {
                source.sendFailure(Component.translatable("command.xero_quest_journal.mail_item_invalid", item));
                return 0;
            }
            attachments = List.of(new MailboxRuntime.Attachment("item", item.toString(), count, false));
        }
        long expiresAt = expiresSeconds == 0 ? 0 : System.currentTimeMillis() + expiresSeconds * 1000L;
        for (ServerPlayer player : targets) {
            MailboxRuntime.send(player, id, sender, subject, body, expiresAt, attachments);
        }
        source.sendSuccess(() -> Component.translatable("command.xero_quest_journal.mail_sent",
                targets.size(), id), true);
        return targets.size();
    }

    private static void addMailAction(LiteralArgumentBuilder<CommandSourceStack> root, String name,
                                      MailAction action, boolean hasId) {
        var target = Commands.argument("targets", EntityArgument.players());
        if (hasId) {
            target.then(Commands.argument("id", StringArgumentType.word())
                    .executes(context -> runMailAction(context.getSource(),
                            EntityArgument.getPlayers(context, "targets"),
                            StringArgumentType.getString(context, "id"), action)));
        } else {
            target.executes(context -> runMailAction(context.getSource(),
                    EntityArgument.getPlayers(context, "targets"), "", action));
        }
        root.then(Commands.literal(name).then(target));
    }

    private static int runMailAction(CommandSourceStack source, Collection<ServerPlayer> targets,
                                     String id, MailAction action) {
        int changed = 0;
        String last = "";
        for (ServerPlayer player : targets) {
            last = action.run(player, id);
            if (List.of("read", "starred", "deleted", "claimed").contains(last)) changed++;
        }
        String result = last;
        int changedCount = changed;
        source.sendSuccess(() -> Component.translatable("command.xero_quest_journal.mail_result",
                result, changedCount, targets.size()), true);
        return changed;
    }

    @FunctionalInterface
    private interface MailAction {
        String run(ServerPlayer player, String id);
    }
}
