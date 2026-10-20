package dev.terravista.render.gpu;

import java.util.ArrayList;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 持有二维图像与各类视图，并按帧回收顺序释放。 */
public final class DeviceImage {
    final FrameCommands commands;
    public final long image, memory, view;
    public final long[] mipViews;
    public final int width, height, mipLevels, format, aspect;
    int layout = VK_IMAGE_LAYOUT_UNDEFINED;
    private final ArrayList<Long> extraViews = new ArrayList<>();

    public DeviceImage(FrameCommands commands, int width, int height, int mipLevels, int format,
                       int usage, int aspect, boolean perMipViews) {
        this(commands, width, height, mipLevels, format, usage, aspect, perMipViews, null);
    }
    public DeviceImage(FrameCommands commands, int width, int height, int mipLevels, int format,
                       int usage, int aspect, boolean perMipViews, int[] viewFormats) {
        this.commands = commands;
        this.width = width;
        this.height = height;
        this.mipLevels = mipLevels;
        this.format = format;
        this.aspect = aspect;
        var device = commands.device();
        long createdImage = 0, allocatedMemory = 0, createdView = 0;
        long[] createdMips = null;
        try (var stack = stackPush()) {
            var create = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D).format(format)
                    .extent(extent -> extent.width(width).height(height).depth(1))
                    .mipLevels(mipLevels).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            if (viewFormats != null && viewFormats.length > 0) {
                create.flags(VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT);
                var formats = VkImageFormatListCreateInfo.calloc(stack).sType$Default().pViewFormats(stack.ints(viewFormats));
                create.pNext(formats.address());
            }
            var handle = stack.mallocLong(1);
            check(vkCreateImage(device.device, create, null, handle), "vkCreateImage");
            image = handle.get(0);
            createdImage = image;
            var requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device.device, image, requirements);
            var allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(device.memory.find(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            var memoryHandle = stack.mallocLong(1);
            check(vkAllocateMemory(device.device, allocate, null, memoryHandle), "vkAllocateMemory(image)");
            memory = memoryHandle.get(0);
            allocatedMemory = memory;
            check(vkBindImageMemory(device.device, image, memory, 0), "vkBindImageMemory");
            view = createView(stack, device, 0, mipLevels);
            createdView = view;
            if (perMipViews) {
                mipViews = createdMips = new long[mipLevels];
                for (int mip = 0; mip < mipLevels; mip++) mipViews[mip] = createView(stack, device, mip, 1);
            } else mipViews = null;
        } catch (RuntimeException | Error failure) {
            if (createdMips != null) {
                for (long mip : createdMips) if (mip != 0) vkDestroyImageView(device.device, mip, null);
            }
            if (createdView != 0) vkDestroyImageView(device.device, createdView, null);
            if (createdImage != 0) vkDestroyImage(device.device, createdImage, null);
            if (allocatedMemory != 0) vkFreeMemory(device.device, allocatedMemory, null);
            throw failure;
        }
    }
    private long createView(MemoryStack stack, VulkanDevice device, int baseMip, int count) {
        var create = VkImageViewCreateInfo.calloc(stack).sType$Default().image(image)
                .viewType(VK_IMAGE_VIEW_TYPE_2D).format(format);
        create.subresourceRange().aspectMask(aspect).baseMipLevel(baseMip).levelCount(count).baseArrayLayer(0).layerCount(1);
        var handle = stack.mallocLong(1);
        check(vkCreateImageView(device.device, create, null, handle), "vkCreateImageView");
        return handle.get(0);
    }
    public long aspectView(int viewAspect) {
        try (var stack = stackPush()) {
            var create = VkImageViewCreateInfo.calloc(stack).sType$Default().image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D).format(format);
            create.subresourceRange().aspectMask(viewAspect).baseMipLevel(0).levelCount(mipLevels).baseArrayLayer(0).layerCount(1);
            var handle = stack.mallocLong(1);
            check(vkCreateImageView(commands.device().device, create, null, handle), "vkCreateImageView(aspect)");
            long result = handle.get(0);
            extraViews.add(result);
            return result;
        }
    }
    public void free() {
        if (mipViews != null) for (long mip : mipViews) commands.retirement().deferImage(0, mip, 0);
        for (long extra : extraViews) commands.retirement().deferImage(0, extra, 0);
        commands.retirement().deferImage(image, view, memory);
    }
}
