package com.moakiee.ae2lt.mixin.mest;

import com.lhy.mest.client.MESTScreen;
import com.lhy.mest.client.dock.DockManager;
import com.moakiee.ae2lt.integration.mest.client.MestTianshuClient;
import com.moakiee.ae2lt.integration.mest.client.TianshuModulePanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MESTScreen.class, remap = false)
public abstract class MestScreenMixin {
    @Shadow @Final private DockManager dock;
    @Inject(method = "mouseClickedInWorkspace", at = @At("HEAD"), cancellable = true)
    private void ae2lt$focus(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        var self = (MESTScreen) (Object) this;
        var target = dock.topLeafAt(x, y);
        if (target != null && !(target instanceof TianshuModulePanel)) MestTianshuClient.clearFocus(self);
        if (button == 2 && Screen.hasShiftDown() && target == self.meListPanel()) {
            var slot = self.meListPanel().repoSlotAt(x, y);
            var panel = MestTianshuClient.crafting(self);
            if (panel != null && slot != null && slot.getEntry() != null) {
                panel.source.requestMaintenanceEditorFor(slot.getEntry().getWhat()); cir.setReturnValue(true);
            }
        }
    }
    @Inject(method = "renderTooltipAtOverlay", at = @At("HEAD"), cancellable = true)
    private void ae2lt$tooltip(GuiGraphics g, int x, int y, CallbackInfo ci) {
        if (dock.topLeafAt(x, y) instanceof TianshuModulePanel panel) { panel.tooltip(g, x, y); ci.cancel(); }
    }
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void ae2lt$release(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        var panel = MestTianshuClient.focused((MESTScreen) (Object) this);
        if (panel != null && panel.mouseReleased(x, y, button)) cir.setReturnValue(true);
    }
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void ae2lt$drag(double x, double y, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        var panel = MestTianshuClient.focused((MESTScreen) (Object) this);
        if (panel != null && panel.mouseDragged(x, y, button, dx, dy)) cir.setReturnValue(true);
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ae2lt$key(int key, int scan, int mods, CallbackInfoReturnable<Boolean> cir) {
        var panel = MestTianshuClient.focused((MESTScreen) (Object) this);
        if (panel != null && panel.keyPressed(key, scan, mods)) cir.setReturnValue(true);
    }
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void ae2lt$char(char character, int mods, CallbackInfoReturnable<Boolean> cir) {
        var panel = MestTianshuClient.focused((MESTScreen) (Object) this);
        if (panel != null && panel.charTyped(character, mods)) cir.setReturnValue(true);
    }
}
