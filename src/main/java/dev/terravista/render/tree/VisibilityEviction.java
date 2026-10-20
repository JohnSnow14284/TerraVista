package dev.terravista.render.tree;

import dev.terravista.render.DeviceBuffer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/** 逐帧清理可见性并管理编码槽位的复位与清空集合。 */
public interface VisibilityEviction {
    int OUTPUT_COUNT = 256;
    void processFrame(DeviceBuffer nodes);
    void updateSlots(IntOpenHashSet slots);
    void close();
}
