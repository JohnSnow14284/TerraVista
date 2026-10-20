package dev.terravista.task;

import dev.terravista.bootstrap.ModLog;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.Kernel32;

/** 对调用线程执行原生亲和性操作。 */
public final class ProcessorAffinity {
    public static final int WINDOWS_TIME_CRITICAL = 15;
    public static final int WINDOWS_LOWEST = -2;
    public static final int WINDOWS_BACKGROUND_BEGIN = 0x00010000;
    public static final int WINDOWS_BACKGROUND_END = 0x00020000;
    public static final boolean WINDOWS = Platform.get() == Platform.WINDOWS;
    public static final boolean LINUX = Platform.get() == Platform.LINUX;
    private static final long priorityFunction;
    private static final long windowsMaskFunction;
    private static final long linuxMaskFunction;

    static {
        priorityFunction = WINDOWS ? Kernel32.getLibrary().getFunctionAddress("SetThreadPriority") : 0;
        windowsMaskFunction = WINDOWS ? Kernel32.getLibrary().getFunctionAddress("SetThreadSelectedCpuSetMasks") : 0;
        long linuxFunction = 0;
        if (LINUX) {
            try {
                var libc = APIUtil.apiCreateLibrary("libc.so.6");
                linuxFunction = APIUtil.apiGetFunctionAddress(libc, "sched_setaffinity");
            } catch (Exception exception) { ModLog.error(exception); }
        }
        linuxMaskFunction = linuxFunction;
    }

    private ProcessorAffinity() {}

    public static boolean windowsMask(long mask) { return windowsMasks(new long[]{mask}, new short[]{0}); }

    public static boolean windowsMasks(long[] masks, short[] groups) {
        if (windowsMaskFunction == 0 || !WINDOWS) return false;
        if (masks == null) {
            if (JNI.invokePPCI(Kernel32.GetCurrentThread(), 0, (short) 0, windowsMaskFunction) == 0)
                throw new IllegalStateException();
            return true;
        }
        if (masks.length != groups.length) throw new IllegalArgumentException();
        try (var stack = MemoryStack.stackPush()) {
            long address = stack.ncalloc(16, masks.length, 16);
            MemoryUtil.memSet(address, 0, masks.length * 16L);
            writeWindowsMasks(address, masks, groups);
            if (JNI.invokePPCI(Kernel32.GetCurrentThread(), address, (short) masks.length, windowsMaskFunction) == 0)
                throw new IllegalStateException();
            return true;
        }
    }

    static void writeWindowsMasks(long address, long[] masks, short[] groups) {
        for (int i = 0; i < masks.length; i++) {
            MemoryUtil.memPutLong(address + i * 16L, masks[i]);
            MemoryUtil.memPutShort(address + i * 16L + 8L, groups[i]);
        }
    }

    public static boolean windowsPriority(int priority) {
        if (priorityFunction == 0 || !WINDOWS) return false;
        if (JNI.callPI(Kernel32.GetCurrentThread(), priority, priorityFunction) == 0)
            throw new IllegalStateException("TerraVista Tasks: Operation failed");
        return true;
    }

    public static boolean linuxMasks(long[] masks) {
        if (linuxMaskFunction == 0 || WINDOWS) return false;
        try (var stack = MemoryStack.stackPush()) {
            long address = stack.ncalloc(8, masks.length, 8);
            writeLinuxMasks(address, masks);
            if (JNI.invokePPI(0, (long) masks.length * 8, address, linuxMaskFunction) != 0)
                throw new IllegalStateException();
            return true;
        }
    }

    static void writeLinuxMasks(long address, long[] masks) {
        for (int i = 0; i < masks.length; i++) MemoryUtil.memPutLong(address + i * 8L, masks[i]);
    }
}
