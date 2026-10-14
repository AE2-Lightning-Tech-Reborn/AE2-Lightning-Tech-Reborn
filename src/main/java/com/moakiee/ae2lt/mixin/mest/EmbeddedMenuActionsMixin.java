package com.moakiee.ae2lt.mixin.mest;

import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import com.lhy.mest.terminal.MESTMenu;
import com.moakiee.ae2lt.integration.mest.MestSlot;
import com.moakiee.ae2lt.menu.extension.EmbeddedTerminalMenu;
import com.moakiee.ae2lt.menu.extension.TianshuMenuContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEBaseMenu.class, remap = false)
public abstract class EmbeddedMenuActionsMixin {
    @ModifyArg(method = "sendClientAction(Ljava/lang/String;Ljava/lang/Object;)V", at = @At(value = "INVOKE",
            target = "Lappeng/core/network/serverbound/GuiActionPacket;<init>(ILjava/lang/String;Ljava/lang/String;)V"), index = 1)
    private String ae2lt$namespaceAction(String action) {
        return (Object) this instanceof EmbeddedTerminalMenu menu ? menu.qualifyAction(action) : action;
    }

    @Inject(method = "receiveClientAction", at = @At("HEAD"), cancellable = true)
    private void ae2lt$routeAction(String action, String payload, CallbackInfo ci) {
        if (!((Object) this instanceof MESTMenu outer) || !(outer instanceof TianshuMenuContainer container)
                || !action.startsWith(EmbeddedTerminalMenu.ACTION_PREFIX)) return;
        ci.cancel();
        if (!(outer.getPlayer() instanceof ServerPlayer player) || !outer.canUseTerminal(player)) return;
        String qualified = action.substring(EmbeddedTerminalMenu.ACTION_PREFIX.length());
        int separator = qualified.indexOf('.');
        if (separator < 0) return;
        var controller = container.tianshuMenu(qualified.substring(0, separator));
        if (controller != null) controller.receiveClientAction(qualified.substring(separator + 1), payload);
    }

    @Unique private MestSlot ae2lt$slot(int index) {
        var self = (AEBaseMenu) (Object) this;
        if (!(self instanceof TianshuMenuContainer) || index < 0 || index >= self.slots.size()) return null;
        return self.slots.get(index) instanceof MestSlot slot ? slot : null;
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void ae2lt$click(int index, int button, ClickType type, Player player, CallbackInfo ci) {
        var slot = ae2lt$slot(index);
        // Vanilla owns the outer drag/pick-all gesture, including its start/end packets outside a slot.
        if (slot == null || type == ClickType.QUICK_CRAFT || type == ClickType.PICKUP_ALL) return;
        if (!((TianshuMenuContainer) this).tianshuAccessValid()) { ci.cancel(); return; }
        slot.controller().clicked(slot.original().index, button, type, player);
        ci.cancel();
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void ae2lt$quickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        var slot = ae2lt$slot(index);
        if (slot != null) cir.setReturnValue(((TianshuMenuContainer) this).tianshuAccessValid()
                ? slot.controller().quickMoveStack(player, slot.original().index) : ItemStack.EMPTY);
    }

    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void ae2lt$action(ServerPlayer player, InventoryAction action, int index, long id, CallbackInfo ci) {
        var slot = ae2lt$slot(index);
        if (slot == null) return;
        if (!((TianshuMenuContainer) this).tianshuAccessValid()) { ci.cancel(); return; }
        slot.controller().doAction(player, action, slot.original().index, id);
        ci.cancel();
    }

    @Inject(method = "setFilter", at = @At("HEAD"), cancellable = true)
    private void ae2lt$filter(int index, ItemStack stack, CallbackInfo ci) {
        var slot = ae2lt$slot(index);
        if (slot == null) return;
        if (!((TianshuMenuContainer) this).tianshuAccessValid()) { ci.cancel(); return; }
        slot.controller().setFilter(slot.original().index, stack);
        ci.cancel();
    }

    @Inject(method = "canDragTo", at = @At("HEAD"), cancellable = true)
    private void ae2lt$drag(Slot slot, CallbackInfoReturnable<Boolean> cir) {
        if (slot instanceof MestSlot proxy) cir.setReturnValue(proxy.controller().canDragTo(proxy.original()));
    }

    @Inject(method = "swapSlotContents", at = @At("HEAD"), cancellable = true)
    private void ae2lt$swap(int a, int b, CallbackInfo ci) {
        // AE2's direct swap does not call onTake. Workstation result slots must use native crafting gestures.
        var left = ae2lt$slot(a);
        var right = ae2lt$slot(b);
        if (left == null && right == null) return;
        if (!((TianshuMenuContainer) this).tianshuAccessValid()
                || !ae2lt$allowsDirectSwap(left) || !ae2lt$allowsDirectSwap(right)) ci.cancel();
    }

    @Unique private static boolean ae2lt$allowsDirectSwap(MestSlot proxy) {
        return proxy == null || !(proxy.original() instanceof appeng.menu.slot.FakeSlot)
                && proxy.controller().canTakeItemForPickAll(proxy.original().getItem(), proxy.original());
    }

    @Inject(method = "isValidQuickMoveDestination", at = @At("HEAD"), cancellable = true)
    private void ae2lt$explicitInputs(Slot slot, ItemStack stack, boolean fromPlayer, CallbackInfoReturnable<Boolean> cir) {
        if (slot instanceof MestSlot) cir.setReturnValue(false);
    }
}
