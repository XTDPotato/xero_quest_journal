package xtdpotato.xero_quest_journal;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import xtdpotato.xero_quest_journal.forgecompat.RegisterPayloadHandlersEvent;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.platform.forge.ForgePlatform;
import xtdpotato.xero_quest_journal.forgecompat.ForgeNetworkBridge;
import xtdpotato.xero_quest_journal.forgecompat.ForgeSceneBlocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(XeroQuestJournal.MOD_ID)
public final class XeroQuestJournal {
    public static final String MOD_ID = "xero_quest_journal";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public XeroQuestJournal() {
        PlatformServices.install(new ForgePlatform());
        ForgeNetworkBridge.initialize();
        ForgeSceneBlocks.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        bootstrapSharedRuntime();
        registerSharedNetwork();
    }

    private void bootstrapSharedRuntime() {
        try {
            Class.forName("xtdpotato.xero_quest_journal.core.QuestConfig")
                    .getMethod("ensureDefaults").invoke(null);
            Class.forName("xtdpotato.xero_quest_journal.core.io.QuestRepository")
                    .getMethod("reload").invoke(null);
        } catch (ClassNotFoundException ignored) {
            // The standalone bootstrap profile intentionally omits shared gameplay.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to initialize shared quest runtime", exception);
        }
    }

    private void registerSharedNetwork() {
        try {
            Class<?> network = Class.forName("xtdpotato.xero_quest_journal.core.network.QuestNetwork");
            network.getMethod("register", RegisterPayloadHandlersEvent.class)
                    .invoke(null, new RegisterPayloadHandlersEvent());
        } catch (ClassNotFoundException ignored) {
            // The bootstrap build intentionally omits shared gameplay sources.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to register Forge payloads", exception);
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        try {
            Class<?> commands = Class.forName("xtdpotato.xero_quest_journal.core.QuestCommand");
            commands.getMethod("register", CommandDispatcher.class).invoke(null, dispatcher);
        } catch (ClassNotFoundException ignored) {
            // The standalone bootstrap build intentionally omits shared gameplay.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to register shared commands", exception);
        }
        dispatcher.register(Commands.literal("xero_quest")
                .then(Commands.literal("status")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Xero Quest Journal Forge 1.20.1 platform active"),
                                    false);
                            return 1;
                        })));
    }
}
