package ru.hniApplications.testApplication.capture;

/**
 * Представление аудиоустройства для захвата звука.
 */
public class AudioDevice {
    private final String id;
    private final String displayName;
    private final String ffmpegArg;
    private final AudioDeviceType type;

    /**
     * Конструктор аудиоустройства.
     *
     * @param id уникальный идентификатор устройства
     * @param displayName отображаемое имя для пользователя
     * @param ffmpegArg аргумент для FFmpeg (-i audio=...)
     * @param type тип устройства (микрофон или системный звук)
     */
    public AudioDevice(String id, String displayName, String ffmpegArg, AudioDeviceType type) {
        this.id = id;
        this.displayName = displayName;
        this.ffmpegArg = ffmpegArg;
        this.type = type != null ? type : AudioDeviceType.UNKNOWN;
    }

    /**
     * @return уникальный идентификатор устройства
     */
    public String getId() {
        return id;
    }

    /**
     * @return отображаемое имя для пользователя
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return аргумент для подстановки в команду FFmpeg
     */
    public String getFfmpegArg() {
        return ffmpegArg;
    }

    /**
     * @return тип устройства
     */
    public AudioDeviceType getType() {
        return type;
    }

    /**
     * Проверяет, является ли устройство системным звуком (loopback).
     *
     * @return true если это системный звук
     */
    public boolean isLoopback() {
        return type == AudioDeviceType.SYSTEM_LOOPBACK;
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioDevice that = (AudioDevice) o;
        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
