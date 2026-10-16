package com.moakiee.ae2lt.blockentity.workbench;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceItem;
import com.moakiee.ae2lt.device.DeviceKind;

public final class DeviceWorkbenchAdapters {
    private static final Map<DeviceKind, DeviceWorkbenchAdapter> BY_KIND = new EnumMap<>(DeviceKind.class);
    private static final com.moakiee.ae2lt.logic.extension.ItemExtensionRegistry<DeviceWorkbenchAdapter> BY_ITEM =
            new com.moakiee.ae2lt.logic.extension.ItemExtensionRegistry<>();

    static {
        register(ArmorWorkbenchAdapter.HELMET);
        register(ArmorWorkbenchAdapter.CHESTPLATE);
        register(ArmorWorkbenchAdapter.LEGGINGS);
        register(ArmorWorkbenchAdapter.BOOTS);
        register(RailgunWorkbenchAdapter.INSTANCE);
    }

    private DeviceWorkbenchAdapters() {}

    public static void register(DeviceWorkbenchAdapter adapter) {
        BY_KIND.put(adapter.deviceKind(), adapter);
    }

    public static Optional<DeviceWorkbenchAdapter> get(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            var exact = BY_ITEM.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (exact != null) return Optional.of(exact);
        }
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof DeviceItem device)) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_KIND.get(device.deviceKind()));
    }

    public static void registerItem(net.minecraft.resources.ResourceLocation itemId, DeviceWorkbenchAdapter adapter) {
        BY_ITEM.register(itemId, adapter);
    }

    public static void freezeItems() { BY_ITEM.freeze(); }
}
