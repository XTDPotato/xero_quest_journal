package xtdpotato.xero_quest_journal.platform;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class PlatformServices {
    private static volatile XeroPlatform current = new FallbackPlatform();

    private PlatformServices() {
    }

    public static void install(XeroPlatform platform) {
        if (platform == null) throw new IllegalArgumentException("platform");
        current = platform;
    }

    public static Path configDir() {
        return current.configDir();
    }

    public static Path gameDir() {
        return current.gameDir();
    }

    public static boolean isModLoaded(String modId) {
        return modId != null && !modId.isBlank() && current.isModLoaded(modId);
    }

    private static final class FallbackPlatform implements XeroPlatform {
        private final Path gameDir = Paths.get(System.getProperty("user.dir", "."))
                .toAbsolutePath().normalize();

        @Override
        public Path configDir() {
            return gameDir.resolve("config");
        }

        @Override
        public Path gameDir() {
            return gameDir;
        }

        @Override
        public boolean isModLoaded(String modId) {
            return false;
        }
    }
}
