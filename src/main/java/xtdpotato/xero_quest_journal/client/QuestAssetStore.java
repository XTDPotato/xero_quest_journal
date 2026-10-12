package xtdpotato.xero_quest_journal.client;

import xtdpotato.xero_quest_journal.platform.PlatformServices;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Confined file operations for player-authored quest assets. */
public final class QuestAssetStore {
    public enum Sort { NAME, TYPE, MODIFIED, SIZE }

    public record Entry(Path path, String name, boolean directory, String extension,
                        long size, long modified, int imageWidth, int imageHeight) {}

    @FunctionalInterface
    private interface IoAction { void run() throws IOException; }

    private record History(IoAction undo, IoAction redo, IoAction discard) {}

    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 16L * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;
    private static final int MAX_HISTORY = 48;
    private static final List<String> IMAGE_EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".bmp");
    private static final List<String> TEXT_EXTENSIONS = List.of(".txt", ".json", ".css", ".mcmeta");
    private static final Path ROOT = QuestIcons.localRoot();
    private static final Path HISTORY_ROOT = PlatformServices.configDir()
            .resolve("xero_quest").resolve("cache").resolve("asset-history");

    private final Path sessionTrash = HISTORY_ROOT.resolve(UUID.randomUUID().toString()).normalize();
    private final Deque<History> undo = new ArrayDeque<>();
    private final Deque<History> redo = new ArrayDeque<>();
    private int trashIndex;

    public Path root() {
        try {
            Files.createDirectories(ROOT);
            return ROOT;
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to create quest asset directory", failure);
        }
    }

    public Path resolve(String relative) throws IOException {
        return resolve(relative == null || relative.isBlank() ? ROOT : ROOT.resolve(relative));
    }

    public Path resolve(Path path) throws IOException {
        Path root = ROOT.toAbsolutePath().normalize();
        Path target = path.toAbsolutePath().normalize();
        if (!target.startsWith(root)) throw new IOException("Asset path escapes the asset directory");
        Path current = root;
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root))
            throw new IOException("Asset directory cannot be a symbolic link");
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
                throw new IOException("Symbolic links are not supported in quest assets");
        }
        return target;
    }

    public List<Entry> list(Path directory, Sort sort, boolean descending) throws IOException {
        Path safe = resolve(directory);
        if (!Files.isDirectory(safe, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Not a quest asset directory");
        try (var children = Files.list(safe)) {
            Comparator<Entry> comparator = switch (sort) {
                case TYPE -> Comparator.comparing((Entry e) -> e.directory() ? "" : e.extension())
                        .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
                case MODIFIED -> Comparator.comparingLong(Entry::modified);
                case SIZE -> Comparator.comparingLong(Entry::size);
                case NAME -> Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
            };
            if (descending) comparator = comparator.reversed();
            return children.filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                            || supported(path))
                    .map(path -> entry(path))
                    .sorted(Comparator.comparing((Entry e) -> !e.directory()).thenComparing(comparator))
                    .limit(4096)
                    .toList();
        }
    }

    private Entry entry(Path path) {
        try {
            boolean directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
            String name = path.getFileName().toString();
            String extension = extension(name);
            long size = directory ? 0 : Files.size(path);
            long modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
            int imageWidth = 0, imageHeight = 0;
            if (!directory && isImage(path)) {
                var dimensions = imageDimensions(path);
                imageWidth = dimensions[0];
                imageHeight = dimensions[1];
            }
            return new Entry(path, name, directory, extension, size, modified, imageWidth, imageHeight);
        } catch (IOException ignored) {
            return new Entry(path, path.getFileName().toString(), false, extension(path.toString()),
                    0, 0, 0, 0);
        }
    }

    public void createFolder(Path parent, String name) throws IOException {
        Path directory = resolve(parent);
        Path target = child(directory, name);
        Files.createDirectories(directory);
        Files.createDirectory(target);
        record(new History(
                () -> Files.delete(target),
                () -> Files.createDirectory(target),
                () -> {}));
    }

    public void createTextFile(Path parent, String name) throws IOException {
        String safeName = ensureTextExtension(name);
        Path directory = resolve(parent);
        Path target = child(directory, safeName);
        Files.createDirectories(directory);
        Files.writeString(target, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        record(new History(
                () -> Files.delete(target),
                () -> Files.writeString(target, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW),
                () -> {}));
    }

    public Path createTexture(Path parent, String name, int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (long) width * height > MAX_IMAGE_PIXELS)
            throw new IOException("Texture dimensions are out of range");
        Path directory = resolve(parent);
        Path target = child(directory, ensurePngExtension(name));
        Files.createDirectories(directory);
        BufferedImage blank = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        writePng(blank, target, false);
        byte[] contents = Files.readAllBytes(target);
        record(new History(
                () -> Files.delete(target),
                () -> Files.write(target, contents, StandardOpenOption.CREATE_NEW),
                () -> {}));
        return target;
    }

    public Path importFile(Path source, Path destinationDirectory, String desiredName) throws IOException {
        if (source == null || Files.isSymbolicLink(source)
                || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                || Files.size(source) > MAX_FILE_BYTES || !supported(source))
            throw new IOException("Unsupported or oversized asset file");
        Path directory = resolve(destinationDirectory);
        Files.createDirectories(directory);
        String name = desiredName == null || desiredName.isBlank() ? source.getFileName().toString() : desiredName;
        Path target = child(directory, name);
        Files.copy(source, target);
        record(new History(
                () -> deleteTree(target),
                () -> copyTree(source, target),
                () -> {}));
        return target;
    }

    public void rename(Path source, String newName) throws IOException {
        Path from = resolve(source);
        if (from.equals(ROOT) || !Files.exists(from, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Asset does not exist");
        Path to = child(from.getParent(), newName);
        if (from.equals(to)) return;
        Files.move(from, to);
        record(new History(
                () -> Files.move(to, from),
                () -> Files.move(from, to),
                () -> {}));
    }

    public Path paste(Path source, Path destinationDirectory, boolean move) throws IOException {
        Path from = resolve(source);
        Path parent = resolve(destinationDirectory);
        if (!Files.exists(from, LinkOption.NOFOLLOW_LINKS) || from.equals(ROOT)
                || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid asset selection");
        if (Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS) && parent.startsWith(from))
            throw new IOException("A folder cannot be pasted into itself");
        Path to = child(parent, from.getFileName().toString());
        if (from.equals(to)) throw new IOException("Source and destination are the same");
        if (move) Files.move(from, to);
        else copyTree(from, to);
        record(new History(
                () -> { if (move) Files.move(to, from); else deleteTree(to); },
                () -> { if (move) Files.move(from, to); else copyTree(from, to); },
                () -> {}));
        return to;
    }

    public void delete(Path path) throws IOException {
        Path target = resolve(path);
        if (target.equals(ROOT) || !Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid asset selection");
        verifyTree(target);
        Files.createDirectories(sessionTrash);
        Path saved = sessionTrash.resolve(Integer.toString(trashIndex++));
        Files.move(target, saved);
        record(new History(
                () -> Files.move(saved, target),
                () -> Files.move(target, saved),
                () -> deleteTree(saved)));
    }

    public void writeText(Path path, String text) throws IOException {
        Path target = resolve(path);
        if (!isText(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Not an editable text asset");
        byte[] previous = Files.readAllBytes(target);
        byte[] updated = text.getBytes(StandardCharsets.UTF_8);
        if (updated.length > 1024 * 1024) throw new IOException("Text asset is too large");
        Files.write(target, updated, StandardOpenOption.TRUNCATE_EXISTING);
        record(new History(
                () -> Files.write(target, previous, StandardOpenOption.TRUNCATE_EXISTING),
                () -> Files.write(target, updated, StandardOpenOption.TRUNCATE_EXISTING),
                () -> {}));
    }

    public String readText(Path path) throws IOException {
        Path target = resolve(path);
        if (!isText(target) || Files.size(target) > 1024 * 1024)
            throw new IOException("Not an editable text asset");
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    public BufferedImage readImage(Path path) throws IOException {
        Path target = resolve(path);
        if (!isImage(target) || Files.size(target) > MAX_FILE_BYTES)
            throw new IOException("Unsupported or oversized image");
        BufferedImage image = ImageIO.read(target.toFile());
        if (image == null || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION
                || (long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS)
            throw new IOException("Image dimensions are out of range");
        return image;
    }

    public Path savePng(Path parent, String name, BufferedImage image, boolean overwrite) throws IOException {
        Path directory = resolve(parent);
        Path target = child(directory, ensurePngExtension(name));
        Files.createDirectories(directory);
        writePng(image, target, overwrite);
        return target;
    }

    public void overwritePng(Path target, BufferedImage image) throws IOException {
        Path safe = resolve(target);
        writePng(image, safe, true);
    }

    public boolean undo() throws IOException {
        if (undo.isEmpty()) return false;
        History entry = undo.peekLast();
        entry.undo().run();
        undo.removeLast();
        redo.addLast(entry);
        return true;
    }

    public boolean redo() throws IOException {
        if (redo.isEmpty()) return false;
        History entry = redo.peekLast();
        entry.redo().run();
        redo.removeLast();
        undo.addLast(entry);
        return true;
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    public void close() {
        clear(undo);
        clear(redo);
        try { deleteTree(sessionTrash); }
        catch (IOException failure) { XeroQuestJournal.LOGGER.warn("Unable to clear asset undo files", failure); }
    }

    private void record(History entry) {
        clear(redo);
        undo.addLast(entry);
        while (undo.size() > MAX_HISTORY) discard(undo.removeFirst());
    }

    private void clear(Deque<History> history) {
        while (!history.isEmpty()) discard(history.removeFirst());
    }

    private void discard(History history) {
        try { history.discard().run(); }
        catch (IOException failure) { XeroQuestJournal.LOGGER.warn("Unable to discard asset history", failure); }
    }

    private static Path child(Path parent, String name) throws IOException {
        Path directory = new QuestAssetStore().resolve(parent);
        String safeName = validateName(name);
        Path target = directory.resolve(safeName).normalize();
        if (!target.getParent().equals(directory)) throw new IOException("Invalid asset name");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("An asset already uses that name");
        return target;
    }

    private static String validateName(String name) throws IOException {
        if (name == null) throw new IOException("A file name is required");
        String value = name.strip();
        if (value.isBlank() || value.length() > 128 || value.equals(".") || value.equals("..")
                || value.endsWith(".") || value.endsWith(" ")
                || value.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*"))
            throw new IOException("Invalid asset name");
        return value;
    }

    private static String ensurePngExtension(String name) throws IOException {
        String value = validateName(name);
        return extension(value).equals(".png") ? value : value + ".png";
    }

    private static String ensureTextExtension(String name) throws IOException {
        String value = validateName(name);
        if (!isText(Path.of(value))) value += ".txt";
        return value;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    public static boolean supported(Path path) {
        return isImage(path) || isText(path);
    }

    public static boolean isImage(Path path) {
        return IMAGE_EXTENSIONS.contains(extension(path.getFileName().toString()));
    }

    public static boolean isText(Path path) {
        return TEXT_EXTENSIONS.contains(extension(path.getFileName().toString()));
    }

    private static int[] imageDimensions(Path path) {
        try {
            var image = ImageIO.read(path.toFile());
            return image == null ? new int[]{0, 0} : new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException ignored) {
            return new int[]{0, 0};
        }
    }

    private static void writePng(BufferedImage image, Path target, boolean overwrite) throws IOException {
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1
                || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION
                || (long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS)
            throw new IOException("Image dimensions are out of range");
        Path safeTarget = new QuestAssetStore().resolve(target);
        if (!overwrite && Files.exists(safeTarget, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("An asset already uses that name");
        Path temporary = safeTarget.resolveSibling(safeTarget.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            if (!ImageIO.write(image, "png", temporary.toFile())) throw new IOException("PNG encoder unavailable");
            if (overwrite) Files.move(temporary, safeTarget, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, safeTarget);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void verifyTree(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links cannot be managed");
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (var children = Files.list(path)) {
                for (Path child : children.toList()) verifyTree(child);
            }
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        verifyTree(source);
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(target);
            try (var children = Files.list(source)) {
                for (Path child : children.toList())
                    copyTree(child, target.resolve(child.getFileName().toString()));
            }
        } else {
            Files.copy(source, target);
        }
    }

    private static void deleteTree(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        verifyTree(target);
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            try (var children = Files.list(target)) {
                for (Path child : children.toList()) deleteTree(child);
            }
        }
        Files.deleteIfExists(target);
    }
}
