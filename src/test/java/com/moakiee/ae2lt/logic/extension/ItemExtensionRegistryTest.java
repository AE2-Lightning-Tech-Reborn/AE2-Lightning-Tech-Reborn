package com.moakiee.ae2lt.logic.extension;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ItemExtensionRegistryTest {
    @Test void distinctAddonItemsCoexistAndDuplicateOrLateRegistrationsFail() {
        var registry = new ItemExtensionRegistry<String>();
        var coil = new ResourceLocation("first:coil");
        var tool = new ResourceLocation("second:tool");
        registry.register(coil, "coil"); registry.register(tool, "tool");
        assertThrows(IllegalArgumentException.class, () -> registry.register(coil, "replacement"));
        assertEquals("coil", registry.get(coil)); assertEquals("tool", registry.get(tool));
        registry.freeze();
        assertThrows(IllegalStateException.class,
                () -> registry.register(new ResourceLocation("third:tool"), "late"));
    }
}
