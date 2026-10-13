package xtdpotato.xero_quest_journal.productioncheck;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.QuestConfigScreen;
import xtdpotato.xero_quest_journal.client.ClientEditMode;
import xtdpotato.xero_quest_journal.client.StyledButton;

public final class ProductionClientCheck implements ClientModInitializer {
    private int frames;
    private boolean opened;
    private boolean done;
    private Object startupScreen;
    private int stableTicks;
    private int tabIndex;
    private boolean scrolled;
    private boolean languageRequested;
    private static final String[] TABS = {"general", "motion", "notifications", "appearance", "tasks", "tools"};

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(game -> {
            if (done) return;
            Path output = Path.of(System.getProperty("xero.productionCheckDir"));
            try {
                if (game.getOverlay() != null) return;
                if (!languageRequested) {
                    game.options.guiScale().set(2);
                    game.resizeDisplay();
                    game.getLanguageManager().setSelected("zh_cn");
                    game.options.languageCode = "zh_cn";
                    game.reloadResourcePacks();
                    languageRequested = true;
                    return;
                }
                if (!opened) {
                    // A fresh instance can display accessibility onboarding before the title.
                    if (game.screen == null) return;
                    if (startupScreen != game.screen) {
                        startupScreen = game.screen;
                        stableTicks = 0;
                    }
                    if (++stableTicks < 40) return;
                    ClientEditMode.set(false);
                    game.setScreen(new QuestConfigScreen(game.screen));
                    if (!(game.screen instanceof QuestConfigScreen))
                        throw new AssertionError("Settings fixture did not open");
                    if (game.screen.children().isEmpty()) throw new AssertionError("Settings widgets missing");
                    opened = true;
                    return;
                }
                if (++frames < 25) return;
                if (!(game.screen instanceof QuestConfigScreen)) throw new AssertionError("Settings screen disappeared");
                var doneButton = button(game.screen, "done");
                var rail = game.screen.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast)
                        .filter(widget -> widget.getClass().getSimpleName().equals("ScrollRail")).findFirst().orElse(null);
                if (rail != null && rail.getY() + rail.getHeight() >= doneButton.getY())
                    throw new AssertionError("Settings body overlaps footer");
                if (game.screen.getChildAt(doneButton.getX() + 5, doneButton.getY() + 5).orElse(null) != doneButton)
                    throw new AssertionError("Settings body intercepted footer input");
                try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    if (image.getWidth() != 1920 || image.getHeight() != 1080)
                        throw new AssertionError("Production screenshot is not 1920x1080");
                    if (!game.getLanguageManager().getSelected().equals("zh_cn"))
                        throw new AssertionError("Production language is not Chinese");
                    String name = tabIndex == 0 && !scrolled ? "production-settings"
                            : "production-settings-" + TABS[tabIndex] + (scrolled ? "-scrolled" : "");
                    image.writeToFile(output.resolve(name + ".png"));
                }
                if (!scrolled && rail != null) {
                    rail.setFocused(true);
                    if (!rail.keyPressed(GLFW.GLFW_KEY_END, 0, 0)) throw new AssertionError("Settings rail ignored End");
                    scrolled = true;
                    frames = 0;
                    return;
                }
                var field = QuestConfigScreen.class.getDeclaredField("bodyWidgets");
                field.setAccessible(true);
                var body = (java.util.List<?>) field.get(game.screen);
                var topMethod = QuestConfigScreen.class.getDeclaredMethod("bodyTop");
                var bottomMethod = QuestConfigScreen.class.getDeclaredMethod("bodyBottom");
                topMethod.setAccessible(true);
                bottomMethod.setAccessible(true);
                int bodyTop = (int) topMethod.invoke(game.screen), bodyBottom = (int) bottomMethod.invoke(game.screen);
                for (var value : body) if (value instanceof AbstractWidget widget) {
                    if (widget.getY() + widget.getHeight() <= bodyTop || widget.getY() >= bodyBottom) continue;
                    if (widget.getY() + widget.getHeight() >= doneButton.getY())
                        throw new AssertionError("Settings body overlaps its footer");
                    int probeY = Math.max(bodyTop, widget.getY()) + 5;
                    if (widget.active && game.screen.getChildAt(widget.getX() + 5, probeY).orElse(null) != widget)
                        throw new AssertionError("Settings row cannot receive pointer input: "
                                + TABS[tabIndex] + "/" + (widget instanceof StyledButton button ? button.styleId() : widget.getClass().getName()));
                }
                if (++tabIndex < TABS.length) {
                    button(game.screen, "config-tab-" + TABS[tabIndex]).onPress();
                    frames = 0;
                    scrolled = false;
                    return;
                }
                Files.writeString(output.resolve("result.txt"),
                        "PASS: production release JAR client startup, native client mixins, shader registration, "
                                + "embedded dependencies; all six full-screen settings tabs, personal access without author mode, "
                                + "footer pointer isolation and Chinese 1920x1080 screenshots. "
                                + "Full world workflows are covered separately by the development regressions.");
                done = true;
                game.stop();
            } catch (Throwable failure) {
                try { Files.writeString(output.resolve("result.txt"), "FAIL: " + failure); }
                catch (Exception secondary) { failure.addSuppressed(secondary); }
                failure.printStackTrace();
                done = true;
                game.stop();
            }
        });
    }

    private static StyledButton button(net.minecraft.client.gui.screens.Screen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(widget -> widget.styleId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing settings button: " + id));
    }
}
