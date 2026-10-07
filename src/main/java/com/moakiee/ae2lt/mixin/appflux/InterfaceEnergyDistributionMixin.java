package com.moakiee.ae2lt.mixin.appflux;

import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import com.glodblock.github.appflux.common.me.energy.EnergyHandler;
import com.glodblock.github.appflux.util.AFUtil;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applied Flux 1.20.1 merges its distributor into InterfaceLogic. */
@Mixin(value = InterfaceLogic.class, priority = 900, remap = false)
public abstract class InterfaceEnergyDistributionMixin {
    @Shadow protected InterfaceLogicHost host;
    @Shadow protected IManagedGridNode mainNode;
    @Shadow protected IActionSource actionSource;

    @Inject(method = "distribute()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2lt$routeAdjacentOutput(CallbackInfo ci) {
        if (!(host instanceof OverloadedInterfaceBlockEntity overloaded)) return;
        ci.cancel();
        // Explicit faces and wireless links are serviced by the block ticker.
        if (overloaded.getInterfaceMode() != OverloadedInterfaceBlockEntity.InterfaceMode.NORMAL
                || overloaded.getTargetDirection() != null) return;
        var grid = mainNode.getGrid();
        var level = overloaded.getLevel();
        if (grid == null || level == null) return;
        for (var side : Direction.values()) {
            var pos = overloaded.getBlockPos().relative(side);
            if (!level.isLoaded(pos) || !overloaded.allowsNormalInteraction(side)) continue;
            var target = level.getBlockEntity(pos);
            if (target != null && AFUtil.isWhiteListTE(target, side.getOpposite())) {
                // Re-resolve after the grid policy check: AppFlux 1.20 caches both
                // forbidden grids and capabilities until a neighbor update.
                EnergyHandler.getHandler(target, side.getOpposite()).send(grid.getStorageService(), actionSource);
            }
        }
    }
}
