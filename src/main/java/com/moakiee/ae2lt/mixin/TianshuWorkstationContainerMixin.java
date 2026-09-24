package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.recipe.compat.LegacyContainerListeners;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleContainer.class)
public abstract class TianshuWorkstationContainerMixin implements LegacyContainerListeners {
    @Unique private Runnable ae2lt$changeListener;
    @Override public void ae2lt$addChangeListener(Runnable listener) {
        var previous = ae2lt$changeListener;
        ae2lt$changeListener = previous == null ? listener : () -> { previous.run(); listener.run(); };
    }
    @Inject(method = "setChanged", at = @At("RETURN"))
    private void ae2lt$notifyWorkstation(CallbackInfo ci) {
        if (ae2lt$changeListener != null) ae2lt$changeListener.run();
    }
}
