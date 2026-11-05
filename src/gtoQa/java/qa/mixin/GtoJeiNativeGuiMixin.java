package qa.mixin;

import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The original JEI layout assertions use the real JEI object, alongside the real EMI browser. */
@Mixin(value = RecipesGui.class, remap = false)
public abstract class GtoJeiNativeGuiMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void ae2lt$captureNativeGui(CallbackInfo ci) {
        if (Boolean.getBoolean("ae2lt.remoteDeviceClientProbe"))
            qa.GtoJeiNativeGui.capture((RecipesGui) (Object) this);
    }
}
