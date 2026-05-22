package ru.hniApplications.testApplication.codec.wasapi;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * PROPVARIANT структура для работы с IPropertyStore::GetValue.
 * Упрощённая версия — поддерживает только VT_LPWSTR (строки).
 */
public class PROPVARIANT extends Structure {
    public short vt;
    public short wReserved1;
    public short wReserved2;
    public short wReserved3;
    public Pointer pwszVal;  // для VT_LPWSTR (тип 31)

    public PROPVARIANT() { super(); }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("vt", "wReserved1", "wReserved2", "wReserved3", "pwszVal");
    }
}
