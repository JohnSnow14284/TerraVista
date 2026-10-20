package dev.terravista.storage;

import java.util.function.LongConsumer;

public interface StoredSectionVisitor {
    void iteratePositions(int level, LongConsumer consumer);
}
