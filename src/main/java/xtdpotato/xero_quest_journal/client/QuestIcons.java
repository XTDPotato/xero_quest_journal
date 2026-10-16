package xtdpotato.xero_quest_journal.client;

import com.ibm.icu.text.Transliterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestConfig;
import xtdpotato.xero_quest_journal.core.runtime.QuestItemStackValue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestIcons {
    public static final String LOCAL_NAMESPACE = "xero_quest_local";
    private static final long MAX_LOCAL_IMAGE_BYTES = 16L * 1024L * 1024L;
    private static final Set<String> LOCAL_IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".bmp");
    private static final Map<ResourceLocation, ItemStack> ITEM_STACKS = new ConcurrentHashMap<>();
    private static final Transliterator PINYIN = Transliterator.getInstance(
            "Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC");
    private static Object itemLanguage;
    private static Object itemConnection;
    private static List<IconChoice> itemChoices = List.of();
    private static Object textureLanguage;
    private static List<IconChoice> textureChoices = List.of();
    private static List<IconChoice> localChoices = List.of();
    private static boolean localChoicesLoaded;

    private QuestIcons() {
    }

    public record IconChoice(String value, String displayName, String searchText) {
        public boolean matches(String query) {
            String normalized = normalize(query);
            return matchesNormalized(normalized);
        }

        boolean matchesNormalized(String normalized) {
            if (normalized.isEmpty()) {
                return true;
            }
            for (String token : normalized.split("\\s+")) {
                if (!searchText.contains(token)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static boolean valid(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (QuestItemStackValue.encoded(value)) {
            var player = Minecraft.getInstance().player;
            return player != null && QuestItemStackValue.decode(value, player.registryAccess()).isPresent();
        }
        if (ClientMediaManager.isNetworkSource(value)) {
            return ClientMediaManager.validImageSource(value);
        }
        var id = ResourceLocation.tryParse(value);
        if (id != null && id.getNamespace().equals(LOCAL_NAMESPACE)) {
            Path path = localPath(id);
            try {
                return path != null && Files.isRegularFile(path)
                        && Files.size(path) <= MAX_LOCAL_IMAGE_BYTES && isLocalImage(path);
            } catch (Exception ignored) {
                return false;
            }
        }
        if (id == null && ClientMediaManager.validImageSource(value)) {
            return true;
        }
        return id != null && (BuiltInRegistries.ITEM.containsKey(id)
                || Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
    }

    public static void render(GuiGraphics graphics, String value, int x, int y, int size) {
        render(graphics, value, x, y, size, size, 1.0F);
    }

    /** Item stack named by an icon value (registry ID or encoded stack); empty for textures and tags. */
    public static ItemStack stack(String value) {
        if (value == null || value.isBlank() || value.startsWith("#")) return ItemStack.EMPTY;
        if (QuestItemStackValue.encoded(value)) {
            var player = Minecraft.getInstance().player;
            return player == null ? ItemStack.EMPTY
                    : QuestItemStackValue.decode(value, player.registryAccess()).orElse(ItemStack.EMPTY);
        }
        var id = ResourceLocation.tryParse(value);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        return ITEM_STACKS.computeIfAbsent(id, key -> new ItemStack(BuiltInRegistries.ITEM.get(key)));
    }

    public static void render(GuiGraphics graphics, String value, int x, int y,
                              int width, int height, float alpha) {
        if (value == null || value.isBlank()) {
            return;
        }
        int drawWidth = Math.max(1, width);
        int drawHeight = Math.max(1, height);
        float drawAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        if (value.startsWith("#")) {
            var tag = ResourceLocation.tryParse(value.substring(1));
            if (tag != null) BuiltInRegistries.ITEM.getTag(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, tag)).filter(set -> set.size() > 0)
                    .ifPresent(set -> render(graphics, BuiltInRegistries.ITEM.getKey(set.get(0).value()).toString(),
                            x, y, width, height, alpha));
            return;
        }
        if (QuestItemStackValue.encoded(value)) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            QuestItemStackValue.decode(value, player.registryAccess()).ifPresent(stack -> {
                graphics.setColor(1.0F, 1.0F, 1.0F, drawAlpha);
                graphics.pose().pushPose();
                float scale = Math.min(drawWidth, drawHeight) / 16f;
                graphics.pose().translate(x + (drawWidth - 16 * scale) / 2.0F,
                        y + (drawHeight - 16 * scale) / 2.0F, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            });
            return;
        }
        if (ClientMediaManager.isNetworkSource(value)) {
            ClientMediaManager.renderImage(graphics, value, x, y, drawWidth, drawHeight, drawAlpha);
            return;
        }
        var id = ResourceLocation.tryParse(value);
        if (id != null && id.getNamespace().equals(LOCAL_NAMESPACE)) {
            Path path = localPath(id);
            if (path != null) {
                String key = id.toString();
                try {
                    key += ":" + Files.getLastModifiedTime(path).toMillis() + ":" + Files.size(path);
                } catch (Exception ignored) {
                }
                ClientMediaManager.renderImage(graphics, path, key,
                        x, y, drawWidth, drawHeight, drawAlpha);
            }
            return;
        }
        if (id == null && ClientMediaManager.validImageSource(value)) {
            ClientMediaManager.renderImage(graphics, value, x, y, drawWidth, drawHeight, drawAlpha);
            return;
        }
        if (id != null && id.getPath().endsWith(".png")
                && Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) {
            graphics.setColor(1.0F, 1.0F, 1.0F, drawAlpha);
            graphics.blit(id, x, y, drawWidth, drawHeight, 0, 0, 16, 16, 16, 16);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }
        int size = Math.min(drawWidth, drawHeight);
        ItemStack stack = ITEM_STACKS.computeIfAbsent(id,
                key -> new ItemStack(BuiltInRegistries.ITEM.get(key)));
        graphics.setColor(1.0F, 1.0F, 1.0F, drawAlpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x + (drawWidth - size) / 2.0F, y + (drawHeight - size) / 2.0F, 0);
        graphics.pose().scale(size / 16f, size / 16f, 1);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static synchronized List<IconChoice> itemChoices() {
        Object language = Language.getInstance();
        var game = Minecraft.getInstance();
        if (itemLanguage == language && itemConnection == game.getConnection() && !itemChoices.isEmpty()) {
            return itemChoices;
        }
        List<IconChoice> choices = new ArrayList<>();
        BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> BuiltInRegistries.ITEM.get(id) != net.minecraft.world.item.Items.AIR)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(id -> choices.add(itemChoice(id)));
        if (game.player != null && game.level != null) {
            CreativeModeTabs.tryRebuildTabContents(game.level.enabledFeatures(), game.player.hasPermissions(2),
                    game.player.registryAccess());
            var variants = ItemStackLinkedSet.createTypeAndComponentsSet();
            for (var tab : CreativeModeTabs.allTabs()) variants.addAll(tab.getDisplayItems());
            for (var stack : variants) {
                if (stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()))) continue;
                String value = QuestItemStackValue.encode(stack, game.player.registryAccess());
                if (!value.isBlank()) choices.add(choice(value));
            }
        }
        itemLanguage = language;
        itemConnection = game.getConnection();
        itemChoices = List.copyOf(choices);
        return itemChoices;
    }

    public static synchronized List<IconChoice> textureChoices() {
        var resources = Minecraft.getInstance().getResourceManager();
        Object language = Language.getInstance();
        if (textureLanguage == language && !textureChoices.isEmpty()) {
            return textureChoices;
        }
        List<IconChoice> choices = resources.listResources("textures",
                        id -> id.getPath().toLowerCase(Locale.ROOT).endsWith(".png"))
                .keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .map(QuestIcons::plainChoice)
                .toList();
        textureLanguage = language;
        textureChoices = List.copyOf(choices);
        return textureChoices;
    }

    public static synchronized List<IconChoice> localChoices() {
        if (localChoicesLoaded) return localChoices;
        localChoices = localIcons().stream().map(QuestIcons::choice).toList();
        localChoicesLoaded = true;
        return localChoices;
    }

    /** Clears icon indexes so the picker can discover changed language packs and local files. */
    public static synchronized void refreshChoices() {
        ITEM_STACKS.clear();
        itemLanguage = null;
        itemChoices = List.of();
        textureLanguage = null;
        textureChoices = List.of();
        localChoices = List.of();
        localChoicesLoaded = false;
    }

    public static IconChoice choice(String value) {
        if (QuestItemStackValue.encoded(value)) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var stack = QuestItemStackValue.decode(value, player.registryAccess()).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    String display = displayName(value) + " (" + id + ")";
                    String data = stack.save(player.registryAccess()).toString();
                    return new IconChoice(value, display, searchText(id + " " + data, display, true));
                }
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)
                && BuiltInRegistries.ITEM.get(id) != net.minecraft.world.item.Items.AIR) {
            return itemChoice(id);
        }
        String display = displayName(value);
        return new IconChoice(value, display, searchText(value, display, false));
    }

    public static List<IconChoice> tagChoices() {
        return BuiltInRegistries.ITEM.getTagNames().map(tag -> "#" + tag.location()).sorted()
                .map(QuestIcons::choice).toList();
    }

    public static Component tooltip(String value) {
        var game = Minecraft.getInstance();
        if (game.player != null) {
            var stack = QuestItemStackValue.encoded(value)
                    ? QuestItemStackValue.decode(value, game.player.registryAccess()).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                var lines = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(game, stack);
                var result = Component.empty();
                for (var line : lines) {
                    if (!result.getSiblings().isEmpty()) result.append("\n");
                    result.append(line.copy());
                }
                return result;
            }
        }
        return Component.literal(displayName(value));
    }

    public static Component metadataTooltip(String value) {
        var result = tooltip(value).copy();
        result.append(Component.literal("\n" + Component.translatable(
                "screen.xero_quest_journal.icon_picker.metadata.id", value).getString())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        Path path = filePath(value);
        if (path != null) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                result.append(Component.literal("\n" + Component.translatable(
                        "screen.xero_quest_journal.icon_picker.metadata.path", path.toAbsolutePath()).getString())
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                result.append(Component.literal("\n" + Component.translatable(
                        "screen.xero_quest_journal.icon_picker.metadata.created",
                        Instant.ofEpochMilli(attributes.creationTime().toMillis())).getString())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                result.append(Component.literal("\n" + Component.translatable(
                        "screen.xero_quest_journal.icon_picker.metadata.modified",
                        Instant.ofEpochMilli(attributes.lastModifiedTime().toMillis())).getString())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                result.append(Component.literal("\n" + Component.translatable(
                        "screen.xero_quest_journal.icon_picker.metadata.size",
                        attributes.size() / 1024.0).getString()).withStyle(net.minecraft.ChatFormatting.GRAY));
                return result;
            } catch (Exception ignored) {
                // Keep resource metadata available even if filesystem attributes are inaccessible.
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && !id.getNamespace().equals(LOCAL_NAMESPACE)) {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id).orElse(null);
            if (resource != null) {
                result.append(Component.literal("\n" + Component.translatable(
                        "screen.xero_quest_journal.icon_picker.metadata.pack", resource.sourcePackId()).getString())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                return result;
            }
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                result.append(Component.literal("\n" + Component.translatable(
                        "screen.xero_quest_journal.icon_picker.metadata.registry",
                        BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.get(id)).toString()).getString())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                return result;
            }
        }
        result.append(Component.literal("\n" + Component.translatable(
                "screen.xero_quest_journal.icon_picker.metadata.time_unavailable").getString())
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return result;
    }

    public static Path filePath(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && id.getNamespace().equals(LOCAL_NAMESPACE)) return localPath(id);
        return null;
    }

    public static long fileTimestamp(String value, boolean creation) {
        Path path = filePath(value);
        if (path == null) return -1;
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return (creation ? attributes.creationTime() : attributes.lastModifiedTime()).toMillis();
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static boolean matchesSearch(String query, String... values) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) return true;
        String searchable = java.util.Arrays.stream(values)
                .filter(java.util.Objects::nonNull)
                .map(value -> searchText(value, value, true))
                .filter(value -> !value.isBlank())
                .reduce("", (left, right) -> left + "\n" + right);
        for (String token : normalizedQuery.split("\\s+")) {
            if (!searchable.contains(token)) return false;
        }
        return true;
    }

    public static List<String> localIcons() {
        Path root = localRoot();
        List<String> values = new ArrayList<>();
        try {
            Files.createDirectories(root);
            try (var paths = Files.walk(root)) {
                paths.filter(path -> Files.isRegularFile(path) && isLocalImage(path))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(path -> values.add(localId(path)));
            }
        } catch (Exception exception) {
            XeroQuestJournal.LOGGER.warn("Unable to scan local quest icons", exception);
        }
        return List.copyOf(values);
    }

    public static Path localRoot() {
        return QuestConfig.root().resolve("assets").toAbsolutePath().normalize();
    }

    public static String displayName(String value) {
        if (QuestItemStackValue.encoded(value)) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var stack = QuestItemStackValue.decode(value, player.registryAccess()).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    String identity = QuestItemStackValue.modelIdentity(value, player.registryAccess());
                    if (!identity.isBlank()) {
                        String modelName = xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat
                                .displayName(identity);
                        if (!modelName.isBlank()) return modelName;
                    }
                    String hoverName = stack.getHoverName().getString();
                    if (hoverName.matches("(item|block|entity)\\.[a-zA-Z0-9_.-]+"))
                        hoverName = xtdpotato.xero_quest_journal.core.compat.TacticalItemCompat
                                .readableName(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    return hoverName + " (" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + ")";
                }
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)
                && BuiltInRegistries.ITEM.get(id) != net.minecraft.world.item.Items.AIR) {
            return itemDisplayName(id);
        }
        Path path = id == null ? null : localPath(id);
        return path == null ? value : localRoot().relativize(path).toString().replace('\\', '/');
    }

    private static IconChoice itemChoice(ResourceLocation id) {
        String value = id.toString();
        String display = itemDisplayName(id);
        return new IconChoice(value, display, searchText(value, display, true));
    }

    private static IconChoice plainChoice(String value) {
        return new IconChoice(value, value, normalize(value));
    }

    private static String itemDisplayName(ResourceLocation id) {
        String value = id.toString();
        String localized = new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
        return localized.isBlank() || localized.equals(value) ? value : localized + " (" + value + ")";
    }

    private static String searchText(String value, String display, boolean pinyin) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.add(normalize(value));
        terms.add(normalize(display));
        if (pinyin) {
            String transliterated = normalize(PINYIN.transliterate(display));
            terms.add(transliterated);
            String[] syllables = transliterated.split("[^a-z0-9]+");
            StringBuilder compact = new StringBuilder();
            StringBuilder initials = new StringBuilder();
            for (String syllable : syllables) {
                if (syllable.isEmpty()) continue;
                compact.append(syllable);
                initials.append(syllable.charAt(0));
            }
            terms.add(compact.toString());
            terms.add(initials.toString());
        }
        return String.join("\n", terms);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    public static String searchIndex(String value, String display) {
        return searchText(value, display, true);
    }

    static String localId(Path path) {
        Path relative = localRoot().relativize(path.toAbsolutePath().normalize());
        String encoded = HexFormat.of().formatHex(relative.toString().replace('\\', '/')
                .getBytes(StandardCharsets.UTF_8));
        return ResourceLocation.fromNamespaceAndPath(LOCAL_NAMESPACE, encoded).toString();
    }

    static Path localPath(ResourceLocation id) {
        if (!id.getNamespace().equals(LOCAL_NAMESPACE)) {
            return null;
        }
        try {
            String relative = new String(HexFormat.of().parseHex(id.getPath()), StandardCharsets.UTF_8);
            Path resolved = localRoot().resolve(relative).toAbsolutePath().normalize();
            return resolved.startsWith(localRoot()) && !Files.isSymbolicLink(resolved) ? resolved : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isLocalImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return LOCAL_IMAGE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
