package xtdpotato.xero_quest_journal.fabriccompat;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Loader-neutral event values populated by Fabric callbacks and targeted Minecraft mixins. */
public final class FabricEvents {
    private FabricEvents() {}
    public enum TriState { DEFAULT, TRUE, FALSE }
    public static class Cancelable {
        private boolean canceled;
        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean value) { canceled = value; }
    }
    public record RegisterCommandsEvent(CommandDispatcher<CommandSourceStack> getDispatcher) {}
    public record ServerStartedEvent(MinecraftServer getServer) {}
    public record ServerStoppingEvent(MinecraftServer getServer) {}
    public static class ServerTickEvent {
        public record Post(MinecraftServer getServer) {}
    }
    public static class PlayerTickEvent {
        public record Post(Player getEntity) {}
    }
    public static class LevelTickEvent {
        public record Post(Level getLevel) {
            public Post(java.util.function.BooleanSupplier ignored, Level level) { this(level); }
        }
    }
    public static class ClientTickEvent { public record Post() {} }
    public static class RenderGuiEvent {
        public record Post(net.minecraft.client.gui.GuiGraphics getGuiGraphics) {}
    }
    public static class RenderFrameEvent { public record Post() {} }
    public static class EntityTravelToDimensionEvent extends Cancelable {
        private final Entity entity;
        private final ResourceKey<Level> dimension;
        public EntityTravelToDimensionEvent(Entity entity, ResourceKey<Level> dimension) {
            this.entity = entity; this.dimension = dimension;
        }
        public Entity getEntity() { return entity; }
        public ResourceKey<Level> getDimension() { return dimension; }
    }
    public static class LivingDeathEvent extends Cancelable {
        private final LivingEntity entity;
        private final DamageSource source;
        public LivingDeathEvent(LivingEntity entity, DamageSource source) {
            this.entity = entity; this.source = source;
        }
        public LivingEntity getEntity() { return entity; }
        public DamageSource getSource() { return source; }
    }
    public static class LivingIncomingDamageEvent extends LivingDeathEvent {
        public LivingIncomingDamageEvent(LivingEntity entity, DamageSource source) { super(entity, source); }
    }
    public static class LivingEvent {
        public record LivingJumpEvent(LivingEntity getEntity) {}
    }
    public static class PlayerEvent {
        public record PlayerLoggedInEvent(Player getEntity) {}
        public record PlayerLoggedOutEvent(Player getEntity) {}
        public record PlayerRespawnEvent(Player getEntity) {}
        public record PlayerChangedDimensionEvent(Player getEntity, ResourceKey<Level> getTo) {
            public PlayerChangedDimensionEvent(Player player, ResourceKey<Level> from, ResourceKey<Level> to) { this(player, to); }
        }
        public record ItemCraftedEvent(Player getEntity, ItemStack getCrafting) {}
        public record ItemSmeltedEvent(Player getEntity, ItemStack getSmelting) {}
    }
    public record PlayerWakeUpEvent(Player getEntity) {}
    public static class AdvancementEvent {
        public record AdvancementEarnEvent(Player getEntity, net.minecraft.advancements.AdvancementHolder getAdvancement) {}
    }
    public static class PlayerInteractEvent extends Cancelable {
        private final Player player;
        private final InteractionHand hand;
        private final BlockPos pos;
        private final Entity target;
        private InteractionResult result = InteractionResult.PASS;
        public PlayerInteractEvent(Player player, InteractionHand hand, BlockPos pos, Entity target) {
            this.player = player; this.hand = hand; this.pos = pos; this.target = target;
        }
        public Player getEntity() { return player; }
        public InteractionHand getHand() { return hand; }
        public Level getLevel() { return player.level(); }
        public BlockPos getPos() { return pos; }
        public Entity getTarget() { return target; }
        public void setCancellationResult(InteractionResult value) { result = value; }
        public InteractionResult getCancellationResult() { return result; }
        public static class RightClickBlock extends PlayerInteractEvent {
            public RightClickBlock(Player p, InteractionHand hand, BlockPos pos) { super(p, hand, pos, null); }
            public RightClickBlock(Player p, InteractionHand hand, BlockPos pos, net.minecraft.world.phys.BlockHitResult hit) {
                this(p, hand, pos);
            }
        }
        public static class RightClickItem extends PlayerInteractEvent {
            public RightClickItem(Player p, InteractionHand hand) { super(p, hand, p.blockPosition(), null); }
        }
        public static class LeftClickBlock extends PlayerInteractEvent {
            public LeftClickBlock(Player p, InteractionHand hand, BlockPos pos) { super(p, hand, pos, null); }
        }
        public static class EntityInteract extends PlayerInteractEvent {
            public EntityInteract(Player p, InteractionHand hand, Entity entity) { super(p, hand, entity.blockPosition(), entity); }
        }
        public static class EntityInteractSpecific extends EntityInteract {
            public EntityInteractSpecific(Player p, InteractionHand hand, Entity entity) { super(p, hand, entity); }
        }
    }
    public static class AttackEntityEvent extends PlayerInteractEvent {
        public AttackEntityEvent(Player player, Entity target) { super(player, InteractionHand.MAIN_HAND, target.blockPosition(), target); }
    }
    public static class BlockEvent extends Cancelable {
        private final Entity entity;
        private final BlockPos pos;
        private final BlockState state;
        public BlockEvent(Entity entity, BlockPos pos, BlockState state) {
            this.entity = entity; this.pos = pos; this.state = state;
        }
        public Entity getEntity() { return entity; }
        public Player getPlayer() { return (Player) entity; }
        public BlockPos getPos() { return pos; }
        public BlockState getState() { return state; }
        public static class BreakEvent extends BlockEvent {
            public BreakEvent(Player p, BlockPos pos, BlockState state) { super(p, pos, state); }
        }
        public static class EntityPlaceEvent extends BlockEvent {
            public EntityPlaceEvent(Entity entity, BlockPos pos, BlockState state) { super(entity, pos, state); }
        }
    }
    public static class ItemEntityPickupEvent {
        public record Post(Player getPlayer, ItemStack getOriginalStack, ItemStack getCurrentStack) {}
        public static class Pre {
            private final Player player;
            private final ItemEntity item;
            private TriState pickup = TriState.DEFAULT;
            public Pre(Player player, ItemEntity item) { this.player = player; this.item = item; }
            public Player getPlayer() { return player; }
            public ItemEntity getItemEntity() { return item; }
            public void setCanPickup(TriState value) { pickup = value; }
            public TriState canPickup() { return pickup; }
        }
    }
}
