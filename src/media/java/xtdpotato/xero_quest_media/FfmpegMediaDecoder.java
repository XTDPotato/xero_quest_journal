package xtdpotato.xero_quest_media;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import xtdpotato.xero_quest_journal.client.media.MediaDecoder;

/** Optional backend. All FFmpeg/JavaCPP/JavaCV references live in the companion, not the core. */
public final class FfmpegMediaDecoder implements MediaDecoder {
    private final FFmpegFrameGrabber grabber;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();

    public FfmpegMediaDecoder(String input, boolean networkSource) throws Exception {
        grabber = new FFmpegFrameGrabber(input);
        try {
            grabber.setOption("rw_timeout", "10000000");
            if (networkSource) {
                grabber.setOption("protocol_whitelist", "http,https,tcp,tls,crypto,data");
                grabber.setOption("user_agent", "XeroQuestJournal/0.1");
            }
            grabber.setSampleFormat(avutil.AV_SAMPLE_FMT_S16);
            grabber.start();
        } catch (Exception | LinkageError failure) {
            try { grabber.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            converter.close();
            throw failure;
        }
    }

    @Override public int sampleRate() { return grabber.getSampleRate(); }
    @Override public int channels() { return grabber.getAudioChannels(); }

    @Override public DecodedFrame nextFrame() throws Exception {
        Frame frame = grabber.grab();
        if (frame == null) return null;
        return new DecodedFrame(frame.timestamp,
                frame.image == null ? null : converter.convert(frame), frame.samples);
    }

    @Override public void close() throws Exception {
        try { grabber.close(); }
        finally { converter.close(); }
    }
}
