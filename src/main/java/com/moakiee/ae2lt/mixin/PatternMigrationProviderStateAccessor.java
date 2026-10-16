package com.moakiee.ae2lt.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.helpers.NetworkCraftingProviders;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "appeng.me.service.helpers.NetworkCraftingProviders$ProviderState", remap = false)
public interface PatternMigrationProviderStateAccessor {
    @Accessor("provider") ICraftingProvider ae2lt$provider();
    @Accessor("patterns") List<IPatternDetails> ae2lt$patterns();
    @Mutable @Accessor("patterns") void ae2lt$patterns(List<IPatternDetails> patterns);
    @Invoker("unmount") void ae2lt$unmount(NetworkCraftingProviders providers);
}
