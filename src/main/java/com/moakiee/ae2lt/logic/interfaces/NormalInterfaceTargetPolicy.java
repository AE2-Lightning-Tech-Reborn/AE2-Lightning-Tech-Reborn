package com.moakiee.ae2lt.logic.interfaces;

import org.jetbrains.annotations.Nullable;

import appeng.api.AECapabilities;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.IStorageProvider;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Eligibility for automatic adjacent I/O; explicitly selected faces bypass this policy. */
public final class NormalInterfaceTargetPolicy {
    private NormalInterfaceTargetPolicy() {}

    public static boolean isProtectedTarget(ServerLevel level, BlockPos pos, Direction face, IGrid sourceGrid) {
        var host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
        if (host == null) return false;

        var faceNode = host.getGridNode(face);
        if (faceNode != null) return isProtectedNode(faceNode, sourceGrid);

        // A drive's front exposes its cell inventory but no ME connection. It
        // can still belong to our grid through a cable on another side.
        for (var side : Direction.values()) {
            if (isProtectedNode(host.getGridNode(side), sourceGrid)) return true;
        }
        return false;
    }

    static boolean isProtectedNode(@Nullable IGridNode node, IGrid sourceGrid) {
        if (node == null || node.getGrid() != sourceGrid) return false;
        var owner = node.getOwner();
        return node.getService(IStorageProvider.class) != null
                || owner instanceof PatternProviderLogicHost
                || owner instanceof InterfaceLogicHost;
    }
}
