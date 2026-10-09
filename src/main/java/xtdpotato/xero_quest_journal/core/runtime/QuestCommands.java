package xtdpotato.xero_quest_journal.core.runtime;

import com.mojang.brigadier.context.ContextChain;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;

/** A receipt must not finish while a function is still queued in an outer command. */
final class QuestCommands {
    private QuestCommands() {}

    static int execute(ServerPlayer player, String command) {
        if (!player.getServer().isSameThread() || command.isBlank()
                || command.length() > 4096 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid reward command");
        String value = command.startsWith("/") ? command.substring(1) : command;
        var outcome = new Outcome();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput()
                .withCallback((success, result) -> { if (!success) outcome.failed = true; });
        var event = new CommandEvent(player.getServer().getCommands().getDispatcher().parse(value, source));
        if (NeoForge.EVENT_BUS.post(event).isCanceled())
            throw new IllegalArgumentException("Reward command canceled", event.getException());
        var parsed = event.getParseResults();
        try {
            Commands.validateParseResults(parsed);
            var chain = ContextChain.tryFlatten(parsed.getContext().build(value))
                    .orElseThrow(() -> new IllegalArgumentException("Incomplete reward command"));
            var rules = player.getServer().getGameRules();
            int limit = Math.max(1, Math.min(65536, rules.getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH)));
            int forks = Math.max(1, Math.min(1024, rules.getInt(GameRules.RULE_MAX_COMMAND_FORK_COUNT)));
            try (var context = new ExecutionContext<CommandSourceStack>(limit, forks, player.getServer().getProfiler())) {
                context.tracer(outcome);
                ExecutionContext.queueInitialCommandExecution(context, value, chain, parsed.getContext().getSource(),
                        (success, result) -> { outcome.finished = true; if (!success) outcome.failed = true; });
                // The sentinel runs after dynamically inserted function bodies, unless execution was interrupted.
                var frame = new net.minecraft.commands.execution.Frame(0, CommandResultCallback.EMPTY, () -> {});
                context.queueNext(new net.minecraft.commands.execution.CommandQueueEntry<>(frame,
                        (ignored, ignoredFrame) -> outcome.finished = true));
                context.runCommandQueue();
            }
            if (outcome.failed || !outcome.finished)
                throw new IllegalArgumentException("Reward command failed or exceeded its execution budget");
            return outcome.result;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalArgumentException("Reward command failed", failure);
        }
    }

    private static final class Outcome implements TraceCallbacks {
        boolean failed;
        boolean finished;
        int result;
        @Override public void onCommand(int depth, String command) {}
        @Override public void onReturn(int depth, String command, int value) { result = value; }
        @Override public void onError(String message) { failed = true; }
        @Override public void onCall(int depth, ResourceLocation function, int size) {}
        @Override public void close() {}
    }
}
