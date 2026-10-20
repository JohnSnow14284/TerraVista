package dev.terravista.render.gpu;

import java.util.List;
import static org.lwjgl.vulkan.VK10.*;

/** 描述两类管线共享的绑定与设备缓存键。 */
public final class PipelineBindings {
    public static final int UNIFORM_BUFFER = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    public static final int STORAGE_BUFFER = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    public static final int COMBINED_SAMPLER = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    public static final int STORAGE_IMAGE = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;

    public record Entry(int binding, int type) {}
    record LayoutKey(List<Entry> entries, int stages) {}

    public static Entry uniform(int binding) { return new Entry(binding, UNIFORM_BUFFER); }
    public static Entry storage(int binding) { return new Entry(binding, STORAGE_BUFFER); }
    public static Entry sampler(int binding) { return new Entry(binding, COMBINED_SAMPLER); }
    public static Entry image(int binding) { return new Entry(binding, STORAGE_IMAGE); }
    static LayoutKey key(List<Entry> entries, int stages) { return new LayoutKey(List.copyOf(entries), stages); }
    private PipelineBindings() {}
}
