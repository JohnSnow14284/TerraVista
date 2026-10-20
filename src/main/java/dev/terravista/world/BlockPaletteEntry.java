package dev.terravista.world;

import com.mojang.serialization.Dynamic;
import dev.terravista.bootstrap.ModLog;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** 保存方块身份与不透明度，并按 NBT 格式读取和修复数据。 */
public final class BlockPaletteEntry {
    public final int id;
    public final BlockState state;
    final int mipTraits;
    final int mipHeight;
    public BlockPaletteEntry(int id, BlockState state) {
        this.id = id; this.state = state;
        int opacity = state.getBlock() instanceof LeavesBlock ? 15 : state.getLightDampening();
        mipTraits = Math.clamp(opacity, 0, 15) | (!state.getFluidState().isEmpty() ? 16 : 0)
                | (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock ? 32 : 0)
                | (state.isAir() ? 64 : 0)
                | (state.isSolid() || state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock
                    || !state.getFluidState().isEmpty() ? 128 : 0)
                | (state.getBlock() instanceof net.minecraft.world.level.block.IceBlock ? 256 : 0)
                | (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER) ? 512 : 0);
        var shape = state.isSolid() || state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock
                ? state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) : null;
        mipHeight = shape == null || shape.isEmpty() ? 256
                : Math.clamp((int) Math.round(shape.max(net.minecraft.core.Direction.Axis.Y) * 256), 1, 256);
    }
    public byte[] encode() {
        try {
            var tag = new CompoundTag();
            tag.putInt("id", id);
            tag.put("block_state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).result().get());
            var bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, bytes);
            return bytes.toByteArray();
        } catch (Exception exception) { throw new RuntimeException(exception); }
    }
    public static BlockPaletteEntry decode(int id, byte[] bytes, boolean[] resave) {
        try {
            var tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            if (tag.getIntOr("id", -1) != id) throw new IllegalStateException("TerraVista World: Encoded id != expected id");
            var block = tag.get("block_state");
            var result = BlockState.CODEC.parse(NbtOps.INSTANCE, block);
            if (result.isError()) {
                ModLog.info("World: Could not decode blockstate, attempting fixes, error: " + result.error().get().message());
                block = (CompoundTag) DataFixers.getDataFixer().update(References.BLOCK_STATE,
                        new Dynamic<>(NbtOps.INSTANCE, block), 0, SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
                result = BlockState.CODEC.parse(NbtOps.INSTANCE, block);
                if (result.isError()) {
                    ModLog.error("World: Could not decode blockstate setting to air. id:" + id + " error: " + result.error().get().message());
                    return new BlockPaletteEntry(id, Blocks.AIR.defaultBlockState());
                }
                ModLog.info("World: Fixed blockstate to: " + result.getOrThrow());
                resave[0] |= true;
                return new BlockPaletteEntry(id, result.getOrThrow());
            }
            return new BlockPaletteEntry(id, result.getOrThrow());
        } catch (IOException exception) { throw new RuntimeException(exception); }
    }
}
