package xtdpotato.xero_quest_journal.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

@EventBusSubscriber(
        modid = XeroQuestJournal.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class XeroQuestClientModBus {
    private XeroQuestClientModBus() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(QuestKeyMappings.OPEN);
        event.register(QuestKeyMappings.OPEN_MAILBOX);
        event.register(QuestKeyMappings.OPEN_NEW_MAIL);
        event.register(QuestKeyMappings.TRACK_TASK);
        event.register(QuestKeyMappings.DISMISS_NEW_TASK);
    }

    @SubscribeEvent
    public static void registerShaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event)
            throws java.io.IOException {
        event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        XeroQuestJournal.MOD_ID, "category_icon"),
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR),
                CategoryIconRenderer::shader);
    }

    @SubscribeEvent
    public static void registerConfigScreen(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModList.get().getModContainerById(XeroQuestJournal.MOD_ID).ifPresent(container ->
                container.registerExtensionPoint(IConfigScreenFactory.class,
                        (IConfigScreenFactory) (ignored, parent) -> new QuestConfigScreen(parent))));
    }
}
