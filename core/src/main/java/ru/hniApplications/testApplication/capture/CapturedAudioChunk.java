package ru.hniApplications.testApplication.capture;

import java.util.Objects;

/**
 * Модель захваченного аудио-чанка.
 * Содержит сырые PCM-данные и метаданные для воспроизведения/кодирования.
 */
public final class CapturedAudioChunk {

    /** Сырые PCM-данные (байтовый массив) */
    private final byte[] pcmData;

    /** Формат аудио (частота, каналы, разрядность) */
    private final AudioFormat format;

    /** Временная метка в наносекундах (относительно начала захвата) */
    private final long timestampNanos;

    /** Количество сэмплов в чанке */
    private final int sampleCount;

    /**
     * Создаёт аудио-чанк.
     *
     * @param pcmData сырые PCM-данные
     * @param format формат аудио
     * @param timestampNanos временная метка в наносекундах
     */
    public CapturedAudioChunk(byte[] pcmData, AudioFormat format, long timestampNanos) {
        this.pcmData = Objects.requireNonNull(pcmData, "PCM data must not be null");
        this.format = Objects.requireNonNull(format, "Audio format must not be null");
        this.timestampNanos = timestampNanos;

        // Вычисляем количество сэмплов на основе размера данных и формата
        int bytesPerSample = format.getBytesPerSample();
        int bytesPerFrame = format.getBytesPerFrame();
        this.sampleCount = pcmData.length / bytesPerFrame;
    }

    /**
     * Возвращает сырые PCM-данные.
     * Копия массива для безопасности.
     */
    public byte[] getPcmData() {
        return pcmData.clone();
    }

    /**
     * Возвращает прямой доступ к PCM-данным (без копирования).
     * Использовать с осторожностью — не модифицировать массив.
     */
    public byte[] getPcmDataDirect() {
        return pcmData;
    }

    public AudioFormat getFormat() {
        return format;
    }

    public long getTimestampNanos() {
        return timestampNanos;
    }

    /**
     * Возвращает временную метку в миллисекундах.
     */
    public long getTimestampMillis() {
        return timestampNanos / 1_000_000;
    }

    /**
     * Возвращает длительность чанка в миллисекундах.
     */
    public long getDurationMillis() {
        return (sampleCount * 1000L) / format.getSampleRate();
    }

    public int getSampleCount() {
        return sampleCount;
    }

    /**
     * Возвращает размер данных в байтах.
     */
    public int getDataSize() {
        return pcmData.length;
    }

    @Override
    public String toString() {
        return String.format("CapturedAudioChunk{%d bytes, %d samples, %dms, %s}",
                pcmData.length, sampleCount, getDurationMillis(), format);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CapturedAudioChunk that = (CapturedAudioChunk) o;
        return timestampNanos == that.timestampNanos
                && sampleCount == that.sampleCount
                && Objects.equals(format, that.format);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, timestampNanos, sampleCount);
    }
}
