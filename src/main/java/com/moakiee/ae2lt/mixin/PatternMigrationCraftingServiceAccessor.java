package com.moakiee.ae2lt.mixin;

import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CraftingService.class, remap = false)
public interface PatternMigrationCraftingServiceAccessor {
    @Accessor("craftingProviders") NetworkCraftingProviders ae2lt$craftingProviders();
}
