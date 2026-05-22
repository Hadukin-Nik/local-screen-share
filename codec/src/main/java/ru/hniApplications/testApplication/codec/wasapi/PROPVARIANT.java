package ru.hniApplications.testApplication.codec.wasapi;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * PROPVARIANT структура для работы с IPropertyStore::GetValue.
 *
 * Реальный размер PROPVARIANT в Windows:
 *   - 16 байт на x86 (4-байтовое выравнивание)
 *   - 24 байта на x64 (8-байтовое выравнивание)
 *
 * Layout:
 *   VARTYPE vt;                  // 2 bytes (offset 0)
 *   WORD    wReserved1;          // 2 bytes (offset 2)
 *   WORD    wReserved2;          // 2 bytes (offset 4)
 *   WORD    wReserved3;          // 2 bytes (offset 6)
 *   union { ... };               // 8 bytes на x86, 16 байт на x64 (offset 8)
 *
 * Для VT_LPWSTR (тип 31) первое поле union = pwszVal (LPWSTR = Pointer).
 *
 * Чтобы избежать повреждения памяти из-за нехватки размера, используем
 * union-буфер из 16 байт.
 */
public class PROPVARIANT extends Structure {
    public short vt;
    public short wReserved1;
    public short wReserved2;
    public short wReserved3;
    /** Union: 16 байт. Для VT_LPWSTR первые 8 байт = pwszVal на x64. */
    public byte[] unionData = new byte[16];

    public PROPVARIANT() { super(); }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("vt", "wReserved1", "wReserved2", "wReserved3", "unionData");
    }

    /** Возвращает указатель LPWSTR (для VT_LPWSTR = 31). */
    public Pointer getPwszVal() {
        // На x64 указатель занимает первые 8 байт union'а
        long ptrValue = 0;
        for (int i = 0; i < 8; i++) {
            ptrValue |= ((long) (unionData[i] & 0xFF)) << (i * 8);
        }
        return ptrValue == 0 ? null : new Pointer(ptrValue);
    }
}
