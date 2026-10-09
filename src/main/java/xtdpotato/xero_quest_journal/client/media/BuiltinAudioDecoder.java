package xtdpotato.xero_quest_journal.client.media;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Uses only Minecraft's Vorbis decoder and Java Sound, with bounded PCM chunks. */
public final class BuiltinAudioDecoder implements MediaDecoder {
    private final AudioStream vorbis;
    private final AudioInputStream pcm;
    private final AudioFormat format;
    private long sampleFrames;

    public BuiltinAudioDecoder(Path file) throws IOException, UnsupportedAudioFileException {
        BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file));
        AudioStream openedVorbis = null;
        AudioInputStream openedPcm = null;
        try {
            input.mark(64);
            byte[] magic = input.readNBytes(64);
            input.reset();
            if (magic.length >= 4 && magic[0] == 'O' && magic[1] == 'g'
                    && magic[2] == 'g' && magic[3] == 'S') {
                int packet = magic.length > 26 ? 27 + Byte.toUnsignedInt(magic[26]) : magic.length;
                if (magic.length < packet + 7 || magic[packet] != 1
                        || !new String(magic, packet + 1, 6, java.nio.charset.StandardCharsets.US_ASCII)
                            .equals("vorbis")) {
                    throw new UnsupportedAudioFileException("Built-in Ogg support is Vorbis only");
                }
                openedVorbis = new JOrbisAudioStream(input);
                format = openedVorbis.getFormat();
            } else {
                AudioInputStream encoded = AudioSystem.getAudioInputStream(input);
                try {
                    AudioFormat original = encoded.getFormat();
                    format = new AudioFormat(original.getSampleRate(), 16,
                            original.getChannels(), true, false);
                    openedPcm = AudioSystem.getAudioInputStream(format, encoded);
                } catch (RuntimeException failure) {
                    encoded.close();
                    throw failure;
                }
            }
            if (format.getChannels() < 1 || format.getChannels() > 8
                    || format.getSampleRate() <= 0 || !Float.isFinite(format.getSampleRate())) {
                throw new IOException("Unsupported audio channel count or sample rate");
            }
        } catch (IOException | UnsupportedAudioFileException | RuntimeException failure) {
            if (openedVorbis != null) openedVorbis.close();
            if (openedPcm != null) openedPcm.close();
            input.close();
            throw failure;
        }
        vorbis = openedVorbis;
        pcm = openedPcm;
    }

    @Override public int sampleRate() { return Math.round(format.getSampleRate()); }
    @Override public int channels() { return format.getChannels(); }

    @Override public DecodedFrame nextFrame() throws IOException {
        ByteBuffer samples;
        if (vorbis != null) {
            samples = vorbis.read(4096 * format.getFrameSize());
        } else {
            byte[] bytes = pcm.readNBytes(4096 * format.getFrameSize());
            samples = ByteBuffer.wrap(bytes);
        }
        if (!samples.hasRemaining()) return null;
        long timestamp = sampleFrames * 1_000_000L / sampleRate();
        sampleFrames += samples.remaining() / format.getFrameSize();
        return new DecodedFrame(timestamp, null, new Buffer[]{samples});
    }

    @Override public void close() throws IOException {
        if (vorbis != null) vorbis.close();
        else pcm.close();
    }
}
