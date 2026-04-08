package ru.hniApplications.testApplication;

import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.util.concurrent.*;

public class StreamDecoder implements AutoCloseable {

    private final int width;
    private final int height;

    private Process videoProcess;
    private Process audioProcess;
    private OutputStream videoInput;
    private OutputStream audioInput;

    private final ArrayBlockingQueue<BufferedImage> frameQueue =
            new ArrayBlockingQueue<>(60);

    private volatile boolean running = true;

    private Thread videoReadThread;
    private Thread audioReadThread;
    private Thread videoErrThread;
    private Thread audioErrThread;

    // ── Управление громкостью ──
    private volatile float volume = 1.0f;
    private SourceDataLine audioLine;

    public StreamDecoder(int width, int height) {
        this.width = width;
        this.height = height;

        startVideoProcess();
        startAudioProcess();
    }

    private void startVideoProcess() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel", "error",
                    "-f", "mpegts",
                    "-i", "pipe:0",
                    "-map", "0:v:0",
                    "-c:v", "rawvideo",
                    "-pix_fmt", "bgr24",
                    "-s", width + "x" + height,
                    "-f", "rawvideo",
                    "pipe:1"
            );
            pb.redirectErrorStream(false);
            videoProcess = pb.start();
            videoInput = new BufferedOutputStream(videoProcess.getOutputStream(), 256 * 1024);

            // Чтение декодированных кадров
            videoReadThread = new Thread(() -> {
                try {
                    InputStream in = new BufferedInputStream(
                            videoProcess.getInputStream(), 512 * 1024);
                    int frameSize = width * height * 3;
                    byte[] frameBuf = new byte[frameSize];

                    while (running) {
                        int offset = 0;
                        while (offset < frameSize) {
                            int read = in.read(frameBuf, offset, frameSize - offset);
                            if (read == -1) return;
                            offset += read;
                        }

                        BufferedImage img = new BufferedImage(
                                width, height, BufferedImage.TYPE_3BYTE_BGR);
                        byte[] imgData = ((DataBufferByte) img.getRaster()
                                .getDataBuffer()).getData();
                        System.arraycopy(frameBuf, 0, imgData, 0, frameSize);

                        // Если очередь полна — выбрасываем старый кадр
                        if (!frameQueue.offer(img)) {
                            frameQueue.poll();
                            frameQueue.offer(img);
                        }
                    }
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[VideoDecoder] Ошибка чтения: "
                                + e.getMessage());
                    }
                }
            }, "video-decoder-read");
            videoReadThread.setDaemon(true);
            videoReadThread.start();

            videoErrThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(videoProcess.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.err.println("[ffmpeg-video] " + line);
                    }
                } catch (Exception ignored) {}
            }, "ffmpeg-video-stderr");
            videoErrThread.setDaemon(true);
            videoErrThread.start();

        } catch (IOException e) {
            throw new RuntimeException("Не удалось запустить видеодекодер", e);
        }
    }

    private void startAudioProcess() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel", "error",
                    "-f", "mpegts",
                    "-i", "pipe:0",
                    "-map", "0:a:0?",          // '?' — не падать если аудио нет
                    "-c:a", "pcm_s16le",
                    "-ar", "44100",
                    "-ac", "2",
                    "-f", "s16le",
                    "pipe:1"
            );
            pb.redirectErrorStream(false);
            audioProcess = pb.start();
            audioInput = new BufferedOutputStream(audioProcess.getOutputStream(), 256 * 1024);

            // Настраиваем javax.sound для воспроизведения
            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    44100,    // sample rate
                    16,       // bits per sample
                    2,        // channels
                    4,        // frame size (2 channels * 2 bytes)
                    44100,    // frame rate
                    false     // little-endian
            );

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(format, 44100 * 4); // ~1 секунда буфера
            audioLine.start();

            // Чтение PCM-данных и воспроизведение
            audioReadThread = new Thread(() -> {
                try {
                    InputStream in = new BufferedInputStream(
                            audioProcess.getInputStream(), 64 * 1024);
                    byte[] buf = new byte[4096];

                    while (running) {
                        int read = in.read(buf);
                        if (read == -1) break;

                        // Применяем громкость
                        if (volume < 0.999f) {
                            applyVolume(buf, read);
                        }

                        audioLine.write(buf, 0, read);
                    }
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[AudioDecoder] Ошибка чтения: "
                                + e.getMessage());
                    }
                }
            }, "audio-decoder-read");
            audioReadThread.setDaemon(true);
            audioReadThread.start();

            Thread audioErrThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(audioProcess.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.err.println("[ffmpeg-audio] " + line);
                    }
                } catch (Exception ignored) {}
            }, "ffmpeg-audio-stderr");
            audioErrThread.setDaemon(true);
            audioErrThread.start();

        } catch (Exception e) {
            System.err.println("[AudioDecoder] Не удалось запустить: " + e.getMessage());
            // Аудио не критично — продолжаем без него
        }
    }

    /**
     * Применяет громкость к PCM s16le данным (in-place).
     */
    private void applyVolume(byte[] buf, int length) {
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
            sample = (short) Math.max(-32768,
                    Math.min(32767, (int) (sample * volume)));
            buf[i] = (byte) (sample & 0xFF);
            buf[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
    }

    /**
     * Подаём MPEG-TS чанк. Пишем в оба процесса одновременно.
     */
    public void feedData(byte[] data) {
        if (data == null || data.length == 0 || !running) return;

        try {
            if (videoInput != null) {
                videoInput.write(data);
                videoInput.flush();
            }
        } catch (IOException e) {
            System.err.println("[StreamDecoder] Ошибка записи в видео: "
                    + e.getMessage());
        }

        try {
            if (audioInput != null) {
                audioInput.write(data);
                audioInput.flush();
            }
        } catch (IOException e) {
            // Аудио может не быть — не критично
        }
    }

    public BufferedImage getLatestFrame() {
        BufferedImage latest = null;
        BufferedImage frame;
        while ((frame = frameQueue.poll()) != null) {
            latest = frame;
        }
        return latest;
    }

    public BufferedImage waitForFrame(long timeoutMs) throws InterruptedException {
        return frameQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public int drainFrames(java.util.Collection<BufferedImage> dest) {
        return frameQueue.drainTo(dest);
    }

    public int getQueuedFrameCount() {
        return frameQueue.size();
    }

    public void setVolume(float vol) {
        this.volume = Math.max(0f, Math.min(2f, vol));

        // Также пробуем через javax.sound FloatControl
        if (audioLine != null && audioLine.isControlSupported(
                FloatControl.Type.MASTER_GAIN)) {
            FloatControl gc = (FloatControl) audioLine.getControl(
                    FloatControl.Type.MASTER_GAIN);
            float db = (float) (20.0 * Math.log10(Math.max(0.0001, this.volume)));
            db = Math.max(gc.getMinimum(), Math.min(gc.getMaximum(), db));
            gc.setValue(db);
        }
    }

    public float getVolume() {
        return volume;
    }

    public void setMuted(boolean muted) {
        if (audioLine != null && audioLine.isControlSupported(
                BooleanControl.Type.MUTE)) {
            BooleanControl mc = (BooleanControl) audioLine.getControl(
                    BooleanControl.Type.MUTE);
            mc.setValue(muted);
        }
    }

    public int getOutputWidth()  { return width; }
    public int getOutputHeight() { return height; }

    @Override
    public void close() {
        running = false;

        closeQuietly(videoInput);
        closeQuietly(audioInput);

        if (audioLine != null) {
            audioLine.stop();
            audioLine.close();
        }

        if (videoProcess != null) videoProcess.destroyForcibly();
        if (audioProcess != null) audioProcess.destroyForcibly();

        interruptQuietly(videoReadThread);
        interruptQuietly(audioReadThread);
    }

    private void closeQuietly(OutputStream os) {
        try { if (os != null) os.close(); }
        catch (IOException ignored) {}
    }

    private void interruptQuietly(Thread t) {
        if (t != null) {
            t.interrupt();
            try { t.join(1000); }
            catch (InterruptedException ignored) {}
        }
    }
}