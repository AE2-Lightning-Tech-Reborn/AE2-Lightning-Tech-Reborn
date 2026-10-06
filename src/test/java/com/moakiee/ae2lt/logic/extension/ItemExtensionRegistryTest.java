package com.moakiee.ae2lt.logic.extension;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ItemExtensionRegistryTest {
    @Test void distinctAddonItemsCoexistAndDuplicateOrLateRegistrationsFail() {
        var registry = new ItemExtensionRegistry<String>();
        var coil = ResourceLocation.parse("first:coil");
        var tool = ResourceLocation.parse("second:tool");
        registry.register(coil, "coil"); registry.register(tool, "tool");
        assertThrows(IllegalArgumentException.class, () -> registry.register(coil, "replacement"));
        assertEquals("coil", registry.get(coil)); assertEquals("tool", registry.get(tool));
        registry.freeze();
        assertThrows(IllegalStateException.class,
                () -> registry.register(ResourceLocation.parse("third:tool"), "late"));
    }
}
