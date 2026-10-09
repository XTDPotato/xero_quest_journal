package xtdpotato.xero_quest_journal.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.QuestTypeStore;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.QuestType;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.runtime.QuestConditions;
import xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue;

import java.util.List;
import java.util.Set;

final class CategoryRegression {
    static void serverChecks(ServerPlayer player, String prefix) {
        String id = prefix + "_category";
        require(QuestTypeStore.save(player, new QuestType(id, "Category regression", ""), false).isEmpty(),
                "Cannot create quest type");
        var request = new QuestAuthoring.Request(QuestAuthoring.Kind.GROUP, id, "Typed group", "",
                "", "", id, "", 1);
        require(QuestAuthoring.create(player, request).successful(), "Cannot create typed group");
        require(QuestCatalog.decode(QuestCatalog.serverJson()).groups().stream()
                .anyMatch(g -> g.id().equals(id) && g.type().equals(id)), "Group type missing in catalog");
        require(QuestTypeStore.delete(player, id).equals("in_use"), "Deleted referenced quest type");
        require(QuestTypeStore.delete(null, id).equals("permission"), "Unauthorized type deletion");
        require(QuestBatchAuthoring.delete(player, Set.of(), Set.of(id)).successful(), "Typed group cleanup");
        require(QuestTypeStore.delete(player, id).isEmpty(), "Unused type deletion failed");
        try {
            QuestTypeStore.ensureDefaults();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
        require(QuestTypeStore.all().stream().noneMatch(t -> t.id().equals(id)), "Deleted type restored");
    }

    static void check(Minecraft game) throws Exception {
        var customStack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);
        customStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Component regression"));
        String encoded = QuestItemStackValue.encode(customStack, game.player.registryAccess());
        var decoded = QuestItemStackValue.decode(encoded, game.player.registryAccess()).orElseThrow();
        require(QuestItemStackValue.matches(customStack, encoded, game.player.registryAccess())
                        && !QuestItemStackValue.matches(
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND),
                        encoded, game.player.registryAccess())
                        && net.minecraft.world.item.ItemStack.isSameItemSameComponents(customStack, decoded),
                "Component-specific item identity failed");
        require(QuestConditions.valid(List.of(new QuestDefinition.Trigger("location",
                        "coords|minecraft:overworld|1|64|2|8", 1)))
                        && QuestConditions.valid(List.of(new QuestDefinition.Trigger("location",
                        "structure|minecraft:overworld|minecraft:village_plains|256", 1)))
                        && !QuestConditions.valid(List.of(new QuestDefinition.Trigger("location",
                        "coords|minecraft:overworld|NaN|64|2|8", 1))),
                "Location target validation failed");
        var screen = (QuestScreen) game.screen;
        int originalWidth = screen.width, originalHeight = screen.height;
        for (int width : new int[]{320, 400, 640, 1280}) {
            screen.init(game, width, Math.max(240, width * 5 / 8));
            int left = metric(screen, "categoryViewportLeft"), right = metric(screen, "categoryViewportRight");
            StyledButton q = find(screen, "category-previous"), e = find(screen, "category-next");
            require(q.getWidth() == (width < 400 ? 9 : 10)
                            && e.getHeight() == (metric(screen, "categoryHeight") - 2) / 2,
                    "Q/E keycaps are not half-sized");
            require(q.getRight() < left && e.getX() > right, "Keycaps overlap category viewport");
            require(e.getRight() < find(screen, "settings").getX() || e.getY() >= find(screen, "settings").getBottom(),
                    "Keycap overlaps settings");
            screen.mouseScrolled((left + right) / 2.0, metric(screen, "categoryY") + 5, 0, -100);
            Object motion = field(screen, "categoryMotion");
            var advance = motion.getClass().getDeclaredMethod("advance", double.class);
            advance.setAccessible(true);
            for (int i = 0; i < 20; i++) advance.invoke(motion, .1);
            var animate = screen.getClass().getDeclaredMethod("animateCategories");
            animate.setAccessible(true);
            animate.invoke(screen);
            StyledButton add = find(screen, "category-add");
            require(add.getRight() <= right && add.getX() >= left, "Cannot scroll to add tab");
            require(q.getRight() < left && e.getX() > right, "Scrolling moved keycaps");
            screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
            require(field(screen, "category").equals("main"), "E did not select main category");
            var model = (JournalListModel) field(screen, "journalList");
            require(model.rows().stream().filter(r -> r.group() != null)
                    .allMatch(r -> r.group().type().equals("main")), "Other group type leaked");
            StyledButton main = find(screen, "category-main");
            require(main.isMouseOver(main.getX() + main.getWidth() / 2.0, main.getY() + 5),
                    "Selected category did not scroll into view");
            screen.mouseClicked(main.getX() + main.getWidth() / 2.0, main.getY() + 5, 1);
            @SuppressWarnings("unchecked")
            var menu = (List<StyledButton>) field(screen, "menu");
            require(menu.stream().anyMatch(b -> b.styleId().equals("context-properties"))
                    && menu.stream().anyMatch(b -> b.styleId().equals("context-delete_quest_type")),
                    "Category context actions missing");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            EditBox search = (EditBox) field(screen, "taskSearch");
            screen.setFocused(search);
            screen.keyPressed(GLFW.GLFW_KEY_Q, 0, 0);
            require(field(screen, "category").equals("main"), "Search input switched category");
            screen.setFocused(null);
            screen.keyPressed(GLFW.GLFW_KEY_Q, 0, 0);
            require(field(screen, "category").equals("active"), "Q did not return to active");
        }
        screen.init(game, originalWidth, originalHeight);
    }

    private static StyledButton find(QuestScreen screen, String id) {
        return screen.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(b -> b.styleId().equals(id)).findFirst().orElseThrow();
    }

    static void checkCentered(Minecraft game, NativeImage image) {
        var tab = find((QuestScreen) game.screen, "category-active");
        double scaleX = image.getWidth() / (double) game.screen.width;
        double scaleY = image.getHeight() / (double) game.screen.height;
        int[] circle = {Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1};
        int[] icon = circle.clone();
        for (int y = (int) (tab.getY() * scaleY); y < (int) ((tab.getBottom() - 4) * scaleY); y++) {
            for (int x = (int) (tab.getX() * scaleX); x < (int) (tab.getRight() * scaleX); x++) {
                int pixel = image.getPixelRGBA(x, y);
                int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
                if (Math.abs(r - g) > 2 || Math.abs(r - b) > 2) continue;
                int[] bounds = r >= 245 ? circle : r >= 110 && r <= 145 ? icon : null;
                if (bounds == null) continue;
                bounds[0] = Math.min(bounds[0], x);
                bounds[1] = Math.min(bounds[1], y);
                bounds[2] = Math.max(bounds[2], x);
                bounds[3] = Math.max(bounds[3], y);
            }
        }
        require(circle[2] >= 0 && icon[2] >= 0, "Selected category circle/icon did not render");
        require(Math.abs((circle[2] - circle[0] + 1) / scaleX
                        - (circle[3] - circle[1] + 1) / scaleY) <= 1,
                "Selected category background is elliptical");
        require(Math.abs(circle[0] + circle[2] - icon[0] - icon[2]) <= 2 * scaleX
                        && Math.abs(circle[1] + circle[3] - icon[1] - icon[3]) <= 2 * scaleY,
                "Category icon and circle are not concentric");
    }
    private static int metric(Object screen, String name) throws Exception {
        var method = screen.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (int) method.invoke(screen);
    }
    private static Object field(Object screen, String name) throws Exception {
        var field = screen.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
