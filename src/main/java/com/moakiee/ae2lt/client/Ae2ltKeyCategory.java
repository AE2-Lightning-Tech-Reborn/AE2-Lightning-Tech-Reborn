package com.moakiee.ae2lt.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Shared key category so the 26.1 registration occurs exactly once. */
public final class Ae2ltKeyCategory {
    public static final KeyMapping.Category INSTANCE =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ae2lt", "main"));

    private Ae2ltKeyCategory() {}
}
