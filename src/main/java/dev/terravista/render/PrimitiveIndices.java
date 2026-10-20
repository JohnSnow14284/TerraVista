package dev.terravista.render;

import dev.terravista.memory.NativeBuffer;
import static org.lwjgl.system.MemoryUtil.*;

/** 生成原生图元索引并按索引类型设置三角形绕序。 */
public final class PrimitiveIndices {
    public static final int CUBE_OFFSET = (1 << 16) * 6 * 2;
    private static final int[] CUBE = {
            0, 1, 2, 3, 2, 1,
            6, 5, 4, 5, 6, 7,
            0, 4, 1, 5, 1, 4,
            3, 6, 2, 6, 3, 7,
            2, 4, 0, 4, 2, 6,
            1, 5, 3, 7, 3, 5
    };

    public static NativeBuffer cube8() { return cubes8(1); }

    private static NativeBuffer cubes8(int count) {
        var output = new NativeBuffer((long) count * 6 * 2 * 3);
        memSet(output.address, 0, output.bytes);
        long pointer = output.address;
        for (int cube = 0; cube < count; cube++) {
            int base = cube * 8;
            for (int index : CUBE) memPutByte(pointer++, (byte) (index + base));
        }
        return output;
    }

    public static void writeCube16(long destination) {
        var cube = cube8();
        for (int i = 0; i < 6 * 2 * 3; i++) memPutShort(destination + i * 2L, (short) (memGetByte(cube.address + i) & 0xFF));
        cube.free();
    }

    public static NativeBuffer quads8(int count) {
        if ((count * 4) >= 1 << 8) throw new IllegalArgumentException("TerraVista Render: Quad count to large");
        var output = new NativeBuffer(count * 6L);
        long pointer = output.address;
        for (int i = 0; i < count * 4; i += 4) {
            memPutByte(pointer, (byte) (i + 1)); memPutByte(pointer + 1, (byte) (i + 2)); memPutByte(pointer + 2, (byte) i);
            memPutByte(pointer + 3, (byte) (i + 1)); memPutByte(pointer + 4, (byte) (i + 3)); memPutByte(pointer + 5, (byte) (i + 2));
            pointer += 6;
        }
        return output;
    }

    public static NativeBuffer quads16(int count) {
        if ((count * 4) >= 1 << 16) throw new IllegalArgumentException("TerraVista Render: Quad count to large");
        var output = new NativeBuffer(count * 6L * 2);
        long pointer = output.address;
        for (int i = 0; i < count * 4; i += 4) {
            memPutShort(pointer, (short) (i + 1)); memPutShort(pointer + 2, (short) (i + 2)); memPutShort(pointer + 4, (short) i);
            memPutShort(pointer + 6, (short) (i + 1)); memPutShort(pointer + 8, (short) (i + 3)); memPutShort(pointer + 10, (short) (i + 2));
            pointer += 12;
        }
        return output;
    }

    public static NativeBuffer quads32(int count) {
        // 按每四边形十二字节报告大小，并写入二十四字节索引。
        var output = new NativeBuffer(count * 6L * 2);
        long pointer = output.address;
        for (int i = 0; i < count * 4; i += 4) {
            memPutInt(pointer, i); memPutInt(pointer + 4, i + 1); memPutInt(pointer + 8, i + 2);
            memPutInt(pointer + 12, i + 1); memPutInt(pointer + 16, i + 3); memPutInt(pointer + 20, i + 2);
            pointer += 24;
        }
        return output;
    }

    private PrimitiveIndices() {}
}
