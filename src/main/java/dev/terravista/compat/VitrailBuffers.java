package dev.terravista.compat;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Render-thread descriptor bindings for Vitrail's packed LOD programs, without GPU copies. */
public final class VitrailBuffers {
    public enum Slot {
        SCENE("TerraVistaScene"), QUADS("TerraVistaQuads"), MODELS("TerraVistaModels"),
        COLOURS("TerraVistaColours"), POSITIONS("TerraVistaPositions");
        public final String block;
        Slot(String block) { this.block = block; }
    }

    private static final Slot[] SLOTS = Slot.values();
    private static VitrailBuffers current;
    private final Binding[] bindings = new Binding[SLOTS.length];

    private static Slot slot(String name) {
        return switch (name) {
            case "TerraVistaScene" -> Slot.SCENE;
            case "TerraVistaQuads" -> Slot.QUADS;
            case "TerraVistaModels" -> Slot.MODELS;
            case "TerraVistaColours" -> Slot.COLOURS;
            case "TerraVistaPositions" -> Slot.POSITIONS;
            default -> null;
        };
    }

    public static boolean named(String name) { return slot(name) != null; }

    public void set(Slot slot, dev.terravista.render.gpu.GpuBuffer owner, long offset, long bytes) {
        if (current == this) throw new IllegalStateException("TerraVista: changing active Vitrail storage");
        Binding previous = bindings[slot.ordinal()];
        // Reuse the facade, slice and upstream Bound record until the underlying allocation/range moves.
        if (previous != null && previous.owner == owner && previous.bytes == bytes) {
            previous.buffer.checkCanBeUsed();
            if (previous.offset != offset) {
                if (offset < 0 || offset > owner.sizeBytes() - bytes) {
                    throw new IllegalArgumentException("TerraVista: invalid storage slice for " + slot.block);
                }
                bindings[slot.ordinal()] = new Binding(owner, previous.buffer, offset, bytes,
                        previous.buffer.slice(offset, 16), previous.bound);
            }
            return;
        }
        register(slot, owner, owner.buffer, owner.sizeBytes(), offset, bytes, owner::isReleased);
    }

    void register(Slot slot, Object owner, long handle, long capacity, long offset, long bytes,
                  BooleanSupplier released) {
        if (offset < 0 || bytes < 16 || offset > capacity - bytes) {
            throw new IllegalArgumentException("TerraVista: invalid storage slice for " + slot.block);
        }
        var buffer = new BorrowedBuffer(handle, GpuBuffer.USAGE_UNIFORM, capacity, released);
        buffer.checkCanBeUsed();
        var slice = buffer.slice(offset, 16);
        var bound = BoundApi.create(handle, bytes);
        bindings[slot.ordinal()] = new Binding(owner, buffer, offset, bytes, slice, bound);
    }

    /** The scope covers binding and command submission, and must be released in a finally block. */
    public void activate() {
        if (current != null) throw new IllegalStateException("TerraVista: nested Vitrail buffer binding");
        for (Slot slot : SLOTS) {
            Binding binding = bindings[slot.ordinal()];
            if (binding == null) throw new IllegalStateException("TerraVista: missing " + slot.block);
            binding.buffer.checkCanBeUsed();
        }
        current = this;
    }

    public void deactivate() {
        if (current != this) throw new IllegalStateException("TerraVista: unbalanced Vitrail buffer binding");
        current = null;
    }

    private static Binding binding(String name) {
        Slot slot = slot(name);
        if (slot == null) return null;
        if (current == null) throw new IllegalStateException("TerraVista: " + name + " outside a packed LOD draw");
        Binding binding = current.bindings[slot.ordinal()];
        binding.buffer.checkCanBeUsed();
        return binding;
    }

    public static Object bound(String name) {
        Binding binding = binding(name);
        return binding == null ? null : binding.bound;
    }

    public static void bindSection(RenderPass pass) {
        pass.setUniform("OfDistantSection", binding("TerraVistaScene").slice);
    }

    public static void bind(RenderPass pass, List<String> names) {
        for (String name : names) {
            Binding binding = binding(name);
            if (binding != null) pass.setUniform(name, binding.slice);
        }
    }

    private record Binding(Object owner, BorrowedBuffer buffer, long offset, long bytes,
                           GpuBufferSlice slice, Object bound) {}

    private static final class BoundApi {
        private static final MethodHandle CONSTRUCTOR = resolve();

        private static MethodHandle resolve() {
            try {
                Class<?> type = Class.forName("dev.vitrail.render.storage.StorageBuffers$Bound", false,
                        VitrailBuffers.class.getClassLoader());
                return MethodHandles.publicLookup().findConstructor(type,
                        MethodType.methodType(void.class, long.class, long.class));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("TerraVista: incompatible Vitrail storage binding API", failure);
            }
        }

        private static Object create(long handle, long bytes) {
            try {
                return CONSTRUCTOR.invoke(handle, bytes);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("TerraVista: cannot bind native LOD storage", failure);
            }
        }
    }
}
