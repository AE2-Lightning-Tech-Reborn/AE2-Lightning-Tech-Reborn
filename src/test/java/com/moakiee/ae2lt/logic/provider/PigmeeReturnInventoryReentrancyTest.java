package com.moakiee.ae2lt.logic.provider;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PigmeeReturnInventoryReentrancyTest {
    @BeforeAll
    static void bootstrap() {
        com.moakiee.ae2lt.testing.MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void nestedNetworkDrainCannotCreditTheInFlightStackTwice() {
        var inventory = new PigmeePatternProviderReturnInventory(() -> {});
        var key = LightningKey.HIGH_VOLTAGE;
        inventory.setStack(0, new GenericStack(key, 10));
        long[] credited = {0};
        int[] calls = {0};
        var source = IActionSource.empty();
        var receiver = new MEStorage() {
            @Override
            public long insert(AEKey offered, long amount, Actionable mode, IActionSource actionSource) {
                assertEquals(key, offered);
                assertEquals(Actionable.MODULATE, mode);
                calls[0]++;
                if (calls[0] == 1) {
                    inventory.drainInto(this, actionSource);
                }
                credited[0] += amount;
                return amount;
            }

            @Override
            public Component getDescription() {
                return Component.literal("reentrant return receiver");
            }
        };

        assertTrue(inventory.drainInto(receiver, source));
        assertEquals(10, credited[0]);
        assertEquals(1, calls[0]);
        assertTrue(inventory.isEmpty());
        assertTrue(inventory.canInsert());
    }
}
