package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternProviderMenu;
import com.moakiee.ae2lt.api.patternprovider.WirelessPatternProviderHost;
import com.moakiee.ae2lt.client.provider.SharedProviderPanels;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AEBaseScreen.class)
public abstract class ProviderScreenWidgetOwnershipMixin {
    @Shadow(remap = false) @Final protected WidgetContainer widgets;

    // AEBaseScreen returns before PatternProviderScreen runs AppliedFlux's constructor hook.
    // Marking the container in our concrete screen constructor would already be too late.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2lt$markSharedProviderPanels(AEBaseMenu menu, Inventory inventory,
            Component title, ScreenStyle style, CallbackInfo ci) {
        if (menu instanceof PatternProviderMenu
                && menu.getTarget() instanceof WirelessPatternProviderHost) {
            ((SharedProviderPanels) widgets).ae2lt$enableSharedProviderPanels();
        }
    }
}
