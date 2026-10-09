package xtdpotato.xero_quest_journal.client.media;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Set;

/** One capability table and decoder factory, shared by rendering, validation and file pickers. */
public final class MediaDecoders {
    public static final Set<String> BASIC_AUDIO = Set.of("ogg", "oga", "wav", "aif", "aiff", "au", "snd");
    public static final Set<String> EXTENDED_AUDIO = Set.of(
            "ogg", "oga", "wav", "aif", "aiff", "au", "snd", "mp3", "m4a", "aac", "flac", "opus");
    public static final Set<String> VIDEO = Set.of(
            "mp4", "m4v", "mov", "mkv", "webm", "avi", "flv", "m3u8");
    private static final String ADDON_DECODER = "xtdpotato.xero_quest_media.FfmpegMediaDecoder";

    private MediaDecoders() {}

    // Do not initialize optional classes while opening the journal or checking file extensions.
    private static final class Addon {
        private static final Constructor<?> CONSTRUCTOR = findConstructor();

        private static Constructor<?> findConstructor() {
            try {
                return Class.forName(ADDON_DECODER, false, MediaDecoders.class.getClassLoader())
                        .asSubclass(MediaDecoder.class).getConstructor(String.class, boolean.class);
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (ReflectiveOperationException | LinkageError incompatible) {
                // An invalid companion must not prevent ordinary journal/basic-audio use.
                return null;
            }
        }
    }

    public static boolean addonAvailable() {
        return FfmpegInstallation.find() != null || MediaPlayerDecoder.available() || Addon.CONSTRUCTOR != null;
    }

    public static Set<String> extensions(boolean video) {
        return video ? addonAvailable() ? VIDEO : Set.of()
                : addonAvailable() ? EXTENDED_AUDIO : BASIC_AUDIO;
    }

    public static MediaDecoder open(String input, boolean networkSource, boolean video) throws Exception {
        boolean local = !input.startsWith("http://") && !input.startsWith("https://");
        Exception basicFailure = null;
        // Optional codecs must not replace Minecraft's reliable basic-audio path.
        if (!video && local) {
            try { return new BuiltinAudioDecoder(Path.of(input)); }
            catch (UnsupportedAudioFileException | IllegalArgumentException unsupported) { basicFailure = unsupported; }
        }
        Path executable = FfmpegInstallation.find();
        if (executable != null) return new FfmpegExecutableDecoder(executable, input,
                input.startsWith("http://") || input.startsWith("https://"), video);
        if (MediaPlayerDecoder.available()) {
            try { return new MediaPlayerDecoder(input, video); }
            catch (Exception | LinkageError failure) {
                if (!video && !input.startsWith("http://") && !input.startsWith("https://")) {
                    try { return new BuiltinAudioDecoder(Path.of(input)); }
                    catch (Exception fallback) { failure.addSuppressed(fallback); }
                }
                throw failure;
            }
        }
        if (Addon.CONSTRUCTOR != null) {
            try {
                return (MediaDecoder) Addon.CONSTRUCTOR.newInstance(input, networkSource);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                // Basic audio remains available even if the installed companion has wrong-platform natives.
                if (!video && !input.startsWith("http://") && !input.startsWith("https://")) {
                    try { return new BuiltinAudioDecoder(Path.of(input)); }
                    catch (Exception fallbackFailure) { cause.addSuppressed(fallbackFailure); }
                }
                if (cause instanceof Exception exception) throw exception;
                if (cause instanceof Error error) throw error;
                throw new IOException("Unable to initialize optional media decoder", cause);
            }
        }
        if (video || input.startsWith("http://") || input.startsWith("https://")) {
            throw new MissingAddonException("Install optional MediaPlayer or place FFmpeg in the ffmpeg folder");
        }
        throw new MissingAddonException("Audio codec is not supported by Minecraft/Java Sound", basicFailure);
    }

    public static final class MissingAddonException extends IOException {
        public MissingAddonException(String message) { super(message); }
        public MissingAddonException(String message, Throwable cause) { super(message, cause); }
    }
}
