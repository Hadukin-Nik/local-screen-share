package ru.hniApplications.testApplication.codec.wasapi;

import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Guid;

import java.util.Arrays;
import java.util.List;

/**
 * PROPERTYKEY структура для работы с IPropertyStore.
 * Используется для получения свойств устройств (например, friendly name).
 */
public class PROPERTYKEY extends Structure {
    public Guid.GUID fmtid;
    public int       pid;

    public PROPERTYKEY() { super(); }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("fmtid", "pid");
    }
}
