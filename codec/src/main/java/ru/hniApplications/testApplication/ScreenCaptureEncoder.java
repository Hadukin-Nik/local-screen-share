package ru.hniApplications.testApplication;

import java.io.*;

public class ScreenCaptureEncoder implements AutoCloseable {

    private final Process process;
    private final InputStream ffmpegOutput;

    public ScreenCaptureEncoder(int fps, int width, int height) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "info",


                "-f", "gdigrab",
                "-framerate", String.valueOf(fps),
                "-video_size", width + "x" + height,
                "-i", "desktop",


                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-pix_fmt", "yuv420p",
                "-profile:v", "baseline",
                "-bf", "0",
                "-g", "30",
                "-keyint_min", "30",
                "-b:v", "2000k",


                "-flush_packets", "1",
                "-f", "mpegts",
                "pipe:1"
        );

        pb.redirectErrorStream(false);
        this.process = pb.start();
        this.ffmpegOutput = new BufferedInputStream(process.getInputStream(), 256 * 1024);

        Thread errThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("[ffmpeg] " + line);
                }
            } catch (Exception ignored) {
            }
        }, "ffmpeg-encoder-stderr");
        errThread.setDaemon(true);
        errThread.start();
    }

    public byte[] readChunk() throws IOException {
        byte[] buf = new byte[16 * 1024];
        int read = ffmpegOutput.read(buf);
        if (read == -1) return null;
        if (read == buf.length) return buf;
        byte[] result = new byte[read];
        System.arraycopy(buf, 0, result, 0, read);
        return result;
    }

    @Override
    public void close() {
        process.destroyForcibly();
    }
}