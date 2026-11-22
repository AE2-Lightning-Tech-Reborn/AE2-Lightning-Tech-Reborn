package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIJEIWidget;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockPreviewUI;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Updates LDLib's hovered slot before JEI resolves a recipe or uses shortcut. */
@Mixin(targets = "com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory$ModularUIRecipeWidget",
        remap = false)
public abstract class JeiMultiblockSlotHoverMixin {
    @WrapOperation(method = "getSlotUnderMouse", at = @At(value = "INVOKE",
            target = "Lcom/lowdragmc/lowdraglib2/integration/xei/jei/ModularUIJEIWidget;getWorldMouse(FF)Lorg/joml/Vector2f;"),
            require = 1)
    private Vector2f ae2lt$refreshSlotHover(ModularUIJEIWidget widget, float x, float y, Operation<Vector2f> original) {
        var mouse = original.call(widget, x, y);
        var ui = widget.modularUI;
        if (MultiblockPreviewUI.owns(ui)) {
            ui.refreshHoveredElement(mouse.x, mouse.y);
        }
        return mouse;
    }
}
