package com.moakiee.ae2lt.mixin.mest;

import appeng.client.gui.AEBaseScreen;
import com.moakiee.ae2lt.integration.mest.client.MestTianshuClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.minecraft.client.gui.screens.Screen;

@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class EmbeddedSubScreenMixin {
    @ModifyArg(method = "switchToScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"), index = 0)
    private Screen ae2lt$returnToComposite(Screen screen) {
        return screen instanceof AEBaseScreen<?> ae ? MestTianshuClient.returnTarget(ae) : screen;
    }
}
