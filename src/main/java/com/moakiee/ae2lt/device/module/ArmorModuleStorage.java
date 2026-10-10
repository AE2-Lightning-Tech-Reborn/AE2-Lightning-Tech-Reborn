package com.moakiee.ae2lt.device.module;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.celestweave.ArmorPart;

public final class ArmorModuleStorage implements DeviceModuleStorage {

    private final ArmorPart part;

    public ArmorModuleStorage(ArmorPart part) {
        this.part = part;
    }

    @Override
    public DeviceKind deviceKind() {
        return part.deviceKind();
    }

    @Override
    public List<ItemStack> listEntries(ItemStack device) {
        return List.of();
    }

    @Override
    public int getCount(ItemStack device, String typeId) {
        return 0;
    }

    @Override
    public boolean canInstallOne(ItemStack device, ItemStack candidate) {
        return false;
    }

    @Override
    public boolean installOne(ItemStack device, ItemStack candidate) {
        return false;
    }

    @Override
    public ItemStack uninstallOne(ItemStack device, String typeId) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack uninstallAll(ItemStack device, String typeId) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hasAnyInstalled(ItemStack device) {
        return false;
    }

    @Override
    public Stream<ItemStack> installedModuleStacks(ItemStack device) {
        return Stream.empty();
    }
}
