package com.moakiee.ae2lt.performance;

/** Startup switches also used by independent-JVM A/B measurements. */
public final class ObjectReuseOptions {
    public static final boolean PLAIN_KEYS = Boolean.parseBoolean(
            System.getProperty("ae2lt.objectReuse.plainKeys", "true"));
    public static final boolean FAST_NBT_COPIES = Boolean.parseBoolean(
            System.getProperty("ae2lt.objectReuse.fastNbtCopies", "true"));

    private ObjectReuseOptions() {}
}
