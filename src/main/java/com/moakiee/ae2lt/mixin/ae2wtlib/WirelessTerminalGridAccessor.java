package com.moakiee.ae2lt.mixin.ae2wtlib;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.helpers.WirelessTerminalMenuHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Forge's native wireless host keeps its own grid and storage cache. */
@Mixin(value = WirelessTerminalMenuHost.class, remap = false)
public interface WirelessTerminalGridAccessor {
    @Mutable @Accessor("targetGrid") void ae2lt$setTargetGrid(IGrid grid);
    @Accessor("sg") void ae2lt$setStorageService(IStorageService storage);
}
