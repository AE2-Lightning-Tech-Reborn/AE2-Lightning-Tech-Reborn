package com.moakiee.ae2lt.mixin.recipeviewer.emi;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.integration.xei.emi.LDLibEMIPlugin;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockPreviewUI;
import dev.emi.emi.api.widget.Bounds;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps EMI's keyboard and pre-click drag source lookup inside the preview's visible slot area. */
@Mixin(value = LDLibEMIPlugin.class, remap = false)
public abstract class EmiMultiblockSlotBoundsMixin {
    @Inject(method = "getBounds(Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;Z)Ldev/emi/emi/api/widget/Bounds;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private static void ae2lt$clipPreviewBounds(UIElement element, boolean content,
            CallbackInfoReturnable<Bounds> cir) {
        var ui = element.getModularUI();
        if (ui == null || !MultiblockPreviewUI.owns(ui)) {
            return;
        }

        Bounds bounds = cir.getReturnValue();
        for (UIElement ancestor = element; ancestor != null; ancestor = ancestor.getParent()) {
            var style = ancestor.getStyle();
            if (!ancestor.isDisplayed() || !ancestor.isVisible() || style.opacity() <= 0) {
                cir.setReturnValue(Bounds.EMPTY);
                return;
            }
            if (ancestor != element && !style.overflowVisible()) {
                // Match LDLib's bounds coordinate conversion; these preview panels are axis-aligned.
                var origin = ancestor.localToWorld(new Vector2f(ancestor.getContentX(), ancestor.getContentY()));
                var size = ancestor.localToWorldNormal(
                        new Vector2f(ancestor.getContentWidth(), ancestor.getContentHeight()));
                bounds = bounds.overlap(new Bounds((int) origin.x, (int) origin.y, (int) size.x, (int) size.y));
                if (bounds.empty()) {
                    cir.setReturnValue(Bounds.EMPTY);
                    return;
                }
            }
        }
        cir.setReturnValue(bounds);
    }
}
