package xtdpotato.xero_quest_journal.client.media;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Bounded RGB/PCM pipes; FFmpeg remains an optional user-supplied executable. */
public final class FfmpegExecutableDecoder implements MediaDecoder {
    private final Process audio, video;
    private final InputStream samples, images;
    private long audioFrames, imageFrames;
    private boolean closed;

    public FfmpegExecutableDecoder(Path executable, String input, boolean networkSource, boolean hasVideo) throws IOException {
        Process a = null, v = null;
        try {
            a = launch(executable, input, networkSource, List.of("-vn", "-map", "0:a:0",
                    "-ac", "2", "-ar", "44100", "-f", "s16le", "pipe:1"));
            if (hasVideo) v = launch(executable, input, networkSource, List.of("-an", "-map", "0:v:0",
                    "-vf", "fps=30,scale='min(1280,iw)':-2", "-c:v", "ppm", "-f", "image2pipe", "pipe:1"));
        } catch (IOException failure) {
            terminate(a);
            terminate(v);
            throw failure;
        }
        audio = a;
        video = v;
        samples = new BufferedInputStream(audio.getInputStream());
        images = video == null ? null : new BufferedInputStream(video.getInputStream(), 65536);
    }

    private static Process launch(Path executable, String input, boolean network, List<String> output) throws IOException {
        var args = new ArrayList<String>();
        args.add(executable.toString());
        args.addAll(List.of("-hide_banner", "-loglevel", "error", "-nostdin"));
        // Do not permit playlists to open file/device protocols from remote media.
        args.addAll(List.of("-protocol_whitelist", network ? "http,https,tcp,tls,crypto,data" : "file,pipe"));
        if (network) args.addAll(List.of("-rw_timeout", "10000000"));
        args.addAll(List.of("-i", input));
        args.addAll(output);
        return new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    @Override public int sampleRate() { return 44100; }
    @Override public int channels() { return 2; }

    @Override public DecodedFrame nextFrame() throws Exception {
        if (closed) return null;
        BufferedImage image = null;
        long timestamp;
        if (images != null) {
            String magic = token(images);
            if (magic == null) {
                successful(video, "video");
                return null;
            }
            if (!magic.equals("P6")) throw new IOException("Unexpected FFmpeg frame format");
            int width = dimension(token(images)), height = dimension(token(images));
            if (!"255".equals(token(images)) || (long) width * height > 1280L * 4096)
                throw new IOException("FFmpeg frame exceeds limits");
            byte[] rgb = images.readNBytes(Math.multiplyExact(Math.multiplyExact(width, height), 3));
            if (rgb.length != width * height * 3) throw new EOFException("Truncated video frame");
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] pixels = new int[width * height];
            for (int i = 0, b = 0; i < pixels.length; i++, b += 3)
                pixels[i] = (rgb[b] & 255) << 16 | (rgb[b + 1] & 255) << 8 | rgb[b + 2] & 255;
            image.setRGB(0, 0, width, height, pixels, 0, width);
            timestamp = imageFrames++ * 1_000_000L / 30;
        } else timestamp = audioFrames * 1_000_000L / 44100;
        byte[] pcm = samples.readNBytes(images == null ? 16384 : 5880);
        if (pcm.length == 0 && images == null) {
            successful(audio, "audio");
            return null;
        }
        audioFrames += pcm.length / 4;
        return new DecodedFrame(timestamp, image, pcm.length == 0 ? null : new Buffer[]{ByteBuffer.wrap(pcm)});
    }

    private static int dimension(String value) throws IOException {
        try {
            int size = Integer.parseInt(value);
            if (size < 1 || size > 4096) throw new NumberFormatException();
            return size;
        } catch (NumberFormatException invalid) { throw new IOException("Invalid frame size", invalid); }
    }

    private static String token(InputStream stream) throws IOException {
        int value;
        do {
            value = stream.read();
            if (value == '#') { while (value >= 0 && value != '\n') value = stream.read(); }
        } while (value >= 0 && value <= 32);
        if (value < 0) return null;
        var result = new StringBuilder();
        do {
            if (result.length() >= 32) throw new IOException("Invalid PPM header");
            result.append((char) value);
            value = stream.read();
        } while (value > 32);
        return result.toString();
    }

    private static void successful(Process process, String type) throws Exception {
        if (!process.waitFor(3, TimeUnit.SECONDS)) throw new IOException("FFmpeg " + type + " did not terminate");
        if (process.exitValue() != 0) throw new IOException("FFmpeg could not decode " + type);
    }

    @Override public void close() {
        closed = true;
        terminate(video);
        terminate(audio);
        try { samples.close(); } catch (IOException ignored) {}
        if (images != null) try { images.close(); } catch (IOException ignored) {}
    }

    private static void terminate(Process process) {
        if (process == null) return;
        process.destroy();
        try { if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly(); }
        catch (InterruptedException interrupted) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
    }
}
