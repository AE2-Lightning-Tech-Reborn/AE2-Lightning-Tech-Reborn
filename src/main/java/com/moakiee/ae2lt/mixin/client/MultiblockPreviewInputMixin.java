package com.moakiee.ae2lt.mixin.client;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockPreviewUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hit-test at the input coordinates when a recipe viewer forwards events between rendered frames. */
@Mixin(value = ModularUI.ModularUIWidget.class, remap = false)
public abstract class MultiblockPreviewInputMixin {
    @Shadow public abstract ModularUI getModularUI();

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ae2lt$refreshClickTarget(double x, double y, int button, CallbackInfoReturnable<Boolean> callback) {
        ae2lt$refreshPreviewTarget(x, y);
        var ui = getModularUI();
        if (MultiblockPreviewUI.owns(ui) && !MultiblockPreviewUI.acceptsMouseClick(ui, x, y, button)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void ae2lt$refreshScrollTarget(double x, double y, double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> callback) {
        ae2lt$refreshPreviewTarget(x, y);
        var ui = getModularUI();
        if (MultiblockPreviewUI.owns(ui)) {
            callback.setReturnValue(MultiblockPreviewUI.mouseScrolled(ui, x, y, scrollY));
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void ae2lt$guardDragTarget(double x, double y, int button, double deltaX, double deltaY,
            CallbackInfoReturnable<Boolean> callback) {
        var ui = getModularUI();
        if (MultiblockPreviewUI.owns(ui)) {
            callback.setReturnValue(MultiblockPreviewUI.mouseDragged(ui, x, y, button, deltaX, deltaY));
        }
    }

    @Unique
    private void ae2lt$refreshPreviewTarget(double x, double y) {
        var ui = getModularUI();
        if (MultiblockPreviewUI.owns(ui)) {
            ui.refreshHoveredElement((float) x, (float) y);
        }
    }
}
