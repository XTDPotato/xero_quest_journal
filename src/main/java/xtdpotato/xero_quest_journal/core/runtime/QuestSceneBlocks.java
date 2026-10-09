package xtdpotato.xero_quest_journal.core.runtime;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

public final class QuestSceneBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, XeroQuestJournal.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, XeroQuestJournal.MOD_ID);
    public static final java.util.function.Supplier<CompletionBlock> COMPLETION = BLOCKS.register("scene_completion",
            () -> new CompletionBlock(BlockBehaviour.Properties.of().strength(2).lightLevel(state -> state.getValue(RedstoneLampBlock.LIT) ? 15 : 0)));
    static { ITEMS.register("scene_completion", () -> new BlockItem(COMPLETION.get(), new Item.Properties())); }
    private QuestSceneBlocks() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
    public static final class CompletionBlock extends Block {
        public static final MapCodec<CompletionBlock> CODEC = simpleCodec(CompletionBlock::new);
        public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT = RedstoneLampBlock.LIT;
        public CompletionBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(LIT, false));
        }
        @Override public MapCodec<CompletionBlock> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(
                net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }
        @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (!old.is(this) && !level.isClientSide) level.scheduleTick(pos, this, 1);
        }
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos,
                                      net.minecraft.util.RandomSource random) {
            update(state, level, pos);
        }
        @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block other,
                                                  BlockPos from, boolean moved) {
            if (!level.isClientSide) level.scheduleTick(pos, this, 1);
        }
        private void update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean rising = !state.getValue(LIT) && level.hasNeighborSignal(pos);
            if (state.getValue(LIT) != level.hasNeighborSignal(pos))
                level.setBlock(pos, state.setValue(LIT, level.hasNeighborSignal(pos)), 2);
            if (rising) QuestStructures.signal(level, pos);
        }
    }
}
