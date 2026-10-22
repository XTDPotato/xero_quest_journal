package xtdpotato.xero_quest_journal.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.fabriccompat.*;

public final class XeroQuestJournalFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        FabricNetwork.registerClientReceivers();
        KeyBindingHelper.registerKeyBinding(QuestKeyMappings.OPEN);
        KeyBindingHelper.registerKeyBinding(QuestKeyMappings.OPEN_MAILBOX);
        KeyBindingHelper.registerKeyBinding(QuestKeyMappings.OPEN_NEW_MAIL);
        KeyBindingHelper.registerKeyBinding(QuestKeyMappings.TRACK_TASK);
        KeyBindingHelper.registerKeyBinding(QuestKeyMappings.DISMISS_NEW_TASK);
        ClientTickEvents.END_CLIENT_TICK.register(game -> {
            XeroQuestClient.clientTick(new FabricEvents.ClientTickEvent.Post());
            FabricSmokeHooks.tick();
        });
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            XeroQuestClient.renderHud(new FabricEvents.RenderGuiEvent.Post(graphics));
            FabricSmokeHooks.frame();
        });
        ScreenEvents.AFTER_INIT.register((game, screen, width, height) -> {
            XeroQuestClient.addInventoryEntry(new FabricScreenEvent.Init.Post(screen,
                    listener -> Screens.getButtons(screen).add((net.minecraft.client.gui.components.AbstractWidget) listener)));
            ScreenEvents.afterRender(screen).register((page, graphics, x, y, delta) -> {
                var event = new FabricScreenEvent.Render.Post(page, graphics);
                XeroQuestClient.renderMailOverScreen(event);
                FabricSmokeHooks.rendered(event);
            });
        });
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "category_icon"),
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR,
                FabricClientAccess::categoryShader));
    }
}
