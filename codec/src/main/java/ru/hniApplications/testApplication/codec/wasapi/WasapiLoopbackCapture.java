package ru.hniApplications.testApplication.codec.wasapi;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WasapiLoopbackCapture implements AutoCloseable {

    private final String deviceId;
    private PipedInputStream pipedIn;
    private PipedOutputStream pipedOut;
    private Thread captureThread;
    private volatile boolean stopping = false;
    private Format format;
    private final CountDownLatch formatLatch = new CountDownLatch(1);
    private volatile Throwable initError = null;

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

    public WasapiLoopbackCapture(String deviceId) {
        this.deviceId = deviceId;
    }

    public void start() {
        try {
            pipedOut = new PipedOutputStream();
            pipedIn = new PipedInputStream(pipedOut, 64 * 1024);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create pipe", e);
        }
        captureThread = new Thread(this::runCaptureLoop, "wasapi-loopback-capture");
        captureThread.setDaemon(true);
        captureThread.start();
        try {
            if (!formatLatch.await(5000, TimeUnit.MILLISECONDS)) {
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

    public InputStream getOutputStream() { return pipedIn; }
    public Format getFormat() { return format; }

    private void runCaptureLoop() {
        ComUtil.initThread();
        Pointer pEnum = null;
        Pointer pDevice = null;
        Pointer pAudioClient = null;
        Pointer pCaptureClient = null;
        Pointer pMixFormatRaw = null;
        try {
            // 1. CoCreateInstance(MMDeviceEnumerator)
            PointerByReference enumeratorPtr = new PointerByReference();
            WinNT.HRESULT hr = Ole32.INSTANCE.CoCreateInstance(
                    WasapiGuids.CLSID_MMDeviceEnumerator,
                    null,
                    WasapiConstants.CLSCTX_ALL,
                    WasapiGuids.IID_IMMDeviceEnumerator,
                    enumeratorPtr);
            ComUtil.checkHr(hr.intValue(), "CoCreateInstance(MMDeviceEnumerator)");
            pEnum = enumeratorPtr.getValue();

            // 2. GetDevice(deviceId) — LPCWSTR передаём как WString
            PointerByReference pDev = new PointerByReference();
            WString wDeviceId = new WString(deviceId);
            int rc = ComUtil.callCom(pEnum, 5, wDeviceId, pDev);
            ComUtil.checkHr(rc, "GetDevice(" + deviceId + ")");
            pDevice = pDev.getValue();

            // Можно уже освободить enum
            ComUtil.release(pEnum);
            pEnum = null;

            // 3. Activate(IAudioClient)
            PointerByReference pClient = new PointerByReference();
            rc = ComUtil.callCom(pDevice, 3,
                    WasapiGuids.IID_IAudioClient,
                    WasapiConstants.CLSCTX_ALL,
                    null,
                    pClient);
            ComUtil.checkHr(rc, "Activate(IAudioClient)");
            pAudioClient = pClient.getValue();

            // 4. GetMixFormat
            PointerByReference pFormat = new PointerByReference();
            rc = ComUtil.callCom(pAudioClient, 8, pFormat);
            ComUtil.checkHr(rc, "GetMixFormat");
            pMixFormatRaw = pFormat.getValue();

            WaveFormatEx mixFormat = new WaveFormatEx(pMixFormatRaw);
            int bitsPerSample = mixFormat.wBitsPerSample;
            if (bitsPerSample == 0) bitsPerSample = 32;

// Правильное определение float vs PCM:
// - WAVE_FORMAT_IEEE_FLOAT (0x0003) — явно float
// - WAVE_FORMAT_PCM (0x0001) — явно PCM
// - WAVE_FORMAT_EXTENSIBLE (0xFFFE) — нужно смотреть SubFormat,
//   но на практике WASAPI shared mix format на современных Windows
//   ВСЕГДА float32 при 32-битной глубине.
            boolean isFloat;
            if (mixFormat.wFormatTag == WasapiConstants.WAVE_FORMAT_IEEE_FLOAT) {
                isFloat = true;
            } else if (mixFormat.wFormatTag == WasapiConstants.WAVE_FORMAT_PCM) {
                isFloat = false;
            } else if (mixFormat.wFormatTag == WasapiConstants.WAVE_FORMAT_EXTENSIBLE) {
                // Читаем SubFormat GUID из WAVEFORMATEXTENSIBLE.
                // Layout: WAVEFORMATEX (18 байт) + Samples (2) + dwChannelMask (4) + SubFormat (16)
                // Смещение SubFormat от начала структуры = 18 + 2 + 4 = 24
                Pointer raw = pMixFormatRaw;
                // Первые 4 байта SubFormat = Data1 поля GUID
                int subFormatData1 = raw.getInt(24);
                // KSDATAFORMAT_SUBTYPE_IEEE_FLOAT начинается с 0x00000003
                // KSDATAFORMAT_SUBTYPE_PCM        начинается с 0x00000001
                if (subFormatData1 == 0x00000003) {
                    isFloat = true;
                } else if (subFormatData1 == 0x00000001) {
                    isFloat = false;
                } else {
                    // Fallback: при 32 битах в WASAPI shared практически всегда float
                    isFloat = (bitsPerSample == 32);
                }
            } else {
                // Неизвестный формат — предполагаем float для 32 бит
                isFloat = (bitsPerSample == 32);
            }

            int blockAlign = mixFormat.nBlockAlign;
            if (blockAlign == 0) blockAlign = mixFormat.nChannels * bitsPerSample / 8;
            if (blockAlign == 0) blockAlign = 4;

            format = new Format(
                    mixFormat.nSamplesPerSec,
                    mixFormat.nChannels,
                    bitsPerSample,
                    isFloat);

            System.out.println("[WASAPI] device=" + deviceId);
            System.out.println("[WASAPI] format: sr=" + format.sampleRate
                    + " ch=" + format.channels
                    + " bits=" + format.bitsPerSample
                    + " float=" + format.isFloat
                    + " blockAlign=" + blockAlign);

            // 5. Initialize(LOOPBACK)
            long hnsBufferDuration = 40 * WasapiConstants.REFTIMES_PER_SEC / 1000;
            rc = ComUtil.callCom(pAudioClient, 3,
                    WasapiConstants.AUDCLNT_SHAREMODE_SHARED,
                    WasapiConstants.AUDCLNT_STREAMFLAGS_LOOPBACK,
                    hnsBufferDuration,
                    0L,
                    pMixFormatRaw,
                    null);
            ComUtil.checkHr(rc, "Initialize(LOOPBACK)");

            // mixFormat больше не нужен — освободим сразу
            Ole32.INSTANCE.CoTaskMemFree(pMixFormatRaw);
            pMixFormatRaw = null;

            // 6. GetService(IAudioCaptureClient)
            PointerByReference pCapture = new PointerByReference();
            rc = ComUtil.callCom(pAudioClient, 14,
                    WasapiGuids.IID_IAudioCaptureClient,
                    pCapture);
            ComUtil.checkHr(rc, "GetService(IAudioCaptureClient)");
            pCaptureClient = pCapture.getValue();

            // 7. Start
            rc = ComUtil.callCom(pAudioClient, 10);
            ComUtil.checkHr(rc, "Start");

            formatLatch.countDown();

            // 8. Цикл захвата
            byte[] silenceBuffer = new byte[blockAlign * 1024];

            while (!stopping) {
                IntByReference numFrames = new IntByReference();
                rc = ComUtil.callCom(pCaptureClient, 5, numFrames);
                if (rc != WasapiConstants.S_OK) {
                    Thread.sleep(1);
                    continue;
                }
                if (numFrames.getValue() == 0) {
                    Thread.sleep(1);
                    continue;
                }

                PointerByReference pData = new PointerByReference();
                IntByReference framesRead = new IntByReference();
                IntByReference flags = new IntByReference();
                LongByReference devicePosition = new LongByReference();
                LongByReference qpcPosition = new LongByReference();

                rc = ComUtil.callCom(pCaptureClient, 3,
                        pData, framesRead, flags, devicePosition, qpcPosition);
                if (rc != WasapiConstants.S_OK) {
                    Thread.sleep(1);
                    continue;
                }

                int frames = framesRead.getValue();
                int bytesToRead = frames * blockAlign;
                byte[] audioData;

                if ((flags.getValue() & WasapiConstants.AUDCLNT_BUFFERFLAGS_SILENT) != 0) {
                    if (bytesToRead > silenceBuffer.length) {
                        silenceBuffer = new byte[bytesToRead];
                    } else {
                        java.util.Arrays.fill(silenceBuffer, 0, bytesToRead, (byte) 0);
                    }
                    audioData = silenceBuffer;
                } else {
                    Pointer dataPtr = pData.getValue();
                    if (dataPtr == null || bytesToRead <= 0) {
                        ComUtil.callCom(pCaptureClient, 4, frames);
                        continue;
                    }
                    audioData = dataPtr.getByteArray(0, bytesToRead);
                }

                ComUtil.callCom(pCaptureClient, 4, frames);

                try {
                    pipedOut.write(audioData, 0, bytesToRead);
                } catch (IOException e) {
                    break;
                }
            }

        } catch (Throwable t) {
            initError = t;
            formatLatch.countDown();
            System.err.println("[WasapiLoopbackCapture] Error: " + t.getMessage());
            t.printStackTrace();
        } finally {
            if (pCaptureClient != null) ComUtil.release(pCaptureClient);
            if (pAudioClient != null) {
                try { ComUtil.callCom(pAudioClient, 11); } catch (Throwable ignored) {}
                ComUtil.release(pAudioClient);
            }
            if (pDevice != null) ComUtil.release(pDevice);
            if (pEnum != null) ComUtil.release(pEnum);
            if (pMixFormatRaw != null) Ole32.INSTANCE.CoTaskMemFree(pMixFormatRaw);
            ComUtil.uninitThread();
        }
    }

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
            try { pipedOut.close(); } catch (IOException ignored) {}
        }
        if (pipedIn != null) {
            try { pipedIn.close(); } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== WASAPI Loopback Test ===");
        var devices = WasapiDeviceEnumerator.list();
        System.out.println("Found " + devices.size() + " render devices:");
        for (var d : devices) {
            System.out.println("  - " + d.getDisplayName() + " [" + d.getId() + "]");
        }
        if (devices.isEmpty()) return;

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
    }
}
