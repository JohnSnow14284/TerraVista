package dev.terravista.mesh;

/** 以高位优先级和低位顺序号构成无符号任务排序键。 */
public final class MeshJobPriority {
    private MeshJobPriority() {}
    public static long nativeOrder(int levelRank, int attempts, int addition, int sequence) {
        return encode((levelRank * 3L + Math.min(attempts, 3)) * 2 + addition, sequence);
    }
    public static long spatialOrder(int rank, int attempts, int addition, int sequence) {
        return encode(rank * 8L + Math.min(attempts, 3) * 2L + addition, sequence);
    }
    private static long encode(long rank, int sequence) {
        return (rank << 32) | Integer.toUnsignedLong(sequence);
    }
}
