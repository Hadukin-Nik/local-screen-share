package ru.hniApplications.testApplication;

import ru.hniApplications.testApplication.capture.AudioDevice;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ScreenCaptureEncoder implements AutoCloseable {
    private final Process process;
    private final InputStream ffmpegOutput;
    private final OutputStream ffmpegInput = new ByteArrayOutputStream();

    public ScreenCaptureEncoder(int fps, int width, int height) throws IOException {
        this(fps, width, height, null, "2000k");
    }

    public ScreenCaptureEncoder(int fps, int width, int height, boolean useAudioPipe) throws IOException {
        this(fps, width, height, null, "2000k");
    }

    public ScreenCaptureEncoder(int fps, int width, int height, AudioDevice device) throws IOException {
        this(fps, width, height, device, "2000k");
    }

    // НОВЫЙ ГЛАВНЫЙ КОНСТРУКТОР С БИТРЕЙТОМ
    public ScreenCaptureEncoder(int fps, int width, int height, AudioDevice device, String videoBitrate) throws IOException {
        List<String> cmd = new ArrayList<>();

        cmd.add(FFmpegLocator.getPath());
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("warning");

        cmd.add("-f");
        cmd.add("dshow");
        cmd.add("-framerate");
        cmd.add(String.valueOf(fps));
        cmd.add("-video_size");
        cmd.add(width + "x" + height);

        if (device != null) {
            System.out.println("[ENCODER] Инициализация видео + аудио: " + device.ffmpegArg);
            cmd.add("-i");
            cmd.add("video=screen-capture-recorder:audio=" + device.ffmpegArg);
        } else {
            System.out.println("[ENCODER] Инициализация только видео (без звука)");
            cmd.add("-i");
            cmd.add("video=screen-capture-recorder");
        }

        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("ultrafast");
        cmd.add("-tune");
        cmd.add("zerolatency");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-g");
        cmd.add(String.valueOf(fps));
        cmd.add("-b:v");
        cmd.add(videoBitrate != null && !videoBitrate.isEmpty() ? videoBitrate : "2000k"); // ПРИМЕНЯЕМ КАЧЕСТВО СЮДА

        if (device != null) {
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("128k");
            cmd.add("-ac");
            cmd.add("2");
            cmd.add("-ar");
            cmd.add("44100");
        }

        cmd.add("-flush_packets");
        cmd.add("1");
        cmd.add("-f");
        cmd.add("mpegts");
        cmd.add("pipe:1");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        this.process = pb.start();

        this.ffmpegOutput = new BufferedInputStream(process.getInputStream(), 256 * 1024);

        Thread errThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("[ffmpeg-encode] " + line);
                }
            } catch (Exception ignored) {
            }
        }, "ffmpeg-encoder-stderr");
        errThread.setDaemon(true);
        errThread.start();
    }

    public OutputStream getAudioInputStream() {
        return ffmpegInput;
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