package dev.terravista.memory;

import java.lang.ref.Cleaner;

public final class NativeCleaner {
    public static final Cleaner INSTANCE = Cleaner.create();
}
