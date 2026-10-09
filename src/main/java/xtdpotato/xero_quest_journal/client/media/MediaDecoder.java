package xtdpotato.xero_quest_journal.client.media;

import java.awt.image.BufferedImage;
import java.nio.Buffer;

/** Small shared API: the core has no references to optional codec-library classes. */
public interface MediaDecoder extends AutoCloseable {
    int sampleRate();
    int channels();
    DecodedFrame nextFrame() throws Exception;
    @Override void close() throws Exception;

    record DecodedFrame(long timestampMicros, BufferedImage image, Buffer[] samples) {}
}
