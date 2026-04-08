package ru.hniApplications.testApplication;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class H264Codec implements AutoCloseable {

    private final ScreenCaptureEncoder encoder;
    private final StreamDecoder decoder;

    public H264Codec(int fps, int width, int height) throws IOException {
        this.encoder = new ScreenCaptureEncoder(fps, width, height, null);
        this.decoder = new StreamDecoder(width, height);
    }

    public byte[] encode(BufferedImage image) {
        try {
            return encoder.readChunk();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public BufferedImage getLatestFrame() {
        return decoder.getLatestFrame();
    }


    public BufferedImage waitForFrame(long timeoutMs) throws InterruptedException {
        return decoder.waitForFrame(timeoutMs);
    }


    public BufferedImage decode(byte[] encodedData) throws InterruptedException {
        decoder.feedData(encodedData);
        return decoder.waitForFrame(50);
    }

    @Override
    public void close() {
        encoder.close();
        decoder.close();
    }
}