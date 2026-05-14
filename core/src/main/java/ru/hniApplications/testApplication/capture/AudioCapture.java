package ru.hniApplications.testApplication.capture;

import java.io.IOException;

/**
 * Источник захвата аудио. Pull-модель: потребитель сам читает чанки.
 * <p>
 * Жизненный цикл:
 * <pre>
 *   AudioCapture cap = new SomeImpl(...);
 *   cap.start();
 *   while (!done) {
 *       CapturedAudioChunk chunk = cap.read();  // блокирующий
 *       if (chunk == null) break;               // EOF
 *       // обработать chunk
 *   }
 *   cap.close();
 * </pre>
 *
 * Реализации:
 * <ul>
 *   <li>{@code DshowAudioCapture} — захват через FFmpeg + dshow (legacy)</li>
 *   <li>{@code WasapiLoopbackCapture} — захват через нативный exe (Windows)</li>
 *   <li>{@code JavaSoundAudioCapture} — захват через javax.sound (микрофон)</li>
 * </ul>
 */
public interface AudioCapture extends AutoCloseable {

    /**
     * Запускает захват. До вызова {@link #read()} ничего не происходит.
     *
     * @throws CaptureException если устройство недоступно или формат не поддерживается
     */
    void start() throws CaptureException;

    /**
     * Блокирующее чтение следующего PCM-чанка.
     *
     * @return чанк с сырым PCM или {@code null} если поток завершён (EOF)
     * @throws IOException если произошла ошибка чтения
     * @throws IllegalStateException если захват не запущен
     */
    CapturedAudioChunk read() throws IOException;

    /**
     * Фактический формат захватываемого аудио.
     * Доступен только после {@link #start()}.
     *
     * @return формат или {@code null} если ещё не запущен
     */
    AudioFormat getFormat();

    /**
     * Запущен ли захват.
     */
    boolean isCapturing();

    /**
     * Останавливает захват и освобождает ресурсы.
     * Идемпотентен. После вызова {@link #read()} возвращает {@code null}.
     */
    @Override
    void close();
}
