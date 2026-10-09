package com.moakiee.ae2lt.api.device;

import static org.junit.jupiter.api.Assertions.*;
import com.moakiee.ae2lt.blockentity.workbench.AddonWorkbenchAdapter;
import com.moakiee.ae2lt.blockentity.workbench.DeviceWorkbenchAdapters;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkbenchDeviceTest {
    @BeforeAll static void bootstrap() {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        }
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }

    static class Device implements WorkbenchDevice {
        ItemStack core = new ItemStack(Items.DIAMOND);
        public ItemStack core(ItemStack deviceStack) { return core; }
        public boolean canPlaceCore(ItemStack deviceStack, ItemStack candidate) { return candidate.is(Items.DIAMOND); }
        public void setCore(ItemStack deviceStack, ItemStack candidate) { core = candidate; }
        public List<ItemStack> modules(ItemStack deviceStack) { return List.of(); }
        public String moduleId(ItemStack candidate) { return "test:module"; }
        public int maxInstallAmount(ItemStack candidate) { return 1; }
        public boolean canInstallOne(ItemStack deviceStack, ItemStack candidate) { return false; }
        public boolean installOne(ItemStack deviceStack, ItemStack candidate) { return false; }
        public ItemStack uninstallOne(ItemStack deviceStack, String identifier) { return ItemStack.EMPTY; }
        public ItemStack uninstallAll(ItemStack deviceStack, String identifier) { return ItemStack.EMPTY; }
    }

    @Test void removingCoreTransfersOneOwnedStackWithoutAliasing() {
        var device = new Device(); var adapter = new AddonWorkbenchAdapter(device);
        var tool = new ItemStack(Items.STICK);
        assertTrue(adapter.removeStructuralSlot(tool, null, null, 0).isEmpty());
        var original = device.core;
        var removed = adapter.removeStructuralSlot(tool, null, null, 1);
        assertEquals(1, removed.getCount()); assertNotSame(original, removed);
        assertTrue(device.core.isEmpty());
        assertTrue(adapter.removeStructuralSlot(tool, null, null, 1).isEmpty());
    }

    @Test void registeredOrdinaryItemsDoNotNeedDeviceItemInheritance() {
        var adapter = new AddonWorkbenchAdapter(new Device());
        var other = new AddonWorkbenchAdapter(new Device());
        DeviceWorkbenchAdapters.registerItem(BuiltInRegistries.ITEM.getKey(Items.STICK), adapter);
        DeviceWorkbenchAdapters.registerItem(BuiltInRegistries.ITEM.getKey(Items.BLAZE_ROD), other);
        assertSame(adapter, DeviceWorkbenchAdapters.get(new ItemStack(Items.STICK)).orElseThrow());
        assertSame(other, DeviceWorkbenchAdapters.get(new ItemStack(Items.BLAZE_ROD)).orElseThrow());
        assertTrue(DeviceWorkbenchAdapters.get(new ItemStack(Items.DIRT)).isEmpty());
    }

    @Test void placingCoreCopiesInputAndRejectsInvalidItems() {
        var device = new Device();
        var adapter = new AddonWorkbenchAdapter(device);
        var tool = new ItemStack(Items.STICK);
        var input = new ItemStack(Items.DIAMOND);
        adapter.setStructuralSlot(tool, null, null, input);
        assertNotSame(input, device.core);
        input.shrink(1);
        assertEquals(1, device.core.getCount());
        var installed = device.core;
        adapter.setStructuralSlot(tool, null, null, new ItemStack(Items.DIRT));
        assertSame(installed, device.core);
        adapter.setStructuralSlot(tool, null, null, ItemStack.EMPTY);
        assertTrue(device.core.isEmpty());
    }

    @Test void publicRegistrationDelegatesEnergyAndServerTicksForOrdinaryItems() {
        var device = new Device() {
            public long storedEnergy(ItemStack deviceStack) { return 7; }
            public long energyCapacity(ItemStack deviceStack) { return 24; }
            public boolean serverTick(ItemStack deviceStack, Context context) {
                deviceStack.getOrCreateTag().putBoolean("AddonTick", true);
                return true;
            }
        };
        DeviceWorkbenchApi.register(BuiltInRegistries.ITEM.getKey(Items.FISHING_ROD), device);
        var tool = new ItemStack(Items.FISHING_ROD);
        var adapter = (AddonWorkbenchAdapter) DeviceWorkbenchAdapters.get(tool).orElseThrow();
        assertEquals(7, adapter.energyBuffer().stored(tool));
        assertEquals(24, adapter.energyBuffer().capacity(tool));
        assertTrue(adapter.serverTick(tool, new WorkbenchDevice.Context(null, net.minecraft.core.BlockPos.ZERO, null)));
        assertTrue(tool.getTag().getBoolean("AddonTick"));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceWorkbenchApi.register(BuiltInRegistries.ITEM.getKey(Items.FISHING_ROD), new Device()));
    }

    @Test void serverContextSnapshotsTheWorkbenchPosition() {
        var position = new net.minecraft.core.BlockPos.MutableBlockPos(1, 2, 3);
        var context = new WorkbenchDevice.Context(null, position, null);
        position.set(4, 5, 6);
        assertEquals(new net.minecraft.core.BlockPos(1, 2, 3), context.pos());
    }
}
