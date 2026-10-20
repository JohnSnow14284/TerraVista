package dev.terravista.world;

import java.util.concurrent.atomic.AtomicLong;

/** 为区块生命周期与编辑分配统一的进程序号。 */
final class SectionRevision {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private final AtomicLong value = new AtomicLong(SEQUENCE.incrementAndGet());

    long current() { return value.get(); }
    void advance() {
        // 保留已发布的较新修订号。
        value.accumulateAndGet(SEQUENCE.incrementAndGet(), Math::max);
    }
}
