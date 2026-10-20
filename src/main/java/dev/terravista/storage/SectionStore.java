package dev.terravista.storage;

import dev.terravista.world.SectionContents;

/** 定义区块读写、调色板持久化与位置遍历。 */
public abstract class SectionStore implements PaletteStorage, StoredSectionVisitor {
    public abstract int load(SectionContents section);
    public abstract void save(SectionContents section);
}
