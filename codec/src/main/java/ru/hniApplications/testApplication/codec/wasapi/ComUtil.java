package ru.hniApplications.testApplication.codec.wasapi;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;

/**
 * Утилиты для работы с COM из JNA.
 *
 * ВАЖНО: каждый поток, который вызывает COM-методы, должен сначала
 * вызвать initThread(), а перед завершением — uninitThread().
 */
public final class ComUtil {

    private ComUtil() {}

    /**
     * Инициализирует COM-библиотеку для текущего потока.
     * Должен вызываться в каждом потоке, работающем с COM.
     */
    public static void initThread() {
        WinNT.HRESULT hr = Ole32.INSTANCE.CoInitializeEx(
                null, WasapiConstants.COINIT_MULTITHREADED);
        // S_OK или S_FALSE (уже инициализировано) — оба ОК.
        int code = hr.intValue();
        if (code != WasapiConstants.S_OK && code != WasapiConstants.S_FALSE) {
            throw new RuntimeException("CoInitializeEx failed: 0x"
                    + Integer.toHexString(code));
        }
    }

    /**
     * Освобождает COM-библиотеку для текущего потока.
     * Должен вызываться перед завершением потока.
     */
    public static void uninitThread() {
        Ole32.INSTANCE.CoUninitialize();
    }

    /**
     * Проверяет HRESULT и бросает исключение при ошибке.
     *
     * @param hr значение HRESULT
     * @param op описание операции для сообщения об ошибке
     */
    public static void checkHr(int hr, String op) {
        if (hr != WasapiConstants.S_OK) {
            throw new RuntimeException(op + " failed: HRESULT=0x"
                    + Integer.toHexString(hr));
        }
    }

    /**
     * Вызывает виртуальный метод COM-объекта.
     *
     * @param thisPtr указатель на COM-объект
     * @param vtblIndex индекс метода в vtable (начиная с IUnknown::QueryInterface=0)
     * @param args аргументы (первым неявно идёт thisPtr — не указывать!)
     * @return HRESULT (или указатель — зависит от метода)
     */
    public static int callCom(Pointer thisPtr, int vtblIndex, Object... args) {
        Pointer vtbl = thisPtr.getPointer(0);
        Pointer methodPtr = vtbl.getPointer((long) vtblIndex * Native.POINTER_SIZE);
        com.sun.jna.Function fn = com.sun.jna.Function.getFunction(methodPtr);

        Object[] fullArgs = new Object[args.length + 1];
        fullArgs[0] = thisPtr;
        System.arraycopy(args, 0, fullArgs, 1, args.length);

        return fn.invokeInt(fullArgs);
    }

    /**
     * Освобождает COM-объект (IUnknown::Release, vtable index = 2).
     *
     * @param comObj указатель на COM-объект
     */
    public static void release(Pointer comObj) {
        if (comObj != null) {
            callCom(comObj, 2);
        }
    }
}
