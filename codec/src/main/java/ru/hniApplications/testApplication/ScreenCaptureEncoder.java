package ru.hniApplications.testApplication;

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

        cmd.add("C:\\Users\\husan\\Downloads\\ffmpeg-master-latest-win64-gpl\\bin\\ffmpeg.exe");
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

    public static class AudioDevice {
        public final String displayName;
        public final String ffmpegArg;
        public final boolean isLoopback;

        public AudioDevice(String displayName, String ffmpegArg, boolean isLoopback) {
            this.displayName = displayName;
            this.ffmpegArg = ffmpegArg;
            this.isLoopback = isLoopback;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static List<AudioDevice> listAllAudioDevices() {
        List<AudioDevice> list = new ArrayList<>();
        String ffmpegPath = "C:\\Users\\husan\\Downloads\\ffmpeg-master-latest-win64-gpl\\bin\\ffmpeg.exe";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath, "-list_devices", "true", "-f", "dshow", "-i", "dummy"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), "UTF-8"))) {

                String line;
                String currentAudioName = null;

                while ((line = br.readLine()) != null) {
                    if (line.contains("(audio)")) {
                        int startQuote = line.indexOf('"');
                        int endQuote = line.indexOf('"', startQuote + 1);
                        if (startQuote != -1 && endQuote > startQuote) {
                            currentAudioName = line.substring(startQuote + 1, endQuote);

                            if (currentAudioName.equals("virtual-audio-capturer")) {
                                list.add(new AudioDevice("Системный звук (Virtual Capturer)", currentAudioName, false));
                                currentAudioName = null;
                            }
                        }
                    } else if (line.contains("Alternative name") && currentAudioName != null) {
                        int startQuote = line.indexOf('"');
                        int endQuote = line.lastIndexOf('"');
                        if (startQuote != -1 && endQuote > startQuote) {
                            String altName = line.substring(startQuote + 1, endQuote);
                            list.add(new AudioDevice(currentAudioName, altName, false));
                            currentAudioName = null;
                        }
                    } else if (line.contains("(video)")) {
                        currentAudioName = null;
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}