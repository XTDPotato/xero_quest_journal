package xtdpotato.xero_quest_journal.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void write(Path file, String source, boolean replace) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".xero-", ".tmp");
        try {
            Files.writeString(temporary, source, StandardCharsets.UTF_8);
            if (replace) {
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.move(temporary, file);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
