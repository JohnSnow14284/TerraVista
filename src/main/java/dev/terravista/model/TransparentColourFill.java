package dev.terravista.model;

import it.unimi.dsi.fastutil.bytes.ByteArrayFIFOQueue;
import org.lwjgl.system.MemoryUtil;
import java.util.Arrays;
import static dev.terravista.model.TextureMipmaps.FACE_SIZE;

/** 以线程局部广度优先遍历传播透明像素颜色。 */
final class TransparentColourFill {
    private record Scratch(short[] nearest, ByteArrayFIFOQueue frontier) {
        private Scratch() {
            this(new short[FACE_SIZE * FACE_SIZE], new ByteArrayFIFOQueue(FACE_SIZE * FACE_SIZE));
        }
    }
    // 首次非暗化写入时创建缓存，并在线程生命周期内复用。
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private TransparentColourFill() {}

    static void fill(long base, byte faces) {
        var scratch = SCRATCH.get();
        for (int face = 0; face < 6; face++) {
            if (((faces >> face) & 1) == 0) continue;
            int x = (face >> 1) * FACE_SIZE;
            int y = (face & 1) * FACE_SIZE;
            seed(base + (long) (x + y * FACE_SIZE * 3) * 4, scratch);
            spread(scratch);
            apply(base, x, y, scratch.nearest);
        }
    }

    private static void seed(long face, Scratch scratch) {
        Arrays.fill(scratch.nearest, (short) -1);
        for (int y = 0; y < FACE_SIZE; y++) {
            for (int x = 0; x < FACE_SIZE; x++) {
                int colour = MemoryUtil.memGetInt(face + (x + y * FACE_SIZE * 3) * 4);
                if ((colour & 0xFF000000) != 0) {
                    int index = x + y * FACE_SIZE;
                    scratch.nearest[index] = (short) index;
                    scratch.frontier.enqueue((byte) index);
                }
            }
        }
    }

    private static void spread(Scratch scratch) {
        while (!scratch.frontier.isEmpty()) {
            int index = Byte.toUnsignedInt(scratch.frontier.dequeueByte());
            int x = index & (FACE_SIZE - 1);
            int y = index / FACE_SIZE;
            short candidate = (short) (scratch.nearest[index] + (short) 0x0100);
            for (int direction = 3; direction != -1; direction--) {
                int step = 2 * (direction & 1) - 1;
                int nx = x + (((direction & 2) == 2) ? step : 0);
                int ny = y + (((direction & 2) == 0) ? step : 0);
                if (nx < 0 || nx >= FACE_SIZE || ny < 0 || ny >= FACE_SIZE) continue;
                int neighbour = nx + ny * FACE_SIZE;
                if ((candidate & 0xFF00) < (scratch.nearest[neighbour] & 0xFF00)) {
                    scratch.nearest[neighbour] = candidate;
                    scratch.frontier.enqueue((byte) neighbour);
                }
            }
        }
    }

    private static void apply(long base, int x, int y, short[] nearest) {
        for (int index = 0; index < FACE_SIZE * FACE_SIZE; index++) {
            int value = Short.toUnsignedInt(nearest[index]);
            if ((value & 0xFF00) != 0) {
                int colour = MemoryUtil.memGetInt(base + offset(x, y, value & 0xFF) * 4) & 0x00FFFFFF;
                MemoryUtil.memPutInt(base + offset(x, y, index) * 4, colour);
            }
        }
    }

    private static long offset(int x, int y, int index) {
        x += index & (FACE_SIZE - 1);
        y += index / FACE_SIZE;
        return x + y * FACE_SIZE * 3;
    }
}
