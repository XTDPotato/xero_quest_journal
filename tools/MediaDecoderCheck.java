import xtdpotato.xero_quest_journal.client.media.MediaDecoder;
import xtdpotato.xero_quest_journal.client.media.MediaDecoders;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone decoder regression: no Minecraft window, audio device or optional natives required. */
public final class MediaDecoderCheck {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void decode(Path path) throws Exception {
        try (MediaDecoder decoder = MediaDecoders.open(path.toString(), false, false)) {
            require(decoder.sampleRate() > 0 && decoder.channels() > 0, "Invalid PCM format");
            long previous = -1;
            int frames = 0;
            for (MediaDecoder.DecodedFrame frame; (frame = decoder.nextFrame()) != null;) {
                require(frame.timestampMicros() >= previous, "Timestamps moved backwards");
                require(frame.image() == null && frame.samples() != null, "Invalid audio-only frame");
                require(((ByteBuffer) frame.samples()[0]).hasRemaining(), "Empty PCM chunk");
                previous = frame.timestampMicros();
                require(++frames < 10000, "Decoder did not reach EOF");
            }
            require(frames > 0, "No audio decoded: " + path);
        }
        System.out.println("PASS: decoded " + path.getFileName());
    }

    public static void main(String[] args) throws Exception {
        boolean withAddon = args.length > 0 && args[0].equals("addon");
        require(MediaDecoders.addonAvailable() == withAddon, "Optional decoder discovery mismatch");
        require(MediaDecoders.extensions(false).contains("ogg"), "Built-in Vorbis missing");
        require(MediaDecoders.extensions(false).contains("wav"), "Built-in WAV missing");
        require(MediaDecoders.extensions(false).contains("mp3") == withAddon, "MP3 capability mismatch");
        require(MediaDecoders.extensions(true).contains("mp4") == withAddon, "Video capability mismatch");
        Path fixtures = Path.of("build/media-test");
        decode(fixtures.resolve("sample.wav"));
        decode(Path.of("build/slim-work/fixtures/sample-vorbis.ogg"));
        for (AudioFileFormat.Type type : new AudioFileFormat.Type[]{AudioFileFormat.Type.WAVE,
                AudioFileFormat.Type.AIFF, AudioFileFormat.Type.AU}) {
            Path temporary = Files.createTempFile("xero-basic-audio-", "." + type.getExtension());
            try {
                byte[] samples = new byte[22050];
                try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(samples),
                        new AudioFormat(22050, 8, 1, true, false), samples.length)) {
                    require(AudioSystem.write(stream, type, temporary.toFile()) > 0, "Fixture creation failed");
                }
                decode(temporary);
            } finally { Files.deleteIfExists(temporary); }
        }
        if (!withAddon) {
            try {
                MediaDecoders.open(fixtures.resolve("sample.ogg").toString(), false, false);
                throw new AssertionError("Ogg/FLAC accepted as built-in Vorbis");
            } catch (MediaDecoders.MissingAddonException expected) {
                System.out.println("PASS: Ogg/FLAC is not confused with Ogg/Vorbis");
            }
            try {
                MediaDecoders.open(fixtures.resolve("sample.mp3").toString(), false, false);
                throw new AssertionError("MP3 accepted without a decoder addon");
            } catch (MediaDecoders.MissingAddonException expected) {
                System.out.println("PASS: unsupported MP3 reports missing optional decoder");
            }
            try {
                MediaDecoders.open(fixtures.resolve("sample.mp4").toString(), false, true);
                throw new AssertionError("Video accepted without a decoder addon");
            } catch (MediaDecoders.MissingAddonException expected) {
                System.out.println("PASS: video reports missing optional decoder");
            }
        }
        System.out.println("PASS: capabilities, decoding, EOF and optional dependency isolation");
    }
}
