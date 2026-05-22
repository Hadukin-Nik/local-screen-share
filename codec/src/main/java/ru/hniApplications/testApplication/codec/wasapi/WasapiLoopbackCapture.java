package ru.hniApplications.testApplication.codec.wasapi;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;

/**
 * Захват системного звука через WASAPI Loopback на Windows.
 * Работает по принципу OBS Studio — захватывает звук из render-устройств
 * без необходимости установки сторонних драйверов.
 *
 * Использование:
 * 1. WasapiDeviceEnumerator.list() — получить список устройств
 * 2. WasapiLoopbackCapture(deviceId) — создать захватчик
 * 3. start() — запустить захват
 * 4. getOutputStream() — получить InputStream с PCM-данными
 * 5. close() — остановить и освободить ресурсы
 */
public class WasapiLoopbackCapture implements AutoCloseable {

    private final String deviceId;
    private PipedInputStream pipedIn;
    private PipedOutputStream pipedOut;
    private Thread captureThread;
    private volatile boolean stopping = false;
    private Format format;
    private final CountDownLatch formatLatch = new CountDownLatch(1);
    private volatile Throwable initError = null;

    /**
     * Формат аудио потока.
     */
    public static class Format {
        public final int sampleRate;
        public final int channels;
        public final int bitsPerSample;
        public final boolean isFloat;

        public Format(int sampleRate, int channels, int bitsPerSample, boolean isFloat) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bitsPerSample = bitsPerSample;
            this.isFloat = isFloat;
        }

        public int getSampleRate() { return sampleRate; }
        public int getChannels() { return channels; }
        public int getBitsPerSample() { return bitsPerSample; }
        public boolean isFloat() { return isFloat; }
    }

    /**
     * Создаёт захватчик для указанного устройства.
     *
     * @param deviceId ID устройства вывода (из WasapiDeviceEnumerator)
     */
    public WasapiLoopbackCapture(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * Запускает фоновый поток захвата звука.
     * Блокируется до инициализации WASAPI или возникновения ошибки.
     */
    public void start() {
        // Создаём pipe с большим буфером (1 МБ)
        try {
            pipedOut = new PipedOutputStream();
            pipedIn = new PipedInputStream(pipedOut, 1024 * 1024);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create pipe", e);
        }

        captureThread = new Thread(this::runCaptureLoop, "wasapi-loopback-capture");
        captureThread.setDaemon(true);
        captureThread.start();

        // Ждём инициализации формата или ошибки
        try {
            if (!formatLatch.await(5000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("WASAPI initialization timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("WASAPI initialization interrupted", e);
        }

        if (initError != null) {
            throw new RuntimeException("WASAPI initialization failed", initError);
        }
    }

    /**
     * Возвращает InputStream с RAW PCM-данными.
     * Формат данных определяется через getFormat().
     *
     * @return InputStream с PCM-данными для передачи в ffmpeg
     */
    public InputStream getOutputStream() {
        return pipedIn;
    }

    /**
     * Возвращает формат аудио потока.
     *
     * @return формат (sample rate, channels, bits per sample, isFloat)
     */
    public Format getFormat() {
        return format;
    }

    /**
     * Основной цикл захвата.
     * Инициализирует WASAPI, читает аудио и пишет в pipe.
     */
    private void runCaptureLoop() {
        ComUtil.initThread();
        Pointer pDevice = null;
        Pointer pAudioClient = null;
        Pointer pCaptureClient = null;
        WaveFormatEx mixFormat = null;

        try {
            // CoCreateInstance(MMDeviceEnumerator)
            PointerByReference enumeratorPtr = new PointerByReference();
            WinNT.HRESULT hr = Ole32.INSTANCE.CoCreateInstance(
                    WasapiGuids.CLSID_MMDeviceEnumerator,
                    null,
                    WasapiConstants.CLSCTX_ALL,
                    WasapiGuids.IID_IMMDeviceEnumerator,
                    enumeratorPtr);
            ComUtil.checkHr(hr.intValue(), "CoCreateInstance(MMDeviceEnumerator)");
            Pointer pEnum = enumeratorPtr.getValue();

            try {
                // GetDevice(deviceId)
                PointerByReference pDev = new PointerByReference();
                // IMMDeviceEnumerator::GetDevice — vtable index 5
                int rc = ComUtil.callCom(pEnum, 5, deviceId, pDev);
                ComUtil.checkHr(rc, "GetDevice(" + deviceId + ")");
                pDevice = pDev.getValue();

                // Activate(IAudioClient)
                PointerByReference pClient = new PointerByReference();
                // IMMDevice::Activate — vtable index 3
                rc = ComUtil.callCom(pDevice, 3,
                        WasapiGuids.IID_IAudioClient,
                        WasapiConstants.CLSCTX_ALL,
                        null,
                        pClient);
                ComUtil.checkHr(rc, "Activate(IAudioClient)");
                pAudioClient = pClient.getValue();

                // GetMixFormat
                PointerByReference pFormat = new PointerByReference();
                // IAudioClient::GetMixFormat — vtable index 8
                rc = ComUtil.callCom(pAudioClient, 8, pFormat);
                ComUtil.checkHr(rc, "GetMixFormat");

                // Читаем WAVEFORMATEX
                mixFormat = new WaveFormatEx(pFormat.getValue());
                boolean isFloat = (mixFormat.wFormatTag == WasapiConstants.WAVE_FORMAT_IEEE_FLOAT) ||
                        (mixFormat.wFormatTag == WasapiConstants.WAVE_FORMAT_EXTENSIBLE && mixFormat.cbSize >= 22);
                int bitsPerSample = mixFormat.wBitsPerSample;
                if (bitsPerSample == 0) bitsPerSample = 32; // для float по умолчанию

                format = new Format(
                        mixFormat.nSamplesPerSec,
                        mixFormat.nChannels,
                        bitsPerSample,
                        isFloat);

                // Инициализация AudioClient для loopback
                // IAudioClient::Initialize — vtable index 3
                // hnsBufferDuration = 200ms = 2_000_000 * 100ns
                long hnsBufferDuration = 200 * WasapiConstants.REFTIMES_PER_SEC / 1000;
                rc = ComUtil.callCom(pAudioClient, 3,
                        WasapiConstants.AUDCLNT_SHAREMODE_SHARED,
                        WasapiConstants.AUDCLNT_STREAMFLAGS_LOOPBACK,
                        hnsBufferDuration,
                        0L,  // hnsPeriodicity
                        pFormat.getValue(),
                        null);  // AudioSessionGuid
                ComUtil.checkHr(rc, "Initialize(LOOPBACK)");

                // GetService(IAudioCaptureClient)
                PointerByReference pCapture = new PointerByReference();
                // IAudioClient::GetService — vtable index 14
                rc = ComUtil.callCom(pAudioClient, 14,
                        WasapiGuids.IID_IAudioCaptureClient,
                        pCapture);
                ComUtil.checkHr(rc, "GetService(IAudioCaptureClient)");
                pCaptureClient = pCapture.getValue();

                // Start
                // IAudioClient::Start — vtable index 10
                rc = ComUtil.callCom(pAudioClient, 10);
                ComUtil.checkHr(rc, "Start");

                // Сигналим что формат готов
                formatLatch.countDown();

                // Цикл захвата
                int blockAlign = mixFormat.nBlockAlign;
                if (blockAlign == 0) {
                    blockAlign = mixFormat.nChannels * mixFormat.wBitsPerSample / 8;
                }
                if (blockAlign == 0) blockAlign = 4; // fallback

                byte[] silenceBuffer = new byte[blockAlign * 1024]; // буфер тишины

                while (!stopping) {
                    // GetNextPacketSize — vtable index 5 у IAudioCaptureClient
                    IntByReference numFrames = new IntByReference();
                    rc = ComUtil.callCom(pCaptureClient, 5, numFrames);
                    if (rc != WasapiConstants.S_OK) {
                        Thread.sleep(10);
                        continue;
                    }

                    if (numFrames.getValue() == 0) {
                        Thread.sleep(10);
                        continue;
                    }

                    // GetBuffer — vtable index 3
                    PointerByReference pData = new PointerByReference();
                    IntByReference framesRead = new IntByReference();
                    IntByReference flags = new IntByReference();

                    rc = ComUtil.callCom(pCaptureClient, 3,
                            pData, framesRead, flags, null, null);
                    if (rc != WasapiConstants.S_OK) {
                        Thread.sleep(10);
                        continue;
                    }

                    int frames = framesRead.getValue();
                    int bytesToRead = frames * blockAlign;

                    byte[] audioData;
                    if ((flags.getValue() & WasapiConstants.AUDCLNT_BUFFERFLAGS_SILENT) != 0) {
                        // Тихий пакет — заполняем нулями
                        if (bytesToRead > silenceBuffer.length) {
                            silenceBuffer = new byte[bytesToRead];
                        }
                        audioData = silenceBuffer;
                        java.util.Arrays.fill(audioData, 0, bytesToRead, (byte) 0);
                    } else {
                        // Читаем данные
                        audioData = pData.getValue().getByteArray(0, bytesToRead);
                    }

                    // ReleaseBuffer — vtable index 4
                    ComUtil.callCom(pCaptureClient, 4, frames);

                    // Пишем в pipe
                    synchronized (pipedOut) {
                        try {
                            pipedOut.write(audioData, 0, bytesToRead);
                            pipedOut.flush();
                        } catch (IOException e) {
                            // Pipe закрыт — выходим
                            break;
                        }
                    }
                }

            } finally {
                if (pCaptureClient != null) ComUtil.release(pCaptureClient);
                if (pAudioClient != null) {
                    // Stop перед release
                    ComUtil.callCom(pAudioClient, 11);  // Stop
                    ComUtil.release(pAudioClient);
                }
                if (pDevice != null) ComUtil.release(pDevice);
                if (pEnum != null) ComUtil.release(pEnum);
            }

        } catch (Throwable t) {
            initError = t;
            formatLatch.countDown();
            System.err.println("[WasapiLoopbackCapture] Error: " + t.getMessage());
            t.printStackTrace();
        } finally {
            // Освобождаем mixFormat если был выделен
            if (mixFormat != null && mixFormat.getPointer() != null) {
                // GetMixFormat возвращает выделенную память — нужно освободить
                Ole32.INSTANCE.CoTaskMemFree(mixFormat.getPointer());
            }
            ComUtil.uninitThread();
        }
    }

    /**
     * Останавливает захват и освобождает ресурсы.
     */
    @Override
    public void close() {
        stopping = true;

        if (captureThread != null) {
            try {
                captureThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (pipedOut != null) {
            try {
                pipedOut.close();
            } catch (IOException e) {
                // ignore
            }
        }
        if (pipedIn != null) {
            try {
                pipedIn.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Standalone-тест для проверки захвата.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== WASAPI Loopback Test ===");

        var devices = WasapiDeviceEnumerator.list();
        System.out.println("Found " + devices.size() + " render devices:");
        for (var d : devices) {
            System.out.println("  - " + d.getDisplayName() + " [" + d.getId() + "]");
        }

        if (devices.isEmpty()) {
            System.out.println("No render devices found.");
            return;
        }

        var dev = devices.get(0);
        String id = dev.getFfmpegArg().substring("wasapi-loopback://".length());

        System.out.println("\nCapturing from: " + dev.getDisplayName());
        try (var cap = new WasapiLoopbackCapture(id)) {
            cap.start();
            var fmt = cap.getFormat();
            System.out.println("Format: " + fmt.sampleRate + "Hz, "
                    + fmt.channels + "ch, "
                    + fmt.bitsPerSample + "bit, float=" + fmt.isFloat);

            try (var out = new java.io.FileOutputStream("test.raw")) {
                byte[] buf = new byte[8192];
                var in = cap.getOutputStream();
                long total = 0;
                long start = System.currentTimeMillis();
                System.out.println("Capturing for 5 seconds...");
                while (System.currentTimeMillis() - start < 5000) {
                    int n = in.read(buf);
                    if (n > 0) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                }
                System.out.println("Captured: " + total + " bytes");
            }
        }

        System.out.println("\nTo play back, run:");
        System.out.println("  ffplay -f f32le -ar 48000 -ch_layout stereo test.raw");
        System.out.println("(adjust -ar and -ch_layout based on captured format)");
    }
}
