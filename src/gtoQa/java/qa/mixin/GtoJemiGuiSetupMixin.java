package qa.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional real JEI backend for QA; JEMI then installs its normal public runtime services. */
@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.JemiPlugin", remap = false)
public abstract class GtoJemiGuiSetupMixin {
    @Inject(method = "registerRuntime(Lmezz/jei/api/registration/IRuntimeRegistration;)V", at = @At("HEAD"), require = 1)
    private void ae2lt$prepareNativeLayoutBackend(@Coerce Object registration, CallbackInfo ci) {
        if (Boolean.getBoolean("ae2lt.remoteDeviceClientProbe")) qa.GtoJeiNativeGui.startBeforeJemi(registration);
    }
}
