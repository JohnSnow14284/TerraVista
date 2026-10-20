package dev.terravista.world;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** 保存生物群系身份并按对象身份比较条目。 */
public final class BiomePaletteEntry {
    public final int id;
    public final String biome;
    public BiomePaletteEntry(int id, String biome) { this.id = id; this.biome = biome; }
    public byte[] encode() {
        try {
            var tag = new CompoundTag();
            tag.putInt("id", id); tag.putString("biome_id", biome);
            var bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, bytes);
            return bytes.toByteArray();
        } catch (Exception exception) { throw new RuntimeException(exception); }
    }
    public static BiomePaletteEntry decode(int id, byte[] bytes) {
        try {
            var tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            if (tag.getIntOr("id", -1) != id) throw new IllegalStateException("TerraVista World: Encoded id != expected id");
            return new BiomePaletteEntry(id, tag.getStringOr("biome_id", null));
        } catch (IOException exception) { throw new RuntimeException(exception); }
    }
}
