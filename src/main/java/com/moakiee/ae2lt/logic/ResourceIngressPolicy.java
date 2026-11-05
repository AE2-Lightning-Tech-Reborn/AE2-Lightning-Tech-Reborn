package com.moakiee.ae2lt.logic;

import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.server.level.ServerLevel;

/** Admission of new resources, not restoration or return of already owned funds. */
public final class ResourceIngressPolicy {
    private ResourceIngressPolicy() {}

    public static boolean isOnline(AEBaseBlockEntity host, IManagedGridNode node) {
        return host.getLevel() instanceof ServerLevel && !host.isRemoved()
                && node != null && node.isActive() && node.getGrid() != null;
    }

    public static boolean canAccept(AEKey key, boolean online) {
        // AE2 15.4.10 has no drop-support flag. Only ordinary items have a
        // guaranteed ItemStack recovery path. Fluid/FE/lightning and unknown
        // custom key types fail closed offline; never call addDrops as a probe.
        return key != null && (online || key instanceof AEItemKey);
    }
}
