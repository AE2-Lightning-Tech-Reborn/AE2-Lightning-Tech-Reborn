package com.moakiee.ae2lt.logic.extension;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/** Registrations are shared between logical sides and frozen after mod loading. */
public final class ItemExtensionRegistry<T> {
    private final Map<ResourceLocation, T> entries = new ConcurrentHashMap<>();
    private boolean frozen;

    public synchronized void register(ResourceLocation itemId, T extension) {
        if (frozen) throw new IllegalStateException("Extension registration has finished");
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(extension);
        if (entries.putIfAbsent(itemId, extension) != null) {
            throw new IllegalArgumentException("Duplicate item extension: " + itemId);
        }
    }

    public T get(ResourceLocation itemId) { return entries.get(itemId); }

    public synchronized void freeze() { frozen = true; }
}
