package com.moakiee.ae2lt.menu.hub;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import com.moakiee.ae2lt.celestweave.ArmorWearer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;

/**
 * MenuProvider that opens the unified DeviceHub UI.
 */
public class DeviceHubHost implements MenuProvider {
    private final int defaultTab;
    private final LivingEntity wearer;

    public DeviceHubHost(int defaultTab) {
        this(defaultTab, null);
    }

    private DeviceHubHost(int defaultTab, LivingEntity wearer) {
        this.defaultTab = defaultTab;
        this.wearer = wearer;
    }

    @Override
    public Component getDisplayName() {
        return wearer == null ? Component.translatable("gui.ae2lt.device_hub")
                : Component.translatable("gui.ae2lt.device_hub.entity", wearer.getDisplayName());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DeviceHubMenu(containerId, playerInventory, defaultTab, wearer == null ? player : wearer);
    }

    /** Open the hub for a player with the given default tab. */
    public static void open(ServerPlayer player, int defaultTab) {
        int resolvedTab = resolveDefaultTab(player, defaultTab);
        if (resolvedTab < 0) {
            return;
        }
        player.openMenu(new DeviceHubHost(resolvedTab), buf -> buf.writeVarInt(resolvedTab));
    }

    public static boolean canConfigure(Player player, LivingEntity wearer) {
        return wearer == player || (!(wearer instanceof Player) && wearer.isAlive() && !wearer.isRemoved()
                && wearer.level() == player.level()
                && ArmorWearer.isOwnedBy(wearer, player)
                && player.canInteractWithEntity(wearer, 1.0D));
    }

    public static void openForEntity(ServerPlayer player, LivingEntity wearer) {
        if (!canConfigure(player, wearer) || !player.hasLineOfSight(wearer)) {
            return;
        }
        int tab = resolveDefaultTab(wearer, DeviceHubMenu.TAB_CHESTPLATE);
        if (tab >= 0) {
            player.openMenu(new DeviceHubHost(tab, wearer), buf -> {
                buf.writeVarInt(tab);
                buf.writeVarInt(wearer.getId());
            });
        }
    }

    /** Map an equipment slot to its corresponding hub tab index. */
    public static int tabForArmorSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> 1;
        };
    }

    private static int resolveDefaultTab(LivingEntity player, int defaultTab) {
        if (hasDeviceForTab(player, defaultTab)) {
            return defaultTab;
        }
        for (int tab = 0; tab < DeviceHubMenu.TAB_COUNT; tab++) {
            if (hasDeviceForTab(player, tab)) {
                return tab;
            }
        }
        return -1;
    }

    private static boolean hasDeviceForTab(LivingEntity player, int tab) {
        return switch (tab) {
            case DeviceHubMenu.TAB_HELMET -> hasArmor(player, EquipmentSlot.HEAD);
            case DeviceHubMenu.TAB_CHESTPLATE -> hasArmor(player, EquipmentSlot.CHEST);
            case DeviceHubMenu.TAB_LEGGINGS -> hasArmor(player, EquipmentSlot.LEGS);
            case DeviceHubMenu.TAB_BOOTS -> hasArmor(player, EquipmentSlot.FEET);
            case DeviceHubMenu.TAB_RAILGUN -> hasRailgun(player);
            default -> false;
        };
    }

    private static boolean hasArmor(LivingEntity player, EquipmentSlot slot) {
        return !CelestweaveEquipmentAccess.findArmor(player, slot).isEmpty();
    }

    private static boolean hasRailgun(LivingEntity player) {
        return player.getMainHandItem().getItem() instanceof ElectromagneticRailgunItem
                || player.getOffhandItem().getItem() instanceof ElectromagneticRailgunItem;
    }
}
