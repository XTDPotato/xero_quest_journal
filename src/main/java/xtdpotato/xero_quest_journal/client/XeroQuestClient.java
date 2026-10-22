package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = XeroQuestJournal.MOD_ID, value = Dist.CLIENT)
public final class XeroQuestClient {
    private XeroQuestClient() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        QuestHudToastQueue.setHudActive(minecraft.player != null && minecraft.screen == null);
        if (minecraft.screen instanceof AnimatedQuestScreen animated) animated.advanceTransition();
        ClientQuestProgress.tick();
        CoordinateGuidance.tick(minecraft);
        ItemPickupToast.tick();
        xtdpotato.xero_quest_journal.core.network.QuestNetwork.ItemPickupNoticePayload pickupNotice;
        while ((pickupNotice = xtdpotato.xero_quest_journal.core.network.QuestClientInbox.takeItemPickup()) != null) {
            ItemPickupToast.enqueue(pickupNotice);
        }
        xtdpotato.xero_quest_journal.core.network.QuestNetwork.LocatedQuestPayload locatedQuest;
        while ((locatedQuest = xtdpotato.xero_quest_journal.core.network.QuestClientInbox.takeLocatedQuest()) != null) {
            QuestHudRenderer.acceptLocatedQuest(locatedQuest);
        }
        XaeroWaypointBridge.tick();
        ClientControlState.tick();
        QuestPresentationClient.tickAll();
        ClientStructures.tick();
        ClientMailbox.tick();
        AnecdoteClientState.tick();
        MailArrivalToast.tick();
        XeroToast.tick();
        AnecdoteToast.tick();
        var generationOffer = xtdpotato.xero_quest_journal.core.network.QuestClientInbox.generationOffer();
        if (generationOffer != null && minecraft.player != null && minecraft.level != null
                && (minecraft.screen == null || !generationOffer.automatic()
                && minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            xtdpotato.xero_quest_journal.core.network.QuestClientInbox.clearGenerationOffer();
            minecraft.setScreen(new QuestGenerateScreen(null, generationOffer));
        }
        var requestedScreen = xtdpotato.xero_quest_journal.core.network.QuestClientInbox.takeOpenScreen();
        if (requestedScreen != null && minecraft.player != null) {
            switch (requestedScreen.screen()) {
                case "structure_entered" -> minecraft.setScreen(null);
                case "journal" -> minecraft.setScreen(QuestScreen.resume(null));
                case "overview" -> minecraft.setScreen(QuestScreen.overview(null));
                case "task" -> minecraft.setScreen(QuestScreen.task(null, requestedScreen.targetId()));
                case "mailbox" -> {
                    ClientMailbox.request();
                    minecraft.setScreen(new MailboxScreen());
                }
                case "settings" -> minecraft.setScreen(new QuestConfigScreen(null));
                case "templates" -> minecraft.setScreen(new QuestTemplateScreen(new QuestScreen(), ""));
                case "anecdotes" -> {
                    AnecdoteClientState.request();
                    minecraft.setScreen(new AnecdoteScreen(null));
                }
                default -> {
                }
            }
        }
        while (QuestKeyMappings.OPEN.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                if (!QuestUnlockToast.openCurrent())
                    minecraft.setScreen(ClientPreferences.openDefault(null));
            } else if (minecraft.screen instanceof QuestScreen screen) {
                if (screen.isClosing()) screen.reopen();
                else screen.onClose();
            }
        }
        while (QuestKeyMappings.DISMISS_NEW_TASK.consumeClick()) {
            if (minecraft.screen == null) {
                long window = minecraft.getWindow().getWindow();
                boolean altDown = org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                        || org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
                QuestUnlockToast.dismiss(altDown);
            }
        }
        while (QuestKeyMappings.OPEN_MAILBOX.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                ClientMailbox.request();
                minecraft.setScreen(new MailboxScreen());
            } else if (minecraft.screen instanceof MailboxScreen screen) {
                if (screen.isClosing()) screen.reopen();
                else screen.onClose();
            }
        }
        while (QuestKeyMappings.OPEN_NEW_MAIL.consumeClick()) {
            if (minecraft.player != null && !(minecraft.screen instanceof MailboxScreen))
                MailArrivalToast.open();
        }
        while (QuestKeyMappings.TRACK_TASK.consumeClick()) {
            if (minecraft.screen == null) QuestUnlockToast.trackCurrent();
        }
        var automaticClaim = xtdpotato.xero_quest_journal.core.network.QuestClientInbox.takeAutomaticClaim();
        if (automaticClaim != null && minecraft.player != null && !automaticClaim.questId().isBlank()
                && ClientQuestProgress.focusRewardAllows(automaticClaim.questId())) {
            boolean hasChoice = automaticClaim.rewards().stream()
                    .anyMatch(reward -> reward.type().equals("loot_choice"));
            if (hasChoice) {
                boolean alreadyOpen = minecraft.screen instanceof QuestLootChoiceScreen choice
                        && choice.questId().equals(automaticClaim.questId());
                if (!alreadyOpen) {
                    minecraft.setScreen(new QuestLootChoiceScreen(minecraft.screen, automaticClaim.questId(),
                            automaticClaim.title(), automaticClaim.rewards(), false));
                }
            } else {
                var received = QuestRewardPopupScreen.receiptRewards(automaticClaim.rewards());
                if (!received.isEmpty()) {
                    minecraft.setScreen(new QuestRewardPopupScreen(minecraft.screen,
                            automaticClaim.title(), received));
                }
            }
        }
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft game = Minecraft.getInstance();
        if (game.screen == null) {
            HudOverlayLayout.beginFrame(game.getWindow().getGuiScaledWidth(),
                    game.getWindow().getGuiScaledHeight());
        }
        if (!(Minecraft.getInstance().screen instanceof AnimatedQuestScreen animated)
                || !animated.showsTrackingHudBehind())
            QuestHudRenderer.render(event.getGuiGraphics());
        if (Minecraft.getInstance().screen == null) {
            renderNotifications(event.getGuiGraphics());
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void renderMailOverScreen(ScreenEvent.Render.Post event) {
        renderNotifications(event.getGuiGraphics());
    }

    /** Draw once, after the HUD/screen, and above their item and text depth. */
    private static void renderNotifications(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean hud = minecraft.screen == null;
        QuestHudToastQueue.setHudActive(hud);
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            MailArrivalToast.render(graphics);
            XeroToast.render(graphics);
            if (hud) {
                QuestCompletionToast.render(graphics);
                QuestUnlockToast.render(graphics);
                ItemPickupToast.render(graphics);
            }
            AnecdoteToast.render(graphics);
            XeroTips.render(graphics);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
            RenderSystem.disableBlend();
        }
    }

    @SubscribeEvent
    public static void clickMailOnScreen(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0 && MailArrivalToast.click(event.getMouseX(), event.getMouseY()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void releaseMailOnScreen(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() == 0 && MailArrivalToast.release(event.getMouseX(), event.getMouseY()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void clickMailInGame(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != 0 || Minecraft.getInstance().screen != null
                || event.getAction() != 0 && event.getAction() != 1) return;
        Minecraft game = Minecraft.getInstance();
        double x = game.mouseHandler.xpos() * game.getWindow().getGuiScaledWidth()
                / game.getWindow().getScreenWidth();
        double y = game.mouseHandler.ypos() * game.getWindow().getGuiScaledHeight()
                / game.getWindow().getScreenHeight();
        if (event.getAction() == 1 ? MailArrivalToast.click(x, y) : MailArrivalToast.release(x, y))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void openScenePause(ScreenEvent.Opening event) {
        if (event.getCurrentScreen() == null && ClientStructures.inScene()
                && event.getNewScreen() instanceof net.minecraft.client.gui.screens.PauseScreen menu)
            event.setNewScreen(new QuestScenePauseScreen(menu));
    }

    @SubscribeEvent
    public static void addInventoryEntry(ScreenEvent.Init.Post event) {
        var screen = event.getScreen();
        var preferences = ClientPreferences.get();
        if ((! (screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen))) {
            return;
        }
        String recipeViewer = RecipeViewerBridge.availableViewer();
        if (PlatformServices.isModLoaded("roughlyenoughitems")) return;
        ButtonStyleSheet styles = ButtonStyleSheet.load();
        boolean itemButtons = recipeViewer.isBlank();
        int[] taskPosition = preferences.taskInventoryPosition.resolve(screen.width, screen.height);
        int x = taskPosition[0], y = taskPosition[1];
        StyledButton button = new StyledButton(x, y, 20, 20,
                Component.translatable("screen.xero_quest_journal.inventory_tooltip"),
                ignored -> Minecraft.getInstance().setScreen(ClientPreferences.openDefault(screen)),
                styles, "inventory-entry", "toolbar-button");
        if (itemButtons) button.icon(new ItemStack(Items.BOOK));
        else button.textureIcon("xero_quest_journal:textures/gui/navigation/task_shortcut.png");
        button.setTooltip(Tooltip.create(Component.translatable("screen.xero_quest_journal.inventory_tooltip")));
        if (preferences.inventoryButton) event.addListener(button);
        int[] mailPosition = preferences.mailInventoryPosition.resolve(screen.width, screen.height);
        StyledButton mailbox = new StyledButton(mailPosition[0], mailPosition[1], 20, 20,
                Component.translatable("screen.xero_quest_journal.mailbox.inventory_tooltip"),
                ignored -> {
                    ClientMailbox.request();
                    Minecraft.getInstance().setScreen(new MailboxScreen(screen));
                }, styles, "mailbox-inventory-entry", "toolbar-button");
        if (itemButtons) mailbox.icon(new ItemStack(Items.PAPER));
        else mailbox.textureIcon("xero_quest_journal:textures/gui/navigation/mail_shortcut.png");
        mailbox.setTooltip(Tooltip.create(
                Component.translatable("screen.xero_quest_journal.mailbox.inventory_tooltip")));
        if (preferences.mailboxInventoryButton) event.addListener(mailbox);
    }

    private static boolean blocked(ScreenEvent event) {
        return event.getScreen() instanceof AnimatedQuestScreen screen && screen.inputBlocked();
    }

    @SubscribeEvent
    public static void mouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        if (blocked(event)) event.setCanceled(true);
        else if (event.getScreen() instanceof AnimatedQuestScreen screen
                && screen.clickChoicePopup(event.getMouseX(), event.getMouseY(), event.getButton())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void mouseUp(ScreenEvent.MouseButtonReleased.Pre event) {
        if (blocked(event)) event.setCanceled(true);
        else if (event.getScreen() instanceof AnimatedQuestScreen screen
                && screen.releaseChoicePopup(event.getMouseX(), event.getMouseY(), event.getButton())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void mouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (blocked(event)) event.setCanceled(true);
        else if (event.getScreen() instanceof AnimatedQuestScreen screen
                && screen.dragChoicePopup(event.getMouseX(), event.getMouseY(), event.getMouseButton(),
                        event.getDragX(), event.getDragY())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (blocked(event)) event.setCanceled(true);
        else if (event.getScreen() instanceof AnimatedQuestScreen screen
                && screen.scrollChoicePopup(event.getMouseX(), event.getMouseY(),
                        event.getScrollDeltaX(), event.getScrollDeltaY())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void character(ScreenEvent.CharacterTyped.Pre event) {
        if (blocked(event)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void keyUp(ScreenEvent.KeyReleased.Pre event) {
        if (blocked(event)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void keyDown(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AnimatedQuestScreen screen) || !screen.inputBlocked()) return;
        boolean toggle = screen instanceof QuestScreen && QuestKeyMappings.OPEN.matches(event.getKeyCode(), event.getScanCode())
                || screen instanceof MailboxScreen && QuestKeyMappings.OPEN_MAILBOX.matches(event.getKeyCode(), event.getScanCode());
        if (screen.isClosing() && toggle) screen.reopen();
        else if (!screen.isClosing() && (toggle || event.getKeyCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            screen.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
        }
        event.setCanceled(true);
    }
}
