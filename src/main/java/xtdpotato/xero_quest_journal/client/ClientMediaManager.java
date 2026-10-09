package xtdpotato.xero_quest_journal.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import xtdpotato.xero_quest_journal.client.media.MediaDecoder;
import xtdpotato.xero_quest_journal.client.media.MediaDecoders;
import xtdpotato.xero_quest_journal.XeroQuestJournal;
import xtdpotato.xero_quest_journal.core.QuestConfig;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client-only media loader. Decoding and network I/O stay off the render thread;
 * decoded frames are uploaded to Minecraft textures on demand.
 */
public final class ClientMediaManager {
    private static final long MAX_IMAGE_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_LOCAL_MEDIA_BYTES = 1024L * 1024L * 1024L;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp");

    private static final ExecutorService WORKERS = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("xero-media-", 0).factory());
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .executor(WORKERS)
            .build();
    private static final Map<String, ImageHandle> IMAGES = new ConcurrentHashMap<>();
    private static final Map<String, int[]> RESOURCE_SIZES = new ConcurrentHashMap<>();
    private static final Map<String, PlaybackHandle> PLAYBACKS = new ConcurrentHashMap<>();

    private ClientMediaManager() {
    }

    public static boolean isNetworkSource(String source) {
        if (source == null) return false;
        try {
            String scheme = URI.create(source.strip()).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean validImageSource(String source) {
        if (source == null || source.isBlank()) return true;
        if (isNetworkSource(source)) return validNetworkUri(source);
        Path path = localMediaPath(source);
        return validLocalFile(path, IMAGE_EXTENSIONS, MAX_IMAGE_BYTES);
    }

    public static boolean validVideoSource(String source) {
        return MediaDecoders.addonAvailable() && validMediaSource(source, MediaDecoders.VIDEO);
    }

    public static boolean validAudioSource(String source) {
        return validMediaSource(source, MediaDecoders.extensions(false));
    }

    private static boolean validMediaSource(String source, Set<String> extensions) {
        if (source == null || source.isBlank()) return false;
        if (isNetworkSource(source)) {
            if (!validNetworkUri(source)) return false;
            String suffix = extension(URI.create(source.strip()).getPath());
            // Extensionless endpoints are sniffed after a bounded, validated download.
            return !(MediaDecoders.EXTENDED_AUDIO.contains(suffix) || MediaDecoders.VIDEO.contains(suffix))
                    || extensions.contains(suffix);
        }
        return validLocalFile(localMediaPath(source), extensions, MAX_LOCAL_MEDIA_BYTES);
    }

    public static void renderImage(GuiGraphics graphics, Path path, String key,
                                   int x, int y, int width, int height, float alpha) {
        if (path == null) return;
        renderImageHandle(graphics, path.toAbsolutePath().normalize(), key,
                x, y, width, height, alpha);
    }

    public static void renderImage(GuiGraphics graphics, String source,
                                   int x, int y, int width, int height, float alpha) {
        if (source == null || source.isBlank()) return;
        Object input = isNetworkSource(source) ? source.strip() : localMediaPath(source);
        if (input == null) return;
        renderImageHandle(graphics, input, source.strip(), x, y, width, height, alpha);
    }

    public static void renderImageCover(GuiGraphics graphics, String source,
                                        int x, int y, int width, int height, float alpha) {
        if (source == null || source.isBlank()) return;
        ResourceLocation id = ResourceLocation.tryParse(source);
        Path local = id == null ? null : QuestIcons.localPath(id);
        if (local != null) {
            renderImageCoverHandle(graphics, local, source, x, y, width, height, alpha);
        } else if (isNetworkSource(source) || validLocalFile(localMediaPath(source),
                IMAGE_EXTENSIONS, MAX_IMAGE_BYTES)) {
            renderImageCoverHandle(graphics, isNetworkSource(source) ? source.strip() : localMediaPath(source),
                    source, x, y, width, height, alpha);
        } else if (id != null && QuestIcons.valid(source)) {
            int[] size = RESOURCE_SIZES.computeIfAbsent(source, ignored -> {
                try (InputStream input = Minecraft.getInstance().getResourceManager().open(id)) {
                    BufferedImage image = ImageIO.read(input);
                    if (image != null) return new int[]{image.getWidth(), image.getHeight()};
                } catch (Exception ignoredException) {
                    // Item icons can be used as fallbacks without a texture resource.
                }
                return new int[]{1, 1};
            });
            if (size[0] == 1 && size[1] == 1) QuestIcons.render(graphics, source, x, y, width, height, alpha);
            else drawTexture(graphics, id, x, y, width, height, alpha, size[0], size[1], "cover");
        }
    }

    private static void renderImageCoverHandle(GuiGraphics graphics, Object input, String key,
                                                int x, int y, int width, int height, float alpha) {
        ImageHandle handle = IMAGES.computeIfAbsent(key, ignored -> new ImageHandle(input, key));
        handle.upload();
        if (handle.textureId != null)
            drawTexture(graphics, handle.textureId, x, y, width, height, alpha,
                    handle.width, handle.height, "cover");
    }

    private static void renderImageHandle(GuiGraphics graphics, Object input, String key,
                                          int x, int y, int width, int height, float alpha) {
        ImageHandle handle = IMAGES.computeIfAbsent(key, ignored -> new ImageHandle(input, key));
        handle.upload();
        if (handle.texture == null) return;
        drawTexture(graphics, handle.textureId, x, y, width, height, alpha,
                handle.width, handle.height, "fit_xy");
    }

    public static void renderVideo(GuiGraphics graphics, String owner, String elementId, String source,
                                   int x, int y, int width, int height, float alpha,
                                   boolean autoplay, boolean loop, boolean muted, float volume,
                                   String scaleType, String displayText) {
        PlaybackHandle handle = playback(owner, elementId, source, true,
                autoplay, loop, muted, volume);
        if (handle == null) return;
        handle.uploadFrame();
        if (handle.textureId != null) {
            drawTexture(graphics, handle.textureId, x, y, width, height, alpha,
                    handle.frameWidth, handle.frameHeight, scaleType);
        } else {
            graphics.fill(x, y, x + width, y + height, 0xB0101216);
            String status = handle.runtimeUnavailable ? missingRuntimeText()
                    : handle.failed ? "!" : handle.playing ? "..." : ">";
            status = Minecraft.getInstance().font.plainSubstrByWidth(status, Math.max(1, width - 12));
            graphics.drawCenteredString(Minecraft.getInstance().font, status,
                    x + width / 2, y + Math.max(0, (height - 8) / 2), 0xFFE4EAF2);
        }
        if (displayText != null && !displayText.isBlank()) {
            graphics.fill(x, y, x + width, Math.min(y + 16, y + height), withAlpha(0xB0101216, alpha));
            graphics.drawString(Minecraft.getInstance().font,
                    Minecraft.getInstance().font.plainSubstrByWidth(displayText, Math.max(1, width - 12)),
                    x + 6, y + 4, withAlpha(0xFFFFFFFF, alpha), true);
        }
    }

    public static void renderAudio(GuiGraphics graphics, String owner, String elementId, String source,
                                   int x, int y, int width, int height, float alpha,
                                   boolean autoplay, boolean loop, boolean muted, float volume,
                                   boolean showControls, String displayText) {
        PlaybackHandle handle = playback(owner, elementId, source, false,
                autoplay, loop, muted, volume);
        if (handle == null || !showControls) return;
        int background = withAlpha(0xCC20242A, alpha);
        graphics.fill(x, y, x + width, y + height, background);
        graphics.renderOutline(x, y, width, height, withAlpha(0xFF66717E, alpha));
        String state = handle.failed ? "!"
                : handle.playing ? "||" : ">";
        String name = handle.runtimeUnavailable ? missingRuntimeText()
                : displayText == null || displayText.isBlank()
                    ? net.minecraft.network.chat.Component.translatable(
                        "screen.xero_quest_journal.layout.audio").getString() : displayText;
        int textY = y + Math.max(0, (height - Minecraft.getInstance().font.lineHeight) / 2);
        graphics.drawString(Minecraft.getInstance().font, state, x + 6, textY,
                withAlpha(0xFFFFFFFF, alpha), true);
        graphics.drawString(Minecraft.getInstance().font,
                Minecraft.getInstance().font.plainSubstrByWidth(name, Math.max(1, width - 30)),
                x + 24, textY, withAlpha(0xFFE4EAF2, alpha), true);
    }

    public static boolean toggle(String owner, String elementId) {
        PlaybackHandle handle = PLAYBACKS.get(playbackKey(owner, elementId));
        if (handle == null) return false;
        handle.toggle();
        return true;
    }

    public static void release(String owner) {
        String prefix = owner + "\u0000";
        PLAYBACKS.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) return false;
            entry.getValue().close();
            return true;
        });
    }

    private static PlaybackHandle playback(String owner, String elementId, String source, boolean video,
                                           boolean autoplay, boolean loop, boolean muted, float volume) {
        // Previously saved extended media must still render a missing-companion notice.
        if (!validMediaSource(source, video ? MediaDecoders.VIDEO : MediaDecoders.EXTENDED_AUDIO)) {
            return null;
        }
        String key = playbackKey(owner, elementId);
        PlaybackHandle current = PLAYBACKS.get(key);
        if (current != null && (!current.source.equals(source) || current.video != video)) {
            if (PLAYBACKS.remove(key, current)) current.close();
            current = null;
        }
        if (current == null) {
            PlaybackHandle created = new PlaybackHandle(key, source, video, autoplay);
            PlaybackHandle previous = PLAYBACKS.putIfAbsent(key, created);
            if (previous != null) {
                created.close();
                current = previous;
            } else {
                current = created;
            }
        }
        current.loop = loop;
        current.muted = muted;
        current.volume = Math.max(0.0F, Math.min(1.0F, volume));
        return current;
    }

    private static String playbackKey(String owner, String elementId) {
        return owner + "\u0000" + elementId;
    }

    private static void drawTexture(GuiGraphics graphics, ResourceLocation texture,
                                    int x, int y, int width, int height, float alpha,
                                    int sourceWidth, int sourceHeight, String scaleType) {
        if ("cover".equals(scaleType) && sourceWidth > 0 && sourceHeight > 0 && width > 0 && height > 0) {
            // Crop texture coordinates, not screen scissor coordinates: callers can scale/pan the pose.
            double scale = Math.max((double) width / sourceWidth, (double) height / sourceHeight);
            int sampleWidth = Math.max(1, Math.min(sourceWidth, (int) Math.round(width / scale)));
            int sampleHeight = Math.max(1, Math.min(sourceHeight, (int) Math.round(height / scale)));
            graphics.setColor(1, 1, 1, Math.clamp(alpha, 0, 1));
            try {
                graphics.blit(texture, x, y, width, height, (sourceWidth - sampleWidth) / 2F,
                        (sourceHeight - sampleHeight) / 2F, sampleWidth, sampleHeight, sourceWidth, sourceHeight);
            } finally {
                graphics.setColor(1, 1, 1, 1);
            }
            return;
        }
        int drawX = x;
        int drawY = y;
        int drawWidth = Math.max(1, width);
        int drawHeight = Math.max(1, height);
        if (sourceWidth > 0 && sourceHeight > 0 && !"fit_xy".equals(scaleType)) {
            if ("center".equals(scaleType)) {
                drawWidth = Math.min(drawWidth, sourceWidth);
                drawHeight = Math.min(drawHeight, sourceHeight);
            } else {
                double scale = "cover".equals(scaleType)
                        ? Math.max((double) drawWidth / sourceWidth, (double) drawHeight / sourceHeight)
                        : Math.min((double) drawWidth / sourceWidth, (double) drawHeight / sourceHeight);
                drawWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
                drawHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
            }
            drawX += (width - drawWidth) / 2;
            drawY += (height - drawHeight) / 2;
        }
        if ("cover".equals(scaleType)) graphics.enableScissor(x, y, x + width, y + height);
        try {
            graphics.setColor(1.0F, 1.0F, 1.0F, Math.max(0.0F, Math.min(1.0F, alpha)));
            graphics.blit(texture, drawX, drawY, drawWidth, drawHeight, 0, 0,
                    Math.max(1, sourceWidth), Math.max(1, sourceHeight),
                    Math.max(1, sourceWidth), Math.max(1, sourceHeight));
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if ("cover".equals(scaleType)) graphics.disableScissor();
        }
    }

    private static NativeImage decodeImage(Object input) throws Exception {
        BufferedImage buffered;
        if (input instanceof Path path) {
            if (!validLocalFile(path, IMAGE_EXTENSIONS, MAX_IMAGE_BYTES)) return null;
            try (InputStream stream = Files.newInputStream(path)) {
                buffered = ImageIO.read(stream);
            }
        } else {
            Path cached = downloadImage(URI.create(input.toString()));
            try (InputStream stream = Files.newInputStream(cached)) {
                buffered = ImageIO.read(stream);
            }
        }
        return buffered == null ? null : toNativeImage(buffered);
    }

    private static NativeImage toNativeImage(BufferedImage source) {
        BufferedImage image = source;
        if (source.getType() != BufferedImage.TYPE_INT_ARGB) {
            image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.drawImage(source, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }
        NativeImage result = new NativeImage(image.getWidth(), image.getHeight(), false);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        for (int py = 0; py < image.getHeight(); py++) {
            for (int px = 0; px < image.getWidth(); px++) {
                int argb = pixels[py * image.getWidth() + px];
                int abgr = argb & 0xFF00FF00
                        | (argb & 0x00FF0000) >>> 16
                        | (argb & 0x000000FF) << 16;
                result.setPixelRGBA(px, py, abgr);
            }
        }
        return result;
    }

    private static Path downloadImage(URI uri) throws Exception {
        validateNetwork(uri);
        Path root = QuestConfig.root().resolve("cache").resolve("media").toAbsolutePath().normalize();
        Files.createDirectories(root);
        String name = sha256(uri.toString()) + ".image";
        Path target = root.resolve(name);
        if (Files.isRegularFile(target) && Files.size(target) > 0 && Files.size(target) <= MAX_IMAGE_BYTES) {
            return target;
        }
        Path temporary = target.resolveSibling(name + ".tmp");
        URI current = uri;
        for (int redirects = 0; redirects <= 5; redirects++) {
            validateNetwork(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "XeroQuestJournal/0.1")
                    .GET().build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IOException("Redirect without location"));
                response.body().close();
                current = current.resolve(location);
                continue;
            }
            if (status < 200 || status >= 300) {
                response.body().close();
                throw new IOException("HTTP " + status);
            }
            long declared = response.headers().firstValueAsLong("content-length").orElse(-1);
            if (declared > MAX_IMAGE_BYTES) {
                response.body().close();
                throw new IOException("Image is too large");
            }
            try (InputStream input = response.body()) {
                copyLimited(input, temporary, MAX_IMAGE_BYTES);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return target;
        }
        throw new IOException("Too many redirects");
    }

    private static void copyLimited(InputStream input, Path target, long maximum) throws IOException {
        try (var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[16384];
            long total = 0;
            for (int read; (read = input.read(buffer)) >= 0; ) {
                total += read;
                if (total > maximum) throw new IOException("Media is too large");
                output.write(buffer, 0, read);
            }
        } catch (IOException failure) {
            Files.deleteIfExists(target);
            throw failure;
        }
    }

    private static Path localMediaPath(String source) {
        if (source == null || source.isBlank() || isNetworkSource(source)) return null;
        Path root = QuestIcons.localRoot();
        try {
            Path resolved = root.resolve(source.strip().replace('/', java.io.File.separatorChar))
                    .toAbsolutePath().normalize();
            return resolved.startsWith(root) && !Files.isSymbolicLink(resolved) ? resolved : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean validLocalFile(Path path, Set<String> extensions, long maximum) {
        if (path == null || !Files.isRegularFile(path)) return false;
        try {
            return Files.size(path) <= maximum && extensions.contains(extension(path.getFileName().toString()));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String extension(String value) {
        int query = value.indexOf('?');
        String clean = query >= 0 ? value.substring(0, query) : value;
        int dot = clean.lastIndexOf('.');
        return dot < 0 ? "" : clean.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean validNetworkUri(String source) {
        try {
            URI uri = URI.create(source.strip());
            if (uri.getHost() == null || uri.getScheme() == null
                    || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return !host.equals("localhost") && !host.endsWith(".localhost")
                    && !host.equals("0.0.0.0") && !host.equals("::") && !host.equals("::1")
                    && !host.startsWith("127.");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void validateNetwork(URI uri) throws Exception {
        if (uri == null || uri.getHost() == null
                || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IOException("Unsupported media URI");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IOException("Private media address is not allowed");
            }
        }
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static String mediaSource(String source) throws Exception {
        if (isNetworkSource(source)) {
            return downloadMedia(URI.create(source.strip()));
        }
        Path path = localMediaPath(source);
        if (path == null || !Files.isRegularFile(path) || Files.size(path) > MAX_LOCAL_MEDIA_BYTES) {
            throw new IOException("Invalid local media source");
        }
        return path.toString();
    }

    private static String downloadMedia(URI uri) throws Exception {
        Path root = QuestConfig.root().resolve("cache").resolve("media").toAbsolutePath().normalize();
        Files.createDirectories(root);
        String name = sha256(uri.toString()) + ".media";
        Path target = root.resolve(name);
        if (Files.isRegularFile(target) && Files.size(target) > 0
                && Files.size(target) <= MAX_LOCAL_MEDIA_BYTES) return target.toString();
        Path temporary = root.resolve(name + "." + java.util.UUID.randomUUID() + ".tmp");
        URI current = uri;
        try {
            for (int redirects = 0; redirects <= 5; redirects++) {
                validateNetwork(current);
                HttpRequest request = HttpRequest.newBuilder(current)
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "XeroQuestJournal/0.1")
                        .GET().build();
                HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    try (InputStream ignored = response.body()) {
                        String location = response.headers().firstValue("location")
                                .orElseThrow(() -> new IOException("Redirect without location"));
                        current = current.resolve(location);
                    }
                    continue;
                }
                if (status < 200 || status >= 300) {
                    response.body().close();
                    throw new IOException("HTTP " + status);
                }
                String contentType = response.headers().firstValue("content-type")
                        .orElse("").toLowerCase(Locale.ROOT);
                String path = current.getPath() == null ? "" : current.getPath().toLowerCase(Locale.ROOT);
                if (path.endsWith(".m3u8") || contentType.contains("mpegurl")) {
                    response.body().close();
                    return current.toString();
                }
                long declared = response.headers().firstValueAsLong("content-length").orElse(-1);
                if (declared > MAX_LOCAL_MEDIA_BYTES) {
                    response.body().close();
                    throw new IOException("Media is too large");
                }
                try (InputStream input = response.body()) {
                    copyLimited(input, temporary, MAX_LOCAL_MEDIA_BYTES);
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return target.toString();
            }
            throw new IOException("Too many redirects");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static int withAlpha(int color, float alpha) {
        int original = color >>> 24;
        int adjusted = Math.round(original * Math.max(0.0F, Math.min(1.0F, alpha)));
        return color & 0xFFFFFF | adjusted << 24;
    }

    private static final class ImageHandle {
        private final String key;
        private final AtomicReference<NativeImage> decoded = new AtomicReference<>();
        private volatile boolean failed;
        private ResourceLocation textureId;
        private DynamicTexture texture;
        private int width;
        private int height;

        private ImageHandle(Object input, String key) {
            this.key = key;
            WORKERS.execute(() -> {
                try {
                    NativeImage image = decodeImage(input);
                    if (image == null) failed = true;
                    else decoded.set(image);
                } catch (Exception exception) {
                    failed = true;
                    XeroQuestJournal.LOGGER.warn("Unable to load quest image {}", key, exception);
                }
            });
        }

        private void upload() {
            NativeImage image = decoded.getAndSet(null);
            if (image == null || failed) return;
            width = image.getWidth();
            height = image.getHeight();
            textureId = ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID,
                    "media/images/" + Integer.toUnsignedString(key.hashCode(), 16));
            Minecraft minecraft = Minecraft.getInstance();
            if (texture != null) {
                minecraft.getTextureManager().release(textureId);
            }
            texture = new DynamicTexture(image);
            minecraft.getTextureManager().register(textureId, texture);
        }
    }

    private static String missingRuntimeText() {
        return net.minecraft.network.chat.Component.translatable(
                "screen.xero_quest_journal.media.runtime_missing").getString();
    }

    private static final class PlaybackHandle {
        private final String key;
        private final String source;
        private final boolean video;
        private final AtomicReference<NativeImage> latestFrame = new AtomicReference<>();
        private volatile boolean playing;
        private volatile boolean loop;
        private volatile boolean muted;
        private volatile float volume = 1.0F;
        private volatile boolean closed;
        private volatile boolean failed;
        private volatile boolean runtimeUnavailable;
        private volatile int frameWidth;
        private volatile int frameHeight;
        private ResourceLocation textureId;
        private DynamicTexture texture;
        private SourceDataLine audioLine;

        private PlaybackHandle(String key, String source, boolean video, boolean autoplay) {
            this.key = key;
            this.source = source;
            this.video = video;
            this.playing = autoplay;
            WORKERS.execute(this::decode);
        }

        private void toggle() {
            if (closed || runtimeUnavailable) return;
            playing = !playing;
            SourceDataLine line = audioLine;
            if (line != null) {
                if (playing) line.start();
                else line.stop();
            }
        }

        private void decode() {
            try {
                while (!closed) {
                    while (!closed && !playing) {
                        Thread.sleep(20);
                    }
                    if (closed) break;
                    failed = false;
                    decodeOnce();
                    closeAudio();
                    if (!closed && loop) {
                        playing = true;
                    }
                }
            } catch (Throwable exception) {
                if (!closed) {
                    runtimeUnavailable = exception instanceof MediaDecoders.MissingAddonException;
                    failed = true;
                    playing = false;
                    XeroQuestJournal.LOGGER.warn("Unable to play quest media {}", source, exception);
                }
            } finally {
                closeAudio();
            }
        }

        private void decodeOnce() throws Exception {
            if (!MediaDecoders.addonAvailable() && (video || !validAudioSource(source))) {
                throw new MediaDecoders.MissingAddonException("Install optional MediaPlayer or a local FFmpeg executable");
            }
            String input = mediaSource(source);
            try (MediaDecoder decoder = MediaDecoders.open(input, isNetworkSource(source), video)) {
                int decodedSampleRate = decoder.sampleRate();
                int decodedChannels = decoder.channels();
                if (!muted && decodedChannels > 0) {
                    openAudio(decodedSampleRate, decodedChannels);
                }
                long firstTimestamp = -1;
                long startedAt = 0;
                while (!closed) {
                    if (!playing) {
                        Thread.sleep(20);
                        continue;
                    }
                    MediaDecoder.DecodedFrame frame = decoder.nextFrame();
                    if (frame == null) {
                        playing = false;
                        return;
                    }
                    if (firstTimestamp < 0) {
                        firstTimestamp = Math.max(0, frame.timestampMicros());
                        startedAt = System.nanoTime();
                    }
                    pace(frame.timestampMicros() - firstTimestamp, startedAt);
                    if (video && frame.image() != null) {
                        BufferedImage buffered = frame.image();
                        if (buffered != null) {
                            NativeImage image = toNativeImage(buffered);
                            NativeImage previous = latestFrame.getAndSet(image);
                            if (previous != null) previous.close();
                        }
                    }
                    if (frame.samples() != null && !muted) {
                        if (audioLine == null && decodedChannels > 0) {
                            openAudio(decodedSampleRate, decodedChannels);
                        }
                        writeSamples(frame.samples());
                    }
                }
            }
        }

        private void pace(long timestampMicros, long startedAt) throws InterruptedException {
            long target = startedAt + Math.max(0, timestampMicros) * 1000L;
            long remaining;
            while (playing && !closed && (remaining = target - System.nanoTime()) > 2_000_000L) {
                Thread.sleep(Math.min(10, remaining / 1_000_000L));
            }
        }

        private void openAudio(int sampleRate, int channels) {
            try {
                int rate = sampleRate > 0 ? sampleRate : 44100;
                int count = Math.max(1, Math.min(8, channels));
                AudioFormat format = new AudioFormat(rate, 16, count, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                audioLine = (SourceDataLine) AudioSystem.getLine(info);
                audioLine.open(format, rate * count);
                if (playing) audioLine.start();
            } catch (Exception exception) {
                audioLine = null;
                XeroQuestJournal.LOGGER.warn("Unable to open quest media audio output", exception);
            }
        }

        private void writeSamples(Buffer[] samples) {
            SourceDataLine line = audioLine;
            if (line == null || samples.length == 0) return;
            Buffer buffer = samples[0];
            byte[] bytes;
            if (buffer instanceof ShortBuffer shorts) {
                ShortBuffer copy = shorts.duplicate();
                bytes = new byte[copy.remaining() * 2];
                int index = 0;
                while (copy.hasRemaining()) {
                    int sample = Math.round(copy.get() * volume);
                    sample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
                    bytes[index++] = (byte) sample;
                    bytes[index++] = (byte) (sample >>> 8);
                }
            } else if (buffer instanceof ByteBuffer raw) {
                ByteBuffer copy = raw.duplicate();
                bytes = new byte[copy.remaining()];
                copy.get(bytes);
                if (volume < 0.999F) {
                    for (int index = 0; index + 1 < bytes.length; index += 2) {
                        short original = (short) ((bytes[index] & 0xFF) | bytes[index + 1] << 8);
                        int sample = Math.round(original * volume);
                        bytes[index] = (byte) sample;
                        bytes[index + 1] = (byte) (sample >>> 8);
                    }
                }
            } else {
                return;
            }
            line.write(bytes, 0, bytes.length);
        }

        private void uploadFrame() {
            NativeImage image = latestFrame.getAndSet(null);
            if (image == null) return;
            frameWidth = image.getWidth();
            frameHeight = image.getHeight();
            Minecraft minecraft = Minecraft.getInstance();
            if (textureId == null) {
                textureId = ResourceLocation.fromNamespaceAndPath(XeroQuestJournal.MOD_ID,
                        "media/video/" + Integer.toUnsignedString(key.hashCode(), 16));
            }
            if (texture == null) {
                texture = new DynamicTexture(image);
                minecraft.getTextureManager().register(textureId, texture);
            } else {
                texture.setPixels(image);
                texture.upload();
            }
        }

        private void close() {
            closed = true;
            playing = false;
            NativeImage pending = latestFrame.getAndSet(null);
            if (pending != null) pending.close();
            closeAudio();
            if (textureId != null) {
                Minecraft.getInstance().execute(() ->
                        Minecraft.getInstance().getTextureManager().release(textureId));
            }
        }

        private void closeAudio() {
            SourceDataLine line = audioLine;
            audioLine = null;
            if (line != null) {
                try {
                    line.stop();
                    line.flush();
                    line.close();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }
}
