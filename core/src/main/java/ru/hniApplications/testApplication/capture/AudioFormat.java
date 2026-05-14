package ru.hniApplications.testApplication.capture;

import java.util.Objects;

/**
 * Описание формата аудио для захвата и воспроизведения.
 * Используется для согласования параметров между AudioCapture и AudioCaptureEncoder.
 */
public final class AudioFormat {

    /** Частота дискретизации (например, 44100, 48000) */
    private final int sampleRate;

    /** Количество каналов (1 = моно, 2 = стерео) */
    private final int channels;

    /** Разрядность в битах (16, 24, 32) */
    private final int bitDepth;

    /** Кодирование (PCM_SIGNED, PCM_UNSIGNED, FLOAT) */
    private final String encoding;

    /**
     * Создаёт аудио формат.
     *
     * @param sampleRate частота дискретизации в Гц (например, 44100)
     * @param channels количество каналов (1 или 2)
     * @param bitDepth разрядность в битах (16, 24, 32)
     * @param encoding тип кодирования ("PCM_SIGNED", "PCM_UNSIGNED", "IEEE_FLOAT")
     */
    public AudioFormat(int sampleRate, int channels, int bitDepth, String encoding) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive: " + sampleRate);
        }
        if (channels < 1 || channels > 8) {
            throw new IllegalArgumentException("Channels must be 1-8: " + channels);
        }
        if (bitDepth <= 0) {
            throw new IllegalArgumentException("Bit depth must be positive: " + bitDepth);
        }
        if (encoding == null || encoding.isEmpty()) {
            throw new IllegalArgumentException("Encoding must not be empty");
        }

        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitDepth = bitDepth;
        this.encoding = encoding;
    }

    /**
     * Создаёт формат по умолчанию (CD-качество, стерео, 16-bit PCM).
     */
    public static AudioFormat defaultFormat() {
        return new AudioFormat(44100, 2, 16, "PCM_SIGNED");
    }

    /**
     * Создаёт формат для телефонного качества (моно, 8kHz, 16-bit).
     */
    public static AudioFormat telephonyFormat() {
        return new AudioFormat(8000, 1, 16, "PCM_SIGNED");
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public int getBitDepth() {
        return bitDepth;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Вычисляет размер одного сэмпла в байтах.
     */
    public int getBytesPerSample() {
        return (bitDepth + 7) / 8;
    }

    /**
     * Вычисляет размер одного кадра (все каналы) в байтах.
     */
    public int getBytesPerFrame() {
        return getBytesPerSample() * channels;
    }

    /**
     * Вычисляет битрейт в байтах в секунду.
     */
    public int getBytesPerSecond() {
        return getBytesPerFrame() * sampleRate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioFormat that = (AudioFormat) o;
        return sampleRate == that.sampleRate
                && channels == that.channels
                && bitDepth == that.bitDepth
                && Objects.equals(encoding, that.encoding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sampleRate, channels, bitDepth, encoding);
    }

    @Override
    public String toString() {
        return String.format("AudioFormat{%dHz, %dch, %d-bit %s}",
                sampleRate, channels, bitDepth, encoding);
    }
}
