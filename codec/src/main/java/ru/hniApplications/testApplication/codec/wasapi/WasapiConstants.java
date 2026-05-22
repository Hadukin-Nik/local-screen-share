package ru.hniApplications.testApplication.codec.wasapi;

/**
 * Константы Windows Core Audio API.
 * Используется для инициализации и работы с WASAPI.
 */
public final class WasapiConstants {

    private WasapiConstants() {}

    // CoInitializeEx flags
    public static final int COINIT_APARTMENTTHREADED = 0x2;
    public static final int COINIT_MULTITHREADED      = 0x0;

    // CLSCTX
    public static final int CLSCTX_ALL = 0x17;

    // EDataFlow
    public static final int eRender   = 0;
    public static final int eCapture  = 1;
    public static final int eAll      = 2;

    // ERole
    public static final int eConsole       = 0;
    public static final int eMultimedia    = 1;
    public static final int eCommunications = 2;

    // DEVICE_STATE
    public static final int DEVICE_STATE_ACTIVE = 0x1;
    public static final int DEVICE_STATEMASK_ALL = 0xF;

    // AUDCLNT_SHAREMODE
    public static final int AUDCLNT_SHAREMODE_SHARED    = 0;
    public static final int AUDCLNT_SHAREMODE_EXCLUSIVE = 1;

    // AUDCLNT_STREAMFLAGS
    public static final int AUDCLNT_STREAMFLAGS_LOOPBACK     = 0x00020000;
    public static final int AUDCLNT_STREAMFLAGS_EVENTCALLBACK = 0x00040000;

    // STGM (для IPropertyStore::Open)
    public static final int STGM_READ = 0;

    // WAVE_FORMAT
    public static final int WAVE_FORMAT_PCM        = 0x0001;
    public static final int WAVE_FORMAT_IEEE_FLOAT = 0x0003;
    public static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;

    // HRESULT
    public static final int S_OK                  = 0;
    public static final int S_FALSE               = 1;
    public static final int AUDCLNT_S_BUFFER_EMPTY = 0x08890001;

    // Длительность буфера: 100ns единицы. 200мс = 2_000_000.
    public static final long REFTIMES_PER_SEC = 10_000_000L;

    // AUDCLNT_BUFFERFLAGS
    public static final int AUDCLNT_BUFFERFLAGS_DATA_DISCONTINUITY = 0x1;
    public static final int AUDCLNT_BUFFERFLAGS_SILENT             = 0x2;
    public static final int AUDCLNT_BUFFERFLAGS_TIMESTAMP_ERROR    = 0x4;
}
