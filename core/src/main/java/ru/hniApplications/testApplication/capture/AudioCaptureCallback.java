package ru.hniApplications.testApplication.capture;

/**
 * Callback интерфейс для получения захваченных аудио-чанков.
 * Вызывается каждый раз, когда AudioCapture захватывает новый блок аудио-данных.
 *
 * @see AudioCapture
 * @see CapturedAudioChunk
 */
@FunctionalInterface
public interface AudioCaptureCallback {

    /**
     * Вызывается при захвате нового аудио-чанка.
     * <p>
     * Важные замечания:
     * <ul>
     *   <li>Метод вызывается в потоке захвата аудио — не выполнять длительных операций</li>
     *   <li>Для обработки данных использовать отдельный поток или очередь</li>
     *   <li>CapturedAudioChunk содержит копию данных — безопасно использовать асинхронно</li>
     * </ul>
     *
     * @param chunk захваченный аудио-чанк
     */
    void onAudioCaptured(CapturedAudioChunk chunk);
}
