package dev.terravista.task;

import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinNT;
import dev.terravista.bootstrap.ModLog;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.system.Platform;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor.LogicalProcessor;
import oshi.hardware.CentralProcessor.PhysicalProcessor;

/** 在类初始化时采集物理核心与原生处理器组掩码。 */
public final class ProcessorTopology {
    public record Affinity(long mask, short group) {}
    public record Core(boolean efficiency, Affinity affinity) {}
    public static final Core[] CORES;
    static {
        Core[] detected = null;
        try {
            if (Platform.get() == Platform.WINDOWS) {
                detected = windowsLayout(Kernel32Util.getLogicalProcessorInformationEx(
                        WinNT.LOGICAL_PROCESSOR_RELATIONSHIP.RelationProcessorCore));
            } else if (Platform.get() == Platform.LINUX) {
                var processor = new SystemInfo().getHardware().getProcessor();
                detected = linuxLayout(processor.getLogicalProcessors(), processor.getPhysicalProcessors());
            }
        } catch (Exception exception) {
            ModLog.error("Tasks: Failed to generate cpu core layout, falling back to null: ", exception);
        }
        CORES = detected;
    }
    private ProcessorTopology() {}

    public static int coreCount() { return CORES == null ? Runtime.getRuntime().availableProcessors() : CORES.length; }

    public static void bind(Core... cores) {
        var affinities = new Affinity[cores.length];
        for (int i = 0; i < cores.length; i++) affinities[i] = cores[i].affinity;
        bind(affinities);
    }

    public static void bind(Affinity... affinities) {
        if (Platform.get() == Platform.WINDOWS) {
            var merged = windowsGroups(affinities);
            ProcessorAffinity.windowsMasks(merged.masks, merged.groups);
        } else if (Platform.get() == Platform.LINUX) {
            ProcessorAffinity.linuxMasks(linuxGroups(affinities));
        } else ModLog.error("Tasks: Don't know how to set thread affinity on this platform.");
    }

    record GroupMasks(long[] masks, short[] groups) {}
    static GroupMasks windowsGroups(Affinity[] affinities) {
        long[] masks = new long[affinities.length];
        short[] groups = new short[affinities.length];
        Arrays.fill(groups, (short) -1);
        int count = 0;
        for (var affinity : affinities) {
            int index = 0;
            while (index < count && groups[index] != affinity.group) index++;
            if (index == count) { groups[index] = affinity.group; count++; }
            masks[index] |= affinity.mask;
        }
        return new GroupMasks(Arrays.copyOf(masks, count), Arrays.copyOf(groups, count));
    }

    static long[] linuxGroups(Affinity[] affinities) {
        // 按有符号短整数差值对调用方数组原地排序。
        Arrays.sort(affinities, (a, b) -> a.group - b.group);
        long[] masks = new long[affinities.length];
        for (int i = 0; i < affinities.length; i++) masks[i] = affinities[i].mask;
        return masks;
    }

    static Core[] windowsLayout(WinNT.SYSTEM_LOGICAL_PROCESSOR_INFORMATION_EX[] descriptors) {
        boolean allZero = true;
        for (var descriptor : descriptors) allZero &= ((WinNT.PROCESSOR_RELATIONSHIP) descriptor).efficiencyClass == 0;
        var result = new Core[descriptors.length];
        int index = 0;
        for (var descriptor : descriptors) {
            var core = (WinNT.PROCESSOR_RELATIONSHIP) descriptor;
            boolean smt = (core.flags & 1) == 1;
            if (core.groupMask.length != 1) throw new IllegalStateException("TerraVista Tasks: Unsupported architecture");
            long mask = core.groupMask[0].mask.longValue();
            if ((Long.bitCount(mask) > 1) != smt) throw new IllegalStateException("TerraVista Tasks: Logic issue");
            result[index++] = new Core(!allZero && core.efficiencyClass == 0, new Affinity(mask, core.groupMask[0].group));
        }
        sort(result);
        return result;
    }

    static Core[] linuxLayout(List<LogicalProcessor> logical, List<PhysicalProcessor> physical) {
        var masks = new Int2ObjectOpenHashMap<Affinity>();
        for (var thread : logical) {
            var prior = masks.getOrDefault(thread.getPhysicalProcessorNumber(), new Affinity(0, (short) thread.getProcessorGroup()));
            if (thread.getProcessorGroup() != prior.group) throw new IllegalStateException();
            masks.put(thread.getPhysicalProcessorNumber(), new Affinity(prior.mask | (1L << thread.getProcessorNumber()), (short) thread.getProcessorGroup()));
        }
        boolean allZero = true;
        for (var core : physical) if (core.getEfficiency() != 0) { allZero = false; break; }
        var result = new Core[physical.size()];
        int index = 0;
        for (var core : physical) {
            var affinity = masks.remove(core.getPhysicalProcessorNumber());
            if (affinity == null) throw new IllegalStateException();
            result[index++] = new Core(core.getEfficiency() == 0 && !allZero, affinity);
        }
        sort(result);
        return result;
    }

    static void sort(Core[] cores) {
        Arrays.sort(cores, (a, b) -> {
            if (a.efficiency != b.efficiency) return a.efficiency ? 1 : -1;
            int group = Short.compareUnsigned(a.affinity.group, b.affinity.group);
            return group == 0 ? Long.compareUnsigned(a.affinity.mask, b.affinity.mask) : group;
        });
    }
}
