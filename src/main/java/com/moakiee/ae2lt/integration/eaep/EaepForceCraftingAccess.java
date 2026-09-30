package com.moakiee.ae2lt.integration.eaep;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import appeng.menu.me.crafting.CraftConfirmMenu;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class EaepForceCraftingAccess {
    private static final Logger LOG = LogUtils.getLogger();
    private static EaepForceCraftingBridge bridge;
    private static boolean resolved;

    private EaepForceCraftingAccess() {
    }

    public static boolean isAvailable(CraftConfirmMenu menu) {
        var adapter = adapter();
        if (adapter == null || !adapter.menuType().isInstance(menu)) {
            return false;
        }
        var minecraft = Minecraft.getInstance();
        var connection = minecraft == null ? null : minecraft.getConnection();
        return connection != null && NetworkRegistry.hasChannel(connection, adapter.channel());
    }

    public static boolean synchronize(CraftConfirmMenu menu, boolean forceStart) {
        if (!isAvailable(menu)) {
            return !forceStart;
        }
        try {
            PacketDistributor.sendToServer(bridge.packet(forceStart));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            bridge = null;
            LOG.warn("Could not synchronize the force-crafting request", failure);
            return false;
        }
    }

    private static EaepForceCraftingBridge adapter() {
        var mods = ModList.get();
        if (mods == null || !mods.isLoaded("extendedae_plus")) {
            return null;
        }
        if (!resolved) {
            resolved = true;
            try {
                bridge = EaepForceCraftingBridge.resolve(
                        Class.forName("com.extendedae_plus.api.crafting.IForceCraftStartSync"),
                        Class.forName("com.extendedae_plus.network.crafting.ForceCraftStartFlagC2SPacket"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                LOG.warn("The optional force-crafting adapter is unavailable", failure);
            }
        }
        return bridge;
    }
}
