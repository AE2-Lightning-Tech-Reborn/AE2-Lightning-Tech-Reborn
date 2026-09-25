package com.moakiee.ae2lt.mixin.ae2wtlib;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.cluster.implementations.QuantumCluster;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Refresh both native Forge grid caches after a quantum bridge disappears. */
@Mixin(value = WTMenuHost.class, remap = false)
public abstract class WTMenuHostQuantumBridgeMixin {
    @Shadow private IActionHost quantumBridge;
    @Shadow @Final @Mutable private IGrid targetGrid;
    @Unique private boolean ae2lt$refreshQuantumGrid;

    @Inject(method = {"rangeCheck", "isQuantumLinked"}, at = @At("HEAD"), require = 1)
    private void ae2lt$refreshQuantumConnection(CallbackInfoReturnable<Boolean> cir) {
        if (quantumBridge instanceof QuantumCluster cluster
                && (cluster.isDestroyed() || cluster.getCenter() == null)) {
            quantumBridge = null;
            ae2lt$refreshQuantumGrid = true;
        }
        if (!ae2lt$refreshQuantumGrid) return;
        var self = (WTMenuHost) (Object) this;
        var stack = self.getItemStack();
        var player = self.getPlayer();
        // Resolve by the terminal's own binding/card rules, retaining native access-point fallback.
        var grid = ((WirelessTerminalItem) stack.getItem()).getLinkedGrid(stack, player.level(), null);
        if (grid == null) return; // Keep retrying while the bridge is being rebuilt.
        targetGrid = grid;
        var nativeHost = (WirelessTerminalGridAccessor) self;
        nativeHost.ae2lt$setTargetGrid(grid);
        nativeHost.ae2lt$setStorageService(grid.getStorageService());
        ae2lt$refreshQuantumGrid = false;
    }
}
