package ru.hniApplications.testApplication.codec.wasapi;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * WAVEFORMATEX из mmreg.h.
 * Описывает формат аудио для WASAPI.
 * Если wFormatTag == WAVE_FORMAT_EXTENSIBLE — реальный формат
 * лежит в WAVEFORMATEXTENSIBLE (расширение). Для нашей задачи нам
 * достаточно знать nSamplesPerSec, nChannels, wBitsPerSample
 * и определить float vs PCM.
 */
public class WaveFormatEx extends Structure {
    public short wFormatTag;
    public short nChannels;
    public int   nSamplesPerSec;
    public int   nAvgBytesPerSec;
    public short nBlockAlign;
    public short wBitsPerSample;
    public short cbSize;

    public WaveFormatEx() { super(); }
    public WaveFormatEx(Pointer p) { super(p); read(); }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList(
                "wFormatTag", "nChannels", "nSamplesPerSec",
                "nAvgBytesPerSec", "nBlockAlign", "wBitsPerSample", "cbSize");
    }
}
