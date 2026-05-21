package ru.hniApplications.testApplication;

public enum StreamQuality {
    LOW("Низкое (1 Мбит/с)", "1000k"),
    MEDIUM("Среднее (2.5 Мбит/с)", "2500k"),
    HIGH("Высокое (5 Мбит/с)", "5000k"),
    ULTRA("Ультра (10 Мбит/с)", "10000k");

    private final String displayName;
    private final String bitrate;

    StreamQuality(String displayName, String bitrate) {
        this.displayName = displayName;
        this.bitrate = bitrate;
    }

    public String getBitrate() {
        return bitrate;
    }

    @Override
    public String toString() {
        return displayName;
    }
}