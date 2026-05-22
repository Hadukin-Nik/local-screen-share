package ru.hniApplications.testApplication.codec.wasapi;

import com.sun.jna.platform.win32.Guid;

/**
 * GUID-константы Windows Core Audio API.
 * Используется для инициализации COM-объектов WASAPI.
 */
public final class WasapiGuids {

    private WasapiGuids() {}

    /** CLSID для CoCreateInstance(MMDeviceEnumerator). */
    public static final Guid.CLSID CLSID_MMDeviceEnumerator =
            new Guid.CLSID("BCDE0395-E52F-467C-8E3D-C4579291692E");

    public static final Guid.IID IID_IMMDeviceEnumerator =
            new Guid.IID("A95664D2-9614-4F35-A746-DE8DB63617E6");

    public static final Guid.IID IID_IAudioClient =
            new Guid.IID("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2");

    public static final Guid.IID IID_IAudioCaptureClient =
            new Guid.IID("C8ADBD64-E71E-48a0-A4DE-185C395CD317");

    public static final Guid.IID IID_IMMEndpoint =
            new Guid.IID("1BE09788-6894-4089-8586-9A2A6C265AC5");

    public static final Guid.IID IID_IPropertyStore =
            new Guid.IID("886d8eeb-8cf2-4446-8d02-cdba1dbdcf99");

    /** PKEY_Device_FriendlyName = {a45c254e-df1c-4efd-8020-67d146a850e0}, 14 */
    public static final byte[] PKEY_Device_FriendlyName_fmtid = guidToBytes(
            new Guid.GUID("a45c254e-df1c-4efd-8020-67d146a850e0"));
    public static final int PKEY_Device_FriendlyName_pid = 14;

    private static byte[] guidToBytes(Guid.GUID g) {
        return g.toByteArray();
    }
}
