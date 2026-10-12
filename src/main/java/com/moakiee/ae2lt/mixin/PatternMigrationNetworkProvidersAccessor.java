package com.moakiee.ae2lt.mixin;

import appeng.api.networking.IGridNode;
import appeng.me.service.helpers.NetworkCraftingProviders;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = NetworkCraftingProviders.class, remap = false)
public interface PatternMigrationNetworkProvidersAccessor {
    @Accessor("craftingProviders") Map<IGridNode, Object> ae2lt$providerStates();
    @Invoker("setLastModifiedOnTick") void ae2lt$markModified();
}
