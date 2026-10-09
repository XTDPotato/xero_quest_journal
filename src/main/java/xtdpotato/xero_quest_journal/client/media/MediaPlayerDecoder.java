package xtdpotato.xero_quest_journal.client.media;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Optional MediaPlayer 1.0.1 API bridge; no library classes or binaries are bundled. */
public final class MediaPlayerDecoder implements MediaDecoder {
    private final Object video, audioDecoder;
    private final AudioStream audio;
    private final double interval;
    private final long durationMicros;
    private long frameIndex, audioFrames;
    private ByteBuffer pixels;
    private boolean closed;

    public static boolean available() {
        if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows")) return false;
        try {
            Class.forName("net.hacker.mediaplayer.AudioDecoder", false, MediaPlayerDecoder.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) { return false; }
    }

    public MediaPlayerDecoder(String input, boolean hasVideo) throws Exception {
        if (input.startsWith("http://") || input.startsWith("https://"))
            throw new IOException("MediaPlayer playlists are not accepted; use a downloaded media file");
        Object openedVideo = null, openedAudio = null;
        AudioStream openedStream = null;
        double frameInterval = 0;
        try {
            if (hasVideo) {
                openedVideo = onRender(() -> {
                    Class<?> type = Class.forName("net.hacker.mediaplayer.DeviceType");
                    return Class.forName("net.hacker.mediaplayer.VideoDecoder")
                            .getConstructor(String.class, type).newInstance(input, type.getField("NONE").get(null));
                });
                frameInterval = (double) openedVideo.getClass().getMethod("getFrameRate").invoke(openedVideo);
                if (!Double.isFinite(frameInterval) || frameInterval <= 0) frameInterval = 1. / 30;
                openedAudio = openedVideo.getClass().getField("audio").get(openedVideo);
            } else openedAudio = Class.forName("net.hacker.mediaplayer.AudioDecoder")
                    .getConstructor(String.class).newInstance(input);
            if (openedAudio != null) openedStream = (AudioStream) openedAudio.getClass()
                    .getMethod("decode", boolean.class).invoke(openedAudio, false);
        } catch (Exception | LinkageError failure) {
            if (openedStream != null) openedStream.close();
            if (openedAudio != null) ((AutoCloseable) openedAudio).close();
            if (openedVideo != null) {
                Object cleanup = openedVideo;
                onRender(() -> { closeVideo(cleanup); return null; });
            }
            throw failure;
        }
        video = openedVideo;
        audioDecoder = openedAudio;
        audio = openedStream;
        interval = frameInterval;
        durationMicros = hasVideo ? movieDuration(Path.of(input)) : 0;
    }

    @Override public int sampleRate() { return audio == null ? 44100 : Math.round(audio.getFormat().getSampleRate()); }
    @Override public int channels() { return audio == null ? 0 : audio.getFormat().getChannels(); }

    @Override public DecodedFrame nextFrame() throws Exception {
        if (closed) return null;
        long timestamp = video == null ? audioFrames * 1_000_000L / sampleRate()
                : Math.round(frameIndex * interval * 1_000_000);
        if (video != null && durationMicros > 0 && timestamp >= durationMicros) return null;
        BufferedImage image = video == null ? null : onRender(this::videoFrame);
        int length = video == null ? 16384 : Math.max(4, (int) Math.round(interval * sampleRate()) * channels() * 2);
        ByteBuffer pcm = audio == null ? null : audio.read(length);
        if (video == null && (pcm == null || !pcm.hasRemaining())) return null;
        if (pcm != null) {
            pcm = pcm.duplicate();
            // The published AudioStream advertises unsigned little-endian PCM.
            if (audio.getFormat().getEncoding().equals(javax.sound.sampled.AudioFormat.Encoding.PCM_UNSIGNED)) {
                ByteBuffer signed = ByteBuffer.allocate(pcm.remaining());
                while (pcm.remaining() >= 2) {
                    int low = pcm.get() & 255, high = pcm.get() & 255;
                    short sample = (short) ((low | high << 8) - 32768);
                    signed.put((byte) sample).put((byte) (sample >>> 8));
                }
                signed.flip();
                pcm = signed;
            }
            audioFrames += pcm.remaining() / Math.max(2, channels() * 2);
        }
        frameIndex++;
        return new DecodedFrame(timestamp, image, pcm == null ? null : new Buffer[]{pcm});
    }

    private BufferedImage videoFrame() throws Exception {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            if (frameIndex > 0) video.getClass().getMethod("decode").invoke(video);
            AbstractTexture texture = (AbstractTexture) video.getClass().getField("frame").get(video);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getId());
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w <= 0 || h <= 0 || w > 4096 || h > 4096) throw new IOException("Invalid MediaPlayer frame size");
            int bytes = Math.multiplyExact(Math.multiplyExact(w, h), 4);
            if (pixels == null || pixels.capacity() != bytes) {
                if (pixels != null) MemoryUtil.memFree(pixels);
                pixels = MemoryUtil.memAlloc(bytes);
            }
            pixels.clear();
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            int[] values = new int[w * h];
            for (int i = 0, offset = 0; i < values.length; i++, offset += 4)
                values[i] = (pixels.get(offset) & 255) << 16 | (pixels.get(offset + 1) & 255) << 8
                        | pixels.get(offset + 2) & 255;
            result.setRGB(0, 0, w, h, values, 0, w);
            return result;
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, packAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    private static <T> T onRender(Callable<T> action) throws Exception {
        var game = Minecraft.getInstance();
        if (game.isSameThread()) return action.call();
        return game.submit(() -> {
            try { return action.call(); }
            catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }).get(30, TimeUnit.SECONDS);
    }

    private static void closeVideo(Object decoder) throws Exception {
        try { ((AutoCloseable) decoder).close(); }
        finally { ((AbstractTexture) decoder.getClass().getField("frame").get(decoder)).releaseId(); }
    }

    @Override public void close() throws Exception {
        if (closed) return;
        closed = true;
        try {
            if (audio != null) audio.close();
            if (audioDecoder != null) ((AutoCloseable) audioDecoder).close();
        } finally {
            try { if (video != null) onRender(() -> { closeVideo(video); return null; }); }
            finally { if (pixels != null) { MemoryUtil.memFree(pixels); pixels = null; } }
        }
    }

    // MediaPlayer exposes no video EOF result. MP4/MOV mvhd supplies the finite timeline.
    private static long movieDuration(Path file) {
        try (var data = new RandomAccessFile(file.toFile(), "r")) {
            return durationBox(data, 0, data.length(), 0);
        } catch (IOException | RuntimeException invalid) { return 0; }
    }

    private static long durationBox(RandomAccessFile data, long start, long end, int depth) throws IOException {
        if (depth > 2) return 0;
        for (long offset = start; offset <= end - 8;) {
            data.seek(offset);
            long size = Integer.toUnsignedLong(data.readInt());
            int type = data.readInt();
            int header = 8;
            if (size == 1) { size = data.readLong(); header = 16; }
            else if (size == 0) size = end - offset;
            if (size < header || size > end - offset) return 0;
            if (type == 0x6D6F6F76) {
                long duration = durationBox(data, offset + header, offset + size, depth + 1);
                if (duration > 0) return duration;
            }
            if (type == 0x6D766864 && size >= header + 20) {
                int version = data.readUnsignedByte();
                data.skipBytes(3 + (version == 1 ? 16 : 8));
                long scale = Integer.toUnsignedLong(data.readInt());
                long duration = version == 1 ? data.readLong() : Integer.toUnsignedLong(data.readInt());
                if (scale > 0 && duration > 0) return Math.round(duration * (1_000_000. / scale));
            }
            offset += size;
        }
        return 0;
    }
}
