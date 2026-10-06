package com.moakiee.ae2lt.api.device;

import com.moakiee.ae2lt.logic.extension.ItemExtensionRegistry;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.menu.hub.DeviceHubHost;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public final class DeviceHubApi {
    private static final ItemExtensionRegistry<DeviceHubPage> PAGES = new ItemExtensionRegistry<>();
    private DeviceHubApi() {}
    /** Register once per item during common setup on both sides. */
    public static void register(ResourceLocation itemId, DeviceHubPage page) {
        java.util.Objects.requireNonNull(page.id());
        PAGES.register(itemId, page);
    }
    public static @Nullable DeviceHubPage find(ItemStack stack) {
        return stack.isEmpty() ? null : PAGES.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
    /** Main hand wins over offhand, for both addon devices and the original railgun. */
    public static ItemStack heldDevice(Player player) {
        for (var stack : java.util.List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (find(stack) != null || stack.getItem() instanceof ElectromagneticRailgunItem) return stack;
        }
        return ItemStack.EMPTY;
    }
    public static void open(ServerPlayer player) {
        if (!heldDevice(player).isEmpty()) DeviceHubHost.open(player, DeviceHubMenu.TAB_RAILGUN);
    }
    @ApiStatus.Internal public static void freeze() { PAGES.freeze(); }
}
