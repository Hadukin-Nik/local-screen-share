package ru.hniApplications.testApplication.desktop;

public enum AudioCaptureMode {
    WASAPI,           // Микрофоны через wasapi
    WASAPI_LOOPBACK,  // Динамики (системный звук) через wasapi (-loopback 1)
    DSHOW             // DShow устройства
}