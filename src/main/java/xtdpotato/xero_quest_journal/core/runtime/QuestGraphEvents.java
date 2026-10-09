package xtdpotato.xero_quest_journal.core.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

@EventBusSubscriber(modid = XeroQuestJournal.MOD_ID)
public final class QuestGraphEvents {
    private record Weather(boolean day, boolean rain, boolean thunder) {}
    private static final Map<ServerLevel, Weather> WEATHER = new WeakHashMap<>();
    private QuestGraphEvents() {}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        QuestEventGraphs.tick(event.getServer());
    }
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestEventGraphs.fire(p, "jump", "");
    }
    @SubscribeEvent public static void join(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestEventGraphs.fire(p, "join", "");
    }
    @SubscribeEvent public static void leave(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestEventGraphs.fire(p, "leave", "");
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestEventGraphs.fire(p, "dimension", e.getTo().location().toString());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestEventGraphs.fire(p, "death", "");
        if (e.getSource().getEntity() instanceof ServerPlayer p)
            QuestEventGraphs.fire(p, "kill", BuiltInRegistries.ENTITY_TYPE.getKey(e.getEntity().getType()).toString());
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestEventGraphs.fire(p, "respawn", "");
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void breakBlock(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p)
            QuestEventGraphs.fire(p, "block_break", BuiltInRegistries.BLOCK.getKey(e.getState().getBlock()).toString());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void interact(PlayerInteractEvent.RightClickBlock e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getHand() == InteractionHand.MAIN_HAND)
            QuestEventGraphs.fire(p, "block_interact", BuiltInRegistries.BLOCK.getKey(e.getLevel().getBlockState(e.getPos()).getBlock()).toString());
    }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post e) {
        if (e.getPlayer() instanceof ServerPlayer p && e.getOriginalStack().getCount() > e.getCurrentStack().getCount())
            QuestEventGraphs.fire(p, "item_pickup", BuiltInRegistries.ITEM.getKey(e.getOriginalStack().getItem()).toString());
    }
    @SubscribeEvent public static void craft(PlayerEvent.ItemCraftedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p)
            QuestEventGraphs.fire(p, "craft", BuiltInRegistries.ITEM.getKey(e.getCrafting().getItem()).toString());
    }
    @SubscribeEvent public static void smelt(PlayerEvent.ItemSmeltedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p)
            QuestEventGraphs.fire(p, "smelt", BuiltInRegistries.ITEM.getKey(e.getSmelting().getItem()).toString());
    }
    @SubscribeEvent public static void wake(PlayerWakeUpEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestEventGraphs.fire(p, "wake", "");
    }
    @SubscribeEvent public static void advancement(AdvancementEvent.AdvancementEarnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestEventGraphs.fire(p, "advancement", e.getAdvancement().id().toString());
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            if (p.tickCount % 20 == 0) QuestEventGraphs.fire(p, "tick", "");
            if (p.isSleeping() && p.getSleepTimer() == 1) QuestEventGraphs.fire(p, "sleep", "");
        }
    }
    @SubscribeEvent public static void worldTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        boolean day = Math.floorMod(level.getDayTime(), 24000) < 12000;
        Weather next = new Weather(day, level.getLevelData().isRaining(), level.getLevelData().isThundering());
        Weather previous = WEATHER.put(level, next);
        String dimension = level.dimension().location().toString();
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (QuestEventGraphs.listens("world_tick")) QuestEventGraphs.fire(player, "world_tick", dimension);
            if (previous == null) continue;
            if (previous.day() != next.day()) QuestEventGraphs.fire(player, day ? "day_start" : "night_start", dimension);
            if (previous.rain() != next.rain()) QuestEventGraphs.fire(player, next.rain() ? "rain_start" : "rain_stop", dimension);
            if (previous.thunder() != next.thunder()) QuestEventGraphs.fire(player, next.thunder() ? "thunder_start" : "thunder_stop", dimension);
        }
    }
}
