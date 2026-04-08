package ru.hniApplications.testApplication;

// ru.hniApplications.testApplication.FrameType
public enum FrameType {
    I_FRAME((byte) 0x01),
    P_FRAME((byte) 0x02),
    SPS((byte) 0x03),
    PPS((byte) 0x04),
    // Добавляем аудио!
    AUDIO((byte) 0x05);

    private final byte code;
    FrameType(byte code) { this.code = code; }
    public byte getCode() { return code; }

    public static FrameType fromCode(byte code) {
        for (FrameType type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Unknown frame type: " + code);
    }
}