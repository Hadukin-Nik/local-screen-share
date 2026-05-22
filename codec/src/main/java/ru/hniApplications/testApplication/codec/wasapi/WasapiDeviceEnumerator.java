package ru.hniApplications.testApplication.codec.wasapi;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import ru.hniApplications.testApplication.capture.AudioDevice;
import ru.hniApplications.testApplication.capture.AudioDeviceType;

import java.util.ArrayList;
import java.util.List;

public final class WasapiDeviceEnumerator {

    private WasapiDeviceEnumerator() {}

    public static List<AudioDevice> list() {
        List<AudioDevice> result = new ArrayList<>();
        ComUtil.initThread();
        try {
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
                PointerByReference pColl = new PointerByReference();
                int rc = ComUtil.callCom(pEnum, 3,
                        WasapiConstants.eRender,
                        WasapiConstants.DEVICE_STATE_ACTIVE,
                        pColl);
                ComUtil.checkHr(rc, "EnumAudioEndpoints");
                Pointer pCollection = pColl.getValue();
                try {
                    IntByReference count = new IntByReference();
                    ComUtil.checkHr(ComUtil.callCom(pCollection, 3, count), "GetCount");

                    for (int i = 0; i < count.getValue(); i++) {
                        PointerByReference pDev = new PointerByReference();
                        ComUtil.checkHr(ComUtil.callCom(pCollection, 4, i, pDev),
                                "Item(" + i + ")");
                        Pointer pDevice = pDev.getValue();
                        try {
                            PointerByReference pId = new PointerByReference();
                            ComUtil.checkHr(ComUtil.callCom(pDevice, 5, pId), "GetId");
                            String deviceId = pId.getValue().getWideString(0);
                            Ole32.INSTANCE.CoTaskMemFree(pId.getValue());

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

    private static String readFriendlyName(Pointer pDevice) {
        PointerByReference pStore = new PointerByReference();
        int rc = ComUtil.callCom(pDevice, 4, WasapiConstants.STGM_READ, pStore);
        if (rc != WasapiConstants.S_OK) {
            return "Unknown Device";
        }
        Pointer pPropStore = pStore.getValue();
        try {
            // PROPERTYKEY готовим в native-памяти
            PROPERTYKEY key = new PROPERTYKEY();
            key.fmtid = new Guid.GUID(WasapiGuids.PKEY_Device_FriendlyName_fmtid);
            key.pid = WasapiGuids.PKEY_Device_FriendlyName_pid;
            key.write();

            PROPVARIANT propVar = new PROPVARIANT();
            propVar.write();

            rc = ComUtil.callCom(pPropStore, 5, key.getPointer(), propVar.getPointer());
            if (rc != WasapiConstants.S_OK) {
                return "Unknown Device";
            }
            propVar.read();

            String name = "Unknown Device";
            // VT_LPWSTR = 31
            if (propVar.vt == 31) {
                Pointer pwsz = propVar.getPwszVal();
                if (pwsz != null) {
                    name = pwsz.getWideString(0);
                    Ole32.INSTANCE.CoTaskMemFree(pwsz);
                }
            }
            return name;
        } finally {
            ComUtil.release(pPropStore);
        }
    }
}
