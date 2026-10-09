package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import xtdpotato.xero_quest_journal.client.ClientMediaManager;
import xtdpotato.xero_quest_journal.client.LayoutEditorState;
import xtdpotato.xero_quest_journal.client.QuestIcons;
import xtdpotato.xero_quest_journal.client.QuestMediaPicker;
import xtdpotato.xero_quest_journal.client.QuestMediaPickerScreen;
import xtdpotato.xero_quest_journal.client.QuestScreen;
import xtdpotato.xero_quest_journal.client.QuestValuePickerScreen;
import xtdpotato.xero_quest_journal.client.StyledButton;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;

final class MediaRegression {
    private static final String OWNER = "media-regression";
    private static int stage;
    private static int ticks;
    private static String source;
    private static String imported;
    private static Object playback;
    private static QuestScreen screen;
    private static QuestValuePickerScreen suggestions;
    private static final java.util.concurrent.atomic.AtomicInteger optionLoads = new java.util.concurrent.atomic.AtomicInteger();

    static boolean tick(Minecraft game) throws Exception {
        switch (stage) {
            case 0 -> {
                verifyDecoderCapabilities();
                Path wav = Files.createTempFile("xero-media-regression-", ".wav");
                try {
                    byte[] samples = new byte[22050];
                    try (var stream = new AudioInputStream(new ByteArrayInputStream(samples),
                            new AudioFormat(22050, 8, 1, true, false), samples.length)) {
                        AudioSystem.write(stream, AudioFileFormat.Type.WAVE, wav.toFile());
                    }
                    source = QuestMediaPicker.importFile(wav, false);
                } finally {
                    Files.deleteIfExists(wav);
                }
                require(ClientMediaManager.validAudioSource(source), "Imported audio not accepted");
                require(Files.isRegularFile(QuestIcons.localRoot().resolve(source)), "Imported file missing");
                require(ClientMediaManager.validAudioSource("https://example.com/audio?id=7"),
                        "Extensionless HTTP media address rejected");
                require(!ClientMediaManager.validAudioSource("http://localhost/private.wav"),
                        "Loopback media address accepted");
                var method = ClientMediaManager.class.getDeclaredMethod("playback",
                        String.class, String.class, String.class, boolean.class,
                        boolean.class, boolean.class, boolean.class, float.class);
                method.setAccessible(true);
                playback = method.invoke(null, OWNER, "wav", source, false, true, false, true, 1.0F);
                ticks = 0;
            }
            case 1 -> {
                require(!flag(playback, "failed"), "Audio decoder failed");
                if (flag(playback, "playing") && ++ticks < 200) return false;
                require(!flag(playback, "playing"), "Audio decoder did not reach EOF");
                ClientMediaManager.release(OWNER);
                String quest = QuestRepository.all().getFirst().id();
                screen = new QuestScreen();
                set(screen, "selectedId", quest);
                set(screen, "editMode", true);
                game.setScreen(screen);
                var layout = (LayoutEditorState) field(screen, "layout");
                layout.elements().put("media-regression-control", new LayoutEditorState.Element(
                        "media-regression-control", quest, "audio", source, 180, 28,
                        Map.of("displayText", "Original label", "muted", "true")));
                layout.setPosition("media-regression-control", 12, 12);
                call(screen, "editElement", "media-regression-control");
                require("Original label".equals(field(field(screen, "form"), "mediaDisplayText")),
                        "Existing display text did not load");
                set(field(screen, "form"), "propertyPage", "content");
                game.setScreen(screen);
                require(screen.children().stream().filter(StyledButton.class::isInstance)
                                .map(StyledButton.class::cast).anyMatch(b -> b.styleId().equals("media-choose")),
                        "Media file picker button missing");
                ticks = 0;
            }
            case 2 -> {
                if (++ticks < 5) return false;
                capture(game);
                @SuppressWarnings("unchecked")
                var fields = (Map<String, EditBox>) field(screen, "formFields");
                var pick = screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast)
                        .filter(b -> b.styleId().equals("media-choose")).findFirst().orElseThrow();
                require(fields.get("layout.media_display_text").getY() < fields.get("layout.media_source").getY(),
                        "Display text is not above media source");
                require(fields.get("layout.media_source").getRight() <= pick.getX(),
                        "Media source overlaps file picker");
                pick.onPress();
                require(game.screen instanceof QuestMediaPickerScreen,
                        "Media button did not open the in-game browser");
                @SuppressWarnings("unchecked")
                var roots = (java.util.List<Path>) field(game.screen, "roots");
                require(roots.contains(QuestIcons.localRoot())
                                && roots.stream().anyMatch(root -> root.endsWith("config")),
                        "Media browser does not include game/config asset folders");
                ticks = 0;
            }
            case 3 -> {
                var browser = (QuestMediaPickerScreen) game.screen;
                if (browser.inputBlocked() || ++ticks < 5) return false;
                capture(game, "media-browser.png");
                int rowX = (browser.width - Math.min(560, browser.width - 24)) / 2 + 10;
                require(browser.mouseClicked(rowX, 112, 0), "Could not enter media folder");
                browser.mouseReleased(rowX, 112, 0);
                require(((Path) field(browser, "directory")).getFileName().toString().equals("media"),
                        "Media folder did not open");
                require(browser.mouseClicked(rowX, 112, 0), "Could not select imported audio");
                browser.mouseReleased(rowX, 112, 0);
                require(field(browser, "selected") instanceof Path, "Media file was not selected");
                browser.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(b -> b.styleId().equals("media-import"))
                        .findFirst().orElseThrow().onPress();
                ticks = 0;
            }
            case 4 -> {
                if (game.screen != screen) {
                    require(++ticks < 120, "Media picker did not finish importing");
                    return false;
                }
                imported = (String) field(field(screen, "form"), "description");
                require(!imported.equals(source) && ClientMediaManager.validAudioSource(imported),
                        "Media picker did not return a playable asset");
                @SuppressWarnings("unchecked")
                var fields = (Map<String, EditBox>) field(screen, "formFields");
                fields.get("layout.media_display_text").setValue("Selected track");
                call(screen, "saveForm");
                var layout = (LayoutEditorState) field(screen, "layout");
                require("Selected track".equals(layout.elements().get("media-regression-control")
                        .attribute("displayText", ""))
                                && imported.equals(layout.elements().get("media-regression-control").value()),
                        "Imported media and display text not saved");
                call(screen, "editElement", "media-regression-control");
                require("Selected track".equals(field(field(screen, "form"), "mediaDisplayText")),
                        "Display text not restored");
                set(field(screen, "form"), "propertyPage", "appearance");
                game.setScreen(screen);
                var transparent = screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast)
                        .filter(b -> b.styleId().equals("color-transparent")).findFirst().orElseThrow();
                var chooseImage = screen.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast)
                        .filter(b -> b.styleId().equals("choose-background")).findFirst().orElseThrow();
                require(chooseImage.getX() >= transparent.getRight()
                                && chooseImage.getY() == transparent.getY(),
                        "Background image picker is not next to the transparent button");
                ClientMediaManager.release((String) field(screen, "mediaOwner"));
                Files.deleteIfExists(QuestIcons.localRoot().resolve(source));
                Files.deleteIfExists(QuestIcons.localRoot().resolve(imported));
                suggestions = new QuestValuePickerScreen(screen, "Candidate test", "media.regression",
                        () -> List.of(new QuestValuePickerScreen.Option("base", "Base")),
                        "", ignored -> {});
                game.setScreen(suggestions);
                ticks = 0;
            }
            case 5 -> {
                if (suggestions.inputBlocked() || ++ticks < 5) return false;
                ((EditBox) field(suggestions, "newCandidate")).setValue("regression-custom");
                suggestions.children().stream().filter(StyledButton.class::isInstance)
                        .map(StyledButton.class::cast).filter(b -> b.styleId().equals("candidate-add"))
                        .findFirst().orElseThrow().onPress();
                @SuppressWarnings("unchecked")
                var options = (List<QuestValuePickerScreen.Option>) field(suggestions, "filtered");
                require(options.stream().anyMatch(option -> option.value().equals("regression-custom")),
                        "Added suggestion did not appear");
            }
            case 6 -> {
                int panel = Math.min(560, suggestions.width - 24);
                int rowX = (suggestions.width - panel) / 2 + panel - 10;
                require(suggestions.mouseClicked(rowX, 78 + 22 + 6, 0),
                        "Could not remove custom suggestion");
                @SuppressWarnings("unchecked")
                var options = (List<QuestValuePickerScreen.Option>) field(suggestions, "filtered");
                require(options.stream().noneMatch(option -> option.value().equals("regression-custom")),
                        "Removed suggestion remained visible");
                suggestions = new QuestValuePickerScreen(screen, "Long candidate test", "",
                        () -> {
                            optionLoads.incrementAndGet();
                            return java.util.stream.IntStream.range(0, 100)
                                    .mapToObj(i -> new QuestValuePickerScreen.Option("option-" + i, "Option " + i))
                                    .toList();
                        }, "", ignored -> {});
                game.setScreen(suggestions);
                ticks = 0;
            }
            case 7 -> {
                if (suggestions.inputBlocked() || ++ticks < 5) return false;
                int panel = Math.min(560, suggestions.width - 24);
                int left = (suggestions.width - panel) / 2;
                int track = left + panel + 4;
                int bottom = Math.max(100, suggestions.height - 72);
                require(suggestions.mouseClicked(track, 80, 0), "Candidate scrollbar not clickable");
                suggestions.mouseDragged(track, bottom - 2, 0, 0, bottom - 82);
                suggestions.mouseReleased(track, bottom - 2, 0);
                require((double) field(suggestions, "smoothScroll") > 0, "Candidate scrollbar did not move");
                require(suggestions.mouseClicked(left + 20, 110, 0), "Candidate row cannot be dragged");
                suggestions.mouseDragged(left + 20, 88, 0, 0, -22);
                suggestions.mouseReleased(left + 20, 88, 0);
                require(game.screen == suggestions, "Dragging a candidate accidentally selected it");
                ((EditBox) field(suggestions, "search")).setValue("option-8");
                require(optionLoads.get() == 1, "Searching reloaded the entire candidate list");
                return true;
            }
        }
        stage++;
        return false;
    }

    private static void verifyDecoderCapabilities() throws Exception {
        boolean addon = Boolean.getBoolean("xero.mediaAddonExpected");
        require(xtdpotato.xero_quest_journal.client.media.MediaDecoders.addonAvailable() == addon,
                "Optional media backend discovery mismatch");
        for (String name : List.of("tone.ogg", "motion.mp4")) {
            boolean video = name.endsWith(".mp4");
            Path file = Files.createTempFile("xero-decoder-", video ? ".mp4" : ".ogg");
            Throwable primary = null;
            try {
                try (var input = MediaRegression.class.getResourceAsStream("/media/" + name)) {
                    require(input != null, "Missing test media " + name);
                    Files.copy(input, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                try (var decoder = xtdpotato.xero_quest_journal.client.media.MediaDecoders.open(file.toString(), false, video)) {
                    require(!video || addon, "Video unexpectedly decoded without optional backend");
                    int images = 0, samples = 0, frames = 0;
                    int firstPixel = 0;
                    boolean motion = false, color = false;
                    for (var frame = decoder.nextFrame(); frame != null; frame = decoder.nextFrame()) {
                        if (frame.image() != null) {
                            images++;
                            require(frame.image().getWidth() == 96, "Unexpected decoded video dimensions");
                            int pixel = frame.image().getRGB(48, 32);
                            if (images == 1) firstPixel = pixel;
                            else motion |= firstPixel != pixel;
                            for (int y = 0; y < frame.image().getHeight(); y += 8)
                                for (int x = 0; x < frame.image().getWidth(); x += 8)
                                    color |= (frame.image().getRGB(x, y) & 0xFFFFFF) != 0;
                        }
                        if (frame.samples() != null && frame.samples().length > 0
                                && frame.samples()[0].hasRemaining()) samples++;
                        require(++frames < 1000, "Media decoder failed to reach EOF");
                    }
                    require(video ? images > 1 : samples > 1, "No decoded content: " + name);
                    require(!video || color && motion, "Video frames were blank or static: " + name);
                } catch (xtdpotato.xero_quest_journal.client.media.MediaDecoders.MissingAddonException expected) {
                    require(video && !addon, "Built-in audio failed or installed decoder unavailable");
                }
            } catch (Exception | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                try { Files.deleteIfExists(file); }
                catch (Exception cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        }
        require(xtdpotato.xero_quest_journal.client.media.MediaDecoders.extensions(true).contains("mp4") == addon,
                "Video picker capability disagrees with decoder");
    }

    private static void capture(Minecraft game) throws Exception {
        capture(game, "media-properties.png");
    }

    private static void capture(Minecraft game, String name) throws Exception {
        Path output = Path.of("..", "build", "verification");
        Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name));
        }
    }

    private static boolean flag(Object target, String name) throws Exception {
        return (boolean) field(target, name);
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        var types = args.length == 0 ? new Class<?>[0] : new Class<?>[]{String.class};
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void require(boolean success, String message) {
        if (!success) throw new AssertionError("Media regression stage " + stage + ": " + message);
    }
}
