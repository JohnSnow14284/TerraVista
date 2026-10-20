package dev.terravista.model;

/** 通过无堆栈异常携带未完成网格状态以供重试。 */
public final class PendingModel extends RuntimeException {
    public final int requestedId;
    public final boolean blockId;
    public int retryMask;
    public long[] retryData;
    public PendingModel(int requestedId, boolean blockId) {
        super(null, null, false, false);
        this.requestedId = requestedId;
        this.blockId = blockId;
    }
}
