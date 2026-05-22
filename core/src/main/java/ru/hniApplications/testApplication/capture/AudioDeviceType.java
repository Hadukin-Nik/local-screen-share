package ru.hniApplications.testApplication.capture;

/**
 * Тип аудиоустройства для захвата.
 */
public enum AudioDeviceType {
    /** Микрофон/внешний вход */
    MICROPHONE,
    /** Системный звук (loopback) */
    SYSTEM_LOOPBACK,
    /** Неизвестный тип */
    UNKNOWN
}
