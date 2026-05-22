package ru.hniApplications.testApplication.codec.wasapi;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import ru.hniApplications.testApplication.capture.AudioDevice;
import ru.hniApplications.testApplication.capture.AudioDeviceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Перечислитель аудиоустройств вывода Windows через WASAPI.
 * Возвращает все активные render-устройства (динамики, наушники и т.д.)
 * для захвата системного звука через WASAPI Loopback.
 */
public final class WasapiDeviceEnumerator {

    private WasapiDeviceEnumerator() {}

    /**
     * Возвращает список всех активных render-устройств вывода.
     * Каждое устройство представляется как AudioDevice с type=SYSTEM_LOOPBACK
     * и ffmpegArg в формате "wasapi-loopback://{device-id}".
     *
     * @return список аудиоустройств для захвата системного звука
     */
    public static List<AudioDevice> list() {
        List<AudioDevice> result = new ArrayList<>();

        ComUtil.initThread();
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
                // EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE, &collection)
                PointerByReference pColl = new PointerByReference();
                int rc = ComUtil.callCom(pEnum, 3,  // EnumAudioEndpoints
                        WasapiConstants.eRender,
                        WasapiConstants.DEVICE_STATE_ACTIVE,
                        pColl);
                ComUtil.checkHr(rc, "EnumAudioEndpoints");
                Pointer pCollection = pColl.getValue();

                try {
                    // GetCount
                    IntByReference count = new IntByReference();
                    ComUtil.checkHr(ComUtil.callCom(pCollection, 3, count),  // GetCount
                            "GetCount");

                    // Item для каждого устройства
                    for (int i = 0; i < count.getValue(); i++) {
                        PointerByReference pDev = new PointerByReference();
                        ComUtil.checkHr(ComUtil.callCom(pCollection.getValue(), 4, i, pDev),  // Item
                                "Item(" + i + ")");
                        Pointer pDevice = pDev.getValue();

                        try {
                            // GetId
                            PointerByReference pId = new PointerByReference();
                            ComUtil.checkHr(ComUtil.callCom(pDevice, 5, pId),  // GetId
                                    "GetId");
                            String deviceId = pId.getValue().getWideString(0);
                            Ole32.INSTANCE.CoTaskMemFree(pId.getValue());

                            // OpenPropertyStore + GetValue для friendly name
                            String friendlyName = readFriendlyName(pDevice);

                            result.add(new AudioDevice(
                                    deviceId,
                                    friendlyName + " (системный звук)",
                                    "wasapi-loopback://" + deviceId,
                                    AudioDeviceType.SYSTEM_LOOPBACK));
                        } finally {
                            ComUtil.release(pDevice);
                        }
                    }
                } finally {
                    ComUtil.release(pCollection);
                }
            } finally {
                ComUtil.release(pEnum);
            }
        } catch (Exception e) {
            System.err.println("[WasapiDeviceEnumerator] Enumeration failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ComUtil.uninitThread();
        }

        return result;
    }

    /**
     * Читает friendly name устройства через IPropertyStore.
     *
     * @param pDevice указатель на IMMDevice
     * @return friendly name или "Unknown Device" при ошибке
     */
    private static String readFriendlyName(Pointer pDevice) {
        PointerByReference pStore = new PointerByReference();
        int rc = ComUtil.callCom(pDevice, 4, WasapiConstants.STGM_READ, pStore);  // OpenPropertyStore
        if (rc != WasapiConstants.S_OK) {
            return "Unknown Device";
        }
        Pointer pPropStore = pStore.getValue();

        try {
            // Создаём PROPERTYKEY для PKEY_Device_FriendlyName
            PROPERTYKEY key = new PROPERTYKEY();
            key.fmtid = new com.sun.jna.platform.win32.Guid.GUID(
                    WasapiGuids.PKEY_Device_FriendlyName_fmtid);
            key.pid = WasapiGuids.PKEY_Device_FriendlyName_pid;

            // GetValue
            PROPVARIANT propVar = new PROPVARIANT();
            rc = ComUtil.callCom(pPropStore, 5, key, propVar);  // GetValue
            if (rc != WasapiConstants.S_OK) {
                return "Unknown Device";
            }

            // VT_LPWSTR = 31
            String name = "Unknown Device";
            if (propVar.vt == 31 && propVar.pwszVal != null) {
                name = propVar.pwszVal.getWideString(0);
            }

            // Освобождаем строку из PROPVARIANT
            if (propVar.pwszVal != null) {
                Ole32.INSTANCE.CoTaskMemFree(propVar.pwszVal);
            }

            return name;
        } finally {
            ComUtil.release(pPropStore);
        }
    }
}
