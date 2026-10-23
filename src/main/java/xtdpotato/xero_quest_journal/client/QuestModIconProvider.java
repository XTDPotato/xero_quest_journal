package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import xtdpotato.xero_quest_journal.core.QuestConfig;

/** Resolves each loader's declared mod logo, with a namespace-owned item icon as fallback. */
final class QuestModIconProvider {
    private static final Map<String, Optional<Path>> LOGOS = new ConcurrentHashMap<>();
    private static final Map<String, String> FALLBACKS = new ConcurrentHashMap<>();
    private static final Map<String, String> DISPLAY_NAMES = new ConcurrentHashMap<>();

    private QuestModIconProvider() {}

    static String displayName(String namespace) {
        if (namespace.equals("minecraft")) return "Minecraft";
        return DISPLAY_NAMES.computeIfAbsent(namespace, key -> {
            Object container = container(key);
            Object metadata = invoke(container, "getModInfo");
            if (metadata == null) metadata = invoke(container, "getMetadata");
            Object name = invoke(metadata, "getDisplayName");
            if (name == null) name = invoke(metadata, "getName");
            return name instanceof String value && !value.isBlank() ? value : key;
        });
    }

    static void render(GuiGraphics graphics, String namespace, int x, int y, int size) {
        if (namespace.equals("minecraft")) {
            QuestIcons.render(graphics, "minecraft:grass_block", x, y, size);
            return;
        }
        Path logo = LOGOS.computeIfAbsent(namespace, QuestModIconProvider::resolveLogo).orElse(null);
        if (logo != null && Files.isRegularFile(logo)) {
            ClientMediaManager.renderImage(graphics, logo, "mod-logo:" + namespace + ":" + logo,
                    x, y, size, size, 1.0F);
            return;
        }
        if (logo != null) LOGOS.remove(namespace, Optional.of(logo));
        String fallback = FALLBACKS.computeIfAbsent(namespace, QuestModIconProvider::representativeItem);
        if (!fallback.isBlank()) QuestIcons.render(graphics, fallback, x, y, size);
        else {
            graphics.fill(x, y, x + size, y + size, 0xFF3D4855);
            String letters = namespace.length() > 1
                    ? namespace.substring(0, 2).toUpperCase(java.util.Locale.ROOT)
                    : namespace.toUpperCase(java.util.Locale.ROOT);
            graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                    letters, x + size / 2, y + (size - 8) / 2, 0xFFE7D4AA);
        }
    }

    private static Optional<Path> resolveLogo(String namespace) {
        Object container = container(namespace);
        if (container == null) return Optional.empty();
        Object metadata = invoke(container, "getModInfo");
        if (metadata == null) metadata = invoke(container, "getMetadata");
        Object logo = invoke(metadata, "getLogoFile");
        if (logo == null) logo = invoke(metadata, "getIconPath", 128);
        Object unwrapped = unwrap(logo);
        if (unwrapped instanceof URL url) return cacheLogo(namespace, url);
        String relative = unwrapString(logo);
        if (relative == null || relative.isBlank()) return Optional.empty();
        Object ownPath = unwrap(invoke(container, "findPath", relative));
        if (ownPath instanceof Path path && usableImage(path)) return Optional.of(path);
        Object owningFile = invoke(metadata, "getOwningFile");
        Object modFile = invoke(owningFile, "getFile");
        Object resource = invoke(modFile, "findResource", (Object) new String[]{relative});
        Object path = unwrap(resource);
        return path instanceof Path image && usableImage(image) ? Optional.of(image) : Optional.empty();
    }

    private static Optional<Path> cacheLogo(String namespace, URL url) {
        if (!url.getProtocol().equals("jar") && !url.getProtocol().equals("file")
                && !url.getProtocol().equals("union")) return Optional.empty();
        String path = url.getPath().toLowerCase(java.util.Locale.ROOT);
        String extension = path.endsWith(".jpg") || path.endsWith(".jpeg") ? ".jpg" : ".png";
        Path root = QuestConfig.root().resolve("cache/mod_icons").toAbsolutePath().normalize();
        Path target = root.resolve(namespace + extension).normalize();
        if (!target.startsWith(root)) return Optional.empty();
        if (!Files.isRegularFile(target)) {
            CompletableFuture.runAsync(() -> {
                try {
                    Files.createDirectories(root);
                    try (var input = url.openStream()) {
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception ignored) {
                    LOGOS.remove(namespace);
                }
            });
        }
        return Optional.of(target);
    }

    private static boolean usableImage(Path path) {
        if (path == null || !Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))
                && !name.endsWith(".svg");
    }

    private static String representativeItem(String namespace) {
        return BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals(namespace))
                .filter(id -> !id.getPath().equals("air"))
                .sorted()
                .map(ResourceLocation::toString)
                .findFirst().orElse("");
    }

    private static Object container(String namespace) {
        try {
            return ModList.get().getModContainerById(namespace).orElse(null);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static Object invoke(Object target, String method, Object... arguments) {
        if (target == null) return null;
        for (Method candidate : target.getClass().getMethods()) {
            if (!candidate.getName().equals(method) || candidate.getParameterCount() != arguments.length) continue;
            try {
                return candidate.invoke(target, arguments);
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                // Try another overload, then report an unavailable optional loader API.
            }
        }
        return null;
    }

    private static Object unwrap(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static String unwrapString(Object value) {
        Object unwrapped = unwrap(value);
        return unwrapped instanceof String string ? string : null;
    }
}
