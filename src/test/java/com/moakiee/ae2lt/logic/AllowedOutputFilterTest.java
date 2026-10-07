package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Items;

class AllowedOutputFilterTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void disabledImportFilterAcceptsUnlistedOutputs() {
        var unrestricted = AllowedOutputFilter.unrestricted();
        var key = AEItemKey.of(Items.DIAMOND);
        assertFalse(unrestricted.isEmpty());
        assertTrue(unrestricted.matches(key));

        var restricted = new AllowedOutputFilter();
        assertTrue(restricted.isEmpty());
        assertFalse(restricted.matches(key));
    }
}
