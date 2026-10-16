package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.HotEventCanvas;

import java.util.List;
import java.util.Map;

/** Uses the same draft editor as hot events; persists only local gallery preferences. */
final class LegendaryBackgroundScreen extends HotEventBackgroundScreen {
    LegendaryBackgroundScreen(Screen parent) {
        super(parent, value(), LegendaryBackgroundScreen::save,
                Component.translatable("screen.xero_quest_journal.legendary.background"));
    }
    private static HotEventCanvas value() {
        var config = ClientPreferences.get();
        return new HotEventCanvas(config.legendaryBackground, "", List.of(), true, Map.of(),
                config.legendaryBackgroundType, config.legendaryBackgroundScale,
                config.legendaryBackgroundOffsetX, config.legendaryBackgroundOffsetY,
                config.legendaryBackgroundMode, config.legendaryBackgroundMusic);
    }
    private static void save(HotEventCanvas value) {
        var config = ClientPreferences.get();
        config.legendaryBackground = value.backgroundImage();
        config.legendaryBackgroundType = value.backgroundType();
        config.legendaryBackgroundMode = value.backgroundMode();
        config.legendaryBackgroundScale = value.backgroundScale();
        config.legendaryBackgroundOffsetX = value.backgroundOffsetX();
        config.legendaryBackgroundOffsetY = value.backgroundOffsetY();
        config.legendaryBackgroundMusic = value.backgroundMusic();
        if (!config.save()) throw new IllegalArgumentException("Could not save background preference");
    }
}
