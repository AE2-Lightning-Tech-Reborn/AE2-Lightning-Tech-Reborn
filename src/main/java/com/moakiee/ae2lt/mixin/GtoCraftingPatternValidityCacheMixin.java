package com.moakiee.ae2lt.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AECraftingPattern;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AE2-GTO's primitive boolean cache must distinguish an untested item from a rejected one. */
@Mixin(value = AECraftingPattern.class, remap = false)
public abstract class GtoCraftingPatternValidityCacheMixin {
    @Shadow @Final private Reference2BooleanOpenHashMap<Item>[] isValidCache;

    @Inject(method = "getTestResult", at = @At("HEAD"), cancellable = true)
    private void ae2lt$leaveUntestedSubstitutionsUncached(int slot, AEItemKey key,
                                                        CallbackInfoReturnable<Boolean> result) {
        if (key == null || key.hasTag()) return;
        var cache = isValidCache[slot];
        if (cache != null && !cache.containsKey(key.getItem())) result.setReturnValue(null);
    }
}
