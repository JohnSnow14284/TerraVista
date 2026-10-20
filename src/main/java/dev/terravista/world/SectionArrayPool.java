package dev.terravista.world;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/** 在进程内复用已退役区块数组并保留其内容。 */
final class SectionArrayPool {
    private static final int LIMIT = 400;
    private static final AtomicInteger COUNT = new AtomicInteger();
    private static final ConcurrentLinkedDeque<long[]> ARRAYS = new ConcurrentLinkedDeque<>();
    private SectionArrayPool() {}

    static long[] take() {
        long[] blocks = ARRAYS.poll();
        if (blocks == null) return new long[SectionContents.VOLUME];
        COUNT.decrementAndGet();
        return blocks;
    }
    static void recycle(SectionContents contents) {
        for (;;) {
            int count = COUNT.get();
            if (count >= LIMIT) return;
            if (COUNT.compareAndSet(count, count + 1)) {
                ARRAYS.add(contents.blocks());
                return;
            }
        }
    }
    static void clear() {
        while (ARRAYS.poll() != null) COUNT.decrementAndGet();
    }
}
