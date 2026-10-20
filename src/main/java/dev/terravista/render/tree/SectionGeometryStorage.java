package dev.terravista.render.tree;

/** 定义渲染线程几何存储的生命周期、活动区块数与容量。 */
public interface SectionGeometryStorage {
    int size();
    void close();
    long maxCapacity();
}
