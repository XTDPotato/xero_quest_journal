package xtdpotato.xero_quest_journal.client.media;

import xtdpotato.xero_quest_journal.platform.PlatformServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Only explicit game-owned folders are searched, never the shell or PATH. */
public final class FfmpegInstallation {
    private FfmpegInstallation() {}

    public static Path find() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String platform = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT).contains("aarch64")
                || System.getProperty("os.arch").toLowerCase(Locale.ROOT).contains("arm64") ? "arm64" : "x86_64";
        String name = platform.equals("windows") ? "ffmpeg.exe" : "ffmpeg";
        for (Path root : List.of(PlatformServices.gameDir().resolve("ffmpeg"),
                PlatformServices.configDir().resolve("xero_quest/ffmpeg"))) {
            for (String directory : List.of(platform + "-" + arch + "/bin", platform + "-" + arch,
                    platform + "/bin", platform, "bin", "")) {
                Path file = root.resolve(directory).resolve(name).toAbsolutePath().normalize();
                if (Files.isRegularFile(file) && Files.isExecutable(file)) return file;
            }
        }
        return null;
    }
}
