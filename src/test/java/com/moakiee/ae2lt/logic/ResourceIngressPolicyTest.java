package com.moakiee.ae2lt.logic;

import com.moakiee.ae2lt.logic.provider.UnlimitedReturnInventory;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class ResourceIngressPolicyTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void offlinePermitsOnlyGuaranteedItemDrops() {
        assertTrue(ResourceIngressPolicy.canAccept(AEItemKey.of(Items.STONE), false));
        assertFalse(ResourceIngressPolicy.canAccept(AEFluidKey.of(Fluids.WATER), false));
        assertFalse(ResourceIngressPolicy.canAccept(LightningKey.HIGH_VOLTAGE, false));
        assertFalse(ResourceIngressPolicy.canAccept(null, false));
    }

    @Test
    void onlineRetainsRegisteredResourceSupport() {
        assertTrue(ResourceIngressPolicy.canAccept(AEFluidKey.of(Fluids.WATER), true));
        assertTrue(ResourceIngressPolicy.canAccept(LightningKey.HIGH_VOLTAGE, true));
        assertFalse(ResourceIngressPolicy.canAccept(null, true));
    }

    @Test
    void returnInventoryRejectsBothSimulationAndMutationOffline() {
        boolean[] online = {false};
        var inv = UnlimitedReturnInventory.create(() -> {}, null);
        inv.setOnlineSupplier(() -> online[0]);
        for (var mode : Actionable.values()) {
            assertEquals(0, inv.insert(0, LightningKey.HIGH_VOLTAGE, 100, mode));
        }
        assertNull(inv.getStack(0));
        online[0] = true;
        assertEquals(100, inv.insert(0, LightningKey.HIGH_VOLTAGE, 100, Actionable.MODULATE));
        online[0] = false;
        assertEquals(0, inv.insert(0, LightningKey.HIGH_VOLTAGE, 100, Actionable.MODULATE));
        assertEquals(100, inv.getAmount(0));
    }

    @Test
    void offlineInventoryRestorationKeepsExistingResources() {
        var inv = UnlimitedReturnInventory.create(() -> {}, null);
        inv.setOnlineSupplier(() -> false);
        inv.setStack(0, new appeng.api.stacks.GenericStack(LightningKey.HIGH_VOLTAGE, 100));
        assertEquals(100, inv.getAmount(0));
        assertEquals(0, inv.insert(0, LightningKey.HIGH_VOLTAGE, 1, Actionable.MODULATE));
    }
}
