package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.me.crafting.CraftErrorScreen;
import com.moakiee.ae2lt.client.crafting.AE2LtCraftConfirmScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Native Chinese AE2 calls this action "Back"; make the report's frozen-stock replan explicit. */
@Mixin(value = CraftErrorScreen.class, remap = false)
public abstract class CraftErrorScreenReplanLabelMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/WidgetContainer;addButton(Ljava/lang/String;"
                    + "Lnet/minecraft/network/chat/Component;Ljava/lang/Runnable;)Lappeng/client/gui/widgets/AE2Button;",
            ordinal = 0), index = 1)
    private Component ae2lt$replanLabel(Component original) {
        return ((CraftErrorScreen) (Object) this).getParent() instanceof AE2LtCraftConfirmScreen
                ? Component.translatable("gui.ae2lt.crafting_report.replan") : original;
    }
}
