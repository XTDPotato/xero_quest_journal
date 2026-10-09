package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.StringArgumentType;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

@EventBusSubscriber(modid = XeroQuestJournal.MOD_ID)
public final class QuestStructureEvents {
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("xero_structure")
                .then(Commands.literal("leave").executes(context -> {
                    QuestStructures.leave(context.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("reset").executes(context -> action(context.getSource().getPlayerOrException(), () -> {
                    QuestStructures.reset(context.getSource().getPlayerOrException()); return ""; })))
                .then(Commands.literal("edit").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("reference", StringArgumentType.greedyString())
                                .executes(context -> action(context.getSource().getPlayerOrException(), () -> {
                                    QuestStructures.edit(context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "reference")); return ""; }))))
                .then(Commands.literal("import").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("template", StringArgumentType.greedyString())
                                .executes(context -> action(context.getSource().getPlayerOrException(), () ->
                                        QuestStructureLibrary.importTemplate(context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "template"))))))
                .then(Commands.literal("draft").requires(s -> s.hasPermission(2))
                        .executes(context -> action(context.getSource().getPlayerOrException(),
                                () -> QuestStructureLibrary.saveEdit(context.getSource().getPlayerOrException(), true))))
                .then(Commands.literal("save").requires(s -> s.hasPermission(2))
                        .executes(context -> action(context.getSource().getPlayerOrException(),
                                () -> QuestStructureLibrary.saveEdit(context.getSource().getPlayerOrException(), false)))));
    }
    private interface Operation { String run() throws Exception; }
    private static int action(ServerPlayer player, Operation operation) {
        try {
            String result = operation.run();
            if (!result.isEmpty()) player.sendSystemMessage(Component.translatable(
                    "screen.xero_quest_journal.structure.saved_copy", result));
            return 1;
        } catch (Exception failure) {
            String key = failure.getMessage();
            player.sendSystemMessage(Component.translatable("screen.xero_quest_journal."
                    + (key != null && key.startsWith("structure.") ? key : "structure.invalid")));
            return 0;
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestStructures.leave(p);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestStructures.leave(p);
    }
    @SubscribeEvent public static void died(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && QuestStructures.active(p)) {
            e.setCanceled(true);
            switch (QuestStructures.settings(p).death()) {
                case "spawn" -> QuestStructures.respawn(p, false);
                case "checkpoint" -> QuestStructures.respawn(p, true);
                default -> QuestStructures.leave(p);
            }
        }
    }
    @SubscribeEvent public static void travel(EntityTravelToDimensionEvent e) {
        boolean sourceScene = e.getEntity().level().dimension().equals(QuestStructures.DIMENSION);
        boolean targetScene = e.getDimension().equals(QuestStructures.DIMENSION);
        if (e.getEntity() instanceof ServerPlayer p) {
            boolean active = QuestStructures.active(p);
            if (targetScene && !active
                    || sourceScene && active && !QuestStructures.returning(p) && !targetScene)
                e.setCanceled(true);
        } else if (sourceScene || targetScene) {
            e.setCanceled(true);
        }
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            QuestStructures.tick(p);
            if (p.tickCount % 10 == 0) QuestStructures.checkBoundary(p);
            if (p.tickCount % 2 == 0) QuestSceneTriggers.tick(p);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent e) {
        for (var player : e.getServer().getPlayerList().getPlayers()) QuestStructures.leave(player);
    }
    @SubscribeEvent public static void started(ServerStartedEvent e) {
        // Only release chunks allocated by this mod, never chunks belonging to other mods.
        QuestStructures.releaseOrphanChunks(e.getServer());
    }
    @SubscribeEvent public static void useBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!QuestStructures.allows(p, e.getPos(), "use")) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.FAIL);
        } else if (QuestStructures.active(p)) QuestSceneTriggers.interact(p, e.getPos());
    }
    @SubscribeEvent public static void useItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getEntity() instanceof ServerPlayer p && !QuestStructures.allows(p, p.blockPosition(), "use")) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getEntity() instanceof ServerPlayer p && !QuestStructures.allows(p, e.getPos(), "build"))
            e.setCanceled(true);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && !QuestStructures.allows(p, e.getPos(), "build"))
            e.setCanceled(true);
    }
    @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !QuestStructures.allows(p, e.getPos(), "build"))
            e.setCanceled(true);
    }
    @SubscribeEvent public static void useEntity(PlayerInteractEvent.EntityInteract e) {
        if (e.getEntity() instanceof ServerPlayer p && (!QuestStructures.allows(p, e.getTarget().blockPosition(), "use")
                || QuestStructures.active(p) && e.getTarget() instanceof net.minecraft.world.entity.player.Player)) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public static void useSpecificEntity(PlayerInteractEvent.EntityInteractSpecific e) {
        if (e.getEntity() instanceof ServerPlayer p && (!QuestStructures.allows(p, e.getTarget().blockPosition(), "use")
                || QuestStructures.active(p) && e.getTarget() instanceof net.minecraft.world.entity.player.Player)) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public static void attack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && (!QuestStructures.allows(p, e.getTarget().blockPosition(), "attack")
                || QuestStructures.active(p) && e.getTarget() instanceof net.minecraft.world.entity.player.Player))
            e.setCanceled(true);
    }
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && QuestStructures.active(p)
                && QuestStructures.settings(p).interaction().equals("view")) e.setCanceled(true);
        if (e.getSource().getEntity() instanceof ServerPlayer p
                && (!QuestStructures.allows(p, e.getEntity().blockPosition(), "attack")
                || QuestStructures.active(p) && e.getEntity() instanceof net.minecraft.world.entity.player.Player))
            e.setCanceled(true);
    }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Pre e) {
        if (e.getPlayer() instanceof ServerPlayer p
                && !QuestStructures.allows(p, e.getItemEntity().blockPosition(), "use"))
            e.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
}
