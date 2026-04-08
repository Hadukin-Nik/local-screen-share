package ru.hniApplications.testApplication.desktop;

import ru.hniApplications.testApplication.ScreenCaptureEncoder;

import java.io.*;

/**
 * Обёртка над Process, реализующая тот же интерфейс
 * что и ScreenCaptureEncoder.
 */
public class ScreenCaptureEncoderWrapper
        extends ScreenCaptureEncoder {

    private final Process process;
    private final InputStream output;

    public ScreenCaptureEncoderWrapper(Process process)
            throws IOException {
        // Вызываем суперконструктор без аудио —
        // но мы его не используем, поэтому нужен хак.
        // Лучше: извлечь интерфейс.
        super(1, 1, 1); // dummy — будет убит сразу

        // Убиваем процесс из суперконструктора
        // (он запустил ненужный ffmpeg)
        try {
            var f = ScreenCaptureEncoder.class
                    .getDeclaredField("process");
            f.setAccessible(true);
            Process dummyProc = (Process) f.get(this);
            dummyProc.destroyForcibly();
        } catch (Exception e) {
            System.err.println("[Wrapper] Не удалось убить "
                    + "dummy процесс: " + e.getMessage());
        }

        this.process = process;
        this.output = new BufferedInputStream(
                process.getInputStream(), 256 * 1024);

        // stderr
        Thread errThread = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(
                            process.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.err.println("[ffmpeg-dual] " + line);
                }
            } catch (Exception ignored) {
            }
        }, "ffmpeg-dual-stderr");
        errThread.setDaemon(true);
        errThread.start();
    }

    @Override
    public byte[] readChunk() throws IOException {
        byte[] buf = new byte[16 * 1024];
        int read = output.read(buf);
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