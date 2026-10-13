package xtdpotato.xero_quest_journal.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import xtdpotato.xero_quest_journal.core.runtime.*;
import xtdpotato.xero_quest_journal.fabriccompat.*;
import xtdpotato.xero_quest_journal.fabriccompat.FabricEvents.*;

public final class FabricGameEvents {
    private FabricGameEvents() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            xtdpotato.xero_quest_journal.core.QuestCommand.register(dispatcher);
            QuestStructureEvents.commands(new RegisterCommandsEvent(dispatcher));
        });
        ServerLifecycleEvents.SERVER_STARTING.register(FabricServerLifecycle::setServer);
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                QuestStructureEvents.started(new ServerStartedEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                QuestStructureEvents.stop(new ServerStoppingEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FabricServerLifecycle.setServer(null));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            QuestRuntime.tick(new ServerTickEvent.Post(server));
            QuestGraphEvents.serverTick(new ServerTickEvent.Post(server));
            for (var player : server.getPlayerList().getPlayers()) {
                QuestGraphEvents.tick(new PlayerTickEvent.Post(player));
                QuestStructureEvents.tick(new PlayerTickEvent.Post(player));
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(level ->
                QuestGraphEvents.worldTick(new LevelTickEvent.Post(level)));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            QuestStructureEvents.login(new PlayerEvent.PlayerLoggedInEvent(handler.player));
            QuestGraphEvents.join(new PlayerEvent.PlayerLoggedInEvent(handler.player));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            QuestGraphEvents.leave(new PlayerEvent.PlayerLoggedOutEvent(handler.player));
            QuestStructureEvents.logout(new PlayerEvent.PlayerLoggedOutEvent(handler.player));
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            FabricPlayerData.get(player).merge(FabricPlayerData.get(oldPlayer));
            QuestGraphEvents.respawn(new PlayerEvent.PlayerRespawnEvent(player));
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            var event = new LivingDeathEvent(entity, source);
            QuestStructureEvents.died(event);
            return !event.isCanceled();
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            var event = new LivingDeathEvent(entity, source);
            QuestConditionEvents.killed(event);
            QuestGraphEvents.death(event);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            var event = new LivingIncomingDamageEvent(entity, source);
            QuestStructureEvents.damage(event);
            return !event.isCanceled();
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer)) return InteractionResult.PASS;
            var event = new PlayerInteractEvent.RightClickBlock(player, hand, hit.getBlockPos());
            QuestStructureEvents.useBlock(event);
            if (event.isCanceled()) return event.getCancellationResult();
            QuestConditionEvents.interacted(event);
            QuestGraphEvents.interact(event);
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!(player instanceof ServerPlayer)) return InteractionResult.PASS;
            var event = new PlayerInteractEvent.LeftClickBlock(player, hand, pos);
            QuestStructureEvents.leftBlock(event);
            return event.isCanceled() ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            var event = new BlockEvent.BreakEvent(player, pos, state);
            QuestStructureEvents.breakBlock(event);
            return !event.isCanceled();
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
            var event = new BlockEvent.BreakEvent(player, pos, state);
            QuestConditionEvents.broken(event);
            QuestGraphEvents.breakBlock(event);
        });
        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (!(player instanceof ServerPlayer)) return InteractionResult.PASS;
            var event = new PlayerInteractEvent.EntityInteract(player, hand, target);
            QuestStructureEvents.useEntity(event);
            return event.isCanceled() ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            var event = new PlayerInteractEvent.RightClickItem(player, hand);
            if (player instanceof ServerPlayer) QuestStructureEvents.useItem(event);
            return event.isCanceled() ? net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand))
                    : net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
        });
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (!(player instanceof ServerPlayer)) return InteractionResult.PASS;
            var event = new AttackEntityEvent(player, target);
            QuestStructureEvents.attack(event);
            return event.isCanceled() ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }
}
