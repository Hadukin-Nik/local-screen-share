package ru.hniApplications.testApplication.codec;

import ru.hniApplications.testApplication.FFmpegLocator;
import ru.hniApplications.testApplication.capture.AudioDevice;
import ru.hniApplications.testApplication.capture.AudioDeviceType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Утилита для перечисления доступных аудиоустройств через FFmpeg.
 */
public class AudioDeviceEnumerator {

    /**
     * Возвращает список доступных аудиоустройств.
     *
     * @return список аудиоустройств
     */
    public static List<AudioDevice> list() {
        List<AudioDevice> list = new ArrayList<>();
        String ffmpegPath = FFmpegLocator.getPath();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath, "-list_devices", "true", "-f", "dshow", "-i", "dummy"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {

                String line;
                String currentAudioName = null;
                String currentAudioAltName = null;

                while ((line = br.readLine()) != null) {
                    if (line.contains("(audio)")) {
                        int startQuote = line.indexOf('"');
                        int endQuote = line.indexOf('"', startQuote + 1);
                        if (startQuote != -1 && endQuote > startQuote) {
                            currentAudioName = line.substring(startQuote + 1, endQuote);
                            currentAudioAltName = null;
                        }
                    } else if (line.contains("Alternative name") && currentAudioName != null) {
                        int startQuote = line.indexOf('"');
                        int endQuote = line.lastIndexOf('"');
                        if (startQuote != -1 && endQuote > startQuote) {
                            currentAudioAltName = line.substring(startQuote + 1, endQuote);
                        }
                    } else if (line.contains("(video)")) {
                        // Сохраняем предыдущее устройство перед сбросом
                        if (currentAudioName != null) {
                            String ffmpegArg = currentAudioAltName != null ? currentAudioAltName : currentAudioName;
                            AudioDeviceType type = "virtual-audio-capturer".equals(currentAudioName)
                                    ? AudioDeviceType.SYSTEM_LOOPBACK
                                    : AudioDeviceType.MICROPHONE;
                            String displayName = "virtual-audio-capturer".equals(currentAudioName)
                                    ? "Системный звук (Virtual Capturer)"
                                    : currentAudioName;
                            list.add(new AudioDevice(currentAudioName, displayName, ffmpegArg, type));
                        }
                        currentAudioName = null;
                        currentAudioAltName = null;
                    }
                }

                // Добавляем последнее устройство если осталось
                if (currentAudioName != null) {
                    String ffmpegArg = currentAudioAltName != null ? currentAudioAltName : currentAudioName;
                    AudioDeviceType type = "virtual-audio-capturer".equals(currentAudioName)
                            ? AudioDeviceType.SYSTEM_LOOPBACK
                            : AudioDeviceType.MICROPHONE;
                    String displayName = "virtual-audio-capturer".equals(currentAudioName)
                            ? "Системный звук (Virtual Capturer)"
                            : currentAudioName;
                    list.add(new AudioDevice(currentAudioName, displayName, ffmpegArg, type));
                }
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
