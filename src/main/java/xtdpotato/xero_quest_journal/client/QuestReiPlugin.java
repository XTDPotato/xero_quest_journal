package xtdpotato.xero_quest_journal.client;

import com.mojang.serialization.DataResult;
import me.shedaniel.rei.api.client.favorites.FavoriteEntry;
import me.shedaniel.rei.api.client.favorites.FavoriteEntryType;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import net.minecraft.client.Minecraft;
import me.shedaniel.math.Rectangle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Loaded by REI only; favorites remain removable and restorable through REI's own UI. */
@me.shedaniel.rei.forge.REIPluginClient
public final class QuestReiPlugin implements REIClientPlugin {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "inventory_shortcut");
    @Override public void registerFavorites(FavoriteEntryType.Registry registry) {
        registry.register(ID, new FavoriteEntryType<Shortcut>() {
            @Override public DataResult<Shortcut> read(CompoundTag tag) { return create(tag.getString("entry")); }
            @Override public DataResult<Shortcut> fromArgs(Object... args) {
                return args.length == 1 && args[0] instanceof String value ? create(value)
                        : DataResult.error(() -> "Expected task or mail shortcut");
            }
            @Override public CompoundTag save(Shortcut entry, CompoundTag tag) {
                tag.putString("entry", entry.kind);
                return tag;
            }
            private DataResult<Shortcut> create(String kind) {
                return java.util.List.of("task", "mail").contains(kind) ? DataResult.success(new Shortcut(kind))
                        : DataResult.error(() -> "Unknown Xero inventory shortcut");
            }
        });
        registry.getOrCrateSection(Component.translatable("screen.xero_quest_journal.rei.section"))
                .add(true, new Shortcut("task"), new Shortcut("mail"));
    }
    public static final class Shortcut extends FavoriteEntry {
        private final String kind;
        public Shortcut(String kind) { this.kind = kind; }
        private Component title() {
            return Component.translatable(kind.equals("task") ? "screen.xero_quest_journal.inventory_tooltip"
                    : "screen.xero_quest_journal.mailbox.inventory_tooltip");
        }
        @Override public boolean isInvalid() { return !java.util.List.of("task", "mail").contains(kind); }
        @Override public Renderer getRenderer(boolean showcase) {
            String icon = "xero_quest_journal:textures/gui/navigation/"
                    + (kind.equals("task") ? "task_shortcut.png" : "mail_shortcut.png");
            return new Renderer() {
                @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, Rectangle bounds,
                                             int mouseX, int mouseY, float delta) {
                    int size = Math.min(16, Math.max(1, Math.min(bounds.width, bounds.height)));
                    QuestIcons.render(graphics, icon,
                            bounds.getCenterX() - size / 2, bounds.getCenterY() - size / 2, size);
                }
                @Override public Tooltip getTooltip(me.shedaniel.rei.api.client.gui.widgets.TooltipContext context) {
                    return Tooltip.create(context.getPoint(), title());
                }
            };
        }
        @Override public boolean doAction(int button) {
            var game = Minecraft.getInstance();
            if (button != 0 || game.player == null) return false;
            net.minecraft.client.gui.screens.Screen parent =
                    game.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                            || game.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
                            ? game.screen : null;
            if (kind.equals("task")) game.setScreen(ClientPreferences.openDefault(parent));
            else {
                ClientMailbox.request();
                game.setScreen(new MailboxScreen(parent));
            }
            return true;
        }
        @Override public long hashIgnoreAmount() { return kind.hashCode(); }
        @Override public FavoriteEntry copy() { return new Shortcut(kind); }
        @Override public ResourceLocation getType() { return ID; }
        @Override public boolean isSame(FavoriteEntry other) { return other instanceof Shortcut shortcut && kind.equals(shortcut.kind); }
    }
}
