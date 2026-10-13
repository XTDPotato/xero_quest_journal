package xtdpotato.xero_quest_journal.forgecompat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

public final class ForgeSceneBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, XeroQuestJournal.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, XeroQuestJournal.MOD_ID);
    public static final RegistryObject<CompletionBlock> COMPLETION = BLOCKS.register(
            "scene_completion",
            () -> new CompletionBlock(BlockBehaviour.Properties.of().strength(2)
                    .lightLevel(state -> state.getValue(RedstoneLampBlock.LIT) ? 15 : 0)));

    static {
        ITEMS.register("scene_completion", () -> new BlockItem(COMPLETION.get(), new Item.Properties()));
    }

    private ForgeSceneBlocks() {
    }

    public static void register(IEventBus bus) {
        if (bus == null) throw new IllegalArgumentException("Forge mod event bus");
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    public static final class CompletionBlock extends Block {
        public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT =
                RedstoneLampBlock.LIT;

        public CompletionBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (!old.is(this) && !level.isClientSide) level.scheduleTick(pos, this, 1);
        }

        @Override
        public void tick(BlockState state, ServerLevel level, BlockPos pos,
                         net.minecraft.util.RandomSource random) {
            update(state, level, pos);
        }

        @Override
        public void neighborChanged(BlockState state, Level level, BlockPos pos, Block other,
                                    BlockPos from, boolean moved) {
            if (!level.isClientSide) level.scheduleTick(pos, this, 1);
        }

        private void update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean signal = level.hasNeighborSignal(pos);
            boolean rising = !state.getValue(LIT) && signal;
            if (state.getValue(LIT) != signal) {
                level.setBlock(pos, state.setValue(LIT, signal), 2);
            }
            if (rising) {
                try {
                    Class<?> structures = Class.forName(
                            "xtdpotato.xero_quest_journal.core.runtime.QuestStructures");
                    structures.getMethod("signal", ServerLevel.class, BlockPos.class).invoke(null, level, pos);
                } catch (ReflectiveOperationException ignored) {
                    // Shared gameplay sources are absent from the bootstrap build.
                }
            }
        }
    }
}
