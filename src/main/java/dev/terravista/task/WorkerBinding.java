package dev.terravista.task;

/** 保存工作线程作业及对应清理对象。 */
public record WorkerBinding(Runnable execute, Runnable cleanup, boolean hasCleanup) {
    public WorkerBinding(Runnable execute, Runnable cleanup) { this(execute, cleanup, true); }
    public static WorkerBinding unmanaged(Runnable execute) { return new WorkerBinding(execute, null, false); }
}
