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
        public ItemStack core(ItemStack d) { return core; }
        public boolean canPlaceCore(ItemStack d, ItemStack s) { return s.is(Items.DIAMOND); }
        public void setCore(ItemStack d, ItemStack s) { core = s; }
        public List<ItemStack> modules(ItemStack d) { return List.of(); }
        public String moduleId(ItemStack s) { return "test:module"; }
        public int maxInstallAmount(ItemStack s) { return 1; }
        public boolean canInstallOne(ItemStack d, ItemStack s) { return false; }
        public boolean installOne(ItemStack d, ItemStack s) { return false; }
        public ItemStack uninstallOne(ItemStack d, String id) { return ItemStack.EMPTY; }
        public ItemStack uninstallAll(ItemStack d, String id) { return ItemStack.EMPTY; }
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
}
