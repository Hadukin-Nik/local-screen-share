package ru.hniApplications.testApplication;

public enum FrameType {

    I_FRAME((byte) 0x01),
    P_FRAME((byte) 0x02),
    SPS((byte) 0x03),
    PPS((byte) 0x04);

    private final byte code;

    FrameType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    
    public static FrameType fromCode(byte code) {
        for (FrameType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new ProtocolException("Unknown frame type code: 0x"
                + String.format("%02X", code));
    }
}